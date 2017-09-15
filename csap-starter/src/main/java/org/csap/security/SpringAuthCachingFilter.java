package org.csap.security;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.FileUtils;
import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.javasimon.SimonManager;
import org.javasimon.Split;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.authentication.configurers.provisioning.UserDetailsManagerConfigurer.UserDetailsBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.web.context.support.WebApplicationContextUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 
 * HelperMethod for securing rest apis. Extend this class, and ensure that
 * spring context includes beans of type
 * 
 * @see AuthenticationManager
 * 
 *      Example
 * @Bean(name = "myAuthenticationManager")
 * @Override public AuthenticationManager authenticationManagerBean() throws
 *           Exception { return super.authenticationManagerBean(); }
 * 
 * @author pnightin
 *
 *         Jersey prefers interceptors, but filters will work in most scenarios:
 *         https://java.net/projects/jax-rs-spec/lists/users/archive/2014-02/
 *         message/0
 *
 */

// @WebFilter(urlPatterns = { "/api/*" }, description = "Api Security Filter",
// initParams = {
// @WebInitParam(name = "placeHolderForFuture", value = "whenNeeded") })
public class SpringAuthCachingFilter implements Filter {

	final static Logger logger = LoggerFactory.getLogger( SpringAuthCachingFilter.class );

	private String group;
	private int cacheSeconds;

	public SpringAuthCachingFilter( String group, int cacheSeconds ) {
		this.group = group;
		this.cacheSeconds = cacheSeconds;
	}

	private ApplicationContext springAppContext;

	public ApplicationContext getSpringAppContext () {
		return springAppContext;
	}

	ObjectMapper jacksonMapper = new ObjectMapper();

	/**
	 * return a group to validate role
	 * 
	 * @return
	 */
	public String getGroup () {
		return group;
	};

	/**
	 * return 0 to disable
	 * 
	 * @return
	 */
	public int getCacheSeconds () {
		return cacheSeconds;
	};

	@Override
	public void init ( FilterConfig filterConfig )
			throws ServletException {
		springAppContext = WebApplicationContextUtils.getWebApplicationContext( filterConfig
			.getServletContext() );
		logger.debug(
			"Security will be applied when either userid or password is found in request params, and cached for: {} seconds.\n springContext: {}",
			getCacheSeconds(), springAppContext );
	}

	final public static String USERID = "userid";
	final public static String PASSWORD = "pass";

	final static public String SEC_RESPONSE_ATTRIBUTE = "securityResponse";

	private String localUser = null;
	private String localPass = null;

	public void setLocalCredentials ( String user, String pass ) {
		logger.info( "***** Adding local credential: {}", user );
		this.localUser = user;
		this.localPass = pass;

	}

	@Override
	public void doFilter ( ServletRequest request, ServletResponse resp, FilterChain filterChain )
			throws IOException, ServletException {

		logger.debug( "Intercepted Request" );
		SimonManager.getCounter( "csap.security.filter" ).increase();

		HttpServletResponse response = (HttpServletResponse) resp;

		int numPurged = purgeStaleAuth();
		logger.debug( "Entries being purged from cache count:  {}", numPurged );

		if ( request.getParameter( USERID ) != null
				&& request.getParameter( PASSWORD ) != null ) {

			ObjectNode resultJson = jacksonMapper.createObjectNode();

			resultJson.put( "userid", request.getParameter( USERID ) );

			request.setAttribute( SEC_RESPONSE_ATTRIBUTE, resultJson );
			String userid = request.getParameter( USERID );
			String inputPass = request.getParameter( PASSWORD );

			if ( this.localUser != null && this.localUser.equals( userid )
					&& this.localPass != null ) {
				String pass = inputPass;
				StandardPBEStringEncryptor encryptor = springAppContext
					.getBean( StandardPBEStringEncryptor.class );
				try {
					pass = encryptor.decrypt( inputPass );
				} catch (Exception e1) {
					resultJson.put( "passwordWarning", "Use of encrypted passwords is recommended." );
					resultJson.put( "passwordEncrypted", encryptor.encrypt( pass ) );
				}

				logger.debug( "Found local user: {} ", userid );
				if ( this.localPass.equals( pass ) ) {
					SimonManager.getCounter( "csap.security.filter.local.pass" ).increase();
				} else {
					SimonManager.getCounter( "csap.security.filter.local.fail" ).increase();					
					resultJson.put( "error", "Failed to authenticate local user: " + userid );
					response.getWriter().println( jacksonMapper.writeValueAsString( resultJson ) );
					response.setStatus( 403 );
					return;
				}

			} else if ( isAuthorizationCached( userid, inputPass ) ) {

				SimonManager.getCounter( "csap.security.filter.cache" ).increase();
				logger.debug( "Found Cached user: {} ", userid );

			} else {

				StandardPBEStringEncryptor encryptor = springAppContext
					.getBean( StandardPBEStringEncryptor.class );

				String pass = inputPass;
				try {
					pass = encryptor.decrypt( inputPass );
				} catch (Exception e1) {
					resultJson.put( "passwordWarning", "Use of encrypted passwords is recommended." );
					resultJson.put( "passwordEncrypted", encryptor.encrypt( pass ) );
				}

				boolean authenticated = false;
				boolean authorized = false;
				Split split = SimonManager.getStopwatch( "csap.security.filter.ldap" ).start();
				try {
					// Use spring inject auth manager, which enables multiple
					// providers
					AuthenticationManager authManager = springAppContext
						.getBean( AuthenticationManager.class );
					UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
						userid, pass );
					Authentication a = authManager.authenticate( authToken );
					a.getAuthorities();

					logger.debug( "Authenticated via authmanager, authorities:  {}", a.getAuthorities().toString() );
					authenticated = true;

					// Spring security uses role prefix
					authorized = a.getAuthorities().toString().contains( getGroup() );

				} catch (Throwable e) {
					logger.warn( "Failed authenticating userid: {}, keyField: {}, Reason: {}", userid,
						getKeyField( request ), e.getMessage() );
					if ( logger.isDebugEnabled() )
						logger.debug( "StackTrace", e );
				}
				split.stop();

				if ( !authenticated ) {

					SimonManager.getCounter( "csap.security.filter.authenticate.fail" ).increase();
					SimonManager.getCounter( "csap.security.filter.authenticate.fail." + userid ).increase();
					resultJson.put( "error", "Failed to authenticate user: " + userid );
					response.getWriter().println( jacksonMapper.writeValueAsString( resultJson ) );
					response.setStatus( 403 );
					return;
				} else if ( !authorized ) {
					SimonManager.getCounter( "csap.security.filter.authorize.fail" ).increase();
					SimonManager.getCounter( "csap.security.filter.authorize.fail." + userid ).increase();
					resultJson.put( "error", "Failed to  authorized user: " + userid
							+ " Must be a member of: " + getGroup() );
					response.getWriter().println( jacksonMapper.writeValueAsString( resultJson ) );
					response.setStatus( 401 );
					return;
				} else {
					SimonManager.getCounter( "csap.security.filter.success" ).increase();
					SimonManager.getCounter( "csap.security.filter.success." + userid ).increase();
					long currentTimeInMillis = System.currentTimeMillis();
					Long result = authenticatedUsersCache.putIfAbsent( userid + "~" + inputPass,
						currentTimeInMillis );
					logger.debug( "Cached entry: {}", result );
				}
			}

		}

