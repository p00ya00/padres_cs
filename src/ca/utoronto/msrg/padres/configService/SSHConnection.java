package ca.utoronto.msrg.padres.configService;

import java.io.ByteArrayOutputStream;
import java.rmi.RemoteException;
import java.util.List;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import ca.utoronto.msrg.padres.configService.schema.*;

import com.jcraft.jsch.*;

/*
 * TO DO:
 * 
 * - create -n parameter
 */

public class SSHConnection {
	
	public void startBroker(Broker broker) throws RemoteExecutionException
	{
		String command = createStartBrokerCommand(broker);
		System.out.println("Starting broker " + broker.getUsername() + "@" + broker.getHost());
		int exitCode = executeCommand(command, broker);
		if(outputStream.size() > 0)
			 System.out.println(outputStream.toString());
		if(errorStream.size() > 0)
		 	System.out.println("Starting broker failed!\n" + errorStream.toString());
		System.out.println("exit-status: " + exitCode);
	}
	
	public void stopBroker(Broker broker) throws RemoteExecutionException
	{
		String command = createStopBrokerCommand(broker);
		System.out.println("Stoping broker " + broker.getUsername() + "@" + broker.getHost());
		int exitCode = executeCommand(command, broker);
		if(outputStream.size() > 0)
			 System.out.println(outputStream.toString());
		if(errorStream.size() > 0)
		 	System.out.println("Stoping broker failed!\n" + errorStream.toString());
		System.out.println("exit-status: " + exitCode);
	}
	
	protected int executeCommand(String command, Broker broker) throws RemoteExecutionException
	{
		outputStream = new ByteArrayOutputStream();
		errorStream = new ByteArrayOutputStream();
		int exitCode;
		JSch jsch = new JSch();
		try {
			Session session= jsch.getSession(broker.getUsername(), broker.getHost(), 22);
			session.setPassword(broker.getPassword());
			session.setConfig("StrictHostKeyChecking", "no");
			session.connect();
			Channel channel=session.openChannel("exec");
			((ChannelExec)channel).setCommand(command);
			channel.setInputStream(null);
			((ChannelExec)channel).setErrStream(errorStream);
			((ChannelExec)channel).setOutputStream(outputStream);
			channel.connect();
			while(true)
			 {
				 Thread.sleep(1000);
				 if(channel.getExitStatus() != -1)
					 break;
			 }
			channel.disconnect();
			session.disconnect();
			exitCode = channel.getExitStatus();
		} catch (JSchException e) {
			RemoteExecutionException exception = new RemoteExecutionException(e.getMessage());
			exception.setStackTrace(e.getStackTrace());
			throw exception;
		} catch (InterruptedException e) {
			RemoteExecutionException exception = new RemoteExecutionException(e.getMessage());
			exception.setStackTrace(e.getStackTrace());
			throw exception;
		}
		
		return exitCode;
	}
	
	public void restartBroker(Broker broker) throws RemoteExecutionException
	{
		stopBroker(broker);
		startBroker(broker);
	}

	public String createStartBrokerCommand(Broker broker)
	{
		String command = "startbroker -uri " + broker.getType() + "://" 
						+ broker.getHost() + ":" + broker.getPort() 
						+ "/" + broker.getName() + " ";
		List<String> neighbours = broker.getNeighbours().getNeighbour(); 
		if(neighbours != null && !neighbours.isEmpty())
			command += "-n";
		
		if(broker.getParams() != null && broker.getParams().getParam() != null)
			for(Param p : broker.getParams().getParam())
				command += "-" + p.getName() + " " + p.getValue() + " ";
		
		return command;
	}
	
	public String createStopBrokerCommand(Broker broker)
	{
		String command = "stopbroker " + broker.getName();
		
		return command;
	}
	
	public String getExecutionOutput()
	{
		if(outputStream != null && outputStream.size() > 0)
			return outputStream.toString();
		return "";
	}
	
	public String getExecutionErrorOutput()
	{
		if(errorStream != null && errorStream.size() > 0)
			return errorStream.toString();
		return "";
	}
	
	private ByteArrayOutputStream outputStream = null;
	private ByteArrayOutputStream errorStream = null;
}
