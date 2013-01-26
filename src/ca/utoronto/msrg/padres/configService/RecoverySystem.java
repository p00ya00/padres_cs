package ca.utoronto.msrg.padres.configService;

import java.io.Serializable;
import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashSet;
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

public class RecoverySystem extends Client {

	private Publication pub;
	private Map<String, Serializable> header;
	private String msgType, failureBrokerID;
	private Set<String> clients;

	public RecoverySystem(String id) throws ClientException {
		super(id);
	}

	public RecoverySystem(String id, String brokerURI) throws ClientException,
			ParseException {
		super(id);

		connect(brokerURI);
		publishGlobalFD();
		subscribeToHeartbeats();

		clients = new HashSet<String>();
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
	
//	private void resubscribeAll(){
//		
//	}
//	
//	private void reAdvartiseAll(){
//		
//	}

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

}