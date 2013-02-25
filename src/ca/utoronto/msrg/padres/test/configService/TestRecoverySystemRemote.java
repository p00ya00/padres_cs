package ca.utoronto.msrg.padres.test.configService;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Iterator;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import ca.utoronto.msrg.padres.broker.brokercore.BrokerCore;
import ca.utoronto.msrg.padres.client.Client;
import ca.utoronto.msrg.padres.client.ClientException;
import ca.utoronto.msrg.padres.common.message.Advertisement;
import ca.utoronto.msrg.padres.common.message.AdvertisementMessage;
import ca.utoronto.msrg.padres.common.message.Publication;
import ca.utoronto.msrg.padres.common.message.Subscription;
import ca.utoronto.msrg.padres.common.message.SubscriptionMessage;
import ca.utoronto.msrg.padres.common.message.parser.MessageFactory;
import ca.utoronto.msrg.padres.common.message.parser.ParseException;
import ca.utoronto.msrg.padres.configService.CSClientImpl;
import ca.utoronto.msrg.padres.configService.RecoverySystem;
import ca.utoronto.msrg.padres.configService.RemoteExecutionException;
import ca.utoronto.msrg.padres.configService.SSHConnection;
import ca.utoronto.msrg.padres.configService.schema.Backup;
import ca.utoronto.msrg.padres.configService.schema.Broker;
import ca.utoronto.msrg.padres.configService.schema.Config;
import ca.utoronto.msrg.padres.configService.schema.Neighbours;
import ca.utoronto.msrg.padres.configService.schema.Node;
import ca.utoronto.msrg.padres.configService.schema.Topology;
import ca.utoronto.msrg.padres.test.junit.MessageWatchAppender;
import ca.utoronto.msrg.padres.test.junit.PatternFilter;

public class TestRecoverySystemRemote {
	private SSHConnection ssh;
	private Config config;

	private Client clientA;
	private Client clientB;

	private RecoverySystem recSys;

	private MessageWatchAppender messageWatcher;

	private PatternFilter msgFilter;

	private InetAddress thisIP;

	private Broker broker;

	@Before
	public void setUp() throws Exception {
		messageWatcher = new MessageWatchAppender();
		msgFilter = new PatternFilter(Client.class.getName());

		thisIP = InetAddress.getLocalHost();
		String hostAddress = thisIP.getHostAddress();

		// build test topology
		config = new Config();
		Topology topology = new Topology();
		for (int i = 0; i < 5; i++) {
			broker = new Broker();
			broker.setHost(hostAddress);
			broker.setPort(5000 + i);
			broker.setName("broker" + (i + 1));
			broker.setType("rmi");

			if (i == 4) {
				Neighbours neighbours = new Neighbours();
				neighbours.getNeighbour().add("broker1");
				broker.setNeighbours(neighbours);
			} else {
				Neighbours neighbours = new Neighbours();
				neighbours.getNeighbour().add("broker" + (i + 2));
				broker.setNeighbours(neighbours);
			}

			topology.getBroker().add(broker);
		}
		config.setTopology(topology);

		// prepare backup nodes
		Backup backup = new Backup();
		Node node1 = new Node();
		node1.setHost(hostAddress);
		node1.setPort(5006);
		backup.getNode().add(node1);
		
		Node node2 = new Node();
		node2.setHost(hostAddress);
		node2.setPort(5007);
		backup.getNode().add(node2);
		
		Node node3 = new Node();
		node3.setHost(hostAddress);
		node3.setPort(5007);
		backup.getNode().add(node3);
		
		config.setBackup(backup);

		recSys = new RecoverySystem("recoverySystem", config);
		
		ssh = new SSHConnection(config);
		for(Broker b: config.getTopology().getBroker())
			ssh.startBroker(b);

		recSys.initialize();

		// start clients
		clientA = new CSClientImpl("ClientA");
		clientA.connect(recSys.getBrokerURI(config.getTopology().getBroker().get(0)));
		clientB = new CSClientImpl("ClientB");
		clientB.connect(recSys.getBrokerURI(config.getTopology().getBroker().get(2)));

		msgFilter.setPattern(".*Client " + clientA.getClientID()
				+ ".+Publication.+stock.+");
		messageWatcher.addFilter(msgFilter);
	}

	@After
	public void tearDown() throws Exception {
		clientA.shutdown();
		clientB.shutdown();
		
		recSys.shutdown();

		for(Broker b: config.getTopology().getBroker())
			ssh.stopBroker(b);
	}

	@Test
	public void recoverBroker1() throws ClientException, ParseException,
			InterruptedException, IOException {
		clientA.advertise(MessageFactory
				.createAdvertisementFromString("[class,eq,'stock'],[price,<,100]"));
		clientB.subscribe(MessageFactory
				.createSubscriptionFromString("[class,eq,'stock'],[price,<,50]"));
		clientA.publish(MessageFactory
				.createPublicationFromString("[class,'stock'],[price,20]"));

		// waiting for the message to be received
		messageWatcher.getMessage();

		try {
			ssh.stopBroker(config.getTopology().getBroker().get(1));
			System.in.read();
		} catch (RemoteExecutionException e) {
			e.printStackTrace();
		}
		messageWatcher.getMessage(20);
		
		Publication pub = MessageFactory
				.createPublicationFromString("[class,'stock'],[price,10]");
		clientA.publish(pub);

		messageWatcher.getMessage();
		Publication expectedPub = clientB.getCurrentPub();
		assertTrue(
				"The publication: [class,'stock'],[price,10] should be matched at clientB",
				expectedPub.equalVals(pub));
	}
}
