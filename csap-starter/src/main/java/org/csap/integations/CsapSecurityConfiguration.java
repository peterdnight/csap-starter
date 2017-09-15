package org.csap.integations;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.naming.Name;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.apache.commons.io.FileUtils;
import org.csap.security.Custom;
import org.csap.security.CustomContextMapper;
import org.csap.security.CustomRememberMeService;
import org.csap.security.SpringAuthCachingFilter;
import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.jasypt.exceptions.EncryptionOperationNotPossibleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.BaseLdapPathContextSource;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.ldap.support.LdapNameBuilder;
import org.springframework.security.authentication.RememberMeAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.ldap.DefaultSpringSecurityContextSource;
import org.springframework.security.ldap.authentication.BindAuthenticator;
import org.springframework.security.ldap.authentication.LdapAuthenticationProvider;
import org.springframework.security.ldap.search.FilterBasedLdapUserSearch;
import org.springframework.security.ldap.search.LdapUserSearch;
import org.springframework.security.ldap.userdetails.DefaultLdapAuthoritiesPopulator;
import org.springframework.security.ldap.userdetails.LdapAuthoritiesPopulator;
import org.springframework.security.ldap.userdetails.LdapUserDetailsService;
import org.springframework.security.web.authentication.RememberMeServices;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.thymeleaf.extras.springsecurity4.dialect.SpringSecurityDialect;

/**
 *
 * Spring Security Integration
 *
 * @author pnightin
 *
 */
@Configuration ( "CsapSecurityConfiguration" )
@ConditionalOnProperty ( "csap.security.enabled" )
@ConfigurationProperties ( prefix = "csap.security" )
// @EnableWebSecurity // boot autoconfigures, but junits require
@Import ( { CsapSecurityController.class, CsapEncryptableProperties.class, CsapSecurityRestFilter.class } )
public class CsapSecurityConfiguration extends WebSecurityConfigurerAdapter {

	private static List<String> DEFAULT_IGNORED = Arrays.asList( "/css/**", "/js/**",
		"/images/**", "/webjars/**", "/**/favicon.ico" );

	// ref SpringBootWebSecurityConfiguration
	/**
	 * @return the remoteUrl
	 */
	public String getSslLoginUrl () {
		return sslLoginUrl;
	}

	final Logger logger = LoggerFactory.getLogger( this.getClass() );

	private boolean ldapEnabled = true;
	private boolean csrfEnabled = false;
	private int maxConcurrentUserSessions = 0;

	public CsapSecurityConfiguration( ) {
		logger.debug( "Starting up" );
	}

	public static String hasAnyRole ( CsapRolesEnum role ) {
		return "hasAnyRole(" + role.value + ") or " + CSAP_SUPER_USER;
	}

	public final static String CSAP_CUSTOM_BEAN_NAME = "csapCustomSecurity";

	private final static String CSAP_SUPER_USER = "(isAuthenticated() and @CsapSecurityConfiguration.superUsers.contains(principal.username))";

	/**
	 *
	 * Enable custom http access rules to be implemented. All instance of
	 * CustomHttpSecurity will be invoked
	 *
	 */
	public interface CustomHttpSecurity {

		public void configure ( HttpSecurity httpSecurity )
				throws Exception;

	}

	// @Bean
	// public IgnoredRequestCustomizer getIgnored() {
	// logger.info( "adding public resources: {} ", DEFAULT_IGNORED );
	// IgnoredRequestCustomizer filters = null;
	//
	// filters = ( ignoredRequestConfigurer ) -> {
	// //String[] paths = {"/css", "/images", "/js"};
	// List<RequestMatcher> matchers = new ArrayList<RequestMatcher>();
	// if (!DEFAULT_IGNORED.isEmpty()) {
	// for (String pattern : DEFAULT_IGNORED) {
	// matchers.add(new AntPathRequestMatcher(pattern, null));
	// }
	// }
	// if (!matchers.isEmpty()) {
	// ignoredRequestConfigurer.requestMatchers(new OrRequestMatcher(matchers));
	// }
	// } ;
	// return filters;
	// }

	@Autowired
	ApplicationContext springContext;

