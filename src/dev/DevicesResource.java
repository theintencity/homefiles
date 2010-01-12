package dev;

import java.io.IOException;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.restlet.Context;  
import org.restlet.data.MediaType;  
import org.restlet.data.Request;  
import org.restlet.data.Response;  
import org.restlet.data.Status;
import org.restlet.resource.DomRepresentation;
import org.restlet.resource.Representation;  
import org.restlet.resource.Resource;  
import org.restlet.resource.ResourceException;  
import org.restlet.resource.Variant;  
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import db.MyReadWriteLock;

/**
 * The DevicesResource is used by the NameServer to represent the list of online
 * devices. Periodically, each device refreshes its entry in the 
 * resource. A new entry expires after some time, if not refreshed.
 * 
 * @author Mamta
 */
public class DevicesResource extends Resource {  
	/**
	 * The configuration item for the expiration of device entries,
	 * defaults to 20 seconds.
	 */
	public static long expiration = 20000;
  
	private static Document doc; // devices stored as XML DOM
	private static XPathFactory xpathFactory;
	private static MyReadWriteLock lock = new MyReadWriteLock();
	
	/**
	 * Static constructor must be invoked by the main so that it creates the
	 * data resource in XML DOM.
	 * 
	 * @throws ParserConfigurationException
	 */
	public static void init() throws ParserConfigurationException {
		DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
		docFactory.setNamespaceAware(true);
		DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
		doc = docBuilder.newDocument();
		Element devices = doc.createElement("Devices");
		doc.appendChild(devices);
		
		xpathFactory = XPathFactory.newInstance();
	}
	
	/**
	 * Construct the resource.
	 * 
	 * @param context
	 * @param request
	 * @param response
	 */
	public DevicesResource(Context context, Request request, Response response) {  
		super(context, request, response);  
		
		getVariants().add(new Variant(MediaType.TEXT_XML));
	}

	/**
	 * POST is allowed for this resource.
	 */
	@Override
	public boolean allowPost() {
		return true;
	}
	
	/**
	 * In response to GET, it returns the XML of the devices list.
	 */
	@Override  
	public Representation represent(Variant variant) throws ResourceException {
		Representation result = null;
		try {
			lock.getWriteLock();
			// first remove any expired devices
			removeExpired();
			// then return the devices list
			result = new DomRepresentation(MediaType.TEXT_XML, doc);
		}
		catch (InterruptedException e) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL, "InterruptedException");
		}
		finally {
			lock.releaseWriteLock();
		}
		if (result == null) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL, "No Devices List");
		}
		
		return result;
	}  
	
	/**
	 * process the POST request for updating the devices in the name server
	 * implementation.
	 */
	@Override
	public void acceptRepresentation(Representation entity) throws ResourceException {
		try {
			lock.getWriteLock();
			add(new DomRepresentation(entity));
		}
		catch (InterruptedException e) {
			e.printStackTrace();
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL, "InterruptedException");
		} catch (DOMException e) {
			e.printStackTrace();
			throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST, "DOMException: " + e.getMessage());
		} catch (IOException e) {
			e.printStackTrace();
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL, "IOException");
		} catch (XPathExpressionException e) {
			e.printStackTrace();
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL, "XPathExpressionException");
		}
		finally {
			lock.releaseWriteLock();
		}
	}
		

	/**
	 * Remove any devices for which we haven't received an update in the last 
	 * interval.
	 * This function assumes that lock is write-locked, and keeps it write-locked
	 * after completion.
	 * 
	 * @throws InterruptedException 
	 */
	private void removeExpired() throws InterruptedException {
		long now = (new Date()).getTime();
		NodeList devices = doc.getFirstChild().getChildNodes();
		List<Node> expired = new LinkedList<Node>();
		for (int i=0; devices != null && i<devices.getLength(); ++i) {
			Element device = (Element) devices.item(i);
			long expires = Long.valueOf(device.getAttribute("expires"));
			if (expires < now) {
				expired.add(device);
			}
		}
		if (expired.size() > 0) {
			for (Iterator<Node> it=expired.iterator(); it.hasNext(); ) {
				Node node = it.next();
				System.out.println("  removing expired device");
				doc.getFirstChild().removeChild(node);
			}
		}
		
	}
	
	/**
	 * Add a new device information in DOM to the devices list with appropriate
	 * expires attribute.
	 * This function assumes that lock is write-locked.
	 * 
	 * @param dom
	 * @throws IOException 
	 * @throws DOMException 
	 * @throws XPathExpressionException 
	 */
	private void add(DomRepresentation dom) throws DOMException, IOException, XPathExpressionException {
		Node device = doc.importNode(dom.getDocument().getFirstChild(), true);
		XPath xpath = xpathFactory.newXPath();
		String deviceName = ((Element) device).getElementsByTagName("Name").item(0).getTextContent();
		Node existing = (Node) xpath.evaluate("/Devices/Device[Name='" + deviceName + "']", doc, XPathConstants.NODE);
		if (existing != null) {
			doc.getFirstChild().removeChild(existing);
		}
		
		// add an expires attribute which is 'expiration' ms after current time.
		String expires = String.valueOf((new Date()).getTime() + expiration);
		((Element) device).setAttribute("expires", expires);

		doc.getFirstChild().appendChild(device);
	}
}  
