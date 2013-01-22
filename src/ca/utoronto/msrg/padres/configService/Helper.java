package main;

import config.Broker;
import config.Topology;
import ca.utoronto.msrg.padres.common.message.Publication;
import ca.utoronto.msrg.padres.common.message.parser.MessageFactory;

public class Helper {

	private static Publication conPub;
	private static Broker broker;
	private static final String DEPLOYMENT_FILE = "topologyScripts\\deployment.xml";

	public static void createConnectPub(String srcBroker, String destBroker) {

		conPub = MessageFactory.createEmptyPublication();

		conPub.addPair("class", "BROKER_CONTROL");
		conPub.addPair("command", "OVERLAY-CONNECT");
		conPub.addPair("brokerID", srcBroker);
		conPub.addPair("fromID", srcBroker);
		conPub.addPair("fromURI", srcBroker);
		conPub.addPair("broker", destBroker);

	}
	
	public static Publication getPublication(){
		return conPub;
	}	

	public static Broker findBroker(String brokerID, Topology topology) {
		broker = null;

		for (int i = 0; i < topology.getBrokersList().size(); i++) {
			if (topology.getBrokersList().get(i).toString().equals(brokerID)) {
				broker = topology.getBrokersList().get(i);
			}
		}
		return broker;
	}
	
	public static Broker getBroker(){
		return broker;
	}
	
	public static String getDeploymentFile(){
		return DEPLOYMENT_FILE;
	}
}
