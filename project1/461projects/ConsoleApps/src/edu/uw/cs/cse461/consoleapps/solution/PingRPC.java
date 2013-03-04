package edu.uw.cs.cse461.consoleapps.solution;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import org.json.JSONObject;

import edu.uw.cs.cse461.consoleapps.PingInterface.PingRPCInterface;
import edu.uw.cs.cse461.consoleapps.PingInterface.PingRawInterface;
import edu.uw.cs.cse461.net.base.NetBase;
import edu.uw.cs.cse461.net.base.NetLoadable.NetLoadableConsoleApp;
import edu.uw.cs.cse461.net.rpc.RPCCall;
import edu.uw.cs.cse461.service.EchoRPCService;
import edu.uw.cs.cse461.service.EchoServiceBase;
import edu.uw.cs.cse461.util.ConfigManager;
import edu.uw.cs.cse461.util.Log;
import edu.uw.cs.cse461.util.SampledStatistic.ElapsedTime;
import edu.uw.cs.cse461.util.SampledStatistic.ElapsedTimeInterval;

/**
 * Raw sockets version of ping client.
 * @author zahorjan
 *
 */
public class PingRPC extends NetLoadableConsoleApp implements PingRPCInterface {
	private static final String TAG="PingRCP";
	
	// ConsoleApp's must have a constructor taking no arguments
	public PingRPC() {
		super("pingrpc");
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

				//Get timeout 
				int socketTimeout = config.getAsInt("net.timeout.socket", 5000);
				
				System.out.println("Host: " + targetIP);
				System.out.println("port: " + port);
				System.out.println("trials: " + nTrials);
				
				ElapsedTimeInterval result = null;

				//Run ping tests
				if(port != 0) {
					JSONObject header = new JSONObject();
					header.put(EchoRPCService.HEADER_TAG_KEY, EchoRPCService.HEADER_STR);
					ElapsedTime.clear();
					result = ping(header, targetIP, port, socketTimeout, nTrials); 					
				}

				//Print results
				if(result != null) {
					System.out.println("PingRPC: " + String.format("%.2f msec (%d failures)", result.mean(), result.nAborted()));
				}
			} catch (Exception e) {
				System.out.println("Exception: " + e.getMessage());
			} 
		} catch (Exception e) {
			System.out.println("PingRPC.run() caught exception: " +e.getMessage());
		}
	}

	@Override
	public ElapsedTimeInterval ping(JSONObject header, String hostIP, int port, int timeout, int nTrials) throws Exception {
		for(int i = 0; i < nTrials; i++) {
			boolean abortTimer = false;
			try {
				ElapsedTime.start("PingRPC");
	
				JSONObject args = new JSONObject();
				args.put(EchoRPCService.HEADER_KEY, header);
				args.put(EchoRPCService.PAYLOAD_KEY, "");
				
				JSONObject response = RPCCall.invoke(hostIP, port, "echorpc", "echo", args, timeout);
				
				if(response == null) {
					System.out.println("NULL RESPONSE");
					abortTimer = true;
				}

				//Check for valid header (should be 'OKAY')
				String header_response = response.getString(EchoRPCService.HEADER_KEY);
				if(!header_response.equalsIgnoreCase(EchoRPCService.RESPONSE_OKAY_STR)) {
					abortTimer  = true;
				}
				
				//Check for valid payload (should be "")
				String payload_reponse = response.getString(EchoRPCService.PAYLOAD_KEY);
				if(!payload_reponse.equalsIgnoreCase("")) {
					abortTimer  = true;
				}
				
			} catch (Exception e) {
				abortTimer = true;
			}
			if(abortTimer) {
				ElapsedTime.abort("PingRPC");
			} else {
				ElapsedTime.stop("PingRPC");
			}
		}
		return ElapsedTime.get("PingRPC");
	}	
}
