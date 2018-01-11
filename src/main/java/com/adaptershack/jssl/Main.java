package com.adaptershack.jssl;

import java.io.File;
import java.net.URI;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;

/**
 * Hello world!
 *
 */
public class Main  {

	public static void main( String[] args ) throws Exception {
        
		Options options = new Options();

		// common options
		options.addOption("s","silent", false, "silences progress messages");
		options.addOption("k", "insecure", false, "ignore SSL validation errors");
		options.addOption(null, "keystore", true, "custom keystore for client certs, if any");
		options.addOption(null, "keypass", true, "password to extract key from store");
		options.addOption(null, "storepass", true, "password to open keystore");			
		options.addOption("o","out-file",true,"write response body to file");
		options.addOption("d", "data", true, "data to be posted to server");
		options.addOption("f", "file", true, "file to be posted to server");
		options.addOption("X", "request", true, "force request method");
		options.addOption("p", "ssl-protocol", true, "protocol to intialize SSLContext");
		options.addOption(null,"alias",true,"use alias from keystore");
		options.addOption(null,"keystore-type", true, "keystore type (default PKCS12)");
		options.addOption("b","binary",false,"disables charset conversions and retrives "
				+ "content from the server byte for byte");

		// options only for HTTP(s)
		options.addOption(null, "content-type", true, "force content type");
		options.addOption("n","no-body",false,"skip printing out the actual response body");
		options.addOption("L", "location", false, "follow redirect");
		options.addOption(null, "no-cache", false, "disallow caching");
		options.addOption("i","include", false, "include response headers in output");
		options.addOption("H", "header", true, "add custom HTTP header(s)");
		options.addOption("g","gzip", false, "request gzip content-encoding");
		options.addOption("w","wget",false,"auto-download file with name taken from url");
		
		// options only for sockets
		options.addOption(null,"crlf",false,"in socket mode, perform outbound CRLF translation");
		options.addOption(null,"buffer",true,"buffer size for binary mode (default 1024)");
		options.addOption(null,"skip-headers",false,"in socket mode, omit headers from out-file");

		options.addOption(null,"download",true,"print only headers, write only body to file "
				+ "(equivalent to -i -n -o for http, -b -n --skip-headers -o for socket)");
		
		
        DefaultParser parser = new DefaultParser();
        
        CommandLine cmdLine = parser.parse(options, args);

        if( cmdLine.getArgList().isEmpty() ) {
        	new HelpFormatter().printHelp("java -jar [this-jar-file] [options] url", "Options", options, "", false);
        	return;
        }

        Log.setQuiet(cmdLine.hasOption("s"));
        
        JSSLClient client = new JSSLClient();
        
        client.setStdin(System.in);
        client.setStdout(System.out);
        
        client.setContentType(cmdLine.getOptionValue("content-type"));
        client.setData(cmdLine.getOptionValue("data"));
        client.setDataFileName(cmdLine.getOptionValue("file"));
        client.setHeaders(cmdLine.getOptionValues("header"));
        client.setInsecure(cmdLine.hasOption("k"));
        client.setKeypass(cmdLine.getOptionValue("keypass"));
        client.setKeystore(cmdLine.getOptionValue("keystore"));
        client.setSslProtocol(cmdLine.getOptionValue("ssl-protocol",JSSLClient.DEFAULT_PROTOCOL));
        client.setStorepass(cmdLine.getOptionValue("storepass"));
        client.setFollowRedirects(cmdLine.hasOption("L"));
        client.setUseCaches(!cmdLine.hasOption("no-cache"));
        client.setIncludeHeaders(cmdLine.hasOption("i"));
        client.setAlias(cmdLine.getOptionValue("alias"));
        client.setCrlf(cmdLine.hasOption("crlf"));
        client.setKeystoreType(cmdLine.getOptionValue("keystore-type"));
        client.setPrintBody(!cmdLine.hasOption("no-body"));
        client.setOutFileName(cmdLine.getOptionValue("out-file"));
        client.setMethod(cmdLine.getOptionValue("X"));
        client.setBinary(cmdLine.hasOption("binary"));
        client.setBufsize( Integer.parseInt( cmdLine.getOptionValue("buffer","1024")));
        client.setSkipHeadersInOutfile(cmdLine.hasOption("skip-headers"));
        client.setGzip(cmdLine.hasOption("gzip"));
        
        String urlString = cmdLine.getArgList().get(0);

        if(cmdLine.hasOption("wget")) {
        	String fileName = null;
        	
    		URI u = new URI(urlString);
    		String path = u.getPath();
    		if(path != null && !path.isEmpty()) {
    			fileName = new File(path).getName();
    		}
        		
        	if(fileName == null || fileName.isEmpty()) {
        		System.out.println("Couldn't guess download file name, use --download <file>");
        		return;
        	}
        	client.setOutFileName(fileName);
        	client.setPrintBody(false);
        	client.setIncludeHeaders(true);
        }
        
        if(cmdLine.hasOption("download")) {
        	client.setPrintBody(false);
        	client.setIncludeHeaders(true);
        	client.setOutFileName(cmdLine.getOptionValue("download"));
        	
        	if(client.isSocketUrl(urlString)) {
        		client.setBinary(true);
            	client.setSkipHeadersInOutfile(true);
        	}
        	
        }
        
		client.run(urlString);
        
        
    }
    
    
    
}
