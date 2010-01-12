package client;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.restlet.data.MediaType;
import org.restlet.data.Response;
import org.restlet.resource.DomRepresentation;
import org.restlet.resource.FileRepresentation;
import org.restlet.resource.Representation;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import api.GoogleDocs;

import com.google.gdata.client.docs.DocsService;
import com.google.gdata.data.MediaContent;
import com.google.gdata.data.PlainTextConstruct;
import com.google.gdata.data.docs.DocumentEntry;
import com.google.gdata.data.docs.DocumentListEntry;
import com.google.gdata.data.docs.DocumentListFeed;
import com.google.gdata.data.media.MediaSource;
import com.google.gdata.util.ServiceException;

import db.FileItem;
import db.FileUtil;
import dev.Device;

/**
 * Class to handle the cp or copy command. Please see the usage below on how it can
 * be invoked.
 * 
 * @author Mamta
 */
public class CopyCommand implements IClientCommand {
	private String src;
	private String dest;
	private String srcDev;
	private String destDev;

	public static final String usage = "<src> <dest>\n" +
		" Copy from source to destination file resource\n" +
		" Either src or dest, but not both, must be a qualified with device name.\n" +
		" The src may be wild-card files, e.g., \n" +
		"   '*' means from all local files in current directory\n" +
		"   'gdocs:*' means from all files in gdocs\n" +
		"   'all:*file*' means any file name containing file in any device\n" +
		" The dest may be specific file, directory or device name only, e.g.,\n" +
		"   'gdocs:' means to google documents\n" +
		"   'dest/dir/' means to the local directory dest/dir\n" +
		"   'Home-PC:' means to that Home-PC device\n" +
		" When copying multiple files, failure of one does not affect others.";

	
	public void exec(String args) {
		try {
			parseArgs(args);
		} catch (Exception e) {
			System.out.println("invalid arguments: " + e.getMessage());
			return;
		}
		
		System.out.println("copy [" + (srcDev != null ? srcDev + ":"  : "") + src 
				+ "] [" + (destDev != null ? destDev + ":" : "") + dest + "]");

		try {
			if (src.length() == 0) {
				throw new Exception("missing src file name or pattern");
			}
			
			if (srcDev != null && destDev == null) {
				// download from device to local files
				downloadFiles();
			}
			else if (srcDev == null && destDev != null) {
				// upload from load files to device
				uploadFiles();
			}
			else {
				throw new Exception("either src or dest, but not both, must have device name");
			}
		} catch (NullPointerException e) {
			throw e;
		} catch (Exception e) {
			System.out.println("cannot copy: " + e.getMessage());
		}
	}
	
	/**
	 * Copy from device or gdocs to local.
	 * 
	 * @throws Exception
	 */
	private void downloadFiles() throws Exception {
		if ("gdocs".equals(srcDev))
			downloadFromGoogle();
		else
			downloadFromDevice();
	}
		
	/**
	 * Copy from local to device or gdocs.
	 * @throws Exception
	 */
	private void uploadFiles() throws Exception {
		List<File> files = FileUtil.getFiles(src);
		if (files.size() == 0) {
			throw new Exception("no src file to copy");
		}
		
		if ("gdocs".equals(destDev))
			uploadToGoogle(files);
		else
			uploadToDevice(files);
	}
	