	// in case ignore is needed to completely disable security on
	// @Override
	// public void configure(WebSecurity web) throws Exception {
	//
	// web.ignoring()
	// .antMatchers( "/css/**" )
	// .antMatchers( "/js/**" )
	// .antMatchers( "/images/**" );
	//
	// }
	/**
	 *
	 * Note the use of spring security fluent apis to concisely define security
	 * policy
	 *
	 * http://spring.io/blog/2013/08/23/spring-security-3-2-0-rc1-highlights-
	 * security-headers/
	 *
	 */
	@Override
	protected void configure ( HttpSecurity httpSecurity )
			throws Exception {

		if ( isLdapEnabled() ) {
			httpSecurity
				.rememberMe()
				.rememberMeServices( getCsapSingleSignOn() );
		}

		if ( infraGroup.equals( DEFAULT_TO_BUILD ) ) {
			logger.warn( "security.role.infra not found in csapSecurity.properties. Default to be the same as buildGroup" );
			infraGroup = buildGroup;
		}

		Map<String, CustomHttpSecurity> customRules = springContext.getBeansOfType( CustomHttpSecurity.class );
		if ( !customRules.isEmpty() ) {
			logger.debug( "Custom security applied" );
			customRules.values().forEach( item -> {
				try {
					item.configure( httpSecurity );
				} catch (Exception e) {
					logger.error( "Failed to configure: ", item.getClass().getName(), e );
				}
			} );

		}
		
		if ( getMaxConcurrentUserSessions() > 0  ) {
			httpSecurity.sessionManagement()
				.maximumSessions( getMaxConcurrentUserSessions() )
				.expiredUrl(  "/sessionError");
		}

		if ( !isCsrfEnabled() ) {
			// cross site security disabled by default due to integration
			// complexity
			// enable using csap.security
			httpSecurity.csrf().disable();
		}

		// @formatter:off
		httpSecurity
				.headers()
					.cacheControl() // no caching on anything; mvcConfig customizes for images
					.and()
				.frameOptions()
					.sameOrigin() // by default, spring disables html frames. sample has javadoc with frames, so we enable 
					.and()
				// All authenticate deligated to microservices
				//			.authorizeRequests()

				// Disable security on public assets. Error is used by boot to resolve
				//				.antMatchers("/error", "/someUrlTree/**")
				//						.permitAll() 
				//						
				//				// Simple example for validating group access - this is on index.jsp for testing
				//				.antMatchers( "/more/**", "/andMoreUrls/**" )
				//						.hasRole( "NonExistGroupToTriggerAuthFailure" ) 

				// Advanced example using spring expressions. They allow for very powerful rules to be loaded from property files, bean properties, etc.
				//				.antMatchers( "/more/**", "/andMoreUrls/**" )
				//					.access("hasAnyRole(@securityConfiguration.adminGroups) or (isAuthenticated() and @securityConfiguration.superUsers.contains(principal.username)) ")

				//				.anyRequest() // Ensure everything else is at least authenticated
				//					.authenticated()

				// What to show if accessing a portion of app without sufficient permisions
				//			.and()
				.exceptionHandling()
					.accessDeniedPage( "/accessError" )
					.and()
				//
				.formLogin()
					.loginPage( "/login" ) // Note the form action is the same
					.passwordParameter( "password" )
					.usernameParameter( "username" )
					.defaultSuccessUrl( "/", false )
					.failureUrl( "/loginError" )
					.permitAll()
					.and()
				//
				.logout()
					.addLogoutHandler( ( request, response, authentication ) -> {
						logger.debug( "logging user out" );
						Cookie cookie = new Cookie( cookieName, null );
						cookie.setDomain( CustomRememberMeService.getSingleSignOnDomain( request ) );
						cookie.setPath( "/" );
						cookie.setMaxAge( 0 );
						response.addCookie( cookie );
						try {
							response.sendRedirect( "services" );
						} catch ( Exception e ) {
							logger.error( "Failed to redirect", e );
						}
					} );

		// @formatter:on
	}

	public static final String DEFAULT_TO_BUILD = "defaultToBuild";

