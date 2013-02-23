package ca.utoronto.msrg.padres.configService;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import ca.utoronto.msrg.padres.client.ClientConfig;
import ca.utoronto.msrg.padres.client.ClientException;
import ca.utoronto.msrg.padres.common.util.CommandLine;

public class CSClientConfig extends ClientConfig {
	
	public static final String CLI_OPTION_RECOVERY_SYSTEM = "rs";
	
	public String recoverySystemLoc = null;

	public String getRecoverySystemLoc() {
		return recoverySystemLoc;
	}

	public void setRecoverySystemLoc(String recoverySystemLoc) {
		this.recoverySystemLoc = recoverySystemLoc;
	}

	public CSClientConfig() throws ClientException {
		this(null);
	}

	public CSClientConfig(String configFile) throws ClientException {
		if (configFile != null)
			this.configFile = configFile;
		try {
			clientProps = new Properties();
			clientProps.load(new FileInputStream(this.configFile));
			clientID = clientProps.getProperty("client.id");
			String neighborList = clientProps.getProperty("client.remoteBrokers");
			if (neighborList != null)
				connectBrokerList = neighborList.split(",\\s*");
			else
				connectBrokerList = new String[0];
			connectionRetries = Integer.parseInt(clientProps.getProperty("connection.retries"));
			retryPauseTime = Integer.parseInt(clientProps.getProperty("connection.retry.pauseTime"));
			detailState = clientProps.getProperty("client.store_detail_state", "OFF").toLowerCase().trim().equals(
					"on");
			logPeriod = Integer.parseInt(clientProps.getProperty("log.period"));
			// recovery system location in the client property file
			recoverySystemLoc = clientProps.getProperty("client.recovery_system");
//			System.out.println("location: "+recoverySystemLoc);
		} catch (FileNotFoundException e) {
			throw new ClientException("Config file not found: " + this.configFile, e);
		} catch (IOException e) {
			throw new ClientException("Error reading config file: " + this.configFile, e);
		} catch (NumberFormatException e) {
			// parseInt() is given a null value: pass
		}
	}

	public static String[] getCommandLineKeys() {
		List<String> cliKeys = new ArrayList<String>();
		cliKeys.add(CLI_OPTION_ID + ":");
		cliKeys.add(CLI_OPTION_BROKER_LIST + ":");
		cliKeys.add(CLI_OPTION_CONNECT_RETRY + ":");
		cliKeys.add(CLI_OPTION_CONNECT_PAUSE + ":");
		cliKeys.add(CLI_OPTION_DETAIL_STATE + ":");
		cliKeys.add(CLI_OPTION_CONFIG_FILE + ":");
		cliKeys.add(CLI_OPTION_LOG_CONFIG + ":");
		cliKeys.add(CLI_OPTION_LOG_LOCATION + ":");
		cliKeys.add(CLI_OPTION_RECOVERY_SYSTEM + ":");
		return cliKeys.toArray(new String[0]);
	}
	
	@Override
	public void overwriteWithCmdLineArgs(CommandLine cmdLine) {
		String buffer = null;
		if ((buffer = cmdLine.getOptionValue(CLI_OPTION_ID)) != null)
			clientID = buffer.trim();
		if ((buffer = cmdLine.getOptionValue(CLI_OPTION_BROKER_LIST)) != null)
			connectBrokerList = buffer.trim().split(",");
		if ((buffer = cmdLine.getOptionValue(CLI_OPTION_CONNECT_RETRY)) != null)
			connectionRetries = Integer.parseInt(buffer.trim());
		if ((buffer = cmdLine.getOptionValue(CLI_OPTION_CONNECT_PAUSE)) != null)
			retryPauseTime = Integer.parseInt(buffer.trim());
		if ((buffer = cmdLine.getOptionValue(CLI_OPTION_DETAIL_STATE)) != null)
			detailState = buffer.trim().toLowerCase().equals("on");
		if ((buffer = cmdLine.getOptionValue(CLI_OPTION_RECOVERY_SYSTEM)) != null)
			recoverySystemLoc = buffer.trim();
	}

}
