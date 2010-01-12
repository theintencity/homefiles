package dev;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import javax.xml.xpath.XPathExpressionException;

import org.restlet.Client;
import org.restlet.Context;  
import org.restlet.data.Form;
import org.restlet.data.MediaType;  
import org.restlet.data.Protocol;
import org.restlet.data.Request;  
import org.restlet.data.Response;  
import org.restlet.data.Status;
import org.restlet.resource.FileRepresentation;
import org.restlet.resource.Representation;  
import org.restlet.resource.ResourceException;  
import org.restlet.resource.StringRepresentation;
import org.restlet.resource.Variant;  
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import api.BaseResource;

import db.Database;
import db.FileUtil;


/**
 * The resource represents a backup data. When GET it initiates a backup
 * request, and when PUT it sends backup file from one machine to another.
 * The GET is called with ?count= parameter from the browser. From the 
 * browser the command= parameter either "backup" or "restore" determines
 * what to do. Without a parameter, this is used for sending backup
 * to another machine or getting backup from another machine (restore).
 */
public class BackupdataResource extends BaseResource {  
  
	/**
	 * Configuration set by main to store the list of devices.
	 */
	public static DeviceUpdater deviceUpdater;
	
	/**
	 * Configuration set by main to store the backup directory.
	 */
	public static String backup_dir;
	
	/**
	 * Construct the resource. Do not call authenticate here, but call it when
	 * processing the command.
	 * 
	 * @param context
	 * @param request
	 * @param response
	 */
    public BackupdataResource(Context context, Request request, Response response) {
    	super(context, request, response);
    	
    	getVariants().add(new Variant(MediaType.TEXT_XML));
    }
  
    /**
     * PUT is supported.
     */
    @Override 
    public boolean allowPut() {
    	return true;
    }
    
    /** 
     * Handle the GET from browser with command=restore or command=backup
     * or GET from another machine for restore.
     */  
    @Override  
    public Representation represent(Variant variant) throws ResourceException {

		// get the command first (backup or restore) if used from web browser.
		Form form = getRequest().getResourceRef().getQueryAsForm();
		String command = form.getFirstValue("command");
		
		// the file path that needs to be backed up
		String path = getRequest().getResourceRef().getRemainingPart();
		if (path.indexOf('?') >= 0)
			path = path.substring(0, path.indexOf('?'));
		
    	Representation result;
		if (command != null) {
			// this is from the browser, hence must authenticate first.
			
			if (!authenticate())
				return null;

			Element fileNode = null;
			try {
				fileNode = getFileNode(gdocs.getUsername(), path);
			}
			catch (Exception e) {
				// resource exception is thrown next
			}
			
	    	if (fileNode == null)
	    		throw new ResourceException(Status.CLIENT_ERROR_NOT_FOUND, "File metadata not found");
			
	    	// process the command by invoking other functions
			if (command.equals("backup")) {
				// the browser is requesting a backup
				result = startBackup(fileNode);
			}
			else if (command.equals("restore")) {
				// the browser is requesting a restore
				result = startRestore(fileNode, path);
			}
			else {
				throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST, "Invalid command name: " + command);
			}
		}
		else {
			// another machine is requesting a restore.
			result = restore(path);
		}
		
