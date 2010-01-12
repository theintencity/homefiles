package client;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Console;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;

import api.GoogleDocs;

import com.google.gdata.client.ClientLoginAccountType;
import com.google.gdata.client.GoogleAuthTokenFactory.UserToken;
import com.google.gdata.client.GoogleService.InvalidCredentialsException;
import com.google.gdata.client.docs.DocsService;
import com.google.gdata.data.docs.DocumentListFeed;
import com.google.gdata.util.AuthenticationException;
import com.google.gdata.util.ServiceException;

/**
 * Handle the login command, which is implicitly invoked by the client when
 * launched and not already logged in.
 * 
 * @author Mamta
 */
public class LoginCommand implements IClientCommand {
	public static final String usage = "\n" +
		" If not already logged in, then prompts for login.\n" +
		" Login using gmail username (without @gmail.com) and password.";

	public void exec(String args) throws IOException {
		String token = loadToken();
		if (token != null) {
			int index = token.indexOf(' ');
			String username = index >= 0 ? token.substring(0, index) : token;
			System.out.println("Logged in as " + username + ". Use logout first to login as different user.");
		}
		else {
			ensureLogin();
		}
	}

	/**
	 * Make sure that the client is logged in. If not, then it prompts for
	 * username and password.
	 * 
	 * @return
	 */
	public static boolean ensureLogin() {
		String token = loadToken();
		if (token == null) {
			try {
				token = loginPrompt();
			} catch (IOException e) {
				e.printStackTrace();
				return false;
			}
			if (token != null) {
				saveToken(token);
				return true;
			}
			return false;
		}
		return true;
	}
	
	/**
	 * Load the token from the saved file.
	 * 
	 * @return
	 */
	public static String loadToken() {
		try {
			BufferedReader in = new BufferedReader(new FileReader(System.getProperty("user.home") + "/.filesynctoken"));
			String token = in.readLine();
			in.close();
			return token;
		} catch (FileNotFoundException e) {
			//e.printStackTrace(); // not an error
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	/**
	 * Save the token to a user specific file.
	 * 
	 * @param token
	 */
	public static void saveToken(String token) {
		try {
			if (token != null) { 
				BufferedWriter out = new BufferedWriter(new FileWriter(System.getProperty("user.home") + "/.filesynctoken"));
				out.write(token);
				out.close();
			}
			else {
				File file = new File(System.getProperty("user.home") + "/.filesynctoken");
				file.delete();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Prompt the user for login prompt. It disables the console display for
	 * password.
	 * 
	 * @return
	 * @throws IOException
	 */
	public static String loginPrompt() throws IOException {
		BufferedReader in = new BufferedReader(new InputStreamReader(System.in));

		System.out.print("login: ");
		String username = in.readLine() + "@gmail.com";
		System.out.print("password: ");
		Console console = System.console();
		String password;
		if (console == null) {
			password = in.readLine();
		}
		else {
			password = new String(console.readPassword());
		}
		
		try {
			DocsService service = new DocsService(GoogleDocs.APPLICATION_NAME);
			service.setUserCredentials(username, password, ClientLoginAccountType.GOOGLE);
			URL feedUrl = new URL( "http://docs.google.com/feeds/default/private/full");
			service.getFeed(feedUrl, DocumentListFeed.class);
			UserToken authToken = (UserToken) service.getAuthTokenFactory().getAuthToken();
			String token = username + " " + authToken.getValue();
			return token;
		}
		catch (InvalidCredentialsException e) {
			System.out.println("Invalid credentials. Please try again.");
		} catch (AuthenticationException e) {
			e.printStackTrace();
		} catch (ServiceException e) {
			e.printStackTrace();
		}
		return null;
	}
}
