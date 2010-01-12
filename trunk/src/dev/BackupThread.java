package dev;

import java.io.File;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Vector;

import javax.naming.NamingException;
import javax.xml.xpath.XPathExpressionException;

import org.restlet.Client;
import org.restlet.Context;
import org.restlet.data.MediaType;
import org.restlet.data.Protocol;
import org.restlet.resource.FileRepresentation;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import db.Database;
import db.FileItem;
import db.LocalDir;


/**
 * The BackupThread periodically gathers all the File items for which number of
 * Backup/Location is less than Backup/@count and tries to backup that file to
 * one more online device.
 * 
 * @author Mamta
 */
public class BackupThread implements Runnable {
	/**
	 * The configuration for list of current devices is set by main.
	 */
	public static DeviceUpdater deviceUpdater;
	
	/**
	 * The associated database.
	 */
	private Database db;
	
	/**
	 * The periodic interval.
	 */
	private long interval;
	
	/**
	 * Random number is used to select a random device for backup.
	 */
	private Random random;
	
	/**
	 * The client for restlet.
	 */
	private Client client;
	
	/**
	 * Construct a new thread object.
	 * 
	 * @param db
	 * @param interval
	 */
	public BackupThread(Database db, long interval) {
		this.db = db;
		this.interval = interval;
		random = new Random();
		
		client = new Client(new Context(), Protocol.HTTP);
		client.getContext().getParameters().add("converter", 
				"com.noelios.restlet.http.HttpClientConverter");
	}
	
	/**
	 * The thread function periodically calls the update function on the local device
	 * for all the users.
	 */
	public void run() {
		while (true) {
			try {
				System.out.println("  BackupThread: waiting for " + interval + "...");
				Thread.sleep(interval);
				System.out.println("  BackupThread: wait completed");
			} catch (InterruptedException e) {				
				e.printStackTrace();
			}
			
			List<LocalDir> list;
			try {
				list = db.getLocalDir();
			} catch (XPathExpressionException e1) {				
				e1.printStackTrace();
				break;
			} catch (InterruptedException e) {				
				e.printStackTrace();
				break;
			}
			
			for (Iterator<LocalDir> it=list.iterator(); it.hasNext(); ) {
				LocalDir localDir = it.next();
				update(localDir.userName, localDir.deviceName);
			}
		}
	}
	
	/**
	 * The update method gets all the files for the given user and device,
	 * and if a backup is required, then uses sendBackupdata to take a backup
	 * and add another Location element.
	 *  
	 * @param userName
	 * @param deviceName
	 * @return
	 */
	private void update(String userName, String deviceName) {
		try {
			// get all the files first
			NodeList files = db.getFiles(userName, deviceName);
			
			// for each file, check the backup status
			for (int i=0; files != null && i<files.getLength(); ++i) {
				Element file = (Element) files.item(i);
				String deleted = file.getElementsByTagName("Deleted").item(0).getTextContent();
				Element backup = (Element) file.getElementsByTagName("Backup").item(0);
				
				// do not do backup f deleted files or if no count attribute is present
				if (deleted != null && deleted.equals("yes") || !backup.hasAttribute("count"))
					continue;
				
				
				int count = Integer.valueOf(backup.getAttribute("count")).intValue();
				NodeList locations = backup.getElementsByTagName("Location");
				String lastModified = file.getElementsByTagName("LastModified").item(0).getTextContent();
				
				// the number of valid and latest backups
				int backupCount = 0;
				for (int j=0; j<locations.getLength(); ++j) {
					Element location = (Element) locations.item(j);
					if (location.hasAttribute("modified") && lastModified.equals(location.getAttribute("modified"))) {
						++backupCount;
					}
				}
				
				// if count is more than current number of backup Location elements
				// then take more backups
				if (backupCount < count) {
					// we need to do some data backup
					
					// first find all the remaining online devices from the current
					// online devices, which are not local and not in Location already.
					Device[] onlineDevices = deviceUpdater.getDevices();
					Vector<Device> remaining = new Vector<Device>();
					for (int j=0; j<onlineDevices.length; ++j) {
						if (db.getLocalDevice().equals(onlineDevices[j].getName()))
							continue;
						
						boolean found = false;
						for (int k=0; k<locations.getLength(); ++k) {
							Element location = (Element) locations.item(k);
							if (onlineDevices[j].getName().equals(location.getTextContent())
								&& location.hasAttribute("modified")
								&& lastModified.equals(location.getAttribute("modified"))) {
								found = true;
								break;
							}
						}
						
						if (!found) {
							remaining.add(onlineDevices[j]);
						}
					}
					
					// if there are potential remaining devices, then
					// randomly pick one and try to send backup to that.
					// Repeat until needed.
					while (backupCount < count && !remaining.isEmpty()) {
						int index = random.nextInt(remaining.size());
						Device dev = remaining.get(index);
						remaining.remove(index);
						
						try {
							sendBackupdata(file, dev, db.getUserLocalDir(userName));
							Element location = db.getDoc().createElement("Location");
							location.setAttribute("modified", lastModified);
							location.appendChild(db.getDoc().createTextNode(dev.getName()));
							backupCount += 1;
							
							Node oldLocation = null;
							for (int k=0; k<locations.getLength(); ++k) {
								Element loc = (Element) locations.item(k);
								if (loc.getTextContent().equals(location.getTextContent())) {
									oldLocation = loc;
									break;
								}
							}
							
							if (oldLocation == null)
								backup.appendChild(location);
							else
								backup.replaceChild(location, oldLocation);
							db.incrVersion(db.getLocalDevice());
							db.setDirty();
						}
						catch (Exception e) {
							e.printStackTrace();
						}
						
						// add a new backup Location
						locations = backup.getElementsByTagName("Location");
					}
				}
			}
		} catch (XPathExpressionException e) {		
			e.printStackTrace();
			return;
		} catch (InterruptedException e) {			
			e.printStackTrace();
			return;
		}
	}
	
	/**
	 * Send a backup of local file to the given device using Restlet client.
	 *  
	 * @param fileNode
	 * @param dev
	 * @throws NamingException
	 */
	private void sendBackupdata(Element fileNode, Device dev, String localDir) throws NamingException {
		FileItem fileItem = new FileItem(fileNode);
		String url = "http://" + dev.getIp() + ":" + dev.getPort() + "/backupdata/" + (fileItem.path.equals("") ? "" : fileItem.path + "/")  +  fileItem.name;
		System.out.println("PUT " + dev.getName() + " " + url);
		String path = (fileItem.path.equals("") ? "" : fileItem.path + "/")  +  fileItem.name;
		File file = new File(localDir + "/" + path);
		client.put(url, new FileRepresentation(file, MediaType.APPLICATION_OCTET_STREAM));
	}
}