	// A straight up custom authentication using builder APIs, but some
	// companies does not use
	// standard LDAP group membership
	// auth.ldapAuthentication()
	// .userDnPatterns(dn, dnGen)
	// .userDetailsContextMapper(customContextMapper)
	// .groupSearchBase(searchGroups)
	// .contextSource(getContextSource())
	// .groupSearchFilter("groupmembership")
	// .rolePrefix("ROLE_");
	/**
	 *
	 * This is a lifecycle bean - that occurs very early in Spring context.
	 *
	 *
	 */
	@Autowired
	public void configureGlobal ( AuthenticationManagerBuilder auth )
			throws Exception {

		// Bean check is used, because yaml property set occurs AFTER this
		// method
		try {
			logger.debug( "============ Authentication Scheme: LDAP :" + springContext.getBean( LdapAuthoritiesPopulator.class ) );
		} catch (Exception e) {
			logger.debug( "============ Authentication Scheme: Memory" );
			SpringAuthCachingFilter.configureInMemoryUsers( auth );
			return;
		}

		//
		// Custom support: authentication and authorization via LDAP
		//
		BindAuthenticator ldapBindAuthenticator = new BindAuthenticator( getContextSource() );
		ldapBindAuthenticator.setUserDnPatterns( new String[] { dn, dnGen } );
		String[] customAttributesToGetOnLdapBind = { "userid", "mail", "usersHost", "cn", "sn",
				"givenName", "street", "memberOf", "groupmembership", "mcoreportingchain",
				"manager", "telephoneNumber", "pwdLastSet" };
		ldapBindAuthenticator.setUserAttributes( customAttributesToGetOnLdapBind );
		LdapAuthenticationProvider ldapAuthProvider = new LdapAuthenticationProvider(
			ldapBindAuthenticator, getActiveDirGroupsProvider() );

		ldapAuthProvider.setAuthoritiesMapper( getCustomAuthoritiesMapper() );

		ldapAuthProvider.setUserDetailsContextMapper( getCustomContextMapper() );
		auth.authenticationProvider( ldapAuthProvider );

		//
		// CS-AP SSO Support
		//
		// logger.info("encryptKey" + encryptKey);
		RememberMeAuthenticationProvider csapSSoAuth = new RememberMeAuthenticationProvider( getCookieEncrypt() );
		auth.authenticationProvider( csapSSoAuth );

	}

	static public RequestMatcher buildRequestMatcher ( String... csrfUrlPatterns ) {

		// Enabled CSFR protection on the following urls:
		// new AntPathRequestMatcher( "/**/verify" ),
		// new AntPathRequestMatcher( "/**/login*" ),

		ArrayList<AntPathRequestMatcher> antPathList = new ArrayList<>();
		for ( String pattern : csrfUrlPatterns ) {
			antPathList.add( new AntPathRequestMatcher( pattern ) );
		}
		RequestMatcher csrfRequestMatcher = new RequestMatcher() {

			ArrayList<AntPathRequestMatcher> requestMatchers = antPathList;

			@Override
			public boolean matches ( HttpServletRequest request ) {
				// If the request match one url the CSFR protection will be
				// enabled

				if ( request.getMethod().equals( "GET" ) ) {
					return false;
				}
				for ( AntPathRequestMatcher rm : requestMatchers ) {
					if ( rm.matches( request ) ) {
						return true;
					}
				}
				return false;
			} // method matches

		};

		return csrfRequestMatcher;
	}

	// http://blog.codeleak.pl/2016/05/thymeleaf-3-get-started-quickly-with.html
	@Bean
	public SpringSecurityDialect springSecurityDialect () {
		return new SpringSecurityDialect();
	}

	// @Bean @Override
	// public AuthenticationManager authenticationManagerBean() throws Exception
	// {
	// return super.authenticationManagerBean();
	// }
	@Bean
	public Custom getCustomAuthoritiesMapper () {
		Custom mapper = new Custom();
		return mapper;
	}

	/**
	 * Custom specific attributes from active directory
	 *
	 * @return
	 */
	@Bean
	public CustomContextMapper getCustomContextMapper () {
		CustomContextMapper customContextMapper = new CustomContextMapper();
		return customContextMapper;
	}

	@Autowired ( required = false )
	StandardPBEStringEncryptor encryptor = null;

