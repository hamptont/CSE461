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

import edu.uw.cs.cse461.consoleapps.PingInterface.PingRawInterface;
import edu.uw.cs.cse461.net.base.NetBase;
import edu.uw.cs.cse461.net.base.NetLoadable.NetLoadableConsoleApp;
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
public class PingRaw extends NetLoadableConsoleApp implements PingRawInterface {
	private static final String TAG="PingRaw";
	
	// ConsoleApp's must have a constructor taking no arguments
	public PingRaw() {
		super("pingraw");
	}
	
	/* (non-Javadoc)
	 * @see edu.uw.cs.cse461.ConsoleApps.PingInterface#run()
	 */
	@Override
	public void run() {
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

				int targetUDPPort;
				System.out.print("Enter the server's UDP port, or empty line to skip: ");
				String targetUDPPortStr = console.readLine();
				if ( targetUDPPortStr == null || targetUDPPortStr.trim().isEmpty() ) return;
				targetUDPPort = Integer.parseInt(targetUDPPortStr);

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
				System.out.println("udp port: " + targetUDPPort);
				System.out.println("tcp port: " + targetTCPPort);
				System.out.println("trials: " + nTrials);
				
				ElapsedTimeInterval udpResult = null;
				ElapsedTimeInterval tcpResult = null;

				if ( targetUDPPort != 0  ) {
					ElapsedTime.clear();
					// we rely on knowing the implementation of udpPing here -- we throw
					// away the return value because we'll print the ElaspedTime stats
					udpResult = udpPing(EchoServiceBase.HEADER_BYTES, targetIP, targetUDPPort, socketTimeout, nTrials);
				}

				if ( targetTCPPort != 0 ) {
					ElapsedTime.clear();
					tcpResult = tcpPing(EchoServiceBase.HEADER_BYTES, targetIP, targetTCPPort, socketTimeout, nTrials);
				}

				if ( udpResult != null ) System.out.println("UDP: " + String.format("%.2f msec (%d failures)", udpResult.mean(), udpResult.nAborted()));
				if ( tcpResult != null ) System.out.println("TCP: " + String.format("%.2f msec (%d failures)", tcpResult.mean(), tcpResult.nAborted()));

			} catch (Exception e) {
				System.out.println("Exception: " + e.getMessage());
			} 
		} catch (Exception e) {
			System.out.println("PingRaw.run() caught exception: " +e.getMessage());
		}
	}
	

	/**
	 * Pings the host/port named by the arguments the number of times named by the arguments.
	 * Returns the mean ping time of the trials.
	 * @throws SocketException 
	 */
	@Override
	public ElapsedTimeInterval udpPing(byte[] header, String hostIP, int udpPort, int socketTimeout, int nTrials){
		DatagramSocket socket = null;
		DatagramPacket packet = null;

		for (int i = 0; i < nTrials; i++) {
			ElapsedTime.start("PingRaw_UDPTotalDelay");
			try {
				socket = new DatagramSocket();

				//Sends the ping packet to the server
				InetSocketAddress address = new InetSocketAddress(hostIP, udpPort);
				packet = new DatagramPacket(header, header.length, address.getAddress(), udpPort);
				socket.send(packet);
				
	            //Receives the response from the server.
				byte[] buf = new byte[4];
				packet = new DatagramPacket(buf, buf.length);
				socket.setSoTimeout(socketTimeout);
				socket.receive(packet);
				
				//Checks for valid responses
				String headerString = "okay";
				if ( packet.getLength() < headerString.length() )
					throw new Exception("Bad header: length = " + packet.getLength());
				String headerStr = new String( buf, 0, headerString.length() );
				if ( ! headerStr.equalsIgnoreCase(headerString) )
					throw new Exception("Bad header: got '" + headerStr + "', wanted '" + headerString + "'");
				
			} catch (SocketTimeoutException e) {
				ElapsedTime.abort("PingRaw_UDPTotalDelay");
				packet = null;
				System.out.println("Dgram reading thread caught " + e.getClass().getName() + " exception: " + e.getMessage());
			} catch (SocketException e) {
				socket = null;
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (Exception e) {
				System.out.println("Dgram reading thread caught " + e.getClass().getName() + " exception: " + e.getMessage());
			} finally {
				
				//Successfully timed packet.
				if(packet != null) {
					ElapsedTime.stop("PingRaw_UDPTotalDelay");
				}
				
				//As long the socket still exists, close it.
				if (socket != null){
					socket.close();
				}
			}
		} 
		return ElapsedTime.get("PingRaw_UDPTotalDelay");
	}
	
	@Override
	public ElapsedTimeInterval tcpPing(byte[] header, String hostIP, int tcpPort, int socketTimeout, int nTrials) {
		Socket socket = null;	
		boolean timeout = false;
		String response = null;
		
		for (int i = 0; i < nTrials; i++) {
			ElapsedTime.start("PingRaw_TCPTotalDelay");
			try {
				socket = new Socket(hostIP, tcpPort);
				
	            //Sets up socket i/o streams
				BufferedReader in = new BufferedReader(new
			            InputStreamReader(socket.getInputStream()));
				OutputStream out = new DataOutputStream(socket.getOutputStream());
				socket.setSoTimeout(socketTimeout);
				
				//Writes to the server
				out.write(header);
				
				//Receives from the server
				response = in.readLine();
				
				//Checks for valid responses
				String headerString = "okay";
				if ( response != null && !response.equalsIgnoreCase(headerString) )
					throw new Exception("Bad header: got '" + response + "', wanted '" + headerString + "'");
			} catch (SocketTimeoutException e) {
				ElapsedTime.abort("PingRaw_TCPTotalDelay");
				timeout = true;
				System.out.println("Socket reading thread caught " + e.getClass().getName() + " exception: " + e.getMessage());
			} catch (SocketException e) {
				socket = null;
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}  catch (Exception e) {
			} finally {
				
				//Handles timer appropriately
				if (!timeout && response != null) {
					ElapsedTime.stop("PingRaw_TCPTotalDelay");
				} else if (response == null){
					ElapsedTime.abort("PingRaw_TCPTotalDelay");
				} else {
					timeout = false;
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
		return ElapsedTime.get("PingRaw_TCPTotalDelay");
	}
}
