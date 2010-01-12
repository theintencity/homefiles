package client;

import org.restlet.data.Response;

/**
 * Implement the "backup" command in the client.
 * 
 * >>> backup path/to/file 2
 * GET /backupdata/path/to/file?command=backup&count=2
 */
public class BackupCommand implements IClientCommand {
	
	public static final String usage = "path/to/file [count]\n" +
			" initiate backup of the specified file on the logged in computer.\n" +
			" count controls the number of backups to create other than primary.\n" +
			" default value for count is 1.";
	
	public void exec(String args) {
		String[] parts = args.split(" ");
		String path = parts[0];
		int count = parts.length < 2 ? 1 : Integer.parseInt(parts[1]);
		
		ClientWithToken client = new ClientWithToken();
		Response response = client.get("/backupdata/" + path + "?command=backup&count=" + count);
		if (response.getStatus().isSuccess()) {
			System.out.println("backup initiated");
		}
		else {
			System.out.println("backup failed: " + response.getStatus().toString());
		}
	}
}
