package com.adaptershack.jssl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayDeque;
import java.util.Queue;

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

		System.setIn(
			new InputStream() {
				@Override
				public int read() throws IOException {
					return queue.isEmpty() ?
							-1 : (int) queue.remove();
				}

				@Override
				public int available() throws IOException {
					return queue.size();
				}
			}
		);
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

		this.queue.clear();
		
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
	
	public void setIn(final byte[] buffer) throws IOException {
		queue.clear();
		printer.write(buffer);
	}
	
	public void setIn(String buffer) {
		printer.print(buffer);
	}
	
	public PrintStream realOut() {
		return realSystemOut;
	}

	public PrintStream realErr() {
		return realSystemErr;
	}
	
	public InputStream realIn() {
		return realSystemIn;
	}
	
	public PrintStream in() {
		return printer;
	}
	
	final Queue<Byte> queue = new ArrayDeque<>();
		
	final PrintStream printer = new PrintStream(
		new OutputStream() {
			@Override
			public void write(int b) throws IOException {
				queue.add((byte)b);
			}
		}			
	);
	
	public byte[] peek() {

		ByteArrayOutputStream bout = new ByteArrayOutputStream();
		
		for( byte b : queue ) {
			bout.write((int)b);
		}
		
		return bout.toByteArray();
	}
	
	

}
