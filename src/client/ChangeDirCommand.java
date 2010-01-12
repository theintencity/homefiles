package client;

import java.io.IOException;

/**
 * Implement the cd or chdir command to change the current directory in the 
 * client config.
 * 
 * @author Mamta
 */
public class ChangeDirCommand implements IClientCommand {

	public static final String usage = "<new-path>\n"
		+ " where <new-path> is of form dir1, dir1/subdir2, /topdir1/dir or ..";
	
	public void exec(String args) throws IOException {
		args = (args != null ? args.trim() : args);
		ClientConfig config = ClientConfig.getInstance();
		
		if (args == null || args.length() == 0) {
			System.out.println(config.getCurrentDevice() + ": " + config.getCurrentDir());
		}
		else if ("..".equals(args)){
			String parent = config.getCurrentDir();
			int index = parent.lastIndexOf('/');
			parent = index >= 0 ? parent.substring(0, index) : "";
			System.out.println("path: " + parent);
			config.setCurrentDir(parent);
		}
		else {
			String parent = config.getCurrentDir();
			if (parent == null || parent.length() == 0)
				parent = args;
			else if (args.startsWith("/"))
				parent = args.substring(1);
			else
				parent = parent + "/" + args;
			System.out.println("path: " + parent);
			config.setCurrentDir(parent);
		}
	}

}
