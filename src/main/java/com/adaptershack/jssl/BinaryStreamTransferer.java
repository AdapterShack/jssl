package com.adaptershack.jssl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

// Borrowed from https://gist.github.com/rafalrusin/2732001#file-streamtransferer-java
public class BinaryStreamTransferer implements Runnable {

	private int buffer;
	
	private InputStream input;
	private OutputStream output, copyOut;

	public BinaryStreamTransferer(InputStream input, OutputStream output, OutputStream copyOut,
			boolean crlf, int buffer) {
		
		this.input = input;
		this.output = output;
		this.copyOut = copyOut;
		this.buffer = buffer;
	}

	@Override
	public void run() {
		try {

			int nRead;
			byte[] data = new byte[buffer];

			while ((nRead = input.read(data, 0, data.length)) != -1) {
			  output.write(data, 0, nRead);
			  output.flush();
			  if(copyOut != null) {
				  copyOut.write(data, 0, nRead);
				  copyOut.flush();
			  }
			}
			
		} catch (IOException e) {
			e.printStackTrace();
		}
	}


}