package db;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import javax.xml.xpath.XPathExpressionException;

import org.w3c.dom.NodeList;


/**
 * Implements the updater thread with 10 seconds interval. Every 10 seconds, it 
 * gets all the top-level user and their local directory from the Database, and 
 * iterates over those sub-directories. It then updates the database with any files
 * that are added, modified or deleted for local device name for that user.
 *
 * For lab3, changed the constructor to take exportTo argument, and in every interval
 * export the database to that file.
 * 
 * Also modified to have a Listener, which receives events when something is updated
 * in this thread detected using the dirty property of the database and the result
 * of filesystem update.
 * 
 * @author Mamta
 */
public class Updater implements Runnable {
	private Database db;
	private long interval;
	private String exportTo;
	
	/**
	 * Create an updater thread that updates the supplied database using the 
	 * supplied interval.
	 * @param db
	 * @param interval
	 * @param exportTo optional file name to export database to. If null, do not export.
	 */
	public Updater(Database db, long interval, String exportTo) {
		this.db = db;
		this.interval = interval;
		this.exportTo = exportTo;
	}
	
	/**
	 * The Updater invokes the listener object when something is changed in the 
	 * database, if a listener is set. The static is needed because the interface
	 * definition is static instead of per-Object based. The actualy Listener
	 * object defined later is _not_ static. The static interface definition
	 * allows creating a Listener object without a Updater object.
	 */
	public static interface Listener {
		public void updated(Database db);
	}
	
	// the listener for this updater.
	private Listener listener;
	
	/**
	 * set the update listener object.
	 */
	public void setListener(Listener listener) {
		this.listener = listener;
	}
	
