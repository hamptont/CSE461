package edu.uw.cs.cse461.net.tcpmessagehandler;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;



/**
 * Sends/receives a message over an established TCP connection.
 * To be a message means the unit of write/read is demarcated in some way.
 * In this implementation, that's done by prefixing the data with a 4-byte
 * length field.
 * <p>
 * Design note: TCPMessageHandler cannot usefully subclass Socket, but rather must
 * wrap an existing Socket, because servers must use ServerSocket.accept(), which
 * returns a Socket that must then be turned into a TCPMessageHandler.
 *  
 * @author zahorjan
 *
 */
public class TCPMessageHandler implements TCPMessageHandlerInterface {
	private static final String TAG="TCPMessageHandler";
	
	private Socket socket; 
	private int timeout;
	private boolean noDelay;
	private int maxReadLength;
	private InputStream in;
	
	//--------------------------------------------------------------------------------------
	// helper routines
	//--------------------------------------------------------------------------------------

	/**
	 * We need an "on the wire" format for a binary integer.
	 * This method encodes into that format, which is little endian
	 * (low order bits of int are in element [0] of byte array, etc.).
	 * @param i
	 * @return A byte[4] encoding the integer argument.
	 */
	protected static byte[] intToByte(int i) {
		ByteBuffer b = ByteBuffer.allocate(4);
		b.order(ByteOrder.LITTLE_ENDIAN);
		b.putInt(i);
		byte buf[] = b.array();
		return buf;
	}
	
	/**
	 * We need an "on the wire" format for a binary integer.
	 * This method decodes from that format, which is little endian
	 * (low order bits of int are in element [0] of byte array, etc.).
	 * @param buf
	 * @return 
	 */
	protected static int byteToInt(byte buf[]) {
		ByteBuffer b = ByteBuffer.wrap(buf);
		b.order(ByteOrder.LITTLE_ENDIAN);
		int ans = b.getInt();
		return ans;
	}

	/**
	 * Constructor, associating this TCPMessageHandler with a connected socket.
	 * @param sock
	 * @throws IOException
	 */
	public TCPMessageHandler(Socket sock) throws IOException {
		this.socket = sock; 
		this.timeout = 1000;
		this.noDelay = true;
		this.maxReadLength = 1000;
		this.in = this.socket.getInputStream();
	}
	
	/**
	 * Closes the underlying socket and renders this TCPMessageHandler useless.
	 */
	public void close() {
		if(this.socket != null) {
			try {
				this.socket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * Set the read timeout on the underlying socket.
	 * @param timeout Time out, in msec.
	 * @return The previous time out.
	 */
	@Override
	public int setTimeout(int timeout) throws SocketException {
		int old_timeout = this.timeout;
		this.timeout = timeout;
		this.socket.setSoTimeout(this.timeout);
		return old_timeout;
	}
	
	/**
	 * Enable/disable TCPNoDelay on the underlying TCP socket.
	 * @param value The value to set
	 * @return The old value
	 */
	@Override
	public boolean setNoDelay(boolean value) throws SocketException {
		boolean old_noDelay = this.noDelay;
		this.noDelay = value;
		this.socket.setTcpNoDelay(this.noDelay);
		return old_noDelay;
	}
	
	/**
	 * Sets the maximum allowed size for which decoding of a message will be attempted.
	 * @return The previous setting of the maximum allowed message length.
	 */
	@Override
	public int setMaxReadLength(int maxLen) {
		int old_maxReadLength = this.maxReadLength;
		this.maxReadLength = maxLen;
		return old_maxReadLength;
	}

	/**
	 * Returns the current setting for the maximum read length
	 */
	@Override
	public int getMaxReadLength() {
		return this.maxReadLength;
	}
	
	//--------------------------------------------------------------------------------------
	// send routines
	//--------------------------------------------------------------------------------------
	
	@Override
	public void sendMessage(byte[] buf) throws IOException {		
		//Set up socket and streams
		OutputStream out = new DataOutputStream(this.socket.getOutputStream());
		this.socket.setSoTimeout(this.timeout);
		this.socket.setTcpNoDelay(this.noDelay);
		
		byte[] len = intToByte(buf.length);
		
		out.write(len);
		out.write(buf);
		out.flush();
	}
	
	/**
	 * Uses str.getBytes() for conversion.
	 */
	@Override
	public void sendMessage(String str) throws IOException {
		byte[] buf = str.getBytes("UTF-8");
		sendMessage(buf);
	}

	/**
	 * We convert the int to the one the wire format and send as bytes.
	 */
	@Override
	public void sendMessage(int value) throws IOException{
		byte[] buf = intToByte(value); 
		sendMessage(buf);
	}
	
	/**
	 * Sends JSON string representation of the JSONArray.
	 */
	@Override
	public void sendMessage(JSONArray jsArray) throws IOException {
		byte[] buf = jsArray.toString().getBytes("UTF-8"); 
		sendMessage(buf);
	}
	
	/**
	 * Sends JSON string representation of the JSONObject.
	 */
	@Override
	public void sendMessage(JSONObject jsObject) throws IOException {
		byte[] buf = jsObject.toString().getBytes("UTF-8");
		sendMessage(buf);
	}
	
	//--------------------------------------------------------------------------------------
	// read routines
	//   All of these invert any encoding done by the corresponding send method.
	//--------------------------------------------------------------------------------------
	
	@Override
	public byte[] readMessageAsBytes() throws IOException {
		//read length header
		int next;
		byte[] length_byte = new byte[4];
		for(int i = 0; i < 4; i++) {
			next = in.read();
			length_byte[i] = new Integer(next).byteValue();
		}
		int length = byteToInt(length_byte);
		
		//read content
		byte[] response = new byte[length];
		int count = 0;
		while((count < length) && ((next = in.read()) != -1))
		{
			byte b = new Integer(next).byteValue();
			response[count] = b;
			count++;
		}
		return response;
	}
	
	@Override
	public String readMessageAsString() throws IOException {
		byte[] buf = readMessageAsBytes();
		return new String(buf);
	}

	@Override
	public int readMessageAsInt() throws IOException {
		byte[] buf = readMessageAsBytes();
		return byteToInt(buf);
	}
	
	@Override
	public JSONArray readMessageAsJSONArray() throws IOException, JSONException {
		byte[] buf = readMessageAsBytes();
		String sourceStr = new String(buf);
		return new JSONArray(sourceStr); 
	}
	
	@Override
	public JSONObject readMessageAsJSONObject() throws IOException, JSONException {
		byte[] buf = readMessageAsBytes();
		String sourceStr = new String(buf);
		return new JSONObject(sourceStr); 
	}
}
