package ca.utoronto.msrg.padres.configService;

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

	public CSClientImpl() throws ClientException {
		super();
		registerToRecoverySys();
	}

	public CSClientImpl(CSClientConfig newConfig) throws ClientException {
		super(newConfig);
		registerToRecoverySys();
		//System.out.println("[DEBUG] RecoverySystem: "+newConfig.recoverySystemLoc);
	}

	public CSClientImpl(String id, CSClientConfig newConfig) throws ClientException {
		super(id, newConfig);
		registerToRecoverySys();
	}

	public CSClientImpl(String id) throws ClientException {
		super(id);
		registerToRecoverySys();
	}
	
	protected void registerToRecoverySys()
	{
		try {
			Registry registry = LocateRegistry.getRegistry();
			IRecoverySys recSysStub = (IRecoverySys) registry.lookup("RecoverySystem");
			CSClient clientStub = (CSClient)UnicastRemoteObject.exportObject(this, 0);
			recSysStub.registerClient(clientStub);
		} catch (Exception e) {
			System.err.println("Client cannot access recovery system!");
			e.printStackTrace();
		}
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
