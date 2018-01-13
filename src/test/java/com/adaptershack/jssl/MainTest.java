package com.adaptershack.jssl;

import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeThat;
import static org.junit.Assume.assumeTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

import javax.net.ssl.SSLHandshakeException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

import static org.hamcrest.CoreMatchers.*;

/**
 * This class performs actual HTTP and HTTPS requests using
 * WireMock to create an HTTP server. All tests will skip if
 * it is detected that the server isn't listening (such as 
 * might happen if the OS won't allow it to run).
 * 
 */
public class MainTest 
{
	static {
		System.setProperty("no-exit", "true");
		//System.setProperty("javax.net.debug", "all");
	}

	// capture standard in, out, error
	@Rule
	public StreamCatcher streams = new StreamCatcher(false);	

	@ClassRule
	public static WireMockRule wireMockRule = new WireMockRule(
	  WireMockConfiguration.wireMockConfig()
	  .port(9090)
	  .httpsPort(9091)
	  .keystorePath("test-certs/server-ks")
	  .keystorePassword("changeit")
	);

	@ClassRule
	public static WireMockRule wireMockRuleClientCerts = new WireMockRule(
	  WireMockConfiguration.wireMockConfig()
	  .httpsPort(9092)
	  .needClientAuth(true)
	  .keystorePath("test-certs/server-ks")
	  .keystorePassword("changeit")
	  .trustStorePath("test-certs/server-cacerts")
	  .trustStorePassword("changeit")
	);
	
	// we use jetty directly (not via wiremock) to handle basic auth
	@ClassRule
	public static JettyRule jetty = new JettyRule(9093);
	
	static String unicodeJunk = "\u00c4\u00df\u00e7\u00f0";		
	static String json = "{'user':'abc','pass','123'}";
	static String html = "<html><head><title>Hello</title></head><body>World</body></html>";

	static ResponseDefinitionBuilder withBody = aResponse()
			.withHeader("Content-type", "text/html; charset=UTF-8")
			.withBody(html);

	@SuppressWarnings("serial")
	@BeforeClass
	public static void config() throws UnsupportedEncodingException {
		
		wireMockRule.stubFor(
				get(urlPathEqualTo("/"))
				.willReturn(withBody));

		wireMockRule.stubFor(
				head(urlPathEqualTo("/"))
				.willReturn(
					aResponse()
						.withStatus(200)
						.withHeader("Content-Length", "666")
						.withHeader("Content-Encoding", "gzip")
						.withHeader("etag", "12345678")
						));
		
		wireMockRule.stubFor(
				get(urlPathEqualTo("/index.html"))
				.willReturn(withBody));

		wireMockRule.stubFor(
				get(urlPathEqualTo("/redir"))
				.willReturn(
					aResponse()
					.withStatus(302)
					.withHeader("Location", "/index.html")));
		
		wireMockRule.stubFor(
				get(urlPathEqualTo("/i18n/utf-8"))
				.willReturn(
					aResponse()
					.withStatus(200)
					.withHeader("Content-Type", "text/plain; charset=UTF-8")
					.withBody( unicodeJunk.getBytes("UTF-8") )
					));

		wireMockRule.stubFor(
				get(urlPathEqualTo("/i18n/utf-8-no-cs"))
				.willReturn(
					aResponse()
					.withStatus(200)
					.withHeader("Content-Type", "text/plain")
					.withBody( unicodeJunk.getBytes("UTF-8") )
					));
		
		wireMockRule.stubFor(
				get(urlPathEqualTo("/i18n/iso-8859-1"))
				.willReturn(
					aResponse()
					.withStatus(200)
					.withHeader("Content-Type", "text/plain; charset=iso-8859-1")
					.withBody( unicodeJunk.getBytes("iso-8859-1") )
					));

		jetty.addServlet("/",
	    		new HttpServlet() {
	    			@Override
	    			protected void service(HttpServletRequest req, HttpServletResponse res)
	    					throws ServletException, IOException {
	 
	    				String expected = "Basic " + Base64.getEncoder().encodeToString(
	    						"user:password".getBytes());
			
	    				if( ! expected.equals(req.getHeader("Authorization"))) {
	    					res.setHeader("WWW-Authenticate", "Basic realm=\"Members Area\"");
	    					res.sendError(401);
	    				} else {
	    					res.setContentType("text/html); charset=utf-8");
	    					res.getWriter().print(html);
	    				}
	    			}
	    		});
	}
		
