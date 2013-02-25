package edu.uw.cs.cse461.service;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.util.ArrayList;

import org.json.JSONObject;

import edu.uw.cs.cse461.net.base.NetBase;
import edu.uw.cs.cse461.net.base.NetLoadableInterface.NetLoadableServiceInterface;
import edu.uw.cs.cse461.net.tcpmessagehandler.TCPMessageHandler;
import edu.uw.cs.cse461.util.ConfigManager;
import edu.uw.cs.cse461.util.IPFinder;
import edu.uw.cs.cse461.util.Log;

public class DataXferTCPMessageHandlerService extends DataXferServiceBase {
	private static final String TAG="DataXferTCPMessageHandlerService";
	
	private ServerSocket mServerSocket;
	
	public DataXferTCPMessageHandlerService() throws Exception {
		super("DataXferTCPMessageHandlerService");
		
		ConfigManager config = NetBase.theNetBase().config();
		String serverIP = IPFinder.localIP();
		int basePort = config.getAsInt("dataxferraw.server.baseport", 0);
		mServerSocket = new ServerSocket();
		InetSocketAddress addr = new InetSocketAddress(serverIP, (basePort + 1000));
		mServerSocket.bind(addr);
		mServerSocket.setSoTimeout(NetBase.theNetBase().config().getAsInt("net.timeout.granularity", 500));

		Thread tcpThread = new Thread() {
			public void run() {
				try {
					while (!mAmShutdown) {
						Socket sock = null;
						try {
							sock = mServerSocket.accept();
							
							TCPMessageHandler socket = null;
							try {
								socket = new TCPMessageHandler(sock);
				
								String headerstr = socket.readMessageAsString();
				
								if(!headerstr.equalsIgnoreCase("XFER")) {
									throw new Exception("Bad header string. Expected: XFER, received: " + headerstr);
								}
										
								JSONObject json = socket.readMessageAsJSONObject();
								int xfer_size = (Integer) json.get("transferSize");
										
								socket.sendMessage("OKAY");
										
								while(xfer_size > 0) {
										//	System.out.println("xfer_size: " + xfer_size);
									int msg_size = Math.min(xfer_size, socket.getMaxReadLength());
									byte buf[] = new byte[msg_size];
									socket.sendMessage(buf);
									xfer_size -= msg_size;
								}
							} catch (SocketTimeoutException e) {
								Log.e(TAG, "Timed out waiting for data on tcp connection");
							} catch (EOFException e) {
								// normal termination of loop
								Log.d(TAG, "EOF on tcpMessageHandlerSocket.readMessageAsString()");
							} catch (Exception e) {
								Log.i(TAG, "Unexpected exception while handling connection: " + e.getMessage());
							} finally {
								if ( socket != null ) try { socket.close(); } catch (Exception e) {}
							}
						} catch (SocketTimeoutException e) {
							// this is normal.  Just loop back and see if we're terminating.
						}
					}
				} catch (Exception e) {
					Log.w(TAG, "Server thread exiting due to exception: " + e.getMessage());
					System.out.println(e.getMessage());
				} finally {
					//System.out.println("CLOSE!");
					if ( mServerSocket != null )  try { mServerSocket.close(); } catch (Exception e) {}
					mServerSocket = null;
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
		if(mServerSocket != null) {
			try {
				mServerSocket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		Log.d(TAG, "Shutting down");
	}
	
	
	/**
	 * Returns string summarizing the status of this server.  The string is printed by the dumpservicestate
	 * console application, and is also available by executing dumpservicestate through the web interface.
	 */
	@Override
	public String dumpState()
	{
		return null;
	}

}
