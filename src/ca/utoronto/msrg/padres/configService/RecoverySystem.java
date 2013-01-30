package ca.utoronto.msrg.padres.configService;

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

import javax.xml.bind.JAXBException;

import ca.utoronto.msrg.padres.broker.brokercore.HeartbeatSubscriber;
import ca.utoronto.msrg.padres.client.Client;
import ca.utoronto.msrg.padres.client.ClientException;
import ca.utoronto.msrg.padres.common.message.Message;
import ca.utoronto.msrg.padres.common.message.Publication;
import ca.utoronto.msrg.padres.common.message.PublicationMessage;
import ca.utoronto.msrg.padres.common.message.parser.MessageFactory;
import ca.utoronto.msrg.padres.common.message.parser.ParseException;
import ca.utoronto.msrg.padres.configService.schema.Config;

public class RecoverySystem extends Client implements IRecoverySys {

	private Publication pub;
	private Map<String, Serializable> header;
	private String msgType, failureBrokerID, detectorID;
	private Set<String> failedNodes;
	// stub of clients
	private List<CSClient> registeredClients = new ArrayList<CSClient>();

	public RecoverySystem(String id) throws ClientException, ParseException,
			JAXBException {
		super(id);

		Config config = Helper.loadDeploymentFile();
		String brokerURI = config.getTopology().getBroker().get(0).getAddress();
		failedNodes = new HashSet<String>();

		exportToRegistry();
		connect(brokerURI);
		publishGlobalFD();
		subscribeToHeartbeats();
		System.out.println("RecoverySystem has started...");
	}

	public RecoverySystem(String id, String brokerURI) throws ClientException,
			ParseException {
		super(id);
		failedNodes = new HashSet<String>();

		exportToRegistry();
		connect(brokerURI);
		publishGlobalFD();
		subscribeToHeartbeats();
		System.out.println("RecoverySystem has started...");
	}

	private void publishGlobalFD() throws ClientException, ParseException {
		advertise(MessageFactory
				.createAdvertisementFromString("[class,eq,GLOBAL_FD],[flag,isPresent,'TEXT']"));

		publish(MessageFactory
				.createPublicationFromString("[class,GLOBAL_FD],[flag,\"true\"]"));
	}

	private void subscribeToHeartbeats() throws ClientException, ParseException {
		subscribe(MessageFactory.createSubscriptionFromString("[class,eq,"
				+ HeartbeatSubscriber.MESSAGE_CLASS + "],"
				+ "[detectorID,isPresent,'TEXT'],"
				+ "[detectedID,isPresent,'TEXT']," + "[type,isPresent,'TEXT']"));
	}

	private void clearHeartbeatFailure(String detectedID, String detectorID) {
		// try {
		// publish(MessageFactory
		// .createPublicationFromString("[class,HEARTBEAT_MANAGER],[detectedID,'"
		// + detectedID
		// + "'],"
		// + "[type,'FAILURE_CLEARED'],[detectorID,'"
		// + detectorID + "']"));
		// } catch (ClientException | ParseException e) {
		// e.printStackTrace();
		// }
	}

	@Override
	public void processMessage(Message msg) {
		// super.processMessage(msg);

		if (msg instanceof PublicationMessage) {
			pub = ((PublicationMessage) msg).getPublication();
			header = pub.getPairMap();
			msgType = header.get("class").toString();
			System.out.println("Message: " + msg);

			// heartbeat message arrived
			if (msgType.equals(HeartbeatSubscriber.MESSAGE_CLASS)) {
				failureBrokerID = header.get("detectedID").toString();
				// we don't know about this failed node yet
				if (!failedNodes.contains(failureBrokerID)) {

					detectorID = header.get("detectorID").toString();
					failedNodes.add(failureBrokerID);

					// try to restart failed node
					restartBroker(failureBrokerID);
					readvertiseAll();
					resubscribeAll();
					clearHeartbeatFailure(failureBrokerID, detectorID);
					System.out.println("failureBrokerID: " + failureBrokerID);
				}
			}
		}
	}

	private void restartBroker(String brokerURI) {
		System.out.println("restartBroker " + brokerURI);
		// do the stuff to restart (ssh)
	}

	private void addBroker(String neighboursURI) {
		System.out.println("addBroker " + neighboursURI);
		// get broker from the pool and replace old one with the taken one
	}

	@Override
	public void registerClient(CSClient client) throws RemoteException {
		registeredClients.add(client);
	}

	@Override
	public void deregisterClient(CSClient client) throws RemoteException {
		registeredClients.remove(client);
	}

	public void readvertiseAll() {
		System.out.println("No. registered clients: "
				+ registeredClients.size());
		for (CSClient client : registeredClients)
			try {
				client.resendAdvertisements();
			} catch (Exception e) {
				System.err.println("Client cannot readvertise!");
				e.printStackTrace();
			}
	}

	public void resubscribeAll() {
		for (CSClient client : registeredClients)
			try {
				client.resendSubscriptions();
			} catch (Exception e) {
				System.err.println("Client cannot resubscribe!");
				e.printStackTrace();
			}
	}

	protected void exportToRegistry() {
		try {
			IRecoverySys recSysStub = (IRecoverySys) UnicastRemoteObject
					.exportObject(this, 0);
			Registry registry = LocateRegistry.getRegistry();
			registry.bind("RecoverySystem", recSysStub);
			System.err.println("Recovery system added to registry!");
		} catch (Exception e) {
			System.err.println("Cannot register recovery service in registry!");
			e.printStackTrace();
		}
	}

	public static void main(String[] args) throws ClientException,
			ParseException, JAXBException {
		RecoverySystem rs = new RecoverySystem("recoverySystem");
	}

}