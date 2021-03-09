package i5.las2peer.classLoaders.libraries;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import i5.las2peer.classLoaders.LibraryNotFoundException;
import i5.las2peer.logging.L2pLogger;
import i5.las2peer.tools.SimpleTools;

/**
 * implements a repository which loads all libraries from a given directory or from severeal ones. The search for
 * library files (jars) may be recursive.
 *
 */
public class FileSystemRepository implements Repository {

	private static final L2pLogger logger = L2pLogger.getInstance(FileSystemRepository.class);

	private Iterable<String> directories;
	private boolean recursive = false;
	private Hashtable<String, Hashtable<LibraryVersion, String>> htFoundJars;
	private long lastModified = 0;

	/**
	 * create a repository for the given directory, non-recursive
	 *
	 * @param directory A directory path name to use as repository
	 */
	public FileSystemRepository(String directory) {
		this(new String[] { directory }, false);
	}

	/**
	 * create a repository for the given directory
	 *
	 * @param directory A directory path name to use as repository
	 * @param recursive If true, recursion is used for sub-directories.
	 */
	public FileSystemRepository(String directory, boolean recursive) {
		this(new String[] { directory }, recursive);
	}

	/**
	 * create a repository for the given directories, non-recursive
	 *
	 * @param directories An array of directory path names to use as repository
	 */
	public FileSystemRepository(String[] directories) {
		this(directories, false);
	}

	/**
	 * create a repository for the given directories
	 *
	 * @param directories An array of directory path names to use as repository
	 * @param recursive If true, recursion is used for sub-directories.
	 */
	public FileSystemRepository(String[] directories, boolean recursive) {
		this(Arrays.asList(directories), recursive);
	}

	/**
	 * create a repository for the given directories
	 *
	 * @param directories A bunch of directory path names to use as repository
	 * @param recursive If true, recursion is used for sub-directories.
	 */
	public FileSystemRepository(Iterable<String> directories, boolean recursive) {
		this.directories = directories;
		this.recursive = recursive;

		updateRepository(true);
	}

	/**
	 * get the newest library for the given name
	 *
	 * @param name A library name to search for
	 * @return Returns a LoadedLibrary for the requested library name
	 * @throws LibraryNotFoundException If the library could not be found in this repository
	 */
	@Override
	public LoadedLibrary findLibrary(String name) throws LibraryNotFoundException {
		updateRepository(false);

		Hashtable<LibraryVersion, String> htVersions = htFoundJars.get(name);
		if (htVersions == null) {
			System.err.println(this + " could not find " + name);
			throw new LibraryNotFoundException(
					"library '" + name + "' package could not be found in the repositories!");
		} else {
			System.err.println(this + " has " + htVersions.size() + " versions of " + name);
		}

		LibraryVersion version = null;
		for (Enumeration<LibraryVersion> en = htVersions.keys(); en.hasMoreElements();) {
			LibraryVersion v = en.nextElement();
			if (version == null) {
				version = v;
			}
		}

		try {
			return LoadedJarLibrary.createFromJar(htVersions.get(version));
		} catch (Exception e) {
			throw new LibraryNotFoundException("Error opening library jar " + htVersions.get(version), e);
		}
	}

	/**
	 * get a library matching name and version of the given identifier
	 *
	 * @param lib A library identifier
	 * @return Returns a LoadedLibrary for the requested library identifier
	 * @throws LibraryNotFoundException If the library could not be found in this repository
	 */
	@Override
	public LoadedLibrary findLibrary(LibraryIdentifier lib) throws LibraryNotFoundException {
		updateRepository(false);

		Hashtable<LibraryVersion, String> htVersions = htFoundJars.get(lib.getName());
		if (htVersions == null) {
			throw new LibraryNotFoundException(
					"library '" + lib.toString() + "' package could not be found in the repositories!");
		}

		String jar = htVersions.get(lib.getVersion());

		if (jar == null) {
			throw new LibraryNotFoundException(
					"library '" + lib.toString() + "' package could not be found in the repositories!");
		}

		try {
			return LoadedJarLibrary.createFromJar(jar);
		} catch (Exception e) {
			throw new LibraryNotFoundException(
					"library '" + lib.toString() + "' package could not be found in the repositories!", e);
		}
	}

	/**
	 * get an array with all versions found for the given library name
	 *
	 * @param libraryName A canonical library name
	 * @return array with all available versions of the given library
	 */
	public String[] getAvailableVersions(String libraryName) {
		return getAvailableVersionSet(libraryName).toArray(new String[0]);
	}

