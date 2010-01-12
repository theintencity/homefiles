package db;

import java.util.Date;

import javax.naming.NamingException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Class to represent a file element and allows converting between
 * this and XML objects so that XML Node can be used in Database API.
 * The negative size is stored as "unknown" in XML because Google Docs
 * sets the size to -1. The Backup element is stored as a Node.
 * 
 * @author Mamta
 */
public class FileItem {

	public String path;
	public String name;
	public boolean deleted;
	public long size;
	public Node backup;
	public long lastModified;
	
	public FileItem() {
		path = "";
		name = "";
		deleted = false;
		size = 0;
		backup = null;
		lastModified = (new Date()).getTime();
	}
	
	public FileItem(String name, String path, long lastModified, long size, boolean deleted) {
		this.name = name;
		this.path = path;
		this.lastModified = lastModified;
		this.size = size;
		this.backup = null;
		this.deleted = deleted;
	}
	
	public FileItem(Node node) throws NamingException {
		path = "";
		name = "";
		deleted = false;
		size = 0;
		backup = null;
		lastModified = (new Date()).getTime();
		
		if (!"File".equals(node.getNodeName())) {
			throw new NamingException("Node is not <File/>");
		}
		
		NodeList children = node.getChildNodes();
		for (int i=0; i<children.getLength(); ++i) {
			Node child = children.item(i);
			if ("Path".equals(child.getNodeName())) {
				path = child.getTextContent();
			}
			else if ("Name".equals(child.getNodeName())) {
				name = child.getTextContent();
			}
			else if ("Deleted".equals(child.getNodeName())) {
				deleted = "yes".equalsIgnoreCase(child.getTextContent());
			}
			else if ("Size".equals(child.getNodeName())) {
				try {
					size = Long.parseLong(child.getTextContent());
				} catch (NumberFormatException e) {
					size = -1;
				}
			}
			else if ("Backup".equals(child.getNodeName())) {
				backup = child;
			}
			else if ("LastModified".equals(child.getNodeName())) {
				lastModified = Long.valueOf(child.getTextContent()).longValue();
			}
		}
	}
	
	/**
	 * Convert this object to an XML <File/> node using the supplied document.
	 * @param doc
	 * @return
	 */
	public Node toNode(Document doc) {
		Element node = doc.createElement("File");
		Element path = doc.createElement("Path");
		path.setTextContent(this.path);
		Element name = doc.createElement("Name");
		name.setTextContent(this.name);
		Element deleted = doc.createElement("Deleted");
		deleted.setTextContent(this.deleted ? "yes" : "no");
		Element size = doc.createElement("Size");
		size.setTextContent(this.size >= 0 ? String.valueOf(this.size) : "unknown");
		Element backup = null;
		if (this.backup == null) {
			backup = doc.createElement("Backup");
		}
		else {
			backup = (Element) doc.adoptNode(this.backup.cloneNode(true));
		}
		Element modified = doc.createElement("LastModified");
		
		modified.setTextContent(String.valueOf(this.lastModified));
		node.appendChild(path);
		node.appendChild(name);
		node.appendChild(deleted);
		node.appendChild(size);
		node.appendChild(backup);
		node.appendChild(modified);
		return node;
	}
	
	/**
	 * Used for debug trace.
	 */
	public String toString() {
		return "[File path=" + path + " name=" + name + " deleted=" + deleted + " size=" + size + " lastModified=" + lastModified +  " (" + lastModified + ")" + "]";
	}
	
	/**
	 * Compare whether the file name (and path) matches the other file.
	 * The file is same if both name and path elements match.
	 * @param other
	 * @return
	 */
	public boolean equalsName(FileItem other) {
		return name.equals(other.name) && path.replace('\\', '/').equals(other.path.replace('\\', '/'));
	}
	
	/**
	 * Compare whether the content (deleted, lastModified, size) are same as that
	 * of the other file.
	 * @param other
	 * @return
	 */
	public boolean equalsContent(FileItem other) {
		return deleted == other.deleted 
		&& lastModified == other.lastModified
		&& size == other.size;
	}
	
}
