package client;

import java.io.IOException;

/**
 * Handle the logout command.
 * 
 * @author Mamta
 */
public class LogoutCommand implements IClientCommand {
	public static final String usage = "\n" +
		" if already logged in, logout the client and terminate.";

	public void exec(String args) throws IOException {
		// clear the token. The caller exists the command prompt loop.
		LoginCommand.saveToken(null);
	}

}
