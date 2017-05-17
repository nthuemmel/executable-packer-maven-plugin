package de.ntcomputer.executablepacker.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;

public class JarInJarURLConnection extends URLConnection {
	private final ClassLoader outerJarClassLoader;

	public JarInJarURLConnection(URL url, ClassLoader outerJarClassLoader) {
		super(url);
		this.outerJarClassLoader = outerJarClassLoader;
	}

	@Override
	public void connect() throws IOException {
		// nothing to do
	}
	
	@Override
	public InputStream getInputStream() throws IOException {
		String filePath = URLDecoder.decode(this.url.getFile(), "UTF-8");
		InputStream result = this.outerJarClassLoader.getResourceAsStream(filePath);
		if(result==null) {
			throw new IOException("Failed to open stream to URL '" + this.url + "' in JAR file context");
		}
		return result;
	}

}