	// support migration to more secure config
	private String decodeProperty ( String eolItem, String eolDescription, String itemWithEncyrptSupport, String description ) {

		logger.debug( "eolItem: {} itemWithEncyrptSupport: {} ", eolItem, itemWithEncyrptSupport );
		if ( !eolItem.equals( DEFAULT ) ) {

			logger.warn( "{} is deprecated. Switch to {} in csapSecurity.properties",
				eolDescription, description );
		}

		String password = eolItem;

		if ( !itemWithEncyrptSupport.equals( DEFAULT ) ) {

			password = itemWithEncyrptSupport;
		}

		try {
			if ( encryptor != null ) {
				password = encryptor.decrypt( itemWithEncyrptSupport );
			} else {
				logger.error( "Did not get encrypter = password wil not be decrypted" );
			}
		} catch (EncryptionOperationNotPossibleException e) {
			logger.warn( "{} is not encrypted. Use CSAP encrypt to generate", description );
		}
		// logger.debug("Raw: {}, post: {}", directoryPass, password) ;
		return password;
	}

	@Bean
	@ConditionalOnProperty ( "csap.security.ldapEnabled" )
	public LdapTemplate ldapTemplate () {
		LdapTemplate template = new LdapTemplate();
		LdapContextSource contextSource = new LdapContextSource();

		contextSource.setUserDn( getLdapSearchUser().toString() );

		contextSource.setPassword( decodeProperty( directoryPass, "security.dir.pass", directoryPassword, "security.dir.password" ) );

		contextSource.setUrl( getUrl() );

		try {
			contextSource.afterPropertiesSet();
			template.setContextSource( contextSource );
			template.afterPropertiesSet();
		} catch (Exception e) {
			logger.error( "Failed to init LDAP", e );
		}
		StringBuilder builder = new StringBuilder();

		builder.append( "\n\n ==========================" );
		builder.append( "\n LdapTemplate for accessing Identity" );
		builder.append( "\n Directory url: " + getUrl() );
		builder.append( "\n Access User: " + directoryUser );
		builder.append( "\n Access Tree: " + genericUseridTree );
		builder.append( "\n Directory user search tree: " + getSearchUser() );
		builder.append( "\n==========================\n\n" );

		logger.debug( builder.toString() );

		return template;

	}

	/**
	 * Custom does not use groupmembership attribute in activedirectory, but
	 * stores groups in an attribute
	 *
	 * To hook into Spring, a custom populator is necessary. For more insight,
	 * download Softerra ldap browser and explore active directory.
	 *
	 * @return
	 */
	@Bean
	@ConditionalOnProperty ( "csap.security.ldapEnabled" )
	public LdapAuthoritiesPopulator getActiveDirGroupsProvider () {

		DefaultLdapAuthoritiesPopulator ldapAuthPopulator = new DefaultLdapAuthoritiesPopulator(
			getContextSource(), searchGroups );

		return ldapAuthPopulator;
	}

	/**
	 * @return
	 */
	@Bean
	@ConditionalOnProperty ( "csap.security.ldapEnabled" )
	public BaseLdapPathContextSource getContextSource () {
		logger.debug( "Constructing using url: {} , user: {} ", url, directoryUser );
		// + " directoryPass" + directoryPass
		DefaultSpringSecurityContextSource ldapContext = new DefaultSpringSecurityContextSource( url );
		ldapContext.setUserDn( directoryUser );
		ldapContext.setPassword( decodeProperty( directoryPass, "security.dir.pass", directoryPassword, "security.dir.password" ) );

		return ldapContext;
	}

	@Bean
	@ConditionalOnProperty ( "csap.security.ldapEnabled" )
	public RememberMeServices getCsapSingleSignOn () {

		LdapUserDetailsService userDetailsService = new LdapUserDetailsService(
			getMultipleLdapTreeSearch(),
			getActiveDirGroupsProvider() );

		userDetailsService.setUserDetailsMapper( getCustomContextMapper() );

		// logger.info("encryptKey" + encryptKey);
		CustomRememberMeService rememberMe = new CustomRememberMeService( getCookieEncrypt(), userDetailsService );
		rememberMe.setCookieName( cookieName );
		rememberMe.setTokenValiditySeconds( cookieExpirationInSeconds );
		rememberMe.setAlwaysRemember( true );

		rememberMe.setAuthoritiesMapper( getCustomAuthoritiesMapper() );

		return rememberMe;
	}

