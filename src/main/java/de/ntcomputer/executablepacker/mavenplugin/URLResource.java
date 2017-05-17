package de.ntcomputer.executablepacker.mavenplugin;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;

import org.codehaus.plexus.components.io.resources.PlexusIoURLResource;

/**
 * Simple URL resource class compatible with maven archiver resource types.
 * Designed to be used by URLs referencing local class files.
 * 
 * @author Nikolaus Thuemmel
 *
 */
public class URLResource extends PlexusIoURLResource {
	private final URL url;
	
	/**
	 * Creates a new URL resource for the given URL.
	 * The URL must point to an existing and valid file.
	 * A connection will be opened to that URL to query lastModified and content size metadata.
	 * 
	 * @param url
	 * @return the created resource
	 * @throws IOException if querying metadata fails
	 */
	public static URLResource create(URL url) throws IOException {
		try {
			URLConnection connection = url.openConnection();
			
			long lastModified = connection.getLastModified();
			if(lastModified==0) {
				throw new IOException("lastModified is unknown");
			}
			
			long size = connection.getContentLengthLong();
			if(size<0) {
				throw new IOException("content size is unknown");
			}
			
			return new URLResource(url, lastModified, size);
			
		} catch(Exception e) {
			throw new IOException("Failed to query metadata for URL '" + url + "'", e);
		}
	}
	
	private URLResource(URL url, long lastModified, long size) {
		super(url.toString(), lastModified, size, true, false, true);
		this.url = url;
	}

	@Override
	public URL getURL() throws IOException {
		return this.url;
	}

}
