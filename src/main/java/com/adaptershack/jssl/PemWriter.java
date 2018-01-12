package com.adaptershack.jssl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Base64.Encoder;

public class PemWriter extends CertWriter {

	ByteArrayOutputStream buffer = new ByteArrayOutputStream();
	boolean verbose;

	
	
	public PemWriter(boolean verbose) {
		super();
		this.verbose = verbose;
	}

	@Override
	public void store(OutputStream stream, char[] password)
			throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException {

		stream.write(buffer.toByteArray());
		
	}

	@Override
	public void load(InputStream stream, char[] password)
			throws NoSuchAlgorithmException, CertificateException, IOException {

		if(stream != null) {
			buffer.write(Utils.toByteArray(stream));
		}
	}

	@Override
	public void setCertificateEntry(String alias, Certificate cert) throws KeyStoreException {

		PrintStream p = new PrintStream(buffer);
		
		X509Certificate x509 = (X509Certificate) cert;
		
		if(verbose) {
			p.println(x509);
		} else {
			p.println("Version: " + x509.getVersion());
			p.println("Subject: " + x509.getSubjectX500Principal());
			p.println("Issuer: " + x509.getIssuerX500Principal());
			p.println("Serial Number: " + x509.getSerialNumber().toString(16));
		}
		
		Encoder encoder = Base64.getEncoder();
		p.println("-----BEGIN CERTIFICATE-----");
		try {
			String base64d = encoder.encodeToString(cert.getEncoded());
			int i=0;
			for( char c : base64d.toCharArray() ) {
				p.print(c);
				if( (++i % 60 ) == 0) {
					p.println();
				}
			}
			p.println();
		} catch (CertificateEncodingException e) {
			throw new KeyStoreException(e);
		}
		p.println("-----END CERTIFICATE-----");		
		
	}

	@Override
	public boolean supportsPassword() {
		return false;
	}
	

}
