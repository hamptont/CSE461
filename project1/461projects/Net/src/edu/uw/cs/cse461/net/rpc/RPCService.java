package edu.uw.cs.cse461.net.rpc;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
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
	
	private ServerSocket server;
	private int port;
	
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
		
		//Get IP address
		String serverIP = IPFinder.localIP();
		if ( serverIP == null ){
			throw new Exception("IPFinder isn't providing the local IP address.  Can't run.");
		}
		
		//get port number from config file. Set as 0 if value not found
		port = NetBase.theNetBase().config().getAsInt("rpc.server.port", 0);
		
		//create and bind socket
		server = new ServerSocket();
		InetSocketAddress addr = new InetSocketAddress(serverIP, port);
		server.bind(addr);	
		server.setSoTimeout(NetBase.theNetBase().config().getAsInt("net.timeout.granularity", 500));

		//fork thread to accept connections
		Thread t = new Thread(this);
		t.start();
	}
	
	/**
	 * Executed by an RPCService-created thread.  Sits in loop waiting for
	 * connections, then creates an RPCCalleeSocket to handle each one.
	 */
	@Override
	public void run() {
		while(true) {
			try {
				Socket connection = server.accept();
				//TODO handle connection

			} catch (Exception e) {
				
			}
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
		return port;
	}
	
	@Override
	public String dumpState() {
		return "";
	}
}
