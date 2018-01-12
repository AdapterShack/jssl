package com.adaptershack.jssl;

import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeThat;
import static org.junit.Assume.assumeTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.UUID;

import javax.net.ssl.SSLHandshakeException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import static org.hamcrest.CoreMatchers.*;

/**
 * Unit test for simple App.
 */
@SuppressWarnings("restriction")
public class AppTest 
{
	static {
		System.setProperty("no-exit", "true");
	}
	
	@Test
	public void testSimpleHttp() throws Exception {
		testHTML("http://www.example.com");
	}

	@Test
	public void testSimpleHttps() throws Exception {
		testHTML("https://www.example.com");
	}
	

	//@Test
	public void testFollowRedirectShort() throws Exception {
		testHTML("http://www.adaptershack.com", "-L");
	}

	//@Test
	public void testFollowRedirectLong() throws Exception {
		testHTML("http://www.adaptershack.com", "--location");
	}

	@Test
	public void testHeaders() throws Exception {
		assumeAndRun("https://www.example.com","-i");
		assertGoodHtmlWithHeaders();
	}
	@Test
	public void testHeadersLong() throws Exception {
		assumeAndRun("https://www.example.com","--include");
		assertGoodHtmlWithHeaders();
	}

	@Test
	public void testHeadersOnly() throws Exception {
		assumeAndRun("https://www.example.com","-i","-n");
		assertHeadersOnly();
	}
	
	@Test
	public void testSavePEM() throws Exception {
		String temp = File.createTempFile("junit", "tmp").getAbsolutePath();
		assumeAndRun("https://www.example.com","-i",
				"--save-certs", temp, "--save-type","pem");
		assertGoodHtmlWithHeaders();
		String content = new String(
			Files.readAllBytes(Paths.get(temp)));
		assertThat(content,containsString("BEGIN CERTIFICATE"));
		assertThat(content,not(containsString("Certificate")));
		Files.deleteIfExists(Paths.get(temp));
	}
	
	@Test
	public void testSavePEMText() throws Exception {
		String temp = File.createTempFile("junit", "tmp").getAbsolutePath();
		assumeAndRun("https://www.example.com","-i",
				"--save-certs", temp, "--save-type","text");
		assertGoodHtmlWithHeaders();
		String content = new String(
			Files.readAllBytes(Paths.get(temp)));
		assertThat(content,containsString("BEGIN CERTIFICATE"));
		assertThat(content,containsString("Certificate"));
		Files.deleteIfExists(Paths.get(temp));
	}

	@Test
	public void testSavePKCS12() throws Exception {
		String randpass = UUID.randomUUID().toString();
		String temp = File.createTempFile("junit", "tmp").getAbsolutePath();
		Files.deleteIfExists(Paths.get(temp));

		assumeAndRun("https://www.example.com","-i",
				"--save-certs", temp, "--save-type","pkcs12",
				"--save-pass",randpass);
		
		assertGoodHtmlWithHeaders();
		byte[] content = Files.readAllBytes(Paths.get(temp));
		
		KeyStore ks = KeyStore.getInstance("pkcs12");
		ks.load(new ByteArrayInputStream(content), randpass.toCharArray());
		
		for(String alias : Collections.list(ks.aliases())) {
			assertThat(stringContent().toLowerCase(), containsString("alias [" + alias + "]"));
		}
		
		Files.deleteIfExists(Paths.get(temp));
	}
	
	@Test
	public void testSavePKCS12OneCert() throws Exception {
		String randpass = UUID.randomUUID().toString();
		String temp = File.createTempFile("junit", "tmp").getAbsolutePath();
		Files.deleteIfExists(Paths.get(temp));

		assumeAndRun("https://www.example.com","-i",
				"--save-certs", temp, "--save-type","pkcs12",
				"--save-pass",randpass,"--save-chain","1");
		
		assertGoodHtmlWithHeaders();
		byte[] content = Files.readAllBytes(Paths.get(temp));
		
		KeyStore ks = KeyStore.getInstance("pkcs12");
		ks.load(new ByteArrayInputStream(content), randpass.toCharArray());
		
		ArrayList<String> list = Collections.list(ks.aliases());
		assertThat( list.size(), is(1));
		for(String alias : list) {
			assertThat(stringContent().toLowerCase(), containsString("alias [" + alias + "]"));
		}
		
		Files.deleteIfExists(Paths.get(temp));
	}
	
	
	@Test
	public void testOutfile() throws Exception {
		String temp = File.createTempFile("junit", "tmp").getAbsolutePath();
		assumeAndRun("https://www.example.com","-i","-o", temp);
		assertGoodHtmlWithHeaders();
		String content = new String(
			Files.readAllBytes(Paths.get(temp)));
		assertThat(content,containsString("<html"));
		assertThat(content,not( startsWith("HTTP/1.1 200 OK")));
		Files.deleteIfExists(Paths.get(temp));
	}

