package edu.uw.cs.cse461.service;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;

import org.json.JSONObject;

import edu.uw.cs.cse461.net.base.NetBase;
import edu.uw.cs.cse461.net.base.NetLoadableInterface.NetLoadableServiceInterface;
import edu.uw.cs.cse461.net.tcpmessagehandler.TCPMessageHandler;
import edu.uw.cs.cse461.util.ConfigManager;
import edu.uw.cs.cse461.util.IPFinder;
import edu.uw.cs.cse461.util.Log;

public class DataXferTCPMessageHandlerService extends DataXferServiceBase implements NetLoadableServiceInterface {
	private static final String TAG="DataXferTCPMessageHandlerService";
	
	protected DataXferTCPMessageHandlerService(String loadablename) throws Exception {
		super("DataXferTCPMessageHandlerService");
	
		ConfigManager config = NetBase.theNetBase().config();
		String serverIP = IPFinder.localIP();
		int basePort = config.getAsInt("dataxferraw.server.baseport", 0);

		ServerSocket server = new ServerSocket();
		server.bind(new InetSocketAddress(serverIP, basePort));
		server.setSoTimeout(NetBase.theNetBase().config().getAsInt("net.timeout.granularity", 500));
	
		Socket s = server.accept();
		TCPMessageHandler socket = new TCPMessageHandler(s);
		
		String headerstr = socket.readMessageAsString();
		
		if(!headerstr.equalsIgnoreCase("XFER")) {
			throw new Exception("Bad header string. Expected: XFER, received: " + headerstr);
		}
		
		JSONObject json = socket.readMessageAsJSONObject();
		int xfer_size = (Integer) json.get("transferSize");
		
		while(xfer_size > 0) {
			int msg_size = Math.min(xfer_size, socket.getMaxReadLength());
			byte buf[] = new byte[msg_size];
			socket.sendMessage(buf);
			xfer_size -= msg_size;
		}
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
		return null;
	}

}
