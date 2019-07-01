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
import java.net.DatagramPacket;

/**
 * This class handles assembly and disassembly FSP packets from/to DatagramPacket.
 * <p>
 * FSPpacket class represents one packet sent over network while carrying
 * payload of FSP protocol.
 *
 * @author Radim Kolar
 * @see java.net.DatagramPacket
 * @version 1.0rc7
 * @since 1.0
 */
public class FSPpacket
{

	/** Size of FSP protocol v2 header - 12 bytes. */
	public final static int HSIZE=12;
	/** Maximum standard FSP data payload - 1024 bytes.
	 *    All FSP clients and servers are required to handle this. */
	public final static int SPACE=1024;
	/** Maximum payload supported by this library. */
	public final static int MAXSPACE=SPACE;
	/** Maximum packet size (HSIZE+MAXSPACE). */
	public final static int MAXSIZE=MAXSPACE+HSIZE;

	/* type of packet direction */
	protected boolean serverside;

	/* decoded FSP HEADER fields */

	/** FSP command, use one of CC_ constants */
	public byte cmd;
	/** FSP packet checksum, field is computed by assemble() */
	public byte sum;
	/** server access KEY, needs to be correct */
	public short bb_key;
	/** client side sequence number, any number can be used */
	public short bb_seq;
	/** number of required bytes in buffer */
	public short bb_len;
	/** file position pointer (4GB-1 max) */
	public int   bb_pos;
	/** optional data size in buffer */
	public int   xtra_len;
	/** buffer for holding user generated data to be transmitted via FSP protocol */
	public byte buf[];

	/* FSP commands */
	/** get server setup */
	public final static byte CC_VERSION =0x10;
	/** get server info block */
	public final static byte CC_INFO    =0x11;
	/** error response from server */
	public final static byte CC_ERR     =0x40;
	/** get a directory listing */
	public final static byte CC_GET_DIR =0x41;
	/** get a file */
	public final static byte CC_GET_FILE=0x42;
	/** open temporary file for writing */
	public final static byte CC_UP_LOAD =0x43;
	/** close a opened file for writing and move it to final location */
	public final static byte CC_INSTALL =0x44;
	/** delete file */
	public final static byte CC_DEL_FILE=0x45;
	/** delete directory */
	public final static byte CC_DEL_DIR =0x46;
	/** get directory protection */
	public final static byte CC_GET_PRO =0x47;
	/** set directory protection */
	public final static byte CC_SET_PRO =0x48;
	/** make directory */
	public final static byte CC_MAKE_DIR=0x49;
	/** finish a session */
	public final static byte CC_BYE     =0x4A;
	/** atomic get+delete */
	public final static byte CC_GRAB_FILE=0x4b;
	/** atomic get+delete done */
	public final static byte CC_GRAB_DONE=0x4c;
	/** get file/directory information */
	public final static byte CC_STAT=0x4d;
	/** rename file or directory */
	public final static byte CC_RENAME=0x4e;
	/** commands > 7f are reserved for FSP v3 headers */
	public final static byte CC_LIMIT=(byte)0x80;
	/** reserved for testing FSP v3 header */
	public final static byte CC_TEST =(byte)0x81;

	/**
	 * Constructs a new storage for handling client side of
	 * FSP packets. Storage can be used for sending any number of packets.
	 *
	 * @since 1.0
	 */
	public FSPpacket()
	{
		buf=new byte[MAXSPACE];
		serverside=false;
	}

	/**
	 * Constructs a new storage for handling FSP packets. Storage can be used
	 * for sending any number of packets.
	 *
	 * @param serverside Use server-side checksum method for FSP packets
	 * @since 1.0rc4
	 */
	public FSPpacket(boolean serverside)
	{
		this();
		this.serverside=serverside;
	}

	/** Disassemble UDP packet into this FSP packet
	 *
	 * @param udp the DatagramPacket to be disassembled.
	 * @since 1.0
	 * @return true if valid FSP packet was found
	 */
	public boolean disassemble(DatagramPacket udp)
	{
		byte data[]=udp.getData();
		byte csum;
		short nbb_len;
		int nxtra_len;
		/* check size */
		short nsum=(short)(udp.getLength());
		nbb_len =(short)((data[6] << 8) | (data[7] & 0xFF));
		// System.out.println("udp len "+nsum+", hdr len "+nbb_len);
		nxtra_len=nsum-HSIZE-nbb_len;
		if(nxtra_len<0) {
			// System.out.println("Packet truncated.");
			return false;
		}
		if(nbb_len+nxtra_len>MAXSPACE)
		{
			// System.out.println("Packet too big.");
			return false;
		}
		/* check sum */
		sum=data[1]; /* sum */
		data[1]=0;
		int t=nsum-1;
		if(!serverside) nsum = 0;
		for(;t>=0;t--)
			nsum+=(data[t] & 0xFF);
		csum=(byte)(nsum + (nsum >>> 8));
		if(csum!=sum) {
			// System.out.println("BAD sum. Got="+sum+" Computed="+csum);
			return false;
		}
		/* extract header */
		cmd= data[0];
		bb_key =(short)((data[2] << 8) | (data[3] & 0xFF));
		bb_seq =(short)((data[4] << 8) | (data[5] & 0xFF));
		bb_pos =((data[8] << 8) | (data[9] & 0xFF)) << 16;
		bb_pos|=((data[10] & 0xFF)<< 8) | (data[11] & 0xFF);
		bb_len=nbb_len;
		xtra_len=nxtra_len;

		/* extract data */
		System.arraycopy(data,HSIZE,buf,0,bb_len+xtra_len);
		return true;
	}