	/**
	 * Copy from a device to local. The file name of "*" matches any file.
	 * There is another function to handle download from gdocs.
	 * This uses the REST URL /device/xml/file/path/to/file.ext
	 * @throws Exception
	 */
	private void downloadFromDevice() throws Exception {
		if ("*".equals(src)) {
			src = "";
		}
		
		File target = new File(dest);
		if (dest.endsWith("/") && !target.exists()) {
			throw new Exception("dest directory " + dest + " does not exist");
		}
		
		boolean isDirectory = false; // indicates that the dest is a directory
		if (target.exists() && target.isDirectory()) {
			isDirectory = true;
		}
		
		Representation entity = ListCommand.getFilesList(srcDev + ":" + src);
		if (entity == null) {
			throw new Exception("resource " + srcDev + ":" + src + " not available or is empty");
		}
		
		DomRepresentation dom = new DomRepresentation(entity);
		Document doc = dom.getDocument();
		NodeList deviceNodes = doc.getFirstChild().getChildNodes();
		if (deviceNodes == null || deviceNodes.getLength() == 0) {
			throw new Exception("not resource available");
		}
		
		ClientConfig config = ClientConfig.getInstance();
		Device[] devices = config.getDevices();
		
		for (int i=0; i<deviceNodes.getLength(); ++i) {
			Element deviceNode = (Element) deviceNodes.item(i);
			NodeList fileNodes = deviceNode.getElementsByTagName("FileList").item(0).getChildNodes();
			List<FileItem> fileItems = new LinkedList<FileItem>();
			List<String> urls = new LinkedList<String>();
			
			String deviceName = deviceNode.getElementsByTagName("Name").item(0).getTextContent();
			
			if ("Google Documents".equals(deviceName) && !"gdocs".equals(srcDev)) {
				// ignore gdocs src files unless the user explicitly asks for it.
				// This is because our filtering mechanism does not work on gdocs.
				continue;
			}
			
			Device device = null;
			for (int j=0; j<devices.length; ++j) {
				if (deviceName.equals(devices[j].getName())) {
					device = devices[j];
					break;
				}
			}
			
			if (device == null) {
				System.out.println(" ignoring offline src device " + deviceName);
				continue;
			}
			
			for (int j=0; j<fileNodes.getLength(); ++j) {
				try {
					Element fileNode = (Element) fileNodes.item(j);
					FileItem fileItem = new FileItem(fileNode);
					if (!fileItem.deleted) {
						String url;
						if ("Google Documents".equals(deviceName)) {
							url = fileNode.getElementsByTagName("URL").item(0).getTextContent();
						}
						else {
							url = device.getURL() + "/" + device.getName() + "/xml/file/" 
								+ (fileItem.path.length() > 0 ? fileItem.path + "/" : "") 
								+ fileItem.name;
						}
						urls.add(url);
						fileItems.add(fileItem);
					}
					else {
						System.out.println("ignoring deleted src file " + fileItem.name);
					}
				}
				catch (Exception e) {
					System.out.println("exception: " + e.getMessage());
				}
			}
			
			Iterator<FileItem> it1 = fileItems.iterator();
			for (Iterator<String> it2=urls.iterator(); it2.hasNext(); ) {
				FileItem fileItem = it1.next();
				String url = it2.next();
				
				String destFileName = (isDirectory ? target.getPath() + "/" + fileItem.name : target.getPath());
				
				try {
					if (url.startsWith("http://docs.google.com")) {
						System.out.println(" download from gdocs not supported");
					}
					else {
						ClientWithToken client = new ClientWithToken();
						System.out.println(" downloading " + url);
						Response response = client.get(url);
						if (!response.getStatus().isSuccess()) {
							System.out.println("error downloading " + url + " " + response.getStatus().toString());
							continue;
						}
						
						Representation fileContent = response.getEntity();
						FileUtil.copyStream(fileContent, new File(destFileName));
					}
				} catch (Exception e2) {
					System.out.println("exception: " + e2.getMessage());
				}
				
			}
		}
	}

	/**
	 * Upload the local list of files to the device.
	 * This uses the REST URL of the form /device/xml/file/path/to/file.ext
	 * @param files
	 * @throws Exception
	 */
	private void uploadToDevice(List<File> files) throws Exception {
		ClientConfig config = ClientConfig.getInstance();
		
		Device[] devices = config.getDevices();
		Device device = null;
		if ("all".equals(destDev)) {
			device = config.getLoginDevice();
		}
		else {
			for (int i=0; i<devices.length; ++i) {
				if (devices[i].getName().equals(destDev)) {
					device = devices[i];
					break;
				}
			}
		}
		
		if (device == null) {
			throw new Exception("dest device " + destDev + " not available");
		}
		
		for (Iterator<File> it=files.iterator(); it.hasNext(); ) {
			File srcFile = it.next();
			
			String destFileName;
			if (dest.equals(""))
				destFileName = srcFile.getName();
			else if (dest.endsWith("/"))
				destFileName = dest + srcFile.getName();
			else
				destFileName = dest;
			
			String url = device.getURL() + "/" + device.getName() + "/xml/file/"
						+ destFileName;
			ClientWithToken client = new ClientWithToken();
			System.out.println(" uploading " + srcFile.getPath() + " to " + url);
			Response response = client.put(url, new FileRepresentation(srcFile, MediaType.APPLICATION_OCTET_STREAM));
			if (response == null || !response.getStatus().isSuccess()) {
				System.out.println(" error uploading " + url + " " + (response != null ? response.getStatus().toString(): ""));
			}
		}
	}
	
