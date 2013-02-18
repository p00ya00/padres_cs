package ca.utoronto.msrg.padres.test.configService;

import static org.junit.Assert.*;
import org.junit.Test;

import ca.utoronto.msrg.padres.configService.RemoteExec;
import ca.utoronto.msrg.padres.configService.schema.Broker;

public class TestRemoteExecution {
	
	@Test
	public void testCreateCommand()
	{
		Broker broker = new Broker();
		broker.setHost("123.45.67.89");
		broker.setName("broker_a");
		broker.setType("rmi");
		broker.setPort(6666);
		RemoteExec remoteExec = new RemoteExec();
		String command = remoteExec.createCommand(broker);
		assertEquals(command, "/home/p00ya/padres-master/bin/startbroker -uri rmi://123.45.67.89:6666/broker_a ");
	}
}
