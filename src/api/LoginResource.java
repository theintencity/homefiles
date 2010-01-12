package api;

import javax.naming.NamingException;
import javax.xml.xpath.XPathExpressionException;

import org.restlet.Context;  
import org.restlet.data.Request;  
import org.restlet.data.Response;  
import org.restlet.data.Status;

import db.Database;
  
/**
 * The login resource performs authentication using GoogleDocs's class and
 * on failure redirects again to Google's authentication page, and on
 * success redirects to the filelisting for all devices at URL
 * "/all/html/filelist".
 * 
 * @see GoogleDocs
 */
public class LoginResource extends BaseResource {  

	public static final String FILE_LIST_URL = "/all/html/filelist";
	
	/**
	 * Construct the resource.
	 * @param context
	 * @param request
	 * @param response
	 */
    public LoginResource(Context context, Request request, Response response) {
    	
        super(context, request, response);
  
        if (!authenticate())
        	return;
        
		String username = getUsername();
		Database db = Database.getInstance();
		
		try {
			db.addUsername(username);
		} catch (XPathExpressionException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (NamingException e) {
			e.printStackTrace();
		}
		
		response.setLocationRef(FILE_LIST_URL);
		response.setStatus(Status.REDIRECTION_TEMPORARY);
    }
}
