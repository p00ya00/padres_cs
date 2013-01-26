package ca.utoronto.msrg.padres.configService;

import java.rmi.Remote;
import java.rmi.RemoteException;

import ca.utoronto.msrg.padres.client.ClientException;

public interface CSClient extends Remote{
	public void resendAdvertisements() throws RemoteException, ClientException;
	public void resendSubscriptions() throws RemoteException, ClientException;
}
