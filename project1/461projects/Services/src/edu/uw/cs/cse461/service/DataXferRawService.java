package edu.uw.cs.cse461.service;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.*;

import edu.uw.cs.cse461.net.base.NetBase;
import edu.uw.cs.cse461.util.ConfigManager;
import edu.uw.cs.cse461.util.IPFinder;
import edu.uw.cs.cse461.util.Log;
import edu.uw.cs.cse461.net.base.NetLoadableInterface.NetLoadableServiceInterface;

/**
 * Transfers reasonably large amounts of data to client over raw TCP and UDP sockets.  In both cases,
 * the server simply sends as fast as it can.  The server does not implement any correctness mechanisms,
 * so, when using UDP, clients may not receive all the data sent.
 * <p>
 * Four consecutive ports are used to send fixed amounts of data of various sizes.
 * <p>
 * @author zahorjan
 *
 */
public class DataXferRawService extends DataXferServiceBase implements NetLoadableServiceInterface {
	private static final String TAG="DataXferRawService";
	
	public static final int NPORTS = 4;
	public static final int[] XFERSIZE = {1000, 10000, 100000, 1000000};
	public static final int FIRST_PORT = 46104;
 
	private int mBasePort;

	private List<ServerSocket> mServerSocket;
	private List<DatagramSocket> mDatagramSocket;
	
	public DataXferRawService() throws Exception {
		super("dataxferraw");
		
		ConfigManager config = NetBase.theNetBase().config();
		mBasePort = config.getAsInt("dataxferraw.server.baseport", 0);
		if ( mBasePort == 0 ) throw new RuntimeException("dataxferraw service can't run -- no dataxferraw.server.baseport entry in config file");
		
		// Sanity check -- code below relies on this property
		if ( HEADER_STR.length() != RESPONSE_OKAY_STR.length() )
			throw new Exception("Header and response strings must be same length: '" + HEADER_STR + "' '" + RESPONSE_OKAY_STR + "'");	
		
		// The echo raw service's IP address is the ip the entire app is running under
		String serverIP = IPFinder.localIP();
		if ( serverIP == null ){
			throw new Exception("IPFinder isn't providing the local IP address.  Can't run.");
		}
		
		// There is (purposefully) no config file field to define the echo raw service's ports.
		// Instead, ephemeral ports are used.  (You can run the dumpservericestate application
		// to see ports are actually allocated.)
		
		mServerSocket = new ArrayList<ServerSocket>();
		mDatagramSocket = new ArrayList<DatagramSocket>();
		
		/*
		 * Create two sockets for each port, one for UDP and one for TCP. 
		 */
		int port = FIRST_PORT;
		for(int i = 0; i < NPORTS; i++)
		{
			ServerSocket server = new ServerSocket();
			server.bind(new InetSocketAddress(serverIP, port));
			server.setSoTimeout(NetBase.theNetBase().config().getAsInt("net.timeout.granularity", 500));
			
			mServerSocket.add(server);
			
			DatagramSocket datagram = new DatagramSocket(new InetSocketAddress(serverIP, port));
			datagram.setSoTimeout(NetBase.theNetBase().config().getAsInt("net.timeout.granularity", 500));
			
			mDatagramSocket.add(datagram);
			
			port++;
		
			Log.i(TAG,  "Server socket = " + server.getLocalSocketAddress());
			Log.i(TAG,  "Datagram socket = " + datagram.getLocalSocketAddress());
			
			//fork threads to listen on these sockets
			startUdpThread(i);
			startTcpThread(i);
		}
	}

	/*
	 * Start a thread to listen for UDP messages on socket mDatagramSocket.get(i)
	 * @param i : Index in mDatagramSocket array to look for socket to use
	 */
	private void startUdpThread(final int i)
	{
		// Code/thread handling the UDP socket
		Thread dgramThread = new Thread() {
			public void run() {
				byte buf[] = null;
				DatagramPacket packet = null;

				//	Thread termination in this code is primitive.  When shutdown() is called (by the
				//	application's main thread, so asynchronously to the threads just mentioned) it
				//	closes the sockets.  This causes an exception on any thread trying to read from
				//	it, which is what provokes thread termination.
				try {
					while ( !mAmShutdown ) {
						try {
							buf = new byte[HEADER_STR.length()];
							packet = new DatagramPacket(buf, buf.length);
							
							mDatagramSocket.get(i).receive(packet);
							if ( packet.getLength() < HEADER_STR.length() )
								throw new Exception("Bad header: length = " + packet.getLength());
							String headerStr = new String( buf, 0, HEADER_STR.length() );
							if ( ! headerStr.equalsIgnoreCase(HEADER_STR) )
								throw new Exception("Bad header: got '" + headerStr + "', wanted '" + HEADER_STR + "'");

							int response_length = XFERSIZE[i];
							
							while (response_length > 0) {
								int bytes_to_send = Math.min(response_length, 1000);
								response_length -= bytes_to_send;
								buf = new byte[RESPONSE_OKAY_STR.length() + bytes_to_send];
								System.arraycopy(RESPONSE_OKAY_STR.getBytes(), 0, buf, 0, HEADER_STR.length());
								
								mDatagramSocket.get(i).send(new DatagramPacket(buf, buf.length, packet.getAddress(), packet.getPort()));
							}
							
						} catch (SocketTimeoutException e) {
							// socket timeout is normal
						} catch (Exception e) {
							Log.w(TAG,  "Dgram reading thread caught " + e.getClass().getName() + " exception: " + e.getMessage());
						}
					}
				} finally {
					if ( mDatagramSocket != null ) { mDatagramSocket.get(i).close(); mDatagramSocket = null; }
				}
			}
		};
		dgramThread.start();
	}
	
