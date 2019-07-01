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
import java.net.InetAddress;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketTimeoutException;
import java.io.InterruptedIOException;
import java.io.IOException;
import java.util.Hashtable;

/** This class represents one live FSP session.
 * <p>
 * FSPsession class takes care about FSP session management. It handles
 * packet resends on timeouts and key management. It also handles locks
 * for synchronizing multi session access to same FSP server.
 *
 * @author Radim Kolar
 * @see FSPpacket
 * @version 1.0rc7
 * @since 1.0
 */
public class FSPsession
{
	private DatagramSocket socket;
	private DatagramPacket udp;
	private FSPpacket   packet;

	private short seq;  /* sequence number */

	private int timeout;
	private int delay;
	private int maxdelay;

	private int port;
	private InetAddress host;
	private String hostadr;  /* 1.2.3.4:2234  */

	private static Hashtable<String, Short> locks;
	volatile private Object writer;
	private Object lockwait;

	/** minimum resent delay (msec) */
	public final static int MIN_DELAY=1000;
	/** default resent delay (msec) */
	public final static int DEFAULT_DELAY=1340;
	/** maximum resent delay. FSP protocol has max delay 300s,
	 * but we are using 60s for faster recovery on common Internet/Wifi lines. */
	public final static int MAX_DELAY=60000;

	/** default timeout (msec) */
	public final static int DEFAULT_TIMEOUT=300000;

	/** Creates a new FSP session
	 *
	 * @param host hostname of fsp server
	 * @param port port number on fsp server
	 * @since 1.0
	 */
	public FSPsession(String host, int port) throws java.net.SocketException,java.net.UnknownHostException
	{
		this(InetAddress.getByName(host),port);
	}

	/** Creates a new FSP session
	 *
	 * @param host address of fsp server
	 * @param port port number on fsp server
	 * @since 1.0
	 */
	public FSPsession(InetAddress host, int port) throws java.net.SocketException
	{
		socket=new DatagramSocket();
		udp=new DatagramPacket(new byte[FSPpacket.MAXSIZE],FSPpacket.MAXSIZE,host,port);
		if(port==0) port=21;
		udp.setPort(port);
		udp.setAddress(host);
		packet=new FSPpacket();
		seq=(short)((int)(Math.random()*0xffff) & 0xfff8);
		timeout=DEFAULT_TIMEOUT;
		delay=DEFAULT_DELAY;
		maxdelay=MAX_DELAY;

		if(locks==null) locks=new Hashtable<String, Short>();
		hostadr=host.getHostAddress()+":"+port;
		hostadr=hostadr.intern();
		synchronized(locks)
		{
			Short k=(Short)locks.get(hostadr);
			if(k==null)
			{
				k = Short.valueOf((short)0);
				locks.put(hostadr,k);
			}
			/* Get object from hash table, we need it for correct locking */
			java.util.Enumeration<String> en=locks.keys();
			while(en.hasMoreElements())
			{
				String hkey;
				hkey=(String)en.nextElement();
				if(hostadr.equals(hkey))
				{
					hostadr=hkey;
					break;
				}
			}
		}
		this.port=port;
		this.host=host;
		lockwait = new Object();
	}

	/**
	 * Sends FSP packet and waits for reply, packet is resent if lost.
	 *
	 * @param cmd FSP command to be sent to server
	 * @param filepos position in file
	 * @param data1 array contains data for FSP packet
	 * @param offset1 starting offset of data in array
	 * @param length1 length of data
	 * @param data2 array with extra data
	 * @param offset2 starting offset of data in array
	 * @param length2 length of extra data
	 * @since 1.0
	 * @throws SocketTimeoutException if server can't be reached within timeout
	 */
	public FSPpacket interact(byte cmd,long filepos, byte data1[],int offset1,int length1, byte data2[],int offset2,int length2) throws SocketTimeoutException
	{
		int rdelay=delay;
		int rtimeout=0;
		Short k;

		/* setup the packet */
		packet.setData(data1,offset1,length1,data2,offset2,length2);
		k = Short.valueOf((short)((int)(Math.random()*0xffff) & 0xfff8));
		if (k.shortValue()==seq)
			seq ^=0x1080;
		else
			seq=k.shortValue();
		packet.bb_seq=seq;
		packet.bb_pos=(int)(filepos & 0xffffffff);
		packet.cmd=cmd;
		/* get key for the host */
		synchronized(hostadr)
		{
			k=(Short)locks.get(hostadr);
			packet.bb_key=k.shortValue();

			while(true)
			{
				/* increase a sequence number */
				packet.bb_seq=(short)(seq + (++packet.bb_seq & 0x07));
				packet.assemble(udp);
				try
				{
					socket.setSoTimeout(rdelay);
				}
				catch (java.net.SocketException ex) {}
				try
				{
					socket.send(udp);
					udp.setLength(FSPpacket.MAXSIZE);
					socket.receive(udp);
					if(packet.disassemble(udp)==true)
					{
						// System.out.println("received valid fsp packet seq="+packet.bb_seq+" our seq="+seq);
						/* check reply type */
						if( (packet.cmd != cmd) && (packet.cmd != FSPpacket.CC_ERR))
							continue;
						/* check position */
						if(packet.bb_pos != filepos && ( cmd == FSPpacket.CC_GET_DIR || cmd == FSPpacket.CC_GET_FILE || cmd == FSPpacket.CC_UP_LOAD || cmd == FSPpacket.CC_GRAB_FILE || cmd == FSPpacket.CC_INFO) )
							continue;
						/* check sequence number */
						if( (packet.bb_seq & 0xfff8) == (seq & 0xfff8) )
						{
							locks.put(hostadr, Short.valueOf(packet.bb_key));
							return packet;
						}
					}
				} catch (InterruptedIOException ioe) {}
				catch (IOException ioe) {}

				rtimeout+=rdelay;
				// System.out.println(rtimeout);
				if(rtimeout>=timeout) throw new SocketTimeoutException("Timeout");
				/* increase delay */
				rdelay*=1.5f;
				if(rdelay>maxdelay) rdelay=maxdelay;
			}
		}
	}

