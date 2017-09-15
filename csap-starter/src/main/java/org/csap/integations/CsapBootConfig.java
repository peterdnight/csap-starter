package org.csap.integations;

import java.net.InetAddress;
import java.net.UnknownHostException;

import javax.annotation.PostConstruct;

import org.csap.debug.CsapDebug;
import org.csap.docs.DocumentController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.security.SecurityAutoConfiguration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.retry.annotation.EnableRetry;

@Configuration
@EnableAutoConfiguration(exclude = { SecurityAutoConfiguration.class })
@EnableCaching
@EnableRetry
@Import({ CsapEncryptableProperties.class, CsapInformation.class, CsapServiceLocator.class,
		CsapWebServerConfig.class, CsapPerformance.class, CsapDebug.class, CsapSecurityConfiguration.class,
		DocumentController.class})
public class CsapBootConfig implements ApplicationListener<ContextRefreshedEvent> {

	final static Logger logger = LoggerFactory.getLogger( CsapBootConfig.class );

	@PostConstruct
	public void showInitMessage() {
	}

	@Override
	public void onApplicationEvent(ContextRefreshedEvent contextRefreshedEvent) {

		ApplicationContext ctx = contextRefreshedEvent.getApplicationContext();
		StringBuffer s = new StringBuffer();

		if ( ctx.containsBean( "csapInformation" ) ) {
			s.append( ctx.getBean( CsapInformation.class ).toString() );
		}
		
		if ( ctx.containsBean( "CsapDocController" ) ) {
			s.append( ctx.getBean( DocumentController.class ).toString() );
		}
		
		if ( ctx.containsBean( "CsapWebServer" ) ) {
			s.append( ctx.getBean( CsapWebServerConfig.class ).toString() );
		}

		if ( ctx.containsBean( "CsapServiceLocator" ) ) {
			s.append( ctx.getBean( CsapServiceLocator.class ).toString() );
		}

		if ( ctx.containsBean( "CsapPerformance" ) ) {
			s.append( ctx.getBean( CsapPerformance.class ).toString() );
		}

		if ( ctx.containsBean( CsapDebug.BEAN_NAME ) ) {
			s.append( ctx.getBean( CsapDebug.class ).toString() );
		}
		
		if ( ctx.containsBean( "CsapEncryptableProperties" ) ) {
			s.append( ctx.getBean( "CsapEncryptableProperties" ).toString() );
		}
		
		if ( ctx.containsBean( "CsapSecurityConfiguration" ) ) {
			s.append( ctx.getBean( "CsapSecurityConfiguration".toString() ) );
			if ( ctx.containsBean( "CsapSecurityLoginController" ) ) {
				s.append( ctx.getBean( "CsapSecurityLoginController" ).toString() );
			}
			if ( ctx.containsBean( "CsapSecurityRestFilter" ) ) {
				s.append( ctx.getBean( "CsapSecurityRestFilter" ).toString() );
			}
		}

		s.append( "\n" );
		logger.warn( s.toString() );
	}

	public String getHostName() {
		return HOST_NAME;
	}

	static String HOST_NAME = "notFound";

	static {
		try {
			HOST_NAME = InetAddress.getLocalHost().getHostName();
		} catch (UnknownHostException e) {
			HOST_NAME = "HOST_LOOKUP_ERROR";
		}
	}

}
