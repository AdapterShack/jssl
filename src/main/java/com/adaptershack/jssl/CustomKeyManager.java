package com.adaptershack.jssl;

import java.net.Socket;
import java.security.Principal;

import javax.net.ssl.KeyManager;
import javax.net.ssl.X509KeyManager;

import com.adaptershack.duckrabbit.DynamicDelegator;

import static com.adaptershack.jssl.Log.*;

public class CustomKeyManager extends DynamicDelegator<X509KeyManager> {
	
	private String alias;

	public CustomKeyManager(KeyManager impl, String alias) {
		super((X509KeyManager)impl);
		this.alias = alias;
	}

	public String chooseClientAlias(String[] arg0, Principal[] arg1, Socket arg2) {
		log("chooseClientAlias: returning: " + alias);
		return alias;
	}

}
