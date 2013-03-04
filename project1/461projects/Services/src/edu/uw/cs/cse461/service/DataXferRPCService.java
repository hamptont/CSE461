package edu.uw.cs.cse461.service;

import java.io.IOException;

import org.json.JSONException;
import org.json.JSONObject;

import edu.uw.cs.cse461.net.base.NetBase;
import edu.uw.cs.cse461.net.rpc.RPCCallableMethod;
import edu.uw.cs.cse461.net.rpc.RPCService;

public class DataXferRPCService extends DataXferServiceBase {  
	
	/**
	 * Key used for EchoRPC's header, in the args of an RPC call.
	 * The header element is a string (EchoServiceBase.HEADER_STR).
	 */
	public static final String HEADER_KEY = "header";
	public static final String HEADER_TAG_KEY = "tag";
	public static final String HEADER_TAG = "xfer";
	public static final String XFER_LEN = "xferLength";
	
	/**
	 * Key used for EchoRPC's payload, in the args of an RPC call
	 */
	public static final String PAYLOAD_KEY = "payload";
	
	// A variable capable of describing a method that can be invoked by RPC.
	private RPCCallableMethod xfer;
	
	/**
	 * The constructor registers RPC-callable methods with the RPCService.
	 * @throws IOException
	 * @throws NoSuchMethodException
	 */
	public DataXferRPCService() throws Exception {	
		super("dataxferrpc");
		
		// Set up the method descriptor variable to refer to this->_echo()
		xfer = new RPCCallableMethod(this, "_xfer");
		// Register the method with the RPC service as externally invocable method "echo"
		((RPCService)NetBase.theNetBase().getService("rpc")).registerHandler(loadablename(), "xfer", xfer );
	}
	
	/**
	 * This method is callable by RPC (because of the actions taken by the constructor).
	 * All RPC-callable methods take a JSONObject as their single parameter, and return
	 * a JSONObject.  (The return value can be null.)  
	 * @param args
	 * @return
	 * @throws JSONException
	 */
	public JSONObject _xfer(JSONObject args) throws Exception {
		// TODO Implement method for _xfer 
		
		/*
		// check header
		JSONObject header = args.getJSONObject(DataXferRPCService.HEADER_KEY);
		System.out.println("args: " + args.toString());
		if ( header == null  || !header.has(HEADER_TAG_KEY) || !header.getString(HEADER_TAG_KEY).equalsIgnoreCase(HEADER_STR) )
		//	throw new Exception("Missing or incorrect header value: '" + header + "'");
		
		header.put(HEADER_TAG_KEY, RESPONSE_OKAY_STR);
		return args;
		*/
		return null; 
	}
}
