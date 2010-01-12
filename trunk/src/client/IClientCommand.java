package client;

import java.io.IOException;

/**
 * An interface implemented by various command handlers.
 * 
 * @author Mamta
 */
public interface IClientCommand {
	void exec(String args) throws IOException;
}
