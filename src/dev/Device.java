package dev;

import java.util.Date;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * An object to represent a device information. 
 * This object is immutable.
 */
public class Device {
	
	// the IP address or host name of the device
	private String ip;
	
	// the listening RESTlet port
	private int port;
	
	// the name of the device, defaults to the host name
	private String name;
	
	// the time this device was started in this instance.
	private long started;
	
	// expire at this time
	private long expires = 0;
	
	/**
	 * Construct a new device object.
	 * 
	 * @param ip
	 * @param port
	 */
	public Device(String ip, int port) {
		this.ip = ip;
		this.port = port;
		this.name = null;
		this.started = (new Date()).getTime();
	}
	
	/**
	 * Construct a new device object using all the attributes.
	 * 
	 * @param ip
	 * @param port
	 * @param name
	 * @param started
	 */
	public Device(String ip, int port, String name, long started) {
		this.ip = ip;
		this.port = port;
		this.name = name;
		this.started = started;
	}

	/**
	 * Getter for IP.
	 * 
	 * @return
	 */
	public String getIp() {
		return ip;
	}

	/**
	 * Getter for port number.
	 * 
	 * @return
	 */
	public int getPort() {
		return port;
	}

	/**
	 * getter for name of the device.
	 * 
	 * @return
	 */
	public String getName() {
		return name;
	}
	
	/**
	 * Getter for started time.
	 * 
	 * @return
	 */
	public long getStarted() {
		return started;
	}
	
	/**
	 * Get the URL for this device.
	 * 
	 * @return
	 */
	public String getURL() {
		return "http://" + ip + ":" + port;
	}
	
	/**
	 * Set the expires attribute for this device.
	 * 
	 * @return
	 */
	public void setExpires(long value) {
		expires = value;
	}
	
	/**
	 * Get the expires attribute for this device.
	 * 
	 * @return
	 */
	public long getExpires() {
		return expires;
	}
	
	/**
	 * Check whether the device has expired or not?
	 */
	public boolean hasExpired() {
		return (expires > 0 && expires < (new Date()).getTime());
	}
	
	/**
	 * String representation of this device.
	 */
	public String toString() {
		return (name != null ? (name + " ") : "") + "<" + ip + ":" + String.valueOf(port) + ">";
	}
	
	/**
	 * Compare ip, port, name and started of two devices. 
	 * 
	 * @param other
	 * @return
	 */
	public boolean equals(Device other) {
		return this.ip.equals(other.ip) && this.port == other.port &&
			(this.name == null && other.name == null || this.name != null && other.name != null && 
					this.name.equals(other.name) && this.started == other.started);
	}
	
	/**
	 * Convert this device object to an XML node using the supplied document.
	 * @param doc
	 * @return
	 */
	public Node toNode(Document doc) {
    	Element node = doc.createElement("Device");
    	Element name = doc.createElement("Name");
    	name.appendChild(doc.createTextNode(getName() != null ? getName() : ""));
    	Element ip = doc.createElement("IP");
    	ip.appendChild(doc.createTextNode(getIp()));
    	Element port = doc.createElement("Port");
    	port.appendChild(doc.createTextNode(String.valueOf(getPort())));
    	node.appendChild(name);
    	node.appendChild(ip);
    	node.appendChild(port);
    	node.setAttribute("started", String.valueOf(getStarted()));
    	return node;
	}
	
	/**
	 * Create a new Device object from the XML node.
	 * @param node
	 * @return
	 */
	public static Device fromNode(Node node) {
		String name = ((Element) node).getElementsByTagName("Name").item(0).getTextContent();
		String ip   = ((Element) node).getElementsByTagName("IP").item(0).getTextContent();
		String port = ((Element) node).getElementsByTagName("Port").item(0).getTextContent();
		long started = Long.valueOf(((Element) node).getAttribute("started")).longValue();
		Device result = new Device(ip, Integer.valueOf(port).intValue(), name, started);
		if (((Element) node).hasAttribute("expires")) {
			long expires = Long.valueOf(((Element) node).getAttribute("expires")).longValue();
			result.expires = expires;
		}
		return result;
	}
}