	/** generate UDP packet from this FSP packet
	 *
	 * @param udp storage for assembled packet
	 * @return assembled packet. Reference to udp paramater.
	 * @since 1.0
	 */
	public DatagramPacket assemble(DatagramPacket udp)  {
		byte data[]=udp.getData();
		int payload = bb_len+xtra_len;

        if ( payload > MAXSPACE )
        	throw new IllegalArgumentException("Maximum supported payload by this library is "+MAXSPACE);
        else
        	if ( data.length < HSIZE+payload ) {
        		data = new byte[payload+HSIZE];
        		udp.setData(data);
        	}

		/* make header */
		data[0] =cmd; /* command */
		data[1] =0; /* sum */
		data[2] =(byte)((bb_key >>> 8) & 0xFF); /* key */
		data[3] =(byte)(bb_key & 0xFF);
		data[4] =(byte)((bb_seq >>> 8) & 0xFF);  /* seq */
		data[5] =(byte)(bb_seq & 0xFF);
		data[6] =(byte)((bb_len >>> 8) & 0xFF); /* len */
		data[7] =(byte)(bb_len & 0xFF);
		data[8] =(byte)((bb_pos >>>24) & 0xFF); /* pos */
		data[9] =(byte)((bb_pos >>>16) & 0xFF);
		data[10]=(byte)((bb_pos >>> 8) & 0xFF);
		data[11]=(byte)(bb_pos & 0xFF);
		/* END OF HEADER */

		// copy data
		System.arraycopy(buf,0,data,HSIZE,payload);

		/* make sum */
		short nsum=0;
		if(!serverside) nsum=(short)(HSIZE+payload);
		for(int t=HSIZE+payload-1;t>=0;t--)
			nsum+=(data[t] & 0xFF);
		data[1]=(byte)(nsum + (nsum >>> 8));
		sum=data[1];

		udp.setLength(HSIZE+payload);
		return udp;
	}

	/** prints header of FSP packet.
	 *
	 * @since 1.0
	 * @return FSP Header converted to the string
	 */
	public String toString()
	{
		StringBuffer sb=new StringBuffer(40);
		sb.append(this.getClass());
		sb.append(" cmd=0x");
		sb.append(Integer.toString(cmd & 0xFF,16));
		sb.append(" sum=0x");
		sb.append(Integer.toString(sum & 0xFF,16));
		sb.append(" key=0x");
		sb.append(Integer.toString(bb_key & 0xFFFF,16));
		sb.append(" seq=0x");
		sb.append(Integer.toString(bb_seq & 0xFFFF,16));
		sb.append(" len=");
		sb.append(bb_len);
		sb.append(" pos=");
		sb.append(bb_pos);
		sb.append(" xtra_len=");
		sb.append(xtra_len);

		return sb.toString();
	}

	/** set FSP data payload to data1 and data2
	 *
	 * @since 1.0
	 * @param data1 data payload array
	 * @param offset1 starting offset of data in data1 array
	 * @param length1 data size
	 * @param data2 xtra data payload array
	 * @param offset2 starting offset of xtra data in data2 array
	 * @param length2 xtra data size
	 *
	 * @throws java.lang.IllegalArgumentException
	 * */
	public void setData(byte data1[],int offset1,int length1,byte data2[],int offset2,int length2)
	{
		if ( length1 + length2 > FSPpacket.MAXSPACE)
			throw new IllegalArgumentException("Maximum supported payload size is "+MAXSPACE);
		if(length1>0)
		{
            try {
    			System.arraycopy(data1,offset1,buf,0,length1);
            }
            catch (ArrayIndexOutOfBoundsException e) {
				throw new IllegalArgumentException("invalid offset1/length1");
            }
		} else
			if ( length1 < 0 )
				throw new IllegalArgumentException("length1 can not be negative");
		bb_len=(short)length1;
		if(length2>0)
		{
			try {
				System.arraycopy(data2,offset2,buf,bb_len,length2);
			}
			catch (ArrayIndexOutOfBoundsException e) {
				throw new IllegalArgumentException("invalid offset2/length2");
			}
		} else
			if ( length2 < 0)
				throw new IllegalArgumentException("length2 can not be negative");
		xtra_len=(short)length2;
	}

	/** check if we are using server side checksum method.
	 *
	 * @since 1.0rc7
	 */
	public boolean isServerSide() {
		return serverside;
	}

	/**
	 * Test if packet is expected response to sent command.
	 * If comparison fails, throw exception.
	 *
	 * If packet contains CC_ERR, use it for detailed error message.
	 * @param command expected reply
	 * @throws IOException unexpected response received
	 * @since 1.0rc7
	 */
	public void expect(byte command) throws IOException {
		if ( cmd == command )
			return;
		if ( cmd == CC_ERR )
			throw new IOException("FSP ERR: "+new String(buf,0,bb_len-1));
		else
			throw new IOException("Unexpected FSP server response. Expected: "+command+" Received: "+cmd);
	}
}
