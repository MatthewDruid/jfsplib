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
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.util.Vector;

/** Utilities for easier working with FSP.
 * <p>
 * There are some higher level function for working with FSP.
 *
 * @author Radim Kolar
 * @version 1.0rc7
 * @since 1.0
 */
public class FSPutil
{
	/** Downloads a file from FSP server.
	 * <p>
	 * This procedure download a file from FSP server, file is written to
	 * OutputStream.  OutputStream is not closed at end of transfer.
	 *
	 * @since 1.0
	 * @param session active FSP session
	 * @param filename filename on FSP server
	 * @param os write file to this stream
	 * @param start_from offset where to start download
	 * @param byteswanted how many bytes to download, &lt; 0 for all
	 * @throws IOException if i/o or net error ocured during file transfer
	 */
	public static void download(FSPsession session,String filename,OutputStream os,long start_from,long byteswanted) throws IOException
	{
		FSPpacket pkt;
		byte fname[]=stringToASCIIZ(filename);
		while(true)
		{
			pkt=session.interact(FSPpacket.CC_GET_FILE,start_from,fname,0,fname.length,null,0,0);
			pkt.expect(FSPpacket.CC_GET_FILE);
			if(pkt.bb_len==0) return;
			if(
					(pkt.bb_len > byteswanted) &&
					( byteswanted >= 0 )
			)
			{
				pkt.bb_len = (short) byteswanted;
			}
			os.write(pkt.buf,0,pkt.bb_len);
			start_from+=pkt.bb_len;
			byteswanted-=pkt.bb_len;
			if(byteswanted == 0 ) return;
		}
	}

	/**
	 * Uploads file to FSP server
	 * <p>
	 * @param session opened FSPsession to target server
	 * @param filename filename on remote server
	 * @param is InputStream to be sent to server. Stream is not closed at
	 *        end of operation
	 * @param timestamp timestamp for uploaded file, 0 if not used. Timestamp
	 *                  in in standard Java format (milliseconds)
	 * @throws IOException if i/o or net error ocured during file transfer
	 * @since 1.0rc7
	 */
	public static void upload(FSPsession session, String filename, InputStream is, long timestamp) throws IOException {
		byte[] buf = new byte [FSPpacket.SPACE];
		FSPpacket pkt;
		int br;
		byte[] fname = stringToASCIIZ(filename);
		long pos=0;
		boolean first=true;

		while( (br = is.read(buf)) > 0 || first ) {
			first = false;
			pkt = session.interact(FSPpacket.CC_UP_LOAD, pos, buf, 0, br, null, 0, 0);
			pkt.expect(FSPpacket.CC_UP_LOAD);
			pos += br;
		}
		if (timestamp != 0 ) {
			buf = new byte[4];
			timestamp /= 1000L;
			buf[0] =(byte)((timestamp >>>24) & 0xFF);
			buf[1] =(byte)((timestamp >>>16) & 0xFF);
			buf[2]=(byte)((timestamp  >>> 8) & 0xFF);
			buf[3]=(byte)(timestamp & 0xFF);
		}
		else
			buf = new byte[0];

		pkt = session.interact(FSPpacket.CC_INSTALL, buf.length, fname, 0, fname.length, buf, 0, buf.length);
		pkt.expect(FSPpacket.CC_INSTALL);
	}

	/** Gets information about file or directory.
	 * <p>
	 * This function requests information about specific path from FSP server.
	 * Server must support CC_STAT command, which is supported from FSP 2.8.1
	 * Beta 11.
	 *
	 * @since 1.0
	 * @param session FSPsession
	 * @param path path for getting information
	 * @return FSPstat object or null if path is not found
	 * @throws IOException if server is not responding or do not supports
	 *         CC_STAT command
	 * @see FSPstat
	 * @see #statSupported(FSPsession)
	 */
	public static FSPstat stat(FSPsession session,String path) throws IOException
	{
		byte fname[]=stringToASCIIZ(path);
		FSPpacket pkt;

		pkt=session.interact(FSPpacket.CC_STAT,0,fname,0,fname.length,null,0,0);
		pkt.expect(FSPpacket.CC_STAT);
		if(pkt.buf[8]==0) return null; /* does not exists */
		FSPstat stat=new FSPstat();
		stat.name=path;
		stat.lastmod=( (pkt.buf[0]<<8) | (pkt.buf[1] & 0xFF)) <<16;
		stat.lastmod|= ((pkt.buf[2] & 0xFF)<<8) | (pkt.buf[3] & 0xFF);
		stat.lastmod&=0xffffffffL;
		stat.lastmod*=1000L;
		stat.length  =((pkt.buf[4] << 8) | (pkt.buf[5] & 0xFF)) << 16;
		stat.length |=((pkt.buf[6] & 0xFF)<< 8) | (pkt.buf[7] & 0xFF);
		stat.length &= 0xffffffffL;
		stat.type = pkt.buf[8];
		return stat;
	}

