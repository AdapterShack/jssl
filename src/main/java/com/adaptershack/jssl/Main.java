package com.adaptershack.jssl;

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

		options.addOption("i","include", false, "include response headers in output");
		options.addOption("s","silent", false, "silences progress messages");
		options.addOption("k", "insecure", false, "ignore SSL validation errors");
		options.addOption(null, "keystore", true, "custom keystore for client certs, if any");
		options.addOption(null, "keypass", true, "password to extract key from store");
		options.addOption(null, "storepass", true, "password to open keystore");			
		options.addOption("H", "header", true, "add custom HTTP header(s)");			
		options.addOption("d", "data", true, "data to be posted to server");
		options.addOption("f", "file", true, "file to be posted to server");
		options.addOption("X", "request", true, "force request method");
		options.addOption(null, "content-type", true, "force content type");
		options.addOption("p", "ssl-protocol", true, "protocol to intialize SSLContext");
		options.addOption("L", "location", false, "follow redirect");
		options.addOption(null, "no-cache", false, "disallow caching");
		options.addOption(null,"alias",true,"use alias from keystore");
		options.addOption(null,"keystore-type", true, "keystore type (default PKCS12)");
		options.addOption(null,"crlf",false,"in socket mode, perform outbound CRLF translation");
		options.addOption("n","no-body",false,"skip printing out the actual response body");
		options.addOption("o","out-file",true,"write response body to file");
		
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
        
        client.run(cmdLine.getArgList().get(0));
        
        
    }
    
    
    
}
