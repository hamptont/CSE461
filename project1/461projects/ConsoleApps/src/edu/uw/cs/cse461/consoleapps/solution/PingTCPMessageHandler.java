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
		System.out.println("Ping TCP Message Handler - CONSTRUCTOR");
	}

	public ElapsedTimeInterval ping(String header, String hostIP, int port, int timeout, int nTrials) throws Exception
	{
		Socket socket = null;	
		boolean socket_timeout = false;
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
				System.out.println("!!!");
				response = handler.readMessageAsString();
				System.out.println("!!!!");
				System.out.println("response length: " + response.length());
				System.out.println("response: " + response);
			} catch (SocketTimeoutException e) {
				System.out.println("TIME OUT!");
				ElapsedTime.abort("PingTCPMessageHandler");
				socket_timeout = true;
				System.out.println("Socket reading thread caught " + e.getClass().getName() + " exception: " + e.getMessage());
			} catch (SocketException e) {
				System.out.println("socket exception");
				socket = null;
			} catch (IOException e) {
				e.printStackTrace();
			}  catch (Exception e) {
				
			} finally {				
				//Handles timer appropriately
				if (!socket_timeout) {
					ElapsedTime.stop("PingTCPMessageHandler");
			//	} else if (response.length != 0){
			//		ElapsedTime.abort("PingTCPMessageHandler");
				} else {
					socket_timeout = false;
				}
				response = null;
				//Clean up socket
				if (socket != null){
					try {
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
		//TODO PROJECT 2
		System.out.println("RUN :0");
	}
}
