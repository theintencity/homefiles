package db;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
//import java.util.concurrent.locks.ReadWriteLock;
//import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.sun.org.apache.xml.internal.serialize.OutputFormat;
import com.sun.org.apache.xml.internal.serialize.XMLSerializer;

/**
 * The Database class implements a database and provides API methods to add, modify,
 * search, export and import.
 * 
 * All the methods are thread safe. The add, modify and importFrom use a write lock.
 * The search and export use a read-lock.
 * 
 * The actual API method names are as follows:
 *  add: 
 *  	add(user-name, device-name, <File>...</File>)
 *  modify: 
 *  	modify(user-name, device-name, old <File/>, new <File/>)
 *  search: 
 *  	getFiles(user-name, device-name)
 *  	findFiles(user-name, device-name, file-name) 
 *  	matchFiles(user-name, device-name, file-name-substring)
 *  	modifiedFiles(user-name, device-name, last-modified-after)
 *  	search(XPath expression)
 *  export: 
 *  	exportTo(file-name)
 *  import: 
 *  	importFrom(file-name)
 *  
 * The getFiles, findFiles, matchFiles and modifiedFiles functions handle the
 * case when deviceName is "all".
 * 
 * A new set of API methods getFilesAlt, findFilesAlt, matchFilesAlt and modifiedFilesAlt
 * are also defined which return the filelist inside the device element and the top
 * level element is Devices. This gives the device information to the client. Note
 * that these API return clone of the nodes hence changes to return value is not
 * updated on database, unlike the previous APIs.
 * @author Mamta
 */
public class Database {
	
	private DocumentBuilderFactory docFactory;
	private DocumentBuilder docBuilder;
	private Document doc;
	private String localdevice; // the local devicename
	private boolean dirty = false; // whether something is modified, which needs to be sent
									// to remote in next update intervale
	
	private XPathFactory xpathFactory;
	
	// internal lock for read-write
	//private ReadWriteLock lock;
	private MyReadWriteLock lock;
	
	private static Database singleton;
	
	/**
	 * The singleton instance of the database.
	 */
	public static Database getInstance() {
		if (singleton == null) {
			try {
				singleton = new Database();
			} catch (ParserConfigurationException e) {
				e.printStackTrace();
			}
		}
		return singleton;
	}
	
	/**
	 * Construct a new Database object, and create various XML and XPath related
	 * factory objects.
	 * 
	 * @throws ParserConfigurationException
	 */
	private Database() throws ParserConfigurationException{
		docFactory = DocumentBuilderFactory.newInstance();
		docFactory.setNamespaceAware(true);
		docBuilder = docFactory.newDocumentBuilder();
		doc = docBuilder.newDocument();
		
		// create an empty database initially.
		doc.appendChild(doc.createElement("Database"));
		dirty = true;
		
		xpathFactory = XPathFactory.newInstance();
	
		//lock = new ReentrantReadWriteLock();
		lock = new MyReadWriteLock();
	}
	
	/**
	 * The setter for localdevice name property.
	 * @param name
	 */
	public void setLocalDevice(String name) {
		localdevice = name;
	}
	
	/**
	 * The getter for localdevice name property.
	 * @return
	 */
	public String getLocalDevice() {
		return localdevice;
	}
	
	/**
	 * Whether the database is dirty (modified?) since last update interval.
	 */
	public boolean isDirty() {
		return dirty;
	}
	
	/**
	 * Reset the dirty flag for the database.
	 */
	public void resetDirty() {
		dirty = false;
	}
	
	/**
	 * Set the dirty flag for the database so that metadata is refreshed.
	 */
	public void setDirty() {
		dirty = true;
	}
	
	/**
	 * Import the data from the supplied XML file, and replace the local database
	 * to reflect the database imported from that file.
	 * 
	 * @param fileName
	 * @throws SAXException
	 * @throws IOException
	 * @throws InterruptedException  
	 */
	public void importFrom(String fileName) throws SAXException, IOException, InterruptedException {
		lock.getWriteLock();
		try {
			dirty = true;
			doc = docBuilder.parse(new File(fileName));
		}
		finally {
			lock.releaseWriteLock();
		}
	}
	
