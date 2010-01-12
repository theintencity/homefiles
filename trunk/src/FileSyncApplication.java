import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Date;
import java.util.Properties;

import org.restlet.Application;  
import org.restlet.Component;
import org.restlet.Restlet;  
import org.restlet.Router; 
import org.restlet.data.Protocol;

import db.Database;
import db.Updater;
import dev.BackupThread;
import dev.BackupdataResource;
import dev.Device;
import dev.DeviceMulticastUpdater;
import dev.DeviceUpdater;
import dev.MetadataResource;
import dev.MetadataUpdater;
import api.DefaultResource;
import api.FileListResource;
import api.FileDownloadResource;
import api.LogoutResource;
import api.UploadResource;
import api.LoginResource;
import api.SettingsResource;

/**
 * The main entry point in the server application for File Sync.
 */
public class FileSyncApplication extends Application {  
  
    /** 
     * Creates a root Restlet that will receive all incoming calls. 
     */  
    @Override  
    public Restlet createRoot() {  
        // Create a router Restlet that routes each call to a new resource 
        Router router = new Router(getContext());  
  
        // Defines route  
        router.attach("/login", LoginResource.class);
        router.attach("/logout", LogoutResource.class);
        router.attach("/settings", SettingsResource.class);
        router.attach("/settings?rootdir={directory}", SettingsResource.class);
        router.attach("/{devicename}/xml/filelist", FileListResource.class);
        router.attach("/{devicename}/xml/filelist?matches={matches}", FileListResource.class);
        router.attach("/{devicename}/xml/filelist?contains={contains}", FileListResource.class);
        router.attach("/{devicename}/xml/filelist?modifiedsince={date}", FileListResource.class);
        router.attach("/{devicename}/xml/filelist?token={token}", FileListResource.class);
        router.attach("/{devicename}/html/filelist", FileListResource.class);
        router.attach("/{devicename}/html/filelist?matches={matches}", FileListResource.class);
        router.attach("/{devicename}/html/filelist?contains={contains}", FileListResource.class);
        router.attach("/{devicename}/html/filelist?modifiedsince={date}", FileListResource.class);
        router.attach("/{devicename}/html/filelist?token={token}", FileListResource.class);
        router.attach("/xml/gdocsupload/", UploadResource.class);
        router.attach("/html/gdocsupload/", UploadResource.class);
        router.attach("/{devicename}/xml/file/", FileDownloadResource.class);
        router.attach("/{devicename}/html/file/", FileDownloadResource.class);
        router.attach("/metadata", MetadataResource.class);
        router.attach("/backupdata/", BackupdataResource.class);

        router.attachDefault(DefaultResource.class);
  
        return router;  
    }  

    public static void main(String[] args) {
        try {
        	// get the local hostname and IP address.
        	String local_ip = null;
        	String local_host = null;
        	
        	try {
        		local_ip = InetAddress.getLocalHost().getHostAddress();
        		local_host = InetAddress.getLocalHost().getHostName();
        	} 
        	catch (Exception e) {
        		System.out.println("Cannot get local host name or IP address. Using localhost.");
        		local_ip = "127.0.0.1";
        		local_host = "locahost";
        	}
        	
        	// read the configuration file
        	Properties properties = new Properties();
        	try {
        		String filename = args.length > 0 ? args[0] : "filesync.properties";
        		properties.load(new FileInputStream(filename));
        	}
        	catch (IOException e) {
        		// ignore the error
        		System.out.println("Ignoring error while reading properties file. Please use a filesync.properties file");
        	}
        	
        	// define property as variable
        	String device_name = properties.getProperty("device_name", local_host);
        	int port = Integer.valueOf(properties.getProperty("port", "3000")).intValue();
        	String database = properties.getProperty("database", "db-" + device_name + ".xml");
        	String stylesheet = properties.getProperty("stylesheet", "db.xsl");
        	int update_interval = Integer.valueOf(properties.getProperty("update_interval", "10000")).intValue();
        	int backup_interval = Integer.valueOf(properties.getProperty("backup_interval", "19000")).intValue();
        	int device_interval = Integer.valueOf(properties.getProperty("device_interval", "11000")).intValue();
        	String nameserver_ip = properties.getProperty("nameserver_ip", "127.0.0.1");
        	int nameserver_port = Integer.valueOf(properties.getProperty("nameserver_port", "2500"));
        	String backup_dir = properties.getProperty("backup_dir", "backup-" + device_name);
        	
        	// validate certain property items.
        	if (port <= 1024 || port >= 65536) {
        		System.err.println("port must be > 1024 and < 65536. port=" + port);
        		return;
        	}
        	
        	// store the properties in FileListResource
        	FileListResource.stylesheet = stylesheet;
        	BackupdataResource.backup_dir = backup_dir;
        	
        	Database db = Database.getInstance();
        	db.setLocalDevice(device_name); // the device name is stored in db

        	try {
        		db.importFrom(database);
        	}
           	catch (FileNotFoundException e) {
           		// ignore the exception
           		System.out.println("File not found: " + database + ". ignored");
           	}
           	
           	db.setDeviceStatus(null, "offline"); // set all device status to offline initially
           	db.setDeviceStatus(device_name, "online"); // except this, until nameserver is pinged.
        	
           	// create the local device and name server device
        	Device nameserver = new Device(nameserver_ip, nameserver_port);
        	Device localdevice = new Device(local_ip, port, device_name, (new Date()).getTime());
        	
        	// create the device updater thread
        	DeviceUpdater.interval = device_interval;
        	DeviceUpdater deviceUpdater = new DeviceMulticastUpdater(nameserver, localdevice); 
        	Thread th0 = new Thread(deviceUpdater);
        	th0.start();
        	
        	FileListResource.deviceUpdater = deviceUpdater;
           	BackupdataResource.deviceUpdater = deviceUpdater;
        	
           	// increment the clock of the local device
           	db.incrVersion(localdevice.getName());
           	
           	// create the metadata updater 
           	MetadataUpdater mupdater = new MetadataUpdater(deviceUpdater);
           	deviceUpdater.setListener(mupdater);
           	
           	// create the database updater thread
           	Updater updater = new Updater(db, update_interval, database);
           	updater.setListener(mupdater);
			Thread th1 = new Thread(updater);
			th1.start();
        	
           	BackupThread backup = new BackupThread(db, backup_interval);
           	BackupThread.deviceUpdater = deviceUpdater;
			Thread th2 = new Thread(backup);
			th2.start();
        	
            // Create a new Component.
            Component component = new Component();

            // Add a new HTTP server listening on port.
            component.getServers().add(Protocol.HTTP, port);

            // Attach the application.
            component.getDefaultHost().attach(new FileSyncApplication());

            // Start the component.
            component.start();
        } catch (Exception e) {
            // Something is wrong.
            e.printStackTrace();
        }
    }
}  
