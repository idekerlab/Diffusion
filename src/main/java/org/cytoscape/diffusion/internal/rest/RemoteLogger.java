package org.cytoscape.diffusion.internal.rest;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Properties;

import org.cytoscape.property.CyProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cloudbees.syslog.Facility;
import com.cloudbees.syslog.MessageFormat;
import com.cloudbees.syslog.SDElement;
import com.cloudbees.syslog.SDParam;
import com.cloudbees.syslog.Severity;
import com.cloudbees.syslog.SyslogMessage;
import com.cloudbees.syslog.sender.UdpSyslogMessageSender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * This is a temporary implementation of remote logging, intended to be taken over by CyREST endpoint logging.
 * 
 * If CyREST has remote logging, this class shouldn't be here at all.
 * 
 * @author David Otasek (dotasek.dev@gmail.com)
 *
 */
public class RemoteLogger {

	public static final String INSTALLOPTIONS_SHARESTATISTICS = "installoptions.shareStatistics";

	public static final String CYTOSCAPE_REMOTELOGGING_SYSLOGSERVER = "cytoscape.remotelogging.syslogserver";

	public static final String CYTOSCAPE_REMOTELOGGING_SYSLOGSERVERPORT = "cytoscape.remotelogging.syslogserverport";

	public static final String CYTOSCAPE_REMOTELOGGING_SENDERADDRESSSERVICEHOSTNAME = "cytoscape.remotelogging.senderaddressservicehostname";
	
	private static final Logger logger = LoggerFactory.getLogger(RemoteLogger.class);

	static RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
	static String jvmName = runtimeBean.getName();

	private static boolean enabled = false;

	private UdpSyslogMessageSender messageSender;

	public static void configureFromCyProperties(CyProperty<Properties> cyProps) 
	{
		try 
		{
			String shareStatistics = cyProps.getProperties().getProperty(RemoteLogger.INSTALLOPTIONS_SHARESTATISTICS);
			if (shareStatistics != null && shareStatistics.equalsIgnoreCase("true")) {
				RemoteLogger.setEnabled(true);
			}
			
			String syslogServerport = cyProps.getProperties().getProperty(RemoteLogger.CYTOSCAPE_REMOTELOGGING_SYSLOGSERVERPORT);
			if (syslogServerport != null) {
				try {
					Integer portNumber = Integer.valueOf(syslogServerport);
					RemoteLogger.getDefaultLogger().messageSender.setSyslogServerPort(portNumber);
				} catch (Throwable e) {
					logger.error("Could not set remote logging syslog server port from properties");
					throw e;
				}
			} else {
				syslogServerport = Integer.toString(RemoteLogger.DEFAULT_SYSLOG_SERVER_PORT);
			}
			
			String syslogServer = cyProps.getProperties().getProperty(RemoteLogger.CYTOSCAPE_REMOTELOGGING_SYSLOGSERVER);
			if (syslogServer != null) {
				try {
					setSyslogServerHostname(RemoteLogger.getDefaultLogger().messageSender, syslogServer);
				} catch (Throwable e) {	
					logger.error("Could not set remote logging syslog server from properties");
					throw e;
				}
			} else {
				syslogServer = DEFAULT_SYSLOG_SERVER_HOSTNAME;
			}
			
			String senderAddressServiceHostname = cyProps.getProperties().getProperty(RemoteLogger.CYTOSCAPE_REMOTELOGGING_SENDERADDRESSSERVICEHOSTNAME);
			if (senderAddressServiceHostname != null) {
				try {
					URL url = getSenderAddressServiceURL(senderAddressServiceHostname);
					logger.info("No property set for " + RemoteLogger.CYTOSCAPE_REMOTELOGGING_SENDERADDRESSSERVICEHOSTNAME + "; setting logger sender address service hostname to: " + url);
					RemoteLogger.getDefaultLogger().setSenderAddressServiceHostname(senderAddressServiceHostname);
				} catch (Throwable e) {	
					logger.error("Could not set remote logging sender address service server from properties");
					throw e;
				}
			} else {
				senderAddressServiceHostname = DEFAULT_SENDER_ADDRESS_SERVICE_HOSTNAME;
			}
			cyProps.getProperties().setProperty(RemoteLogger.CYTOSCAPE_REMOTELOGGING_SYSLOGSERVERPORT, syslogServerport);
			cyProps.getProperties().setProperty(RemoteLogger.CYTOSCAPE_REMOTELOGGING_SYSLOGSERVER, syslogServer);
			cyProps.getProperties().setProperty(RemoteLogger.CYTOSCAPE_REMOTELOGGING_SENDERADDRESSSERVICEHOSTNAME, senderAddressServiceHostname);
		} catch (Throwable e) {
			RemoteLogger.resetDefaultLogger();
			logger.error("Could not configure syslog server from properties", e);
		}
	}

