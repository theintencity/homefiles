package client;

import org.restlet.data.Response;

/**
 * Handle the restore command.
 * 
 * >>> restore path/to/deleted-file
 * GET /backupdata/path/to/deleted-file?command=restore
 * 
 * @author Mamta
 */
public class RestoreCommand implements IClientCommand {
	
	public static final String usage = "path/to/deleted-file\n" +
			" initiate restore of the deleted file on the logged in computer.";
	
	public void exec(String args) {
		ClientWithToken client = new ClientWithToken();
		Response response = client.get("/backupdata/" + args + "?command=restore");
		if (response.getStatus().isSuccess()) {
			System.out.println("restore completed");
		}
		else {
			System.out.println("restore failed: " + response.getStatus().toString());
		}
	}
}
