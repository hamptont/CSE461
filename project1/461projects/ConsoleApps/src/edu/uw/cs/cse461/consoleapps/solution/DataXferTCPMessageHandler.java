package edu.uw.cs.cse461.consoleapps.solution;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.SocketTimeoutException;

import org.json.JSONException;
import org.json.JSONObject;

import edu.uw.cs.cse461.consoleapps.DataXferInterface.DataXferTCPMessageHandlerInterface;
import edu.uw.cs.cse461.net.base.NetBase;
import edu.uw.cs.cse461.net.base.NetLoadable.NetLoadableConsoleApp;
import edu.uw.cs.cse461.net.tcpmessagehandler.TCPMessageHandler;
import edu.uw.cs.cse461.service.DataXferRawService;
import edu.uw.cs.cse461.service.DataXferServiceBase;
import edu.uw.cs.cse461.service.EchoServiceBase;
import edu.uw.cs.cse461.util.ConfigManager;
import edu.uw.cs.cse461.util.SampledStatistic.TransferRate;
import edu.uw.cs.cse461.util.SampledStatistic.TransferRateInterval;

public class DataXferTCPMessageHandler extends NetLoadableConsoleApp implements DataXferTCPMessageHandlerInterface{
	private static final String TAG="DataXferTCPMessageHandler";

	public DataXferTCPMessageHandler() {
		super("DataXferTCPMessageHandler");
	}
	
	public byte[] DataXfer(String header, String hostIP, int port, int timeout, int xferLength) throws JSONException, IOException {		
		Socket tcpSocket = null;
		TCPMessageHandler tcpMessageHandlerSocket = null;
		StringBuffer buf = new StringBuffer();

		try {
			tcpSocket = new Socket(hostIP, port);
			tcpMessageHandlerSocket = new TCPMessageHandler(tcpSocket);
			tcpMessageHandlerSocket.setTimeout(timeout);
			tcpMessageHandlerSocket.setNoDelay(true);

			tcpMessageHandlerSocket.sendMessage(header);
			
			JSONObject json = new JSONObject();
			json.put("transferSize", new Integer(xferLength));
			tcpMessageHandlerSocket.sendMessage(json);
						
			// read response header
			String headerStr = tcpMessageHandlerSocket.readMessageAsString();
			if (!headerStr.equalsIgnoreCase("OKAY")) {
				throw new Exception("Bad response header: '" + headerStr + "'");
			}
			
			String responseStr = tcpMessageHandlerSocket.readMessageAsString();
			buf.append(responseStr);
			int charsRead = responseStr.length();
			int count = charsRead;
			while(charsRead > 0 && count < xferLength){
				responseStr = tcpMessageHandlerSocket.readMessageAsString();
				charsRead = responseStr.length();
				count += charsRead;
				buf.append(responseStr);
			}
			if (count != xferLength) {
				throw new Exception("Bad response payload: expected " + xferLength + "bytes, received " + charsRead + " bytes.");
			}
		} catch (SocketTimeoutException e) {
			System.out.println("Timed out");
		} catch (Exception e) {
			System.out.println("TCPMessageHandler read failed: " + e.getMessage());
		} finally {
			if(tcpMessageHandlerSocket != null){
				try{
					tcpMessageHandlerSocket.close();
				}catch (Exception e) {
					
				}
			}
		}
		
		return buf.toString().getBytes();
	//	return response;
	}
	
	public TransferRateInterval DataXferRate(String header, String hostIP, int port, int timeout, int xferLength, int nTrials) {		
		for(int i = 0; i < nTrials; i++) {
			try {
				TransferRate.start("DataXferRate");
				byte[] msg = DataXfer(header, hostIP, port, timeout, xferLength);
				if(msg.length == xferLength){
					TransferRate.stop("DataXferRate", xferLength);
				}else{
					TransferRate.abort("DataXferRate", xferLength);
				}
			} catch ( java.net.SocketTimeoutException e) {
				TransferRate.abort("DataXferRate", xferLength);
			} catch (Exception e) {
				TransferRate.abort("DataXferRate", xferLength);
			}
		}
		return TransferRate.get("DataXferRate");
	}
	
	@Override
	public void run() throws Exception {
		try {
			// Eclipse doesn't support System.console()
			BufferedReader console = new BufferedReader(new InputStreamReader(System.in));

			ConfigManager config = NetBase.theNetBase().config();
			String server = config.getProperty("net.server.ip");
			if ( server == null ) {
				System.out.print("Enter a host ip, or exit to exit: ");
				server = console.readLine();
				if ( server == null ) return;
				if ( server.equals("exit")) return;
			}

			int basePort = config.getAsInt("dataxferraw.server.baseport", -1);
			if ( basePort == -1 ) {
				System.out.print("Enter port number, or empty line to exit: ");
				String portStr = console.readLine();
				if ( portStr == null || portStr.trim().isEmpty() ) return;
				basePort = Integer.parseInt(portStr);
			} else {
				basePort += 1000;
			}
						
			int socketTimeout = config.getAsInt("net.timeout.socket", -1);
			if ( socketTimeout < 0 ) {
				System.out.print("Enter socket timeout (in msec.): ");
				String timeoutStr = console.readLine();
				socketTimeout = Integer.parseInt(timeoutStr);
				
			}
			
			System.out.print("Enter the XferLength: ");
			String lengthStr = console.readLine();
			int xferLength = Integer.parseInt(lengthStr);

			System.out.print("Enter number of trials: ");
			String trialStr = console.readLine();
			int nTrials = Integer.parseInt(trialStr);

			TransferRate.clear();
			
			int port = basePort;

			System.out.println("\n" + xferLength + " bytes");


			//-----------------------------------------------------
			// TCP transfer
			//-----------------------------------------------------

			TransferRateInterval tcpStats = DataXferRate("xfer", server, port, socketTimeout, xferLength, nTrials);

			System.out.println("\nTCP: xfer rate = " + String.format("%9.0f", tcpStats.mean() * 1000.0) + " bytes/sec.");
			System.out.println("TCP: failure rate = " + String.format("%5.1f", tcpStats.failureRate()) +
					           " [" + tcpStats.nAborted()+ "/" + tcpStats.nTrials() + "]");

	
		} catch (Exception e) {
			System.out.println("Unanticipated exception: " + e.getMessage());
		}
	}
}
