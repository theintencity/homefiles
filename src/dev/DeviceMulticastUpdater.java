package dev;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.restlet.data.MediaType;
import org.restlet.resource.DomRepresentation;
import org.restlet.resource.StringRepresentation;
import org.w3c.dom.Element;

/**
 * Extended DeviceUpdater to support multicast device discovery. 
 * Depending on the client, it may or may not do the client-server discovery.
 * Depending on the IP address of the name server it may or may not do multicast
 * device discovery.
 * 
 * @author Mamta
 */
public class DeviceMulticastUpdater extends DeviceUpdater {
	
	// the multicast IP address and port
	protected SocketAddress address;
	
	// the listening and sending socket for multicast
	protected MulticastSocket socket;
	
	// whether this thread is running or not?
	private volatile boolean running = false;
	
	/**
	 * Construct a new multicast updater. If invokes the base class constructor
	 * which will create the client-server updater if needed.
	 * If the name server IP address is multicast address, it will also create
	 * the multicast socket and join the multicast group. The multicast socket
	 * is set with TTL of 1 so that it does not go beyond local network.
	 * 
	 * @param ns
	 * @param local
	 */
	public DeviceMulticastUpdater(Device ns, Device local) {
		super(ns, local);

		try {
			InetAddress ip = InetAddress.getByName(ns.getIp());
			if (ip.isMulticastAddress()) {
				this.client = null;
				this.address = new InetSocketAddress(ip, ns.getPort());
				this.socket = new MulticastSocket(ns.getPort());
				this.socket.setTimeToLive(1);
				this.socket.setSoTimeout((int) 1000);
				this.socket.setLoopbackMode(false);
				this.socket.joinGroup(ip);
				
			}
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * The client application calls close, to terminate this thread.
	 */
	public void close() {
		running = false;
	}
	
	/**
	 * The thread function has four parts: 
	 * 1. for client-server case, add local device to the name server.
	 * 2. for client-server case, get list of current online devices from name server.
	 * 3. remove any expired devices from our list.
	 * 4. for multicast case, advertise our device information if available.
	 * 5. for multicast case, receive device information from other devices.
	 */
	@Override
	public void run() {
		running = true;
		while (running) {
			if (this.local != null && this.client != null) {
				client.addDevice(this.local);
			}
			
			if (this.client != null) {
		    	Device[] devices = client.getDevices();
		    	if (devices == null) {
		    		// if name server is crashed, do not update list of devices to null.
		    		System.err.println("ERROR: cannot get list of devices from the name server.");
		    	}
		    	else {
		    		setDevices(devices);
		    	}
			}
			
			// update expires field
			if (devices != null && devices.length > 0) {
				List<Device> available = new LinkedList<Device>();
				for (int i=0; i<devices.length; ++i) {
					if (!devices[i].hasExpired())
						available.add(devices[i]);
				}
				if (available.size() != devices.length) {
					Device[] newDevices = new Device[available.size()];
					int i = 0;
					for (Iterator<Device> it=available.iterator(); it.hasNext(); ) {
						newDevices[i] = it.next();
						++i;
					}
					setDevices(newDevices);
				}
			}
			
			if (this.local != null) {
				// this is the server which needs to be broadcast local device info
				multicastLocalDevice();
			}
			else {
				// this is the client which just receives remote device info.
			}

			if (this.socket != null) {
				// receive device information from other devices
				Date start = new Date();
				while ((new Date()).getTime() - start.getTime() < interval) {
					if (!running)
						break;
					multicastReceive();
				}
			}
			else {
				// if not available, then just wait in the interval (client-server case).
				try {
					System.out.println("  DeviceUpdater: waiting for " + interval + "...");
					Thread.sleep(interval);
					System.out.println("  DeviceUpdater: wait completed");
				} catch (InterruptedException e) {				
					e.printStackTrace();
				}
			}
		}
	}
	
	/**
	 * Multicast the local device information to the group.
	 * The expired attribute is added so that other devices can remove this
	 * information if we don't send a refresh.
	 */
	protected void multicastLocalDevice() {
		if (socket != null && address != null && local != null) {
			try {
				DomRepresentation dom = new DomRepresentation(MediaType.TEXT_XML);
				Element deviceNode = (Element) local.toNode(dom.getDocument());
				long expires = (new Date()).getTime() + DevicesResource.expiration;
				deviceNode.setAttribute("expires", String.valueOf(expires));
				dom.getDocument().appendChild(deviceNode);
				String msg = dom.getText();
				byte[] buf = msg.getBytes();
				//System.out.println("sending:\n" + msg);
				DatagramPacket packet = new DatagramPacket(buf, buf.length);
				packet.setSocketAddress(address);
				socket.send(packet);
			} catch (IOException e) {
				e.printStackTrace();
				return;
			}
		}
	}
	
	/**
	 * Clear the list of devices. This is called by the client to clear the list
	 * before fetching a new list, so that it gets the most-up-to-date devices
	 * list.
	 */
	public void clearDevices() {
		devices = null;
	}
	
	/**
	 * Send a multicast <Query/> command, which triggers the other devices to
	 * respond immediately with their device information. This is used by the 
	 * client to immediately get the devices list, instead of waiting for 
	 * the next interval.
	 */
	public void multicastQuery() {
		if (socket != null && address != null) {
			try {
				String msg = "<Query/>";
				DatagramPacket packet = new DatagramPacket(msg.getBytes(), msg.length());
				packet.setSocketAddress(address);
				socket.send(packet);
			} catch (IOException e) {
				e.printStackTrace();
				return;
			}
		}
	}
	
	/**
	 * Receive a message on the multicast socket and act on it.
	 * If it is a <Query/> request, then respond with local device information if
	 * available.
	 * If it is a <Device/> message, then update our devices list.
	 */
	protected void multicastReceive() {
		byte[] buf = new byte[1000];
		DatagramPacket packet = new DatagramPacket(buf, buf.length);
		try {
			socket.receive(packet);
			String msg = new String(packet.getData(), 0, packet.getLength());
			if ("<Query/>".equals(msg)) {
				// client is requesting the information. send local device information.
				multicastLocalDevice();
			}
			else {
				//System.out.println("receive: \n" + msg);
				DomRepresentation dom = new DomRepresentation(new StringRepresentation(msg, MediaType.TEXT_XML));
				Device newDevice = Device.fromNode(dom.getDocument().getFirstChild());
				receiveDeviceData(newDevice);
			}
		} catch (SocketTimeoutException e) {
			// do nothing
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Receive a new device data from another device, and update local devices list.
	 * @param newDevice
	 */
	private void receiveDeviceData(Device newDevice) {
		Device[] devices = this.devices;
		boolean found = false;
		// check if the device name is already found?
		for (int i=0; devices != null && i<devices.length; ++i) {
			Device existing = devices[i];
			if (existing != null && existing.getName().equals(newDevice.getName())) {
				found = true;
				if (!existing.equals(newDevice)) {
					// if matching entry not found, then replace the 
					// old entry with the new one.
					devices[i] = newDevice;
					listener.removed(existing);
					listener.added(newDevice);
				}
				else {
					// otherwise if matching entry found, then just 
					// update the expiration time.
					existing.setExpires(newDevice.getExpires());
				}
			}
		}
		
		// if exiting device name not found, then add a new device information
		if (!found) {
			devices = new Device[(devices != null ? devices.length : 0) + 1];
			int i = 0;
			for (; this.devices != null && i<this.devices.length; ++i) {
				devices[i] = this.devices[i];
			}
			devices[i] = newDevice;
			this.devices = devices;
			if (listener != null)
				listener.added(newDevice);
		}
	}
}
