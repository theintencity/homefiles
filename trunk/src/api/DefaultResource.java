package api;

import org.restlet.Context;  
import org.restlet.data.MediaType;  
import org.restlet.data.Request;  
import org.restlet.data.Response;  
import org.restlet.data.Status;
import org.restlet.resource.Representation;  
import org.restlet.resource.Resource;  
import org.restlet.resource.ResourceException;
import org.restlet.resource.StringRepresentation;  
import org.restlet.resource.Variant;
  
/** 
 * Resource that is used for anything that does not match the handled resource URL. 
 * It responds with "403 forbidden" and a descriptive message body.
 */  
public class DefaultResource extends Resource {  
  
	/**
	 * Construct the default resource.
	 * 
	 * @param context
	 * @param request
	 * @param response
	 */
    public DefaultResource(Context context, Request request,  
            Response response) {
    	
        super(context, request, response);
        
        getVariants().add(new Variant(MediaType.TEXT_HTML));
        getVariants().add(new Variant(MediaType.TEXT_XML));
        getVariants().add(new Variant(MediaType.TEXT_PLAIN));
    }
    
    
    /** 
	 * On GET return the error messages indicating valid URL formats.
	 * For HTML mediatype also provide a link to login. 
	 */  
	@Override  
	public Representation represent(Variant variant) throws ResourceException {
		
		Representation result = null;
		
        // respond with 403 forbidden error
        getResponse().setStatus(Status.CLIENT_ERROR_FORBIDDEN);
        
        // the message.
        String msg =                 
        "Please use the following query format only.\n" +
        "  /login\n" +
        "  /logout\n" +
        "  /settings\n" +
        "  /settings?rootdir={directory}\n" +
		"  /{devicename}/{xml|html}/filelist\n" +
		"  /{devicename}/{xml|html}/filelist?contains={query}\n" + 
		"  /{devicename}/{xml|html}/filelist?matches={query}\n" +
		"  /{devicename}/{xml|html}/filelist?modifiedsince={date} where date is MM-dd-yyyy format\n" +
		"  /{devicename}/{xml|html}/file/{path/to/file/and/filename.ext}\n" +
		"  /gdocs/{xml|html}/filelist\n" + 
		"  /{xml|html}/gdocsupload/path/to/local/file.ext\n" +
		" where devicename may be \"all\" to include all computers or a name of the computer\n" + 
		" Note that if devicename is \"gdocs\" it does not support query format or file.\n"; 

        // also put descriptive text in message body
        if (variant.getMediaType() == MediaType.TEXT_PLAIN) {
        	result = new StringRepresentation(msg, MediaType.TEXT_PLAIN);
        }
        else {
        	result = new StringRepresentation("<html><body>\n" 
        			+ "<a href=\"/login\">Login</a><br/>\n" 
        			+ msg.replaceAll("\n", "<br/>\n") 
        			+ "</body></html>", MediaType.TEXT_HTML);
        }
        return result;
    }  
}  
