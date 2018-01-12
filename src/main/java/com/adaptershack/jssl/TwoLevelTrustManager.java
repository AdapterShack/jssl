package com.adaptershack.jssl;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

class TwoLevelTrustManager implements X509TrustManager {

	List<X509TrustManager> tms = new ArrayList<>();
	
	public TwoLevelTrustManager(X509TrustManager impl, TrustManager[] keystoreTM) {
		tms.add(impl);
		for( TrustManager tm : keystoreTM ) {
			if(tm instanceof X509TrustManager) {
				tms.add((X509TrustManager) tm);
			}
		}
	}

	private interface Checker {
		void check(X509TrustManager tm) throws CertificateException;
	}
	
	private void tryAll(Checker c) throws CertificateException {
		for(X509TrustManager x : tms) {
			try {
				c.check(x);
				return;
			} catch (CertificateException e) {
				Log.log(e.toString());
			}
		}
		throw new CertificateException("No trust manager was able to checkClientTrusted");
	}

	
	@Override
	public void checkClientTrusted(final X509Certificate[] chain, final String authType) throws CertificateException {
		tryAll(new Checker() {
			@Override
			public void check(X509TrustManager tm) throws CertificateException {

				tm.checkClientTrusted(chain, authType);
				
			}
		});
	}

	@Override
	public void checkServerTrusted(final X509Certificate[] chain, final String authType) throws CertificateException {
		tryAll(new Checker() {
			@Override
			public void check(X509TrustManager tm) throws CertificateException {

				tm.checkServerTrusted(chain, authType);
				
			}
		});
	}

	@Override
	public X509Certificate[] getAcceptedIssuers() {
		
		Set<X509Certificate> certs = new HashSet<>();
		
		for(X509TrustManager x : tms) {
			certs.addAll(Arrays.asList(x.getAcceptedIssuers()));
		}
		
		return certs.toArray(new X509Certificate[ certs.size() ]  );
	}

	public String toString() {
		return getClass().getSimpleName() + "->" + tms;
	}
	
	
};