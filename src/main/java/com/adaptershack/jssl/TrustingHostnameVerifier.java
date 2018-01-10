package com.adaptershack.jssl;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;
import static com.adaptershack.jssl.Log.*;

public class TrustingHostnameVerifier implements HostnameVerifier {

	@Override
	public boolean verify(String arg0, SSLSession arg1) {
		log("Trusting all host names");
		return true;
	}

}