	public RemoteLogger(UdpSyslogMessageSender messageSender, String senderAddressServiceHostname) {
		try {	
			this.messageSender = messageSender;
			this.messageSender.setDefaultAppName("cytoscape");
			this.messageSender.setDefaultFacility(Facility.USER);
			this.messageSender.setDefaultSeverity(Severity.INFORMATIONAL);
			this.messageSender.setMessageFormat(MessageFormat.RFC_5424); // optional, default is RFC 3164
		} catch (Throwable e) {
			logger.error("Error instantiating UdpSyslogMessageSender", e);
			this.messageSender = null;
		}
		this.senderAddressServiceHostname = senderAddressServiceHostname;
	}

	public static final String DEFAULT_SYSLOG_SERVER_HOSTNAME = "35.197.10.101";
	
	public static final String DEFAULT_SENDER_ADDRESS_SERVICE_HOSTNAME = "35.197.44.209";
	
	public static final int DEFAULT_SYSLOG_SERVER_PORT = 3333;

	public RemoteLogger() {
		this(DEFAULT_SYSLOG_SERVER_HOSTNAME, DEFAULT_SYSLOG_SERVER_PORT, DEFAULT_SENDER_ADDRESS_SERVICE_HOSTNAME);
	}

	private static final RemoteLogger defaultLogger = new RemoteLogger();
	public static final RemoteLogger getDefaultLogger() {
		return defaultLogger;
	}

	public void setSyslogServerHostname(String syslogServerHostname) {
		try {
			setSyslogServerHostname(messageSender, syslogServerHostname);
		} catch (UnknownHostException e) {
			logger.error("Could not set syslog server host name: unknown host", e);
		}
	}

	private static void setSyslogServerHostname(UdpSyslogMessageSender messageSender, String syslogServerHostname) throws UnknownHostException {
		java.net.InetAddress.getByName(syslogServerHostname);
		messageSender.setSyslogServerHostname(syslogServerHostname);	
	}

	public String getSyslogServerHostname() {
		return messageSender.getSyslogServerHostname();
	}

	public void setSyslogServerPort(int syslogServerPort) {
		messageSender.setSyslogServerPort(syslogServerPort);
	}

	public int getSyslogServerPort() {
		return messageSender.getSyslogServerPort();
	}

	public static void resetDefaultLogger() {
		defaultLogger.messageSender.setSyslogServerHostname(DEFAULT_SYSLOG_SERVER_HOSTNAME);
		defaultLogger.messageSender.setSyslogServerPort(DEFAULT_SYSLOG_SERVER_PORT);
		defaultLogger.setSenderAddressServiceHostname(DEFAULT_SENDER_ADDRESS_SERVICE_HOSTNAME);
	}

	private String senderAddressServiceHostname;
	
	public void setSenderAddressServiceHostname(String senderAddressServiceHostname) {
		this.senderAddressServiceHostname = senderAddressServiceHostname;
	}
	
	public String getSenderAddressServiceHostname() {
		return senderAddressServiceHostname;
	}
	
	public RemoteLogger(UdpSyslogMessageSender messageSender, String syslogServerHostname, int syslogServerPort, String senderAddressServiceHostname) {
		this(messageSender, senderAddressServiceHostname);
		try {	
			this.messageSender.setSyslogServerHostname(syslogServerHostname);
			this.messageSender.setSyslogServerPort(syslogServerPort);
			this.senderAddressServiceHostname = senderAddressServiceHostname;
		} catch (Throwable e) {
			this.messageSender = null;
			logger.error("Error instantiating UdpSyslogMessageSender", e);
		}
	}
	public RemoteLogger(String syslogServerHostname, int syslogServerPort, String senderAddressServiceHostname) {
		this(new UdpSyslogMessageSender(), syslogServerHostname, syslogServerPort, senderAddressServiceHostname);
	}

	private String publicIP = null;

