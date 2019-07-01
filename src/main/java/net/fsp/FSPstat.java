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
import java.util.Date;

/** Class for holding result of CC_STAT command.
 * This command retrieves information about file/directory.
 * <p>
 * There are no get/set methods defined. Use public variables directly
 * for data access.
 *
 * @author Radim Kolar
 * @version 1.0rc7
 * @since 1.0
 */
public class FSPstat
{
	/** object name
	 * @since 1.0 */
	public String name;
	/** length in bytes
	 * @since 1.0 */
	public long length;
	/** last modification time in standard Java timeunits
	 * @since 1.0 */
	public long lastmod;
	/** object type.
	 * It can be RDTYPE_FILE or RDTYPE_DIR
	 * @since 1.0 */
	public byte type;

	/** object is a file
	 * @since 1.0 */
	public static final byte RDTYPE_FILE=1;
	/** object is a directory
	 * @since 1.0 */
	public static final byte RDTYPE_DIR=2;

	/** converts information to human-readable string
	 * @since 1.0
	 */
	public String toString()
	{
		StringBuffer res;
		res=new StringBuffer(80);

		if(type==RDTYPE_FILE)
			res.append("file ");
		else
			if(type==RDTYPE_DIR)
				res.append("dir ");
			else
				res.append("unkn ");

		res.append(name);
		res.append(" lastmod=");
		res.append(new Date(lastmod));

		res.append(" size=");
		res.append(length);

		return res.toString();
	}
}