	@Test
	public void testOutfileOnly() throws Exception {
		String temp = File.createTempFile("junit", "tmp").getAbsolutePath();
		assumeAndRun("https://www.example.com","-i","-n","-o", temp);
		assertHeadersOnly();
		String content = new String(
			Files.readAllBytes(Paths.get(temp)));
		assertThat(content,containsString("<html"));
		assertThat(content,not( startsWith("HTTP/1.1 200 OK")));
		Files.deleteIfExists(Paths.get(temp));
	}
	
	
	@Test
	public void testDownload() throws Exception {
		String temp = File.createTempFile("junit", "tmp").getAbsolutePath();
		assumeAndRun("https://www.example.com","--download", temp);
		assertHeadersOnly();
		String content = new String(
			Files.readAllBytes(Paths.get(temp)));
		assertThat(content,containsString("<html"));
		assertThat(content,not( startsWith("HTTP/1.1 200 OK")));
		Files.deleteIfExists(Paths.get(temp));
	}

	
	@Test
	public void testCharsetTransation() throws Exception {
		
		final String unicodeContent = "\u00c4\u00df\u00e7\u00f0";

		com.sun.net.httpserver.HttpServer server = makeServer(unicodeContent.getBytes("UTF-8"), "text/plain; charset=UTF-8");
		
		server.start();

		try {
			String saveCS = System.getProperty("file.encoding");
			System.setProperty("file.encoding", "iso-8859-1");
	
			String temp = File.createTempFile("junit", "tmp").getAbsolutePath();
			assumeAndRun("http://localhost:18089/index.html","-i","-n","-o", temp);
			assertHeadersOnly();
			assumeThat( stringContent().toLowerCase(), containsString("content-type: text/plain; charset=utf-8"));
			assertThat(stringContent().toLowerCase(), containsString("translating server charset utf-8 to local iso-8859-1"));
			byte[] allBytes = Files.readAllBytes(Paths.get(temp));
			String content = new String(
				allBytes, "iso-8859-1");
			assertThat(content,not( startsWith("HTTP/1.1 200 OK")));
			assertThat(content,containsString(unicodeContent));
			Files.deleteIfExists(Paths.get(temp));
			System.setProperty("file.encoding", saveCS);
		} finally {
			server.stop(1);
		}
	}

	@Test
	public void testCharsetTransationReverse() throws Exception {
		
		final String unicodeContent = "\u00c4\u00df\u00e7\u00f0";
		int utfLength = unicodeContent.getBytes("UTF-8").length;
		int isoLength = unicodeContent.getBytes("iso-8859-1").length;
		
		com.sun.net.httpserver.HttpServer server = makeServer(unicodeContent.getBytes("iso-8859-1"), "text/plain; charset=iso-8859-1");
		
		server.start();

		try {
			String saveCS = System.getProperty("file.encoding");
			System.setProperty("file.encoding", "UTF-8");
	
			String temp = File.createTempFile("junit", "tmp").getAbsolutePath();
			assumeAndRun("http://localhost:18089/index.html","-i","-n","-o", temp);
			assertHeadersOnly();
			assumeThat( stringContent().toLowerCase(), containsString("content-type: text/plain; charset=iso-8859-1"));
			assertThat(stringContent().toLowerCase(), containsString("translating server charset iso-8859-1 to local utf-8"));
			assertThat(stringContent().toLowerCase(), containsString("read "+isoLength+" bytes"));
			assertThat(stringContent().toLowerCase(), containsString("wrote "+utfLength+" bytes"));
			byte[] allBytes = Files.readAllBytes(Paths.get(temp));
			String content = new String(
				allBytes, "UTF-8");
			assertThat(content,not( startsWith("HTTP/1.1 200 OK")));
			assertThat(content,containsString(unicodeContent));
			Files.deleteIfExists(Paths.get(temp));
			System.setProperty("file.encoding", saveCS);
		} finally {
			server.stop(1);
		}
	}
	
	
	
