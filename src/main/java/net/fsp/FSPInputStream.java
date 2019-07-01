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

/** This class allows reading file from FSP server.
 *
 * @author Radim Kolar
 * @see java.io.InputStream
 * @version 1.0rc7
 * @since 1.0
 */
public class FSPInputStream extends java.io.InputStream {

	private long mark=0;
	private long pos=0; // position of next packet

	private byte buf[]=new byte[FSPpacket.SPACE];
	private int bufpos=FSPpacket.SPACE; // no data in buffer
	private boolean eof=false;

	private FSPsession ses;
	private byte[] fname;

	/** creates a new Input stream for reading file from FSP server
	 *
	 *  @param session open session to target server
	 *  @param filename filename for download
	 * */
	public FSPInputStream (FSPsession session,String filename)
	{
		this.ses=session;
		fname=FSPutil.stringToASCIIZ(filename);
	}

	/** returns the number of bytes that can be read from this input stream
	 * without contacting server. */
	public int available()
	{
		return FSPpacket.SPACE-bufpos;
	}

	/** frees internal buffers.
	 * FSPsession is not closed.
	 */
	public void close()
	{
		eof=true;
		buf=null;
		ses=null;
		bufpos=FSPpacket.SPACE;
	}

	/** marks current position in the stream.
	 * @param readlimit ignored */
	public synchronized void mark(int readlimit)
	{
		mark=pos-(FSPpacket.SPACE-bufpos);
	}

	/**  Repositions this stream to the position at the time the mark method was last called on this input stream.
	 * <p>
	 * If no mark method was called, rewind stream to zero.
	 */
	public synchronized void reset()
	{
		bufpos=FSPpacket.SPACE;
		pos=mark;
	}

	/** returns true - mark is supported. */
	public boolean markSupported()
	{
		return true;
	}

	/** reads next byte from stream or -1 if EOF */
	public int read() throws IOException
	{
		if(eof==true) return -1;

		if(bufpos<FSPpacket.SPACE)
		{
			return buf[bufpos++] & 0xFF;
		}

		fillbuffer();
		return read();
	}

	/** reads one FSP packet and fills internal buffer */
	private void fillbuffer() throws IOException
	{
		FSPpacket pkt;

		pkt=ses.interact(FSPpacket.CC_GET_FILE,pos,fname,0,fname.length,null,0,0);
		pkt.expect(FSPpacket.CC_GET_FILE);
		if(pkt.bb_len==0) {
			eof=true;
		}
		bufpos=FSPpacket.SPACE-pkt.bb_len;
		pos+=pkt.bb_len;
		System.arraycopy(pkt.buf,0,buf,bufpos,pkt.bb_len);
	}

	/** reads data from FSP stream */
	public int read(byte b[],int off,int len) throws IOException
	{
		if(eof==true) return -1;
		if(len<=0) return 0;
		if(bufpos>=FSPpacket.SPACE)
			fillbuffer();

		int read=Math.min(len,FSPpacket.SPACE-bufpos);
		System.arraycopy(buf,bufpos,b,off,read);
		bufpos+=read;
		return read;
	}

	/** skips n bytes in input stream.
	 *
	 * @since 1.0
	 *
	 * @param bytes number of bytes to be skipped
	 * @return 0 on EOF or bytes
	 */
	public long skip(long bytes) throws IOException
	{
		if(eof==true) return 0;
		pos=pos-(FSPpacket.SPACE-bufpos)+bytes;
		bufpos=FSPpacket.SPACE;
		return bytes;
	}

}
