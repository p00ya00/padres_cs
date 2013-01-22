package main;

import java.io.File;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import ca.utoronto.msrg.padres.broker.brokercore.BrokerCoreException;
import ca.utoronto.msrg.padres.client.Client;
import ca.utoronto.msrg.padres.client.ClientException;
import ca.utoronto.msrg.padres.common.message.Advertisement;
import ca.utoronto.msrg.padres.common.message.parser.MessageFactory;
import ca.utoronto.msrg.padres.common.message.parser.ParseException;
import ca.utoronto.msrg.padres.tools.padresmonitor.ClientInjectionManager;
import ca.utoronto.msrg.padres.tools.padresmonitor.MonitorFrame;
import config.Broker;
import config.Neighbour;
import config.Topology;

public class TopologyService {

	public static void main(String[] args) throws BrokerCoreException, ClientException, ParseException, JAXBException {

		Broker neighbourBroker;
		String neighbourUri = "", brokerURI = "";
		Advertisement initialAdv = null;
		int length;

		try {

			File file = new File(Helper.getDeploymentFile());
			JAXBContext jaxbContext = JAXBContext.newInstance(Topology.class);

			Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
			Topology topology = (Topology) jaxbUnmarshaller.unmarshal(file);

			try {
				initialAdv = MessageFactory
						.createAdvertisementFromString("[class,eq,'BROKER_CONTROL'],[brokerID,isPresent,''],[command,str-contains,'-'],"
								+ "[broker,isPresent,''],[fromID,isPresent,''],[fromURI,isPresent,'']");
			} catch (ParseException e) {
				e.printStackTrace();
			}

			Client client = new Client("ClientTS");
			
			for (Broker broker : topology.getBrokersList()) {
				length = (broker.getNeighboursList() == null) ? 0 : broker
						.getNeighboursList().toArray().length;
				System.out.println("\n" + broker.getBrokerInfo()
						+ " neighbours: " + length);

				brokerURI = broker.getType() + "://" + broker.getHost() + ":"
						+ broker.getPort() + "/" + broker.getName();
				
				client.connect(brokerURI);
				client.advertise(initialAdv);
				
				if (broker.getNeighboursList() != null) {
					for (Neighbour neighbour : broker.getNeighboursList()) {
						neighbourBroker = Helper.findBroker(
								neighbour.getNeighbourName(), topology);

						if (neighbourBroker != null) {
							neighbourUri = neighbourBroker.getType() + "://"
									+ neighbourBroker.getHost() + ":"
									+ neighbourBroker.getPort() + "/"
									+ neighbourBroker.getName();

							Helper.createConnectPub(brokerURI, neighbourUri);
							client.publish(Helper.getPublication());

							// System.out.println("BrokerURI: " + brokerURI
							// + "; NeighbourURI: " + neighbourUri);
							System.out.println(neighbour.getNeighbourName()
									+ " - connected");
						}
					}
				}

//				client.clearBrokerStates(client.getDefaultBrokerAddress());
				client.disconnect(brokerURI);
				
			}

		} catch (JAXBException | ClientException e) {
			e.printStackTrace();
		}
		
		//start emergency service
		EmergencyService es = new EmergencyService("emergencyService", brokerURI);

	}

}
