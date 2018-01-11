package com.adaptershack.jssl;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

public class CountingZipStream extends InputStream {

	
	InputStream rawStream;
	GZIPInputStream zipStream;
	
	int rawbytes = 0;
	
	
	public CountingZipStream(InputStream rawStream) throws IOException {
		this.rawStream = rawStream;
		
		zipStream = new GZIPInputStream(
			new InputStream() {
				@Override
				public int read() throws IOException {
					int b = rawStream.read();
					if(b != -1) {
						rawbytes++;
					}
					return b;
				}
			});
		
	}


	@Override
	public int read() throws IOException {

		return zipStream.read();
	
	}
	
	public int rawBytes() {
		return rawbytes;
	}
	
}
