JSSL is a Java implementation of a HTTP, HTTPS, SSL, and TCP sockets client.

Command-line options are intended to be reminescent of keytool if they deal with
keystores, and roughly similar to cUrl otherwise. Obviously, not every option 
of either of those tools is being copied.

Why, one may ask, is this needed? Multiple client software for these protocols
exists, much of it extremely mature and featureful.

The main reason is I needed to exactly emulate the behavior of one Java application
calling another using client certificate authentication, and openssl (the usual
go-to) wasn't getting close enough. TLS is a complex protocol and the implementation
matters. By writing this tool in Java, I ensure that the same SSL engine used
in my actual production applications is being used by this tool.

The following URL schemes are supported:

  http     normal, insecure http
  
  https    standard https

  tcp      plain, unencrypted TCP sockets; will open an interactive session

  ssl      opens an SSL socket for interaction
  
Http(s) URL's support the conventional syntax for basic authentication:

   http://user:password@www.whatever.com
   
If authentication is actually required by the server, the user will be prompted.

The two "socket" schemes (ssl and tcp) are intended mainly for protocol-level debugging
of HTTP(s) servers. The level of interactivity is rudimentary: each line typed is 
sent to the server, each lined written by the server is echoed back.

In particular, the "ssl" protocol does not make this an *SSH* client. SSH is an entirely
separate protocol which is not at all supported by this tool. You could theoretically
use "tcp" mode as a very dumb telnet client, if there were still any telnet systems online
in <current year>.

It is a deliberate design goal to provide a "fat" executable jar (containing all
dependencies) that is as skinny as possible. Towards this goal, many common libraries
are not used. We do without HttpComponents, commons-lang, slf4j, or any of their
equivalents. The fact that this is doable, shows that the JDK provides much more
useful built-in functionality now that it once did.

Usage: java -jar [this-jar-file] [options] url

Options

 -a,--no-auth               disable HTTP Basic auth
    --alias <arg>           use alias from keystore
 -b,--binary                disables charset conversions and retrieves
                            content from the server byte for byte
    --buffer <arg>          buffer size for binary mode (default 1024)
    --content-type <arg>    force content type
    --crlf                  in socket mode, perform outbound CRLF
                            translation
 -d,--data <arg>            data to be posted to server
    --debug                 turn on SSL debugging (equivalent to
                            -Djavax.ssl.debug=all
    --download <arg>        print only headers, write only body to file
                            (equivalent to -i -n -o for http, -b -n
                            --skip-headers -o for socket)
 -f,--file <arg>            file to be posted to server
 -g,--gzip                  request gzip content-encoding
 -H,--header <arg>          add custom HTTP header(s)
 -i,--include               include response headers in output
 -k,--insecure              ignore SSL validation errors
    --keypass <arg>         password to extract key from store
    --keystore <arg>        custom keystore for client certs, if any
    --keystore-type <arg>   keystore type (default PKCS12)
 -L,--location              follow redirect
 -n,--no-body               skip printing out the actual response body
    --no-cache              disallow caching
 -o,--out-file <arg>        write response body to file
 -p,--ssl-protocol <arg>    protocol to intialize SSLContext
    --print-certs           print certificates to stdout
    --proxy-auth <arg>      proxy-authorization as user:password
 -s,--silent                silences progress messages
    --save-certs <arg>      save server's certs to file
    --save-chain <arg>      how many certs of the chain to save (default
                            all)
    --save-pass <arg>       password for saved keystore
    --save-type <arg>       type of saved keystore
    --skip-headers          in socket mode, omit headers from out-file
    --socks                 indicates proxy is a SOCKS v4 or v5 proxy
    --storepass <arg>       password to open keystore
 -t,--tunnel <arg>          tunnel the specified local port to the host,
                            accepting connections until interrupted or
                            killed
    --trustpass <arg>       trust store password
    --truststore <arg>      custom trust store for server certs, if any
 -v,--version               print version information
 -w,--wget                  auto-download file with name taken from url
 -X,--request <arg>         force request method
 -x,--proxy <arg>           use proxy host:port
 -z,--ping                  just determine if the port is open, don't send
                            any data

Examples:

Retrieving a page from a server:

   java -jar jssl.jar https://www.example.com/whatever.html

Using a keystore containing a client certificate:

   java -jar jssl.jar https://www.example.com --keystore foo.p12 --keypass changeit

Using a keystore containing multiple client certs, and selecting a particalar alias to send:

   java -jar jssl.jar https://www.example.com --keystore foo.p12 --keypass changeit --alias myalias

Posting JSON content to a server (content-type "application/json" is inferred by inspecting the data):

   java -jar jssl.jar https://www.example.com/api/login -d '{"user":"jeff","password":"xxxxx"}'

Posting from a file (this will guess "application/xml" from the file name):

   java -jar jssl.jar https://www.example.com/api/login -f login.xml

Download a large or binary file without having its contents spill onto the console:

	java -jar jssl.jar http://www.example.com/images/foo.png --download abc.png
	
Note there is no need to add the -b flag as the binary nature of the content is detected
from the content-type header.

Turn on verbose Java SSL debugging (the real reason this tool exists):

   java -jar jssl.jar https://www.example.com --debug

Open an interactive session on secure socket, so you can manually enter HTTP request:

   java -jar jssl.jar ssl://www.example.com

Open an insecure socket on an arbitrary port:

   java -jar jssl.jar tcp://www.example.com:4444

Compose an entire HTTP request inline (backslash escapes are parsed):

   java -jar jssl.jar ssl://www.example.com -d 'GET / HTTP/1.1\nHost: www.example.com\n\n'

Compose an arbitrary HTTP request and download the result:

   java -jar jssl.jar ssl://www.example.com -d 'GET /images/foo.png HTTP/1.1\nHost: www.example.com\n\n' --download foo.png 

There is no need for -b here either, because --download auto-enables it.
