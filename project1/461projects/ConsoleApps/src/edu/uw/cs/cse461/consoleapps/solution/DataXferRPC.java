package edu.uw.cs.cse461.consoleapps.solution;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import org.json.JSONException;
import org.json.JSONObject;

import edu.uw.cs.cse461.consoleapps.DataXferInterface.DataXferRPCInterface;
import edu.uw.cs.cse461.net.base.NetBase;
import edu.uw.cs.cse461.net.base.NetLoadable.NetLoadableConsoleApp;
import edu.uw.cs.cse461.net.rpc.RPCCall;
import edu.uw.cs.cse461.service.DataXferRPCService;
import edu.uw.cs.cse461.util.Base64;
import edu.uw.cs.cse461.util.ConfigManager;
import edu.uw.cs.cse461.util.SampledStatistic.ElapsedTime;
import edu.uw.cs.cse461.util.SampledStatistic.TransferRate;
import edu.uw.cs.cse461.util.SampledStatistic.TransferRateInterval;


public class DataXferRPC extends NetLoadableConsoleApp implements DataXferRPCInterface {
	private static final String TAG="DataXferRPC";
	
	// ConsoleApp's must have a constructor taking no arguments
	public DataXferRPC() {
		super("DataXferRPC");
	}
	
	@Override
	public void run() {
		try {
			// Eclipse doesn't support System.console()
			BufferedReader console = new BufferedReader(new InputStreamReader(System.in));
			ConfigManager config = NetBase.theNetBase().config();

			try {
				//Get IP address
				String targetIP = config.getProperty("net.server.ip");
				if ( targetIP == null ) {
					System.out.println("No net.server.ip entry in config file.");
					System.out.print("Enter the server's ip, or empty line to exit: ");
					targetIP = console.readLine();
					if ( targetIP == null || targetIP.trim().isEmpty() ) return;
				}

				//Get port number
				int port;
				System.out.print("Enter the server's RPC port, or empty line to exit: ");
				String targetPortStr = console.readLine();
				if(targetPortStr == null || targetPortStr.trim().isEmpty()) {
					port = 0;
				} else {
					port = Integer.parseInt(targetPortStr);
				}

				//Get number of trials
				System.out.print("Enter number of trials: ");
				String trialStr = console.readLine();
				int nTrials = Integer.parseInt(trialStr);

				//Get number of bytes to send
				System.out.print("Enter number of bytes to transfer: ");
				String byteStr = console.readLine();
				int xferLength = Integer.parseInt(byteStr);
				
				//Get timeout 
				int socketTimeout = config.getAsInt("net.timeout.socket", 5000);
				
				System.out.println("Host: " + targetIP);
				System.out.println("port: " + port);
				System.out.println("trials: " + nTrials);
				
				TransferRateInterval result = null;

				//Run xfer tests
				if(port != 0) {
					JSONObject header = new JSONObject();
					header.put(DataXferRPCService.HEADER_TAG_KEY, DataXferRPCService.HEADER_TAG);
					header.put(DataXferRPCService.XFER_LEN, xferLength);
					ElapsedTime.clear();
					result = DataXferRate(header, targetIP, port, socketTimeout, nTrials); 					
				}

				//Print results
				if(result != null) {
					System.out.println("DataXferRPC: " + String.format("%.2f msec (%d failures)", result.mean(), result.nAborted()));
				}
			} catch (Exception e) {
				System.out.println("Exception caught: " + e.getMessage());
			} 
		} catch (Exception e) {
			System.out.println("DataXferRPC.run() caught exception: " + e.getMessage());
		}
	}

	@Override
	public TransferRateInterval DataXferRate(JSONObject header, String hostIP, int port, int timeout, int nTrials) {
		int xferLength;
		try {
			xferLength = header.getInt(DataXferRPCService.XFER_LEN);
		} catch (JSONException e1) {
			xferLength = 0; //bad header -- unknown xferLength
		}
		for(int i = 0; i < nTrials; i++) {
			boolean abortTimer = false;
			try {
				TransferRate.start("DataXferRPC");
				byte[] response = DataXfer(header, hostIP, port, timeout);
				if(response.length != xferLength) {
					abortTimer = true;
				}
			} catch (JSONException e) {
				abortTimer = true;
			} catch (IOException e) {
				abortTimer = true;
			} finally {
				if(!abortTimer) {
					TransferRate.stop("DataXferRPC", xferLength);
				} else {
					TransferRate.abort("DataXferRPC", xferLength);
				}
			}
		}
		return TransferRate.get("DataXferRPC");
	}	
	
	@Override
	public byte[] DataXfer(JSONObject header, String hostIP, int port, int timeout) throws JSONException, IOException {
		JSONObject args = new JSONObject();
		args.put(DataXferRPCService.HEADER_KEY, header);
		
		JSONObject response = RPCCall.invoke(hostIP, port, "dataxferrpc", "xfer", args, timeout);
		
		byte[] response_bytes = Base64.decode(response.getString(DataXferRPCService.HEADER_DATA));

		return response_bytes;
	}
}
