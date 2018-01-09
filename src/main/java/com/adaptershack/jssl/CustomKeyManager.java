package com.adaptershack.jssl;

import java.net.Socket;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

import javax.net.ssl.X509KeyManager;

public class CustomKeyManager implements X509KeyManager {
	
	private X509KeyManager impl;
	public CustomKeyManager(X509KeyManager impl, String alias) {
		super();
		this.impl = impl;
		this.alias = alias;
	}

	private String alias;

	@Override
	public String chooseClientAlias(String[] arg0, Principal[] arg1, Socket arg2) {
		return alias;
	}

	@Override
	public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
		return impl.chooseServerAlias(keyType, issuers, socket);
	}

	@Override
	public X509Certificate[] getCertificateChain(String arg0) {
		return impl.getCertificateChain(arg0);
	}

	@Override
	public String[] getClientAliases(String keyType, Principal[] issuers) {
		return impl.getClientAliases(keyType, issuers);
	}

	@Override
	public PrivateKey getPrivateKey(String arg0) {
		return impl.getPrivateKey(arg0);
	}

	@Override
	public String[] getServerAliases(String keyType, Principal[] issuers) {
		return impl.getServerAliases(keyType, issuers);
	}

}
