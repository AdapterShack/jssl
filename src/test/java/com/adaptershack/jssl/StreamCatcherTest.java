package com.adaptershack.jssl;

import static org.junit.Assert.assertEquals;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import org.junit.Rule;
import org.junit.Test;

public class StreamCatcherTest {

	// capture standard in, out, error
	@Rule
	public StreamCatcher streams = new StreamCatcher(false);	
	
	String newline = System.lineSeparator();
			
	@Test
	public void testOutCaptured() {
		System.out.println("hello");
		
		assertEquals("hello"+newline, streams.outText());
	}
	
	@Test
	public void testOutAgain() {
		System.out.println("world");
		assertEquals("world"+newline, streams.outText());
	}

	@Test
	public void testErr() {
		System.err.println("world");
		assertEquals("world"+newline, streams.errText());
	}
	
	
	@Test
	public void testIn() throws IOException {
		
		streams.setIn("Hello, world\n");
		
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

		assertEquals("Hello, world", br.readLine());
		assertEquals(null, br.readLine());
		
	}

	@Test
	public void testReset() {

		System.out.println("hello");
		assertEquals("hello"+newline, streams.outText());

		streams.reset();
		
		System.out.println("world");
		assertEquals("world"+newline, streams.outText());
		
		
	}
	
}
