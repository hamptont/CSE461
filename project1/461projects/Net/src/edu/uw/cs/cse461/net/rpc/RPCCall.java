package edu.uw.cs.cse461.net.rpc;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import org.json.JSONException;
import org.json.JSONObject;

import edu.uw.cs.cse461.net.base.NetBase;
import edu.uw.cs.cse461.net.base.NetLoadable.NetLoadableService;
import edu.uw.cs.cse461.net.tcpmessagehandler.TCPMessageHandler;
import edu.uw.cs.cse461.util.Log;

/**
 * Class implementing the caller side of RPC -- the RPCCall.invoke() method.
 * The invoke() method itself is static, for the convenience of the callers,
 * but this class is a normal, loadable, service.
 * <p>
 * <p>
 * This class is responsible for implementing persistent connections. 
 * (What you might think of as the actual remote call code is in RCPCallerSocket.java.)
 * Implementing persistence requires keeping a cache that must be cleaned periodically.
 * We do that using a cleaner thread.
 * 
 * @author zahorjan
 *
 */
public class RPCCall extends NetLoadableService {
	private static final String TAG="RPCCall";
	
	private static Map<String, TCPMessageHandler> persistentConnections =  new HashMap<String, TCPMessageHandler>();

	//-------------------------------------------------------------------------------------------
	//-------------------------------------------------------------------------------------------
	// The static versions of invoke() are just a convenience for caller's -- it
	// makes sure the RPCCall service is actually running, and then invokes the
	// the code that actually implements invoke.
	
	/**
	 * Invokes method() on serviceName located on remote host ip:port.
	 * @param ip Remote host's ip address
	 * @param port RPC service port on remote host
	 * @param serviceName Name of service to be invoked
	 * @param method Name of method of the service to invoke
	 * @param userRequest Arguments to call
	 * @param socketTimeout Maximum time to wait for a response, in msec.
	 * @return Returns whatever the remote method returns.
	 * @throws JSONException
	 * @throws IOException
	 */
	public static JSONObject invoke(
			String ip,				  // ip or dns name of remote host
			int port,                 // port that RPC is listening on on the remote host
			String serviceName,       // name of the remote service
			String method,            // name of that service's method to invoke
			JSONObject userRequest,   // arguments to send to remote method,
			int socketTimeout         // timeout for this call, in msec.
			) throws JSONException, IOException {
		RPCCall rpcCallObj =  (RPCCall)NetBase.theNetBase().getService( "rpccall" );
		if ( rpcCallObj == null ) throw new IOException("RPCCall.invoke() called but the RPCCall service isn't loaded");
		return rpcCallObj._invoke(ip, port, serviceName, method, userRequest, socketTimeout, true);
	}
	
	/**
	 * A convenience implementation of invoke() that doesn't require caller to set a timeout.
	 * The timeout is set to the net.timeout.socket entry from the config file, or 2 seconds if that
	 * doesn't exist.
	 */
	public static JSONObject invoke(
			String ip,				  // ip or dns name of remote host
			int port,                 // port that RPC is listening on on the remote host
			String serviceName,       // name of the remote service
			String method,            // name of that service's method to invoke
			JSONObject userRequest    // arguments to send to remote method,
			) throws JSONException, IOException {
		int socketTimeout  = NetBase.theNetBase().config().getAsInt("net.timeout.socket", 2000);
		return invoke(ip, port, serviceName, method, userRequest, socketTimeout);
	}

	//-------------------------------------------------------------------------------------------
	//-------------------------------------------------------------------------------------------
	
	/**
	 * The infrastructure requires a public constructor taking no arguments.  Plus, we need a constructor.
	 */
	public RPCCall() {
		super("rpccall");
	}

	/**
	 * This private method performs the actual invocation, including the management of persistent connections.
	 * Note that because we may issue the call twice, we  may (a) cause it to be executed twice at the server(!),
	 * and (b) may end up blocking the caller for around twice the timeout specified in the call. (!)
	 * 
	 * @param ip
	 * @param port
	 * @param serviceName
	 * @param method
	 * @param userRequest
	 * @param socketTimeout Max time to wait for this call
	 * @param tryAgain Set to true if you want to repeat call if a socket error occurs; e.g., persistent socket is no good when you use it
	 * @return
	 * @throws JSONException
	 * @throws IOException
	 */
	private JSONObject _invoke(
			String ip,				  // ip or dns name of remote host
			int port,                 // port that RPC is listening on on the remote host
			String serviceName,       // name of the remote service
			String method,            // name of that service's method to invoke
			JSONObject userRequest,   // arguments to send to remote method
			int socketTimeout,        // max time to wait for reply
			boolean tryAgain          // true if an invocation failure on a persistent connection should cause a re-try of the call, false to give up
			) throws JSONException, IOException {
		
		TCPMessageHandler handler = null;
		JSONObject returnValue = null;
		try {
			if (persistentConnections.containsKey(ip+port+serviceName+method)) {
				handler = persistentConnections.get(ip+port+serviceName+method);
			} else {
				//Set up TCPMessage Handler
				Socket socket = new Socket(ip, port);
				handler = new TCPMessageHandler(socket);
				
				//Send connect RPC Message
				JSONObject connectJSON = new RPCMessage().marshall();
				connectJSON.put("action", "connect");
				connectJSON.put("type", "control");
				connectJSON.put("options", new JSONObject().put("connection", "keep-alive"));
								
				RPCMessage connect = RPCMessage.unmarshall(connectJSON.toString());
				
				handler.sendMessage(connect.marshall());
				
				
				//Read Response
				JSONObject connectResponse = handler.readMessageAsJSONObject();
				if(!connectResponse.get("type").equals("OK")) {
				  //handle error
				  throw new IOException("Error Response");
				}
				
				persistentConnections.put(ip+port+serviceName+method, handler);
				socket.setSoTimeout(NetBase.theNetBase().config().getAsInt("rpc.persistence.timeout", 10000));
			}
			
			//Invoke
			JSONObject invokeJSON = new RPCMessage().marshall();
			invokeJSON.put("app", serviceName);
			invokeJSON.put("method", method);
			invokeJSON.put("args", userRequest);
			invokeJSON.put("type", "invoke");
			
			RPCMessage invoke = RPCMessage.unmarshall(invokeJSON.toString());
			
			handler.sendMessage(invoke.marshall());
			
			JSONObject invokeResponse = handler.readMessageAsJSONObject();
			if(!invokeResponse.get("type").equals("OK")) {
			  //handle error
			  throw new IOException("Error Response");
			}
			
			returnValue = invokeResponse.getJSONObject("value");
		} catch (SocketException e){
			persistentConnections.remove(ip+port+serviceName+method);
			handler.close();

			if(tryAgain) {
				returnValue = _invoke(ip, port, serviceName, method, userRequest, socketTimeout, false);
			}
		}
		
		return returnValue;
	}
	
	@Override
	public void shutdown() {
		for(TCPMessageHandler handler : persistentConnections.values()) {
			handler.close();
		}
		persistentConnections = null;
	}
	
	@Override
	public String dumpState() {
		return "Current persistent connections are ...";
	}
}