		return result;
    }
    
    /**
     * Handle the PUT when other machines sends backup data to this.
     * It creates a file in the backup_dir
     */
    @Override
    public void storeRepresentation(Representation entity) throws ResourceException {
		try {
			File file = new File(backup_dir + "/" + getRequest().getResourceRef().getRemainingPart());
			System.out.println("  creating backup at " + file.getPath());
			File parent = file.getParentFile();
			parent.mkdirs();
			FileOutputStream out = new FileOutputStream(file);
			InputStream in = entity.getStream();
			int c;
			while ((c = in.read()) != -1) 
				out.write(c);
			out.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
    }

    /**
     * Get the file element for the given username and path from the XML database.
     * 
     * @param username
     * @param path
     * @return
     * @throws XPathExpressionException
     * @throws InterruptedException
     */
    private Element getFileNode(String username, String path) throws XPathExpressionException, InterruptedException {
		File file = new File(path);
		path = file.getParent();
		if (path == null)
			path = "";
		path = path.replace('\\', '/'); 
		
    	Database db = Database.getInstance();
		NodeList nodes = db.matchFiles(username, db.getLocalDevice(), file.getName());
		Element found = null;
		for (int i=0; i<nodes.getLength(); ++i) {
			Element fileNode = (Element) nodes.item(i);
			if (path.equals(fileNode.getElementsByTagName("Path").item(0).getTextContent())) {
				found = fileNode;
				break;
			}
		}
		
		return found;
    }
    
    /**
     * Handle the backup command from the browser. It sets the count attribute of the 
     * file in XML database to be desired number of backups. The background 
     * thread in BackupThread takes care of actually performing backups.
     * 
     * @param fileNode
     * @return
     * @throws ResourceException
     */
    private Representation startBackup(Element fileNode) throws ResourceException {
    	String count = getRequest().getResourceRef().getQueryAsForm().getFirst("count").getValue();
		Element backup = (Element) fileNode.getElementsByTagName("Backup").item(0);
		backup.setAttribute("count", count);
		while (backup.hasChildNodes())
			backup.removeChild(backup.getFirstChild());
		System.out.println("  scheduling backup to count=" + count);
		try {
			Database.printNode(fileNode, System.out);
		} catch (Exception e) {
			// ignore the debug print exception
		}
		return new StringRepresentation("Backup is scheduled", MediaType.TEXT_PLAIN);
    }
    
    /**
     * Handle the restore command from the browser. It locates the File element in the
     * XML database, finds all the File/Backup/Location items and their current IP:port
     * from the online devices list, and tries to fetch backup from one of that device.
     * In case of failure, it returns the appropriate error response.
     * 
     * @param fileNode
     * @param path
     * @return
     * @throws ResourceException
     */
    private Representation startRestore(Element fileNode, String path) throws ResourceException {
		Element backup = (Element) fileNode.getElementsByTagName("Backup").item(0);
				
		if (!backup.hasChildNodes()) {
			getResponse().setStatus(Status.CLIENT_ERROR_NOT_FOUND, "No backup location found");
			return new StringRepresentation("No backup location found for this file", MediaType.TEXT_PLAIN);
		}
		
		NodeList locationNodes = backup.getElementsByTagName("Location");
		
		// sort the locations in decreasing order of modified attribute
		List<Node> locations = new LinkedList<Node>();
		for (int i=0; i<locationNodes.getLength(); ++i) {
			locations.add(locationNodes.item(i));
		}
		Comparator<Node> compare = new Comparator<Node>() {
			public int compare(Node o1, Node o2) {
				long m1 = Long.parseLong(((Element) o1).getAttribute("modified"));
				long m2 = Long.parseLong(((Element) o1).getAttribute("modified"));
				return (int)(m2 - m1);
			}
			public boolean equals(Object obj) {
				return obj == this;
			}
		};
		Collections.sort(locations, compare);
		
		// not try to fetch the backup from the locations using REST API
		Client client = new Client(new Context(), Protocol.HTTP);
		client.getContext().getParameters().add("converter", 
				"com.noelios.restlet.http.HttpClientConverter");
		
		Database db = Database.getInstance();
		for (Iterator<Node> it=locations.iterator(); it.hasNext(); ) {
			Element location = (Element) it.next();
			Device dev = deviceUpdater.getDevice(location.getTextContent());
			if (dev != null) {
				String url = "http://" + dev.getIp() + ":" + dev.getPort() + "/backupdata/" + path; 
				System.out.println("GET " + dev.getName() + " " + url);
				Response response = client.get(url);
				if (response.getStatus().isSuccess()) {
					Representation repr = response.getEntity();
					try {
						String filename = FileUtil.getFullPath(path, db.getUserLocalDir(gdocs.getUsername()));
						File file = new File(filename);
						File parent = new File(file.getParent().replace('\\', '/')); // parent path
						parent.mkdirs();
						
						InputStream in = repr.getStream();
						if (in != null) {
							FileOutputStream out = new FileOutputStream(file);
							FileUtil.copyStream(in, out);
							return new StringRepresentation("Restore complete", MediaType.TEXT_PLAIN);
						}
					} catch (FileNotFoundException e) {
						e.printStackTrace();
					} catch (IOException e) {
						e.printStackTrace();
					} catch (XPathExpressionException e) {
						e.printStackTrace();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					
				}
				else {
					System.out.println("  received a failure response: " + response.getStatus().toString());
				}
			}
		}
		
		getResponse().setStatus(Status.CLIENT_ERROR_NOT_FOUND, "No active location available");
		return new StringRepresentation("No active backup location available at this time. Please try again later.", MediaType.TEXT_PLAIN);
    }
    
    /**
     * Handle the GET request from another machine by returning the file
     * content from our backup directory. In case of error in file name path
     * it returns the appropriate response.
     * 
     * @param path
     * @return
     */
    private Representation restore(String path) {
        try {
        	// if file name as spaces, it gets URL encoded. So first decode it.
			path = URLDecoder.decode(path, "US-ASCII");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
			getResponse().setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
			return new StringRepresentation("Unsupported encoding for filename", MediaType.TEXT_PLAIN);
		}
		
        if (path != null && (path.startsWith("/") || path.startsWith("\\") || path.contains(".."))) {
        	getResponse().setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
        	return new StringRepresentation("Invalid file name: cannot start with /, \\ or contain .. for security reason.\nfilename=" + path, MediaType.TEXT_PLAIN);
        }
        
    	String filepath = backup_dir + "/" + path;
    	System.out.println("  restore returning file " + filepath);
    	return new FileRepresentation(new File(filepath), MediaType.APPLICATION_OCTET_STREAM);
    }
}  
