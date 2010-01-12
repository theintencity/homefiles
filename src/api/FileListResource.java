package api;

import java.io.File;
import java.net.URISyntaxException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;

import org.restlet.Context;  
import org.restlet.data.MediaType;  
import org.restlet.data.Request;  
import org.restlet.data.Response;  
import org.restlet.data.Status;
import org.restlet.resource.DomRepresentation;
import org.restlet.resource.FileRepresentation;
import org.restlet.resource.Representation;  
import org.restlet.resource.ResourceException;  
import org.restlet.resource.StringRepresentation;  
import org.restlet.resource.TransformRepresentation;
import org.restlet.resource.Variant;  
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import db.Database;
import dev.Device;
import dev.DeviceUpdater;
  
/** 
 * The filelist resource represents a list of files using XML. It gets attributes
 * such as devicename, contains, matches and date, queries the database for those
 * attributes and returns the results. 
 */  
public class FileListResource extends BaseResource {  
  
	/**
	 * The stylesheet file name that is used in XSL transformation to generate
	 * HTML pages.
	 */
	public static String stylesheet = "";
	
	/**
	 * The devices list.
	 */
	public static DeviceUpdater deviceUpdater;
	
	/**
	 * Construct a new resource and authenticate.
	 * 
	 * @param context
	 * @param request
	 * @param response
	 */
	public FileListResource(Context context, Request request, Response response) {  
		super(context, request, response);  
		
		if (!authenticate())
			return;
		
		List<String> seg = request.getResourceRef().getSegments();
		if (seg.size() >= 2 && "html".equals(seg.get(1)))
			getVariants().add(new Variant(MediaType.TEXT_HTML));
		else
			getVariants().add(new Variant(MediaType.TEXT_XML));
	}  
	 
    /** 
	 * Returns the XML of HTML file list response. 
	 */  
	@Override  
	public Representation represent(Variant variant) throws ResourceException {

		Request request = getRequest();
		Response response = getResponse();
		
		// extract the search parameters.
		
		String devicename = (String) request.getAttributes().get("devicename");
		String query = request.getResourceRef().getQuery();
		String contains = (String) request.getAttributes().get("contains");
		String matches = (String) request.getAttributes().get("matches");
		String date = (String) request.getAttributes().get("date");
		
		// create a new DOM for result
		Document doc;
		try {
			doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
		} catch (ParserConfigurationException e1) {
			e1.printStackTrace();
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL, "ParserConfigurationException");
		}
		
		// use the Database class to get the files list.
		try {
			
			// get the selected XML node with all the devices and files.
			Node node = getDatabaseFiles(gdocs.getUsername(), devicename, query, matches, contains, date);

			if (node != null) {
				
				// update the DOM with this XML node. Also add the URL property if the 
				// device is online so that user can visit that device if needed.
				
				node = doc.adoptNode(node);
				NodeList devices = node.getChildNodes();
				for (int i=0; devices != null && i<devices.getLength(); ++i) {
					Element device = (Element) devices.item(i);
					String name = device.getElementsByTagName("Name").item(0).getTextContent();
					Device dev = deviceUpdater.getDevice(name);
					Node filelist = device.getElementsByTagName("FileList").item(0);
					if (dev != null) {
						Element url = doc.createElement("URL");
						url.appendChild(doc.createTextNode(dev.getURL()));
						device.insertBefore(url, filelist);
					}
				}
				doc.appendChild(doc.adoptNode(node));
			}
			
		} catch (XPathExpressionException e) {
			e.printStackTrace();
			response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "XPathExpressionException");
			return new StringRepresentation("Error: Invalid XPath expression");
		} catch (InterruptedException e) {
			e.printStackTrace();
			throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST, "InterruptedException");
		} catch (ParseException e) {
			e.printStackTrace();
			response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "ParseException");
			return new StringRepresentation("Error: parsing date string. Use MM-dd-yyyy format");
		} catch (URISyntaxException e) {
			e.printStackTrace();
			response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "URISyntaxException");
			return new StringRepresentation("Error: " + e.getReason());
		}
		
		// if the devicename is "all" or "gdocs" then add the files from the
		// Google documents also.
		if ("all".equals(devicename) || "gdocs".equals(devicename)) {
			try {
				// for HTML we also need device nodes
				Node gdocsFiles = gdocs.viewDocs();
				doc.getFirstChild().appendChild(doc.adoptNode(gdocsFiles));
			} catch (Exception e) {
				e.printStackTrace();
			}
		}		
		
		// now create the representation
		Representation result = null;
		try {
			// first create DOM XML
			DomRepresentation dom = new DomRepresentation(MediaType.TEXT_XML, doc); 
			if (variant.getMediaType() == MediaType.TEXT_XML){
				result = dom;
			}
			else {
				// for anything else, e.g., HTML, return the HTML using XSLT
				result = new TransformRepresentation(getContext(), dom, 
						new FileRepresentation(new File(FileListResource.stylesheet), MediaType.TEXT_HTML, -1));
			}				
		} catch (Exception e) {
			e.printStackTrace();
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL, e.getMessage());
		}
		
		return result;
	}  

	/**
	 * Get the query result using database functions. The returned element is returned
	 * in this resource's GET response.
	 * 
	 * @param username
	 * @param devicename
	 * @param query
	 * @param matches
	 * @param contains
	 * @param date
	 * @return
	 * @throws XPathExpressionException
	 * @throws InterruptedException
	 * @throws ParseException
	 * @throws URISyntaxException
	 */
	private Node getDatabaseFiles(String username, String devicename, String query, 
			String matches, String contains, String date) 
			throws XPathExpressionException, InterruptedException, ParseException, URISyntaxException {
		
		Node result;
		
		Database db = Database.getInstance();
		
		if (matches != null) {
			result = db.matchFilesAlt(username, devicename, matches);
		}
		else if (contains != null) {
			result = db.containFilesAlt(username, devicename, contains);
		}
		else if (date != null) {
			DateFormat format = new SimpleDateFormat("MM-dd-yyyy");
			Date d = format.parse(date);
			result = db.modifiedFilesAlt(username, devicename, d);
		}
		else { // even if this in invalid query, use no query case
			result = db.getFilesAlt(username, devicename);
		}
		
		// add the localdevice name
		if (result != null) {
			((Element) result).setAttribute("localdevice", db.getLocalDevice());
		}
		return result;
	}
	
}  
