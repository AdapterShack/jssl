package com.adaptershack.jssl;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class SocketListener {

	Thread listenerThread;
	int port;
	
	public SocketListener(int port, Handler handler) {
		super();
		this.port = port;
		this.handler = handler;
	}

	@FunctionalInterface
	public interface Handler {
		void handle(Socket s) throws Exception;
	}

	Handler handler;
	
	
	
	public void start() {
	
		listenerThread = new Thread(
				() -> {
					try (
						ServerSocket server = new ServerSocket(port)
					) {
							
						while(true) {
							try {
								Socket req = server.accept();
								
								new Thread(
									() -> {
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
								).start();
								
							} finally {
							}
						}
						
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			);
		
		listenerThread.start();
	}
	
	public void stop() {
		if(listenerThread != null) {
			listenerThread.interrupt();
		}
	}
	

}
