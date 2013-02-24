package ca.utoronto.msrg.padres.test.configService;

import static org.junit.Assert.*;

import java.io.File;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import ca.utoronto.msrg.padres.configService.*;
import ca.utoronto.msrg.padres.configService.schema.*;
import ca.utoronto.msrg.padres.client.*;

/* sample deployment file:
 
 <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<config>
<topology>
	 <broker name="broker1">
	     <type>rmi</type>
	     <params>
	         <param name="rl" value="50" />
	     </params> 
	     <host>127.0.0.1</host> 
	     <port>5555</port>
	     <username>p00ya</username>
	     <password>papoola874</password>
	     <neighbours>
	         <neighbour>broker2</neighbour>
	         <neighbour>broker3</neighbour>
	     </neighbours>
     </broker>
     <broker name="broker2">
	     <type>rmi</type>
	     <params>
	     </params> 
	     <host>127.0.0.1</host> 
	     <port>5556</port>
	     <username>p00ya</username>
	     <password>papoola874</password>
	     <neighbours>
	     </neighbours>
     </broker>
	 <broker name="broker3">
	     <type>rmi</type>
	     <params>
	     </params> 
	     <host>127.0.0.1</host> 
	     <port>5557</port>
	     <username>p00ya</username>
	     <password>papoola874</password>
	     <neighbours>
		 	<neighbour>broker2</neighbour>
	     </neighbours>
     </broker>
</topology>	     
<backup>
    <node>
         <host>188.193.161.115</host> 
	     <port>5522</port>
	     <username>chris</username>
	     <password>11pass123</password>
    </node>
    <node>
         <host>188.193.161.115</host> 
	     <port>5522</port>
	     <username>chris</username>
	     <password></password>
    </node>
</backup>
</config>
 
 */

public class TestRemoteExecution {
	
	@BeforeClass
	public static void parseConfiguration()
	{
		File file = new File("src" + s + "ca" + s + "utoronto" + s + "msrg" + s 
				+ "padres" + s + "test" + s + "configService" + s + "sample_deployment.xml");
		JAXBContext jaxbContext = null; 
		try {
			jaxbContext = JAXBContext.newInstance(Config.class);
			Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
			config = (Config)jaxbUnmarshaller.unmarshal(file);
		} catch(JAXBException e) {
			System.err.println("Cannot start topology service. Missing or corrupted deployment file!");
			e.printStackTrace();
			System.exit(1);
		}		
	}
	
	@Before
	public void createSSHConnection()
	{
		ssh = new SSHConnection(config);
	}
	
	@Test
	public void testCreateStopBrokerCommand()
	{
		String command = ssh.createStopBrokerCommand(config.getTopology().getBroker().get(1));
		assertEquals(command, "stopbroker broker2");
	}
	
	@Test
	public void testCreateStartBrokerCommandNoNeighbour()
	{
		String command = ssh.createStartBrokerCommand(config.getTopology().getBroker().get(1));
		assertEquals(command.trim(), "startbroker -uri rmi://127.0.0.1:5556/broker2");
	}
	
	@Test
	public void testCreateStartBrokerCommandWithNeighboursNoParams()
	{
		String command = ssh.createStartBrokerCommand(config.getTopology().getBroker().get(2));
		assertEquals(
				command.trim(), 
				"startbroker -uri rmi://127.0.0.1:5557/broker3 -n rmi://127.0.0.1:5556/broker2"
				);
	}
	
	@Test
	public void testCreateStartBrokerCommandWithNeighboursWithParams()
	{
		String command = ssh.createStartBrokerCommand(config.getTopology().getBroker().get(0));
		assertEquals(
				command.trim(), 
				"startbroker -uri rmi://127.0.0.1:5555/broker1 -n rmi://127.0.0.1:5556/broker2,rmi://127.0.0.1:5557/broker3 -rl 50"
				);
	}
	
	@Test
	public void testStartBrokerSuccessful()
	{
		Broker b = config.getTopology().getBroker().get(1);
		try {
			ssh.startBroker(b);
		} catch (RemoteExecutionException e) {
			e.printStackTrace();
			assertTrue("Test failed. Should not throw!", false);
		}
		Client client = null;
		try {
			client = new Client("client1");
			client.connect(b.getType() + "://" + b.getHost() + ":" + b.getPort() + "/" + b.getName());
		} catch (ClientException e) {
			e.printStackTrace();
		}
		assertEquals(client.isConnected(), true);
		assertEquals(ssh.getExitCode(), 0);
		assertEquals(ssh.getExecutionErrorOutput(), "");
		try {
			client.shutdown();
		} catch (ClientException e) {
			e.printStackTrace();
		}
	}
	
	@Test(expected=RemoteExecutionException.class)
	public void testStartBrokerFail() throws RemoteExecutionException
	{
		Broker b = config.getTopology().getBroker().get(0);
		//change broker address so it will fail!
		b.setHost("123.456.78.9");
		ssh.startBroker(b);
	}
	
	private static Config config = null;
	private SSHConnection ssh = null;
	private static String s = File.separator;
}