		filterChain.doFilter( request, response );

	}

	// Over ride if you want to include an identify string in logs on failures
	// Default implementation looks for eventJson param used by CSAP data
	// services.
	protected String getKeyField ( ServletRequest request ) {
		String result = "none";
		String jsonParam = request.getParameter( "eventJson" );
		logger.debug( "Payload: {}", jsonParam );
		if ( jsonParam != null ) {
			try {
				JsonNode node = jacksonMapper.readTree( jsonParam );

				result = node.findValue( "host" ).asText();
			} catch (IOException e) {
				logger.debug( "Failed to parse eventJson parameter" );
			}
		}
		return result;
	}

	long lastPurgeTime = 0;
	private Lock cacheLock = new ReentrantLock();

	private int purgeStaleAuth () {

		int numStaleEntries = 0;

		// Caching disabled
		if ( getCacheSeconds() == 0 )
			return numStaleEntries;

		long maxAge = getCacheSeconds() * 1000;

		long now = System.currentTimeMillis();

		// Only execute intermittenly as purges slow down performance
		if ( now - lastPurgeTime < maxAge ) {
			return numStaleEntries;
		}
		lastPurgeTime = now;

		// no need to block multiple threads behind the load
		if ( cacheLock.tryLock() ) {
			try {
				logger.debug( "Running Purge Logic" );
				cacheLock.lock();
				for ( Iterator<Map.Entry<String, Long>> authIter = authenticatedUsersCache.entrySet()
					.iterator(); authIter.hasNext(); ) {
					Map.Entry<String, Long> authEntry = authIter.next();
					if ( now - authEntry.getValue().longValue() > maxAge ) {
						authIter.remove();
						numStaleEntries++;
					}

				}
			} catch (Exception e) {
				logger.error( "Failed to prune auth cache" );
				;
			} finally {
				cacheLock.unlock();
			}
		}

		return numStaleEntries;

	}

	// http://javarevisited.blogspot.com/2013/02/concurrenthashmap-in-java-example-tutorial-working.html
	private ConcurrentHashMap<String, Long> authenticatedUsersCache = new ConcurrentHashMap<>();

	private boolean isAuthorizationCached ( String userId, String inputPasswd ) {

		// Caching disabled
		if ( getCacheSeconds() == 0 )
			return false;

		logger.debug( "authCache: {}", authenticatedUsersCache );
		return authenticatedUsersCache.containsKey( userId + "~" + inputPasswd );

	}

	@Override
	public void destroy () {
		// TODO Auto-generated method stub

	}

	/**
	 * A rare scenario - useful for running without LDAP security. This enables
	 * same file to be used everywhere
	 * 
	 * @param auth
	 * @throws IOException
	 * @throws Exception
	 * 
	 */
	public static void configureInMemoryUsers ( AuthenticationManagerBuilder auth )
			throws IOException,
			Exception {

		File inMemoryUsers = getAuthFile();

		List<String> userLines = FileUtils.readLines( inMemoryUsers );

		StringBuilder users = new StringBuilder( "Using in memory auth: "
				+ inMemoryUsers.getAbsolutePath() );

		for ( String line : userLines ) {
			String[] columns = line.split( "," );
			if ( columns.length >= 3 &&
					columns[0].trim().length() != 0
					&& !columns[0].trim().startsWith( "#" ) ) {

				UserDetailsBuilder builder = auth.inMemoryAuthentication()
					.withUser( columns[0] );

				builder.password( columns[1] );
				builder.roles( Arrays.copyOfRange( columns, 2, columns.length ) );
				users.append( "\n" + Arrays.asList( columns ) );

			}
		}

	}

	public static File getAuthFile () {
		File inMemoryUsers = new File( System.getenv().get( "STAGING" )
				+ "/conf/propertyOverride/csapSecurityMemory.txt" );

		if ( !inMemoryUsers.canRead() ) {
			try {
				inMemoryUsers = (new ClassPathResource( "csapSecurityMemory.txt" )).getFile();
			} catch (IOException e) {
				logger.warn( "failed to locate users.txt file. Add to classpath" );
			}
		}
		return inMemoryUsers;
	}

}
