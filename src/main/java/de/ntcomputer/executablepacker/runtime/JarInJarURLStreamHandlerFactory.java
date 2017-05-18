package de.ntcomputer.executablepacker.runtime;

import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;

/**
 * Helper class that creates appropriate URL stream handlers for the custom jar-in-jar URL protocol.
 * 
 * @author Nikolaus Thuemmel
 *
 */
public class JarInJarURLStreamHandlerFactory implements URLStreamHandlerFactory {
	private final ClassLoader outerJarClassLoader;

	public JarInJarURLStreamHandlerFactory(ClassLoader outerJarClassLoader) {
		this.outerJarClassLoader = outerJarClassLoader;
	}

	public URLStreamHandler createURLStreamHandler(String protocol) {
		if(JarInJarURLStreamHandler.PROTOCOL.equals(protocol)) {
			return new JarInJarURLStreamHandler(this.outerJarClassLoader);
		} else {
			return null;
		}
	}

}