	@Test
	public void testBinary() throws Exception {
		
		final String unicodeContent = "\u00c4\u00df\u00e7\u00f0";
		com.sun.net.httpserver.HttpServer server = makeServer(unicodeContent.getBytes("UTF-8"), "text/plain; charset=UTF-8");
		
		server.start();

		try {
			String saveCS = System.getProperty("file.encoding");
			System.setProperty("file.encoding", "iso-8859-1");
	
			String temp = File.createTempFile("junit", "tmp").getAbsolutePath();
			assumeAndRun("http://localhost:18089/index.html","-i","-n","-b","-o", temp);
			assertHeadersOnly();
			assumeThat( stringContent().toLowerCase(), containsString("content-type: text/plain; charset=utf-8"));
			assertThat(stringContent().toLowerCase(), not( containsString("translating server charset")));
			byte[] allBytes = Files.readAllBytes(Paths.get(temp));
			String content = new String(
				allBytes, "UTF-8");
			assertThat(content,not( startsWith("HTTP/1.1 200 OK")));
			assertThat(content,containsString(unicodeContent));
			Files.deleteIfExists(Paths.get(temp));
			System.setProperty("file.encoding", saveCS);
		} finally {
			server.stop(1);
		}
	}

	@Test
	public void testBinaryByDefault() throws Exception {
		
		final String unicodeContent = "\u00c4\u00df\u00e7\u00f0";
		// note lack of charset in this content type
		com.sun.net.httpserver.HttpServer server = makeServer(unicodeContent.getBytes("UTF-8"), "text/plain");
		
		server.start();

		try {
			String saveCS = System.getProperty("file.encoding");
			System.setProperty("file.encoding", "iso-8859-1");
	
			String temp = File.createTempFile("junit", "tmp").getAbsolutePath();
			assumeAndRun("http://localhost:18089/index.html","-i","-n","-b","-o", temp);
			assertHeadersOnly();
			assumeThat( stringContent().toLowerCase(), containsString("content-type: text/plain"));
			assertThat(stringContent().toLowerCase(), not( containsString("translating server charset")));
			byte[] allBytes = Files.readAllBytes(Paths.get(temp));
			String content = new String(
				allBytes, "UTF-8");
			assertThat(content,not( startsWith("HTTP/1.1 200 OK")));
			assertThat(content,containsString(unicodeContent));
			Files.deleteIfExists(Paths.get(temp));
			System.setProperty("file.encoding", saveCS);
		} finally {
			server.stop(1);
		}
	}
	
	@Test
	public void testPost() throws Exception {
		
		final ByteArrayOutputStream posted = new ByteArrayOutputStream();
		
		final String[] method = new String[1];
		
		com.sun.net.httpserver.HttpServer server
		= com.sun.net.httpserver.HttpServer.create(
				new InetSocketAddress(18089), 0);

		server.createContext("/login",
			new HttpHandler() {
	
				@Override
				public void handle(HttpExchange exchange) throws IOException {
					//byte[] bytes = unicodeContent.getBytes("UTF-8");
					posted.write(Utils.toByteArray(exchange.getRequestBody()));
					exchange.getResponseHeaders().add("Content-Type","text/plain");
					method[0] = exchange.getRequestMethod();
					byte[] bytes = "Hello, world".getBytes();
					exchange.sendResponseHeaders(200, bytes.length);
					OutputStream responseBody = exchange.getResponseBody();
					responseBody.write(bytes);
					responseBody.flush();
					responseBody.close();
				}
			
		});
		
		server.start();

		try {
			String json = "{'user':'abc','pass','123'}";
			
			assumeAndRun("http://localhost:18089/login","-i","-n","-d",json);
			assertHeadersOnly();
			String content = new String(posted.toByteArray());

			assertThat( method[0], is("POST"));
			assertThat( content.toLowerCase(), is(json));
			
		} finally {
			server.stop(1);
		}
	}

	
	// using this for now until i figure out why wiremock doesn't work
	public com.sun.net.httpserver.HttpServer makeServer(final byte[] bytes, final String contentType)
			throws IOException {
		com.sun.net.httpserver.HttpServer server
			= com.sun.net.httpserver.HttpServer.create(
					new InetSocketAddress(18089), 0);

		server.createContext("/index.html",
			new HttpHandler() {

				@Override
				public void handle(HttpExchange exchange) throws IOException {
					exchange.getResponseHeaders().add("Content-Type",contentType);
					//byte[] bytes = unicodeContent.getBytes("UTF-8");
					exchange.sendResponseHeaders(200, bytes.length);
					OutputStream responseBody = exchange.getResponseBody();
					responseBody.write(bytes);
					responseBody.flush();
					responseBody.close();
				}
			
		});
		return server;
	}

	
	