	@Bean
	@ConditionalOnProperty ( "csap.security.ldapEnabled" )
	public MultipleLdapTreeSearch getMultipleLdapTreeSearch () {
		// FilterBasedLdapUserSearch filterUserSearch = new
		// FilterBasedLdapUserSearch(
		// searchUser, "(uid={0})",
		// getContextSource() );
		//
		// LdapUserDetailsService userDetailsService = new
		// LdapUserDetailsService(
		// filterUserSearch,
		// getActiveDirGroupsProvider() );

		FilterBasedLdapUserSearch primaryUsers = new FilterBasedLdapUserSearch(
			searchUser, "(uid={0})",
			getContextSource() );

		FilterBasedLdapUserSearch genericUsers = new FilterBasedLdapUserSearch(
			genericUseridTree, "(uid={0})",
			getContextSource() );

		MultipleLdapTreeSearch multipleTreeSearch = new MultipleLdapTreeSearch( primaryUsers );
		multipleTreeSearch.addSearch( genericUsers );

		return multipleTreeSearch;

	}

	/**
	 * 
	 * Helper class to support multiple user contexts in LDAP tree. eg. real
	 * versus generic ids.
	 * 
	 * @author pnightin
	 *
	 */
	public class MultipleLdapTreeSearch implements LdapUserSearch {
		public static final String SAM_FILTER = "(&(sAMAccountName={0})(objectclass=user))";

		List<LdapUserSearch> treesToSearch;

		public MultipleLdapTreeSearch( LdapUserSearch primarySearch ) {
			treesToSearch = new ArrayList<>();
			treesToSearch.add( primarySearch );
		}

		public void addSearch ( LdapUserSearch alternateSearch ) {
			logger.info( "Adding: {}", alternateSearch );
			treesToSearch.add( alternateSearch );
		}

		public DirContextOperations searchForUser ( String username ) {
			try {
				return treesToSearch.get( 0 ).searchForUser( username );
			} catch (UsernameNotFoundException e) {
				if ( treesToSearch.size() > 1 ) {
					return treesToSearch.get( 1 ).searchForUser( username );
				} else {
					throw e;
				}
			}
		}
	}

	private String token = null;

	private String getCookieEncrypt () {

		if ( token == null ) {
			// only do once
			token = decodeProperty( cookieEncrypt, "security.cookie.encrypt", cookieToken, "security.cookie.token" );

		}
		return token;
	}

	public static final String DEFAULT = "default";

	@Value ( "${security.cookie.name}" )
	private String cookieName = "CSAP_SSO";

	@Value ( "${security.cookie.encrypt:" + DEFAULT + "}" )
	private String cookieEncrypt;

	@Value ( "${security.cookie.token:" + DEFAULT + "}" )
	private String cookieToken;

	@Value ( "${security.cookie.expire}" )
	private int cookieExpirationInSeconds = 30;

	@Value ( "${security.dir.url:notUsed}" )
	private String url;

	@Value ( "${security.dir.dn:notUsed}" )
	private String dn;

	@Value ( "${security.dir.dn.gen:notUsed}" )
	private String dnGen;

	@Autowired ( required = false )
	@Value ( "${security.dir.tree:noUsed}" )
	private String genericUseridTree;

	@Value ( "${security.dir.search.groups:notUsed}" )
	private String searchGroups;

	@Value ( "${security.dir.search.user:notUsed}" )
	private String searchUser;

	@Value ( "${security.dir.user:notUsed}" )
	private String directoryUser;

	@Value ( "${security.dir.pass:" + DEFAULT + "}" )
	private String directoryPass;

	@Value ( "${security.dir.password:" + DEFAULT + "}" )
	private String directoryPassword;

	// This will be set to adminGroup if not set
	@Value ( "${security.role.infra:" + DEFAULT_TO_BUILD + "}" )
	public String infraGroup = DEFAULT_TO_BUILD;

	@Value ( "${security.role.admin:ROLE_AUTHENTICATED}" )
	public String adminGroup;

	@Value ( "${security.role.superusers:noSuperUsers}" )
	public String superUsers;

