package edu.uw.cs.cse461.net.rpc;

import java.io.EOFException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;

import edu.uw.cs.cse461.net.base.NetBase;
import edu.uw.cs.cse461.net.base.NetLoadable.NetLoadableService;
import edu.uw.cs.cse461.net.rpc.RPCMessage.RPCCallMessage;
import edu.uw.cs.cse461.net.rpc.RPCMessage.RPCCallMessage.RPCControlMessage;
import edu.uw.cs.cse461.net.rpc.RPCMessage.RPCCallMessage.RPCInvokeMessage;
import edu.uw.cs.cse461.net.rpc.RPCMessage.RPCResponseMessage;
import edu.uw.cs.cse461.net.rpc.RPCMessage.RPCResponseMessage.RPCErrorResponseMessage;
import edu.uw.cs.cse461.net.rpc.RPCMessage.RPCResponseMessage.RPCNormalResponseMessage;
import edu.uw.cs.cse461.net.tcpmessagehandler.TCPMessageHandler;
import edu.uw.cs.cse461.util.ConfigManager;
import edu.uw.cs.cse461.util.IPFinder;
import edu.uw.cs.cse461.util.Log;

/**
 * Implements the side of RPC that receives remote invocation requests.
 * 
 * @author zahorjan
 *
 */
public class RPCService extends NetLoadableService implements Runnable, RPCServiceInterface {
	private static final String TAG="RPCService";
	
	private ServerSocket mServerSocket;
	
	//Hashmap of serviceName to <hashmap of methodName to RPCCallableMethod>
	private Map<String, Map<String, RPCCallableMethod>> handlers;
	
	/**
	 * Constructor.  Creates the Java ServerSocket and binds it to a port.
	 * If the config file specifies an rpc.server.port value, it should be bound to that port.
	 * Otherwise, you should specify port 0, meaning the operating system should choose a currently unused port.
	 * <p>
	 * Once the port is created, a thread needs to be spun up to listen for connections on it.
	 * 
	 * @throws Exception
	 */
	public RPCService() throws Exception {
		super("rpc");
	
		//init registered handlers hashmap
		handlers = new HashMap<String, Map<String, RPCCallableMethod>>();
		
		ConfigManager config = NetBase.theNetBase().config();
		String serverIP = IPFinder.localIP();
		int basePort = 0; //config.getAsInt("dataxferraw.server.baseport", 0);
		mServerSocket = new ServerSocket();
		InetSocketAddress addr = new InetSocketAddress(serverIP, (basePort));
		mServerSocket.bind(addr);
		mServerSocket.setSoTimeout(NetBase.theNetBase().config().getAsInt("net.timeout.granularity", 500));
		
		Thread tcpThread = new Thread(this);
		
		tcpThread.start();
	}
	
	/**
	 * Executed by an RPCService-created thread.  Sits in loop waiting for
	 * connections, then creates an RPCCalleeSocket to handle each one.
	 */
	@Override
	public void run() {
		try {
			while (!mAmShutdown) {
				Socket socket = null;
				try {
					socket = mServerSocket.accept();
					
					TCPMessageHandler handler = null;
					try {
						handler = new TCPMessageHandler(socket);
		
						//Connect
						JSONObject connectJSON = handler.readMessageAsJSONObject();
						if (!connectJSON.get("action").equals("connect")) {
							//failed connect
							throw new Exception("Connect message not received");
						}
						
						
						JSONObject responseJSON = new RPCMessage().marshall();
						responseJSON.put("type", "OK");
						responseJSON.put("callid", connectJSON.getInt("id"));
						
						RPCMessage response = RPCMessage.unmarshall(responseJSON.toString());

						handler.sendMessage(response.marshall());
						
						//invoke
						JSONObject invokeJSON = handler.readMessageAsJSONObject();
						String type = invokeJSON.getString("type");
						if (!type.equals("invoke")) {
							//failed connect
							throw new Exception("Invoke message not received");
						}
						
						RPCCallableMethod method = getRegistrationFor(invokeJSON.getString("app"), invokeJSON.getString("method"));
						
						JSONObject returnJSON = method.handleCall(invokeJSON.getJSONObject("args"));
						
						responseJSON = new RPCMessage().marshall();
						responseJSON.put("type", "OK");
						responseJSON.put("value", returnJSON);
						responseJSON.put("callid", invokeJSON.getInt("id"));


						response = RPCMessage.unmarshall(responseJSON.toString());

						handler.sendMessage(response.marshall());
						
						
					} catch (SocketTimeoutException e) {
						Log.e(TAG, "Timed out waiting for data on tcp connection");
					} catch (EOFException e) {
						// normal termination of loop
						Log.d(TAG, "EOF on tcpMessageHandlerSocket.readMessageAsString()");
					} catch (Exception e) {
						Log.i(TAG, "Unexpected exception while handling connection: " + e.getMessage());
					} finally {
						if ( handler != null ) try { handler.close(); } catch (Exception e) {}
					}
				} catch (SocketTimeoutException e) {
					// this is normal.  Just loop back and see if we're terminating.
				}
			}
		} catch (Exception e) {
			Log.w(TAG, "Server thread exiting due to exception: " + e.getMessage());
			System.out.println(e.getMessage());
		} finally {
			if ( mServerSocket != null )  try { mServerSocket.close(); } catch (Exception e) {}
			mServerSocket = null;
		}
	}
	
	/**
	 * Services and applications with RPC callable methods register them with the RPC service using this routine.
	 * Those methods are then invoked as callbacks when an remote RPC request for them arrives.
	 * @param serviceName  The name of the service.
	 * @param methodName  The external, well-known name of the service's method to call
	 * @param method The descriptor allowing invocation of the Java method implementing the call
	 * @throws Exception
	 */
	@Override
	public synchronized void registerHandler(String serviceName, String methodName, RPCCallableMethod method) throws Exception {
		Map<String, RPCCallableMethod> serviceMap = handlers.get(serviceName);
		if(serviceMap == null) {
			//first method for this serviceName
			serviceMap = new HashMap<String, RPCCallableMethod>();
			serviceMap.put(methodName, method);
			handlers.put(serviceName, serviceMap);
		} else {
			//If the method already exists, it will override the existing RPCCallableMethod
			serviceMap.put(methodName, method);
			//handlers.put(serviceName, serviceMap);
		}
	}
	
	/**
	 * Some of the testing code needs to retrieve the current registration for a particular service and method,
	 * so this interface is required.  You probably won't find a use for it in your code, though.
	 * 
	 * @param serviceName  The service name
	 * @param methodName The method name
	 * @return The existing registration for that method of that service, or null if no registration exists.
	 */
	public RPCCallableMethod getRegistrationFor( String serviceName, String methodName) {
		Map<String, RPCCallableMethod> serviceMap = handlers.get(serviceName);
		if(serviceMap == null) {
			return null;
		} 
		return serviceMap.get(methodName);
	}
	
	/**
	 * Returns the port to which the RPC ServerSocket is bound.
	 * @return The RPC service's port number on this node
	 */
	@Override
	public int localPort() {
		return mServerSocket.getLocalPort();
	}
	
	@Override
	public String dumpState() {
		return "baseport: " + localPort();
	}
}