	@Test
	public void testWget() throws Exception {
		assumeTrue( !Files.exists(Paths.get("index.html"))); 
		assumeAndRun("https://www.example.com/index.html","--wget");
		assertHeadersOnly();
		String content = new String(
			Files.readAllBytes(Paths.get("index.html")));
		assertThat(content,containsString("<html"));
		assertThat(content,not( startsWith("HTTP/1.1 200 OK")));
		Files.deleteIfExists(Paths.get("index.html"));
		assertTrue( !Files.exists(Paths.get("index.html"))); 
	}

	
	@Test
	public void testHeadersOnlyLong() throws Exception {
		assumeAndRun("https://www.example.com","--include","--no-body");
		assertHeadersOnly();
	}
	@Test
	public void testHeadMethod() throws Exception {
		assumeAndRun("https://www.example.com","--include","-X","HEAD");
		assertHeadersOnly();
	}
	@Test
	public void testHeadMethodLong() throws Exception {
		assumeAndRun("https://www.example.com","--include","--request","HEAD");
		assertHeadersOnly();
	}
	
	@Test
	public void testZip() throws Exception {
		assumeAndRun("https://www.example.com","-i","-g");
		assumeThat(stringContent().toLowerCase(), containsString("content-encoding: gzip"));
		assertGoodHtmlWithHeaders();
		assertThat( stringContent(), containsString("compressed bytes, inflated"));
	}
	
	@Test
	public void testSSLWithDataShort() throws Exception {
		assumeAndRun("ssl://www.example.com","-d",
				"GET / HTTP/1.1\nHost: www.example.com\nConnection: close\n\n");
		assertSocketConnected();
		assertGoodHtmlWithHeaders();
	}

	
	@Test
	public void testSSLWithDataLong() throws Exception {
		assumeAndRun("ssl://www.example.com","--data",
				"GET / HTTP/1.1\nHost: www.example.com\nConnection: close\n\n");
		assertSocketConnected();
		assertGoodHtmlWithHeaders();
	}
	
	@Test
	public void testSSLFromStdin() throws Exception {

		System.setIn( new ByteArrayInputStream(
			"GET / HTTP/1.1\r\nHost: www.example.com\r\nConnection: close\r\n\r\n".getBytes()));
		assumeAndRun("ssl://www.example.com");
		assertSocketConnected();
		assertGoodHtmlWithHeaders();
	}

	@Test
	public void testSSLFromStdinNoBody() throws Exception {

		System.setIn( new ByteArrayInputStream(
			"GET / HTTP/1.1\r\nHost: www.example.com\r\nConnection: close\r\n\r\n".getBytes()));
		assumeAndRun("ssl://www.example.com","-n");
		assertSocketConnected();
		assertHeadersOnly();
	}
	
	
	@Test
	public void testTCPWithDataShort() throws Exception {
		assumeAndRun("tcp://www.example.com","-d",
				"GET / HTTP/1.1\nHost: www.example.com\nConnection: close\n\n");
		assertSocketConnected();
		assertGoodHtmlWithHeaders();
	}

	
	@Test
	public void testTCPWithDataLong() throws Exception {
		assumeAndRun("tcp://www.example.com","--data",
				"GET / HTTP/1.1\nHost: www.example.com\nConnection: close\n\n");
		assertSocketConnected();
		assertGoodHtmlWithHeaders();
	}
	
