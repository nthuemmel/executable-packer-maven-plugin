package de.ntcomputer.executablepacker.runtime;

import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;

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
