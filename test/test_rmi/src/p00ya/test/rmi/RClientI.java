package p00ya.test.rmi;

import java.io.Serializable;
import java.rmi.Remote;
import java.rmi.RemoteException;

public interface RClientI extends Remote, Serializable
{
	public void readvertise() throws RemoteException;
	public void resubscribe() throws RemoteException;
}