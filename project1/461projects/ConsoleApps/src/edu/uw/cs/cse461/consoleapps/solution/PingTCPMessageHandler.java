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
import edu.uw.cs.cse461.net.base.NetLoadable.NetLoadableConsoleApp;
import edu.uw.cs.cse461.net.tcpmessagehandler.TCPMessageHandler;
import edu.uw.cs.cse461.util.SampledStatistic.ElapsedTime;
import edu.uw.cs.cse461.util.SampledStatistic.ElapsedTimeInterval;

public class PingTCPMessageHandler extends NetLoadableConsoleApp implements PingTCPMessageHandlerInterface {
	private static final String TAG="PingTCPMessageHandler";

	public PingTCPMessageHandler() {
		super("PingTCPMessageHandler");
		System.out.println("CONSTRUCTOR");
		Socket s = new Socket();
		try {
			TCPMessageHandler  handler = new TCPMessageHandler(s);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public ElapsedTimeInterval ping(String header, String hostIP, int port, int timeout, int nTrials) throws Exception
	{
		Socket socket = null;	
		boolean socket_timeout = false;
		String response = null;
		
		for (int i = 0; i < nTrials; i++) {
			ElapsedTime.start("PingTCPMessageHandler");
			try {
				socket = new Socket(hostIP, port);
				/*
	            //Sets up socket i/o streams
				BufferedReader in = new BufferedReader(new
			            InputStreamReader(socket.getInputStream()));
				OutputStream out = new DataOutputStream(socket.getOutputStream());
				socket.setSoTimeout(timeout);
				
				//Writes to the server
			//	out.write(header);
				
				//Receives from the server
				response = in.readLine();
				
				//Checks for valid responses
				String headerString = "okay";
				if ( response != null && !response.equalsIgnoreCase(headerString) )
					throw new Exception("Bad header: got '" + response + "', wanted '" + headerString + "'");
				*/
			} catch (SocketTimeoutException e) {
				ElapsedTime.abort("PingTCPMessageHandler");
				socket_timeout = true;
				System.out.println("Socket reading thread caught " + e.getClass().getName() + " exception: " + e.getMessage());
			} catch (SocketException e) {
				socket = null;
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}  catch (Exception e) {
			} finally {
				
				//Handles timer appropriately
				if (!socket_timeout && response != null) {
					ElapsedTime.stop("PingTCPMessageHandler");
				} else if (response == null){
					ElapsedTime.abort("PingTCPMessageHandler");
				} else {
					socket_timeout = false;
				}
				response = null;
				
				//Clean up socket
				if (socket != null){
					try {
						socket.close();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		} 
		return ElapsedTime.get("PingTCPMessageHandler");
	}
	
	@Override
	public void run() throws Exception {
		//TODO PROJECT 2
		System.out.println("RUN :0");
	}
}
