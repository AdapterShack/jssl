package com.adaptershack.jssl;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {

	public static byte[] readAll(HttpURLConnection connection, boolean useCharset) throws Exception {
		try ( InputStream is = getStream(connection)) {
	
			String charset = getCharset(connection);

			try {
				if( is != null && useCharset && charset != null ) {
	
					Log.log("Translating server charset %s to local %s",
							charset,
							System.getProperty("file.encoding"));
					
					return toByteArray(is, charset);
				
				}	if( is != null) {
					
					if(charset != null) {
						Log.log("Downloading server charset %s unmodified", charset);
					}
					
					return toByteArray(is);
		
				} else {
					return new byte[0];
				}
			} finally {
				if ( is instanceof CountingZipStream) {
					Log.log("Read %s compressed bytes", ((CountingZipStream) is).rawBytes());
				}
			}
		}
	}
	
	public static byte[] toByteArray(InputStream is, String charset) throws Exception {
		
		ByteArrayOutputStream bis = new ByteArrayOutputStream();
	
		// this uses our system charset
		OutputStreamWriter writer = new OutputStreamWriter(bis);
		
		// this uses the charset from the HTTP response
		InputStreamReader reader = new InputStreamReader(is,charset);
		
		int nRead;
		char[] data = new char[1024*16];
	
		while ((nRead = reader.read(data, 0, data.length)) != -1) {
		  writer.write(data, 0, nRead);
		}
	
		writer.flush();
		
		return bis.toByteArray();
	}

	public static byte[] toByteArray(InputStream is) throws IOException {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
	
		int nRead;
		byte[] data = new byte[16*1024];
	
		while ((nRead = is.read(data, 0, data.length)) != -1) {
		  buffer.write(data, 0, nRead);
		}
	
		buffer.flush();
	
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
					new BufferedReader(new InputStreamReader(stdin))
					.readLine();
			} catch (IOException e) {
				e.printStackTrace();
				return null;
			}
		}
	}

}
