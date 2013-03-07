package edu.uw.cs.cse461.service;

import java.io.IOException;

import org.json.JSONException;
import org.json.JSONObject;

import edu.uw.cs.cse461.net.base.NetBase;
import edu.uw.cs.cse461.net.rpc.RPCCallableMethod;
import edu.uw.cs.cse461.net.rpc.RPCService;
import edu.uw.cs.cse461.util.Base64;

public class DataXferRPCService extends DataXferServiceBase {  
	
	/**
	 * Key used for EchoRPC's header, in the args of an RPC call.
	 * The header element is a string (EchoServiceBase.HEADER_STR).
	 */
	public static final String HEADER_KEY = "header";
	public static final String HEADER_TAG_KEY = "tag";
	public static final String HEADER_TAG = "xfer";
	public static final String XFER_LEN = "xferLength";
	public static final String HEADER_OKAY = "okay";
	public static final String HEADER_DATA = "data";
	
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
		//check header
		JSONObject header = args.getJSONObject(DataXferRPCService.HEADER_KEY);

		if( header == null  || !header.has(HEADER_TAG_KEY) || !header.getString(HEADER_TAG_KEY).equalsIgnoreCase(HEADER_STR) ) {
			System.out.println("Invalid Header");
			throw new Exception("Missing or incorrect header value: '" + header + "'");
		}
		
		if(!header.has(XFER_LEN)) {
			System.out.println("header missing xferlength field");
			throw new Exception ("Header missing xferLength field");
		}
		
		int xferLength = header.getInt(XFER_LEN);	
		
		//create response
		JSONObject header_json = new JSONObject();
		header_json.put(HEADER_TAG_KEY, HEADER_OKAY);
		header_json.put(XFER_LEN, xferLength);
		
		byte[] raw_bytes = new byte[xferLength];
		
		JSONObject json_response = new JSONObject();
		json_response.put(HEADER_KEY, header_json);
		json_response.put(HEADER_DATA, Base64.encodeBytes(raw_bytes));
		return json_response; 
	}
}
