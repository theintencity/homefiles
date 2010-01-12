package api;

import org.restlet.Context;  
import org.restlet.data.MediaType;
import org.restlet.data.Request;  
import org.restlet.data.Response;  
import org.restlet.resource.Representation;
import org.restlet.resource.Resource;  
import org.restlet.resource.ResourceException;
import org.restlet.resource.StringRepresentation;
import org.restlet.resource.Variant;
  
/**
 * The logout resource clears the cookies and sends the user to the login page.
 * 
 * @see GoogleDocs
 */
public class LogoutResource extends Resource {  
  
	/**
	 * Construct the resource.
	 * 
	 * @param context
	 * @param request
	 * @param response
	 */
    public LogoutResource(Context context, Request request, Response response) {
    	
        super(context, request, response);
  
		GoogleDocs gdocs = new GoogleDocs();
		gdocs.clearCookie(request, response);

		getVariants().add(new Variant(MediaType.TEXT_HTML));
    }

    /** 
	 * Returns a full representation for a given variant. 
	 */  
	@Override  
	public Representation represent(Variant variant) throws ResourceException {
		
		Representation representation = new StringRepresentation(
			"<html><h2>Logout complete</h2><a href=\"/login\">Login Again</a>", 
			MediaType.TEXT_HTML);
        return representation;
    }  
}
