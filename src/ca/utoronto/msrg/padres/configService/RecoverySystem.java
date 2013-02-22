package ca.utoronto.msrg.padres.configService;

import java.io.File;
import java.io.Serializable;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import org.apache.log4j.Logger;

import ca.utoronto.msrg.padres.broker.brokercore.BrokerCore;
import ca.utoronto.msrg.padres.broker.brokercore.BrokerCoreException;
import ca.utoronto.msrg.padres.broker.brokercore.HeartbeatSubscriber;
import ca.utoronto.msrg.padres.client.Client;
import ca.utoronto.msrg.padres.client.ClientException;
import ca.utoronto.msrg.padres.common.message.Message;
import ca.utoronto.msrg.padres.common.message.MessageDestination;
import ca.utoronto.msrg.padres.common.message.Publication;
import ca.utoronto.msrg.padres.common.message.PublicationMessage;
import ca.utoronto.msrg.padres.common.message.parser.MessageFactory;
import ca.utoronto.msrg.padres.common.message.parser.ParseException;
import ca.utoronto.msrg.padres.configService.schema.Broker;
import ca.utoronto.msrg.padres.configService.schema.Config;
import ca.utoronto.msrg.padres.configService.schema.Node;

public class RecoverySystem extends Client implements IRecoverySys {

	private Publication pub;
	private Map<String, Serializable> header;
	private String msgType, failedBrokerID;
	private int usedBackupNodes;
	private Config config;
	private RemoteExec remoteExec;
	// stub of clients
	private List<CSClient> registeredClients = new ArrayList<CSClient>();

	// deployment file location
	private final String DEPLOYMENT_FILE = "etc\\deployment.xml";

	private final boolean DEBUG = true; 

	/**
	 * 
	 * Constructor. It loads default deployment file and connects to the first broker
	 * on the list. It registers service in the RMI registry, switches on the global
	 * failure detection option and subscribe to the heartbeats in the system.
	 * 
	 * @param id - recovery system identifier
	 * @throws ClientException
	 * @throws ParseException
	 * @throws JAXBException
	 */
	public RecoverySystem(String id) throws ClientException, ParseException,
			JAXBException {
		super(id);

		this.config = loadDeploymentFile();
		String brokerURI = getBrokerToConnect(config, 0);
		this.remoteExec = new RemoteExec();
		usedBackupNodes = 0;

		exportToRegistry();
		connect(brokerURI);
		publishGlobalFD(true);
		subscribeToHeartbeats();
		
		initLog("recoverySystem");
		System.out.println("RecoverySystem has started...");
	}

	/**
	 * 
	 * Constructor. It connects to the first broker provided in the Config object.
	 * It registers service in the RMI registry, switches on the global
	 * failure detection option and subscribe to the heartbeats in the system.
	 * 
	 * @param id - recovery system identifier
	 * @param config - Config object with whole topology nodes
	 * @param remoteExec
	 * @throws ClientException
	 * @throws ParseException
	 */
	public RecoverySystem(String id, Config config, RemoteExec remoteExec)
			throws ClientException, ParseException {
		super(id);
		this.config = config;
		this.remoteExec = remoteExec;
		usedBackupNodes = 0;

		exportToRegistry();
		connect(getBrokerToConnect(config, 0));
		publishGlobalFD(true);
		subscribeToHeartbeats();
		
		initLog("recoverySystem");
		System.out.println("RecoverySystem has started...");
	}

	/**
	 * 
	 * Loads default XML deployment file with the topology and then using 
	 * JAXB maps it to the Java representation
	 * 
	 * @return  
	 * @throws JAXBException
	 */
	private Config loadDeploymentFile() throws JAXBException {
		File file = new File(getDeploymentFile());
		JAXBContext jaxbContext = JAXBContext.newInstance(Config.class);

		Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
		return (Config) jaxbUnmarshaller.unmarshal(file);
	}

	/**
	 * @return default deployment file
	 */
	private String getDeploymentFile() {
		return DEPLOYMENT_FILE;
	}

	/**
	 * @param config - object with the topology
	 * @param number - number of the broker on the list
	 * @return brokerURI
	 */
	private String getBrokerToConnect(Config config, int number) {
		return config.getTopology().getBroker().get(number).getURI();
	}

	/**
	 * 
	 * It updates given Broker object with the information contained
	 * in the provided Node object
	 * 
	 * @param node
	 * @param broker
	 * @return
	 */
	private Broker nodeToBroker(Node node, Broker broker) {
		broker.setHost(node.getHost());
		broker.setPort(node.getPort());
		broker.setUsername(node.getUsername());
		broker.setPassword(node.getPassword());

		return broker;
	}

