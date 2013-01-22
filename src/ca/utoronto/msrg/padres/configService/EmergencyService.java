package main;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.xml.bind.JAXBException;

import ca.utoronto.msrg.padres.broker.brokercore.HeartbeatSubscriber;
import ca.utoronto.msrg.padres.client.Client;
import ca.utoronto.msrg.padres.client.ClientException;
import ca.utoronto.msrg.padres.common.message.Message;
import ca.utoronto.msrg.padres.common.message.Publication;
import ca.utoronto.msrg.padres.common.message.PublicationMessage;
import ca.utoronto.msrg.padres.common.message.parser.MessageFactory;
import ca.utoronto.msrg.padres.common.message.parser.ParseException;

public class EmergencyService extends Client {

	private Publication pub;
	private Map<String, Serializable> header;
	private String msgType, failureBrokerID, restartBroker;
	private Set<String> failedBrokers;

	public EmergencyService(String id) throws ClientException {
		super(id);
	}

	public EmergencyService(String id, String brokerURI)
			throws ClientException, ParseException, JAXBException {
		super(id);

		connect(brokerURI);
		publishGlobalFD();
		subscribeToHeartbeats();
		subscribeToBrokerInfo();
		networkDiscovery();

		failedBrokers = new HashSet<String>();
		System.out.println("EmergencyService has started...");
	}
	
	private void publishGlobalFD() throws ClientException, ParseException {
		advertise(MessageFactory
				.createAdvertisementFromString("[class,eq,GLOBAL_FD],[flag,isPresent,'TEXT']"));

		publish(MessageFactory
				.createPublicationFromString("[class,GLOBAL_FD],[flag,\"true\"]"));
	}

	private void subscribeToBrokerInfo() throws ClientException, ParseException {
		subscribe(MessageFactory
				.createSubscriptionFromString("[class,eq,BROKER_INFO]"));
	}

	private void subscribeToHeartbeats() throws ClientException, ParseException {
		subscribe(MessageFactory.createSubscriptionFromString("[class,eq,"
				+ HeartbeatSubscriber.MESSAGE_CLASS + "],"
				+ "[detectorID,isPresent,'TEXT'],"
				+ "[detectedID,isPresent,'TEXT']," + "[type,isPresent,'TEXT']"));
	}

	private void networkDiscovery() throws ClientException, ParseException {
		advertise(MessageFactory
				.createAdvertisementFromString("[class,eq,NETWORK_DISCOVERY]"));

		publish(MessageFactory
				.createPublicationFromString("[class,NETWORK_DISCOVERY]"));
	}

	private void resendAdvertisements() throws ClientException, ParseException {
//		System.out.println("vvv " + ads.length());

//		advertise(MessageFactory
//				.createAdvertisementFromString("[class,eq,'temp'],[area,eq,'tor'],[value,<,100]"));
	}

	@Override
	public void processMessage(Message msg) {
		// super.processMessage(msg);

		if (msg instanceof PublicationMessage) {
			pub = ((PublicationMessage) msg).getPublication();
			header = pub.getPairMap();
			msgType = header.get("class").toString();
			System.out.println("Message: " + msg);
			
			if (msgType.equals(HeartbeatSubscriber.MESSAGE_CLASS)) {
				failureBrokerID = header.get("detectedID").toString();
				failedBrokers.add(failureBrokerID);
				System.out.println("failureBrokerID: "+failureBrokerID);
				try {
					networkDiscovery();
				} catch (ClientException | ParseException e) {
					e.printStackTrace();
				}
			} else {
				ConcurrentHashMap payload = (ConcurrentHashMap) pub
						.getPayload();
				if (failedBrokers
						.contains(payload.get("Broker URI").toString())) {
					System.out.println("failed: "+payload.get("Broker URI").toString());
					// get neighbours and call restart broker
					restartBroker = payload.get("Broker URI").toString()
							+ " -n " + payload.get("Neighbours").toString();
					restartBroker(restartBroker);
					// if not successful add broker
					addBroker(" -n " + payload.get("Neighbours").toString());

					// System.out.println("adv: "
					// + payload.get("Advertisements").toString()
					// + ", subs: "
					// + payload.get("Subscriptions").toString());

			
//						try {
//							resendAdvertisements();
//						} catch (ClientException | ParseException e) {
//							// TODO Auto-generated catch block
//							e.printStackTrace();
//						}
		
					// resendAdvertisements(ads)
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

}