	/**
	 * get a collection with all versions found for the given library name
	 *
	 * @param libraryName A canonical library name
	 * @return a collections with all versions of the given library
	 */
	public Collection<LibraryVersion> getAvailableVersionSet(String libraryName) {
		Hashtable<LibraryVersion, String> htFound = htFoundJars.get(libraryName);
		if (htFound == null) {
			return new HashSet<>();
		}

		return htFound.keySet();
	}

	/**
	 * get an array with found jar files within this repository
	 *
	 * @return an array with all libraries in this repository
	 */
	public String[] getAllLibraries() {
		Collection<String> libs = getLibraryCollection();
		return libs.toArray(new String[0]);
	}

	/**
	 * get a collection with all found jar files within this repository
	 *
	 * @return a collection with all libraries in this repository
	 */
	public Collection<String> getLibraryCollection() {
		HashSet<String> hsTemp = new HashSet<>();

		Enumeration<String> eLibs = htFoundJars.keys();
		while (eLibs.hasMoreElements()) {
			String lib = eLibs.nextElement();
			Iterator<String> jars = htFoundJars.get(lib).values().iterator();
			while (jars.hasNext()) {
				String jar = jars.next();
				hsTemp.add(jar);
			}
		}

		return hsTemp;
	}

	/**
	 * helper method to get the last modification date of a directory
	 *
	 * @param dir A directory
	 * @param recursive If true also files inside the directory are considered
	 * @return Returns the last modified date in epoch format
	 */
	public static long getLastModified(File dir, boolean recursive) {
		File[] files = dir.listFiles();
		if (files == null || files.length == 0) {
			return dir.lastModified();
		}

		long lastModified = 0;
		for (File f : files) {
			if (f.isDirectory() && recursive) {
				long ll = getLastModified(f, recursive);
				if (lastModified < ll) {
					lastModified = ll;
				}
			} else {
				if (lastModified < f.lastModified()) {
					lastModified = f.lastModified();
				}
			}
		}
		return lastModified;
	}

	/**
	 * checks if there were changes made in the folder and re-reads repositories if so
	 *
	 * @param force if true, the repository will be updated independent from last modification
	 */
	private void updateRepository(boolean force) {
		long currentLastModified = 0;
		for (String directory : directories) {
			long ll = getLastModified(new File(directory), recursive);
			if (currentLastModified < ll) {
				currentLastModified = ll;
			}
		}

		if (lastModified < currentLastModified || force) {
			lastModified = currentLastModified;
			initJarList();
		}
	}

	/**
	 * initialize the list if jars
	 */
	private void initJarList() {
		htFoundJars = new Hashtable<>();

		for (String directory : directories) {
			searchJars(directory);
		}
	}

	/**
	 * look for jars in the given directory, search recursive, if flag is set
	 *
	 * @param directory
	 */
	private void searchJars(String directory) {
		File f = new File(directory);

		if (f.exists()) {
			if (!f.isDirectory()) {
				// since this is a search function, stay friendly and don't throw an exception
				logger.log(Level.WARNING, "Given path is not a directory: " + f.toString());
				return;
			}
		} else {
			logger.log(Level.FINE, "Given path does not exist: " + f.toString());
			return;
		}

		File[] entries = f.listFiles();

		Pattern versionPattern = Pattern.compile("-(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-((?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\\.(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?(?:\\+([0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)*))?$");

		for (File entry : entries) {
			if (entry.isDirectory()) {
				if (recursive) {
					searchJars(entry.toString());
				}
			} else if (entry.getPath().endsWith(".jar")) {
				String file = entry.getName().substring(0, entry.getName().length() - 4);
				Matcher m = versionPattern.matcher(file);

				if (m.find()) {
					try {
						String name = file.substring(0, m.start());
						LibraryVersion version = new LibraryVersion(m.group().substring(1));
						registerJar(entry.getPath(), name, version);
					} catch (IllegalArgumentException e) {
						System.out.println(
								"Notice: library " + entry + " has no version info in it's name! - Won't be used!");
					}
				} else {
					System.out.println(
							"Notice: library " + entry + " has no version info in it's name! - Won't be used!");
				}
			}
		}
	}

	/**
	 * register a found jar file to the hashtable of available jars in this repository
	 *
	 * @param file
	 * @param name
	 * @param version
	 */
	private void registerJar(String file, String name, LibraryVersion version) {
		Hashtable<LibraryVersion, String> htNameEntries = htFoundJars.get(name);
		if (htNameEntries == null) {
			htNameEntries = new Hashtable<>();
			htFoundJars.put(name, htNameEntries);
		}
		htNameEntries.put(version, file);
	}

	/**
	 * @return a simple string representation of this object
	 */
	@Override
	public String toString() {
		return "FS-Repository at " + SimpleTools.join(directories, ":");
	}

}
