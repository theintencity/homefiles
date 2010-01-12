package client;

import java.io.IOException;

import client.IClientCommand;
import dev.Device;

/**
 * Handle the devices or devs command to print the list of available devices.
 * 
 * @author Mamta
 */
public class DevicesCommand implements IClientCommand {
	public static final String usage = "\n" +
	" fetch and print list of available devices.";

	public void exec(String args) throws IOException {
		ClientConfig config = ClientConfig.getInstance();
		Device[] devices = config.getDevices();
		printDevices(devices);
	}

	private void printDevices(Device[] devices) {
		System.out.println("There are " + (devices != null ? devices.length : 0) + " device(s)");
		for (int i=0; devices != null && i<devices.length; ++i) {
			System.out.println(" " + devices[i].toString());
		}
	}

}
