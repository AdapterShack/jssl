package com.adaptershack.jssl;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import javax.security.auth.x500.X500Principal;

import java.security.cert.X509Certificate;

import static com.adaptershack.jssl.Log.*;
import static com.adaptershack.jssl.HeaderAwareOutputStream.*;

public class JSSLClient {
	
	
	private static final int DEFAULT_BUFFER = 1024;
	public static final String DEFAULT_PROTOCOL = "TLSv1.2";
	boolean useSSL;
	boolean useSocket;
	boolean insecure;
	
	String keystore;
	char[] keypass;
	char[] storepass;
	
	String trustStore;
	char[] trustpass;
	
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
	
	private String userInfo;
	private String saveCertsFile;
	private char[] saveStorePassword ;
	private String saveKeystoreType;
	private int saveChainLength = -1;
	
	private boolean ping;
	private boolean noAuth;
	private String proxy;
	private boolean socks;
	
	private int listenPort;
	
	


	public void run (String urlString) throws Exception {
		
		checkSetup();
		
		parseUrl(urlString);

		if(ping) {
			try {
				Utils.ping(host,port);
				Log.log("%s is open", urlString);
			} catch (Exception e) {
				e.printStackTrace(stdout);
			}
			return;
		}

		
		if(useSSL) {
			socketFactory = createSocketFactory();
		}
		
		
		if( useSocket ) {
			
			if(listenPort > 0) {
				doTunnel();
			} else {
				doSocket();
			}
			
		} else {
			
			doURL(urlString);
		}
		
	}



	private void checkSetup() {
		
		if(stdin == null) {
			stdin = System.in;
		}
		
		if(stdout == null) {
			stdout = System.out;
		}
		
		Log.setOut(stdout);
		
	}


	private void doTunnel() {
		
		Log.log("Listening on port %d", listenPort);
		
		SocketListener l = new SocketListener(listenPort,
			(localSocket) -> 	{
				try (Socket remoteSocket = createSocket()) {

					Log.log("Connection from %s, tunnel to %s:%d",
							localSocket.getInetAddress().toString(), this.host, this.port);
					
					InputStream fromClient = localSocket.getInputStream();
					OutputStream toClient =   localSocket.getOutputStream();
					InputStream fromServer = remoteSocket.getInputStream();
					OutputStream toServer = remoteSocket.getOutputStream();
					
					Thread up = new Thread ( new BinaryStreamTransferer(fromClient, toServer, null, false, bufsize));
					Thread down = new Thread ( new BinaryStreamTransferer(fromServer, toClient, null, false, bufsize));
					
					up.start();
					down.start();
					
					down.join();
					up.interrupt();
				}
			}
		);
		
		l.start();
	}

