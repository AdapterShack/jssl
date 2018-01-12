package com.adaptershack.jssl;

import java.io.IOException;
import java.io.InputStream;

public class ProgressInputStream extends InputStream {

	int mod;
	int count;
	int expected = -1;
	int len;
	
//	public ProgressInputStream(int mod, InputStream in) {
//		this.mod = mod;
//		this.in = in;
//	}

	public ProgressInputStream(int expected, InputStream in) {
		this.in=in;
		this.expected = expected;
		if( expected > 0) {
			
			int chunks;
			
			if (expected > 1024 * 1024 ) {
				chunks = 20;
			} else if (expected > 100 * 1024 ) {
				chunks = 10;
			} else {
				chunks = 5;
			}
			
			mod = expected / chunks;
			len = String.valueOf(expected).length();
		} else {
			mod = 1024 * 4;
		}
		
		// could happen when the content-length is
		// actually than the number of chunks
		if(mod==0) {
			mod = expected;
		}
	}
	
	
//	public ProgressInputStream(InputStream in) {
//		this(1024 * 4 , in);
//	}

	InputStream in;
	
	@Override
	public int read() throws IOException {
		
		int c = in.read();

		if(c != -1) {
			if( count > 0 && (count % mod) == 0) {
				if(expected > 0) {
					Log.log("%3d%% (%"+len+"d of %d bytes) read...", 
							(int) (((double) count / (double) expected) * 100),
							count, expected);
				} else {
					Log.log("%10d bytes read...", count);
				}
			}
			count++;
		}
		
		return c;
	}

	public int getCount() {
		return count;
	}
	

	
	

}
