package edu.uw.cs.cse461.service;

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

public class DataXferTCPMessageHandlerService extends DataXferServiceBase// implements NetLoadableServiceInterface 
{
	private static final String TAG="DataXferTCPMessageHandlerService";
	private ServerSocket mServerSocket;
	private Socket sock;
	private static int port_offset = 0;
	
	public DataXferTCPMessageHandlerService() throws Exception {
		super("DataXferTCPMessageHandlerService");
		System.out.println("DataXferTCPMessageHandlerService");

		Thread tcpThread = new Thread() {
			public void run() {
				try {
					while(true) {
						ConfigManager config = NetBase.theNetBase().config();
						String serverIP = IPFinder.localIP();
						int basePort = config.getAsInt("dataxferraw.server.baseport", 0);

						mServerSocket = new ServerSocket();
						InetSocketAddress addr = new InetSocketAddress(serverIP, (basePort + port_offset + 10));
						port_offset++;
						mServerSocket.bind(addr);

						mServerSocket.setSoTimeout(NetBase.theNetBase().config().getAsInt("net.timeout.granularity", 500));
						while(true) {
							sock = mServerSocket.accept();
							TCPMessageHandler socket = new TCPMessageHandler(sock);
							
							String headerstr = socket.readMessageAsString();
	
							if(!headerstr.equalsIgnoreCase("XFER")) {
								throw new Exception("Bad header string. Expected: XFER, received: " + headerstr);
							}
							
							JSONObject json = socket.readMessageAsJSONObject();
							int xfer_size = (Integer) json.get("transferSize");
							
							socket.sendMessage("OKAY");
							
							while(xfer_size > 0) {
								System.out.println("xfer_size: " + xfer_size);
								int msg_size = Math.min(xfer_size, socket.getMaxReadLength());
								byte buf[] = new byte[msg_size];
								socket.sendMessage(buf);
								xfer_size -= msg_size;
							}
						}
					}
				} catch (Exception e) {
					Log.w(TAG, "Server thread exiting due to exception: " + e.getMessage());
					System.out.println(e.getMessage());
				} finally {
					System.out.println("CLOSE!");
					if ( sock != null )  try { sock.close(); } catch (Exception e) {}
					sock = null;
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
		if(sock != null) {
			try {
				sock.close();
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