	@Test
	public void testTCPFromStdin() throws Exception {

		System.setIn( new ByteArrayInputStream(
			"GET / HTTP/1.1\r\nHost: www.example.com\r\nConnection: close\r\n\r\n".getBytes()));
		assumeAndRun("tcp://www.example.com");
		assertSocketConnected();
		assertGoodHtmlWithHeaders();
	}
	
	public void assertSocketConnected() {
		assertThat("SSL connected",
				stringContent(),
				containsString("Connected to"));
	}

	public void assertGoodHtml() {
		assertThat("HTML returned",
				stringContent(),
				containsString("<html"));
	}

	void assertGoodHtmlWithHeaders() {
		assertThat("HTTP OK",
				stringContent(),
				containsString("HTTP/1.1 200 OK"));
		assertGoodHtml();
	}
	
	void assertHeadersOnly() {
		assertThat("HTTP OK",
				stringContent(),
				containsString("HTTP/1.1 200 OK"));
		assertThat("HTML returned",
				stringContent(),
				not( containsString("<html")));
	}
	
	@Test(expected=SSLHandshakeException.class)
	public void testHandshakeFail() throws Exception {
		assumeAndRun("https://self.adaptershack.com");
	}

	@Test(expected=SSLHandshakeException.class)
	public void testHandshakeFailSSL() throws Exception {
		assumeAndRun("ssl://self.adaptershack.com","-d",
				"GET / HTTP/1.1\nHost: self.adaptershack.com\nConnection: close\n\n");
	}
	
	@Test
	public void testTrustAll() throws Exception {
		assumeAndRun("https://self.adaptershack.com","-k");
	}
	
	@Test
	public void testTrustAllLong() throws Exception {
		assumeAndRun("https://self.adaptershack.com","--insecure");
	}

	@Test
	public void testTrustAllSSL() throws Exception {
		assumeAndRun("ssl://self.adaptershack.com","-k","-d",
				"GET / HTTP/1.1\nHost: self.adaptershack.com\nConnection: close\n\n");
		assertSocketConnected();
		assertGoodHtml();
	}
	
	@Test
	public void testTrustAllSSLLong() throws Exception {
		assumeAndRun("ssl://self.adaptershack.com","--insecure","-d",
				"GET / HTTP/1.1\nHost: self.adaptershack.com\nConnection: close\n\n");
		assertSocketConnected();	
		assertGoodHtml();
		
	}

	
	@Test
	public void testPingHttpLong() throws Exception {
		testPing("http://www.example.com","--ping");
	}
	@Test
	public void testPingHttpShort() throws Exception {
		testPing("http://www.example.com","-z");
	}
	@Test
	public void testPingHttpsLong() throws Exception {
		testPing("https://www.example.com","--ping");
	}
	@Test
	public void testPingHttpsShort() throws Exception {
		testPing("https://www.example.com","-z");
	}
	@Test
	public void testPingSSLLong() throws Exception {
		testPing("ssl://www.example.com","--ping");
	}
	@Test
	public void testPingSSLShort() throws Exception {
		testPing("ssl://www.example.com","-z");
	}
	@Test
	public void testPingTCPLong() throws Exception {
		testPing("tcp://www.example.com","--ping");
	}
	@Test
	public void testPingTCPShort() throws Exception {
		testPing("tcp://www.example.com","-z");
	}
	
	
	public void testPing(String...args) throws Exception {
		assumeAndRun(args);
		assertThat("Port open",
				stringContent(),
				containsString("is open"));
	}

	
	public String stringContent() {
		return new String(outContent.toByteArray());
	}

	public void testPingLong() throws Exception {
		assumeAndRun("https://www.example.com","-ping");
	}
	
	
	public void testHTML(String...args) throws Exception {
		assumeAndRun(args);
		assertGoodHtml();
	}

	
	void assumeAndRun(String... args) throws Exception {
		assumeTrue(Utils.reachable(args[0]));		
		Main
		.main(args);
	}
	
	
	private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
	private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();

	@Before
	public void setUpStreams() {
	    System.setOut(new PrintStream(outContent));
	    System.setErr(new PrintStream(errContent));
	}

	@After
	public void cleanUpStreams() {
	    System.setOut(null);
	    System.setErr(null);
	}	
	
}