	/**
	 * The thread runs a loop. In each loop it first waits for the interval. Then
	 * it gets all the top level users in the database. For each user, it updates
	 * the database using the local directory of that user.
	 */
	public void run() {
		while (true) {
			try {
				System.out.println("  Updater: waiting for " + interval + "...");
				Thread.sleep(interval);
				System.out.println("  Updater: wait completed");
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
			
			boolean updated = false;
			for (Iterator<LocalDir> it=list.iterator(); it.hasNext(); ) {
				LocalDir localDir = it.next();
				if (update(localDir.userName, localDir.deviceName, localDir.local_dir)) {
					updated = true;
				}
			}
			
			if (updated && db.getLocalDevice() != null) {
				// increment local device version if something is updated.
				try {
					//System.out.println("  incrementing version for " + db.getLocalDevice());
					db.incrVersion(db.getLocalDevice());
				} catch (XPathExpressionException e) {
					e.printStackTrace();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			
			boolean dirty = db.isDirty();
			if (dirty)
				db.resetDirty();
			
			if (dirty && listener != null) {
				System.out.println("  Updater: sending updated event");
				listener.updated(db);
			}
			
			if (exportTo != null) {
				try {
					//System.out.println("  exportTo " + exportTo);
					db.exportTo(exportTo);
				} catch (IOException e) {
					e.printStackTrace();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	/**
	 * List all the files in the sub-directory. This is used to recurse through a
	 * sub-directory. Only non-directory File objects are returned in the list.
	 * @param path
	 * @return
	 */
	public List<File> listFiles(File file) {
		List<File> result = new LinkedList<File>();
		File[] files = file.listFiles();
		if (files == null) 
			return result;
		for (int i=0; i<files.length; ++i) {
			if (files[i].isDirectory()) {
				List<File> sub = listFiles(files[i]);
				for (Iterator<File> it=sub.iterator(); it.hasNext(); ) {
					result.add(it.next());
				}
			} 
			else {
				result.add(files[i]);
			}
		}
		return result;
	}
	
	/**
	 * Each iteration of the thread run() invokes the update method to update a
	 * single user's files, for all users in the database.
	 * It first gets the old files from the database, and collects the current
	 * files in the file system for that user's local directory. Then for all files
	 * in the old list, that are present in the new list, it updates the database 
	 * using modify() API call. Similarly, for all files in the new list, that are
	 * not present in the old list, it updates the database using add() API call.
	 * 
	 * For compatibility with Unix files, all file paths are converted to use '/' as
	 * separator. If a file is found to be missing in the file system, the modify
	 * API call just changes the deleted flag for that file to "yes".
	 * 
	 * Any execptions are printed out and ignored.
	 * 
	 * @param userName
	 * @param deviceName
	 * @param path
	 * @return true if there was an update, else false if there was not update.
	 */
	public boolean update(String userName, String deviceName, String path) {
		
		boolean updated = false;
		
		NodeList oldNodes;
		String rootdir;
		try {
			oldNodes = db.getFiles(userName, deviceName);
			rootdir = db.getUserLocalDir(userName);
		} catch (XPathExpressionException e) {		
			e.printStackTrace();
			return false;
		} catch (InterruptedException e) {			
			e.printStackTrace();
			return false;
		}
		
		List<FileItem> oldFiles = new LinkedList<FileItem>();
		for (int i=0; i<oldNodes.getLength(); ++i) {
			try {
				oldFiles.add(new FileItem(oldNodes.item(i)));
			} catch (NamingException e) {
				e.printStackTrace();
			}
		}
		
		List<File> newNodes = listFiles(new File(path));
		List<FileItem> newFiles = new LinkedList<FileItem>();
		for (Iterator<File> it=newNodes.iterator(); it.hasNext(); ) {
			File newFile = it.next();
			FileItem fileItem = new FileItem();
			fileItem.name = newFile.getName();
			fileItem.path = FileUtil.getRelativePath(
				newFile.getParentFile().getPath().replace('\\', '/'), rootdir);
			fileItem.lastModified = newFile.lastModified();
			fileItem.size = newFile.length();
			fileItem.deleted = false;
			newFiles.add(fileItem);
		}
		
		// modify existing nodes
		for (Iterator<FileItem> oldIt = oldFiles.iterator(); oldIt.hasNext(); ) {
			FileItem oldItem = oldIt.next();
			FileItem foundItem = null;
			for (Iterator<FileItem> newIt = newFiles.iterator(); newIt.hasNext(); ) {
				FileItem newItem = newIt.next();
				if (oldItem.equalsName(newItem)) {
					// found. Modify it.
					foundItem = newItem;
					break;
				}
			}
			
			if (foundItem == null) {
				// make it same as oldItem, but deleted = true
				foundItem = new FileItem(oldItem.name, oldItem.path, oldItem.lastModified, oldItem.size, true);
			}
			
			// backup information is not updated on file update
			foundItem.backup = oldItem.backup;

			// if either old or new is not deleted, modify old to new.
			if (!oldItem.deleted || !foundItem.deleted) {
				if (oldItem.equalsContent(foundItem)) {
					continue; // no modification found.
				}
				
				try {
					System.out.println("\n--- updating-modify: "  + userName + ", " + deviceName + ", " + path);
					System.out.println("  old file");
					Database.printNode(oldItem.toNode(db.getDoc()), System.out);
					System.out.println("  new file");
					Database.printNode(foundItem.toNode(db.getDoc()), System.out);
					
					db.modify(userName, deviceName, oldItem.toNode(db.getDoc()), foundItem.toNode(db.getDoc()));
					updated = true;
				} catch (XPathExpressionException e) {					
					e.printStackTrace();
				} catch (NameNotFoundException e) {					
					e.printStackTrace();
				} catch (IOException e) {					
					e.printStackTrace();
				} catch (InterruptedException e) {					
					e.printStackTrace();
				}
			}
		}
		
		// add new nodes
		for (Iterator<FileItem> newIt = newFiles.iterator(); newIt.hasNext(); ) {
			boolean found = false;
			FileItem newItem = newIt.next();
			for (Iterator<FileItem> oldIt = oldFiles.iterator(); oldIt.hasNext(); ) {
				FileItem oldItem = oldIt.next();
				if (oldItem.equalsName(newItem)) {
					found = true;
					break;
				}
			}
			if (!found) { // need to add a new item
				try {
					System.out.println("\n--- updating-add: " + userName + ", " + deviceName + ", " + path);
					Database.printNode(newItem.toNode(db.getDoc()), System.out);
					
					db.add(userName, deviceName, newItem.toNode(db.getDoc()));
					updated = true;
				} catch (XPathExpressionException e) {				
					e.printStackTrace();
				} catch (NamingException e) {					
					e.printStackTrace();
				} catch (IOException e) {					
					e.printStackTrace();
				} catch (InterruptedException e) {				
					e.printStackTrace();
				}
			}
		}
		
		return updated;
	}
}
