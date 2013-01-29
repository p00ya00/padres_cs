package ca.utoronto.msrg.padres.configService;

import RecSys;
import RecSysI;

import java.io.Serializable;
import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ca.utoronto.msrg.padres.broker.brokercore.HeartbeatSubscriber;
import ca.utoronto.msrg.padres.client.Client;
import ca.utoronto.msrg.padres.client.ClientException;
import ca.utoronto.msrg.padres.common.message.Message;
import ca.utoronto.msrg.padres.common.message.Publication;
import ca.utoronto.msrg.padres.common.message.PublicationMessage;
import ca.utoronto.msrg.padres.common.message.parser.MessageFactory;
import ca.utoronto.msrg.padres.common.message.parser.ParseException;

public class RecoverySystem extends Client implements IRecoverySys {

	private Publication pub;
	private Map<String, Serializable> header;
	private String msgType, failureBrokerID;
	// stub of clients
	private List<CSClient> registeredClients = new ArrayList<CSClient>();

	public RecoverySystem(String id) throws ClientException {
		super(id);
	}

	public RecoverySystem(String id, String brokerURI) throws ClientException, ParseException {
		super(id);
		connect(brokerURI);
		publishGlobalFD();
		subscribeToHeartbeats();
		exportToRegistry();
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
				// try to restart failed node
				restartBroker(failureBrokerID);
				readvertiseAll();
				resubscribeAll();
				System.out.println("failureBrokerID: " + failureBrokerID);
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
	
	public void readvertiseAll()
	{
		for(CSClient client: registeredClients)
			try {
				client.resendAdvertisements();
			} catch (Exception e)
			{
				System.err.println("Client cannot readvertise!");
				e.printStackTrace();
			}
	}
	
	public void resubscribeAll()
	{
		for(CSClient client: registeredClients)
			try {
				client.resendSubscriptions();
			} catch (Exception e)
			{
				System.err.println("Client cannot resubscribe!");
				e.printStackTrace();
			}
	}
	
	protected void exportToRegistry()
	{
		try 
		{
			IRecoverySys recSysStub = (IRecoverySys)UnicastRemoteObject.exportObject(this, 0);
			Registry registry = LocateRegistry.getRegistry();
			registry.bind("RecoverySystem", recSysStub);
	        System.err.println("Recovery system added to registry!");
		} 
		catch (Exception e) 
		{
			System.err.println("Cannot register recovery service in registry!");
			e.printStackTrace();
		}
	}
}