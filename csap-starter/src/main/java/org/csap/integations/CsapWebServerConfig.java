package org.csap.integations;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.valves.RemoteIpValve;
import org.apache.coyote.AbstractProtocol;
import org.apache.coyote.ProtocolHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.embedded.EmbeddedServletContainerFactory;
import org.springframework.boot.context.embedded.EmbeddedServletContainerInitializedEvent;
import org.springframework.boot.context.embedded.tomcat.TomcatConnectorCustomizer;
import org.springframework.boot.context.embedded.tomcat.TomcatEmbeddedServletContainer;
import org.springframework.boot.context.embedded.tomcat.TomcatEmbeddedServletContainerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;

/**
 * Optional integration with Apache Web Server; you can also use client side loadbalancing as an alternative
 *
 * @author pnightin
 *
 */
/**
 *
 * =============== WebServer integration provides LoadBalancing and Sticky sessions
 *
 */
@Configuration("CsapWebServer")
@ConditionalOnProperty("csap.webServer.enabled")
@ConfigurationProperties(prefix = "server")
public class CsapWebServerConfig {

	final Logger logger = LoggerFactory.getLogger( CsapWebServerConfig.class );

	private String contextPath;

	public String getContextPath() {

		if ( contextPath.length() > 1 ) {
			return contextPath.substring( 1 );
		}
		return "NO_CONTEXT_FOUND";
	}

	public void setContextPath(String contextPath) {
		this.contextPath = contextPath;
	}

	private int httpPort;

	public int getHttpPort() {
		return httpPort;
	}

	// csap default is httpPort +1
	public void setPort(int httpPort) {
		this.httpPort = httpPort;
	}

	@Autowired
	private Environment springEnvironment;

	public static String PROPERTY_BASE="csap.web-server." ;
	
	
	private String getAjpSecret() {

		String secret = springEnvironment.getProperty( PROPERTY_BASE + "ajp-secret",  "");

		return secret;
	}
	
	private String getAjpScheme() {

		String scheme = springEnvironment.getProperty( PROPERTY_BASE + "ajp-scheme",  "http");

		return scheme;
	}
	
	
	private int getAjpPort() {

		Integer port = springEnvironment.getProperty( PROPERTY_BASE + "ajp-connection-port",  
				Integer.class, 
				getHttpPort() + 1);

		return port;
	}
	
	private int getAjpRedirectPort() {

		Integer port = springEnvironment.getProperty( PROPERTY_BASE + "ajp-redirect-port",  Integer.class, 443);

		return port;
	}
	
	private int getAjpProxyPort() {

		Integer port = springEnvironment.getProperty( PROPERTY_BASE + "ajp-proxy-port",  Integer.class, 0);

		return port;
	}
	
	private boolean getAjpSecure() {

		Boolean isSecure = springEnvironment.getProperty( PROPERTY_BASE + "ajp-secure",  Boolean.class, Boolean.FALSE);

		return isSecure;
	}

	private int getMaxThreads() {

		String threadConfig = springEnvironment.getProperty( "server.tomcat.max-threads" );
		logger.debug( "server.tomcat.max-threads: {} ", threadConfig );

		int maxThreads = 50;
		try {
			maxThreads = Integer.parseInt( threadConfig );

		} catch ( NumberFormatException e ) {
			logger.warn( "ignoring server.tomcat.max-threads as non integer was found: {}", threadConfig );
		}

		return maxThreads;
	}

	private int getMaxHttpConnections() {

		String connectionConfig = springEnvironment.getProperty( PROPERTY_BASE + "max-connections-http" );
		logger.debug( "max-connections-http: {} ", connectionConfig );

		int maxConnections = 1000;
		try {
			maxConnections = Integer.parseInt( connectionConfig );

		} catch ( NumberFormatException e ) {
			logger.warn( "ignoring max-connections-http as non integer was found: {}", connectionConfig );
		}

		return maxConnections;
	}

