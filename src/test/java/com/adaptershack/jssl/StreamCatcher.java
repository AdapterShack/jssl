package com.adaptershack.jssl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import org.junit.rules.ExternalResource;

/**
 * Captures the i/o of classes run during a test case so
 * it can be inspected by the tests.
 * 
 */
public class StreamCatcher extends ExternalResource {

	private ByteArrayOutputStream capturedOut;
	private ByteArrayOutputStream capturedErr;

	private final static PrintStream realSystemOut = System.out;
	private final static PrintStream realSystemErr = System.err;
	private final static InputStream realSystemIn = System.in;
	
	boolean tee;
	
	
	public StreamCatcher() {
		this(true);
	}
	
	public StreamCatcher(boolean tee) {
		super();
		this.tee = tee;
	}

	@Override
	protected void before() throws Throwable {
		super.before();
		reset();
	}
	
	public void reset() {

		capturedOut = new ByteArrayOutputStream();		
		capturedErr = new ByteArrayOutputStream();		
		
		System.setOut(new PrintStream( tee ? 
			new OutputStream() {
				public void write(int b) throws IOException {
					realSystemOut.write(b);
					capturedOut.write(b);
				}
			} : capturedOut
		));

		System.setErr(new PrintStream( tee ?
			new OutputStream() {
				public void write(int b) throws IOException {
					realSystemErr.write(b);
					capturedErr.write(b);
				}
		} : capturedErr ));
		
	}

	@Override
	protected void after() {
		super.after();
	    System.setOut(realSystemOut);
	    System.setErr(realSystemErr);
	    System.setIn(realSystemIn);
	}
	
	public byte[] getOut() {
		return capturedOut.toByteArray();		
	}

	public byte[] getErr() {
		return capturedErr.toByteArray();		
	}
	
	public String outText() {
		return new String(capturedOut.toByteArray());		
	}

	public String errText() {
		return new String(capturedErr.toByteArray());		
	}
	
	public void setIn(final byte[] buffer) {
		System.setIn( new ByteArrayInputStream(buffer));
	}
	
	public void setIn(String buffer) {
		setIn(buffer.getBytes());
	}
	

}
