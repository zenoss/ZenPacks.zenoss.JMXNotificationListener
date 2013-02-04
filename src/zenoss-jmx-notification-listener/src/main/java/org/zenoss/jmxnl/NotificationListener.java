/*****************************************************************************
 * 
 * Copyright (C) Zenoss, Inc. 2008, all rights reserved.
 * 
 * This content is made available according to terms specified in
 * License.zenoss under the directory where your Zenoss product is installed.
 * 
 ****************************************************************************/


package org.zenoss.jmxnl;


import java.io.IOException;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.management.AttributeChangeNotification;
import javax.management.AttributeChangeNotificationFilter;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanServerConnection;
import javax.management.MBeanServerNotification;
import javax.management.MalformedObjectNameException;
import javax.management.Notification;
import javax.management.ObjectName;
import javax.management.monitor.MonitorNotification;
import javax.management.relation.RelationNotification;
import javax.management.remote.JMXConnectionNotification;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.xmlrpc.XmlRpcException;

public class NotificationListener implements Runnable, javax.management.NotificationListener {
	private static Log log = LogFactory.getLog(NotificationListener.class);
	
	private JMXServiceURL url;
	private ObjectName scope;
	private Map<String,String[]> environment;
	private String zenossDevice;
	private AttributeChangeNotificationFilter attributeFilter;
	private String connectionRetryInterval;
	private Map<String,String> connectionEvent;
	
	public NotificationListener(String url, String scope, String username, String password, String zenossDevice, String[] attributeFilters, String connectionRetryInterval) throws MalformedURLException, MalformedObjectNameException, NullPointerException {
		this.url = new JMXServiceURL(url);
		
		if (scope != null) {
			this.scope = new ObjectName(scope);
		} else {
			this.scope = new ObjectName("*:*");
		}
		
		environment = new HashMap<String,String[]>();
		if (username != null && password != null) {
			String[] credentials = new String[] { username, password };
			environment.put("jmx.remote.credentials", credentials);
		}
		
		this.zenossDevice = zenossDevice;
		
		if (attributeFilters == null) {
			log.debug(url + ": No attribute filters configured");
			attributeFilter = null;
		} else {
			attributeFilter = new AttributeChangeNotificationFilter();
			for (int i = 0; i < attributeFilters.length; i++) {
				log.info(url + ": Registering attribute filter for " + attributeFilters[i]);
				attributeFilter.enableAttribute(attributeFilters[i]);
			}
		}
		
		this.connectionRetryInterval = connectionRetryInterval;
		
		connectionEvent = new HashMap<String,String>();
		connectionEvent.put("device", zenossDevice);
		connectionEvent.put("component", "zenjmxnl");
		connectionEvent.put("eventKey", this.url.toString());
		connectionEvent.put("eventClass", "/Status/JMX/Conn");
	}
	
	private void sendConnectionEvent(String severity, String summary) {
		Map<String, String> evt = new HashMap<String,String>(connectionEvent);
		evt.put("severity", severity);
		evt.put("summary", summary);
		
		try {
			EventSender.getEventSender().sendEvent(evt);
		} catch (XmlRpcException e) {
			log.error("Error sending event: " + e);
		}
	}
	
	public void run() {
		try {
			connect();
		} catch (SecurityException e) {
			log.error(url + ": Connection failed. Invalid credentials");
			sendConnectionEvent("4", "Connection failed. Invalid credentials");
			return;
		} catch (IOException e) {
			log.error(url + ": Connection failed. Retrying in " + connectionRetryInterval + "seconds.");
			sendConnectionEvent("4", "Connection failed. Retrying in " + connectionRetryInterval + "seconds.");
			
			try {
				Thread.sleep(Integer.valueOf(connectionRetryInterval)*1000);
			} catch (InterruptedException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}

			run();
		}
	}
	
	@SuppressWarnings("unchecked")
	private void connect() throws IOException {
		log.info(url + ": Attempting connection (timeout in 180 seconds)");
		JMXConnector connector = JMXConnectorFactory.connect(url, environment);
		connector.addConnectionNotificationListener(this, null, "placeholder");
		MBeanServerConnection connection = connector.getMBeanServerConnection();
		
		log.info(url + ": Connected.");
		sendConnectionEvent("0", "JMX connection has been restored");

		Set <ObjectName> results = connection.queryNames(scope, null);
		java.util.Iterator<ObjectName> iter = results.iterator();
		while (iter.hasNext()) {
			ObjectName objName = (ObjectName)iter.next();
			String type = objName.getKeyProperty("type");
			if (type == null || !type.equals("alias")) {
				try {
					connection.addNotificationListener(objName, this, attributeFilter, zenossDevice);
					log.debug("Added notification listener: " + objName);
				} catch (IllegalArgumentException e) {
					log.debug("Can't listen to " + objName + " because it is not a notification broadcaster.");
				} catch (InstanceNotFoundException e) {
					log.debug("Can't listen to " + objName + " because it was not found on the server.");
				}
			}
			
			// There can be a lot of listeners to add. Give other threads a
			// chance to get work done while this happens.
			Thread.yield();
		}
	}

