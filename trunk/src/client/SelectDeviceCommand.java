package client;

import java.io.IOException;

/**
 * Handle the select command to change or display the currently selected
 * device. The list command by default lists the files on the current
 * selected device.
 * 
 * @author Mamta
 */
public class SelectDeviceCommand implements IClientCommand {
	public static final String usage = "[devname]\n" +
		" change the selected device to the given device name.\n" +
		" without devname, print the currently selected device.\n" +
		" use 'select' command to change the device selection.";

	public void exec(String args) throws IOException {
		args = (args != null ? args.trim() : args);
		ClientConfig config = ClientConfig.getInstance();
		
		if (args == null || args.length() == 0) {
			System.out.println(config.getCurrentDevice());
		}
		else {
			config.setCurrentDevice(args);
		}
	}
}