	/**
	 * This method is used to extract src and dest arguments, and also handle escaping
	 * using '\' for file names that contain spaces.
	 * @param args
	 * @throws Exception
	 */
	private void parseArgs(String args) throws Exception {
		args = args != null ? args.trim() : args;
		if (args == null || args.length() == 0) {
			throw new Exception("missing src and dest arguments");
		}
		
		StringBuilder src = new StringBuilder();
		StringBuilder dest = new StringBuilder();
		boolean escape = false;
		StringBuilder current = src;
		
		for (int i=0; i<args.length(); ++i) {
			char c = args.charAt(i);
			if (c == '\\' && !escape) {
				escape = true;
				continue;
			}
			
			if (escape || c != ' ') {
				current.append(c);
			}
			else {
				if (current == src) {
					current = dest;
					while (args.charAt(i+1) == ' ')
						++i;
				}
				else if (current == dest) {
					throw new Exception("found more than two arguments");
				}
			}
			escape = false;
		}
		
		if (src.length() == 0)
			throw new Exception("missing src");
		if (dest.length() == 0)
			throw new Exception("missing dest");
		
		this.src = src.toString();
		this.dest = dest.toString();
		
		if (this.src.contains(":")) {
			int index = this.src.indexOf(':');
			this.srcDev = this.src.substring(0, index);
			this.src = this.src.substring(index+1);
		}
		if (this.dest.contains(":")) {
			int index = this.dest.indexOf(':');
			this.destDev = this.dest.substring(0, index);
			this.dest = this.dest.substring(index+1);
		}
	}
	
	
	/**
	 * Upload the list of files to google documents.
	 * @param files
	 */
	private void uploadToGoogle(List<File> files) {
		for (Iterator<File> it=files.iterator(); it.hasNext(); ) {
			File file = it.next();
			try {
				String fileName;
				if (dest.length() == 0)
					fileName = file.getName();
				else if (dest.endsWith("/"))
					fileName = dest + file.getName();
				else
					fileName = dest;
				
				uploadToGoogle(file, fileName);
				System.out.println(" uploaded " + file.getPath() + " to gdocs:" + fileName);
			}
			catch (Exception e) {
				System.out.println(" error uploading " + file.getPath() + ": " + e.getMessage());
			}
		}
	}
	
	/**
	 * Upload a single local file to google documents as the supplied fileName.
	 * @param file
	 * @param fileName
	 * @throws MalformedURLException
	 * @throws IOException
	 * @throws ServiceException
	 */
	private void uploadToGoogle(File file, String fileName) throws MalformedURLException, IOException, ServiceException {
		String mimeType = DocumentListEntry.MediaType.TXT.getMimeType(); 
		try {
			mimeType = DocumentListEntry.MediaType.fromFileName(file.getName()).getMimeType();
		}
		catch (Exception e) {
			//System.out.println(" using mimeType " + mimeType);
		}
		
		DocumentEntry doc = new DocumentEntry();
		doc.setTitle(new PlainTextConstruct(fileName));
		doc.setFile(file, mimeType);
		DocsService service = createNewDocsService();
		@SuppressWarnings("unused")
		DocumentListEntry entry = service.insert(new URL(GoogleDocs.GDOCS_URL), doc);
		//System.out.println(" entry=" + entry.toString());
	}

	/**
	 * Create a new document service object and set teh user token.
	 * @return
	 */
	private DocsService createNewDocsService() {
		DocsService service = new DocsService(GoogleDocs.APPLICATION_NAME);
		String[] userToken = LoginCommand.loadToken().split(" ", 2);
		service.setUserToken(userToken[1]);
		return service;
	}

	/**
	 * Download the source files from google documents.
	 * @throws MalformedURLException
	 * @throws IOException
	 * @throws ServiceException
	 */
	private void downloadFromGoogle() throws MalformedURLException, IOException, ServiceException {
		DocsService service = createNewDocsService();
		DocumentListFeed feed = service.getFeed(new URL(GoogleDocs.GDOCS_URL), DocumentListFeed.class);

		for (Iterator<DocumentListEntry> it = feed.getEntries().iterator(); it.hasNext(); ) {
			DocumentListEntry entry = it.next();
			String title = entry.getTitle().getPlainText();
			if (FileUtil.matchFileName(title, src)) {
				String fileName;
				if (dest.length() == 0)
					fileName = title;
				else if (dest.endsWith("/"))
					fileName = dest + title;
				else
					fileName = title;
				downloadFromGoogle(service, entry, fileName);
			}
		}
	}
	
	/**
	 * Download a single file from google document's service result.
	 * 
	 * @param service
	 * @param entry
	 * @param fileName
	 * @throws IOException
	 * @throws ServiceException
	 */
	private void downloadFromGoogle(DocsService service, DocumentListEntry entry, String fileName) 
			throws IOException, ServiceException {
		String resourceId = entry.getResourceId();
		String docType = resourceId.substring(0, resourceId.lastIndexOf(':'));
		String docId = resourceId.substring(resourceId.lastIndexOf(':') + 1);
		
		URL exportUrl = new URL("http://docs.google.com/feeds/download/" + docType +
		  "s/Export?docID=" + docId);
		
		MediaContent mc = new MediaContent();
		mc.setUri(exportUrl.toString());
		MediaSource ms = service.getMedia(mc);
		
		FileUtil.copyStream(ms, new File(fileName));
	}
}
