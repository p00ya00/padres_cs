package ca.utoronto.msrg.padres.configService;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

import ca.utoronto.msrg.padres.client.Client;
import ca.utoronto.msrg.padres.client.ClientException;
import ca.utoronto.msrg.padres.common.message.AdvertisementMessage;
import ca.utoronto.msrg.padres.common.message.SubscriptionMessage;

/* TODO
 * 
 * deregistration should also be added after shutdown
 * (override Client::shutdown())
 * 
 * pass host (recovery system) address to registry
 */

public class CSClientImpl extends Client implements CSClient {

	private String recSysloc;
	private IRecoverySys recSysStub;
	private CSClient clientStub;

	public CSClientImpl() throws ClientException {
		super(new CSClientConfig());
		CSClientConfig config = new CSClientConfig();
		this.recSysloc = config.getRecoverySystemLoc();
		
		registerToRecoverySys();
	}

	public CSClientImpl(CSClientConfig newConfig) throws ClientException {
		super(newConfig);
		this.recSysloc = newConfig.getRecoverySystemLoc();
		
		registerToRecoverySys();
	}

	public CSClientImpl(String id, CSClientConfig newConfig) throws ClientException {
		super(id, newConfig);
		this.recSysloc = newConfig.getRecoverySystemLoc();
		
		registerToRecoverySys();
	}

	public CSClientImpl(String id) throws ClientException {
		super(id, new CSClientConfig());
		this.recSysloc = new CSClientConfig().getRecoverySystemLoc();
		
		registerToRecoverySys();
	}
	
	protected void registerToRecoverySys()
	{
		System.out.println("Trying to register client to Recovery System...");
		try {
			Registry registry = LocateRegistry.getRegistry(recSysloc, 1099);
			this.recSysStub = (IRecoverySys) registry.lookup("RecoverySystem");
			this.clientStub = (CSClient)UnicastRemoteObject.exportObject(this, 0);
			recSysStub.registerClient(clientStub);
			System.out.println("Client successfully registered to Recovery System at "+recSysloc);
		} catch (Exception e) {
			System.err.println("Client cannot access recovery system!");
			e.printStackTrace();
		}
	}
	
	protected void deregister(){
		try {
			this.recSysStub.deregisterClient(clientStub);
		} catch (RemoteException e) {
			System.err.println("Cannot deregister client from the Recovery System!");
			e.printStackTrace();
		}	
	}
	
	@Override
	public void shutdown() throws ClientException {
		deregister();
		super.shutdown();
	}

	public void resendAdvertisements() throws ClientException {
		for (AdvertisementMessage am : getAdvertisements().values()) {
			advertise(am.getAdvertisement());
		}
	}

	public void resendSubscriptions() throws ClientException {
		for (SubscriptionMessage sm : getSubscriptions().values()) {
			subscribe(sm.getSubscription());
		}
	}
}