	/** Check if FSP server supports CC_STAT command. This command is implemented
	 * in fspd from FSP suite version 2.8.1 Beta 11 or later.
	 * <p>
	 * This function is also suitable for detecting if FSP server is still
	 * alive. If you dont get IOException during function call then you
	 * are still connected to FSP server.
	 * @param session FSPsession
	 * @since 1.0
	 * @return true if CC_STAT command is supported
	 * @throws IOException if server can not be contacted
	 */
	public static boolean statSupported(FSPsession session) throws IOException
	{
		byte fname[]=stringToASCIIZ("/");
		FSPpacket pkt;

		pkt=session.interact(FSPpacket.CC_STAT,0,fname,0,fname.length,null,0,0);
		if(pkt.cmd!=FSPpacket.CC_STAT)
			return false;
		else
			return true;
	}

	/** Converts String to ASCIIZ byte array.
	 * @since 1.0
	 * @param filename string to be converted
	 * @return converted byte array NULL terminated
	 */
	public static byte[] stringToASCIIZ(String filename)
	{
		if(filename==null) return new byte[1];
		byte fname[]=new byte[filename.length()+1];
		System.arraycopy(filename.getBytes(),0,fname,0,filename.length());
		fname[fname.length-1]=0;
		return fname;
	}

	/** get a filename directory list from server.
	 *
	 * @since 1.0
	 * @param session live FSPsession
	 * @param directory directory to be listed
	 * @return file list
	 * @throws IOException if network failure ocurs
	 */
	public static String[] list(FSPsession session,String directory) throws IOException
	{
		byte fname[]=stringToASCIIZ(directory);
		Vector<String> dirlist=new Vector<String>(20);
		FSPpacket pkt;
		int pos=0;
		int i,j;

		dirlister:while(true)
		{
            pkt=session.interact(FSPpacket.CC_GET_DIR,pos,fname,0,fname.length,null,0,0);
            pkt.expect(FSPpacket.CC_GET_DIR);
			if(pkt.bb_len==0) break;
			pos+=pkt.bb_len;
			i=0;
			nextentry:while(i<pkt.bb_len-9)
			{
				/* check entry type */
				switch(pkt.buf[i+8])
				{
				case 0x2A:
					/* RDTYPE_SKIP */
					break nextentry;
				case 0x00:
					break dirlister;
				}
				i+=9;
				/* read ASCIIZ fname */
				j=i;
				while(pkt.buf[j]!=0)
					j++;
				dirlist.addElement(new String(pkt.buf,i,j-i));
				i=j+1;
				/* move to next 4byte boundary */
				while((i & 0x3)>0)
					i++;
			}
		}
		/* convert Vector to array */
		String list[]=new String[dirlist.size()];
		for(i=0;i<dirlist.size();i++)
		{
			list[i]=(String)dirlist.elementAt(i);
		}
		return list;

	}

