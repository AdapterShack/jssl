package com.adaptershack.jssl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;

// Borrowed from https://gist.github.com/rafalrusin/2732001#file-streamtransferer-java
public class TextStreamTransferer implements Runnable {
	private InputStream input;
	private OutputStream output, copyOut;
	private boolean crlf;

	public TextStreamTransferer(InputStream input, OutputStream output, OutputStream copyOut, boolean crlf) {
		this.input = input;
		this.output = output;
		this.crlf = crlf;
		this.copyOut = copyOut;
	}

	@Override
	public void run() {
		try {
			PrintWriter writer = getWriter(output);
			
			@SuppressWarnings("resource")
			PrintWriter writer2 = copyOut == null ? null : new PrintWriter(copyOut);
			
			BufferedReader reader = new BufferedReader(new InputStreamReader(input));
			String line;
			while ((line = reader.readLine()) != null) {
				writer.println(line);
				writer.flush();
				if(writer2 != null) {
					writer2.println(line);
					writer2.flush();
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private PrintWriter getWriter(OutputStream output2) {
		if(crlf) {
			return new PrintWriter(output2) {
				@Override
				public void println(String x) {
					super.write(x);
					super.write("\r\n");
				}
			};
		} else {
			return new PrintWriter(output2);
		}
		
	}
}