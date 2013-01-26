package ca.utoronto.msrg.padres.configService;

import ca.utoronto.msrg.padres.client.Client;
import ca.utoronto.msrg.padres.client.ClientException;
import ca.utoronto.msrg.padres.common.message.AdvertisementMessage;
import ca.utoronto.msrg.padres.common.message.SubscriptionMessage;

public class CSClientImpl extends Client implements CSClient {

	public CSClientImpl() throws ClientException {
		super();
	}

	public CSClientImpl(CSClientConfig newConfig) throws ClientException {
		super(newConfig);
		System.out.println("[DEBUG] RecoverySystem: "+newConfig.recoverySystemLoc);
	}

	public CSClientImpl(String id, CSClientConfig newConfig)
			throws ClientException {
		super(id, newConfig);
	}

	public CSClientImpl(String id) throws ClientException {
		super(id);
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
