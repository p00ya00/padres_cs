package p00ya.test.rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface RClientI extends Remote 
{
	public void readvertise() throws RemoteException;
	public void resubscribe() throws RemoteException;
}