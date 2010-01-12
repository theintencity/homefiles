package client;

import java.io.IOException;

import org.restlet.data.Response;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Handler to change or display the current local root directory for the user
 * on a particular device.
 * 
 * @author Mamta
 */
public class RootDirCommand implements IClientCommand {
	public static final String usage = " [path]\n" +
		" change the top-level dir for logged in device.\n" +
		" without <path>, print the current top-level dir for logged in device.\n" +
		" use 'device' command to change the logged in device.";

	public void exec(String args) throws IOException {
		args = (args != null ? args.trim() : args);
		ClientWithToken client = new ClientWithToken();
		ClientConfig config = ClientConfig.getInstance();
		
		if (args == null || args.length() == 0) {
			Response response = client.get("/settings");
			if (response.getStatus().isSuccess()) {
				Document doc = response.getEntityAsDom().getDocument();
				try {
					Element settingsNode = (Element) doc.getFirstChild();
					Element rootNode = (Element) settingsNode.getElementsByTagName("Root").item(0);
					String rootdir = rootNode.getAttribute("directory");
					System.out.println(config.getLoginDevice().getName() + ": " + rootdir);
				}
				catch (Exception e) {
					System.out.println("cannot get root directory");
				}
			}
			else {
				System.out.println("cannot get root directory " + response.getStatus().toString());
			}
		}
		else {
			Response response = client.get("/settings?rootdir=" + args);
			if (!response.getStatus().isSuccess()) {
				System.out.println("cannot change root directory: " + response.getStatus().toString());
			}
		}
	}
}
