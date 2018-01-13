package com.adaptershack.jssl;

import javax.servlet.Servlet;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.rules.ExternalResource;

/**
 * Utility for automatically starting/stopping the embedded Jetty server
 *
 */
public class JettyRule extends ExternalResource {

	Server jetty;
	int port;

    ServletContextHandler context;
	
	public JettyRule(int port) {
		super();
		this.port = port;
	}

	@Override
	protected void before() throws Throwable {
		super.before();
		jetty = new Server(port);
		context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
		jetty.setHandler(context);
		jetty.start();
	}

	public void addServlet(String path, Servlet servlet) {
	    context.addServlet(new ServletHolder(servlet),path);
	}

	@Override
	protected void after() {
		super.after();
		try {
			jetty.stop();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	
	
	
}
