package api;

import org.restlet.Context;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.resource.Resource;

/**
 * Base class to support common functions among the resources, e.g., 
 * authentication and authenticated username.
 */
public class BaseResource extends Resource {
	
	/**
	 * The object to perform authentication.
	 */
	protected GoogleDocs gdocs;
	
	/**
	 * Construct the object.
	 * 
	 * @param context
	 * @param request
	 * @param response
	 */
    public BaseResource(Context context, Request request, Response response) {
        super(context, request, response);
    }
    
    /**
     * Perform authentication. This must be called as the first in sub-class
     * constructor after invoking the base class constructor.
     * 
     * @return true on success and false otherwise.
     */
    public boolean authenticate() {
		gdocs = new GoogleDocs();
		String token = gdocs.getToken(getRequest(), getResponse());
		return token != null;
    }
    
    /**
     * Get the authenticated username.
     * 
     * @return The authenticated username.
     */
    public String getUsername() {
    	return gdocs.getUsername();
    }
}
