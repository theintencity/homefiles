package dev;

import java.io.IOException;

import javax.xml.xpath.XPathExpressionException;

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
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import db.Database;

/**
 * The metadata resource represents the metadata on a particular device which
 * is put by another machine to synchronize the metadata.
 *
 * @author Mamta
 */
public class MetadataResource extends Resource {  
  
	/**
	 * Construct a new resource.
	 * 
	 * @param context
	 * @param request
	 * @param response
	 */
    public MetadataResource(Context context, Request request, Response response) {
    	super(context, request, response);
    	
		getVariants().add(new Variant(MediaType.TEXT_XML));
    }
  
    /**
     * PUT is allowed from another machine on metadata.
     */
	@Override
	public boolean allowPut() {
		return true;
	}
	
    /** 
     * GET returns a full representation in XML. 
     */  
    @Override  
    public Representation represent(Variant variant) throws ResourceException {
    	Representation result = null;
		try {
    		Database db = Database.getInstance();
			result = createMetadata(db);
		} catch (IOException e) {
			e.printStackTrace();
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL, "IOException");
		} catch (InterruptedException e) {
			e.printStackTrace();
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL, "InterruptedException");
		} catch (XPathExpressionException e) {
			e.printStackTrace();
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL, "XPathExpressionException");
		}
		
    	if (result == null) {
    		throw new ResourceException(Status.SERVER_ERROR_INTERNAL, "Incomplete Database");
    	}
    	
    	return result;
    }  
    
    /**
     * PUT invokes the database.update method to update the database using the
     * new metadata received from another machine.
     */
    @Override
    public void storeRepresentation(Representation entity) throws ResourceException {
    	DomRepresentation dom = new DomRepresentation(entity);
    	Database db = Database.getInstance(); 
    	try {
			db.update(dom.getDocument());
		} catch (InterruptedException e) {
			e.printStackTrace();
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL, "InterruptedException");
		} catch (IOException e) {
			e.printStackTrace();
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL, "IOException");
		}
    }
    
    /**
     * Create the metadata XML DOM for the current database.
     * The metadata has top-level element as Devices with localdevice and username
     * attributes. The Devices element contains all the devices and its files for that
     * username. The localdevice allows the remote to identify this device's name.
     * 
     * @param db
     * @return
     * @throws IOException
     * @throws XPathExpressionException
     * @throws InterruptedException
     */
    public static DomRepresentation createMetadata(Database db) throws IOException, XPathExpressionException, InterruptedException {
    	DomRepresentation dom = new DomRepresentation(MediaType.TEXT_XML);
    	Document doc = dom.getDocument();
    	
    	// since I need to POST username also to the remote newly started machine,
    	// I am passing the username also as an attribute to the Devices element.
    	// The localdevice is needed so that remote machine knows which machine
    	// sent the request.
    	
    	Element devicesNode = doc.createElement("Devices");
    	devicesNode.setAttribute("localdevice", db.getLocalDevice());
    	NodeList users = db.search("/Database/User");
    	if (users.getLength() > 0) {
    		devicesNode.setAttribute("username", ((Element) users.item(0)).getAttribute("name"));
    	}
    	
    	NodeList existing = db.search("/Database/User/Devices/Device");
    	for (int i=0; i<existing.getLength(); ++i) {
    		devicesNode.appendChild(doc.adoptNode(existing.item(i).cloneNode(true)));
    	}
    	doc.appendChild(devicesNode);

		return dom;
	}
}  