	/*
	 * Start a thread to listen for TCP messages on socket mServerSocket.get(i)
	 * @param i : Index in mServerSocket array to look for socket to use
	 */
	private void startTcpThread(final int i)
	{
		// Code/thread handling the TCP socket
		Thread tcpThread = new Thread() {
	
			public void run() {
				byte[] header = new byte[4];
				byte[] buf = new byte[1024];
				int socketTimeout = NetBase.theNetBase().config().getAsInt("net.timeout.socket", 5000);
				try {
					while ( !isShutdown() ) {
						Socket sock = null;
						try {
							// accept() blocks until a client connects.  When it does, a new socket is created that communicates only
							// with that client.  That socket is returned.
							sock = mServerSocket.get(i).accept();
							// We're going to read from sock, to get the message to echo, but we can't risk a client mistake
							// blocking us forever.  So, arrange for the socket to give up if no data arrives for a while.
							sock.setSoTimeout(socketTimeout);
							InputStream is = sock.getInputStream();
							OutputStream os = sock.getOutputStream();
							// Read the header.  Either it gets here in one chunk or we ignore it.  (That's not exactly the
							// spec, admittedly.)
							int len = is.read(header);
							if ( len != HEADER_STR.length() )
								throw new Exception("Bad header length: got " + len + " but wanted " + HEADER_STR.length());
							String headerStr = new String(header); 
							if ( !headerStr.equalsIgnoreCase(HEADER_STR) )
								throw new Exception("Bad header: got '" + headerStr + "' but wanted '" + HEADER_STR + "'");
							os.write(RESPONSE_OKAY_STR.getBytes());

							int response_length = XFERSIZE[i];
							
							//Write back the data to the client
							while(response_length > 0)
							{
								int bytes_to_send = Math.min(response_length, 1024);
								response_length -= bytes_to_send;
								os.write(buf, 0, bytes_to_send);
							}
							
						} catch (SocketTimeoutException e) {
							// normal behavior, but we're done with the client we were talking with
						} catch (Exception e) {
							Log.i(TAG, "TCP thread caught " + e.getClass().getName() + " exception: " + e.getMessage());
						} finally {
							if ( sock != null ) try { sock.close(); sock = null;} catch (Exception e) {}
						}
					}
				} catch (Exception e) {
					Log.w(TAG, "TCP server thread exiting due to exception: " + e.getMessage());
				} finally {
					if ( mServerSocket != null ) try { mServerSocket.get(i).close(); mServerSocket = null; } catch (Exception e) {};
				}
			}
		};
		tcpThread.start();
	}
	
	/**
	 * This method is called when the entire infrastructure
	 * wants to terminate.  We set a flag indicating all threads
	 * should terminate.  We then close the sockets.  The threads
	 * using those sockets will either timeout and see the flag set or
	 * else wake up on an IOException because the socket has been closed
	 * and notice the flag is set.  Either way, they'll terminate.
	 */
	@Override
	public void shutdown() {
		super.shutdown();
		Log.d(TAG, "Shutting down");
	}
	
	
	/**
	 * Returns string summarizing the status of this server.  The string is printed by the dumpservicestate
	 * console application, and is also available by executing dumpservicestate through the web interface.
	 */
	@Override
	public String dumpState()
	{
		StringBuilder sb = new StringBuilder(super.dumpState());

		for(int i = 0; i < NPORTS; i++)
		{
			sb.append("\nListening on:\n\tTCP: ");
			if ( mServerSocket != null ) sb.append(mServerSocket.toString());
			else sb.append("Not listening");
			sb.append("\n\tUDP: ");
			if ( mDatagramSocket != null ) sb.append(mDatagramSocket.get(i).getLocalSocketAddress());
			else sb.append("Not listening");
		}
		return sb.toString();	
	}
}
