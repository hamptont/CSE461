package edu.uw.cs.cse461.consoleapps.solution;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import edu.uw.cs.cse461.consoleapps.PingInterface.PingTCPMessageHandlerInterface;
import edu.uw.cs.cse461.net.base.NetBase;
import edu.uw.cs.cse461.net.base.NetLoadable.NetLoadableConsoleApp;
import edu.uw.cs.cse461.net.tcpmessagehandler.TCPMessageHandler;
import edu.uw.cs.cse461.service.EchoServiceBase;
import edu.uw.cs.cse461.util.ConfigManager;
import edu.uw.cs.cse461.util.SampledStatistic.ElapsedTime;
import edu.uw.cs.cse461.util.SampledStatistic.ElapsedTimeInterval;

public class PingTCPMessageHandler extends NetLoadableConsoleApp implements PingTCPMessageHandlerInterface {
	private static final String TAG="PingTCPMessageHandler";

	public PingTCPMessageHandler() {
		super("PingTCPMessageHandler");
	}

	public ElapsedTimeInterval ping(String header, String hostIP, int port, int timeout, int nTrials) throws Exception
	{
		Socket socket = null;	
		boolean socket_timeout = false;
		boolean bad_header = false;
		boolean exception_thrown = false;
		String response = null;
		
		TCPMessageHandler handler = null;
		
		for (int i = 0; i < nTrials; i++) {
			ElapsedTime.start("PingTCPMessageHandler");
			try {
				socket = new Socket(hostIP, port);
				handler = new TCPMessageHandler(socket);
				handler.setTimeout(timeout);
				
				handler.sendMessage(header);		
				handler.sendMessage("");
				
				response = handler.readMessageAsString();
				if(!response.equals("okay")) {
					bad_header = true;
				}

			} catch (SocketTimeoutException e) {
				ElapsedTime.abort("PingTCPMessageHandler");
				socket_timeout = true;
				System.out.println("Socket reading thread caught " + e.getClass().getName() + " exception: " + e.getMessage());
			} catch (SocketException e) {
				System.out.println("socket exception");
				socket = null;
			} catch (IOException e) {
				e.printStackTrace();
			}  catch (Exception e) {
				exception_thrown = true;
				
			} finally {				
				//Handles timer appropriately
				if (!socket_timeout && !bad_header && !exception_thrown) {
					ElapsedTime.stop("PingTCPMessageHandler");
				} else {
					ElapsedTime.abort("PingTCPMessageHandler");
				}
				
				exception_thrown = false;
				socket_timeout = false;
				bad_header = false;
				response = null;
				//Clean up socket
				if (socket != null){
					try {
						System.out.println("CLOSE!!");
						socket.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		} 
		return ElapsedTime.get("PingTCPMessageHandler");
	}
	
	@Override
	public void run() throws Exception {
		try {
			// Eclipse doesn't support System.console()
			BufferedReader console = new BufferedReader(new InputStreamReader(System.in));
			ConfigManager config = NetBase.theNetBase().config();

			try {

				String targetIP = config.getProperty("net.server.ip");
				if ( targetIP == null ) {
					System.out.println("No net.server.ip entry in config file.");
					System.out.print("Enter the server's ip, or empty line to exit: ");
					targetIP = console.readLine();
					if ( targetIP == null || targetIP.trim().isEmpty() ) return;
				}

				int targetTCPPort;
				System.out.print("Enter the server's TCP port, or empty line to skip: ");
				String targetTCPPortStr = console.readLine();
				if ( targetTCPPortStr == null || targetTCPPortStr.trim().isEmpty() ) targetTCPPort = 0;
				else targetTCPPort = Integer.parseInt(targetTCPPortStr);

				System.out.print("Enter number of trials: ");
				String trialStr = console.readLine();
				int nTrials = Integer.parseInt(trialStr);

				int socketTimeout = config.getAsInt("net.timeout.socket", 5000);
				
				System.out.println("Host: " + targetIP);
				System.out.println("tcp port: " + targetTCPPort);
				System.out.println("trials: " + nTrials);
				
				ElapsedTimeInterval tcpResult = null;

				if ( targetTCPPort != 0 ) {
					ElapsedTime.clear();
					tcpResult = ping(EchoServiceBase.HEADER_STR, targetIP, targetTCPPort, socketTimeout, nTrials);
				}

				if ( tcpResult != null ) System.out.println("TCP: " + String.format("%.2f msec (%d failures)", tcpResult.mean(), tcpResult.nAborted()));

			} catch (Exception e) {
				System.out.println("Exception: " + e.getMessage());
			} 
		} catch (Exception e) {
			System.out.println("PingRaw.run() caught exception: " +e.getMessage());
		}
	}
}
