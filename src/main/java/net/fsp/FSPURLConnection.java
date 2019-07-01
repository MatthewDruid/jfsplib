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
import java.net.SocketPermission;
import  java.net.URL;
import java.io.FileNotFoundException;
import  java.io.IOException;
import java.io.OutputStream;
import java.security.Permission;
import java.util.ArrayList;
import  java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * class for operations on FSP URL.
 *
 * @version 1.0rc7
 * @since 1.0
 * @author Radim Kolar
 */
public class FSPURLConnection extends java.net.URLConnection
{
	/* FSP session */
	private FSPsession ses;
	private FSPstat stat;

	private static String header_fields[] = { "last-modified", "content-length" };

	/**
	 * creates a new URL connection with FSP protocol.
	 *
	 * @since 1.0
	 */
	protected FSPURLConnection(URL u)
	{
		super(u);
		allowUserInteraction=false;
		useCaches=false;
	}

	/**
	 * gets file status from FSP server
	 *
	 * @since 1.0
	 */
	public void connect() throws IOException
	{
		if(connected) return;
		if ( doInput && doOutput )
			throw new UnsupportedOperationException("FSP supports reading OR writing files");
		if ( !doInput && !doOutput )
			throw new IllegalStateException("You must call setDoInput() or setDoOutput() before calling connect()");
		ses=new FSPsession(url.getHost(),url.getPort());
		ses.setTimeout(getConnectTimeout());
		stat=FSPutil.stat(ses,url.getFile());
		connected=true;
		ses.setTimeout(getReadTimeout());
		return;
	}

	/**
	 * Get connection timeout.
	 *
	 * @return FSP session timeout (ms)
	 * @since 1.0rc8
	 */
	public int getConnectTimeout() {
		return ses.getTimeout();
	}

	/**
	 * FSP do not have concept of transfer encodings. All files are transfered
	 * in 8bit binary. This encoding is usually called "identity". Because this
	 * encoding should not be used in Content-Encoding http/1.1 reply header we
	 * return null here.
	 *
	 * @return null
	 * @since 1.0rc8
	 */
	public String getContentEncoding() {
		return null;
	}

	/**
	 * Get content-length.
	 * FSP v2 protocol support files up to 4 GB long.
	 * This function is not 64-bit LFS safe, it will return negative number
	 * for files 2-4GB long.
	 *
	 * @return file size in bytes
	 * @since 1.0rc8
	 */
	public int getContentLength() {
		if (doOutput || !connected || stat == null)
			return -1;
		else
			return (int)stat.length;
	}

	/**
	 * Get content type.
	 * FSP v2 protocol does not supports content-type.
	 *
	 * @return null - content type not known
	 * @since 1.0rc8
	 */
	public String getContentType() {
		return null;
	}

	/**
	 * Get date of sending server.
	 * FSP protocol does not support it.
	 * @return 0
	 * @since 1.0rc8
	 */
	public long getDate() {
		return 0;
	}
    /**
     * Returns default value for allowUserInteraction field.
     * Because there is no password support, default value is false.
     *
     * @return false
     * @since 1.0rc8
     */
	public static boolean getDefaultAllowUserInteraction() {
		return false;
	}

	/**
	 * Returns default value for useCaches. We don't support caches.
	 *
	 * @since 1.0rc8
	 * @return false
	 */
	public boolean getDefaultUseCaches() {
		return false;
	}

	/**
	 * Get value of Expires: header. FSP protocol does not support headers.
	 * @return 0
	 * @since 1.0rc8
	 */
	public long getExpiration() {
		return 0;
	}

	/**
	 * Returns value of nth header field.
	 *
	 * @param n field index, starting at zero
	 * @return null if there are fewer than n+1 fields
	 * @since 1.0rc8
	 */
	public String getHeaderField(int n) {
		if ( n >= header_fields.length )
			return null;
		else
			if ( n < 0 )
				throw new IllegalArgumentException("header field number must be positive");
			else
				return getHeaderField(header_fields[n]);
	}

