package api;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import java.util.Iterator;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.restlet.data.Cookie;
import org.restlet.data.CookieSetting;
import org.restlet.data.Form;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.data.Status;
import org.restlet.util.Series;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import com.google.gdata.data.*;
import com.google.gdata.data.docs.DocumentEntry;
import com.google.gdata.data.docs.DocumentListEntry;
import com.google.gdata.data.docs.DocumentListFeed;

import com.google.gdata.util.*;
import com.google.gdata.client.docs.DocsService;
import com.google.gdata.client.http.*;

import db.FileItem;

/** 
 * The class handles interaction with GoogleDocs API including login, view and
 * upload.
 */  
public class GoogleDocs {  
	
	// the name of the cookie. The user's email is appended to this COOKIE name.
	public static final String COOKIE = "gdocs-cookie-msingh4-"; 
	public static final String APPLICATION_NAME = "FileSync";
	public static final String GDOCS_URL = "http://docs.google.com/feeds/default/private/full";
	public static final String GDOCS_BASE_URL = "http://docs.google.com";
	public static final String GDOCS_DOWNLOAD_URL = "http://docs.google.com/feeds/download/documents/Export?docID=";
	
	// the document service object for the Google Document API
	private DocsService service;
	
	// the XML document factory to build an XML representation
	private DocumentBuilderFactory docFactory;
	private DocumentBuilder docBuilder;
	
	// the user name extracted from feed in getToken
	private String username;
	
