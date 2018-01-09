package com.adaptershack.jssl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Paths;
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

public class JSSLClient {
	
	
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
	
	String sslProtocol = "TLSv1.2";
	
	private boolean followRedirects;
	private boolean useCaches;
	
	SSLSocketFactory socketFactory;
	
	private String alias;
	
	private String keystoreType = "PKCS12";
	private boolean includeHeaders;
	
	private boolean crlf;
	

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
		
		Socket socket = useSSL ? socketFactory.createSocket(host, port) 
				: new Socket(host,port);
		
		InputStream socketIn = socket.getInputStream();
		OutputStream socketOut = socket.getOutputStream();
		
		byte[] rawData = null;
		
		if(data != null) {
			rawData = unescapeJavaString(data).getBytes();
		} else if (dataFileName != null) {
			rawData = Files.readAllBytes(Paths.get(dataFileName));
		}
		
		if(rawData != null) {
			socketOut.write(rawData);
			socketOut.flush();
		}
		
		Thread fromServer = new Thread( new StreamTransferer(socketIn, stdout));
		Thread toServer = new Thread( new StreamTransferer(stdin, socketOut));
		
		fromServer.start();
		toServer.start();
		
		fromServer.join();
		
		toServer.interrupt();
		
		System.exit(0);
	}



	private void doURL(String urlString) throws IOException, MalformedURLException, Exception {
		HttpURLConnection connection = (HttpURLConnection)
				new URL(urlString).openConnection();
		
		HttpURLConnection.setFollowRedirects(followRedirects);
		connection.setUseCaches(useCaches);
		
		if(connection instanceof HttpsURLConnection) {
			((HttpsURLConnection) connection).setSSLSocketFactory(socketFactory);
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
		
		byte[] postData = null;
		
		if( dataFileName != null ) {
			postData = Files.readAllBytes(Paths.get(dataFileName));
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
				contentType = guessMore(postData);
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
		
		byte[] responseData = readAll(connection);
		
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
		
		stdout.write(responseData);
		stdout.flush();
	}



	private byte[] readAll(HttpURLConnection connection) throws Exception {
		try ( InputStream is = getStream(connection)) {
			if( is != null) {
				return toByteArray(is);
			} else {
				return new byte[0];
			}
		}
	}



	private InputStream getStream(HttpURLConnection connection) throws IOException {
		if(connection.getResponseCode() == 200) {
			return connection.getInputStream();
		} else {
			return connection.getErrorStream();
		}
	}



	private byte[] toByteArray(InputStream is) throws IOException {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();

		int nRead;
		byte[] data = new byte[16384];

		while ((nRead = is.read(data, 0, data.length)) != -1) {
		  buffer.write(data, 0, nRead);
		}

		buffer.flush();

		return buffer.toByteArray();		
	}



	private String guessMore(byte[] postData) {
		
		if(postData[0] == (byte) '{' || postData[0] == (byte) '[' ) {
			return "application/json";
		}
		
		return null;
	}



	private SSLSocketFactory createSocketFactory() throws Exception {
		KeyManager[] keyManagers = getKeyManagers();
		TrustManager[] trustManagers = getTrustManagers();

		if(keyManagers != null || trustManagers != null) {
			SSLContext sslContext = SSLContext.getInstance(this.sslProtocol);
			sslContext.init(keyManagers, trustManagers, null);
			return sslContext.getSocketFactory();
		} else {
			return (SSLSocketFactory) SSLSocketFactory.getDefault();
		}
		
	}



	private TrustManager[] getTrustManagers() {
		
		if(insecure) {
			HttpsURLConnection.setDefaultHostnameVerifier(new TrustingHostnameVerifier());	
			TrustManager[] tm = { new TrustingTrustManager() };
			return tm;
		}
		
		return null;
	}



	private KeyManager[] getKeyManagers() throws Exception {
		
		if(keystore != null) {

			if(keypass == null && System.console() != null) {
				keypass = System.console().readPassword("Key password: ");
			}
			
			if(keypass == null) {
				keypass= new char[0];
			}
			
			if(storepass == null) {
				storepass = keypass;
			}
			
			KeyStore ks = KeyStore.getInstance(keystoreType == null ? "PKCS12" : keystoreType);
			
			try(FileInputStream f = new FileInputStream(keystore)) {
				ks.load(f, storepass);
			}
			
			KeyManagerFactory kmf = KeyManagerFactory
					.getInstance(KeyManagerFactory.getDefaultAlgorithm());
			
			kmf.init(ks, keypass);
			
			KeyManager[] keyManagers = kmf.getKeyManagers();
			
			if(alias != null) {
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
	
	
	
	// boring getters/setters after here
	
	
	public String getSslProtocol() {
		return sslProtocol;
	}



	public void setSslProtocol(String sslProtocol) {
		if(sslProtocol != null) {
			this.sslProtocol = sslProtocol;
		}
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
	
	
    private String unescapeJavaString(String st) {

        StringBuilder sb = new StringBuilder(st.length());

        for (int i = 0; i < st.length(); i++) {
            char ch = st.charAt(i);
            if (ch == '\\') {
                char nextChar = (i == st.length() - 1) ? '\\' : st
                        .charAt(i + 1);
                // Octal escape?
                if (nextChar >= '0' && nextChar <= '7') {
                    String code = "" + nextChar;
                    i++;
                    if ((i < st.length() - 1) && st.charAt(i + 1) >= '0'
                            && st.charAt(i + 1) <= '7') {
                        code += st.charAt(i + 1);
                        i++;
                        if ((i < st.length() - 1) && st.charAt(i + 1) >= '0'
                                && st.charAt(i + 1) <= '7') {
                            code += st.charAt(i + 1);
                            i++;
                        }
                    }
                    sb.append((char) Integer.parseInt(code, 8));
                    continue;
                }
                switch (nextChar) {
                case '\\':
                    ch = '\\';
                    break;
                case 'b':
                    ch = '\b';
                    break;
                case 'f':
                    ch = '\f';
                    break;
                case 'n':
                    ch = '\n';
                    break;
                case 'r':
                    ch = '\r';
                    break;
                case 't':
                    ch = '\t';
                    break;
                case '\"':
                    ch = '\"';
                    break;
                case '\'':
                    ch = '\'';
                    break;
                // Hex Unicode: u????
                case 'u':
                    if (i >= st.length() - 5) {
                        ch = 'u';
                        break;
                    }
                    int code = Integer.parseInt(
                            "" + st.charAt(i + 2) + st.charAt(i + 3)
                                    + st.charAt(i + 4) + st.charAt(i + 5), 16);
                    sb.append(Character.toChars(code));
                    i += 5;
                    continue;
                }
                i++;
            }
            sb.append(ch);
        }
        return sb.toString();
    }	

}
