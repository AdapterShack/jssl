package com.adaptershack.jssl;

import org.junit.rules.ExternalResource;

import com.adaptershack.jssl.SocketListener.Handler;

public class SocketListenerRule extends ExternalResource {
	
	SocketListener sl;

	public SocketListenerRule(int port, Handler handler) {
		this.sl = new SocketListener(port, handler);
	}

	@Override
	protected void before() throws Throwable {
		sl.start();
	}

	@Override
	protected void after() {
		sl.stop();
	}
	
	

}
