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
import org.restlet.resource.FileRepresentation;
import org.restlet.resource.Representation;  
import org.restlet.resource.ResourceException;  
import org.restlet.resource.StringRepresentation;
import org.restlet.resource.Variant;  

import db.Database;
import db.FileUtil;

/** 
 * Resource which represents a single file, and is used to download the file content.
 * It always responds with either error or 200 OK with content as 
 * octet stream type. In case of error it sets the status to 404 if the resource is
 * not found, and 400 if the resource is not a readable regular file or some other 
 * error.
 * 
 * For the client implementation, this is modified to support both upload and download
 * of the files. The upload is done by the client using PUT method.
 */  
public class FileDownloadResource extends BaseResource {  
  
	/**
	 * Construct a new resource.
	 * 
	 * @param context
	 * @param request
	 * @param response
	 */
    public FileDownloadResource(Context context, Request request, Response response) {
    	
        super(context, request, response);  

        if (!authenticate())
        	return;
        
        getVariants().add(new Variant(MediaType.APPLICATION_OCTET_STREAM));
    }
    
    /**
     * Allow the put method.
     */
    @Override 
    public boolean allowPut() {
    	return true;
    }
  
    /** 
     * Returns the FileRepresentation for the specified file. The error if returned if
     * file name is invalid, device is not local or all, or file is not a regular file.
     */  
    @Override  
    public Representation represent(Variant variant) throws ResourceException {

    	Request request = getRequest();
    	Response response = getResponse();
    	
        String devicename = (String) request.getAttributes().get("devicename");
        
    	// the name of the file represented by this resource
    	String filename = null;
    	
    	Database db = Database.getInstance();
    	
		if (devicename.equals("all") || devicename.equals(db.getLocalDevice())) {
	        try {
	        	// if file name as spaces, it gets URL encoded. So first decode it.
				filename = URLDecoder.decode(request.getResourceRef().getRemainingPart(), "US-ASCII");
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
				response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
				return new StringRepresentation("Unsupported encoding for filename", MediaType.TEXT_PLAIN);
			}
			
	        if (filename != null && (filename.startsWith("/") || filename.startsWith("\\") || filename.contains(".."))) {
	        	response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
	        	return new StringRepresentation("Invalid file name: cannot start with /, \\ or contain .. for security reason.\nfilename=" + filename, MediaType.TEXT_PLAIN);
	        }
        }
        else {
        	response.setStatus(Status.CLIENT_ERROR_NOT_FOUND);
        	return new StringRepresentation("devicename of all or local supported.\n"
        			+ "remote devices or gdocs not supported in file download.",
        			MediaType.TEXT_PLAIN);
        }
        
		if (filename == null  || filename.equals("")) {
			response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
			return new StringRepresentation("Please supply a file path .../xml/file/path/to/file.ext where path/to/file.ext is file path.", MediaType.TEXT_PLAIN);
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
    		return new StringRepresentation("Not a valid regular file: " + filename, MediaType.TEXT_PLAIN);
    	}
    	
    	Representation result = new FileRepresentation(file, MediaType.APPLICATION_OCTET_STREAM);
    	return result;
    }  
    
    /**
     * Store the received file representation to a local file. If the file exists, it
     * will silently overwrite the file. The file name and path is derived from
     * the remaining part in the resource reference, and is relative to the 
     * local directory of the requesting user.
     */
    @Override  
    public void storeRepresentation(Representation entity) throws ResourceException {

    	Request request = getRequest();
    	
        String devicename = (String) request.getAttributes().get("devicename");
        
    	// the name of the file represented by this resource
    	String filename = null;
    	
    	Database db = Database.getInstance();
    	
		if (devicename.equals("all") || devicename.equals(db.getLocalDevice())) {
	        try {
	        	// if file name as spaces, it gets URL encoded. So first decode it.
				filename = URLDecoder.decode(request.getResourceRef().getRemainingPart(), "US-ASCII");
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
				throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST, "Unsupported encoding for filename");
			}
			
	        if (filename != null && (filename.startsWith("/") || filename.startsWith("\\") || filename.contains(".."))) {
	        	throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST, "Invalid filename");
	        }
        }
        else {
        	throw new ResourceException(Status.CLIENT_ERROR_NOT_FOUND, "Device name of only all or local allowed");
        }
        
		if (filename == null  || filename.equals("")) {
			throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST, "Missing file path");
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
			
    	if (file.exists() && !file.isFile()) {
    		throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST, "The name already exists and not a file");
    	}
    	
    	if (file.exists()) {
    		System.out.println("overwriting " + file.getPath());
    	}
    	
    	boolean done = FileUtil.copyStream(entity, file, false);
    	if (!done) {
    		throw new ResourceException(Status.SERVER_ERROR_INTERNAL, "Cannot upload file");
    	}
    }  
}  
