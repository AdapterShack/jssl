package com.adaptershack.jssl;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.X509TrustManager;
import static com.adaptershack.jssl.Log.*;

public class TrustingTrustManager implements X509TrustManager {

	@Override
	public void checkClientTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
		log("Trusting all clients");
	}

	@Override
	public void checkServerTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
		log("Trusting all servers");
	}

	@Override
	public X509Certificate[] getAcceptedIssuers() {
		log("Accepting all issuers");
		return null;
	}

}
