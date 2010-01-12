package api;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Iterator;
import java.util.List;

import javax.xml.xpath.XPathExpressionException;

import org.restlet.Context;  
import org.restlet.data.MediaType;  
import org.restlet.data.Request;  
import org.restlet.data.Response;  
import org.restlet.data.Status;
import org.restlet.resource.DomRepresentation;
import org.restlet.resource.Representation;  
import org.restlet.resource.ResourceException;
import org.restlet.resource.StringRepresentation;  
import org.restlet.resource.Variant;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import db.Database;
import db.LocalDir;
  
/** 
 * The SettingsResource is used to get and set the settings of the
 * user's root directory.
 */  
public class SettingsResource extends BaseResource {  
  
	/**
	 * Construct the resource.
	 * @param context
	 * @param request
	 * @param response
	 */
    public SettingsResource(Context context, Request request, Response response) {
    	
        super(context, request, response);
        
        if (!authenticate())
        	return;
        
		// This representation has only one type of representation. 
		getVariants().add(new Variant(MediaType.TEXT_XML));
    }
    
    /** 
	 * Both GET and PUT are implemented using GET to support from browser.
	 * It uses the directory parameter, if present use PUT else GET.
	 */
	@Override  
	public Representation represent(Variant variant) throws ResourceException {
		
		Request request = getRequest();
		String newValue = (String) request.getAttributes().get("directory");
		
			
		try {
			if (newValue == null) {
				// this is for getting the local directory
				Node settings;
				Database db = Database.getInstance();
				List<LocalDir> list = db.getLocalDir();
				LocalDir ldir = null;
				for (Iterator<LocalDir> it=list.iterator(); it.hasNext(); ) {
					LocalDir ldir0 = it.next();
					if (gdocs.getUsername().equals(ldir0.userName)) {
						ldir = ldir0;
						break;
					}
				}
				
				DomRepresentation dom = new DomRepresentation(MediaType.TEXT_XML);
				Document doc = dom.getDocument();
				
				settings = doc.createElement("Settings");
				Node root = doc.createElement("Root");
				((Element) root).setAttribute("directory", ldir != null && ldir.local_dir != null ? ldir.local_dir : "");
				((Element) settings).appendChild(root);
				doc.appendChild(settings);
				
		        return dom;
			}
			else {
				String localDir;
				// this is for setting the local directory
				newValue = URLDecoder.decode(newValue, "US-ASCII");
				
				System.out.println("  set settings=" + newValue);
				Database db = Database.getInstance();
				
				db.setUserLocalDir(gdocs.getUsername(), newValue);
				localDir = newValue;

				return new StringRepresentation(
						(localDir != null ? "Changed rootdir to " + localDir : ""), MediaType.TEXT_PLAIN);
			}
		} catch (XPathExpressionException e) {
			e.printStackTrace();
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL, "XPathExpressionException");
		} catch (InterruptedException e) {
			e.printStackTrace();
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL, "InterruptedException");
		} catch (UnsupportedEncodingException e1) {
			e1.printStackTrace();
			throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST, "UnsupportedEncodingException");
		} catch (IOException e) {
			e.printStackTrace();
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL, "IOException");
		}
    }  
}  
