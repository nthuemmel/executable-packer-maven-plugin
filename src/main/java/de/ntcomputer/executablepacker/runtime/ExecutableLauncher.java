package de.ntcomputer.executablepacker.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.MissingResourceException;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * Launcher that provides a custom jar-in-jar-classloader in order to load nested dependency JARs at runtime.
 * It uses information embedded in the main JAR's manifest regarding the dependency path, the list of dependencies, and the application's actual main class.
 * Once the classloader is set up, the application's static main method is invoked.
 * 
 * @author Nikolaus Thuemmel
 *
 */
public class ExecutableLauncher {
	public static final String MANIFEST_APPLICATION_MAIN_CLASS = "Application-Main-Class";
	public static final String MANIFEST_DEPENDENCY_LIBPATH = "Dependency-Libpath";
	public static final String MANIFEST_DEPENDENCY_JARS = "Dependency-Jars";

	public static void main(String[] args) throws IOException, ClassNotFoundException, NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		// get original classloader
		ClassLoader outerJarClassLoader = Thread.currentThread().getContextClassLoader();
		ClassLoader usedClassLoader = outerJarClassLoader;
		
		// parse Manifest file
		String applicationMainClassName = null;
		String dependencyLibPath = null;
		String dependencyJarFilenames = null;
		
		// Multiple Manifest files may be on the classpath. Parse all of them to find the correct one containing the required metadata
		Enumeration<URL> manifestURLs = outerJarClassLoader.getResources(JarFile.MANIFEST_NAME);
		if(manifestURLs==null || !manifestURLs.hasMoreElements()) {
			throw new MissingResourceException("Manifest file (" + JarFile.MANIFEST_NAME + ") is missing", ExecutableLauncher.class.getName(), JarFile.MANIFEST_NAME);
		}
		
		while(manifestURLs.hasMoreElements()) {
			URL manifestURL = manifestURLs.nextElement();
			try {
				InputStream manifestStream = manifestURL.openStream();
				try {
					Manifest manifest = new Manifest(manifestStream);
					Attributes manifestAttributes = manifest.getMainAttributes();
					String manifestApplicationMainClassName = manifestAttributes.getValue(MANIFEST_APPLICATION_MAIN_CLASS);
					String manifestDependencyLibPath = manifestAttributes.getValue(MANIFEST_DEPENDENCY_LIBPATH);
					String manifestDependencyJarFilenames = manifestAttributes.getValue(MANIFEST_DEPENDENCY_JARS);
					if(manifestApplicationMainClassName!=null) {
						manifestApplicationMainClassName = manifestApplicationMainClassName.trim();
						if(!manifestApplicationMainClassName.trim().isEmpty()) {
							applicationMainClassName = manifestApplicationMainClassName;
							dependencyLibPath = manifestDependencyLibPath;
							dependencyJarFilenames = manifestDependencyJarFilenames;
							break; // This is the correct manifest
						}
					}
				} finally {
					manifestStream.close();
				}
			} catch(Exception e) {
				// Ignore exceptions while parsing a single manifest file
			}
		}
		
		if(applicationMainClassName==null) {
			throw new IOException("Manifest is missing entry " + MANIFEST_APPLICATION_MAIN_CLASS); // No manifest contained the required metadata
		}
		
		if(dependencyLibPath==null) {
			dependencyLibPath = "";
		} else {
			dependencyLibPath = dependencyLibPath.trim();
		}
		
		// parse and apply dependency JAR paths
		if(dependencyJarFilenames!=null && !dependencyJarFilenames.isEmpty()) {
			URL.setURLStreamHandlerFactory(new JarInJarURLStreamHandlerFactory(outerJarClassLoader)); // necessary to handle the custom jar-in-jar URL protocol
			
			// split list of dependency JARs
			String[] dependencyJarFilenameArray = dependencyJarFilenames.split("/");
			List<URL> dependencyJarURLs = new ArrayList<URL>(dependencyJarFilenameArray.length);
			
			// add the root of the outer JAR as well (so classes included in the JAR directly, not jar-in-jar, can be found as well without having to use a parent classloader - see reasons below)
			// add this first in order to take precedence over dependencies
			dependencyJarURLs.add(new URL(JarInJarURLStreamHandler.PROTOCOL + ":./"));
			
			// build URL with custom jar-in-jar protocol for each dependency
			for(String dependencyJarFilename: dependencyJarFilenameArray) {
				dependencyJarFilename = dependencyJarFilename.trim();
				if(!dependencyJarFilename.isEmpty()) {
					URL dependencyJarUrl = new URL("jar:" + JarInJarURLStreamHandler.PROTOCOL + ":" + dependencyLibPath + dependencyJarFilename + "!/");
					dependencyJarURLs.add(dependencyJarUrl);
				}
			}
			
			// apply dependencies
			if(dependencyJarURLs.size() > 1) { // > 1 to account for the always-added root URL
				// replace the main thread's classloader if at least one dependency was found
				URL[] dependencyJarURLArray = dependencyJarURLs.toArray(new URL[dependencyJarURLs.size()]);
				
				URLClassLoader jarInJarClassLoader = new URLClassLoader(dependencyJarURLArray, null);
				// do NOT use outerJarClassLoader as parent!
				// If it is used, the main class (see below) would be found by the parent class loader,
				// and the defined main class would only be able to use the parent class loader (which found the class), not the dependency loader.
				
				usedClassLoader = jarInJarClassLoader;
				Thread.currentThread().setContextClassLoader(jarInJarClassLoader);
			}
		}
		
		// launch the actual application
		Class<?> applicationMainClass = Class.forName(applicationMainClassName, true, usedClassLoader);
		Method applicationMainMethod = applicationMainClass.getMethod("main", String[].class);
		applicationMainMethod.invoke(null, (Object) args);
	}

}
