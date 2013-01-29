package ca.utoronto.msrg.padres.configService;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface IRecoverySys extends Remote {
	public void registerClient(CSClient client) throws RemoteException;
	public void deregisterClient(CSClient client) throws RemoteException;
}