	@Test
	public void testSimpleHttp() throws Exception {
		testHTML("http://localhost:9090");
	}

	@Test
	public void testSimpleHttps() throws Exception {
		testHTML("https://localhost:9091","-k");
	}


	@Test
	public void testBasicAuthOnUrl() throws Exception {
		this.assumeAndRun("http://user:password@localhost:9093","-i");
		this.assertGoodHtmlWithHeaders();
	}

	@Test
	public void testBasicAuthOnPrompted() throws Exception {

		streams.setIn("password\n");
				
		this.assumeAndRun("http://user@localhost:9093","-i");
		this.assertGoodHtmlWithHeaders();
	}


	
	@Test
	public void testClientCerts() throws Exception {

		wireMockRuleClientCerts.stubFor(
				get(urlPathEqualTo("/"))
				.willReturn(withBody));
		
		testHTML("https://localhost:9092","-k","--keystore","test-certs/client-ks","--keypass","changeit");
	}
	
	@Test
	public void testCustomCacerts() throws Exception {

		wireMockRuleClientCerts.stubFor(
				get(urlPathEqualTo("/"))
				.willReturn(withBody));
				
		testHTML("https://localhost:9091","--truststore","test-certs/client-cacerts","--trustpass","changeit");
	}
	

	@Test
	public void testFollowRedirectShort() throws Exception {
		testHTML("http://localhost:9090/redir","-L");
	}

	@Test
	public void testFollowRedirectLong() throws Exception {
		testHTML("http://localhost:9090/redir", "--location");
	}

	@Test
	public void testHeaders() throws Exception {
		assumeAndRun("http://localhost:9090","-i");
		assertGoodHtmlWithHeaders();
	}
	@Test
	public void testHeadersLong() throws Exception {
		assumeAndRun("http://localhost:9090","--include");
		assertGoodHtmlWithHeaders();
	}

	@Test
	public void testHeadersOnly() throws Exception {
		assumeAndRun("http://localhost:9090","-i","-n");
		assertHeadersOnly();
	}
	
