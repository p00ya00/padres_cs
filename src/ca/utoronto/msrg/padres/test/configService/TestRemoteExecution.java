package ca.utoronto.msrg.padres.test.configService;

import static org.junit.Assert.*;

import java.io.File;
import java.util.List;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import ca.utoronto.msrg.padres.common.message.Advertisement;
import ca.utoronto.msrg.padres.common.message.MessageDestination;
import ca.utoronto.msrg.padres.common.message.Publication;
import ca.utoronto.msrg.padres.common.message.Subscription;
import ca.utoronto.msrg.padres.common.message.parser.MessageFactory;
import ca.utoronto.msrg.padres.common.message.parser.ParseException;
import ca.utoronto.msrg.padres.configService.*;
import ca.utoronto.msrg.padres.configService.schema.*;
import ca.utoronto.msrg.padres.client.*;
import ca.utoronto.msrg.padres.test.junit.MessageWatchAppender;
import ca.utoronto.msrg.padres.test.junit.PatternFilter;

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
		Broker b = new Broker();
		b.setPort(8888);
		b.setHost("123.456.78.9");
		b.setName("fakeBroker");
		b.setType("rmi");
		b.setPassword("123456");
		b.setUsername("admin");
		ssh.startBroker(b);
	}
	
	@Test
	public void testTopology()
	{
		List<Broker> brokers = config.getTopology().getBroker();
		for(Broker b: brokers)
			try {
				ssh.startBroker(b);
			} catch (RemoteExecutionException e) {
				e.printStackTrace();
				assertTrue("Test failed. Cannot start " + b.getName(), false);
			}
		Client client1 = null, client2 = null;
		Broker c1b = brokers.get(0);
		Broker c2b = brokers.get(1);
		String c1bURI = c1b.getType() + "://" + c1b.getHost() + ":" + c1b.getPort() + "/" + c1b.getName();
		String c2bURI = c2b.getType() + "://" + c2b.getHost() + ":" + c2b.getPort() + "/" + c2b.getName();
		try {
			client1 = new Client("client1");
			client2 = new Client("client2");
			client1.connect(c1bURI);
			client2.connect(c2bURI);
		} catch (ClientException e) {
			e.printStackTrace();
		}
		assertEquals(client1.isConnected(), true);
		assertEquals(client2.isConnected(), true);
/*		
		try {
			Advertisement adv = MessageFactory.createAdvertisementFromString("[class,eq,'stock'],[price,=,100.3]");
			client1.advertise(adv, c1bURI);
			Subscription sub = MessageFactory.createSubscriptionFromString("[class,eq,'stock'],[price,=,100.3]");
			client2.subscribe(sub, c2bURI);
			Publication pub = MessageFactory.createPublicationFromString("[class,'stock'],[price,100.3]");
			client1.publish(pub, c1bURI);
			MessageWatchAppender messageWatcher = new MessageWatchAppender();
			PatternFilter msgFilter = new PatternFilter(Client.class.getName());
			msgFilter.setPattern(".*Client " + client1.getClientID() + ".+Publication.+stock.+");
			messageWatcher.addFilter(msgFilter);
			messageWatcher.getMessage();
			Publication expectedPub = client1.getCurrentPub();
			assertEquals(pub.equalVals(expectedPub), true);
		} catch (ParseException e) {
			e.printStackTrace();
		} catch (ClientException e) {
			e.printStackTrace();
		}
*/
	}
	
	private static Config config = null;
	private SSHConnection ssh = null;
	private static String s = File.separator;
}
