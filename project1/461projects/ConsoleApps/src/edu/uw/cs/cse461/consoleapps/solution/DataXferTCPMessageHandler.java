package edu.uw.cs.cse461.consoleapps.solution;

import java.io.IOException;
import org.json.JSONException;
import edu.uw.cs.cse461.consoleapps.DataXferInterface.DataXferTCPMessageHandlerInterface;
import edu.uw.cs.cse461.net.base.NetLoadable.NetLoadableConsoleApp;
import edu.uw.cs.cse461.util.SampledStatistic.TransferRateInterval;

public class DataXferTCPMessageHandler extends NetLoadableConsoleApp implements DataXferTCPMessageHandlerInterface{
	private static final String TAG="DataXferTCPMessageHandler";

	public DataXferTCPMessageHandler() {
		super("DataXferTCPMessageHandler");
	}
	
	public byte[] DataXfer(String header, String hostIP, int port, int timeout, int xferLength) throws JSONException, IOException {
		return null;
	}
	
	public TransferRateInterval DataXferRate(String header, String hostIP, int port, int timeout, int xferLength, int nTrials) {
		return null;
	}
	
	@Override
	public void run() throws Exception {
		//TODO PROJECT 2
	}
}