	/** Close a session.
	 * <p>
	 * Session object can't be used after session is closed. This also sends
	 * CC_BYE command to server.
	 *
	 * @since 1.0
	 */
	public void close()
	{
		if(socket!=null)
			try
			{
				interact(FSPpacket.CC_BYE,0,null,0,0,null,0,0);
				socket.close();
			}
			catch (Exception e) {}

		socket=null;
		udp=null;
		packet=null;
		host=null;
		port=0;
	}

	/** Gets the delay time before we resent packet for first time.
	 *
	 * @since 1.0
	 * @return delay in milliseconds
	 */
	public int getDelay()
	{
		return delay;
	}

	/** Sets the delay.
	 * <p>
	 * This functions sets delay parameter of FSP protocol stack. This
	 * is timeout value for first packet. If packet is lost delay is multiplied
	 * by 1.5. Delay is in milliseconds and must be between MIN_DELAY and MAX_DELAY
	 *
	 * @since 1.0
	 * @param delay new delay in milliseconds
	 */
	public void setDelay(int delay)
	{
		if(delay<MIN_DELAY)
			this.delay=MIN_DELAY;
		else
			if(delay>MAX_DELAY)
				this.delay=MAX_DELAY;

		this.delay=delay;
	}

	/** Gets the maximum delay time between packet resents.
	 *
	 * @since 1.0
	 * @return max delay in milliseconds
	 */
	public int getMaxDelay()
	{
		return maxdelay;
	}

	/** Sets the maximum delay time between resent packets.
	 *
	 * @param delay new maximum delay time in milliseconds.
	 * @since 1.0
	 */
	public void setMaxDelay(int delay)
	{
		if(delay>MAX_DELAY)
			this.maxdelay=MAX_DELAY;
		else
			this.maxdelay=delay;
	}

	/**
	 * Get timeout value used by session.
	 *
	 * @since 1.0
	 * @return timeout value in milliseconds. 0 means infinite timeout
	 */
	public int getTimeout()
	{
		if (timeout==Integer.MAX_VALUE)
			return 0;
		else
			return timeout;
	}

	/** Set timeout value.
	 * <p>
	 * If no packet from server is received in this time, session will time out.
	 *
	 * @since 1.0
	 * @param timeout timeout value in milliseconds. 0 means infinite timeout
	 */
	public void setTimeout(int timeout)
	{
		if (timeout>0)
			this.timeout=timeout;
		else
		    if (timeout==0)
		    	this.timeout=Integer.MAX_VALUE;
	}

	/** Get FSP host.
	 * <p>
	 * Get InetAddress of FSP server we are connected to.
	 * @since 1.0rc7
	 * @return InetAddress of connected FSP server or null if session is closed.
	 */
	public InetAddress getHost()
	{
		return host;
	}

	/** Get FSP port.
	 * <p>
	 * Get port of connected FSP server.
	 * @since 1.0rc7
	 * @return port of connected FSP Server or zero if session is closed
	 */
	public int getPort()
	{
		return port;
	}

	/** closes session before doing GC.
	 * <p>
	 * If session is not closed, close it before doing GC on this object.
	 * Timeout is set to 7 sec when doing it.
	 *
	 * @since 1.0
	 */
	@Deprecated
	public void finalize()
	{
		timeout=7000;
		close();
	}

	/**
	 * Set Writer lock. FSP protocol supports only one writer per session.
	 * If you need more writers you need to open more sessions to target
	 * server.
	 * For synchronization purposes before starting write operation you have
	 * to call lockWriter.
	 *
	 * @param lock object writing to FSP session
	 * @param wait wait if we can't acquire lock now
	 * @throws IllegalStateException if session is already write locked and
	 * no wait was specified
	 * @since 1.0rc8
	 */
	public void lockWriter(Object lock, boolean wait) {
		synchronized(lockwait) {
			if ( writer == null || writer == lock )
				writer = lock;
			else
				if ( wait )
					while ( true ) {
						try {
							lockwait.wait();
						} catch (InterruptedException e) {}
						if ( writer == null ) {
							writer = lock;
							return;
						}
					}
				else
					throw new IllegalStateException("Writer is locked");
		}
	}

	/**
	 * Release Writer lock. After you are done writing data to FSP server
	 * you have to call this function for writer lock release.
	 *
	 * Failure to call this function will leave other threads waiting.
	 *
	 * @param lock object writing to fsp session
	 * @throws IllegalStateException if lock is invalid
	 * @since 1.0rc8
	 */
	public void unlockWriter(Object lock) {
		synchronized(lockwait) {
			if ( writer == null || writer != lock )
					throw new IllegalStateException("Not write lock owner");
			writer = null;
			lockwait.notify();
		}
	}
}
