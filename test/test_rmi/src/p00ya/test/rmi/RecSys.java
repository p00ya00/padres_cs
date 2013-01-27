package p00ya.test.rmi;

import java.rmi.registry.Registry;
import java.rmi.registry.LocateRegistry;
import java.rmi.AccessException;
import java.rmi.AlreadyBoundException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;

public class RecSys implements RecSysI 
{
	public static void main(String[] args) 
	{
		RecSys recSystem = new RecSys();
		RecSysI recSysStub = null;
		Registry registry = null;
		try 
		{
			recSysStub = (RecSysI)UnicastRemoteObject.exportObject(recSystem, 0);
			registry = LocateRegistry.getRegistry();
			registry.bind("RecoverySystem", recSysStub);
	        System.out.println("Server ready. Press any key to send recovery message...");
	        while(true)
	        {
		        System.in.read();
				recSystem.recover();
	        }
		} 
		catch (Exception e) 
		{
			System.err.println("Cannot register recovery service in registry!");
			e.printStackTrace();
		}
	}

	@Override
	public void registerClient(RClientI client) throws RemoteException 
	{
		//RClientI clientStub = (RClientI)UnicastRemoteObject.exportObject(client, 0);
		//clients.add(clientStub);
		clients.add(client);
		System.out.println("Client " + client.toString() + "registered itself!!");
	}
	
	public void recover()
	{
		for(RClientI client: clients)
			try {
				client.readvertise();
			} catch (RemoteException e) 
			{
				e.printStackTrace();
			}
		
		for(RClientI client: clients)
			try {
				client.resubscribe();
			} catch (RemoteException e) {
				e.printStackTrace();
			}
	}
	
	private List<RClientI> clients = new ArrayList<RClientI>();
}
