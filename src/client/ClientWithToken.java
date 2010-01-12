package client;

import org.restlet.Client;
import org.restlet.Context;
import org.restlet.data.Form;
import org.restlet.data.Method;
import org.restlet.data.Protocol;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.resource.Representation;

import dev.Device;

/**
 * The wrapper class around restlet's client to add the authentication token to
 * be sent to the server. It adds a new header "x-token" with a string containing
 * the user name and the token. The server will use this request credentials to
 * perform actual command execution as needed.
 * 
 * @author Mamta
 */
public class ClientWithToken {
	private Client client;
	private Device loginDevice;
	
	/**
	 * Construct a new client object.
	 */
	public ClientWithToken() {
		client = new Client(new Context(), Protocol.HTTP);
		client.getContext().getParameters().add("converter", 
			"com.noelios.restlet.http.HttpClientConverter");
		
		loginDevice = ClientConfig.getInstance().getLoginDevice(true);
		if (loginDevice == null) {
			System.out.println("No device available.");
			return;
		}
	}
	
	/**
	 * Perform a GET on a resource path. The path can be "http://..." or just "/path/to/resource"
	 * where the second case uses the default login device as the server part.
	 * The response is returned by the method.
	 * 
	 * @param path
	 * @return
	 */
	public Response get(String path) {
		String url;
		if (path.startsWith("http://"))
			url = path;
		else
			url = loginDevice.getURL() + path;
		Request request = new Request(Method.GET, url);
		Form authForm = new Form();
		authForm.add("x-token", LoginCommand.loadToken());
		request.getAttributes().put("org.restlet.http.headers", authForm);
		Response response = client.handle(request);
		return response;
	}
	
	/**
	 * Perform a PUT on a resource path. The path can be "http://..." or just "/path/to/resource"
	 * where the second case uses the default login device as the server part.
	 * The supplied representation of the entity is sent in the request body to the
	 * server.
	 * 
	 * @param path
	 * @return
	 */
	public Response put(String path, Representation entity) {
		String url;
		if (path.startsWith("http://"))
			url = path;
		else
			url = loginDevice.getURL() + path;
		Request request = new Request(Method.PUT, url);
		Form authForm = new Form();
		authForm.add("x-token", LoginCommand.loadToken());
		request.getAttributes().put("org.restlet.http.headers", authForm);
		request.setEntity(entity);
		Response response = client.handle(request);
		return response;
	}
}
