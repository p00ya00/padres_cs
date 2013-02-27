package ca.utoronto.msrg.padres.configService;

import java.io.Serializable;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import ca.utoronto.msrg.padres.broker.brokercore.BrokerCore;
import ca.utoronto.msrg.padres.broker.brokercore.BrokerCoreException;
import ca.utoronto.msrg.padres.broker.brokercore.HeartbeatSubscriber;
import ca.utoronto.msrg.padres.client.Client;
import ca.utoronto.msrg.padres.client.ClientException;
import ca.utoronto.msrg.padres.common.message.Message;
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
	private SSHConnection remoteExec;
	private Registry registry;
	// URI to which recSys is connected
	private String brokerURI;
	// stub of clients
	private List<CSClient> registeredClients = new ArrayList<CSClient>();

	public static boolean DEBUG = false; 
	public static void isDebug(boolean mode){
		DEBUG = mode;
	}

	/**
	 * 
	 * Constructor. It gets first broker from the Config object.
	 * Resets the counter of the used backup nodes.
	 * 
	 * @param id - recovery system identifier
	 * @param config - Config object with whole topology nodes
	 * @param remoteExec
	 * @throws ClientException
	 * @throws ParseException
	 */
	public RecoverySystem(String id, Config config) throws ClientException {
		super(id);
		
		this.config = config;
		remoteExec = new SSHConnection(config);
		usedBackupNodes = 0;
		this.brokerURI = getBrokerToConnect(config, 0);
	}
	
	/**
	 * 
	 * Initializes the system. Connects to the specified broker.
	 * It registers service in the RMI registry, switches on the global
	 * failure detection option and subscribe to the heartbeats in the system.
	 * 
	 * @throws ClientException
	 * @throws ParseException
	 */
	public void initialize(){
		exportToRegistry();
		try {
			connect(brokerURI);
			publishGlobalFD(true);
			subscribeToHeartbeats();
			
			initLog("recoverySystem");
		} catch (ClientException e1) {
			System.err.println("Cannot initialize Recovery System");
			e1.printStackTrace();
			System.exit(1);
		} catch (ParseException e) {
			System.err.println("Cannot initialize Recovery System");
			e.printStackTrace();
			System.exit(1);
		}
		
		System.out.println("RecoverySystem has started...");
	}

	/**
	 * @param config - object with the topology
	 * @param number - number of the broker on the list
	 * @return brokerURI
	 */
	private String getBrokerToConnect(Config config, int number) {
		return getBrokerURI(config.getTopology().getBroker().get(number));
	}
	
	/**
	 * 
	 * Creates URI of the broker
	 * 
	 * @return
	 */
	public String getBrokerURI(Broker broker) {
		return broker.getType() + "://" + broker.getHost() + ":" + broker.getPort() + "/"
				+ broker.getName();
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
			if (getBrokerURI(broker).equals(brokerID)) {
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
			removeNeigbour(getBrokerURI(findBrokerByName(neighbour)),
					getBrokerURI(failedBroker));
		}

		for (Broker broker : config.getTopology().getBroker()) {
			for (String neighbour : broker.getNeighbours().getNeighbour()) {
				if (neighbour.equals(failedBroker.getName())) {
					removeNeigbour(getBrokerURI(broker),
							getBrokerURI(failedBroker));
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
					if(!DEBUG){
						remoteExec.restartBroker(broker);
					} else {
						throw new RemoteExecutionException();
					}					
				} catch (RemoteExecutionException e) {
					clientLogger.error("Cannot restart failed broker - "+getBrokerURI(broker));
					try {
						// get backup broker to replace failed one
						broker = getBackupNode(broker);
						if (broker != null) {
							// start the replacement
							if(!DEBUG){
								remoteExec.startBroker(broker);
							} else {
								startBrokerLocally(broker);
							}							
						} else {
							clientLogger.error("No more backup nodes to use");
							System.out
									.println("RecoverySystem: No more backup nodes - system cannot recover "
											+ failedBrokerID);
						} 
					} catch (RemoteExecutionException e1) {
						System.err.println("Backup node - "+getBrokerURI(broker)+" cannot be started");
						clientLogger.error("Backup node - "+getBrokerURI(broker)+" cannot be started", e1);
						e1.printStackTrace();
					}
				}
			
				// if there were free backup nodes
				if(broker!=null){
					readvertiseAll();
					resubscribeAll();
					
					System.out.println("[RecoverySys] Broker " + failedBrokerID
							+ " successfully replaced with the "+getBrokerURI(broker)+" broker.");
					clientLogger.info("Broker "+failedBrokerID+ " successfully replaced with the "+getBrokerURI(broker)+" broker.");
				}
			}
		}
	}
	
	/**
	 * 
	 * Start BrokerCore with the provided parameters
	 * 
	 * @param broker
	 */
	private void startBrokerLocally(Broker broker){
		BrokerCore brokerCore;
		System.out.println("Starting "+getBrokerURI(broker));
		try {
			brokerCore = new BrokerCore(createStartCommand(broker));
			brokerCore.initialize();
		} catch (BrokerCoreException e) {
			System.err.println("Cannot start - "+getBrokerURI(broker)+" locally");
			clientLogger.error("Cannot start - "+getBrokerURI(broker)+" locally", e);
			e.printStackTrace();
		}
	}
	
	public String createStartCommand(Broker broker){
		String start = "-uri ";
		start += getBrokerURI(broker);
		
		List<String> neighbours = broker.getNeighbours().getNeighbour(); 
		if(!neighbours.isEmpty()){
			start += " -n ";
			for(String neighbour : broker.getNeighbours().getNeighbour()){
				start = start + getBrokerURI(findBrokerByName(neighbour)) + ",";

			}
		}

		for (Broker otherBrokers : config.getTopology().getBroker()) {
			for (String neighbour : otherBrokers.getNeighbours().getNeighbour()) {
				if (neighbour.equals(broker.getName())) {
					start = start + getBrokerURI(findBrokerByName(otherBrokers.getName())) + ",";
				}
			}
		}
	
		return start.substring(0, start.length()-1);
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
	 * @return number of registered clients to the service
	 */
	public int getRegClientsNumber(){
		return registeredClients.size();
	}

	/**
	 * Forces all registered clients to resend their advertisements
	 */
	public void readvertiseAll() {
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
	
	private Registry createRegistry(int port) throws RemoteException{
		return LocateRegistry.createRegistry(port);
	}

	/**
	 * Registers service under the name "RecoverySystem" in the RMI registry
	 */
	protected void exportToRegistry() {
		try {
			registry = createRegistry(1099);
		} catch (RemoteException e2) {
			try {
				registry = LocateRegistry.getRegistry();
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		}
		
		try {
			IRecoverySys recSysStub = (IRecoverySys) UnicastRemoteObject
					.exportObject(this, 0);
			registry.bind("RecoverySystem", recSysStub);
			System.out.println("Recovery system added to registry!");
		} catch (Exception e) {
			clientLogger.error("Cannot register recovery service in registry", e);
			System.err.println("Cannot register recovery service in registry!");
			e.printStackTrace();
		}
	}
	
	@Override
	public void shutdown() throws ClientException {	
		try {
			registry.unbind("RecoverySystem");
			UnicastRemoteObject.unexportObject(this, true);			
		} catch (RemoteException e) {
			clientLogger.error("Cannot unregister recovery service in registry", e);
			e.printStackTrace();
		} catch (NotBoundException e) {
			clientLogger.error("Cannot unregister recovery service in registry", e);
			e.printStackTrace();
		}
		super.shutdown();	
	}

}