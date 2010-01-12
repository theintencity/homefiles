package dev;

import java.io.IOException;
import java.net.ConnectException;

import javax.xml.xpath.XPathExpressionException;

import org.restlet.Client;
import org.restlet.Context;
import org.restlet.data.Protocol;
import org.restlet.resource.DomRepresentation;

import db.Database;
import db.Updater;

/**
 * The event handler which updates the metadata with the remote machine
 * whenever the updater or devices signals a change event.
 * 
 * @author Mamta
 */
public class MetadataUpdater implements Updater.Listener, DeviceUpdater.Listener {
	/**
	 * The current list of devices.
	 */
	private DeviceUpdater deviceUpdater;
	
	/**
	 * Construct a new updater object.
	 * @param deviceUpdater
	 */
	public MetadataUpdater(DeviceUpdater deviceUpdater) {
		this.deviceUpdater = deviceUpdater;
	}
	
	/**
	 * This is invoked by the updater thread if something is changed in the 
	 * database. This function pushes the database to other devices.
	 */
	public void updated(Database db) {
		Device[] devices = deviceUpdater.getDevices();
		
		Client client = new Client(new Context(), Protocol.HTTP);
		client.getContext().getParameters().add("converter", 
				"com.noelios.restlet.http.HttpClientConverter");
		
		try {
			for (int i=0; i<devices.length; ++i) {
		    	DomRepresentation dom = MetadataResource.createMetadata(db);
				Device device = devices[i];
				if (!device.getName().equals(db.getLocalDevice())) {
					try {
						String url = "http://" + device.getIp() + ":" + device.getPort() + "/metadata";
						System.out.println("PUT " + device.getName() + " " + url);
						client.put(url, dom);
						
						// for some reason, client.post is not declared as throwing IOException, but
						// it does throw IOException on connect failure. Since catching an unthrown 
						// exception gives compilation error, I need the following code to prevent the
						// compilation error and still be able to catch the exception.
						if (false)
							throw new IOException();
					}
					catch (IOException e) {
						System.err.println("updated: device is offline " + device.toString());
					}
				}
				dom.release();
			}
		} catch (IOException e) {
			e.printStackTrace();
		} catch (XPathExpressionException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	/**
	 * This is invoked when a new device becomes online. This object sends the metadata
	 * to that new device. It also changes the device status to online in the database.
	 */
	public void added(Device device) {
		Database db = Database.getInstance();
		if (device.getName().equals(db.getLocalDevice())) {
			return;
		}
		
		Client client = new Client(new Context(), Protocol.HTTP);
		client.getContext().getParameters().add("converter", 
				"com.noelios.restlet.http.HttpClientConverter");

		try {
			db.setDeviceStatus(device.getName(), "online");
			
			DomRepresentation dom = MetadataResource.createMetadata(db);
			
			String url = "http://" + device.getIp() + ":" + device.getPort() + "/metadata";
			System.out.println("PUT " + device.getName() + " " + url);
			client.put(url, dom);
			dom.release();
		} catch (ConnectException e) {
			System.err.println("added: device is offline " + device.toString());
		} catch (IOException e) {
			e.printStackTrace();
		} catch (XPathExpressionException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	/**
	 * This is invoked when a device goes offline. It just changes the device
	 * status to be offline in the database.
	 */
	public void removed(Device device) {
		Database db = Database.getInstance();
		if (device.getName().equals(db.getLocalDevice())) {
			return;
		}
		
		try {
			db.setDeviceStatus(device.getName(), "offline");
		} catch (XPathExpressionException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
}