	private int getMaxAjpConnections() {

		String connectionConfig = springEnvironment.getProperty( PROPERTY_BASE +  "max-connections-ajp" );
		logger.debug( "max-connections-http: {} ", connectionConfig );

		int maxConnections = 50;
		try {
			maxConnections = Integer.parseInt( connectionConfig );

		} catch ( NumberFormatException e ) {
			logger.warn( "ignoring max-connections-ajp as non integer was found: {}", connectionConfig );
		}

		return maxConnections;
	}

	private int getBacklog() {

		String backlogConfig = springEnvironment.getProperty( PROPERTY_BASE + "backlog" );
		logger.debug( "backlog: {} ", backlogConfig );

		int backlog = 1; // prefer to fail fast versus keeping sockets open
		try {
			backlog = Integer.parseInt( backlogConfig );

		} catch ( NumberFormatException e ) {
			logger.warn( "ignoring backlog as non integer was found: {}", backlogConfig );
		}

		return backlog;
	}

	@EventListener
	public void handleContextRefresh(EmbeddedServletContainerInitializedEvent event) {

		StringBuilder builder = new StringBuilder( "\n\n =========== CSAP Web Initialized using csap.web-server and server.tomcat" );
		builder.append( "\n === server.port: " + getHttpPort() + "  server.context-path: " + getContextPath() + "\n");

		try {
			TomcatEmbeddedServletContainer tomcatContainer = (TomcatEmbeddedServletContainer) event.getEmbeddedServletContainer();

			logger.debug( "\n === tomcatContainer: port:" + tomcatContainer.getPort() );
			Tomcat tomcat = tomcatContainer.getTomcat();

			for ( Connector conn : tomcat.getService().findConnectors() ) {
				AbstractProtocol serviceProtol = (AbstractProtocol) conn.getProtocolHandler();
				String protocol = serviceProtol.getClass().getSimpleName();
				builder.append( "\n === " + protocol + "  port: " + serviceProtol.getPort() );
				if ( protocol.contains( "Ajp" ) ) {
					builder.append( "\t\t *Derived from http +1 " );
				}
				builder.append( "\n === " + protocol + "  server.tomcat.max-threads: " + serviceProtol.getMaxThreads() );
				builder.append( "\n === " + protocol + "  csap.web-server.max-connections-*: " + serviceProtol.getMaxConnections() );
				builder.append( "\n === " + protocol + "  default keep Alive: " + serviceProtol.getKeepAliveTimeout() );
				builder.append( "\n === " + protocol + "  csap.web-server.backlog: " + serviceProtol.getBacklog() );
				builder.append( "\n === " + protocol + "  connection scheme: " + conn.getScheme());
				builder.append( "\n === " + protocol + "  connection maxPost: " + (conn.getMaxPostSize()/1024)+ "kb\n\n");
			};
		} catch ( Exception e ) {
			builder.append( "\n === Warning CSAP tomcat initialization failed" );
			logger.error( "Failed to get tomcat information", e );
		}

		logger.info( builder.toString() );
	}

	// BUG in Boot 1.4.0 - bind into init processes to set jvmRoute correctly
//	@Bean
//	public EmbeddedServletContainerCustomizer tomcatCustomizer() {
//		return ( container ) -> {
//			if ( container instanceof TomcatEmbeddedServletContainerFactory ) {
//				((TomcatEmbeddedServletContainerFactory) container)
//						.addContextCustomizers( ( context ) -> {
//							context.addLifecycleListener( ( event ) -> {
//								if ( event.getType().equals( Lifecycle.START_EVENT ) ) {
//									logger.warn( "Boot 1.4 initialization fix. ref https://github.com/spring-projects/spring-boot/issues/6679" );
//									((org.apache.catalina.Context) event.getSource() ).getManager()
//											.getSessionIdGenerator()
//											.setJvmRoute( ((ManagerBase) context.getManager())
//													.getJvmRoute() );
//								}
//							} );
//						} );
//			}
//		};
//	}

