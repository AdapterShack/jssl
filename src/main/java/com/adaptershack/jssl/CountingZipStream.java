package com.adaptershack.jssl;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

public class CountingZipStream extends InputStream {

	
	GZIPInputStream zipStream;
	
	int compressed, uncompressed = 0;
	
	
	public CountingZipStream(final InputStream rawStream) throws IOException {
		
		try {
			zipStream = new GZIPInputStream(
				new InputStream() {
					@Override
					public int read() throws IOException {
						int b = rawStream.read();
						if(b != -1) {
							compressed++;
						}
						return b;
					}
				});
		} catch (EOFException e) {
			Log.log("Could not initialize gzip, already at EOF");
		}
	}


	@Override
	public int read() throws IOException {

		if(zipStream == null) {
			return -1;
		}
		
		int b = zipStream.read();
		if(b != -1) {
			uncompressed++;
		}
		return b;
		
	}
	
	public int getCompressed() {
		return compressed;
	}

	public int getUncompressed() {
		return uncompressed;
	}
	
	public double getRatio() {
		return 
			compressed > 0 ?
				(double) uncompressed / (double) compressed : 1;
	}
}
