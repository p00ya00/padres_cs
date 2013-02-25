package ca.utoronto.msrg.padres.configService;

import java.io.ByteArrayOutputStream;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import ca.utoronto.msrg.padres.configService.schema.*;

import com.jcraft.jsch.*;

/**
 * This class implements an ssh connection. It recieves the unmarshaled 
 * deployment configuration. It can be used to start/stop brokers on
 * remote machines and getting back exit code and output of the execution 
 */
public class SSHConnection {
	
	/**
	 * constructor
	 * 
	 * @param conf : the unmarshaled deployment file
	 */
	public SSHConnection(Config conf)
	{
		config = conf;
	}
	
	/**
	 * starts the given broker on the remote host specified in the 
	 * broker configuration. upon execution, exit code, output and error
	 * output(if any) can be retrieved using the provided methods.
	 * 
	 * @param broker : broker to be started on remote host
	 * 
	 * @throws RemoteExecutionException: if cannot start broker or remote host
	 * doesn't respond.
	 */
	public void startBroker(Broker broker) throws RemoteExecutionException
	{
		String command = createStartBrokerCommand(broker);
		System.out.println("Starting broker " + broker.getUsername() + "@" + broker.getHost());
		exitCode = executeCommand(command, broker);
		if(outputStream.size() > 0)
			 System.out.println(outputStream.toString());
		if(errorStream.size() > 0)
		 	System.out.println("Starting broker failed!\n" + errorStream.toString());
		System.out.println("exit-status: " + exitCode);
	}
	
	/**
	 * Stops the given broker on the host that it runs on.
	 * 
	 * @param broker : broker to be stoped. upon execution, exit code, 
	 * output and error output(if any) can be retrieved using the provided methods.
	 * 
	 * @throws RemoteExecutionException: if cannot start broker or remote host
	 * doesn't respond.
	 */
	public void stopBroker(Broker broker) throws RemoteExecutionException
	{
		String command = createStopBrokerCommand(broker);
		System.out.println("Stoping broker " + broker.getUsername() + "@" + broker.getHost());
		exitCode = executeCommand(command, broker);
		if(outputStream.size() > 0)
			 System.out.println(outputStream.toString());
		if(errorStream.size() > 0)
		 	System.out.println("Stoping broker failed!\n" + errorStream.toString());
		System.out.println("exit-status: " + exitCode);
	}
	
	/**
	 * stop and restart the given broker
	 * 
	 * @param broker
	 * @throws RemoteExecutionException
	 */
	
	public void restartBroker(Broker broker) throws RemoteExecutionException
	{
		stopBroker(broker);
		startBroker(broker);
	}
	
	protected int executeCommand(String command, Broker broker) throws RemoteExecutionException
	{
		outputStream = new ByteArrayOutputStream();
		errorStream = new ByteArrayOutputStream();
		int res;
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
			res = channel.getExitStatus();
		} catch (JSchException e) {
			RemoteExecutionException exception = new RemoteExecutionException(e.getMessage());
			exception.setStackTrace(e.getStackTrace());
			throw exception;
		} catch (InterruptedException e) {
			RemoteExecutionException exception = new RemoteExecutionException(e.getMessage());
			exception.setStackTrace(e.getStackTrace());
			throw exception;
		}
		
		return res;
	}

	public String createStartBrokerCommand(Broker broker)
	{
		String command = "startbroker -uri " + broker.getType() + "://" 
						+ broker.getHost() + ":" + broker.getPort() 
						+ "/" + broker.getName() + " ";
		
		//construct neighbour list
		if(broker.getNeighbours() != null && 
		   broker.getNeighbours().getNeighbour() != null && 
		   !broker.getNeighbours().getNeighbour().isEmpty())
		{
			command += "-n ";
			List<Broker> neighbourList = getNeighbours(broker);
			for(Broker b: neighbourList)
			{
				command += b.getType() + "://" + b.getHost() + ":" + b.getPort() + "/" + b.getName();
				if(b != neighbourList.get(neighbourList.size() - 1))
					command += ",";
			}
		}
		
		if(broker.getParams() != null && broker.getParams().getParam() != null && 
		   !broker.getParams().getParam().isEmpty())
		{
			command += " ";
			for(Param p : broker.getParams().getParam())
				command += "-" + p.getName() + " " + p.getValue() + " ";
		}
		
		return command;
	}
	
	public String createStopBrokerCommand(Broker broker)
	{
		String command = "stopbroker " + broker.getName();
		
		return command;
	}
	
	/**
	 * returns the output(System.out) result of the last executed command 
	 * @return: 
	 */
	public String getExecutionOutput()
	{
		if(outputStream != null && outputStream.size() > 0)
			return outputStream.toString();
		return "";
	}
	
	/**
	 * returns the error output(System.err) result of the last executed command
	 * @return
	 */
	public String getExecutionErrorOutput()
	{
		if(errorStream != null && errorStream.size() > 0)
			return errorStream.toString();
		return "";
	}
	
	/**
	 * returns the exit code resulted from the last executed command.
	 * -1 if no command executed yet through this connection or command
	 * not finished yet
	 * @return
	 */
	public int getExitCode()
	{
		return exitCode;
	}
	
	private List<Broker> getNeighbours(Broker broker)
	{
		List<Broker> neighbours = new ArrayList<Broker>();
		
		for(String neighbour: broker.getNeighbours().getNeighbour())
			for(Broker b: config.getTopology().getBroker())
				if(b.getName().equals(neighbour))
					neighbours.add(b);
		
		return neighbours;
	}
	
	private ByteArrayOutputStream outputStream = null;
	private ByteArrayOutputStream errorStream = null;
	private int exitCode = -1;
	private Config config = null;
}