	/**
	 * Construct a new object which initializes internal variables.
	 */
	public GoogleDocs() {
		service = new DocsService(APPLICATION_NAME);

		docFactory = DocumentBuilderFactory.newInstance();
		docFactory.setNamespaceAware(true);
		try {
			docBuilder = docFactory.newDocumentBuilder();
		} catch (ParserConfigurationException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Start the authentication process with the GoogleDocs API. This invokes the
	 * AuthSub's first method getRequestUrl to get the authentication page's URL.
	 * The client is redirected to this page.
	 * 
	 * @param request
	 * @param response
	 */
	public void startAuth(Request request, Response response) {
		String scope = GDOCS_URL;
		String next = request.getResourceRef().toString();
		String requestUrl = AuthSubUtil.getRequestUrl(next, scope, false, true);
		response.setLocationRef(requestUrl);
		response.setStatus(Status.REDIRECTION_TEMPORARY, "Redirect");
	}
	
	/**
	 * From the request, get the authentication token if available. First it checks
	 * if the token is available in the COOKIE. If not, then it checks the query
	 * parameters ?token= in the request URL. This is the case after user has 
	 * completed the login on GoogleDocs page and the GoogleDocs page redirects
	 * the user to our page again with the token parameter. If no token is found 
	 * in cookie or parameter, then authentication process is started. If a token
	 * is found in the query parameter, then it is exchanged for a session token
	 * using AuthSubUtil's method. The session token is set as a cookie so that
	 * next client request will have that session token in the cookie. If an error 
	 * happens in exchanging the token, then the authentication process is started
	 * again.
	 * 
	 * @param request
	 * @param response
	 * @return The session token string.
	 */
	public String getToken(Request request, Response response) {
		
		Form authForm = (Form) request.getAttributes().get("org.restlet.http.headers");
		String nameToken = authForm.getFirstValue("x-token");
		System.out.println("  x-token " + (nameToken != null ? nameToken.substring(0, 70) : null));
		if (nameToken != null) {
			return getTokenFromHeader(request, response, nameToken); 
		}
		else {
			return getTokenFromCookie(request, response);
		}
	}
	
	/**
	 * Extract the token from the header of the request. This is used when the client
	 * is supplying the x-token header with username and token to be used for that
	 * client.
	 * 
	 * @param request
	 * @param response
	 * @param nameToken
	 * @return
	 */
	public String getTokenFromHeader(Request request, Response response, String nameToken) {
		if (nameToken != null) {
			int index = nameToken.indexOf(' ');
			if (index >= 0) {
				username = nameToken.substring(0, index);
				String token = nameToken.substring(index+1);
				service.setUserToken(token);
				return token;
			}
		}
		
		response.setStatus(Status.CLIENT_ERROR_UNAUTHORIZED, "Supply x-token header");
		return null;
	}
	
	/**
	 * Extract the token from the cookies or parameters as applicable. This is used
	 * when the browser allows the user to login.
	 * 
	 * See the help on getToken for more details.
	 * 
	 * @param request
	 * @param response
	 * @return
	 */
	public String getTokenFromCookie(Request request, Response response) {
		Series<Cookie> cookies = request.getCookies();
		Set<String> names = cookies.getNames();
		String email = null;
		for (Iterator<String> it=names.iterator(); it.hasNext(); ) {
			String name = it.next();
			if (name.startsWith(COOKIE)) {
				email = name.substring(COOKIE.length());
				username = email;
				break;
			}
		}
		
		String token = cookies.getFirstValue(COOKIE + (email != null ? email : ""));
		
		if (token != null) {
			System.out.println("  token (from cookie)=" + token + " email=" + email);
		}
		else {
			boolean hasToken = request.getAttributes().containsKey("token");
			if (!hasToken && request.getResourceRef().getQuery() != null) {
				String query = request.getResourceRef().getQuery();
				System.out.println("  token query=" + query);
				int index = query.lastIndexOf("token=");
				if (index>=0) {
					token = query.substring(index+6);
					hasToken = true;
				}
			}
			if (!hasToken) {
				startAuth(request, response);
				return null;
			}
			else {
				if (token == null) {
					token = (String) request.getAttributes().get("token");
					System.out.println("  token (from URL)=" + token);
				}
				try {
					token = AuthSubUtil.exchangeForSessionToken(token, null);
				} catch (Exception e) {
					e.printStackTrace();
					startAuth(request, response);
					return null;
				}
				
				System.out.println("  token (session)=" + token);

				String cookieName = COOKIE; 
				try {
					// try appending user's email to cookieName so that the token is
					// associated with one user email only.
					service.setAuthSubToken(token);
					DocumentListFeed feed = service.getFeed(new URL(GDOCS_URL), DocumentListFeed.class);
					System.out.println("  email=" + feed.getAuthors().get(0).getEmail());
					username = feed.getAuthors().get(0).getEmail();
					cookieName = cookieName + username;
				}
				catch (Exception e) {
					e.printStackTrace();
				}
				
				//Use global path=/ so that auth is reused for upload also.
				CookieSetting cookie = new CookieSetting(cookieName, token);
				cookie.setPath("/");
				response.getCookieSettings().add(cookie);
				
			}
		}
		
		System.out.println("  using token=" + token);
		
		service.setAuthSubToken(token);
		return token;
	}
	
	/**
	 * Clear the cookie that associates the token for google documents.
	 * 
	 * @param request
	 * @param response
	 */
	public void clearCookie(Request request, Response response) {
		
		Series<Cookie> cookies = request.getCookies();
		Set<String> names = cookies.getNames();
		for (Iterator<String> it=names.iterator(); it.hasNext(); ) {
			String name = it.next();
			if (name.startsWith(COOKIE)) {
				cookies.removeAll(name);
				CookieSetting cookie = new CookieSetting(name, "");
				cookie.setPath("/");
				cookie.setMaxAge(0);
				response.getCookieSettings().add(cookie);
			}
		}		
	}
	
	/**
	 * View all the documents available in user's GoogleDocs account.
	 * It returns a XML Device element which contacts FileList which in turn contains
	 * File elements. Each File element describes a file in GoogleDocs. The
	 * generated XML has a URL element which allows the XSLT to use that URL to
	 * download the file directly from the GoogleDocs. The title of the document is
	 * used as the File's Name. The File's path is set to empty string. The 
	 * getUpdated() and isTrashed() methods of the DocumentListEntry give information 
	 * about LastModified and Deleted elements. The file Size is set as -1 which is
	 * rendered as "unknown" and represents unknown file size.
	 * 
	 * @return The Node of <Device/> elements representing files in GoogleDocs account.
	 * @throws MalformedURLException
	 * @throws IOException
	 * @throws ServiceException
	 */
	public Node viewDocs() throws MalformedURLException, IOException, ServiceException {
		DocumentListFeed feed = service.getFeed(new URL(GDOCS_URL), DocumentListFeed.class);
		
		Document doc = docBuilder.newDocument();
		Element device = doc.createElement("Device");
		Element name = doc.createElement("Name");
		name.appendChild(doc.createTextNode("Google Documents"));
		Element status = doc.createElement("OnlineStatus");
		status.appendChild(doc.createTextNode("online"));
		Element url = doc.createElement("URL");
		url.appendChild(doc.createTextNode(GDOCS_BASE_URL));
		Element filelist = doc.createElement("FileList");
		
		for (Iterator<DocumentListEntry> it = feed.getEntries().iterator(); it.hasNext(); ) {
			DocumentListEntry entry = it.next();
			FileItem fi = new FileItem(entry.getTitle().getPlainText(), "", 
					(new Date(entry.getUpdated().getValue())).getTime(), -1, entry.isTrashed());
			Element node = (Element) fi.toNode(doc);
			Element link = doc.createElement("URL");
			link.setTextContent(entry.getDocumentLink().getHref());
			node.appendChild(link);
			filelist.appendChild(node);
		}
		
		device.appendChild(name);
		device.appendChild(status);
		device.appendChild(url);
		device.appendChild(filelist);
		return device;
	}
	
	/**
	 * Upload a given local file to user's GoogleDocs account. It sets the Title of the
	 * document as the file name. 
	 *  
	 * @param file
	 * @throws MalformedURLException
	 * @throws IOException
	 * @throws ServiceException
	 */
	public void uploadFile(File file) throws MalformedURLException, IOException, ServiceException {
		String mimeType = DocumentListEntry.MediaType.fromFileName(file.getName()).getMimeType();
		DocumentEntry doc = new DocumentEntry();
		doc.setTitle(new PlainTextConstruct(file.getName()));
		doc.setFile(file, mimeType);
		DocumentListEntry entry = service.insert(new URL(GDOCS_URL), doc);
		System.out.println("  entry=" + entry.toString());
	}
	
	/**
	 * Return the username (email) extracted from feed after a getToken.
	 */
	public String getUsername() {
		return username;
	}
}  
