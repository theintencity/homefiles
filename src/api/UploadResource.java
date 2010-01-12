package api;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

import javax.xml.xpath.XPathExpressionException;

import org.restlet.Context;  
import org.restlet.data.MediaType;  
import org.restlet.data.Request;  
import org.restlet.data.Response;  
import org.restlet.data.Status;
import org.restlet.resource.Representation;  
import org.restlet.resource.ResourceException;  
import org.restlet.resource.StringRepresentation;
import org.restlet.resource.Variant;  

import com.google.gdata.util.AuthenticationException;

import db.Database;
import db.FileUtil;

/** 
 * Resource which represents a single file, and is used to upload the file to 
 * user's GoogleDocs account. Only local files can be uploaded. That means a 
 * GoogleDocs file can not be re-uploaded using this class. If the upload fails
 * it returns appropriate error code and a text message body. If the upload is
 * successful, it prints the appropriate message for that.
 */  
public class UploadResource extends BaseResource {  
  
	// construct the resource
    public UploadResource(Context context, Request request, Response response) {
    	
        super(context, request, response);  
        
        if (!authenticate())
        	return;
  
        // This representation has only one type of representation.  
        getVariants().add(new Variant(MediaType.TEXT_PLAIN));
    }
  
    /** 
     * Perform upload to google docs, and return any error or success response. 
     */  
    @Override  
    public Representation represent(Variant variant) throws ResourceException {
    	Request request = getRequest();
    	Response response = getResponse();
    	
    	Database db = Database.getInstance();
    	
        try {
        	// if file name as spaces, it gets URL encoded. So first decode it.
			String filename = URLDecoder.decode(request.getResourceRef().getRemainingPart(), "US-ASCII");
			
			if (filename != null && filename.indexOf('?') >= 0) {
				filename = filename.substring(0, filename.indexOf('?'));
			}
			
    		if (filename == null || filename.equals("")) {
    			response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
    			return new StringRepresentation("Please supply a file path .../xml/file/path/to/file.ext where path/to/file.ext is file path.", MediaType.TEXT_PLAIN);
    		}
			
	        if (filename != null && (filename.startsWith("/") || filename.startsWith("\\") || filename.contains(".."))) {
	        	response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
	        	return new StringRepresentation("Invalid file name: cannot start with /, \\ or contain .. for security reason.\nfilename=" + filename, MediaType.TEXT_PLAIN);
	        }

			try {
				filename = FileUtil.getFullPath(filename, db.getUserLocalDir(gdocs.getUsername()));
			} catch (XPathExpressionException e) {
				e.printStackTrace();
				throw new ResourceException(Status.SERVER_ERROR_INTERNAL, e.getMessage());
			} catch (InterruptedException e) {
				e.printStackTrace();
				throw new ResourceException(Status.SERVER_ERROR_INTERNAL, e.getMessage());
			}
	        
			File file = new File(filename);
			
	    	if (!file.exists() || !file.isFile()) {
	    		response.setStatus(Status.CLIENT_ERROR_NOT_FOUND);
	    		return new StringRepresentation( "Not a valid regular file: " + filename, MediaType.TEXT_PLAIN);
	    	}
	    	
			// try to upload the file using our GoogleDocs object.
			gdocs.uploadFile(file);
			
			return new StringRepresentation("Upload successful", MediaType.TEXT_PLAIN);
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
			response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
			return new StringRepresentation("Unsupported encoding for filename", MediaType.TEXT_PLAIN);
		} catch (AuthenticationException e) {
			// if there is an authentication error, then re-start the authentication
			e.printStackTrace();
			gdocs.startAuth(request, response);
			return null;
		} catch (Exception e) {
			// for all other errors, respond with the error.
			e.printStackTrace();
    		response.setStatus(Status.SERVER_ERROR_INTERNAL);
			return new StringRepresentation("Error: " + e.getMessage(), MediaType.TEXT_PLAIN);
		}
    }  
}  
