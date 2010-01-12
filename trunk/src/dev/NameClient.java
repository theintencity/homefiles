package dev;

import java.io.IOException;

import org.restlet.Client;
import org.restlet.Context;
import org.restlet.data.MediaType;
import org.restlet.data.Protocol;
import org.restlet.data.Response;
import org.restlet.resource.DomRepresentation;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import dev.Device;

/**
 * The client to get or post a devices resource.
 *
 * @author Mamta
 */
public class NameClient {

	// the client object
	private Client client;
	
	// the name server device 
	private Device server;
	
	/**
	 * Create a new name client connecting to the given name server device.
	 * 
	 * @param device
	 */
	public NameClient(Device server) {
		this.server = server;
		client = new Client(new Context(), Protocol.HTTP);
		client.getContext().getParameters().add("converter", 
				"com.noelios.restlet.http.HttpClientConverter");
	}
	
	/**
	 * Get an array of devices from the name server.
	 * 
	 * @return
	 */
    public Device[] getDevices() {
    	String url = "http://" + server.getIp() + ":" + String.valueOf(server.getPort()) + "/devices";
    	//System.out.println("GET " + url);
    	Response response = client.get(url);
    	DomRepresentation dom = response.getEntityAsDom();
    	try {
    		Document doc = dom.getDocument();
    		if (doc.getFirstChild().getNodeName().equals("Devices")) {
    			NodeList nodes = doc.getFirstChild().getChildNodes();
    			Device[] result = new Device[nodes.getLength()];
    			for (int i=0; i<nodes.getLength(); ++i) {
    				Node node = nodes.item(i);
    				result[i] = Device.fromNode(node);
    			}
    			return result;
    		}
    	}
    	catch (Exception e) {
    		System.out.println("Exception receiving response: " + e.getMessage());
    		e.printStackTrace();
    	}
    	return null;
    }
    
    /**
     * Add the local device to the name server.
     * 
     * @param device
     */
    public void addDevice(Device device) {
		try {
	    	DomRepresentation dom = new DomRepresentation(MediaType.TEXT_XML);
	    	Document doc = dom.getDocument();
	    	doc.appendChild(device.toNode(doc));
	    	
	    	String url = "http://" + server.getIp() + ":" + String.valueOf(server.getPort()) + "/devices";
	    	System.out.println("POST " + url);
	    	client.post(url, dom);
		} catch (IOException e) {
			e.printStackTrace();
		}
    }
}
