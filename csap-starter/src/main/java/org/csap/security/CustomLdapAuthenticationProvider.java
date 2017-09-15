package org.csap.security;

import org.javasimon.SimonManager;
import org.javasimon.Split;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.ldap.authentication.LdapAuthenticationProvider;
import org.springframework.security.ldap.authentication.LdapAuthenticator;
import org.springframework.security.ldap.authentication.NullLdapAuthoritiesPopulator;
import org.springframework.security.ldap.userdetails.LdapAuthoritiesPopulator;

public class CustomLdapAuthenticationProvider extends LdapAuthenticationProvider {
	
	public CustomLdapAuthenticationProvider(LdapAuthenticator authenticator,
			LdapAuthoritiesPopulator authoritiesPopulator) {
		super( authenticator, authoritiesPopulator ) ;
	}

	/**
	 * Creates an instance with the supplied authenticator and a null authorities
	 * populator. In this case, the authorities must be mapped from the user context.
	 *
	 * @param authenticator the authenticator strategy.
	 */
	public CustomLdapAuthenticationProvider(LdapAuthenticator authenticator) {


		super( authenticator, new NullLdapAuthoritiesPopulator() ) ;
	}
	
	// Slim wrapper to capture LDAP timings
	public Authentication authenticate(Authentication authentication)
			throws AuthenticationException {

    	logger.debug( "New authentication" );
    	Split split = SimonManager.getStopwatch("csap.security.authenticate").start();
		Authentication result = super.authenticate( authentication ) ;
		split.stop() ;
		return result ;
	}
}