	/**
	 * Returns value of specified header field.
	 * <p>
	 * Only "last-modified" and "content-length" are supported.
	 *
	 * @param name header field name in lower case
	 * @return header field value or null if no such header is found
	 * @since 1.0
	 */
	public String getHeaderField(String name)
	{
		if(stat == null ) return null;
		if(name.equals("last-modified"))
		{
			return new Date(stat.lastmod).toString();
		}
		if(name.equals("content-length"))
		{
			return Long.toString(stat.length);
		}
		return null;
	}

	/**
	 * Return value of header field parsed as date.
	 *
	 * @param name field name. Only "last-modified" is supported
	 * @param Default default value returned if no such header exists
	 * @return header value in standard Java time units (ms)
	 * @since 1.0rc8
	 */
	public long getHeaderFieldDate(String name, long Default) {
		if ( stat == null || !name.equals("last-modified"))
			return Default;
		else
			return stat.lastmod;
	}

	/**
	 * Return value of header field parsed as integer.
	 *
	 * @param name field name. only "content-length" is supported
	 * @param Default default value returned if no such header exists
	 * @return integer value of header
	 * @since 1.0rc8
	 */
	public int getHeaderFieldInt(String name, int Default) {
		if ( stat == null || !name.equals("content-length") )
			return Default;
		else
			return (int)stat.length;
	}

	/**
	 * Return field (header) name for nth header field.
	 * @param n field index, starting at zero
	 * @return header field name or null if there are fewer than n+1 fields
	 * @since 1.0rc8
	 */
	public String getHeaderFieldKey(int n) {
		if ( n < 0 )
			throw new IllegalArgumentException("field number must be >=0");
		if ( n >= header_fields.length)
			return null;
		else
			return header_fields[n];
	}

	/**
	 * Return unmodifiable Map of header fields.
	 * @return Map<String, List<String>>
	 * @since 1.0rc8
	 */
	public Map<String, List<String>> getHeaderFields() {
		if ( stat == null )
			return null;
		HashMap<String, List<String>> rc = new HashMap<String, List<String>>(2, 1.0f);
		List<String> l;
		l = new ArrayList<String>();
		l.add(Long.toString(stat.length));
		rc.put("content-length", l);
		l = new ArrayList<String>();
		l.add(new Date(stat.lastmod).toString());
		rc.put("last-modified", l);
		return rc;
	}

	/**
	 * returns input stream of file from FSPserver.
	 *
	 * @since 1.0
	 */
	public java.io.InputStream getInputStream() throws IOException
	{
		if(!connected) throw new IllegalStateException("Not connected");
		if(!doInput) throw new IllegalStateException("URL not opened for reading");
		if(stat==null) throw new FileNotFoundException("File not found");
		if(stat.type==FSPstat.RDTYPE_DIR)
			throw new UnsupportedOperationException("Is a directory");

		return new FSPInputStream(ses,url.getFile());
	}

	/**
	 * Return last-modified value for file or 0 if not known
	 * @since 1.0rc8
	 */
	public long getLastModified() {
		if ( stat != null )
			return stat.lastmod;
		else
			return 0;
	}

	/**
	 * Returns an output stream that writes to this URL
	 * @since 1.0rc8
	 */
	public OutputStream getOutputStream() {
		if (!connected) throw new IllegalStateException("Not connected");
		if (!doOutput) throw new IllegalStateException("URL not opened for writing");
		return new FSPOutputStream(ses, url.getFile());
	}

	/**
	 * Permission needed for creating connection to this URL
	 * @since 1.0rc8
	 */
	public Permission getPermission() {
		return new SocketPermission(url.getHost()+":"+ses.getPort(), "connect,resolve");
	}

	/**
	 * Get value of useCaches property
	 * @return false. Cache is not supported
	 * @since 1.0rc8
	 */
	public boolean getUseCaches() {
		return false;
	}
}