	/**
	 * Export the current XML database to a file using formatted XML.
	 * 
	 * @param fileName
	 * @throws IOException
	 * @throws InterruptedException 
	 */
	public void exportTo(String fileName) throws IOException, InterruptedException{
		lock.getReadLock();
		try {
			FileOutputStream stream = new FileOutputStream(new File(fileName));
			OutputFormat of = new OutputFormat(doc);
			of.setIndenting(true);
			XMLSerializer serializer = new XMLSerializer(stream, of);
			serializer.serialize(doc);
			stream.close();
		}
		finally {
			lock.releaseReadLock();
		}
	}
	
	/**
	 * Search the document using given XPath query string and returns a NodeList
	 * containing matching nodes. This is used only for searching nodes. This is
	 * used by other high-level API methods such as getFiles, matchFiles, etc.
	 * 
	 * @param query
	 * @return NodeList
	 * @throws XPathExpressionException
	 * @throws InterruptedException 
	 */
	public NodeList search(String query) throws XPathExpressionException, InterruptedException{
		lock.getReadLock();
		try {
			XPath xpath = xpathFactory.newXPath();
			XPathExpression expr = xpath.compile(query);
			NodeList result = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
			return result;
		}
		finally {
			lock.releaseReadLock();
		}
	}
	
	/**
	 * Search all the files for the given user and device, for which the file name
	 * is same as the supplied fileName.
	 * 
	 * @param userName
	 * @param deviceName
	 * @param fileName
	 * @return
	 * @throws XPathExpressionException
	 * @throws InterruptedException 
	 */
	public NodeList matchFiles(String userName, String deviceName, String fileName) throws XPathExpressionException, InterruptedException {
		return search("/Database/User[@name='" + userName + "']/Devices/Device" + (deviceName.equals("all") ? "" : "[Name='" + deviceName + "']") + "/FileList/File[Name='" + fileName + "']");
	}
	
	/**
	 * Search all the files for the given user and device, for which the file name
	 * contains the given matching sub-string.
	 * 
	 * @param userName
	 * @param deviceName
	 * @param match
	 * @return
	 * @throws XPathExpressionException
	 * @throws InterruptedException 
	 */
	public NodeList containFiles(String userName, String deviceName, String match) throws XPathExpressionException, InterruptedException {
		return search("/Database/User[@name='" + userName + "']/Devices/Device" + (deviceName.equals("all") ? "" : "[Name='" + deviceName + "']") + "/FileList/File[contains(string(child::Name), '" + match + "')]");
	}
	
	/**
	 * Search all the files that are modified after the given lastModified date,
	 * for the given user and device.
	 * 
	 * @param userName
	 * @param deviceName
	 * @param lastModified
	 * @return
	 * @throws XPathExpressionException
	 * @throws InterruptedException 
	 */
	public NodeList modifiedFiles(String userName, String deviceName, Date lastModified) throws XPathExpressionException, InterruptedException {
		return search("/Database/User[@name='" + userName + "']/Devices/Device" + (deviceName.equals("all") ? "" : "[Name='" + deviceName + "']") + "/FileList/File[LastModified>" + String.valueOf(lastModified.getTime()) + "]");
	}
	
	/**
	 * Search all the files for a given user and device. 
	 * 
	 * @param userName
	 * @param deviceName
	 * @return
	 * @throws XPathExpressionException
	 * @throws InterruptedException 
	 */
	public NodeList getFiles(String userName, String deviceName) throws XPathExpressionException, InterruptedException {
		return search("/Database/User[@name='" + userName + "']/Devices/Device" + (deviceName.equals("all") ? "" : "[Name='" + deviceName + "']") + "/FileList/File");
	}
	
