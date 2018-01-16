package com.adaptershack.jssl;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import com.adaptershack.duckrabbit.DynamicDelegator;

public class TwoLevelTrustManager extends DynamicDelegator<X509TrustManager> {

	X509TrustManager userTM;
	
	public TwoLevelTrustManager(X509TrustManager impl, TrustManager[] keystoreTMs) {
		super(impl);
		userTM = (X509TrustManager) keystoreTMs[0]; 
	}

	public void checkServerTrusted(final X509Certificate[] chain, final String authType) throws CertificateException {
		try {
			wrapped.checkServerTrusted(chain, authType);
			return;
		} catch (CertificateException e) {
			Log.log(e.toString());
		}
		userTM.checkServerTrusted(chain, authType);
	}

};