package p00ya.test.rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface RecSysI extends Remote 
{
	public void registerClient(RClientI client) throws RemoteException;
}
