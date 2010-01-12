package client;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.naming.NamingException;

import org.restlet.data.Response;
import org.restlet.resource.DomRepresentation;
import org.restlet.resource.Representation;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import db.FileItem;

/**
 * Handle the list or ls command to list the files.
 * Please see the usage below on details.
 * 
 * @author Mamta
 */
public class ListCommand implements IClientCommand {

	public static final String usage = "[fname | *part* | dev:fname | dev:*part* | dev:]\n" +
		" without any argument, print files in current directory on selected device.\n" +
		" fname: print the file named 'fname' on any device in any directory.\n" +
		" *part*: print the file name containing 'part' on any device in any directory.\n" +
		" dev: work only on the given device name for file listing in all directory.\n" +
		"   'all' and 'gdocs' are treated as special device names.";

	private boolean onlyCurrentDir = false; // whether list is called without argument or not?
	
	public void exec(String args) {
		args = (args != null ? args.trim() : null);
		onlyCurrentDir = (args == null || args.length() == 0);
		
		Representation entity = getFilesList(args);
		if (entity != null) {
			printFiles(entity);
		}
	}
	
	/**
	 * This is the core function to get the list of files as XML representation.
	 * This is used by both exec function above as well as in copy command when source
	 * is a file on remote device.
	 * The function uses REST URLs of the form /device/xml/filelist?something=something
	 * similar to browser REST API.
	 * 
	 * @param args
	 * @return
	 */
	public static Representation getFilesList(String args) {
		ClientWithToken client = new ClientWithToken();
		ClientConfig config = ClientConfig.getInstance();
		String url;
		if (args == null || args.length() == 0) {
			url = "/" + config.getCurrentDevice() + "/xml/filelist";
		}
		else {
			if (args.contains(":")) {
				int index = args.indexOf(':');
				url = "/" + args.substring(0, index) + "/xml/filelist";
				if (index < args.length()) {
					args = args.substring(index+1);
				}
			}
			else {
				url = "/all/xml/filelist";
			}
			
			if (args.contains(" ") && args.startsWith("after ")) {
				String date = args.substring(6);
				url = url + "?modifiedsince=" + date; 
			}
			else if (args.startsWith("*") && args.endsWith("*")) {
				url = url + "?contains=" + args.substring(1, args.length()-1);
			}
			else if (args.length() > 0) {
				url = url + "?matches=" + args;
			}
		}
		
		Response response = client.get(url);
		if (response.getStatus().isSuccess()) {
			return response.getEntity();
		}
		return null;
	}
	
	/**
	 * Print the listing of files from the XML representation received in the 
	 * REST response.
	 * 
	 * @param entity
	 */
	private void printFiles(Representation entity) {
		try {
			DomRepresentation dom = new DomRepresentation(entity);
			Document doc = dom.getDocument();
			NodeList deviceNodes = doc.getFirstChild().getChildNodes();
			if (deviceNodes != null && deviceNodes.getLength() > 0) {
				Set<String> subdir = new HashSet<String>();
				ClientConfig config = ClientConfig.getInstance();
				
				for (int i=0; i<deviceNodes.getLength(); ++i) {
					Element deviceNode = (Element) deviceNodes.item(i);
					NodeList fileNodes = deviceNode.getElementsByTagName("FileList").item(0).getChildNodes();
					List<FileItem> fileItems = new LinkedList<FileItem>();
					
					if (onlyCurrentDir && config.getCurrentDir().length() > 0) {
						FileItem fileItem = new FileItem("..", "", -1, -1, false);
						fileItems.add(fileItem);
					}
					
					for (int j=0; j<fileNodes.getLength(); ++j) {
						Element fileNode = (Element) fileNodes.item(j);
						if (!onlyCurrentDir) {
							fileItems.add(new FileItem(fileNode));
						}
						else {
							FileItem fileItem = new FileItem(fileNode);
							if (config.getCurrentDir().equalsIgnoreCase(fileItem.path)) {
								fileItems.add(fileItem);
							}
							else if (fileItem.path.startsWith(config.getCurrentDir())) {
								String remaining = fileItem.path.substring(config.getCurrentDir().length());
								if (remaining.startsWith("/"))
									remaining = remaining.substring(1);
								int index = remaining.indexOf('/');
								String firstPart = index >= 0 ? remaining.substring(0, index) : remaining;
								if (!subdir.contains(firstPart)) {
									subdir.add(firstPart);
									fileItem.lastModified = -1;
									fileItem.name = firstPart + "/";
									fileItem.path = "";
									fileItem.size = -1;
									fileItem.deleted = false;
									fileItem.backup = null;
									fileItems.add(fileItem);
								}
							}
							else {
								// do not need to list parent directory
							}
						}
					}
					
					SimpleDateFormat format = new SimpleDateFormat("MM-dd-yyyy HH:mm");
					
					System.out.println(deviceNode.getElementsByTagName("Name").item(0).getTextContent() + ": "
							+ deviceNode.getElementsByTagName("OnlineStatus").item(0).getTextContent());
					for (Iterator<FileItem> it=fileItems.iterator(); it.hasNext(); ) {
						FileItem fileItem = it.next();
						
						System.out.format("  %-25s %6s %17s %s\n", fileItem.name
								+ (fileItem.deleted ? " (deleted)" : ""), 
								(fileItem.size >= 0 ? String.valueOf(fileItem.size) : ""),
								fileItem.lastModified >= 0 ? format.format(fileItem.lastModified) : "", 
								fileItem.path); 
					}
				}
			}
			else {
				System.out.println("no files");
			}
		} catch (IOException e) {
			e.printStackTrace();
		} catch (NamingException e) {
			e.printStackTrace();
		}
	}
}
