import org.restlet.Application;  
import org.restlet.Component;
import org.restlet.Restlet;  
import org.restlet.Router; 
import org.restlet.data.Protocol;

import dev.DevicesResource;


/**
 * The main entry point in the name server application.
 * This implements the /devices resource to keep a list of online
 * devices. It uses the DevicesResource class.
 * 
 * @author Mamta
 */
public class NameServer extends Application {  
  
    /** 
     * Creates a root Restlet that will receive all incoming calls. 
     */  
    @Override  
    public Restlet createRoot() {  
        // Create a router Restlet that routes each call to a new resource 
        Router router = new Router(getContext());  
  
        // Defines route  
        router.attach("/devices", DevicesResource.class);
        
        return router;  
    }
    
    /**
     * The main routine for nameserver. It takes the listening port from
     * command line argument, which defaults to 2500.
     * 
     * @param args
     */
    public static void main(String[] args) {
        try {
        	// read the configuration file
        	int port = 2500; // change using command line
        	
        	if (args.length > 0) {
        		port = Integer.valueOf(args[0]).intValue();
        	}
        	
        	if (port <= 1024 || port >= 65536) {
        		System.err.println("port must be > 1024 and < 65536. port=" + port);
        		return;
        	}
        	
        	// initialize the devices resource
        	DevicesResource.init();
        	
            // Create a new Component.
            Component component = new Component();

            // Add a new HTTP server listening on port.
            component.getServers().add(Protocol.HTTP, port);

            // Attach the application.
            component.getDefaultHost().attach(new NameServer());

            // Start the component.
            component.start();
        } catch (Exception e) {
            // Something is wrong.
            e.printStackTrace();
        }
    }
}  
