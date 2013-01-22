package main;

import java.io.File;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import ca.utoronto.msrg.padres.broker.brokercore.BrokerCore;
import ca.utoronto.msrg.padres.broker.brokercore.BrokerCoreException;
import config.Broker;
import config.Topology;

public class CopyOfStartBrokersTest {
	public static void main(String[] args) throws BrokerCoreException {

		BrokerCore brokerCore = null;
		String brokerURI = "";
		int length;
		int counter = 0;

		try {

			File file = new File(Helper.getDeploymentFile());
			JAXBContext jaxbContext = JAXBContext.newInstance(Topology.class);

			Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
			Topology topology = (Topology) jaxbUnmarshaller.unmarshal(file);

			for (Broker broker : topology.getBrokersList()) {
				if (counter != 1) {
					length = (broker.getNeighboursList() == null) ? 0 : broker
							.getNeighboursList().toArray().length;
					System.out.println("\n" + broker.getBrokerInfo()
							+ " neighbours: " + length);

					brokerCore = new BrokerCore("-uri " + broker.getAddress());
					// start broker (for local testing)
					brokerCore.initialize();

					System.out.println("Broker: " + brokerCore.getBrokerID()
							+ " initialized successfully.");
			
				}
//				System.out.println("counter: "+counter);
				counter++;

			}

		} catch (JAXBException e) {
			e.printStackTrace();
		}
	}

}
