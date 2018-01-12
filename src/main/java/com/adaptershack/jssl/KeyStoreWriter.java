package com.adaptershack.jssl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;

class KeyStoreWriter extends CertWriter {
	
	KeyStore impl;

	public KeyStoreWriter(KeyStore instance) {
		impl = instance;
	}

	@Override
	public void store(OutputStream stream, char[] password) throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException {
		impl.store(stream, password);
	}

	@Override
	public void load(InputStream stream, char[] password) throws NoSuchAlgorithmException, CertificateException, IOException {
		impl.load(stream, password);
	}

	@Override
	public void setCertificateEntry(String alias, Certificate cert) throws KeyStoreException {
		impl.setCertificateEntry(alias, cert);
	}

	@Override
	public boolean supportsPassword() {
		return true;
	}
	

}