	/** get a FSPstat directory list from server.
	 *
	 * @since 1.0
	 * @param session live FSPsession
	 * @param directory directory to be listed
	 * @return FSPstat list or null on error
	 * @throws IOException if network error ocurs
	 * @see FSPstat
	 */
	public static FSPstat[] statlist(FSPsession session,String directory) throws IOException
	{
		byte fname[]=stringToASCIIZ(directory);
		Vector<FSPstat> dirlist=new Vector<FSPstat>(20);
		FSPpacket pkt;
		FSPstat stat;
		int pos=0;
		int i,j;

		dirlister:while(true)
		{
			pkt=session.interact(FSPpacket.CC_GET_DIR,pos,fname,0,fname.length,null,0,0);
			pkt.expect(FSPpacket.CC_GET_DIR);
			if(pkt.bb_len==0) break;
			pos+=pkt.bb_len;
			i=0;
			nextentry:while(i<pkt.bb_len-9)
			{
				/* check entry type */
				switch(pkt.buf[i+8])
				{
				case 0x2A:
					/* RDTYPE_SKIP */
					break nextentry;
				case 0x00:
					/* END OF LIST */
					break dirlister;
				}
				/* create a new stat object */
				stat=new FSPstat();
				/* extract date */
				stat.lastmod =((pkt.buf[i] << 8) | (pkt.buf[i+1] & 0xFF)) << 16;
				i+=2;
				stat.lastmod|=((pkt.buf[i] & 0xFF)<< 8) | (pkt.buf[i+1] & 0xFF);
				i+=2;
				stat.lastmod &=0xffffffffL;
				stat.lastmod*=1000L;
				/* extract size */
				stat.length  =((pkt.buf[i] << 8) | (pkt.buf[i+1] & 0xFF)) << 16;
				i+=2;
				stat.length |=((pkt.buf[i] & 0xFF)<< 8) | (pkt.buf[i+1] & 0xFF);
				i+=2;
				/* extract type */
				stat.type=pkt.buf[i++];
				/* read ASCIIZ fname */
				j=i;
				while(pkt.buf[j]!=0)
					j++;
				stat.name=new String(pkt.buf,i,j-i);
				dirlist.addElement(stat);
				i=j+1;
				/* move to next 4byte boundary */
				while((i & 0x3)>0)
					i++;
			}
		}

		/* convert Vector to array */
		FSPstat list[]=new FSPstat[dirlist.size()];
		for(i=0;i<dirlist.size();i++)
		{
			list[i]=(FSPstat)dirlist.elementAt(i);
		}
		return list;
	}

	/**
	 * Sends CC_VERSION command to server and parses reply.
	 *
	 * Because some servers do not reply to CC_VERSION command, it
	 * should not be used for detection if FSPsession is still alive.
	 * Better is to use {@link FSPutil#statSupported(FSPsession) statSupported}
	 * function instead.
	 *
	 * @since 1.0rc7
	 * @param session live FSPsession
	 * @return FSPversion object
	 * @throws IOException
	 * @see FSPutil#statSupported(FSPsession)
	 */
	public static FSPversion version(FSPsession session) throws IOException
	{
		FSPversion ver;
		FSPpacket pkt;

		pkt = session.interact(FSPpacket.CC_VERSION,0L,null,0,0,null,0,0);
		pkt.expect(FSPpacket.CC_VERSION);
		ver = new FSPversion(pkt);
		return ver;
	}

	/**
	 * Checks if user have enough rights to upload given file.
	 * @param session opened FSPsession
	 * @param filename file to be uploaded
	 * @return true if user can upload file
	 * @since 1.0rc8
	 */
	public static boolean canUpload(FSPsession session, String filename) throws IOException
	{
		String dirname;
		int n;
		FSPprotection pro;
		FSPpacket pkt;
		byte[] buf;

		n = filename.lastIndexOf('/');
		if ( n < 1 )
			dirname = "/";
		else
			dirname = filename.substring(0, n);

		buf = stringToASCIIZ(dirname);

		pkt = session.interact(FSPpacket.CC_GET_PRO, 0, buf, 0, buf.length, null, 0, 0);
		pkt.expect(FSPpacket.CC_GET_PRO);
		pro = new FSPprotection(pkt);

		/* owner can do anything */
		if ( pro.owner )
			return true;
		/* you need ADD permission for uploading files */
		if ( ! pro.add )
			return false;
		if ( ! pro.delete ) {
			/* we need to check if file about to be uploaded already exists
			 * because we do not have permission to overwrite files.
			 */
			if ( stat(session, filename) != null )
				return false;
		}
		return true;
	}
}