	/**
	 * Search the document using given XPath query string and returns a NodeList
	 * containing matching nodes. The difference between this and previous search
	 * function is that, this returns XML with device hierarchy so that top level
	 * element is Devices, with list of Device, and each Device has a FileList.
	 * The previous search function returns FileList hence the device information is
	 * lost in the result.
	 * 
	 * @param deviceQuery to select the devices
	 * @param fileQuery to select the files within selected devices
	 * @return NodeList
	 * @throws XPathExpressionException
	 * @throws InterruptedException 
	 */
	public Node searchAlt(String deviceQuery, String fileQuery) throws XPathExpressionException, InterruptedException{
		lock.getReadLock();
		try {
			System.out.println("  searchAlt deviceQuery=" + deviceQuery + ", fileQuery=" + fileQuery);
			XPath xpath = xpathFactory.newXPath();
			NodeList devices = (NodeList) xpath.evaluate(deviceQuery, doc, XPathConstants.NODESET);
			Element devicesNode = doc.createElement("Devices");
			for (int i=0; i<devices.getLength(); ++i) {
				Element deviceNode = (Element) doc.adoptNode(devices.item(i).cloneNode(true));
				if (!fileQuery.equals("")) {
					// first find the selected children
					NodeList children = (NodeList) xpath.evaluate(fileQuery, deviceNode, XPathConstants.NODESET);

					Element filelistNode = (Element) deviceNode.getElementsByTagName("FileList").item(0);
					deviceNode.removeChild(filelistNode);
					// then remove all children
					while (filelistNode.getChildNodes().getLength() > 0) {
						filelistNode.removeChild(filelistNode.getFirstChild());
					}
					
					// then add found children
					for (int j=0; j<children.getLength(); ++j) {
						filelistNode.appendChild(children.item(j));
					}
					deviceNode.appendChild(filelistNode);
				}
				devicesNode.appendChild(deviceNode);
			}
			return devicesNode;
		}
		finally {
			lock.releaseReadLock();
		}
	}
	
	public Node matchFilesAlt(String userName, String deviceName, String fileName) throws XPathExpressionException, InterruptedException {
		return searchAlt("/Database/User[@name='" + userName + "']/Devices/Device" + (deviceName.equals("all") ? "" : "[Name='" + deviceName + "']"), "FileList/File[Name='" + fileName + "']");
	}
	
	public Node containFilesAlt(String userName, String deviceName, String match) throws XPathExpressionException, InterruptedException {
		return searchAlt("/Database/User[@name='" + userName + "']/Devices/Device" + (deviceName.equals("all") ? "" : "[Name='" + deviceName + "']"), "FileList/File[contains(string(child::Name), '" + match + "')]");
	}
	
	public Node modifiedFilesAlt(String userName, String deviceName, Date lastModified) throws XPathExpressionException, InterruptedException {
		return searchAlt("/Database/User[@name='" + userName + "']/Devices/Device" + (deviceName.equals("all") ? "" : "[Name='" + deviceName + "']"), "FileList/File[LastModified>" + String.valueOf(lastModified.getTime()) + "]");
	}
	
	public Node getFilesAlt(String userName, String deviceName) throws XPathExpressionException, InterruptedException {
		return searchAlt("/Database/User[@name='" + userName + "']/Devices/Device" + (deviceName.equals("all") ? "" : "[Name='" + deviceName + "']"), "FileList/File");
	}
	