	/**
	 * 
	 * Returns first free broker from the backup list. If all are already in use
	 * returns null
	 * 
	 * @param broker
	 * @return
	 */
	private Broker getBackupNode(Broker broker) {
		List<Node> backupNodes = config.getBackup().getNode();

		if (usedBackupNodes < backupNodes.size()) {
			Node node = backupNodes.get(usedBackupNodes);
			usedBackupNodes++;
			return nodeToBroker(node, broker);
		} else {
			return null;
		}
	}

	/**
	 * 
	 * Returns Broker object which matches given ID
	 * 
	 * @param brokerID
	 * @return
	 */
	private Broker findBrokerByID(String brokerID) {
		for (Broker broker : config.getTopology().getBroker()) {
			if (broker.getURI().equals(brokerID)) {
				return broker;
			}
		}

		return null;
	}

	/**
	 * 
	 * Returns Broker object which matches given name
	 * 
	 * @param brokerName
	 * @return
	 */
	private Broker findBrokerByName(String brokerName) {
		for (Broker broker : config.getTopology().getBroker()) {
			if (broker.getName().equals(brokerName)) {
				return broker;
			}
		}

		return null;
	}

	/**
	 * 
	 * It removes information about failed node from the system. Broker with brokerID
	 * is informed that its neighbour (neighbourID) doesn't exist in the system any more.
	 * 
	 * @param brokerID
	 * @param neighbourID
	 * @throws ClientException
	 * @throws ParseException
	 */
	private void removeNeigbour(String brokerID, String neighbourID)
			throws ClientException, ParseException {
		advertise(MessageFactory
				.createAdvertisementFromString("[class,eq,'BROKER_CONTROL'],[brokerID,isPresent,''],[command,str-contains,'-'],[broker,isPresent,''],[fromID,isPresent,''],[fromURI,isPresent,'']"));

		publish(MessageFactory
				.createPublicationFromString("[class,BROKER_CONTROL],[brokerID,'"
						+ brokerID
						+ "'],[fromID,'"
						+ neighbourID
						+ "'],[command,'OVERLAY-SHUTDOWN_REMOTEBROKER']"));
	}

	/**
	 * 
	 * Informs all the neighbours of the given broker that it doesn't exist any more.
	 * First it checks directly what neigbhours provided broker had. Next it goes through
	 * whole broker list and checks if any of them has failedBroker as a neighbour.
	 * 
	 * @param failedBroker
	 * @throws ClientException
	 * @throws ParseException
	 */
	private void removeBroker(Broker failedBroker) throws ClientException,
			ParseException {

		for (String neighbour : failedBroker.getNeighbours().getNeighbour()) {
			removeNeigbour(findBrokerByName(neighbour).getURI(),
					failedBroker.getURI());
		}

		for (Broker broker : config.getTopology().getBroker()) {
			for (String neighbour : broker.getNeighbours().getNeighbour()) {
				if (neighbour.equals(failedBroker.getName())) {
					removeNeigbour(broker.getURI(),
							failedBroker.getURI());
				}
			}
		}
	}

	/**
	 * 
	 * Depending on the 'option' parameter it either switches on or off
	 * global failure detection
	 * 
	 * @param option - switches on/off global failure detection
	 * @throws ClientException
	 * @throws ParseException
	 */
	private void publishGlobalFD(boolean option) throws ClientException,
			ParseException {

		advertise(MessageFactory
				.createAdvertisementFromString("[class,eq,GLOBAL_FD],[flag,isPresent,'TEXT']"));
		publish(MessageFactory
				.createPublicationFromString("[class,GLOBAL_FD],[flag,'"
						+ option + "']"));
	}

	/**
	 * 
	 * Subscribe to heartbeats published by brokers
	 * 
	 * @throws ClientException
	 * @throws ParseException
	 */
	private void subscribeToHeartbeats() throws ClientException, ParseException {
		subscribe(MessageFactory.createSubscriptionFromString("[class,eq,"
				+ HeartbeatSubscriber.MESSAGE_CLASS + "],"
				+ "[detectorID,isPresent,'TEXT'],"
				+ "[detectedID,isPresent,'TEXT']," + "[type,isPresent,'TEXT']"));
	}

