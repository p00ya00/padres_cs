package ca.utoronto.msrg.padres.test.configService;

import java.net.InetAddress;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;
import ca.utoronto.msrg.padres.broker.brokercore.BrokerConfig;
import ca.utoronto.msrg.padres.broker.brokercore.BrokerCore;
import ca.utoronto.msrg.padres.broker.brokercore.BrokerCoreException;
import ca.utoronto.msrg.padres.client.Client;
import ca.utoronto.msrg.padres.client.ClientConfig;
import ca.utoronto.msrg.padres.client.ClientException;
import ca.utoronto.msrg.padres.common.message.Advertisement;
import ca.utoronto.msrg.padres.common.message.AdvertisementMessage;
import ca.utoronto.msrg.padres.common.message.MessageDestination;
import ca.utoronto.msrg.padres.common.message.Publication;
import ca.utoronto.msrg.padres.common.message.PublicationMessage;
import ca.utoronto.msrg.padres.common.message.Subscription;
import ca.utoronto.msrg.padres.common.message.SubscriptionMessage;
import ca.utoronto.msrg.padres.common.message.parser.MessageFactory;
import ca.utoronto.msrg.padres.common.message.parser.ParseException;
import ca.utoronto.msrg.padres.configService.CSClient;
import ca.utoronto.msrg.padres.configService.CSClientImpl;
import ca.utoronto.msrg.padres.configService.IRecoverySys;
import ca.utoronto.msrg.padres.configService.RecoverySystem;
import ca.utoronto.msrg.padres.configService.SSHConnection;
import ca.utoronto.msrg.padres.configService.schema.Backup;
import ca.utoronto.msrg.padres.configService.schema.Broker;
import ca.utoronto.msrg.padres.configService.schema.Config;
import ca.utoronto.msrg.padres.configService.schema.Neighbours;
import ca.utoronto.msrg.padres.configService.schema.Node;
import ca.utoronto.msrg.padres.configService.schema.Topology;
import ca.utoronto.msrg.padres.test.junit.AllTests;
import ca.utoronto.msrg.padres.test.junit.MessageWatchAppender;
import ca.utoronto.msrg.padres.test.junit.PatternFilter;
import ca.utoronto.msrg.padres.test.junit.tester.GenericBrokerTester;
import ca.utoronto.msrg.padres.test.junit.tester.TesterBrokerCore;
import ca.utoronto.msrg.padres.test.junit.tester.TesterClient;

public class TestRecoverySystem extends TestCase {

	private GenericBrokerTester _brokerTester;

	private BrokerCore broker1;
	private BrokerCore broker2;
	private BrokerCore broker3;
	private BrokerCore broker4;
	private BrokerCore broker5;

	private Client clientA;
	private Client clientB;

	private RecoverySystem recSys;

	private MessageWatchAppender messageWatcher;

	private PatternFilter msgFilter;

	private InetAddress thisIP;

	private Broker broker;

	static boolean setUpIsDone = false;
	
	@Override
	protected void setUp() throws Exception {
		if (setUpIsDone) {
			return;
		} else {
			setUpIsDone = true;

			_brokerTester = new GenericBrokerTester();
			messageWatcher = new MessageWatchAppender();
			msgFilter = new PatternFilter(Client.class.getName());

			thisIP = InetAddress.getLocalHost();
			String hostAddress = thisIP.getHostAddress();

			// build test topology
			Config config = new Config();
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
			
			// prepare backup node
			Backup backup = new Backup();
			Node node = new Node();
			node.setHost(hostAddress);
			node.setPort(5006);
			backup.getNode().add(node);
			config.setBackup(backup);

			RecoverySystem.isDebug(true);
			recSys = new RecoverySystem("recoverySystem", config,
					new SSHConnection());

			// start brokers
			broker = config.getTopology().getBroker().get(0);
			broker1 = new BrokerCore(recSys.createStartCommand(broker));
			broker1.initialize();
			broker = config.getTopology().getBroker().get(1);
			broker2 = new BrokerCore(recSys.createStartCommand(broker));
			broker2.initialize();
			broker = config.getTopology().getBroker().get(2);
			broker3 = new BrokerCore(recSys.createStartCommand(broker));
			broker3.initialize();
			broker = config.getTopology().getBroker().get(3);
			broker4 = new BrokerCore(recSys.createStartCommand(broker));
			broker4.initialize();
			broker = config.getTopology().getBroker().get(4);
			broker5 = new BrokerCore(recSys.createStartCommand(broker));
			broker5.initialize();
			
			recSys.initialize();

			// start clients
			clientA = new CSClientImpl("ClientA");
			clientA.connect(broker1.getBrokerURI());
			clientB = new CSClientImpl("ClientB");
			clientB.connect(broker3.getBrokerURI());

			// connect brokers
			// connectBrokers();

			msgFilter.setPattern(".*Client " + clientA.getClientID()
					+ ".+Publication.+stock.+");
			messageWatcher.addFilter(msgFilter);
		}
	}

