package ca.utoronto.msrg.padres.configService;

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
import ca.utoronto.msrg.padres.configService.schema.Broker;
import ca.utoronto.msrg.padres.configService.schema.Config;

public class TopologyService {

	public static void main(String[] args) throws BrokerCoreException,
			ClientException, ParseException, JAXBException {

		Broker neighbourBroker;
		String neighbourUri = "", brokerURI = "";
		Advertisement initialAdv = null;

		try {
			Config config = Helper.loadDeploymentFile();

			initialAdv = MessageFactory
					.createAdvertisementFromString("[class,eq,'BROKER_CONTROL'],[brokerID,isPresent,''],[command,str-contains,'-'],"
							+ "[broker,isPresent,''],[fromID,isPresent,''],[fromURI,isPresent,'']");

			Client client = new Client("ClientTS");

			for (Broker broker : config.getTopology().getBroker()) {
				// length = (broker.getNeighboursList() == null) ? 0 : broker
				// .getNeighboursList().toArray().length;
				System.out.println("\n" + broker.getAddress() + " neighbours: "
						+ broker.getNeighbours().getNeighbour().size());

				brokerURI = broker.getAddress();

				client.connect(brokerURI);
				client.advertise(initialAdv);

				for (String neighbour : broker.getNeighbours().getNeighbour()) {
					neighbourBroker = Helper.findBroker(neighbour, config);

					neighbourUri = neighbourBroker.getAddress();
					client.publish(Helper.createConnectPub(brokerURI,
							neighbourUri));

					System.out.println(neighbour + " - connected");
				}

				client.disconnect(brokerURI);
			}

		} catch (JAXBException | ClientException e) {
			e.printStackTrace();
		}

		// start recovery system
//		RecoverySystem es = new RecoverySystem("recoverySystem", brokerURI);

	}

}