	// uses a real domain
	@Test
	public void testSavePEM() throws Exception {
		String temp = File.createTempFile("junit", "tmp").getAbsolutePath();
		assumeAndRun("https://localhost:9091","-k","-i",
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
		assumeAndRun("https://localhost:9091","-k","-i",
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

		assumeAndRun("https://localhost:9091","-k","-i",
				"--save-certs", temp, "--save-type","pkcs12",
				"--save-pass",randpass);
		
		assertGoodHtmlWithHeaders();
		byte[] content = Files.readAllBytes(Paths.get(temp));
		
		KeyStore ks = KeyStore.getInstance("pkcs12");
		ks.load(new ByteArrayInputStream(content), randpass.toCharArray());
		ArrayList<String> list = Collections.list(ks.aliases());
		assertThat( list.size(), is(2));
		for(String alias : list) {
			assertThat(streams.outText().toLowerCase(), containsString("alias [" + alias + "]"));
		}
		
		Files.deleteIfExists(Paths.get(temp));
	}
	
	@Test
	public void testSavePKCS12OneCert() throws Exception {
		String randpass = UUID.randomUUID().toString();
		String temp = File.createTempFile("junit", "tmp").getAbsolutePath();
		Files.deleteIfExists(Paths.get(temp));

		assumeAndRun("https://localhost:9091","-k","-i","-n",
				"--save-certs", temp, "--save-type","pkcs12",
				"--save-pass",randpass,"--save-chain","1");
		
		assertHeadersOnly();
		byte[] content = Files.readAllBytes(Paths.get(temp));
		
		KeyStore ks = KeyStore.getInstance("pkcs12");
		ks.load(new ByteArrayInputStream(content), randpass.toCharArray());
		
		ArrayList<String> list = Collections.list(ks.aliases());
		assertThat( list.size(), is(1));
		for(String alias : list) {
			assertThat(streams.outText().toLowerCase(), containsString("alias [" + alias + "]"));
		}
		
		Files.deleteIfExists(Paths.get(temp));
	}
	
	
	@Test
	public void testOutfile() throws Exception {
		String temp = File.createTempFile("junit", "tmp").getAbsolutePath();
		assumeAndRun("http://localhost:9090","-i","-o", temp);
		assertGoodHtmlWithHeaders();
		String content = new String(
			Files.readAllBytes(Paths.get(temp)));
		assertThat(content,containsString(html));
		assertThat(content,not( startsWith("HTTP/1.1 200 OK")));
		Files.deleteIfExists(Paths.get(temp));
	}

	@Test
	public void testOutfileOnly() throws Exception {
		String temp = File.createTempFile("junit", "tmp").getAbsolutePath();
		assumeAndRun("http://localhost:9090","-i","-n","-o", temp);
		assertHeadersOnly();
		String content = new String(
			Files.readAllBytes(Paths.get(temp)));
		assertThat(content,containsString(html));
		assertThat(content,not( startsWith("HTTP/1.1 200 OK")));
		Files.deleteIfExists(Paths.get(temp));
	}
	
	
	@Test
	public void testDownload() throws Exception {
		String temp = File.createTempFile("junit", "tmp").getAbsolutePath();
		assumeAndRun("http://localhost:9090","--download", temp);
		assertHeadersOnly();
		String content = new String(
			Files.readAllBytes(Paths.get(temp)));
		assertThat(content,containsString(html));
		assertThat(content,not( startsWith("HTTP/1.1 200 OK")));
		Files.deleteIfExists(Paths.get(temp));
	}

	
	@Test
	public void testCharsetTransation() throws Exception {
		
		String saveCS = System.getProperty("file.encoding");
		System.setProperty("file.encoding", "iso-8859-1");

		String temp = File.createTempFile("junit", "tmp").getAbsolutePath();
		assumeAndRun("http://localhost:9090/i18n/utf-8","-i","-n","-o", temp);
		assertHeadersOnly();
		assertThat( streams.outText().toLowerCase(), containsString("content-type: text/plain; charset=utf-8"));
		assertThat(streams.outText().toLowerCase(), containsString("translating server charset utf-8 to local iso-8859-1"));
		byte[] allBytes = Files.readAllBytes(Paths.get(temp));
		String content = new String(
			allBytes, "iso-8859-1");
		assertThat(content,not( startsWith("HTTP/1.1 200 OK")));
		assertThat(content,containsString(unicodeJunk));
		Files.deleteIfExists(Paths.get(temp));
		System.setProperty("file.encoding", saveCS);
	}

	@Test
	public void testCharsetTransationReverse() throws Exception {
		
		int utfLength = unicodeJunk.getBytes("UTF-8").length;
		int isoLength = unicodeJunk.getBytes("iso-8859-1").length;
		
		String saveCS = System.getProperty("file.encoding");
		System.setProperty("file.encoding", "UTF-8");

		String temp = File.createTempFile("junit", "tmp").getAbsolutePath();
		assumeAndRun("http://localhost:9090/i18n/iso-8859-1","-i","-n","-o", temp);
		assertHeadersOnly();
		assumeThat( streams.outText().toLowerCase(), containsString("content-type: text/plain; charset=iso-8859-1"));
		assertThat(streams.outText().toLowerCase(), containsString("translating server charset iso-8859-1 to local utf-8"));
		assertThat(streams.outText().toLowerCase(), containsString("read "+isoLength+" bytes"));
		assertThat(streams.outText().toLowerCase(), containsString("wrote "+utfLength+" bytes"));
		byte[] allBytes = Files.readAllBytes(Paths.get(temp));
		String content = new String(
			allBytes, "UTF-8");
		assertThat(content,not( startsWith("HTTP/1.1 200 OK")));
		assertThat(content,containsString(unicodeJunk));
		Files.deleteIfExists(Paths.get(temp));
		System.setProperty("file.encoding", saveCS);
	}
	
	
	
	@Test
	public void testBinary() throws Exception {
		
		String saveCS = System.getProperty("file.encoding");
		System.setProperty("file.encoding", "iso-8859-1");

		String temp = File.createTempFile("junit", "tmp").getAbsolutePath();
		assumeAndRun("http://localhost:9090/i18n/utf-8","-i","-n","-b","-o", temp);
		assertHeadersOnly();
		assumeThat( streams.outText().toLowerCase(), containsString("content-type: text/plain; charset=utf-8"));
		assertThat(streams.outText().toLowerCase(), not( containsString("translating server charset")));
		byte[] allBytes = Files.readAllBytes(Paths.get(temp));
		String content = new String(
			allBytes, "UTF-8");
		assertThat(content,not( startsWith("HTTP/1.1 200 OK")));
		assertThat(content,containsString(unicodeJunk));
		Files.deleteIfExists(Paths.get(temp));
		System.setProperty("file.encoding", saveCS);
	}

	@Test
	public void testBinaryByDefault() throws Exception {
		
		String saveCS = System.getProperty("file.encoding");
		System.setProperty("file.encoding", "iso-8859-1");

		String temp = File.createTempFile("junit", "tmp").getAbsolutePath();
		assumeAndRun("http://localhost:9090/i18n/utf-8-no-cs","-i","-n","-b","-o", temp);
		assertHeadersOnly();
		assumeThat( streams.outText().toLowerCase(), containsString("content-type: text/plain"));
		assertThat(streams.outText().toLowerCase(), not( containsString("translating server charset")));
		byte[] allBytes = Files.readAllBytes(Paths.get(temp));
		String content = new String(
			allBytes, "UTF-8");
		assertThat(content,not( startsWith("HTTP/1.1 200 OK")));
		assertThat(content,containsString(unicodeJunk));
		Files.deleteIfExists(Paths.get(temp));
		System.setProperty("file.encoding", saveCS);
	}

	
	
	
	@Test
	public void testPost() throws Exception {

		String temp = File.createTempFile("junit", "tmp").getAbsolutePath();
		try(FileOutputStream f = new FileOutputStream(temp)) {
			f.write(json.getBytes());
		}
		
		wireMockRule.stubFor(
				post(urlPathEqualTo("/login"))
				.withHeader("Content-type", equalTo("application/json"))
				.withRequestBody( equalTo(json))
			    .willReturn(aResponse()
			            .withHeader("Content-Type", "application/json")
			            .withStatus(200)
			            .withBody("{'success':true}")));

		assumeAndRun("http://localhost:9090/login","-i","-d",json);
		assertThat( streams.outText(), containsString("HTTP/1.1 200 OK"));
		assertThat( streams.outText(), containsString("Content-Type: application/json"));
		assertThat( streams.outText(), containsString("{'success':true}"));
		Files.deleteIfExists(Paths.get(temp));
	}

	@Test
	public void testContentType() throws Exception {
		
		wireMockRule.stubFor(
				post(urlPathEqualTo("/login"))
				.withHeader("Content-type", equalTo("text/json"))
				.withRequestBody( equalTo(json))
			    .willReturn(aResponse()
			            .withHeader("Content-Type", "application/json")
			            .withStatus(200)
			            .withBody("{'success':true}")));
		
		
		
		assumeAndRun("http://localhost:9090/login","-i","-d",json,
				"--content-type", "text/json");
		
		assertThat( streams.outText(), containsString("HTTP/1.1 200 OK"));
		assertThat( streams.outText(), containsString("Content-Type: application/json"));
		assertThat( streams.outText(), containsString("{'success':true}"));
		
	}

	@Test
	public void testPostFromFile() throws Exception {
		wireMockRule.stubFor(
				post(urlPathEqualTo("/login"))
				.withHeader("Content-type", equalTo("application/json"))
				.withRequestBody( equalTo(json))
			    .willReturn(aResponse()
			            .withHeader("Content-Type", "application/json")
			            .withStatus(200)
			            .withBody("{'success':true}")));

		assumeAndRun("http://localhost:9090/login","-i","-d",json);
		assertThat( streams.outText(), containsString("HTTP/1.1 200 OK"));
		assertThat( streams.outText(), containsString("Content-Type: application/json"));
		assertThat( streams.outText(), containsString("{'success':true}"));
	}
	
	
	@Test
	public void testWget() throws Exception {
		
		String temp = "delete_this_" + UUID.randomUUID().hashCode() + ".html";

		String serverpath = "/files/to/download/"+temp;

		wireMockRule.stubFor(
				get(urlPathEqualTo(serverpath))
				.willReturn(withBody));
		
		assumeTrue( !Files.exists(Paths.get(temp))); 
		assumeAndRun("http://localhost:9090"+serverpath,"--wget");
		assertHeadersOnly();
		String content = new String(
			Files.readAllBytes(Paths.get(temp)));
		assertThat(content,containsString(html));
		assertThat(content,not( startsWith("HTTP/1.1 200 OK")));
		Files.deleteIfExists(Paths.get(temp));
		assertTrue( !Files.exists(Paths.get(temp))); 
	}

	
	@Test
	public void testHeadersOnlyLong() throws Exception {
		assumeAndRun("http://localhost:9090","--include","--no-body");
		assertHeadersOnly();
	}
	@Test
	public void testHeadMethod() throws Exception {
		assumeAndRun("http://localhost:9090","--include","-X","HEAD");
		assertHeadersOnly();
		assertThat(streams.outText().toLowerCase(), containsString("etag: 12345678"));
		assertThat(streams.outText().toLowerCase(), containsString("etag: 12345678"));
	}
	
	@Test
	public void testHeadMethodLong() throws Exception {
		assumeAndRun("http://localhost:9090","--include","--request","HEAD");
		assertHeadersOnly();
	}
	
	@Test
	public void testZip() throws Exception {
		
		ByteArrayOutputStream bout = new ByteArrayOutputStream();
		GZIPOutputStream gout = new GZIPOutputStream(bout);
		gout.write(html.getBytes("UTF-8"));
		gout.finish();
		
		wireMockRule.stubFor(
				get(urlPathEqualTo("/"))
				.withHeader("Accept-Encoding", containing("gzip"))
				.willReturn(
					aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "text/html; charset=UTF-8")
						.withHeader("Content-Encoding", "gzip")
						.withHeader("etag", "12345678")
						.withBody(bout.toByteArray())
						));
		
		assumeAndRun("http://localhost:9090","-i","-g");
		assumeThat(streams.outText().toLowerCase(), containsString("content-encoding: gzip"));
		assertGoodHtmlWithHeaders();
		assertThat( streams.outText(), containsString("compressed bytes, inflated"));
	}
	
	@Test
	public void testSSLWithDataShort() throws Exception {
		assumeAndRun("ssl://localhost:9091","-k","-d",
				"GET / HTTP/1.1\nHost: localhost:9091\nConnection: close\n\n");
		assertSocketConnected();
		assertGoodHtmlWithHeaders();
	}

	
	@Test
	public void testSSLWithDataLong() throws Exception {
		assumeAndRun("ssl://localhost:9091","-k","--data",
				"GET / HTTP/1.1\nHost: localhost:9091\nConnection: close\n\n");
		assertSocketConnected();
		assertGoodHtmlWithHeaders();
	}
	
	@Test
	public void testSSLFromStdin() throws Exception {
		streams.setIn("GET / HTTP/1.1\r\nHost: localhost:9091\r\nConnection: close\r\n\r\n".getBytes());
		assumeAndRun("ssl://localhost:9091","-k");
		assertSocketConnected();
		assertGoodHtmlWithHeaders();
	}

	@Test
	public void testSSLFromStdinNoBody() throws Exception {
		streams.setIn("GET / HTTP/1.1\r\nHost: localhost:9091\r\nConnection: close\r\n\r\n".getBytes());
		assumeAndRun("ssl://localhost:9091","-n","-k");
		assertSocketConnected();
		assertHeadersOnly();
	}

	@Test
	public void testSSLDownload() throws Exception {
		String temp = File.createTempFile("junit", "tmp").getAbsolutePath();
		assumeAndRun("ssl://localhost:9091","-k","-d",
				"GET / HTTP/1.1\nHost: localhost:9091\nConnection: close\n\n",
				"--download", temp);
		assertHeadersOnly();
		String content = new String(
			Files.readAllBytes(Paths.get(temp)));
		assertThat(content,containsString(html));
		assertThat(content,not( startsWith("HTTP/1.1 200 OK")));
		Files.deleteIfExists(Paths.get(temp));
	}
	
	@Test
	public void testSSLDownloadAlt() throws Exception {
		String temp = File.createTempFile("junit", "tmp").getAbsolutePath();
		assumeAndRun("ssl://localhost:9091","-k","-d",
				"GET / HTTP/1.1\nHost: localhost:9091\nConnection: close\n\n",
				"-o", temp, "-n", "--skip-headers");
		assertHeadersOnly();
		String content = new String(
			Files.readAllBytes(Paths.get(temp)));
		assertThat(content,containsString(html));
		assertThat(content,not( startsWith("HTTP/1.1 200 OK")));
		Files.deleteIfExists(Paths.get(temp));
	}

	
	@Test
	public void testTCPWithDataShort() throws Exception {
		assumeAndRun("tcp://localhost:9090","-d",
				"GET / HTTP/1.1\nHost: localhost:9090\nConnection: close\n\n");
		assertSocketConnected();
		assertGoodHtmlWithHeaders();
	}

	
	@Test
	public void testTCPWithDataLong() throws Exception {
		assumeAndRun("tcp://localhost:9090","--data",
				"GET / HTTP/1.1\nHost: localhost:9090\nConnection: close\n\n");
		assertSocketConnected();
		assertGoodHtmlWithHeaders();
	}
	
	@Test
	public void testTCPFromStdin() throws Exception {
		streams.setIn("GET / HTTP/1.1\r\nHost: localhost:9090\r\nConnection: close\r\n\r\n".getBytes());
		assumeAndRun("tcp://localhost:9090");
		assertSocketConnected();
		assertGoodHtmlWithHeaders();
	}
	
	public void assertSocketConnected() {
		assertThat("SSL connected",
				streams.outText(),
				containsString("Connected to"));
	}

	public void assertGoodHtml() {
		assertThat("HTML returned",
				streams.outText(),
				containsString("<html"));
	}

	void assertGoodHtmlWithHeaders() {
		assertThat("HTTP OK",
				streams.outText(),
				containsString("HTTP/1.1 200 OK"));
		assertGoodHtml();
	}
	
	void assertHeadersOnly() {
		assertThat("HTTP OK",
				streams.outText(),
				containsString("HTTP/1.1 200 OK"));
		assertThat("HTML returned",
				streams.outText(),
				not( containsString("<html")));
	}
	
	@Test(expected=SSLHandshakeException.class)
	public void testHandshakeFail() throws Exception {
		assumeAndRun("https://localhost:9091");
	}

	@Test(expected=SSLHandshakeException.class)
	public void testHandshakeFailSSL() throws Exception {
		assumeAndRun("ssl://localhost:9091","-d",
				"GET / HTTP/1.1\nHost: localhost:9091\nConnection: close\n\n");
	}
	
	@Test
	public void testTrustAll() throws Exception {
		assumeAndRun("https://localhost:9091","-k");
	}
	
	@Test
	public void testTrustAllLong() throws Exception {
		assumeAndRun("https://localhost:9091","--insecure");
	}

	@Test
	public void testTrustAllSSL() throws Exception {
		assumeAndRun("ssl://localhost:9091","-k","-d",
				"GET / HTTP/1.1\nHost: localhost:9091\nConnection: close\n\n");
		assertSocketConnected();
		assertGoodHtml();
	}
	
	@Test
	public void testTrustAllSSLLong() throws Exception {
		assumeAndRun("ssl://localhost:9091","--insecure","-d",
				"GET / HTTP/1.1\nHost: localhost:9091\nConnection: close\n\n");
		assertSocketConnected();	
		assertGoodHtml();
		
	}

	
	@Test
	public void testPingHttpLong() throws Exception {
		testPing("http://localhost:9090","--ping");
	}
	@Test
	public void testPingHttpShort() throws Exception {
		testPing("http://localhost:9090","-z");
	}
	@Test
	public void testPingHttpsLong() throws Exception {
		testPing("https://localhost:9091","--ping");
	}
	@Test
	public void testPingHttpsShort() throws Exception {
		testPing("https://localhost:9091","-z");
	}
	@Test
	public void testPingSSLLong() throws Exception {
		testPing("ssl://localhost:9091","--ping");
	}
	@Test
	public void testPingSSLShort() throws Exception {
		testPing("ssl://localhost:9091","-z");
	}
	@Test
	public void testPingTCPLong() throws Exception {
		testPing("tcp://localhost:9090","--ping");
	}
	@Test
	public void testPingTCPShort() throws Exception {
		testPing("tcp://localhost:9090","-z");
	}
	
	
	public void testPing(String...args) throws Exception {
		assumeAndRun(args);
		assertThat("Port open",
				streams.outText(),
				containsString("is open"));
	}

	
	public void testPingLong() throws Exception {
		assumeAndRun("https://localhost:9091","-ping");
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
	
	
	
}