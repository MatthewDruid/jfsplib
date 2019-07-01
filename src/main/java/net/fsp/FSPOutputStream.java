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

import java.io.IOException;
import java.io.OutputStream;

/**
 * Stream for writing file to FSP server.
 *
 * You can not have more than one file opened for write per FSPsession.
 *
 * @see java.io.OutputStream
 * @author Radim Kolar
 * @since 1.0rc7
 */
public class FSPOutputStream extends OutputStream {

	private FSPsession ses;
	private byte[] fname;
	private byte[] buf = new byte [FSPpacket.SPACE];
	/** position in buffer for writing next byte */
	private int bufpos;
	/** Position of next packet sent to server */
	private long pos;

	/**
	 * Creates stream for writing file to FSP server. Because FSP protocol
	 * always uploads to temporary file no checking if client can really
	 * create/overwrite desired file is performed until end of upload.
	 * Custom timestamp on uploaded file is not supported by this class.
	 * This constructor will block if there is already another active writing
	 * stream assigned to FSPsession.
	 *
	 * @param session FSPsession to target server
	 * @param filename filename
	 * @since 1.0rc7
	 * @see FSPutil#upload(FSPsession, String, java.io.InputStream, long)
	 * @see FSPutil#canUpload(FSPsession, String)
	 */
	public FSPOutputStream(FSPsession session, String filename) {
		this.ses=session;
		this.fname=FSPutil.stringToASCIIZ(filename);
		this.bufpos = 0;
		this.pos = 0;
		session.lockWriter(this, true);
	}

	/**
	 * Flushes internal buffer to FSP server. File on FSP server is not
	 * created until stream is closed. This function always sent packet
	 * to server even if internal buffer is currently empty.
	 *
	 * @since 1.0rc7
	 */
	public void flush() throws IOException {
		FSPpacket pkt;
		pkt = ses.interact(FSPpacket.CC_UP_LOAD, pos, buf, 0, bufpos, null, 0, 0);
		pkt.expect(FSPpacket.CC_UP_LOAD);
        pos += bufpos;
        bufpos = 0;
	}

	/**
	 * Writes single byte to FSP server
	 * @see java.io.OutputStream#write(int)
	 * @param b byte to write
	 * @since 1.0rc7
	 */
	public void write(int b) throws IOException {
		if ( bufpos < FSPpacket.SPACE ) {
			buf[bufpos++] = (byte) b;
		} else {
			flush();
			write(b);
		}
	}

	/**
	 * Writes byte array to FSP server
	 * @param b array containing data to write
	 * @param off data to write offset
	 * @param len lenght of data to write
	 * @see java.io.OutputStream#write(byte[], int, int)
	 * @since 1.0rc7
	 */
	public void write(byte[] b, int off, int len) throws IOException {
		while ( len > 0) {
			int frag;
			if ( bufpos >= FSPpacket.SPACE )
				flush();
			frag = Math.min(FSPpacket.SPACE - bufpos, len);
			System.arraycopy(b, off, buf, bufpos, frag);
			len -= frag;
			off += frag;
			bufpos += frag;
		}
	}

	/**
	 * Flushes buffer and installs file.
	 *
	 * Setting filestamp on target file is not supported by this function.
	 * @since 1.0rc7
	 * @see FSPutil#upload(FSPsession, String, java.io.InputStream, long)
	 */
    public void close() throws IOException {
    	FSPpacket pkt;
    	try {
        	flush();
    		pkt = ses.interact(FSPpacket.CC_INSTALL, 0, fname, 0, fname.length, null, 0, 0);
    		pkt.expect(FSPpacket.CC_INSTALL);
    	}
    	finally {
    		/* delete buffer so we can't write to closed file */
    		ses.unlockWriter(this);
    		fname = buf = null;
    		ses = null;
    	}
    }

    /**
     * cancels pending upload and unlocks writer object.
     * This function will fail if FSPsession was finalized sooner
     *
     * @since 1.0rc8
     */
	@Deprecated
    public void finalize() throws Exception {
    	if ( fname != null ) {
    		try {
    			int tm=ses.getTimeout();
    			ses.setTimeout(7000);
    			ses.interact(FSPpacket.CC_INSTALL, 0, null, 0, 0, null, 0, 0);
    			ses.setTimeout(tm);
    		}
    		finally {
    			ses.unlockWriter(this);
    		}
    	}
    }
}
