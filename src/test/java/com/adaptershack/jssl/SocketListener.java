package com.adaptershack.jssl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.rules.ExternalResource;

public class SocketListener extends ExternalResource {

	Thread listenerThread;
	int port;
	
	public SocketListener(int port, Handler handler) {
		super();
		this.port = port;
		this.handler = handler;
	}

	interface Handler {
		void handle(Socket s) throws Exception;
	}

	Handler handler;
	
	
	
	@Override
	public void before() {
	
		listenerThread = new Thread(
			new Runnable() {
				@Override
				public void run() {
					try (
						ServerSocket server = new ServerSocket(port)
					) {
							
						while(true) {
							try {
								Socket req = server.accept();
								
								new Thread(
									new Runnable() {
										@Override
										public void run() {
											try {
												
												handler.handle(req);
												
											} catch (Exception e) {
												e.printStackTrace();
											} finally {
												try {
													req.close();
												} catch (IOException e) {
													e.printStackTrace();
												}
											}
										}
									}
								).start();
								
							} finally {
							}
						}
						
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			});
		
		listenerThread.start();
	}
	
	@Override
	public void after() {
		if(listenerThread != null) {
			listenerThread.interrupt();
		}
	}
	

}
