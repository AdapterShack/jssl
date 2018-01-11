package com.adaptershack.jssl;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

public class CountingZipStream extends InputStream {

	
	InputStream rawStream;
	GZIPInputStream zipStream;
	
	int compressed, uncompressed = 0;
	
	
	public CountingZipStream(InputStream rawStream) throws IOException {
		this.rawStream = rawStream;
		
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
		
	}


	@Override
	public int read() throws IOException {

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
		return (double) uncompressed / (double) compressed;
	}
}
