package ca.utoronto.msrg.padres.configService;

import java.io.File;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import ca.utoronto.msrg.padres.client.ClientException;
import ca.utoronto.msrg.padres.common.message.parser.ParseException;
import ca.utoronto.msrg.padres.configService.SSHConnection;
import ca.utoronto.msrg.padres.configService.schema.*;

public class TopologyService {
	public static void main(String[] args) {
		if(args.length == 0)
		{
			System.err.println("Cannot start topology service. No deployment file provided!");
			System.err.println("USAGE: topology_service path/to/deployment.file");
			System.exit(1);
		}
		
		Config config = null;
		File file = new File(args[1]);
		JAXBContext jaxbContext = null; 
		try {
			jaxbContext = JAXBContext.newInstance(Config.class);
			Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
			config = (Config)jaxbUnmarshaller.unmarshal(file);
		} catch(JAXBException e) {
			System.err.println("Cannot start topology service. Missing or corrupted deployment file!");
			System.exit(1);
		}
		
		//start brokers
		SSHConnection ssh = new SSHConnection(config);
		for(Broker broker: config.getTopology().getBroker())
		{
			try {
				ssh.startBroker(broker);
			} catch (RemoteExecutionException e) {
				e.printStackTrace();
				System.exit(1);
			}
		}		
		
		try {
			RecoverySystem es = new RecoverySystem("recoverySystem", config);
			es.initialize();
		} catch (ClientException e) {
			System.err.println("Cannot initialize Recovery System");
			e.printStackTrace();
			System.exit(1);
		}		
	}
}