/*****************************************************************************
 * 
 * Copyright (C) Zenoss, Inc. 2008, all rights reserved.
 * 
 * This content is made available according to terms specified in
 * License.zenoss under the directory where your Zenoss product is installed.
 * 
 ****************************************************************************/


package org.zenoss.jmxnl;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.client.XmlRpcClient;

public class EventSender {
	private static Log log = LogFactory.getLog(EventSender.class);

	private String monitorName = null;
	private String heartbeatTimeout = null;
	private XmlRpcClient xmlRpcClient = null;
	
	public void configure(String monitorName, String heartbeatTimeout, XmlRpcClient xmlRpcClient) {
		this.monitorName = monitorName;
		this.heartbeatTimeout = heartbeatTimeout;
		this.xmlRpcClient = xmlRpcClient;
	}
	
	public void sendHeartbeat() throws XmlRpcException {
		Map<String,String> heartbeatEvent = new HashMap<String, String>();
		heartbeatEvent.put("eventClass", "/Heartbeat");
		heartbeatEvent.put("device", monitorName);
		heartbeatEvent.put("component", "zenjmxnl");
		heartbeatEvent.put("timeout", heartbeatTimeout);
		
		sendEvent(heartbeatEvent);
	}
	
	public synchronized void sendEvent(Map<String,String> evt) throws XmlRpcException {
		ArrayList<Map<String,String>> params = new ArrayList<Map<String,String>>();
		params.add(evt);
		
		xmlRpcClient.execute("sendEvent", params);
		log.info("Event: " + evt);
	}

	// EventSender is a Singleton
	private static EventSender ref;
	public static synchronized EventSender getEventSender() {
		if (ref == null) {
			ref = new EventSender();
		}
		
		return ref;
	}
}