	@Bean
	public EmbeddedServletContainerFactory servletContainer() {
		TomcatEmbeddedServletContainerFactory tomcatFactory = new TomcatEmbeddedServletContainerFactory();

		logger.debug( "Customizing tomcat factory" );

		// https://tomcat.apache.org/tomcat-8.0-doc/config/http.html
		tomcatFactory.addAdditionalTomcatConnectors( createAjpConnector() );
		tomcatFactory.addConnectorCustomizers( new TomcatConnectorCustomizer() {
			@Override
			public void customize(Connector connector) {

				ProtocolHandler handler = connector.getProtocolHandler();

				//connector.setAttribute( "acceptCount", "0" );
				//connector.setAttribute("maxThreads", "1");
				//connector.
				//h.setA
				if ( handler instanceof AbstractProtocol ) {
					AbstractProtocol protocol = (AbstractProtocol) handler;

					// set and overridden by spring boot server.max-thread
					//protocol.setMaxThreads(1);
					protocol.setMaxConnections( getMaxHttpConnections() );
					protocol.setBacklog( getBacklog() );
					//protocol.setConnectionTimeout( 1000 );

				}

			}
		} );
		//tomcatFactory.

		// tunnels through with no JSESSIONID
		//tomcat.addContextValves( createRemoteIpValves() );
		return tomcatFactory;
	}

	private RemoteIpValve createRemoteIpValves() {
		RemoteIpValve remoteIpValve = new RemoteIpValve();
		remoteIpValve.setRemoteIpHeader( "x-forwarded-for" );
		remoteIpValve.setProtocolHeader( "x-forwarded-protocol" );
		return remoteIpValve;
	}

	private Connector createAjpConnector() {


		Connector connector = new Connector( "org.apache.coyote.ajp.AjpNio2Protocol" );
		
		String jvmRouteForModJk = getContextPath() + "_" + getHttpPort() + HOST_NAME;
		// relying on tomcat system property - look for param in the future
		System.setProperty( "jvmRoute", jvmRouteForModJk );

		String secretMessage = "Not Set: Update your config" ;
		
		String ajpSecret = getAjpSecret() ;
		if ( ajpSecret.length() > 0 ) {
			secretMessage = "**** Masked ****" ;
			connector.setAttribute( "requiredSecret", ajpSecret );
		}


		connector.setPort( getAjpPort() );
		connector.setScheme( getAjpScheme() ); 
		connector.setRedirectPort( getAjpRedirectPort() );
		connector.setProxyPort( 0 );
		
		connector.setSecure( getAjpSecure() );
		

		// Using attributes, versus explicity setting in the override
		connector.setAttribute("SSLEnabled", "false" );
		connector.setAttribute("maxThreads", getMaxThreads() );
		connector.setAttribute("maxConnections", getMaxAjpConnections() );
		connector.setAttribute("backlog", getBacklog());
		// connector.setRedirectPort(8443);
		// connector.setSecure(true);
		
		webServerInfo.append( "\n === " + PROPERTY_BASE );
		webServerInfo.append( "\n\t AJP Port: " + getAjpPort() + " jvmRouteForModJk: " + jvmRouteForModJk  + " ajp-secret: " + secretMessage );
		webServerInfo.append( "\n\t ajp-secure: " + getAjpSecure() + " ajp-scheme: " + getAjpScheme() );
		webServerInfo.append( "\n\t ajp-redirect-port: " + getAjpRedirectPort()  + " ajp-proxy-port: " + getAjpProxyPort() );
		webServerInfo.append( "\n " );

		logger.debug( webServerInfo.toString() );
		
		return connector;
	}

	StringBuilder webServerInfo = new StringBuilder();

	public String toString() {
		return webServerInfo.toString();
	}

	static String HOST_NAME = "notFound";

	static {
		try {
			HOST_NAME = InetAddress.getLocalHost().getHostName();
		} catch ( UnknownHostException e ) {
			HOST_NAME = "HOST_LOOKUP_ERROR";
		}
	}

}