	@Override
	protected void tearDown() throws Exception {
		recSys.shutdown();

		clientA.shutdown();
		clientB.shutdown();

		broker1.shutdown();
		broker2.shutdown();
		broker3.shutdown();
		broker4.shutdown();
		broker5.shutdown();
	}

	// protected BrokerCore createNewBrokerCore(BrokerConfig brokerConfig)
	// throws BrokerCoreException {
	// return new TesterBrokerCore(_brokerTester, brokerConfig);
	// }

//	protected Client createNewClient(ClientConfig newConfig)
//			throws ClientException {
//		return new TesterClient(_brokerTester, newConfig);
//	}

	// protected Publication createConnectPub(String srcBroker, String
	// destBroker) {
	//
	// Publication conPub = MessageFactory.createEmptyPublication();
	//
	// conPub.addPair("class", "BROKER_CONTROL");
	// conPub.addPair("command", "OVERLAY-CONNECT");
	// conPub.addPair("brokerID", srcBroker);
	// conPub.addPair("fromID", srcBroker);
	// conPub.addPair("fromURI", srcBroker);
	// conPub.addPair("broker", destBroker);
	//
	// return conPub;
	// }
	//
	// protected void connectBrokers() throws ClientException, ParseException {
	// clientA.advertise(MessageFactory
	// .createAdvertisementFromString("[class,eq,'BROKER_CONTROL'],[brokerID,isPresent,''],[command,str-contains,'-'],"
	// + "[broker,isPresent,''],[fromID,isPresent,''],[fromURI,isPresent,'']"));
	//
	// clientA.publish(createConnectPub(broker1.getBrokerURI(),
	// broker2.getBrokerURI()));
	// clientA.publish(createConnectPub(broker2.getBrokerURI(),
	// broker3.getBrokerURI()));
	// clientA.publish(createConnectPub(broker3.getBrokerURI(),
	// broker4.getBrokerURI()));
	// clientA.publish(createConnectPub(broker4.getBrokerURI(),
	// broker5.getBrokerURI()));
	// clientA.publish(createConnectPub(broker5.getBrokerURI(),
	// broker1.getBrokerURI()));
	// }

//	public void testBrokersCorrectlyConnected() throws Exception {
//		// clientB is publisher, clientA is subscriber
//		MessageDestination mdA = clientA.getClientDest();
//		MessageDestination mdB = clientB.getClientDest();
//		Advertisement adv = MessageFactory
//				.createAdvertisementFromString("[class,eq,'stock'],[price,=,100]");
//		AdvertisementMessage advMsg = new AdvertisementMessage(adv,
//				broker1.getNewMessageID(), mdB);
//		broker1.routeMessage(advMsg, MessageDestination.INPUTQUEUE);
//
//		Subscription sub = MessageFactory
//				.createSubscriptionFromString("[class,eq,'stock'],[price,=,100]");
//		SubscriptionMessage subMsg = new SubscriptionMessage(sub,
//				broker1.getNewMessageID(), mdA);
//		broker1.routeMessage(subMsg, MessageDestination.INPUTQUEUE);
//
//		Publication pub = MessageFactory
//				.createPublicationFromString("[class,'stock'],[price,100]");
//		PublicationMessage pubMsg = new PublicationMessage(pub,
//				broker3.getNewMessageID(), mdB);
//		broker3.routeMessage(pubMsg, MessageDestination.INPUTQUEUE);
//
//		System.out.println("zzz " + pub);
//
//		// waiting for the message to be received
//		messageWatcher.getMessage();
//		Publication expectedPub = clientA.getCurrentPub();
//		System.out.println("aaa " + expectedPub);
//		assertTrue(
//				"The publication:[class,'stock'],[price,100] should be matched at clientB, but not",
//				expectedPub.equalVals(pub));
//	}

	public void testClientsRegisteredSuccessfully() {
		assertTrue("Two clients should be registered to the service", recSys.getRegClientsNumber()==2);
	}

	public void testDeregisterClient() {
		
	}
//
//	public void testResendAdvertisements() {
//
//	}
//
//	public void testResendSubscriptions() {
//
//	}
//
//	public void recoverBroker() {
//
//	}

	public void registerServiceInRMI() {
//		Registry registry = LocateRegistry.getRegistry();
//		IRecoverySys recSysStub = (IRecoverySys) registry.lookup("RecoverySystem");
//		CSClient clientStub = (CSClient)UnicastRemoteObject.exportObject(this, 0);
//		recSysStub.registerClient(clientStub);
	}
}
