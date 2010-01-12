package client;

import java.net.InetAddress;
import java.net.UnknownHostException;

import dev.Device;
import dev.DeviceMulticastUpdater;
import dev.NameClient;

/**
 * The client configuration such as list of devices, currently logged in device,
 * selected device and current directory. This is singleton.
 * 
 * @author Mamta
 */
public class ClientConfig {
	
	private static ClientConfig instance;
	
	private NameClient nameClient;
	private DeviceMulticastUpdater updater;
	
	private Device loginDevice;
	private String currentDevice;
	private String currentDir = "";
	
	/**
	 * Get the singleton instance of the configuration object.
	 * 
	 * @return
	 */
	public static ClientConfig getInstance() {
		if (instance == null)
			instance = new ClientConfig();
		return instance;
	}
	
	/**
	 * Set the name server "address:port". It will create either a client serve name-client
	 * or a multicast device updater, depending on whether the address is a unicast or
	 * multicast address.
	 *  
	 * @param value
	 */
	public void setNameServer(String value) {
		String[] parts = value.split(":");
		String nameserver_ip;
		int nameserver_port;
		if (parts.length == 2) {
			nameserver_ip = parts[0];
			nameserver_port = Integer.parseInt(parts[1]);
		}
		else {
			nameserver_ip = value;
			nameserver_port = 2500;
		}
		if (updater != null) {
			updater = null;
		}
		if (nameClient != null) {
			nameClient = null; // clear the previous one.
		}
		
		InetAddress address = null;
		try {
			address = InetAddress.getByName(nameserver_ip);
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		Device ns = new Device(nameserver_ip, nameserver_port);
		if (address == null || address.isMulticastAddress()) {
			updater = new DeviceMulticastUpdater(ns, null);
        	Thread th0 = new Thread(updater);
        	th0.start();
		}
		else {
			nameClient = new NameClient(ns);
		}
	}
	
	/**
	 * Get the underlying device updater object.
	 * 
	 * @return
	 */
	public DeviceMulticastUpdater getUpdater() {
		return updater;
	}
	
	/**
	 * Get the array of devices using the name client or multicast updater, as the 
	 * case may be. If the loginDevice or currentDevice are empty, they are set to
	 * the first device in the list.
	 * 
	 * @return
	 */
	public Device[] getDevices() {
		Device[] result = null;
		if (nameClient != null) {
			result = nameClient.getDevices();
		}
		else if (updater != null) {
			updater.multicastQuery();
			updater.clearDevices();
			try {
				Thread.sleep(300);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			result = updater.getDevices();
		}
		else {
			System.out.println("no nameserver is set.");
			return null;
		}
		
		if (result != null && loginDevice == null && result.length > 0) {
			loginDevice = result[0]; // use the first device as default login device.
		}
		if (result != null && currentDevice == null && result.length > 0) {
			currentDevice = result[0].getName();
		}
		
		return result;
	}
	
	/**
	 * Set the given device as the current login device.
	 * 
	 * @param loginDevice
	 */
	public void setLoginDevice(Device loginDevice) {
		this.loginDevice = loginDevice;
		this.currentDevice = (loginDevice != null ? loginDevice.getName() : null);
		this.currentDir = ""; // reset to top
	}
	
	/**
	 * Get the current login device.
	 * 
	 * @return
	 */
	public Device getLoginDevice() {
		return getLoginDevice(false);
	}
	
	/**
	 * Get the current login device. If loginIfNeeded is true, then it logs in
	 * to a device if not already logged in.
	 * 
	 * @param loginIfNeeded
	 * @return
	 */
	public Device getLoginDevice(boolean loginIfNeeded) {
		if (loginDevice == null && loginIfNeeded) {
			getDevices();
		}
		return loginDevice;
	}
	
	/**
	 * Get the current selected device.
	 * @return
	 */
	public String getCurrentDevice() {
		return currentDevice;
	}
	
	/**
	 * Set the selected device.
	 * @param name
	 */
	public void setCurrentDevice(String name) {
		currentDevice = name;
	}
	
	/**
	 * Get the current directory on the selected device.
	 * @return
	 */
	public String getCurrentDir() {
		return currentDir;
	}

	/**
	 * Set the current directory on the selected device.
	 * @param currentDir
	 */
	public void setCurrentDir(String currentDir) {
		this.currentDir = currentDir;
	}
}