	/**
	 * Add a new fileNode for the given userName and deviceName. If the file already
	 * exists (with same name and path), then an exception is thrown. Otherwise a
	 * new <File/> element is added in the database.
	 * 
	 * @param userName
	 * @param deviceName
	 * @param fileNode
	 * @throws XPathExpressionException
	 * @throws NamingException
	 * @throws InterruptedException 
	 */
	public void add(String userName, String deviceName, Node fileNode) throws XPathExpressionException, NamingException, InterruptedException {
		lock.getWriteLock();
		try {
			dirty = true;
			XPath xpath = xpathFactory.newXPath();
			Element userNode = (Element) xpath.evaluate("/Database/User[@name='" + userName + "']", doc, XPathConstants.NODE);
			if (userNode == null) {
				userNode = doc.createElement("User");
				userNode.setAttribute("name", userName);
				Element databaseNode = (Element) doc.getFirstChild();
				databaseNode.appendChild(userNode);
				Element devicesNode = doc.createElement("Devices");
				userNode.appendChild(devicesNode);
			}
			
			Element deviceNode = (Element) xpath.evaluate("Devices/Device[string(Name)='" + deviceName + "']", userNode, XPathConstants.NODE);
			if (deviceNode == null) {
				deviceNode = doc.createElement("Device");
				Element nameNode = doc.createElement("Name");
				nameNode.setTextContent(deviceName);
				Element onlineNode = doc.createElement("OnlineStatus");
				onlineNode.setTextContent("online");
				Element filelistNode = doc.createElement("FileList");
				deviceNode.appendChild(nameNode);
				deviceNode.appendChild(onlineNode);
				deviceNode.appendChild(filelistNode);
				Element devicesNode = (Element) xpath.evaluate("Devices", userNode, XPathConstants.NODE);
				if (devicesNode == null) {
					devicesNode = doc.createElement("Devices");
					userNode.appendChild(devicesNode);
				}
				
				devicesNode.appendChild(deviceNode);
			}
			
			if (fileNode != null) {
				String path = xpath.evaluate("Path", fileNode);
				String name = xpath.evaluate("Name", fileNode);
				
				Element existing = (Element) xpath.evaluate("FileList/File[string(Name)='" + name + "'][string(Path)='" + path + "']", deviceNode, XPathConstants.NODE);
				if (existing != null) {
					throw new NamingException("File path/name already exists. Use modify");
				}
				else {
					Element filelistNode = (Element) xpath.evaluate("FileList", deviceNode, XPathConstants.NODE);
					filelistNode.appendChild(fileNode);
				}
			}
		}
		finally {
			lock.releaseWriteLock();
		}
	}
	
	/**
	 * Modify the oldFileNode with the newFileNode for the given userName and deviceName.
	 * If a matching entry is not found, then an exception is thrown indicating what
	 * was not found (user doesn't exist, or device doesn't exist, or old file
	 * doesn' exists.) The inside elements (Path, Name, Size, etc.) of File element
	 * are modified in the database.
	 * 
	 * @param userName
	 * @param deviceName
	 * @param oldFileNode
	 * @param newFileNode
	 * @throws XPathExpressionException
	 * @throws NameNotFoundException
	 * @throws InterruptedException 
	 */
	public void modify(String userName, String deviceName, Node oldFileNode, Node newFileNode) 
				throws XPathExpressionException, NameNotFoundException, InterruptedException {
		lock.getWriteLock();
		try {
			dirty = true;
			XPath xpath = xpathFactory.newXPath();
			Element userNode = (Element) xpath.evaluate("/Database/User[@name='" + userName + "']", doc, XPathConstants.NODE);
			if (userNode == null) {
				throw new NameNotFoundException("User not found for name=" + userName);
			}
			
			Element deviceNode = (Element) xpath.evaluate("Devices/Device[string(Name)='" + deviceName + "']", userNode, XPathConstants.NODE);
			if (deviceNode == null) {
				throw new NameNotFoundException("Device not found for name=" + deviceName);
			}
			
			String path = xpath.evaluate("Path", oldFileNode);
			String name = xpath.evaluate("Name", oldFileNode);
			
			Element existing = (Element) xpath.evaluate("FileList/File[string(Name)='" + name + "' and string(Path)='" + path + "']", deviceNode, XPathConstants.NODE);
			if (existing == null) {
				throw new NameNotFoundException("File not found for name=" + name + " path=" + path);
			}
			else {
				NodeList newChildren = newFileNode.getChildNodes(); 
				System.out.println("newChildren.getLength()=" + newChildren.getLength());
				for (int i=0; i<newChildren.getLength(); ++i) {
					Node newNode =  newChildren.item(i);
					System.out.println("checking node " + newNode.getNodeName());
					if (newNode.getNodeType() == Node.ELEMENT_NODE) {
						String nodeName = newNode.getNodeName();
						Element oldNode = (Element) xpath.evaluate(nodeName, existing, XPathConstants.NODE);
						if (oldNode == null) {
							existing.appendChild(newNode.cloneNode(true));
						}
						else {
							if (newNode != oldNode)
								existing.replaceChild(newNode.cloneNode(true), oldNode);
						}
					}
				}
			}
		}
		finally {
			lock.releaseWriteLock();
		}
	}
	
