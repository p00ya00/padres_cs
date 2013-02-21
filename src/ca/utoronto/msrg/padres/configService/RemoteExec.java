package ca.utoronto.msrg.padres.configService;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.rmi.RemoteException;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import ca.utoronto.msrg.padres.configService.schema.*;

import com.jcraft.jsch.*;

/*
 * TO DO:
 * 
 * - return output of remote execution
 * - get PADRES path from environment variable
 * - recognize host OS type to handle Linux/Windows path format
 * - if possible, replace thread sleep with waiting for command to finish execution
 */

public class RemoteExec {
	
	public void startBroker(Broker broker) throws RemoteExecException
	{
		String command = createStartBrokerCommand(broker);
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		ByteArrayOutputStream errorStream = new ByteArrayOutputStream();
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
			System.out.println("Connecting to " + broker.getUsername() + "@" + broker.getHost());
			System.out.println("Running: " + command);
			channel.connect();
			while(true)
			 {
				 Thread.sleep(1000);
				 if(channel.getExitStatus() != -1)
					 break;
			 }
			channel.disconnect();
			session.disconnect();
			if(outputStream.size() > 0)
				 System.out.println(outputStream.toString());
			if(errorStream.size() > 0)
			 	System.out.println("error:\n" + errorStream.toString());
			int exitCode = channel.getExitStatus();
			System.out.println("exit-status: " + exitCode);
			if(exitCode > 0)
				throw new RemoteExecException("Error occured while starting broker " 
							+ broker.getName() + " Error code: " + exitCode);
		} catch (JSchException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	public void stopBroker(Broker broker)
	{
		
	}
	
	public void restartBroker(Broker broker) throws RemoteExecException
	{
		stopBroker(broker);
		startBroker(broker);
	}
	public void exec(Broker broker)
	{
		String command = createCommand(broker);
		JSch jsch = new JSch();
		try {
			Session session= jsch.getSession(broker.getUsername(), broker.getHost(), 22);
			session.setPassword(broker.getPassword());
			session.setConfig("StrictHostKeyChecking", "no");
			session.connect();
			Channel channel=session.openChannel("exec");
			((ChannelExec)channel).setCommand(command);
			channel.setInputStream(null);
			((ChannelExec)channel).setErrStream(System.err);
			System.out.println("Connecting to " + broker.getUsername() + "@" + broker.getHost());
			System.out.println("Running: " + command);
			channel.connect();
			Thread.sleep(2000);
			channel.disconnect();
			session.disconnect();
		} catch (JSchException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	public String createStartBrokerCommand(Broker broker)
	{
		String command = "startbroker -uri " + broker.getType() + "://" 
						+ broker.getHost() + ":" + broker.getPort() 
						+ "/" + broker.getName() + " ";
		if(broker.getParams() != null && broker.getParams().getParam() != null)
			for(Param p : broker.getParams().getParam())
				command += "-" + p.getName() + " " + p.getValue() + " ";
		
		return command;
	}
	
	public String createStopBrokerCommand(Broker broker) throws RemoteExecException
	{
		if(broker.getName() == null || broker.getName().isEmpty())
			throw new RemoteExecException("Broker ID not specified!");
		String command = "stopbroker " + broker.getName();
		
		return command;
	}
	
//	public static void main(String[] args) {
//		File file = new File("topologyScripts/deployment.xml");
//		JAXBContext jaxbContext;
//		RemoteExec remote = new RemoteExec();
//		try {
//			jaxbContext = JAXBContext.newInstance(Topology.class);
//			Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
//			Topology topology = (Topology) jaxbUnmarshaller.unmarshal(file);
//			for(Broker broker : topology.getBroker())
//				remote.exec(broker);
//		} catch (JAXBException e) {
//			e.printStackTrace();
//		}
//	}
}
