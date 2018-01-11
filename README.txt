JSSL is a Java implementation of a HTTP, HTTPS, SSL, and TCP sockets client.

Command-line options are intended to be reminscent of keytool if they deal with
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

usage: java -jar [this-jar-file] [options] url
Options
    --alias <arg>           use alias from keystore
 -b,--binary                use i/o streams for socket mode, not
                            reader/writer - theoretically making it 8-bit
                            clean
    --buffer <arg>          buffer size for binary mode (default 1024)
    --content-type <arg>    force content type
    --crlf                  in socket mode, perform outbound CRLF
                            translation
 -d,--data <arg>            data to be posted to server
    --download <arg>        print only headers, write only body to file
                            (equivalent -b -i -n --skip-headers -o <arg>)
 -f,--file <arg>            file to be posted to server
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
 -s,--silent                silences progress messages
    --skip-headers          in socket mode, omit headers from out-file
    --storepass <arg>       password to open keystore
 -X,--request <arg>         force request method

Examples:

Retrieving a page from a server:

   java -jar jssl.jar https://www.example.com/whatever.html

Posting JSON content to a server (content-type "application/json" is inferred by inspecting the data):

   java -jar jssl.jar https://www.example.com/api/login -d '{"user":"jeff","password":"xxxxx"}'

Posting from a file (this will guess "application/xml" from the file name):

   java -jar jssl.jar https://www.example.com/api/login -f login.xml

Download a large or binary file without having its contents spill onto the console:

	java -jar jssl.jar http://www.example.com/images/foo.png --download abc.png

Turn on verbose Java SSL debugging (the real reason this tool exists):

   java -Djavax.net.debug=all -jar jssl.jar https://www.example.com

Open an interactive session on secure socket, so you can manually enter HTTP request:

   java -jar jssl.jar ssl://www.example.com

Open an insecure socket on an arbitrary port:

   java -jar jssl.jar tcp://www.example.com:4444

Compose an entire HTTP request inline (backslash escapes are parsed):

   java -jar jssl.jar ssl://www.example.com -d 'GET / HTTP/1.1\nHost: www.example.com\n\n'

Compose an arbitrary HTTP request and download the result:

   java -jar jssl.jar ssl://www.example.com -d 'GET /images/foo.png HTTP/1.1\nHost: www.example.com\n\n' --download foo.png 

Using a keystore continaing a client certificate:

   java -jar jssl.jar https://www.example.com --keystore foo.p12 --keypass changeit

Using a keystore containing multiple client certs, and selecting a particalar alias to send:

   java -jar jssl.jar https://www.example.com --keystore foo.p12 --keypass changeit --alias myalias