	private String getPublicIP() {
		try {
			if (publicIP != null) {
				return publicIP; 
			} else {
				
				final ObjectMapper mapper = new ObjectMapper();;

			    URL url = getSenderAddressServiceURL(senderAddressServiceHostname);
			    HttpURLConnection request = (HttpURLConnection) url.openConnection();
				request.setRequestMethod("GET");
			    request.connect();
			   
			    JsonNode root = mapper.readTree(new InputStreamReader((InputStream) request.getContent()));
		
			    String ip = root.get("sender_address").asText();
			    if (ip != null) {
					publicIP = ip;
					return publicIP;
				} else {
					publicIP = null;
					return "0.0.0.0";
				}
				
				
			}
		} catch (Throwable e) {
			publicIP = null;
			return "0.0.0.0";
		}
	}
	
	private static URL getSenderAddressServiceURL(String hostname) throws MalformedURLException {
		return new URL("http://" + hostname);
	}

	// Main method to manually send a Syslog message for testing.
	public static void main(String[] args) {
		try {
			setEnabled(true);
			RemoteLogger logger = RemoteLogger.defaultLogger;
			long systemTime = System.currentTimeMillis();
			logger.logResourceError("dummyHttpMethod",  "dummyPath", 664, "urn:dummyurn");
			long elapsedTime = System.currentTimeMillis() - systemTime;

			System.out.println(elapsedTime);
			System.out.println(logger.messageSender.getSendDurationInMillis());
		} catch (Throwable e) {
			System.out.println("Caught throwable.");
			e.printStackTrace();
		}
	}

	public static void setEnabled(boolean enabled) {
		RemoteLogger.enabled = enabled;
	}

	public static boolean isEnabled() {
		return enabled;
	}

	private SyslogMessage getBaseMessage() {
		SyslogMessage message = new SyslogMessage()
				.withMsg("-")
				.withTimestamp(System.currentTimeMillis())
				.withFacility(Facility.USER)
				.withSeverity(Severity.INFORMATIONAL);
		return message;
	}

	private SDParam getSDParam(String paramName, String paramValue) {
		return new SDParam(paramName, paramValue != null ? paramValue : "");
	}

	private void sendMessage(SDParam... params) throws Throwable {
		if (canSend()) {
			SDParam[] newParams = new SDParam[params.length + 2];
			newParams[0] = getSDParam("jvmName", jvmName);
			newParams[1] = getSDParam("publicIP", getPublicIP());
			System.arraycopy(params, 0, newParams, 2, params.length);
			SyslogMessage message = getBaseMessage()
					.withSDElement(
							new SDElement(
									"diffusion@cytoscape", 
									newParams));
			messageSender.sendMessage(message);
		}
	}

	public static final String HTTP_METHOD = "httpMethod";
	public static final String PATH = "path";
	public static final String RESPONSE_CODE = "responseCode";
	public static final String ERROR_TYPE = "errorType";
	public static final String SERVICE_URL = "serviceUrl";

	public void logResourceResponse(String httpMethod, String path, int responseCode) {

		try {
			sendMessage(
					getSDParam(HTTP_METHOD, httpMethod),
					getSDParam(PATH, path),
					getSDParam(RESPONSE_CODE, Integer.toString(responseCode))
					);
		} catch (Throwable e) {
			logger.error("Error sending message", e);
		}

	}

	public void logResourceError(String httpMethod, String path, int responseCode, String errorUrn) {

		try {
			sendMessage(
					getSDParam(HTTP_METHOD, httpMethod),
					getSDParam(PATH, path),
					getSDParam(RESPONSE_CODE, Integer.toString(responseCode)),
					getSDParam(ERROR_TYPE, errorUrn)
					);

		} catch (Throwable e) {
			logger.error("Error sending message", e);
		}
	}	

	public boolean messageSenderNotNull() {
		return messageSender != null;
	}

	public final boolean canSend() {
		return messageSender != null && isEnabled();
	}

	public void logServiceError(String serviceUrl, String httpMethod, int responseCode, String errorUrn) {
		try {
			sendMessage(
					getSDParam(SERVICE_URL, serviceUrl),
					getSDParam(HTTP_METHOD, httpMethod),
					getSDParam(RESPONSE_CODE, Integer.toString(responseCode)),
					getSDParam(ERROR_TYPE, errorUrn)
					);

		} catch (Throwable e) {
			logger.error("Error sending message", e);
		}
	}
}