	@Value ( "${security.role.build:ROLE_AUTHENTICATED}" )
	public String buildGroup;

	@Value ( "${security.role.view:ROLE_AUTHENTICATED}" )
	public String viewGroup;

	@Value ( "${security.ssl.login:}" )
	private String sslLoginUrl = "";

	public String toString () {
		logger.debug( "Constructing info bean for console logs " );

		StringBuilder builder = new StringBuilder();
		builder.append( "\n === csap.security: " );
		builder.append( "\n\t CSRF Enabled (cross site request forgery): " + isCsrfEnabled() );
		builder.append( "\n\t max concurrent user sessions(0=unlimited): " + getMaxConcurrentUserSessions() );
		builder.append( "\n\t Cookie: " + cookieName );
		builder.append( "\n\t Cookie encrypt: " + "MASKED" );
		builder.append( "\n\t Cookie expire: " + cookieExpirationInSeconds );
		builder.append( "\n\t CSAP security.role.infra: " + infraGroup );
		builder.append( "\n\t CSAP security.role.admin: " + adminGroup );
		builder.append( "\n\t CSAP security.role.build: " + buildGroup );
		builder.append( "\n\t CSAP security.role.view: " + viewGroup );
		builder.append( "\n\t CSAP security.role.superUsers: " + superUsers );

		if ( isLdapEnabled() ) {
			builder.append( "\n\t Directory url: " + url );
			builder.append( "\n\t Directory dn: " + dn );
			builder.append( "\n\t Directory dnGen: " + dnGen );
			builder.append( "\n\t Directory searchUser: " + searchGroups );
			builder.append( "\n\t Directory searchGroups: " + searchGroups );
			builder.append( "\n\t Directory user: " + directoryUser );
		} else {
			builder.append( "\n\t In Memory Auth loaded from: " + SpringAuthCachingFilter.getAuthFile().getAbsolutePath() );
			try {
				builder.append( "\n\t Credentials: " + FileUtils.readFileToString( SpringAuthCachingFilter.getAuthFile() ) );
			} catch (IOException e) {
				logger.error( "Failed to load roles", e );
			}
		}

		Map<String, CustomHttpSecurity> customRules = springContext.getBeansOfType( CustomHttpSecurity.class );
		if ( !customRules.isEmpty() ) {
			logger.debug( "Custom security applied" );
			customRules.values().forEach( customRule -> {
				builder.append( "\n\t ****Custom Acls: " + customRule.getClass().getName() );
			} );
		} else {
			builder.append( "\n\t ****Custom Acls: Not Configured" );
		}

		builder.append( "\n " );

		logger.debug( builder.toString() );
		return builder.toString();
	}

	public String getUrl () {
		return url;
	}

	public void setUrl ( String url ) {
		this.url = url;
	}

	public String getInfraGroup () {
		return infraGroup;
	}

	public String getAdminGroup () {
		return adminGroup;
	}

	public String getBuildGroup () {
		return buildGroup;
	}

	public String getViewGroup () {
		return viewGroup;
	}

	public boolean isViewGroupAuthenticateOnly () {
		logger.info( "getViewGroups: ", getViewGroup() );
		if ( getViewGroup().equals( "ROLE_AUTHENTICATED" ) ) {
			return true;
		}
		return false;
	}

	public boolean isLdapEnabled () {
		return ldapEnabled;
	}

	public void setLdapEnabled ( boolean ldapEnabled ) {
		logger.debug( "ldapEnabled: {}", ldapEnabled );
		this.ldapEnabled = ldapEnabled;
	}

	public List<String> getAllUserRoles () {
		List<String> roles = new ArrayList<String>();

		Collection<? extends GrantedAuthority> authorities = SecurityContextHolder.getContext()
			.getAuthentication().getAuthorities();

		for ( GrantedAuthority grantedAuthority : authorities ) {
			roles.add( grantedAuthority.getAuthority() );
		}
		return roles;
	}

