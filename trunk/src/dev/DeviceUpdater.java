package dev;

/**
 * The updater thread which periodically refreshes the online devices list
 * by pinging the nameserver. It also refreshes its own entry in the 
 * nameserver.
 * 
 * @author Mamta
 */
public class DeviceUpdater implements Runnable {
	/**
	 * Configuration item for periodic refresh interval.
	 * It defaults to 11 seconds.
	 */
	public static long interval = 11000;
	
	// the Restlet client.
	protected NameClient client;
	
	// the local device information.
	protected Device local;

	// the current list of devices.
	protected Device[] devices;
	
	/**
	 * Construct a new object with the given nameserver and local device.
	 * @param ns
	 * @param local
	 */
	public DeviceUpdater(Device ns, Device local) {
    	this.client = (ns != null ? new NameClient(ns) : null);
    	this.local = local;
	}
	
	/**
	 * Get the current list of online devices based on the last ping from
	 * the name server.
	 * 
	 * @return
	 */
	public Device[] getDevices() {
		return devices;
	}
	
	/**
	 * Get the device object for the given device name. If not found,
	 * return null which means the device is offline.
	 * 
	 * @param name
	 * @return
	 */
	public Device getDevice(String name) {
		for (int i=0; devices != null && i<devices.length; ++i) {
			if (name.equals(devices[i].getName())) 
				return devices[i];
		}
		return null;
	}
	
	/**
	 * The Listener interface allows dispatching event to listener object
	 * when a new device is detected as online. The interface definition is
	 * static so that for creating a new Listener object you do not need a
	 * DeviceUpdater object. But the listener object itself is not static
	 * hence you can have any number of Listener objects created.
	 */
	public static interface Listener {
		/**
		 * When a device becomes online.
		 * @param device
		 */
		public void added(Device device);
		
		/**
		 * When a device becomes offline.
		 * @param device
		 */
		public void removed(Device device);
	}
	
	// listener object to receive device update event.
	protected Listener listener;
	
	/**
	 * Set a listener object which received device update events.
	 * @param listener
	 */
	public void setListener(Listener listener) {
		this.listener = listener;
	}
	
	/**
	 * The thread function first adds the local device to the nameserver
	 * then gets the current list of devices from the nameserver, periodically.
	 */
	public void run() {
		while (true) {
			client.addDevice(this.local);

	    	Device[] devices = client.getDevices();
	    	if (devices == null) {
	    		// if name server is crashed, do not update list of devices to null.
	    		System.err.println("ERROR: cannot get list of devices from the name server.");
	    	}
	    	else {
	    		setDevices(devices);
	    	}
	    	
			try {
				System.out.println("  DeviceUpdater: waiting for " + interval + "...");
				Thread.sleep(interval);
				System.out.println("  DeviceUpdater: wait completed");
			} catch (InterruptedException e) {				
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * The setDevices function takes a new array of devices, and sets it in the 
	 * internal devices list. Additionally, it calls the added and removed
	 * callback method on any event listener on this object.
	 * 
	 * @param devices
	 */
	protected void setDevices(Device[] devices) {
		// update the list of devices with the new list from the name server
		Device[] old = this.devices;
		this.devices = devices;
		
		if (listener != null) {
    		for (int j=0; old != null && j<old.length; ++j) {
    			boolean found = false;
    			for (int i=0; i<devices.length; ++i) {
    				if (devices[i].getName().equals(old[j].getName())) {
    					found = true;
    					break;
    				}
    			}
    			if (! found) {
    				System.out.println("removed device: " + old[j].toString());
    				listener.removed(old[j]);
    			}
    		}
    		for (int i=0; i<devices.length; ++i) {
    			boolean found = false;
    			for (int j=0; old != null && j<old.length; ++j) {
    				if (devices[i].equals(old[j])) {
    					found = true;
    					break;
    				}
    			}
    			if (! found) {
    				System.out.println("added device: " + devices[i].toString());
    				listener.added(devices[i]);
    			}
    		}
		}
	}
}
