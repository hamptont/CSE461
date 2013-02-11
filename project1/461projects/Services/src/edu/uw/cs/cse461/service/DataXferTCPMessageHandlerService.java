package edu.uw.cs.cse461.service;

import edu.uw.cs.cse461.net.base.NetLoadableInterface.NetLoadableServiceInterface;
import edu.uw.cs.cse461.util.Log;

public class DataXferTCPMessageHandlerService extends DataXferServiceBase implements NetLoadableServiceInterface {
	private static final String TAG="DataXferTCPMessageHandlerService";
	
	protected DataXferTCPMessageHandlerService(String loadablename) throws Exception {
		super("DataXferTCPMessageHandlerService");
		
		//TODO Project 2
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