	private void doSocket() throws IOException, UnknownHostException, InterruptedException, KeyStoreException, NoSuchAlgorithmException, CertificateException, InvalidNameException {
		
		log("Opening " + (useSSL ? "SSL " : " ") + "socket to " + host + ":" + port );
		
		Socket socket = createSocket();
		
		try(
			InputStream socketIn = socket.getInputStream();
			OutputStream socketOut = socket.getOutputStream();

			FileOutputStream fileOut = 
					outFileName == null ? null : new FileOutputStream(outFileName);
				
		) {			

			if(saveCertsFile != null && socket instanceof SSLSocket) {
				Certificate[] certs = ((SSLSocket) socket).getSession().getPeerCertificates();
				saveCerts(certs);
			}
			
			CountingOutputStream counter =
					fileOut == null ? null : new CountingOutputStream(fileOut);

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
		
		if(System.getProperty("no-exit")==null) {
			System.exit(0);
		}
	}



	public Socket createSocket() throws IOException, UnknownHostException {
		return useSSL ? socketFactory.createSocket(host, port) 
				: new Socket(host,port);
	}



	private void doURL(String urlString) throws IOException, MalformedURLException, Exception {

		if(!noAuth) {
			Authenticator.setDefault(new CustomAuthenticator());
		}
		
		log("executing URL: %s", urlString);

		HttpURLConnection connection = (HttpURLConnection)
				( proxy != null ? 
						new URL(urlString).openConnection(createProxy()) :
						new URL(urlString).openConnection());
		
		connection.setUseCaches(useCaches);
		connection.setInstanceFollowRedirects(followRedirects);
		
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

		//connection.connect();
				
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

		if( saveCertsFile != null && connection instanceof HttpsURLConnection) {
			connection.connect();
			Certificate[] chain = ((HttpsURLConnection) connection).getServerCertificates();
			saveCerts(chain);
		}

		boolean readWholeResponse = printBody && Log.enabled();
		boolean streamBody = printBody && !readWholeResponse;
		
		byte[] wholeResponse = null;
		
		if(readWholeResponse) {
			Log.log("Entire response will be read before printing");
			wholeResponse = Utils.readAll(connection, !binary);
			
			if(outFileName != null) {
				try(FileOutputStream fout = new FileOutputStream(outFileName)) {
					fout.write(wholeResponse);
					log("Wrote %d bytes to %s",wholeResponse.length,outFileName);
				}
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

		if(readWholeResponse) {
			
			stdout.write(wholeResponse);
			
		} else if(outFileName != null) {
			
			try(FileOutputStream fout = new FileOutputStream(outFileName)) {
				final CountingOutputStream counter = new CountingOutputStream(fout);

				OutputStream finalOut = printBody ? 
					new OutputStream() {
						@Override
						public void write(int b) throws IOException {
							counter.write(b);
							stdout.write(b);
						}
				} : counter;

				Utils.readAll(connection, !binary, finalOut);
				log("Wrote %d bytes to %s",counter.getCount(),outFileName);
			}
			
		} else if(streamBody) {
		
			Utils.readAll(connection, !binary, stdout);
		
		}
		
		
		stdout.flush();
		
	}


	private Proxy createProxy() {
		
		String proxyHost;
		int proxyPort;
		
		if(proxy.contains(":")) {
			String[] parts = proxy.split(":",2);
			proxyHost = parts[0];
			proxyPort = Integer.parseInt(parts[1]);
		} else {
			throw new IllegalArgumentException("Proxy must be host:port");
		}
	
		Log.log("Proxy host %s port %d", proxyHost, proxyPort);
		
		return new Proxy( socks ?
				Proxy.Type.SOCKS : Proxy.Type.HTTP,
				new InetSocketAddress(proxyHost,proxyPort)
			);
		
	}



	public void saveCerts(Certificate[] chain) throws KeyStoreException, IOException, NoSuchAlgorithmException,
			CertificateException, FileNotFoundException, InvalidNameException {
		if(chain.length > 0) {

			 String type = saveKeystoreType == null ? "PKCS12" : saveKeystoreType;
			 
			 CertWriter certWriter = CertWriter.getInstance(type);
			 
			 if(certWriter.supportsPassword() && saveStorePassword == null) {
				 saveStorePassword = Utils.passwordPrompt("Password to save certs: ", stdout, stdin);
			 }
			 
			 if(Files.isRegularFile(Paths.get(saveCertsFile))) {
				 try(FileInputStream f = new FileInputStream(saveCertsFile)) {
					 certWriter.load(f, saveStorePassword);
				 }
			 } else {
				 certWriter.load(null, null);
			 }
			 
			 int count = 0;
			 
			 for(Certificate cert : chain) {
				 if(cert instanceof X509Certificate) {
					 String certAlias = chooseAlias((X509Certificate) cert);
					 Log.log("Saving alias [%s] cert %s", certAlias, namesMap(cert));
					 certWriter.setCertificateEntry(certAlias, cert);
					 
					 if( ++count == saveChainLength  ) {
						 break;
					 }
				 }
			 }

			 
			 try(FileOutputStream f = new FileOutputStream(saveCertsFile)) {
				 certWriter.store(f, saveStorePassword );
				 Log.log("Wrote %d certificates to %s", count, saveCertsFile);
			 }
		}
	}


	public String chooseAlias(X509Certificate cert) throws InvalidNameException {
		Map<String, String> names = getNames(cert.getSubjectX500Principal());
		
		return names.get("CN");
		
	}
	

	public Map<String, Map<String, String>> namesMap(Certificate cert) throws InvalidNameException {
		Map<String, Map<String, String>> names = new LinkedHashMap<>();
		X509Certificate x509 = (X509Certificate) cert;
		names.put("Subject", getNames(x509.getSubjectX500Principal()));
		names.put("Issuer", getNames(x509.getIssuerX500Principal()));
		 
		return names;
	}



	public Map<String, String> getNames(X500Principal principal) throws InvalidNameException {
		LdapName ldn = new LdapName(principal.getName());

		 Map<String,String> names = new HashMap<>();
		 for( Rdn rdn : ldn.getRdns()) {
			 names.put(rdn.getType(), rdn.getValue().toString());
		 }
		return names;
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



	private TrustManager[] getTrustManagers() throws NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException {
		
		if(insecure) {
			log("Creating TrustManager");
			HttpsURLConnection.setDefaultHostnameVerifier(new TrustingHostnameVerifier());	
			TrustManager[] tm = { new TrustingTrustManager() };
			logEach("TrustManagers: ",tm);
			return tm;
		
		} else if (trustStore != null) {
			
			KeyStore myTrustStore = KeyStore.getInstance(KeyStore.getDefaultType());

			try(FileInputStream f = new FileInputStream(trustStore)) {
				myTrustStore.load(f, trustpass);
			}
			
			TrustManagerFactory keystoreTMF = TrustManagerFactory
			    .getInstance(TrustManagerFactory.getDefaultAlgorithm());
			keystoreTMF.init(myTrustStore);				

			TrustManager[] keystoreTM = keystoreTMF.getTrustManagers();
			
			TrustManagerFactory defaultTMF = TrustManagerFactory
				    .getInstance(TrustManagerFactory.getDefaultAlgorithm());

			defaultTMF.init((KeyStore) null);
			
			TrustManager[] tm = defaultTMF.getTrustManagers();
			
			for(int i=0; i<tm.length; i++) {
				tm[i] = new TwoLevelTrustManager((X509TrustManager) tm[0], keystoreTM).getProxy();
			}
			
			Log.logEach("Trust Managers: ", tm);
			
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
			
			logEach("KeyManagers: ",keyManagers);

			if(alias != null) {
				log("Using alias " + alias);
				for( int i=0; i< keyManagers.length; i++) {
					keyManagers[i] = new CustomKeyManager(keyManagers[i],  alias).getProxy();
				}
			}
			
			return keyManagers;
			
		}
		
		return null;
	}


	public boolean isSocketUrl(String u) {
		return u.startsWith("ssl") || u.startsWith("tcp");
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



	public String getSaveCertsFile() {
		return saveCertsFile;
	}



	public void setSaveCertsFile(String saveCertsFile) {
		this.saveCertsFile = saveCertsFile;
	}



	public char[] getSaveStorePassword() {
		return saveStorePassword;
	}



	public void setSaveStorePassword(String _saveStorePassword) {
		if(_saveStorePassword != null ) {
			this.saveStorePassword = _saveStorePassword.toCharArray();
		}
	}



	public String getSaveKeystoreType() {
		return saveKeystoreType;
	}



	public void setSaveKeystoreType(String saveKeystoreType) {
		this.saveKeystoreType = saveKeystoreType;
	}



	public int getSaveChainLength() {
		return saveChainLength;
	}



	public void setSaveChainLength(int saveChainLength) {
		this.saveChainLength = saveChainLength;
	}



	public boolean isPing() {
		return ping;
	}



	public void setPing(boolean ping) {
		this.ping = ping;
	}

	public boolean isGzip() {
		return gzip;
	}



	public void setGzip(boolean gzip) {
		this.gzip = gzip;
	}



	public String getTrustStore() {
		return trustStore;
	}



	public void setTrustStore(String trustStore) {
		this.trustStore = trustStore;
	}



	public void setTrustpass(String s) {
		if(s != null) {
			this.trustpass = s.toCharArray();
		}
	}



	public boolean isNoAuth() {
		return noAuth;
	}



	public void setNoAuth(boolean noAuth) {
		this.noAuth = noAuth;
	}



	public String getProxy() {
		return proxy;
	}



	public void setProxy(String proxy) {
		this.proxy = proxy;
	}



	public boolean isSocks() {
		return socks;
	}



	public void setSocks(boolean socks) {
		this.socks = socks;
	}



	public int getListenPort() {
		return listenPort;
	}



	public void setListenPort(int listenPort) {
		this.listenPort = listenPort;
	}

	
	
}