	public void addRoleIfUserHasAccess ( HttpSession session, String customRole ) {

		session.removeAttribute( customRole );
		String springRoleMapp = "ROLE_" + customRole.toUpperCase();

		Collection<? extends GrantedAuthority> authorities = SecurityContextHolder.getContext()
			.getAuthentication().getAuthorities();
		for ( GrantedAuthority grantedAuthority : authorities ) {
			if ( grantedAuthority.getAuthority().equals( springRoleMapp ) ) {
				session.setAttribute( customRole, customRole );
			}
		}

	}

	public boolean hasCustomRole ( HttpSession session, String customRole ) {
		return session.getAttribute( customRole ) != null;
	}

	public static final String VIEW_ROLE = "ViewRole";
	public static final String BUILD_ROLE = "BuildRole";
	public static final String ADMIN_ROLE = "AdminRole";
	public static final String INFRA_ROLE = "InfraRole";

	/**
	 *
	 * CSAP only roles: view, scm, admin. Use all user roles for generic role
	 * support
	 *
	 * @return
	 */
	public List<String> getUserRolesFromContext () {
		List<String> roles = new ArrayList<String>();

		Collection<? extends GrantedAuthority> authorities = SecurityContextHolder.getContext()
			.getAuthentication().getAuthorities();

		for ( GrantedAuthority grantedAuthority : authorities ) {

			if ( grantedAuthority.getAuthority().equals( infraGroup ) ) {
				roles.add( INFRA_ROLE );
			}

			if ( grantedAuthority.getAuthority().equals( adminGroup ) ) {
				roles.add( ADMIN_ROLE );
			}
			if ( grantedAuthority.getAuthority().equals( buildGroup ) ) {
				roles.add( BUILD_ROLE );
			}
			if ( grantedAuthority.getAuthority().equals( viewGroup ) ) {
				roles.add( VIEW_ROLE );
			}
		}

		Object principle = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
		String userName = "dummy";
		try {
			UserDetails person = (UserDetails) principle;
			userName = person.getUsername();
		} catch (Exception e) {
			logger.warn( "Falling back to default user" );
			User p = (User) principle;
			userName = p.getUsername();
		}

		if ( superUsers.contains( userName ) ) {
			roles.add( ADMIN_ROLE );
			roles.add( BUILD_ROLE );
			roles.add( VIEW_ROLE );
			roles.add( INFRA_ROLE );
		}

		return roles;
	}

	// Helper class for ui
	@SuppressWarnings ( "unchecked" )
	public List<String> getAndStoreUserRoles ( HttpSession session ) {

		// Cache in session for performance
		if ( session.getAttribute( "USER_ROLES" ) == null ) {
			session.setAttribute( "USER_ROLES", getUserRolesFromContext() );
		}
		return (List<String>) session.getAttribute( "USER_ROLES" );
	}

	public String getUserIdFromContext () {
		String userName = "NotFoundUser";
		try {
			UserDetails person = (UserDetails) SecurityContextHolder.getContext()
				.getAuthentication().getPrincipal();
			userName = person.getUsername();
		} catch (Exception e) {
			// if (capabilityManager.isBootstrapComplete())
			logger.warn( "Failed to get user from Spring security context" );
		}
		return userName;
	}

	public String getSearchUser () {
		return searchUser;
	}

	public Name getRealUserDn ( String userid ) {

		return LdapNameBuilder.newInstance( searchUser )
			.add( "uid", userid )
			.build();
	}

	public Name getGenericUserDn ( String userid ) {

		return LdapNameBuilder.newInstance( genericUseridTree )
			.add( "uid", userid )
			.build();
	}

	public Name getLdapSearchUser () {

		return LdapNameBuilder.newInstance( genericUseridTree )
			.add( "uid", directoryUser )
			.build();

	}

	public boolean isCsrfEnabled () {
		return csrfEnabled;
	}

	public void setCsrfEnabled ( boolean csrfEnabled ) {
		this.csrfEnabled = csrfEnabled;
	}
	
	/**
	 * New Sessions initiated by user will result in previous session being invalidated - error page will be displayed
	 * default: 0 (unlimited)
	 * 
	 * @param maxConcurrentUserSessions
	 */
	public int getMaxConcurrentUserSessions () {
		return maxConcurrentUserSessions;
	}


	public void setMaxConcurrentUserSessions ( int maxConcurrentUserSessions ) {
		this.maxConcurrentUserSessions = maxConcurrentUserSessions;
	}

}
