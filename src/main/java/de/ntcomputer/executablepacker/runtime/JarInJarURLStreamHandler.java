package de.ntcomputer.executablepacker.runtime;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

public class JarInJarURLStreamHandler extends URLStreamHandler {
	public static final String PROTOCOL = "jij";
	private final ClassLoader outerJarClassLoader;

	public JarInJarURLStreamHandler(ClassLoader outerJarClassLoader) {
		this.outerJarClassLoader = outerJarClassLoader;
	}

	@Override
	protected URLConnection openConnection(URL u) throws IOException {
		return new JarInJarURLConnection(u, this.outerJarClassLoader);
	}
	
	@Override
	protected void parseURL(URL u, String spec, int start, int limit) {
		// disable the default http-like URL parsing of the base class (the jij-protocol does not provide hostnames, ports, query strings, etc.)
		String filePath = spec.substring(start, limit);
		this.setURL(u, PROTOCOL, null, -1, null, null, filePath, null, null);
	}

}
