package ca.utoronto.msrg.padres.configService;

import java.io.File;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import ca.utoronto.msrg.padres.common.message.Publication;
import ca.utoronto.msrg.padres.common.message.parser.MessageFactory;
import ca.utoronto.msrg.padres.configService.schema.Broker;
import ca.utoronto.msrg.padres.configService.schema.Config;
import ca.utoronto.msrg.padres.configService.schema.Topology;

public class Helper {

	private static Publication conPub;
	private static final String DEPLOYMENT_FILE = "etc\\deployment.xml";

	public static Publication createConnectPub(String srcBroker, String destBroker) {

		conPub = MessageFactory.createEmptyPublication();

		conPub.addPair("class", "BROKER_CONTROL");
		conPub.addPair("command", "OVERLAY-CONNECT");
		conPub.addPair("brokerID", srcBroker);
		conPub.addPair("fromID", srcBroker);
		conPub.addPair("fromURI", srcBroker);
		conPub.addPair("broker", destBroker);
		
		return conPub;
	}

	public static Broker findBroker(String brokerID, Config config) {
		for (Broker broker : config.getTopology().getBroker()) {
			if (broker.getName().equals(brokerID)) {
				return broker;
			}
		}

		return null;
	}
	
	public static Config loadDeploymentFile() throws JAXBException{
		File file = new File(getDeploymentFile());
		JAXBContext jaxbContext = JAXBContext.newInstance(Config.class);

		Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
		return (Config) jaxbUnmarshaller.unmarshal(file);
	}

	public static String getDeploymentFile() {
		return DEPLOYMENT_FILE;
	}
}