	/**
	 * Print a NodeList to OutputStream using XML OutputFormat in UTF-8 encoding and
	 * using indentation. The Driver calls this to print the result of search queries
	 * to System.out OutputStream.
	 * 
	 * @param nodes
	 * @param stream
	 * @throws IOException
	 */
	public static void printNodes(NodeList nodes, OutputStream stream) throws IOException {
		OutputFormat of = new OutputFormat("xml", "UTF-8", true);
		of.setOmitXMLDeclaration(true);
		for (int i=0; i<nodes.getLength(); ++i) {
			Node node = nodes.item(i);
			XMLSerializer serializer = new XMLSerializer(stream, of);
			serializer.serialize((Element)node);
		}
	}
	
	/**
	 * Print the XML node using formatted XML.
	 * @param node
	 * @param stream
	 * @throws IOException
	 */
	public static void printNode(Node node, OutputStream stream) throws IOException {
		OutputFormat of = new OutputFormat("xml", "UTF-8", true);
		of.setOmitXMLDeclaration(true);
		XMLSerializer serializer = new XMLSerializer(stream, of);
		serializer.serialize((Element)node);
	}
	
	/**
	 * Add the given username in the database if it is not already there.
	 * 
	 * @throws InterruptedException 
	 * @throws XPathExpressionException 
	 * @throws NamingException 
	 */
	public void addUsername(String username) throws InterruptedException, XPathExpressionException, NamingException {
		lock.getReadLock();
		try {
			XPath xpath = xpathFactory.newXPath();
			NodeList userNodes = (NodeList) xpath.evaluate("/Database/User[@name='" + username + "']", doc, XPathConstants.NODESET);
			if (userNodes.getLength() == 0) {
				lock.releaseReadLock();
				add(username, localdevice, null);
				lock.getReadLock();
			}
		}
		finally {
			lock.releaseReadLock();
		}
	}
	
	/**
	 * Set the device status.
	 * @throws InterruptedException 
	 * @throws XPathExpressionException 
	 */
	public void setDeviceStatus(String devicename, String status) throws InterruptedException, XPathExpressionException {
		lock.getWriteLock();
		try {
			XPath xpath = xpathFactory.newXPath();
			NodeList statusNodes = (NodeList) xpath.evaluate("/Database/User/Devices/Device" + (devicename != null ? "[Name='" + devicename + "']" : "") + "/OnlineStatus", doc, XPathConstants.NODESET);
			for (int i=0; i<statusNodes.getLength(); ++i) {
				Element statusNode = (Element) statusNodes.item(i);
				statusNode.setTextContent(status);
			}
		}
		finally {
			lock.releaseWriteLock();
		}
	}
	
	/**
	 * Get a list of all the top-level LocalDir, one per user.
	 * @return
	 * @throws XPathExpressionException
	 * @throws InterruptedException 
	 */
	public List<LocalDir> getLocalDir() throws XPathExpressionException, InterruptedException {
		lock.getReadLock();
		try {
			List<LocalDir> result = new LinkedList<LocalDir>();
			XPath xpath = xpathFactory.newXPath();
			NodeList userNodes = (NodeList) xpath.evaluate("/Database/User", doc, XPathConstants.NODESET);
			for (int i=0; i<userNodes.getLength(); ++i) {
				LocalDir local = new LocalDir();
				Element userNode = (Element) userNodes.item(i);
				local.userName = (String) xpath.evaluate("@name", userNode, XPathConstants.STRING);
				local.local_dir = (String) xpath.evaluate("@local_rootdir", userNode, XPathConstants.STRING);
				local.deviceName = localdevice;
				result.add(local);
			}
			return result;
		}
		finally {
			lock.releaseReadLock();
		}
	}
	
	/**
	 * Get a top-level LocalDir, for the given user.
	 * @return
	 * @throws XPathExpressionException
	 * @throws InterruptedException 
	 */
	public String getUserLocalDir(String username) throws XPathExpressionException, InterruptedException {
		lock.getReadLock();
		try {
			XPath xpath = xpathFactory.newXPath();
			Node userNode = (Node) xpath.evaluate("/Database/User[@name='" + username + "']", doc, XPathConstants.NODE);
			if (userNode != null) {
				String local_dir = (String) xpath.evaluate("@local_rootdir", userNode, XPathConstants.STRING);
				return local_dir;
			}
		}
		finally {
			lock.releaseReadLock();
		}
		return null;
	}
	
