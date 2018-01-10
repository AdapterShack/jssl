package com.adaptershack.jssl;

import java.io.IOException;
import java.io.OutputStream;

public class CountingStream extends OutputStream {

	int count = 0;
	
	OutputStream out;
	
	
	public CountingStream(OutputStream out) {
		super();
		this.out = out;
	}


	@Override
	public void write(int b) throws IOException {
		count++;
		out.write(b);
	}

	public int getCount() {
		return count;
	}
	
}
