/*****************************************************************************
 * 
 * Copyright (C) Zenoss, Inc. 2008, all rights reserved.
 * 
 * This content is made available according to terms specified in
 * License.zenoss under the directory where your Zenoss product is installed.
 * 
 ****************************************************************************/


package org.zenoss.jmxnl;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.management.MalformedObjectNameException;

import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;

public class Configurator {
	private String monitorName;
	private String heartbeatInterval;
	private String heartbeatTimeout;
	private String connectionRetryInterval;
	private XmlRpcClient xmlRpcClient;
	private List<NotificationListener> listeners;
	
	public Configurator(String filename) throws NullPointerException, FileNotFoundException, IOException, MalformedObjectNameException {
		FileInputStream propsFile = new FileInputStream(filename);
		Properties props = new Properties();
		props.load(propsFile);
		propsFile.close();
		
		monitorName = props.getProperty("monitor", "localhost");
		heartbeatInterval = props.getProperty("heartbeatInterval", "60");
		heartbeatTimeout = props.getProperty("heartbeatTimeout", "75");
		connectionRetryInterval = props.getProperty("connectionRetryInterval", "10");
		
		// XML-RPC is used for sending events to Zenoss
		XmlRpcClientConfigImpl xmlRpcConfig = new XmlRpcClientConfigImpl();
		xmlRpcConfig.setServerURL(new URL(props.getProperty("xmlRpcUrl", "http://localhost:8081/zport/dmd/ZenEventManager")));
		xmlRpcConfig.setBasicUserName(props.getProperty("xmlRpcUsername", "admin"));
		xmlRpcConfig.setBasicPassword(props.getProperty("xmlRpcPassword", "zenoss"));
		
		xmlRpcClient = new XmlRpcClient();
		xmlRpcClient.setConfig(xmlRpcConfig);
		
		// One listener for each URL we need to monitor for notifications
		listeners = new ArrayList<NotificationListener>();

		String serverList = props.getProperty("serverList", "DEFAULT");
		String[] serverTokens = null;
		if (serverList.contains(",")) {
			serverTokens = serverList.split(",");
		} else {
			serverTokens = new String[] { serverList };
		}
		
		for (int i = 0; i < serverTokens.length; i++) {
			String attributeFilterList = props.getProperty("server." + serverTokens[i] + ".attributeFilters");
			String[] attributeFilters = null;

			if (attributeFilterList != null) {
				if (attributeFilterList.contains(",")) {
					attributeFilters = attributeFilterList.split(",");
				} else {
					attributeFilters = new String[] { attributeFilterList };
				}
			}
			
			listeners.add(new NotificationListener(
					props.getProperty("server." + serverTokens[i] + ".url"),
					props.getProperty("server." + serverTokens[i] + ".scope"),
					props.getProperty("server." + serverTokens[i] + ".username"),
					props.getProperty("server." + serverTokens[i] + ".password"),
					props.getProperty("server." + serverTokens[i] + ".zenossDevice"),
					attributeFilters,
					connectionRetryInterval
			));
		}
	}
	
	public String getMonitorName() {
		return monitorName;
	}

	public String getHeartbeatInterval() {
		return heartbeatInterval;
	}

	public String getConnectionRetryInterval() {
		return connectionRetryInterval;
	}

	public String getHeartbeatTimeout() {
		return heartbeatTimeout;
	}

	public List<NotificationListener> getListeners() {
		return listeners;
	}
	
	public XmlRpcClient getXmlRpcClient() {
		return xmlRpcClient;
	}
}