	// invoked each time message arrives
	@Override
	public void processMessage(Message msg) {

		if (msg instanceof PublicationMessage) {
			pub = ((PublicationMessage) msg).getPublication();
			header = pub.getPairMap();
			msgType = header.get("class").toString();

			// heartbeat failure message has arrived
			if (msgType.equals(HeartbeatSubscriber.MESSAGE_CLASS)) {
				// ID of the failed broker
				failedBrokerID = header.get("detectedID").toString();
				Broker broker = findBrokerByID(failedBrokerID);
				
				System.err.println("[RecoverySys] Broker " + failedBrokerID
						+ " has failed. Trying to restart it...");
				clientLogger.info("Broker " + failedBrokerID
						+ " has failed. Trying to restart it...");

				// try {
				// removeNeigbour("rmi://188.193.163.89:5555/uno",
				// "rmi://188.193.163.89:5556/dos");
				// removeNeigbour("rmi://188.193.163.89:5558/tres",
				// "rmi://188.193.163.89:5556/dos");
				// } catch (ClientException | ParseException e) {
				// e.printStackTrace();
				// }
				// try to restart failed node
				// restartBroker(failedBrokerID);
				
				// inform brokers about failed broker. Let them remove it from their routing tables
				try {
					removeBroker(broker);
				} catch (ClientException e) {
					clientLogger.error("Other brokers cannot remove information about failed one.", e);
					e.printStackTrace();
				} catch(ParseException e){
					clientLogger.error("Other brokers cannot remove information about failed one.", e);
					e.printStackTrace();
				}
				
				try {
					// try to restart failed broker
					remoteExec.restartBroker(broker);
				} catch (RemoteExecException e) {
					clientLogger.error("Cannot restart failed broker - "+broker.getURI());
					try {
						// get backup broker to replace failed one
						broker = getBackupNode(broker);
						if (broker != null) {
							// start the replacement
							remoteExec.startBroker(broker);
						} else {
							clientLogger.error("No more backup nodes to use");
							System.out
									.println("RecoverySystem: No more backup nodes - system cannot recover "
											+ failedBrokerID);
						} 
					} catch (RemoteExecException e1) {
						System.err.println("Backup node - "+broker.getURI()+" cannot be started");
						clientLogger.error("Backup node - "+broker.getURI()+" cannot be started", e1);
						e1.printStackTrace();
					}
				}

				readvertiseAll();
				resubscribeAll();

				System.out.println("[RecoverySys] Broker " + failedBrokerID
						+ " successfully replaced with the "+broker.getURI()+" broker.");
				clientLogger.info("Broker "+failedBrokerID+ " successfully replaced with the "+broker.getURI()+" broker.");
			}
		}
	}

	// just for testing
	private void restartBroker(String brokerURI) {
		BrokerCore brokerCore;
		try {
			brokerCore = new BrokerCore(
					"-uri rmi://188.193.163.89:5588/dos -n rmi://188.193.163.89:5555/uno rmi://188.193.163.89:5558/tres");
			brokerCore.initialize();
		} catch (BrokerCoreException e1) {
			e1.printStackTrace();
		}
	}

	@Override
	public void registerClient(CSClient client) throws RemoteException {
		registeredClients.add(client);
	}

	@Override
	public void deregisterClient(CSClient client) throws RemoteException {
		registeredClients.remove(client);
	}

	/**
	 * Forces all registered clients to resend their advertisements
	 */
	public void readvertiseAll() {
		System.out.println("No. registered clients: "
				+ registeredClients.size());
		for (CSClient client : registeredClients)
			try {
				client.resendAdvertisements();
			} catch (Exception e) {
				clientLogger.error(client.toString()+" cannot readvertise", e);
				System.err.println("Client cannot readvertise!");
				e.printStackTrace();
			}
	}

	/**
	 * Forces all registered clients to resend their subscriptions
	 */
	public void resubscribeAll() {
		for (CSClient client : registeredClients)
			try {
				client.resendSubscriptions();
			} catch (Exception e) {
				clientLogger.error(client.toString()+" cannot resubscribe", e);
				System.err.println("Client cannot resubscribe!");
				e.printStackTrace();
			}
	}

	/**
	 * Registers service under the name "RecoverySystem" in the RMI registry
	 */
	protected void exportToRegistry() {
		try {
			IRecoverySys recSysStub = (IRecoverySys) UnicastRemoteObject
					.exportObject(this, 0);
			Registry registry = LocateRegistry.getRegistry();
			registry.bind("RecoverySystem", recSysStub);
			System.out.println("Recovery system added to registry!");
		} catch (Exception e) {
			clientLogger.error("Cannot register recovery service in registry", e);
			System.err.println("Cannot register recovery service in registry!");
			e.printStackTrace();
		}
	}

	public static void main(String[] args) throws ClientException,
			ParseException, JAXBException {
		RecoverySystem rs = new RecoverySystem("recoverySystem");
	}

}