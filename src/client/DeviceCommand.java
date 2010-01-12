package client;

import java.io.IOException;

import dev.Device;

/**
 * Handle the device or dev command to change the currently logged in device.
 * 
 * @author Mamta
 */
public class DeviceCommand implements IClientCommand {
	public static final String usage = "[devname]\n" +
			" if devname is supplied, login to that device\n" +
			" otherwise display the current device name.";
	
	public void exec(String args) throws IOException {
		args = (args != null ? args.trim(): args);
		if ("google documents".equalsIgnoreCase(args))
			args = "gdocs";
		if (args != null && "gdocs".equals(args)) {
			System.out.println("cannot set device to google documents");
			return;
		}
		
		ClientConfig config = ClientConfig.getInstance();
		if (args == null || args.length() == 0) {
			Device loginDevice = config.getLoginDevice();
			if (loginDevice != null)
				System.out.println(loginDevice.toString());
			else
				System.out.println("no device available");
		}
		else {
			Device[] devices = config.getDevices();
			Device loginDevice = null;
			for (int i=0; i<devices.length; ++i) {
				Device device = devices[i];
				if (args.equalsIgnoreCase(device.getName())) {
					loginDevice = device;
					break;
				}
			}
			if (loginDevice != null) {
				config.setLoginDevice(loginDevice);
				System.out.println(loginDevice.toString());
			}
			else {
				System.out.println("device " + args + " unavailable");
			}
		}
	}
}
