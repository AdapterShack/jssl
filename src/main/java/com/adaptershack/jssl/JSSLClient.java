package com.adaptershack.jssl;

import static com.adaptershack.jssl.HeaderAwareOutputStream.*;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.PasswordAuthentication;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.security.KeyStore;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509KeyManager;

import static com.adaptershack.jssl.Log.*;

public class JSSLClient {
	
	
	private static final int DEFAULT_BUFFER = 1024;
	public static final String DEFAULT_PROTOCOL = "TLSv1.2";
	boolean useSSL;
	boolean useSocket;
	boolean insecure;
	
	String keystore;
	char[] keypass;
	char[] storepass;
	
	String[] headers;
	
	String data;
	String dataFileName;
	
	String contentType;
	
	InputStream stdin;
	PrintStream stdout;
	private String host;
	private int port;
	
	String sslProtocol = DEFAULT_PROTOCOL;
	
	private boolean followRedirects;
	private boolean useCaches;
	
	SSLSocketFactory socketFactory;
	
	private String alias;
	
	private String keystoreType = "PKCS12";
	private boolean includeHeaders;
	
	private boolean crlf;
	private boolean printBody = true;
	
	private String outFileName;
	
	private String method;
	
	private boolean binary;
	
	private int bufsize = DEFAULT_BUFFER;
	
	private boolean skipHeaders;
	
	private boolean gzip;
	private boolean useCharset;
	
	private String userInfo;
	
	
	public boolean isGzip() {
		return gzip;
	}



	public void setGzip(boolean gzip) {
		this.gzip = gzip;
	}



	public void run (String urlString) throws Exception {

		parseUrl(urlString);
		
		if(useSSL) {
			socketFactory = createSocketFactory();
		}
		
		
		if( useSocket ) {
			
			doSocket();
			
			
		} else {
			
			doURL(urlString);
		}
		
	}



	private void doSocket() throws IOException, UnknownHostException, InterruptedException {
		
		log("Opening " + (useSSL ? "SSL " : " ") + "socket to " + host + ":" + port );
		
		Socket socket = useSSL ? socketFactory.createSocket(host, port) 
				: new Socket(host,port);
		
		try(
			InputStream socketIn = socket.getInputStream();
			OutputStream socketOut = socket.getOutputStream();

			FileOutputStream fileOut = 
					outFileName == null ? null : new FileOutputStream(outFileName);
				
		) {			

			CountingStream counter =
					fileOut == null ? null : new CountingStream(fileOut);

			OutputStream copyOut =
				counter == null ? null :
					skipHeaders ? skipHeaders(counter) : counter;
			
			byte[] rawData = null;
	
			banner("Connected to " + host + ":" + port);
			
			if(data != null) {
				String unescaped = Utils.unescapeJavaString(data);
				
				if(crlf) {
					unescaped = Utils.replaceCRLF(unescaped);
				}
				
				rawData = unescaped.getBytes();
				
				
			} else if (dataFileName != null) {
				rawData = Utils.readAll(dataFileName);
				
				if(crlf) {
					String stringData = new String(rawData);
					rawData = Utils.replaceCRLF(stringData).getBytes();
				}
			}
			
			if(rawData != null) {
				socketOut.write(rawData);
				socketOut.flush();
				log("Wrote " + rawData.length + " bytes to server");
			}
			
			Thread fromServer, toServer;
			
			OutputStream stdout1;
			if( printBody ) {
				stdout1 = stdout;
			} else {
				log("Console will only show HTTP headers up to first blank line.");
				stdout1 = skipBody(stdout);
			}
			
			if(binary) {
				fromServer = new Thread( new BinaryStreamTransferer(socketIn, stdout1, copyOut, false, bufsize));
				toServer = new Thread( new BinaryStreamTransferer(stdin, socketOut, null, crlf, bufsize));
			} else {
				fromServer = new Thread( new TextStreamTransferer(socketIn, stdout1, copyOut, false));
				toServer = new Thread( new TextStreamTransferer(stdin, socketOut, null, crlf));
			}
			
			fromServer.start();
			toServer.start();
			
			fromServer.join();

			banner("Connection closed by server");
			
			if(counter != null) {
				log("Wrote " + counter.getCount() + " bytes to " + outFileName);
			}
			
			toServer.interrupt();
		}
		
		System.exit(0);
	}