	public void handleNotification(Notification notification, Object obj) {
		Boolean sendEvent = true;
		Boolean reconnect = false;
		String type = notification.getType();
		
		Map<String,String> evt = new HashMap<String,String>();
		evt.put("device", (String)obj);
		evt.put("severity", "2");
		evt.put("eventClassKey", type);
		
		if (notification instanceof JMXConnectionNotification) {
			if (type.equals(JMXConnectionNotification.CLOSED)) {
				sendConnectionEvent("4", "JMX connection has been closed");
				reconnect = true;
			} else if (type.equals(JMXConnectionNotification.FAILED)) {
				sendConnectionEvent("4", "JMX connection has failed");
				reconnect = true;
			} else if (type.equals(JMXConnectionNotification.NOTIFS_LOST)) {
				sendConnectionEvent("3", "JMX connection has possibly lost notifications");
			} else if (type.equals(JMXConnectionNotification.OPENED)) {
				sendConnectionEvent("0", "JMX connection has been opened");
			}
			
			// Event has already been sent
			sendEvent = false;
		} else if (notification instanceof AttributeChangeNotification) {
			AttributeChangeNotification notif = (AttributeChangeNotification)notification;
			evt.put("component", notif.getAttributeName());
			evt.put("eventKey", notif.getSource().toString() + ":" + notif.getAttributeName());
			evt.put("summary", "Attribute changed from " + notif.getOldValue() + " to " + notif.getNewValue());
		} else if (notification instanceof MBeanServerNotification) {
			MBeanServerNotification notif = (MBeanServerNotification)notification;
			evt.put("severity", "1");
			evt.put("component", notif.getMBeanName().getDomain());
			evt.put("eventKey", notif.getMBeanName().toString());
			if (type.equals(MBeanServerNotification.REGISTRATION_NOTIFICATION)) {
				evt.put("summary", "MBean Registered");
			} else if (type.equals(MBeanServerNotification.UNREGISTRATION_NOTIFICATION)) {
				evt.put("summary", "MBean Unregistered");
			} else {
				evt.put("summary", "Unknown MBean Server Notification");
			}
			
			// These are too noisy and unlikely to be useful
			sendEvent = false;
		} else if (notification instanceof MonitorNotification) {
			MonitorNotification notif = (MonitorNotification)notification;
			evt.put("severity", "3");
			evt.put("component", notif.getObservedObject().toString() + ":" + notif.getObservedAttribute());
			if (type.equals(MonitorNotification.OBSERVED_ATTRIBUTE_ERROR)) {
				evt.put("summary", "Observed attribute not contained within the observed object");
			} else if (type.equals(MonitorNotification.OBSERVED_ATTRIBUTE_TYPE_ERROR)) {
				evt.put("summary", "Type of the observed attribute is not correct");
			} else if (type.equals(MonitorNotification.OBSERVED_OBJECT_ERROR)) {
				evt.put("summary", "The observed object is not registered in the MBean server");
			} else if (type.equals(MonitorNotification.RUNTIME_ERROR)) {
				evt.put("summary", "Non pre-defined error has occurred");				
			} else if (type.equals(MonitorNotification.STRING_TO_COMPARE_VALUE_DIFFERED)) {
				evt.put("summary", "Attribute differs from the string to compare");
			} else if (type.equals(MonitorNotification.STRING_TO_COMPARE_VALUE_MATCHED)) {
				evt.put("summary", "Attribute matched the string to compare");
			} else if (type.equals(MonitorNotification.THRESHOLD_ERROR)) {
				evt.put("summary", "Type of threshold is not correct");
			} else if (type.equals(MonitorNotification.THRESHOLD_HIGH_VALUE_EXCEEDED)) {
				evt.put("summary", "Attribute has exceeded the threshold high value");
			} else if (type.equals(MonitorNotification.THRESHOLD_LOW_VALUE_EXCEEDED)) {
				evt.put("summary", "Attribute has exceeded the threshold low value");
			} else if (type.equals(MonitorNotification.THRESHOLD_VALUE_EXCEEDED)) {
				evt.put("summary", "Attribute has reached the threshold value");
			} else {
				evt.put("summary", "Unknown Monitor Notification");
			}
		} else if (notification instanceof RelationNotification) {
			RelationNotification notif = (RelationNotification)notification;
			evt.put("component", notif.getRelationId());
			if (type.equals(RelationNotification.RELATION_BASIC_CREATION)) {
				evt.put("summary", "Internal relation created");
			} else if (type.equals(RelationNotification.RELATION_BASIC_REMOVAL)) {
				evt.put("summary", "Internal relation removed");
			} else if (type.equals(RelationNotification.RELATION_BASIC_UPDATE)) {
				evt.put("summary", "Internal relation updated");
			} else if (type.equals(RelationNotification.RELATION_MBEAN_CREATION)) {
				evt.put("summary", "MBean relation created");
			} else if (type.equals(RelationNotification.RELATION_MBEAN_REMOVAL)) {
				evt.put("summary", "MBean relation removed");
			} else if (type.equals(RelationNotification.RELATION_MBEAN_UPDATE)) {
				evt.put("summary", "MBean relation updated");
			}
		} else {
			if (notification.getMessage().equals("")) {
				evt.put("summary", "Unknown JMX Notification Type");
			} else {
				evt.put("summary", notification.getMessage());
			}
		}
		
		if (sendEvent) {
			try {
				EventSender.getEventSender().sendEvent(evt);
			} catch (XmlRpcException e) {
				log.error("Error sending event: " + e);
			}
		}
		
		if (reconnect) {
			run();
		}
    }
}
