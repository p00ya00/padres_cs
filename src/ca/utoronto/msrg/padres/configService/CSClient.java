package ca.utoronto.msrg.padres.configService;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.io.Serializable;

import ca.utoronto.msrg.padres.client.ClientException;

public interface CSClient extends Remote, Serializable {
	public void resendAdvertisements() throws RemoteException, ClientException;
	public void resendSubscriptions() throws RemoteException, ClientException;
}
