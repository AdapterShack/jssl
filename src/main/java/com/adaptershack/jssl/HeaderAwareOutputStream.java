package com.adaptershack.jssl;

import java.io.IOException;
import java.io.OutputStream;

public class HeaderAwareOutputStream extends OutputStream {

	public static HeaderAwareOutputStream skipHeaders(OutputStream o) {
		return new HeaderAwareOutputStream(o,Mode.BODY);
	}

	public static HeaderAwareOutputStream skipBody(OutputStream o) {
		return new HeaderAwareOutputStream(o,Mode.HEADER);
	}
	
	boolean headersDone = false;
	OutputStream out;
		
	int[] buffer = { -1, -1 };
	private enum Position { EMPTY, HALF, FULL };
	Position pos = Position.EMPTY;
	
	public enum Mode { HEADER, BODY };
	
	private Mode mode;
	
	@Override
	public void write(int b) throws IOException {

		if( headersDone ) {
			
			if(mode == Mode.BODY) {
				out.write(b);
			}
			
		} else {

			if(mode == Mode.HEADER) {
				out.write(b);
			}
			
			if( b != '\r') {
	
				switch(pos) {
				case EMPTY:
					buffer[0] = b;
					pos = Position.HALF;
					break;
				case HALF:
					buffer[1] = b;
					pos = Position.FULL;
					break;
				case FULL:
				default:
					buffer[0] = buffer[1];
					buffer[1] = b;
				}
				
				if( buffer[0] == '\n' && buffer[1] == '\n' ) {
					headersDone = true;
				}
			}
		}
		
		
	}

	public HeaderAwareOutputStream(OutputStream out, Mode m) {
		super();
		this.out = out;
		mode = m;
	}

}
