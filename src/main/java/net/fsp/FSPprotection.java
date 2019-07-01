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
 * Class for parsing reply to CC_GET_PRO command and holding
 * directory access information.
 * <p>
 * There are no get/set methods defined. Use public variables directly
 * for data access.
 *
 * @author Radim Kolar
 * @version 1.0rc7
 * @since 1.0rc7
 */
public class FSPprotection {

	/** Caller is directory owner */
	public boolean owner;

	/** public can delete files in this directory */
	public boolean delete;

	/** public can add files to this directory */
	public boolean add;

	/** public can create subdirectories in this directory */
	public boolean mkdir;

	/** public can download/get files from this directory */
	public boolean get;

	/** public can list files in this directory */
    public boolean list;

    /** public can rename files in this directory */
    public boolean rename;

    /** Readme file for this directory */
    public String readme;

    /** Directory bitfield - caller is owner */
    public static byte DIR_OWNER =  0x01;
    /** Directory bitfield - public can delete files */
    public static byte DIR_DEL =    0x02;
    /** Directory bitfield - public can add files */
    public static byte DIR_ADD =    0x04;
    /** Directory bitfield - public can make subdirectories */
    public static byte DIR_MKDIR =  0x08;
    /** Directory bitfield - public can download/get files.
     *  This flag is transfered over wire INVERTED because it used to be
     *  DIR_PRIVATE in past.
     */
    public static byte DIR_GET =    0x10;
    /** Directory bitfield - directory contains readme */
    public static byte DIR_README = 0x20;
    /** Directory bitfield - public can list directory */
    public static byte DIR_LIST =   0x40;
    /** Directory bitfield - public can rename files */
    public static byte DIR_RENAME = (byte) 0x80;

	/**
	 * Constructor for empty FSPprotection object.
	 *
	 * All fields are zeroed.
	 * @since 1.0rc7
	 */
	public FSPprotection() {
	}

	/** Creates FSPprotection instance from FSPpacket.
	 *
	 * @param pkt FSPpacket with CC_GET_PRO server response
	 * @throws IllegalArgumentException if pkt is not response
	 *         to CC_GET_PRO command
	 * @since 1.0rc7
	 */
	public FSPprotection(FSPpacket pkt) {
		if ( pkt.cmd != FSPpacket.CC_GET_PRO )
			throw new IllegalArgumentException("Packet is not response to CC_GET_PRO");
		/* Load FSPv1 README */
		if ( pkt.bb_len > 0 )
			readme = new String (pkt.buf,0,pkt.bb_len-1);
		if ( pkt.bb_pos > 0 && pkt.xtra_len > 0 ) {
			/* Parse FSPv2 directory access flags */
			byte prot;

			prot = pkt.buf[pkt.bb_len];
			if ( (prot & DIR_OWNER) != 0 )
				owner = true;
			if ( (prot & DIR_DEL) != 0 )
				delete = true;
			if ( (prot & DIR_ADD) != 0 )
				add = true;
			if ( (prot & DIR_MKDIR) != 0 )
				mkdir = true;
		    if ( (prot & DIR_GET) == 0 )
		    	get = true;
		    if ( (prot & DIR_LIST) != 0 )
		    	list = true;
		    if ( (prot & DIR_RENAME) != 0 )
		    	rename = true;
		} else
		{
			/* for compatibility reasons with FSPv1 or embedded fsp servers
			 * set default access rules.
			 */
			get = true;
			list = true;
		}
	}
}
