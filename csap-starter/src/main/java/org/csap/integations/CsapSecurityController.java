package org.csap.integations;

import java.io.IOException;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.csap.docs.CsapDoc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

// Adds controllers for login page
@Controller ( "CsapSecurityLoginController" )
@ConditionalOnProperty ( "csap.security.enabled" )
@ConfigurationProperties ( prefix = "csap.security" )

@CsapDoc ( title = "CSAP Security Requests" , type = CsapDoc.OTHER , notes = "Provides authentication endpoints, including error redirects and pages. "
		+ "Note: /logout handling is provided via CsapSecurityConfiguration" )
public class CsapSecurityController {

	final Logger logger = LoggerFactory.getLogger( this.getClass() );

	public CsapSecurityController( ) {

		logger.debug( toString() );

	}

	public String toString () {

		StringBuilder builder = new StringBuilder();
		builder.append( "\n === csap.security login controller: " );
		try {
			builder.append( "\n\t Login Page: " + (new ClassPathResource( "/templates/csap/security/login.html" )).getURI() );
		} catch (IOException e) {
			builder.append( "\n Missing Login Page: ERROR - NOT FOUND" );
			logger.error( "Missing Login Page: ERROR - NOT FOUND", e );
		}
		builder.append( "\n\t Registered Urls: login, loginError, accessError, sessionError" );
		builder.append( "\n " );

		return builder.toString();
	}

	@CsapDoc ( notes = "Test Page for user component" )
	@RequestMapping ( "/CsapUser" )
	public String csapUser ( Model springViewModel ) {

		return "csap/components/CsapUser";

	}

	@Inject
	CsapSecurityConfiguration securityConfig;

	@CsapDoc ( notes = "Performs Login" )
	@RequestMapping ( "/login" )
	public String login (
							CsrfToken token,
							Model springViewModel, HttpServletRequest request, HttpServletResponse response ) {

		// token not needed as a parameter - but nice to show
		if ( token != null ) {
			logger.debug( "performing login using: {} value: {}", token.getParameterName(), token.getToken() );
		}

		springViewModel.addAttribute( "serviceName", getServiceName() );
		springViewModel.addAttribute( "serviceVersion", getServiceVersion() );
		springViewModel.addAttribute( "admin", securityConfig.getAdminGroup() );
		springViewModel.addAttribute( "view", securityConfig.getViewGroup() );
		springViewModel.addAttribute( "build", securityConfig.getBuildGroup() );
		springViewModel.addAttribute( "infra", securityConfig.getInfraGroup() );
		springViewModel.addAttribute( "ldap", securityConfig.getUrl() );

		if ( request.getScheme().equals( "http" ) ) {

			springViewModel.addAttribute( "nonSsl", "true" );
			String sslLoginUrl = securityConfig.getSslLoginUrl();
			if ( sslLoginUrl.length() == 0 && request.getServerName().contains( ".yourcompany.com" ) ) {
				sslLoginUrl = "https://csap-secure.yourcompany.com/admin/ssoLogin";
			}
			if ( sslLoginUrl.length() > 0 ) {
				SavedRequest savedRequest = new HttpSessionRequestCache().getRequest( request, response );
				if ( savedRequest != null ) {
					springViewModel.addAttribute( "sslLoginUrl", sslLoginUrl + "?ref=" + savedRequest.getRedirectUrl() );
				}
			}
		}

		if ( !securityConfig.isLdapEnabled() ) {
			springViewModel.addAttribute( "ldap", "In Memory Authentication" );
			springViewModel.addAttribute( "admin", "see csapSecurity.txt file" );
			springViewModel.addAttribute( "view", "see csapSecurity.txt file" );
			springViewModel.addAttribute( "build", "see csapSecurity.txt file" );
		}

		return "csap/security/login";

	}

	@RequestMapping ( "/loginError" )
	public String error ( Model springViewModel ) {

		logger.debug( "performing loginError" );

		springViewModel.addAttribute( "serviceName", getServiceName() );
		springViewModel.addAttribute( "serviceVersion", getServiceVersion() );

		return "csap/security/loginError";

	}

	@RequestMapping ( "/accessError" )
	public String accessError ( Model springViewModel ) {

		logger.debug( "performing accessError" );

		springViewModel.addAttribute( "serviceName", getServiceName() );
		springViewModel.addAttribute( "serviceVersion", getServiceVersion() );

		return "csap/security/accessError";

	}
	
	@RequestMapping ( "/sessionError" )
	public String sessionError ( Model springViewModel ) {

		logger.debug( "performing sessionInvalidation" );

		springViewModel.addAttribute( "serviceName", getServiceName() );
		springViewModel.addAttribute( "serviceVersion", getServiceVersion() );

		return "csap/security/sessionError";

	}

	@Autowired
	CsapInformation csapInformation;

	public String getServiceName () {
		return csapInformation.getName();
	}

	public String getServiceVersion () {
		return csapInformation.getVersion();
	}

}
