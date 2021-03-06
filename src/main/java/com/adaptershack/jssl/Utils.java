package com.adaptershack.jssl;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {

	private static final int COPY_BUFFER = 1024*16;

	public static byte[] readAll(HttpURLConnection connection, boolean useCharset) throws Exception {
		ByteArrayOutputStream bout = new ByteArrayOutputStream();
		readAll(connection,useCharset,bout);
		return bout.toByteArray();
	}
	
	public static void readAll(HttpURLConnection connection, boolean useCharset, OutputStream out) throws Exception {
		try ( InputStream is = getStream(connection)) {
	
			String charset = getCharset(connection);

			try {
				if( is != null && useCharset && charset != null ) {
	
					Log.log("Translating server charset %s to local %s",
							charset,
							System.getProperty("file.encoding"));
					
					copy(is, charset, out);
				
				}	if( is != null) {
					
					if(charset != null) {
						Log.log("Downloading server charset %s unmodified", charset);
					}
					
					copy(is,out);
		
				} 
			} finally {
				if ( is instanceof CountingZipStream) {
					@SuppressWarnings("resource")
					CountingZipStream is2 = (CountingZipStream) is;
					Log.log("Read %s compressed bytes, inflated to %s bytes (%.2f:1)",
							is2.getCompressed(), is2.getUncompressed(),
							is2.getRatio()
							);
				} else if ( is instanceof ProgressInputStream) {
					Log.log("Read %d bytes", ((ProgressInputStream)is).count);
				}
			}
		}
	}
	
	public static void copy(InputStream is, String charset,OutputStream out) throws Exception {
		
		// this uses our system charset
		OutputStreamWriter writer = new OutputStreamWriter(out,
				System.getProperty("file.encoding"));
		
		// this uses the charset from the HTTP response
		InputStreamReader reader = new InputStreamReader(is,charset);
		
		int nRead;
		char[] data = new char[COPY_BUFFER];
	
		while ((nRead = reader.read(data, 0, data.length)) != -1) {
		  writer.write(data, 0, nRead);
		}
	
		writer.flush();
		
	}

	public static void copy(InputStream is,OutputStream out) throws IOException {
		int nRead;
		byte[] data = new byte[COPY_BUFFER];
	
		while ((nRead = is.read(data, 0, data.length)) != -1) {
		  out.write(data, 0, nRead);
		}
	
		out.flush();
	}
	
	
	public static byte[] toByteArray(InputStream is) throws IOException {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		copy(is,buffer);
		return buffer.toByteArray();		
	}

	public static InputStream getStream(HttpURLConnection connection) throws IOException {
	
		InputStream is;
		
		if(connection.getResponseCode() == 200) {
			is = connection.getInputStream();
		} else {
			is = connection.getErrorStream();
		}
		
		if(is==null) {
			return null;
		}
		
		is = new ProgressInputStream( connection.getContentLength(), is);
		
		return "gzip".equals(connection.getContentEncoding()) ?
				new CountingZipStream(is) : is;
		
	}

	public static String replaceCRLF(String unescaped) {
		return unescaped.replaceAll("(?<!\r)\n", "\r\n");
	}

	public static String getCharset(HttpURLConnection connection) {
		String contentType = connection.getContentType();
		
		if(contentType == null) {
			return null;
		}
		
		String regex = "(?i)\\bcharset=\\s*\"?([^\\s;\"]*)";
		
		Matcher m = Pattern.compile(regex).matcher(contentType);
		if (m.find()) {
			return m.group(1).trim().toUpperCase();
		}
		return null;
		
	}

	public static String guessMore(byte[] postData) {
		
		if(postData[0] == (byte) '{' || postData[0] == (byte) '[' ) {
			return "application/json";
		}
		
		return null;
	}

	public static String unescapeJavaString(String st) {
	
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

	public static byte[] readAll(String filename) throws IOException {
		return Files.readAllBytes(Paths.get(filename));
	}

	public static char[] passwordPrompt(String prompt, PrintStream stdout, InputStream stdin) {
	
		if(System.console() != null) {
			return System.console().readPassword(prompt);
		} else {
			Log.log("WARNING: unable to get console, passwords will be echoed");
			
			if(isUnixOnWindows()) {
				Log.log("WARNING: consider running winpty");
			}
			
			String response = prompt(prompt,stdout,stdin);
			return response == null ? null : response.toCharArray();
		}
	}

	private static boolean isUnixOnWindows() {
		return
			System.getProperty("os.name") != null &&
			System.getProperty("os.name").contains("Windows") &&
			System.getenv().containsKey("SHELL") &&
			System.getenv().get("SHELL").contains("sh.exe");
	}

	public static void dumpEnvironment(PrintStream stdout) {
		try {
			System.getProperties().store(stdout, "System Props");
			
			Properties env = new Properties();
			env.putAll(System.getenv());

			env.store(stdout, "Environment");
			
		} catch (IOException e) {
		}
	}

	public static String prompt(String prompt, PrintStream stdout, InputStream stdin) {
		if(System.console() != null) {
			return System.console().readLine(prompt);
		} else {
			stdout.print(prompt);
			try {
				return 
					new BufferedReader(new InputStreamReader(stdin),1)
					.readLine();
			} catch (IOException e) {
				e.printStackTrace();
				return null;
			}
		}
	}

	public static boolean reachable(String u) {
		try {
			ping(u);
			return true;
		} catch (Exception e) {
			return false;
		}
	}
	
	public static void ping(String u) throws Exception {
		URI uri = new URI(u);
		String host = uri.getHost();
		int port = uri.getPort();
		if(port == -1) {
			String scheme = uri.getScheme();
			if( scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("ssl")) {
				port = 443;
			} else {
				port = 80;
			}
		}
		ping(host, port);
	}

	
	public static void ping(String host, int port) throws UnknownHostException, IOException {
		try( Socket s = new Socket(host, port) ) {
			// it worked!
		}
	}

}
