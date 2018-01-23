package com.adaptershack.jssl;

import java.io.BufferedOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Supplier;

/**
 * Wrote a new subclass of HttpURLConnection because HttpsURLConnection
 * doesn't play well with already-opened sockets.
 * 
 * Concept inpsired by:
 * 
 * https://stackoverflow.com/questions/34877470/basic-proxy-authentication-for-https-urls-returns-http-1-0-407-proxy-authenticat
 * 
 *
 */
public class CustomConnection extends HttpURLConnection {

	Supplier<Socket> supplier;
	
	protected CustomConnection(URL u, Supplier<Socket> _supplier) {
		super(u);
		this.supplier = _supplier;
	}

	@Override
	public void disconnect() {
		try {
			s.close();
		} catch (Exception e) {
			Log.log(e.toString());
		}
	}

	@Override
	public boolean usingProxy() {
		return true;
	}

	
	boolean connected;
	InputStream in;
	OutputStream out;
	Socket s;
	private int statusCode;
	
	List<String> headerlines = new ArrayList<>();
	Map<String, List<String>> headers = new HashMap<>();
	private boolean chunked;
	
	@Override
	public void connect() throws IOException {
	
		if(connected) {
			return;
		}
		
		s = supplier.get();
		
		out = s.getOutputStream();
		
		String uri = getURL().getPath();
		if(uri.isEmpty() ) {
			uri = "/";
		}
		
		if(getURL().getQuery() != null && !getURL().getQuery().isEmpty() ) {
			uri += "?" + getURL().getQuery();
		}
		
		out.write( (method + " " + uri + " HTTP/1.0\r\n").getBytes());
		out.write( ("Host " + getURL().getHost() + (
				getURL().getPort() < 1 ? "" : ":"+getURL().getPort()
			) + "\r\n").getBytes());
		
		for( Entry<String, List<String>> e : super.getRequestProperties().entrySet() ) {
			if( e.getKey() != null) {
				
				for(String v : e.getValue()) {
					out.write((e.getKey() + ": " + v + "\r\n").getBytes());
				}
				
			}
		}
		
		if(method.equalsIgnoreCase("POST") || method.equalsIgnoreCase("PUT")) {
			if(super.getRequestProperty("content-length") == null) {
				chunked = true;
				out.write( ("Transfer-Encoding: chunked\r\n").getBytes());
			}
		}
		
		out.write( ("Connection: close\r\n\r\n").getBytes());
		out.flush();
		
		connected = true;
		
	}
	
	boolean responseRead;

	private void readResponseHeaders() throws IOException {

		if(responseRead) {
			return;
		}
		
		connect();
		
		in = s.getInputStream();
		
		DataInput data = new DataInputStream(in);
		String statusLine = data.readLine();
		
		headerlines.add(statusLine);
		headers.put(null, Arrays.asList(statusLine));
		
		String[] splitStatus = statusLine.split(" +");

		statusCode = Integer.parseInt( splitStatus[1] );
		
		String headerLine = null;
		
		while(true) {
			headerLine = data.readLine();
			
			if(headerLine == null || headerLine.isEmpty() ) {
				break;
			}
			
			headerlines.add(headerLine);
			
			String[] splitHeader = headerLine.split(":",2);
			String key = splitHeader[0].trim().toLowerCase();
			String value = splitHeader[1].trim();
			
			List<String> values = headers.get(key);
			if(values == null) {
				values = new ArrayList<String>();
				headers.put(key, values);
			}
			values.add(value);
			
		}
		
		responseRead = true;
	}
	
	@Override
	public InputStream getInputStream() throws IOException {
		readResponseHeaders();
		return in;
	}

	@Override
	public InputStream getErrorStream() {
		try {
			readResponseHeaders();
		} catch (IOException e) {
			Log.log(e.toString());
		}
		return in;
	}
	
	
	@Override
	public OutputStream getOutputStream() throws IOException {

		if(method.equalsIgnoreCase("GET")) {
			method = "POST";
		}
		
		connect();
		
		return chunked ? new FilterOutputStream(out) {

			@Override
			public void write(int b) throws IOException {
				out.write("1\r\n".getBytes() );
				out.write(b);
				out.write("\r\n".getBytes());
				//out.flush();
			}

			@Override
			public void write(byte[] b) throws IOException {
				out.write( (Integer.toString(b.length,16) + "\r\n").getBytes() );
				out.write(b);
				out.write("\r\n".getBytes());
				//out.flush();
			}

			@Override
			public void write(byte[] b, int off, int len) throws IOException {
				out.write( (Integer.toString(b.length,16) + "\r\n").getBytes() );
				out.write(b, off, len);
				out.write("\r\n".getBytes());
				//out.flush();
			}

			@Override
			public void close() throws IOException {
				out.write("0\r\n".getBytes());
				out.write("\r\n".getBytes());
				// don't close it, it will close the socket
				out.flush();
			}
			
		} : 
		new BufferedOutputStream(out, Math.min(8192, Integer.parseInt(super.getRequestProperty("content-length")))) {
			public void close() throws IOException {
				// don't close it, it will close the socket
				flush();
			}
		};
		
	}

	@Override
	public int getResponseCode() throws IOException {
		readResponseHeaders();
		return statusCode;
	}

	@Override
	public String getResponseMessage() throws IOException {
		readResponseHeaders();
		return headerlines.get(0);
	}

	@Override
	public Map<String, List<String>> getHeaderFields() {
		
		try {
			readResponseHeaders();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return headers;
	}

	
	
}