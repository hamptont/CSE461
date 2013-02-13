package edu.uw.cs.cse461.net.tcpmessagehandler;

import java.io.BufferedReader;

import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import edu.uw.cs.cse461.net.base.NetBase;
import edu.uw.cs.cse461.util.Log;


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
	
	//Init in the constructor
	private Socket socket; 
	private int timeout;
	private boolean noDelay;
	private int maxReadLength;
	
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
		//TODO You need to implement this.  It's the inverse of intToByte().
		ByteBuffer b = ByteBuffer.wrap(buf);
		b.order(ByteOrder.LITTLE_ENDIAN);
		return b.getInt();
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
	}
	
	/**
	 * Uses str.getBytes() for conversion.
	 */
	@Override
	public void sendMessage(String str) throws IOException {
		byte[] buf = str.getBytes();
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
		byte[] buf = jsArray.toString().getBytes(); 
		sendMessage(buf);
	}
	
	/**
	 * Sends JSON string representation of the JSONObject.
	 */
	@Override
	public void sendMessage(JSONObject jsObject) throws IOException {
		byte[] buf = jsObject.toString().getBytes();
		sendMessage(buf);
	}
	
	//--------------------------------------------------------------------------------------
	// read routines
	//   All of these invert any encoding done by the corresponding send method.
	//--------------------------------------------------------------------------------------
	
	@Override
	public byte[] readMessageAsBytes() throws IOException {
		//Read response
		this.socket.setSoTimeout(this.timeout);
		this.socket.setTcpNoDelay(this.noDelay);
		BufferedReader in = new BufferedReader(new InputStreamReader(this.socket.getInputStream()));

		char[] response = new char[4];
		
		int chars_read = in.read(response, 0, 4);
		System.out.println("chars read: " + chars_read);

		if(chars_read != 4) {
			System.out.println("Did not read int: " + new String(response));
		}
		else 
		{
			System.out.println("Read int: " + new String(response));
		}

		byte[] responseLength = new byte[4];
		for(int i = 0; i < 4; i++) {
			responseLength[i] = (byte)response[i];
		}

		int length = byteToInt(responseLength);
		System.out.println("length: " + length);
		char[] char_header_response = new char[length];
		chars_read = in.read(char_header_response, 0, length);
		
		if(chars_read != length) {
			System.out.println("Did not read enough bytes");
		}
		
		byte[] header_response = new byte[length];
		for(int i = 0; i < length; i++) {
			header_response[i] = (byte) char_header_response[i];
		}

		return header_response;
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
		
		return new JSONArray(sourceStr); //TODO String to JSONArray
	}
	
	@Override
	public JSONObject readMessageAsJSONObject() throws IOException, JSONException {
		byte[] buf = readMessageAsBytes();
		String sourceStr = new String(buf);
		return new JSONObject(sourceStr); // TODO String to JSONObject
	}
}
