/*****************************************************************************
 * 
 * Copyright (C) Zenoss, Inc. 2008, all rights reserved.
 * 
 * This content is made available according to terms specified in
 * License.zenoss under the directory where your Zenoss product is installed.
 * 
 ****************************************************************************/


package org.zenoss.jmxnl;


import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Iterator;
import java.util.List;

import javax.management.MalformedObjectNameException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.xmlrpc.XmlRpcException;

public class Launcher {
	private static Log log = LogFactory.getLog(Launcher.class);
	
	private String heartbeatInterval;
	private List<NotificationListener> listeners;
	
	public Launcher(String heartbeatInterval, List<NotificationListener> listeners) {
		this.heartbeatInterval = heartbeatInterval;
		this.listeners = listeners;
	}
	
	public void start() {
	    log.info("Starting the Launcher.");
		Iterator<NotificationListener> iter = listeners.iterator();
		
		while (iter.hasNext()) {
			Thread t = new Thread(iter.next());
			t.start();
		}
		
		while (true) {
			try {
				EventSender.getEventSender().sendHeartbeat();
			} catch (XmlRpcException e) {
				log.error("Error sending heartbeat: " + e);			}
			
			try {
				Thread.sleep(Integer.valueOf(heartbeatInterval)*1000);
			} catch (InterruptedException e) {
				System.out.println("Exiting..");
			}
		}		
	}

	public static void main(String[] args) {
	    log.info("Reading configuration file.");
		String propsFilename = args[1];
		
		Configurator c = null;
		try {
			c = new Configurator(propsFilename);
		} catch (MalformedObjectNameException e) {
			System.err.println(e);
		} catch (MalformedURLException e) {
			System.err.println(e);
		} catch (NullPointerException e) {
			System.err.println(e);
		} catch (FileNotFoundException e) {
			System.err.println(e);
		} catch (IOException e) {
			System.err.println(e);
		}
		
		// Initialize our EventSender
		EventSender es = EventSender.getEventSender();
		es.configure(c.getMonitorName(), c.getHeartbeatTimeout(),
				c.getXmlRpcClient());
		
		// Start launching listeners
		Launcher l = new Launcher(c.getHeartbeatInterval(), c.getListeners());
		l.start();
	}
}
