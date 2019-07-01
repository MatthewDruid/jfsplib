/*
Copyright (c) 2003-2009 by Radim 'HSN' Kolar (hsn@sendmail.cz)

You may copy or modify this file in any manner you wish, provided
that this notice is always included, and that you hold the author
harmless for any loss or damage resulting from the installation or
use of this software.

		     This is a free software.  Be creative.
		    Let me know of any bugs and suggestions.
 */
package net.fsp;
import java.net.URLStreamHandlerFactory;
import java.net.URLStreamHandler;
import java.net.URLConnection;
import java.net.URL;

/**
 * URLStreamHandler/Factory for FSP protocol.
 *
 * @author Radim Kolar
 * @version 1.0rc8
 * @since 1.0
 */
public class Handler extends URLStreamHandler implements URLStreamHandlerFactory
{
	/* factory interface */

	/**  creates URLStreamHandler instance for FSP protocol.
	 * <p>
	 * @param protocol requested protocol. Only FSP is supported.
	 * @return  FSPStreamHandler instance or null if protocol is not FSP.
	 * @since 1.0
	 */
	public  URLStreamHandler createURLStreamHandler(String protocol)
	{
		if(protocol==null) return null;
		if(protocol.equals("fsp"))
			return new Handler();
		return null;
	}

	/* Stream handler methods */

	/**
	 * Check if two FSP URLs are Equal.
	 * Only host, port and path URL components are compared.
	 *
	 * @param u1 first FSP url to compare
	 * @param u2 second FSP url to compare
	 * @return true if both url are equals
	 */
	protected boolean equals(URL u1, URL u2) {
		if ( u1.getHost().equals(u2.getHost()) ) {
			/* compare port numbers */
			int p1,p2;
			p1=u1.getPort();
			p2=u2.getPort();
			/* normalize port numbers */
			p1 = (p1 == -1) ? 21: p1;
			p2 = (p2 == -1) ? 21: p2;
			if ( p1 == p2 )
				if ( u1.getPath().equals(u2.getPath()) )
					return true;
		}
		return false;
	}

	/**
	 * Get default port in none is specified in URL.
	 * Default port for FSP service is 21.
	 * @return 21
	 * @since 1.0rc8
	 */
	protected int getDefaultPort() {
		return 21;
	}

	/**
	 * @since 1.0rc8
	 */
	protected int hashCode(URL u) {
		return toExternalForm(u).hashCode();
	}

	/**
	 * opens a FSP connection.
	 *
	 * @since 1.0rc8
	 * @param u URL to be opened
	 * @return FSPURLConnection
	 * @see FSPURLConnection
	 */
	protected URLConnection openConnection(URL u)
	{
		return new FSPURLConnection(u);
	}

	/**
	 * Converts FSP protocol URL to String
	 * @since 1.0rc8
	 * @param u URL to be converted
	 * @return String representation of URL
	 */
	protected String toExternalForm(URL u) {
		int p=u.getPort();
		if ( p == 21 )
			p = -1;
		return "fsp://"+u.getHost()+ (p != -1 ? ":"+p : "")+u.getPath();
	}
}
