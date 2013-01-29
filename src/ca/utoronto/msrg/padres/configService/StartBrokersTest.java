package ca.utoronto.msrg.padres.configService;

import java.io.File;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import ca.utoronto.msrg.padres.broker.brokercore.BrokerCore;
import ca.utoronto.msrg.padres.broker.brokercore.BrokerCoreException;
import ca.utoronto.msrg.padres.configService.schema.Broker;
import ca.utoronto.msrg.padres.configService.schema.Config;

public class StartBrokersTest {
	public static void main(String[] args) throws BrokerCoreException {

		BrokerCore brokerCore = null;

		try {
			File file = new File(Helper.getDeploymentFile());
			JAXBContext jaxbContext = JAXBContext.newInstance(Config.class);

			Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
			Config config = (Config) jaxbUnmarshaller.unmarshal(file); 

			for (Broker broker : config.getTopology().getBroker()) {
				brokerCore = new BrokerCore("-uri " + broker.getAddress());
				// start broker (for local testing)
				brokerCore.initialize();

				System.out.println("Broker: " + brokerCore.getBrokerID()
						+ " initialized successfully.");
			}

		} catch (JAXBException e) {
			e.printStackTrace();
		}
	}

}