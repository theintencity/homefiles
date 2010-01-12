package db;

/**
 * Implements the LocalDir data class that stores the user name, local directory and
 * device name.
 *
 * @author Mamta
 */
public class LocalDir {
	public String userName;
	public String local_dir;
	public String deviceName;
	
	public String toString() {
		return "user='" + userName + "' device='" + deviceName + "' local_dir='" + local_dir + "'";
	}
}
