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

/**
 * Class for parsing reply to CC_VERSION command and holding
 * server setup information.
 * FSP command CC_VERSION queries server setup information.
 * <p>
 * There are no get/set methods defined. Use public variables directly
 * for data access.
 *
 * @author Radim Kolar
 * @version 1.0rc7
 * @since 1.0rc7
 */
public class FSPversion implements Cloneable {
	/** server version
	 * @since 1.0rc7 */
	public String version;
	/** server sent extended info. This field must be true otherwise other
	 * fields than version were not supplied by server and are undefined. */
	public boolean extended_info;
	/** server logs transfers
	 * @since 1.0rc7 */
	public boolean logging;
	/** server runs in read-only mode
	 * @since 1.0rc7 */
	public boolean read_only;
	/** reverse lookup required for connected clients
	 * @since 1.0rc7 */
	public boolean reverse;
	/** server runs in private mode, only authorized clients can connect
	 * @since 1.0rc7 */
	public boolean private_mode;
	/** server does throughput limiting in bytes/sec. zero means no limit.
	 * @since 1.0rc7 */
	public long throughput;
	/** server accepts xtra data on in input packets
	 * @since 1.0rc7 */
	public boolean extra_data;
	/** if > 1024 then maximum payload supported by server, otherwise
	 * Preferred packet size.
	 * @since 1.0rc7 */
	public short payload;

	/**
	 * Constructor for empty FSPversion object.
	 *
	 * All fields are zeroed.
	 * @since 1.0rc7
	 */
	public FSPversion() {
	}

	/**
	 * Creates FSPversion from FSPpacket
	 * @param pkt FSPpacket with CC_VERSION response
	 * @since 1.0rc7
	 * @throws IllegalArgumentException if pkt is not response to
	 * CC_VERSION command
	 */
	public FSPversion(FSPpacket pkt) {
		if (pkt.cmd != FSPpacket.CC_VERSION)
			throw new IllegalArgumentException("Packet is not response to CC_VERSION");

		/* load FSPv1 protocol version string */
		version = new String (pkt.buf,0,pkt.bb_len).trim();
		if (pkt.bb_pos > 0 && pkt.xtra_len > 0) {
			/* reply is from FSPv2 server */
			extended_info = true;
			byte flags;
			flags = pkt.buf[pkt.bb_len];
			if ( (flags & 1) != 0 )
				logging = true;
			else
				logging = false;
			if ( (flags & 2) != 0 )
				read_only = true;
			else
				read_only = false;
			if ( (flags & 4) != 0 )
				reverse = true;
			else
				reverse = false;
			if ( (flags & 8) != 0 )
				private_mode = true;
			else
				private_mode = false;
			if ( (flags & 0x10) != 0 && pkt.xtra_len > 4 ) {
				throughput = ( (pkt.buf[pkt.bb_len+1] << 8)|(pkt.buf[pkt.bb_len+2] & 0xFF)) << 16;
				throughput|= ( (pkt.buf[pkt.bb_len+3] & 0xFF)<< 8)|(pkt.buf[pkt.bb_len+4] & 0xFF);
			} else {
				flags &=0xEF;
				throughput = 0;
			}
			if ( (flags & 0x20) != 0 )
				extra_data = true;
			else
				extra_data = false;
			/* check for optional max packet size block */
			if ( ( flags & 0x10) != 0 && pkt.xtra_len >= 7)
				pkt.bb_len += 5;
			else if ( ( flags & 0x10) == 0 && pkt.xtra_len >= 3 )
				pkt.bb_len += 1;
			else
				pkt.bb_len = 0;
			if (pkt.bb_len > 0) {
				payload =(short)((pkt.buf[pkt.bb_len] << 8)|(pkt.buf[pkt.bb_len+1]&0xFF));
			} else
				payload = 0;
		} else
			extended_info = false;
	}

	/**
	 * Clones FTPversion object.
	 *
	 * @see java.lang.Object#clone()
	 */
	public Object clone() throws CloneNotSupportedException {
		FSPversion newver;
		newver = new FSPversion();
		newver.version = this.version;
		newver.logging = this.logging;
		newver.read_only = this.read_only;
		newver.reverse = this.reverse;
		newver.private_mode = this.private_mode;
		newver.throughput = this.throughput;
		newver.extra_data = this.extra_data;
		newver.payload = this.payload;
		newver.extended_info = this.extended_info;
		return newver;
	}
}