	private void doURL(String urlString) throws IOException, MalformedURLException, Exception {

		Authenticator.setDefault(new CustomAuthenticator());
		HttpURLConnection.setFollowRedirects(followRedirects);

		log("executing URL: " + urlString);
		
		HttpURLConnection connection = (HttpURLConnection)
				new URL(urlString).openConnection();
		
		connection.setUseCaches(useCaches);
		
		if(connection instanceof HttpsURLConnection) {
			((HttpsURLConnection) connection).setSSLSocketFactory(socketFactory);
		}
		
		if(gzip) {
			connection.setRequestProperty("Accept-Encoding", "gzip");
		}
		
		if(headers != null) {
			for(String h : headers) {
				if(h.contains(":")) {
					String[] pieces = h.split(":", 2);
					String key = pieces[0].trim();
					String value = pieces[1].trim();
					connection.setRequestProperty(key, value);
				}
			}
		}

		if(method != null) {
			connection.setRequestMethod(method);
		}
		
		byte[] postData = null;
		
		if( dataFileName != null ) {
			postData = Utils.readAll(dataFileName);
			if(contentType == null) {
				contentType = HttpURLConnection.guessContentTypeFromName(dataFileName);
			}
		}

		if(postData == null && this.data != null) {
			postData = data.getBytes();
		}
		
		if(postData != null) {

			if(contentType == null) {
				contentType = HttpURLConnection.guessContentTypeFromStream(
						new ByteArrayInputStream(postData));
			}
			if(contentType == null) {
				contentType = Utils.guessMore(postData);
			}
			if(contentType != null) {
				connection.setRequestProperty("content-type", contentType);
			}

			connection.setDoOutput(true);
			
			try(OutputStream out = connection.getOutputStream()) {
				out.write(postData);
				out.flush();
			}
		}
		
		byte[] responseData = Utils.readAll(connection, useCharset);
		
		log("Read " + responseData.length + " bytes");

		if(outFileName != null) {
			try(FileOutputStream fout = new FileOutputStream(outFileName)) {
				fout.write(responseData);
				log("Wrote " + responseData.length + " bytes to " + outFileName);
			}
		}
		
		banner("Response:");
		
		if( this.includeHeaders ) {
			Map<String, List<String>> m = connection.getHeaderFields();
			if( m.containsKey(null)) {
				for(String v : m.get(null)) {
					stdout.println(v);
				}
			}
			for( Entry<String, List<String>> e : m.entrySet() ) {
				if( e.getKey() != null) {
					for( String v : e.getValue() ) {
						stdout.println(e.getKey() + ": " + v);
					}
				}
			}
			stdout.println();
		}
		
		if(printBody) {
			stdout.write(responseData);
		}
		stdout.flush();
		
	}



	private SSLSocketFactory createSocketFactory() throws Exception {
		KeyManager[] keyManagers = getKeyManagers();
		TrustManager[] trustManagers = getTrustManagers();

		SSLSocketFactory socketFactory;
		
		if(keyManagers != null || trustManagers != null) {
			log("Creating SSLContext for " + sslProtocol);
			SSLContext sslContext = SSLContext.getInstance(this.sslProtocol);
			sslContext.init(keyManagers, trustManagers, null);
			log("Creating SSLSocketFactory");
			socketFactory = sslContext.getSocketFactory();
		} else {
			log("Using default SSLSocketFactory");
			socketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
		}
		
		log("SocketFactory = " + socketFactory.getClass().getName());
		return socketFactory;
		
	}



	private TrustManager[] getTrustManagers() {
		
		if(insecure) {
			log("Creating TrustManager");
			HttpsURLConnection.setDefaultHostnameVerifier(new TrustingHostnameVerifier());	
			TrustManager[] tm = { new TrustingTrustManager() };
			log("TrustManagers: ",tm);
			return tm;
		}
		
		return null;
	}



	private KeyManager[] getKeyManagers() throws Exception {
		
		if(keystore != null) {

			if(keypass == null ) {
				keypass = Utils.passwordPrompt("Key password: ",stdout,stdin);
			}
			
			if(keypass == null) {
				keypass= new char[0];
			}
			
			if(storepass == null) {
				storepass = keypass;
			}
			
			KeyStore ks = KeyStore.getInstance(keystoreType == null ? "PKCS12" : keystoreType);
			
			try(FileInputStream f = new FileInputStream(keystore)) {
				log("Loading keystore");
				ks.load(f, storepass);
			}
			
			KeyManagerFactory kmf = KeyManagerFactory
					.getInstance(KeyManagerFactory.getDefaultAlgorithm());

			log("KeyManagerFactory = " + kmf.getClass().getName());
			
			kmf.init(ks, keypass);
			
			log("Creating KeyManagers");

			KeyManager[] keyManagers = kmf.getKeyManagers();
			
			log("KeyManagers: ",keyManagers);

			if(alias != null) {
				log("Using alias " + alias);
				for( int i=0; i< keyManagers.length; i++) {
					keyManagers[i] = new CustomKeyManager((X509KeyManager) keyManagers[i],alias);
				}
			}
			
			return keyManagers;
			
		}
		
		return null;
	}



