package org.csap.integations;

import java.util.Arrays;

import org.csap.security.SpringAuthCachingFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration("CsapSecurityRestFilter")
@ConditionalOnProperty("csap.security.restApiFilter.enabled")
@ConfigurationProperties(prefix = "csap.security.restApiFilter")
public class CsapSecurityRestFilter {

	final Logger logger = LoggerFactory.getLogger( this.getClass() );
	
	private String[] urls ;
	
	private String group ;
	
	private int cacheSeconds=60;
	
	@Autowired
	private CsapSecurityConfiguration securityConfig ;
	
	@Bean
	public FilterRegistrationBean restApiFilterRegistration() {
		
		logger.debug( "group: {} , cacheSeconds: {}", getGroup(), getCacheSeconds() );
		restFilter = new SpringAuthCachingFilter( group, cacheSeconds) ;
		FilterRegistrationBean restSecurityFilterRegistration = new FilterRegistrationBean( restFilter );
		restSecurityFilterRegistration.addUrlPatterns( urls );

		return restSecurityFilterRegistration;
	}
	
	SpringAuthCachingFilter restFilter = null ;
	
	public void setLocalCredentials(String user, String pass) {
		logger.debug( "***** Adding local credential: {}", user );
		restFilter.setLocalCredentials( user, pass );			
	}
	
	public String toString() {


		StringBuilder builder = new StringBuilder();
		builder.append("\n === csap.security.rest-api-filter: ");
		builder.append("\n\t urls inspected for user and password params: " + Arrays.asList( urls ).toString() );
		builder.append("\n\t group used to verify access: " + group );
		builder.append("\n\t seconds to cache before re-athentication: " + cacheSeconds );
		builder.append("\n ");
		
		return builder.toString() ;
	}
	
	public String[] getUrls() {
		return urls;
	}

	public void setUrls(String[] urls) {
		// logger.debug( "Injecting by reflection?" );
		this.urls = urls;
	}

	public String getGroup() {
		return group;
	}

	public void setGroup(String group) {
		this.group = group;
		if (group.equals( "$CSAP_ADMIN_GROUP" ))
			this.group = securityConfig.getAdminGroup();
	}

	public int getCacheSeconds() {
		return cacheSeconds;
	}

	public void setCacheSeconds(int cacheSeconds) {
		this.cacheSeconds = cacheSeconds;
	}

}
