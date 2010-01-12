import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

import client.*;

/**
 * The main application for the file sync client. This launches in two modes: if command
 * line commands are supplied, then those are executed and then the application terminates.
 * Otherwise it gives shell-style prompt to the user to enter interactive commands.
 */
public class FileSyncClient {
	
	/**
	 * Map of all the command handlers indexed by command name.
	 */
	@SuppressWarnings("unchecked")
	private Map<String, Class> commands;
	
	/**
	 * List of comma separated commands which is printed on help.
	 */
	private String commandsList;
	
	/**
	 * The constructor just installs the commands to the command handler.
	 */
	@SuppressWarnings("unchecked")
	public FileSyncClient() {
		commands = new LinkedHashMap<String, Class>();
		commands.put("login", LoginCommand.class);
		commands.put("logout", LogoutCommand.class);
		commands.put("device", DeviceCommand.class);
		commands.put("dev", DeviceCommand.class);
		commands.put("devices", DevicesCommand.class);
		commands.put("devs", DevicesCommand.class);
		commands.put("rootdir", RootDirCommand.class);
		commands.put("select", SelectDeviceCommand.class);
		commands.put("list", ListCommand.class);
		commands.put("ls", ListCommand.class);
		commands.put("chdir", ChangeDirCommand.class);
		commands.put("cd", ChangeDirCommand.class);
		commands.put("copy", CopyCommand.class);
		commands.put("cp", CopyCommand.class);
		commands.put("backup", BackupCommand.class);
		commands.put("restore", RestoreCommand.class);

		StringBuilder sb = new StringBuilder();
		for (Iterator<String> it=commands.keySet().iterator(); it.hasNext(); ) {
			sb.append(it.next());
			if (it.hasNext()) {
				sb.append(", ");
			}
		}
		commandsList = sb.toString();
	}
	
	/**
	 * The command executer method takes a command, extracts the command name
	 * and arguments, finds the command handler, and hands over the arguments to
	 * that command handler.
	 * 
	 * @param command
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 * @throws IOException
	 */
	public void exec(String command) 
	throws InstantiationException, IllegalAccessException, IOException {
		command = command.trim();
		int index = command.indexOf(" ");
		String args = null;
		if (index >= 0) {
			args = command.substring(index+1);
			command = command.substring(0, index);
		}
		if (!commands.containsKey(command)) {
			usage(command, args != null ? args.trim() : null);
			return;
		}
		
		Object obj = commands.get(command).newInstance();
		((IClientCommand) obj).exec(args);
	}
	
	/**
	 * Method to print the usage of a particular command. It uses the "usage" static
	 * property of that command handler class to extract the usage information.
	 * If there is no command supplied, or if the command is not valid, it just
	 * prints the list of available commands.
	 * 
	 * @param command
	 * @param args
	 */
	@SuppressWarnings("unchecked")
	public void usage(String command, String args) {
		if (!command.equals("help") || args == null || args.length() == 0) {
			if (!command.equals("help"))
				System.out.println("Invalid command: " + command);
			System.out.println("Allowed commands are " + commandsList);
			System.out.println("Use \"help command\" for more details on individual command");
		}
		else {
			Class cls = commands.get(args);
			if (cls == null) {
				System.out.println("No such command: " + args);
			}
			else {
				try {
					Field field = cls.getDeclaredField("usage");
					String value = (String) field.get(cls);
					System.out.println(args + " " + value);
				} catch (Exception e) {
					System.out.println("No usage defined for command: " + args);
				}
			}
		}
	}
	
	/**
	 * The main routine. It uses the NAMESERVER address:port from the environment
	 * variable and starts the device updater thread using multicast. Next, it performs
	 * login command. Then it either executes all the supplied command line arguments
	 * as commands, or it presents a shell-like prompt to the user for interative 
	 * command handling.
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		
		Logger.getLogger("org.restlet").setUseParentHandlers(false);

		// nameserver is obtained from NAMESERVER environment variable
		String ns = System.getenv("NAMESERVER");
		if (ns == null) {
			System.err.println("Please set NAMESERVER and invoke the command again");
			return;
		}
		
		ClientConfig config = ClientConfig.getInstance();
		config.setNameServer(ns);
		
		FileSyncClient client = new FileSyncClient();
		
		// make sure it is logged in, prompt, and exit on failure.
		if (!LoginCommand.ensureLogin()) {
			config.getUpdater().close();
			return;
		}
		
		if (args.length > 0) {
			for (int i=0; i<args.length; ++i) {
				try {
					client.exec(args[i]);
				}
				catch (Exception e) {
					System.out.println("Ignoring remaining commands, if any");
					e.printStackTrace();
					break;
				}
			}
		}
		else {
			BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
			
			while (true) {
				System.out.print(">>> ");
				System.out.flush();
				String command;
				try {
					command = in.readLine();
				} catch (IOException e1) {
					e1.printStackTrace();
					break;
				}
				
				if (command == null || "exit".equals(command) || "quit".equals(command)) {
					System.out.println("Goodbye");
					break;
				}

				if (command.length() > 0) {
					try {
						client.exec(command);
						if (command.startsWith("logout")) {
							System.out.println("Goodbye");
							break;
						}
					}
					catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
		}
		
		config.getUpdater().close();
	}
}