	/**
	 * Set the localDir for the given user. It also clears the database so that
	 * next update cycle can re-create the database with the new directory.
	 * 
	 * @return
	 * @throws XPathExpressionException
	 * @throws InterruptedException 
	 */
	public void setUserLocalDir(String userName, String localDir) throws XPathExpressionException, InterruptedException {
		lock.getWriteLock();
		try {
			dirty = true;
			XPath xpath = xpathFactory.newXPath();
			// find all the users
			NodeList userNodes = (NodeList) xpath.evaluate("/Database/User", doc, XPathConstants.NODESET);
			for (int i=0; i<userNodes.getLength(); ++i) {
				// find user with matching name.
				Element userNode = (Element) userNodes.item(i);
				String user = (String) xpath.evaluate("@name", userNode, XPathConstants.STRING);
				String old_dir = (String) xpath.evaluate("@local_rootdir", userNode, XPathConstants.STRING);
				if (userName == null || user.equals(userName)) { // default for all user
					if (!old_dir.equals(localDir)) {
						// change the local_rootdir attribute
						System.out.println("  setting localdir to " + localDir);
						userNode.setAttribute("local_rootdir", localDir);
						
						//remove the filelist for the user
						NodeList filelist = (NodeList) xpath.evaluate("Devices/Device/FileList", userNode, XPathConstants.NODESET);
						for (int j=0; j<filelist.getLength(); ++j) {
							NodeList children = filelist.item(j).getChildNodes();
							for (int k=children.getLength()-1; k>=0; --k) {
								if (children.item(k).getNodeType() == Node.ELEMENT_NODE) {
									filelist.item(j).removeChild(children.item(k));
								}
							}
						}
					}
				}
			}
		}
		finally {
			lock.releaseWriteLock();
		}
	}
	
	/**
	 * Increment the version for that device by 1. If the previous value is
	 * missing, assume it 0, and then increment.
	 * 
	 * @param deviceName
	 * @throws XPathExpressionException
	 * @throws InterruptedException
	 */
	public void incrVersion(String deviceName) throws XPathExpressionException, InterruptedException {
		lock.getWriteLock();
		try {
			dirty = true;
			XPath xpath = xpathFactory.newXPath();
			Node node = (Node) xpath.evaluate("/Database/User/Devices/Device[Name='" + deviceName + "']", doc, XPathConstants.NODE);
			if (node != null) {
				Element device = (Element) node;
				long clock = device.hasAttribute("version") ? Long.valueOf(device.getAttribute("version")).longValue() : 0;
				clock += 1;
				device.setAttribute("version", String.valueOf(clock));
			}
		}
		finally {
			lock.releaseWriteLock();
		}
	}
	
	/**
	 * Get the version of that device. In case of error, it returns 0.
	 * 
	 * @param deviceName
	 * @return
	 * @throws XPathExpressionException
	 * @throws InterruptedException
	 */
	public long getVersion(String deviceName) throws XPathExpressionException, InterruptedException {
		lock.getReadLock();
		try {
			XPath xpath = xpathFactory.newXPath();
			Node node = (Node) xpath.evaluate("/Database/User/Devices/Device[Name='" + deviceName + "']", doc, XPathConstants.NODE);
			if (node != null) {
				Element device = (Element) node;
				long clock = device.hasAttribute("version") ? Long.valueOf(device.getAttribute("version")).longValue() : 0;
				return clock;
			}
		}
		finally {
			lock.releaseReadLock();
		}
		return 0;
	}
	
	/**
	 * This is needed for creating a new Node from this document.
	 * @return
	 */
	public Document getDoc() {
		return doc;
	}
	
