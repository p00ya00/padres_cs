package p00ya.test.rmi;

import java.io.Serializable;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

public class RClient implements RClientI, Serializable
{
	public RClient(String id, String address)
	{
		clientID = id;
		recSysAddress = address;
	}
	
	public void start()
	{
//		Context namingContext = new InitialContext();
//		RecSys recSys = (RecSys) namingContext.lookup("rmi://localhost/RecoverySystem");
//		recSys.registerClient(this);
		Registry registry = null;
		try {
			registry = LocateRegistry.getRegistry();
			RecSysI recSysStub = (RecSysI) registry.lookup("RecoverySystem");
			recSysStub.registerClient(this);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public static void main(String[] args) throws RemoteException, NamingException
	{
		RClient rc1 = new RClient("cl_a", null);
		rc1.start();
		RClient rc2 = new RClient("cl_b", null);
		rc2.start();
	}
	
	@Override
	public void readvertise() throws RemoteException 
	{
		System.out.println("Client " + this.toString() + " readvertising...");
	}

	@Override
	public void resubscribe() throws RemoteException 
	{
		System.out.println("Client " + this.toString() + " resubscribing...");
	}

	@Override
	public String toString() 
	{
		return clientID;
	}
	
	private String clientID = null;
	private String recSysAddress = null;
}