	private void parseUrl(String urlString) throws URISyntaxException {
		URI uri = new URI(urlString);
		
		String scheme = uri.getScheme();
		
		userInfo = uri.getUserInfo();

		if( scheme.equalsIgnoreCase("https")) {
			useSSL = true;
			useSocket = false;
		} else if (scheme.equalsIgnoreCase("http")) {
			useSSL = false;
			useSocket = false;
		} else if (scheme.equalsIgnoreCase("ssl")) {
			useSSL = true;
			useSocket = true;
		} else if (scheme.equalsIgnoreCase("tcp")) {
			useSSL = false;
			useSocket = true;
		}

		host = uri.getHost();
		port = uri.getPort();

		if(port == -1) {
			port = useSSL ? 443 : 80;
		}
	}
	
	private class CustomAuthenticator extends Authenticator {

		@Override
		protected PasswordAuthentication getPasswordAuthentication() {

			Log.banner("Password authentication required: " 
			  + this.getRequestingPrompt());
			
			String user = null;
			char[] password = null;
			
			if(userInfo != null ) {
				
				if(userInfo.contains(":")) {
					String[] parts = userInfo.split(":",2);
					user = parts[0];
					password = parts[1].toCharArray();
				} else {
					user = userInfo;
				}
			}

			if( user == null ) {
				user = Utils.prompt("User: ",stdout,stdin);
			}
			if( password == null ) {
				password = Utils.passwordPrompt("Password: ",stdout,stdin);
			}
			
			
			if(user != null && password != null) {
				return new PasswordAuthentication(user,password);
			} else {
				return null;
			}
		}

		
	}
	

	
	
	// boring getters/setters after here
	
	public boolean isUseCharset() {
		return useCharset;
	}



	public void setUseCharset(boolean useCharset) {
		this.useCharset = useCharset;
	}	
	
	public String getSslProtocol() {
		return sslProtocol;
	}



	public void setSslProtocol(String _sslProtocol) {
		this.sslProtocol = _sslProtocol;
	}

	public InputStream getStdin() {
		return stdin;
	}



	public void setStdin(InputStream stdin) {
		this.stdin = stdin;
	}



	public OutputStream getStdout() {
		return stdout;
	}



	public void setStdout(OutputStream _stdout) {
		if(_stdout instanceof PrintStream) {
			stdout = (PrintStream) _stdout;
		} else {
			stdout = new PrintStream(_stdout);
		}
	}





	public boolean isInsecure() {
		return insecure;
	}

	public void setInsecure(boolean insecure) {
		this.insecure = insecure;
	}

	public String getKeystore() {
		return keystore;
	}

	public void setKeystore(String keystore) {
		this.keystore = keystore;
	}


	public void setKeypass(String _keypass) {
		if(_keypass != null) {
			this.keypass = _keypass.toCharArray();
		}
	}


	public void setStorepass(String _storepass) {
		if( _storepass != null) {
			this.storepass = _storepass.toCharArray();
		}
	}

	public String[] getHeaders() {
		return headers;
	}

	public void setHeaders(String[] headers) {
		this.headers = headers;
	}

	public String getData() {
		return data;
	}

	public void setData(String data) {
		this.data = data;
	}

	public String getDataFileName() {
		return dataFileName;
	}

	public void setDataFileName(String dataFileName) {
		this.dataFileName = dataFileName;
	}

	public String getContentType() {
		return contentType;
	}

	public void setContentType(String contentType) {
		this.contentType = contentType;
	}



	public boolean isFollowRedirects() {
		return followRedirects;
	}



	public void setFollowRedirects(boolean followRedirects) {
		this.followRedirects = followRedirects;
	}



	public boolean isUseCaches() {
		return useCaches;
	}



	public void setUseCaches(boolean useCaches) {
		this.useCaches = useCaches;
	}



	public String getAlias() {
		return alias;
	}



	public void setAlias(String alias) {
		this.alias = alias;
	}



	public String getKeystoreType() {
		return keystoreType;
	}



	public void setKeystoreType(String keystoreType) {
		this.keystoreType = keystoreType;
	}



	public boolean isIncludeHeaders() {
		return includeHeaders;
	}



	public void setIncludeHeaders(boolean includeHeaders) {
		this.includeHeaders = includeHeaders;
	}



	public boolean isCrlf() {
		return crlf;
	}



	public void setCrlf(boolean crlf) {
		this.crlf = crlf;
	}
	
	
    public boolean isPrintBody() {
		return printBody;
	}



	public void setPrintBody(boolean printBody) {
		this.printBody = printBody;
	}



	public String getOutFileName() {
		return outFileName;
	}



	public void setOutFileName(String outFileName) {
		this.outFileName = outFileName;
	}



	public String getMethod() {
		return method;
	}



	public void setMethod(String method) {
		this.method = method;
	}



	public boolean isBinary() {
		return binary;
	}



	public void setBinary(boolean binary) {
		this.binary = binary;
	}



	public int getBufsize() {
		return bufsize;
	}



	public void setBufsize(int bufsize) {
		this.bufsize = bufsize;
	}



	public boolean isSkipHeaders() {
		return skipHeaders;
	}



	public void setSkipHeadersInOutfile(boolean skipHeaders) {
		this.skipHeaders = skipHeaders;
	}

}