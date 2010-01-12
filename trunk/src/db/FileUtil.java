package db;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.restlet.resource.Representation;

import com.google.gdata.data.media.MediaSource;

/**
 * A helper class for file related functions. All the methods use normalize
 * path separator of '/' so that the path can be reused in URLs.
 * 
 * @author Mamta
 */
public class FileUtil {
	
	/**
	 * Given the rootdir (root directory) and path (some file path), construct a new
	 * normalized path "rootdir/path". If the path is already an absolute path, then
	 * it does not prefix with rootdir. If the path is empty, it just returns rootdir.
	 *
	 * @param path
	 * @param rootdir
	 * @return
	 */
	public static String getFullPath(String path, String rootdir) {
		if (path == null || path.length() == 0)
			return rootdir;
		// must use "/" even for windows since we normalize to use "/" as in URL
		if (path.startsWith("/"))
			return path;
		return rootdir + "/" + path;
	}
	
	/**
	 * Returns a path string for path relative to the rootdir. For example if
	 * path starts with the rootdir, then the common prefix is removed from the path.
	 * Otherwise, the returned value is prefixed with "/".
	 * 
	 * @param path
	 * @param rootdir
	 * @return
	 */
	public static String getRelativePath(String path, String rootdir) {
		String relative = null;
		if (path.startsWith(rootdir)) {
			relative = path.substring(rootdir.length());
			if (relative == null)
				relative = "";
			if (relative.startsWith("/"))
				relative = relative.substring(1);
		}
		else {
			relative = path;
			// must use "/" even for windows since we normalize to use "/" as in URL
			if (!relative.startsWith("/"))
				relative = "/" + relative;
		}
		return relative;
	}
	
	/**
	 * Get the list of files for the given pattern from local file system. The name
	 * can be something like "bin/*.txt", "/usr/local/file", "*".
	 * 
	 * @param name
	 * @return
	 */
	public static List<File> getFiles(String name) {
		if (name.startsWith("/"))
			return getFiles(name.substring(1), new File("/"));
		else
			return getFiles(name, new File("."));
	}
	
	/**
	 * Get the list of files for the given pattern name relative to the directory dir.
	 *  
	 * @param name
	 * @param dir
	 * @return
	 */
	private static List<File> getFiles(String name, File dir) {
		int index = name.indexOf('/');
		if (index < 0) {
			return getFilesHere(name, dir);
		}
		else {
			List<File> result = new LinkedList<File>();
			List<File> subdir = getFilesHere(name.substring(0, index), dir);
			for (Iterator<File> it=subdir.iterator(); it.hasNext(); ) {
				result.addAll(getFiles(name.substring(index+1), it.next()));
			}
			return result;
		}
	}
	
	/**
	 * Get the list of files or directories in dir that match the name's first
	 * part.
	 * 
	 * @param name
	 * @param dir
	 * @return
	 */
	private static List<File> getFilesHere(String name, File dir) {
		int index = name.indexOf('*');
		List<File> result = new LinkedList<File>();
		if (index < 0) {
			result.add(new File(dir + "/" + name));
		}
		else {
			File[] files = dir.listFiles();
			GlobMatch match = new GlobMatch();
			for (int i=0; i<files.length; ++i) {
				if (match.match(files[i].getName(), name)) {
					result.add(files[i]);
				}
			}
		}
		return result;
	}
	
	/**
	 * Check whether the fileName matches the given glob style pattern.
	 *  
	 * @param fileName
	 * @param pattern
	 * @return
	 */
	public static boolean matchFileName(String fileName, String pattern) {
		GlobMatch match = new GlobMatch();
		return ("*".equals(pattern) || fileName.equals(pattern) || match.match(fileName, pattern));
	}
	
	/**
	 * prompt for overwriting if "out" file already exists. Return true if the 
	 * the file does not exist, or if the user wants to overwrite it.
	 * 
	 * @param out
	 * @return
	 * @throws IOException 
	 */
	private static boolean promptIfNeeded(File out) {
		if (out.exists()) {
			try {
				System.out.print("overwrite " + out.getPath() + " [y/n]? ");
				System.out.flush();
				int ch = System.in.read();
				while (System.in.read() != '\n');
				if (ch != 'y' && ch != 'Y') {
					return false;
				}
			} catch (IOException e) {
				// ignore
				return false;
			}
		}
		return true;
	}
	
	/**
	 * Copy bytes from input stream to output stream.
	 * 
	 * @param inStream
	 * @param outStream
	 * @throws IOException
	 */
	public static void copyStream(InputStream inStream, OutputStream outStream) 
			throws IOException {
		int c;
		while ((c = inStream.read()) != -1) {
			outStream.write(c);
		}
	}
	
	/**
	 * Copy bytes from input representation to the output file. Prompt for overwrite
	 * if needed
	 * 
	 * @param in
	 * @param out
	 * @return
	 */
	public static boolean copyStream(Representation in, File out) {
		return copyStream(in, out, true);
	}
	
	/**
	 * Copy bytes from input representation to the output file.
	 * 
	 * @param in
	 * @param out
	 * @return
	 */
	public static boolean copyStream(Representation in, File out, boolean promptOverwrite) {
		if (promptOverwrite && !promptIfNeeded(out)) {
			return false;
		}
		
		InputStream inStream = null;
		FileOutputStream outStream = null;

		try {
			inStream = in.getStream();
			outStream = new FileOutputStream(out);
			
			copyStream(inStream, outStream);
			
			System.out.println(" downloaded to " + out.getPath());
			return true;
		} catch (IOException e) {
			System.out.println(" error copying to " + out.getPath() + ": " + e.getMessage());
		} finally {
			if (inStream != null) {
				try {
					inStream.close();
				} catch (IOException e) {
					// ignore
				}
			}
			if (outStream != null) {
				try {
					outStream.flush();
					outStream.close();
				} catch (IOException e) {
					// ignore
				}
			}
		}
		return false;
	}
	
	/**
	 * Copy bytes from the input media source to the output file.
	 * Prompt for overwrite if needed.
	 * 
	 * @param in
	 * @param out
	 * @return
	 */
	public static boolean copyStream(MediaSource in, File out) {
		if (!promptIfNeeded(out)) {
			return false;
		}
		
		InputStream inStream = null;
		FileOutputStream outStream = null;

		try {
			inStream = in.getInputStream();
			outStream = new FileOutputStream(out);
			
			copyStream(inStream, outStream);
			
			System.out.println(" downloaded to " + out.getPath());
			return true;
		} catch (IOException e) {
			System.out.println(" error copying to " + out.getPath() + ": " + e.getMessage());
		} finally {
			if (inStream != null) {
				try {
					inStream.close();
				} catch (IOException e) {
					// ignore
				}
			}
			if (outStream != null) {
				try {
					outStream.flush();
					outStream.close();
				} catch (IOException e) {
					// ignore
				}
			}
		}
		return false;
	}
};