	/**
	 * Update the database using a metadata update from another device.
	 * The update is done by using the version information. The metadata
	 * is the Devices element with a localdevice attribute identifying the
	 * sender.
	 * @throws InterruptedException 
	 */
	public void update(Document other) throws InterruptedException {
		lock.getWriteLock();
		try {
			if (! "Devices".equals(other.getFirstChild().getNodeName()) ||
				! ((Element) other.getFirstChild()).hasAttribute("localdevice")) {
				System.out.println("  metadata update must have Devices tag");
				return;
			}

			NodeList newDevices = other.getFirstChild().getChildNodes();
			String remotedevice = ((Element) other.getFirstChild()).getAttribute("localdevice");
			String username = ((Element) other.getFirstChild()).getAttribute("username");
			if (localdevice.equals(remotedevice)) {
				System.out.println("  ignoring metadata update from this device");
				return;
			}
			
			// create the User element if missing
			XPath xpath = xpathFactory.newXPath();
			if (username != null && username.length() > 0) {
				NodeList userNodes = (NodeList) xpath.evaluate("/Database/User[@name='" + username + "']", doc, XPathConstants.NODESET);
				if (userNodes.getLength() == 0) {
					System.out.println("  creating User " + username);
					Element userNode = doc.createElement("User");
					userNode.setAttribute("name", username);
					Element devicesNode = doc.createElement("Devices");
					userNode.appendChild(devicesNode);
					doc.getFirstChild().appendChild(userNode);
				}
			}
			
			// update local clock to be max(remote, local)
			Element remoteDevice = (Element) xpath.evaluate("/Devices/Device[Name='" + remotedevice + "']", other, XPathConstants.NODE);
			long remoteVersion = (remoteDevice != null ? Long.valueOf(remoteDevice.getAttribute("version")).longValue() : 0);
			
			Element localDevice = (Element) xpath.evaluate("/Database/User/Devices/Device[Name='" + localdevice + "']", doc, XPathConstants.NODE);
			long localVersion = (localDevice != null && localDevice.hasAttribute("version") ?
					Long.valueOf(localDevice.getAttribute("version")).longValue() : 0);
			if (localVersion < remoteVersion) {
				localVersion = remoteVersion;
			}
			
			// then increment the local clock before processing the message.
			localVersion += 1;
			if (localDevice != null) {
				localDevice.setAttribute("version", String.valueOf(localVersion));
			}
			
			
			// if remoteDevice is present then make it online
			if (remoteDevice != null) {
				Element statusNode = (Element) remoteDevice.getElementsByTagName("OnlineStatus").item(0);
				statusNode.setTextContent("online");
			}
			
			// now update local database with this metadata.
			
			for (int i=0; i<newDevices.getLength(); ++i) {
				Element newDevice = (Element) newDevices.item(i);
				long newVersion = Long.valueOf(newDevice.getAttribute("version")).longValue();
				String newName = newDevice.getElementsByTagName("Name").item(0).getTextContent();
				NodeList oldDevices = (NodeList) xpath.evaluate("/Database/User/Devices/Device[Name='" + newName + "']", doc, XPathConstants.NODESET);
				if (oldDevices != null && oldDevices.getLength() > 0) {
					// found hence update in the database
					for (int j=0; j<oldDevices.getLength(); ++j) {
						Element oldDevice = (Element) oldDevices.item(j);
						if (!oldDevice.hasAttribute("version") ||
								Long.valueOf(oldDevice.getAttribute("version")).longValue() < newVersion) {
							// either old device does not exist or has lower version.
							String oldName = oldDevice.getElementsByTagName("Name").item(0).getTextContent();
							if (!oldName.equals(remotedevice)) {
								// don't set ours as dirty if we received first hand data
								dirty = true;
							}
							
							System.out.println("  updating with new version for device=" + newName);
							Node parent = oldDevice.getParentNode();
							parent.removeChild(oldDevice);
							parent.appendChild(doc.adoptNode(newDevice.cloneNode(true)));
						}
					}
				}
				else {
					// not found, so add to the default user.
					oldDevices = (NodeList) xpath.evaluate("/Database/User/Devices", doc, XPathConstants.NODESET);
					if (oldDevices != null && oldDevices.getLength() > 0) {
						dirty = true;
						System.out.println("  adding to devices");
						Element devicesNode = (Element) oldDevices.item(0); // use only first one.
						devicesNode.appendChild(doc.adoptNode(newDevice.cloneNode(true)));
					}
					else {
						System.out.println("  no devices found");
					}
				}
			}
		} catch (XPathExpressionException e) {
			e.printStackTrace();
		}
		finally {
			lock.releaseWriteLock();
		}
	}

}
