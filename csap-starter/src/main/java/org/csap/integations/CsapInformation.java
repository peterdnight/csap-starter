package org.csap.integations;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.naming.NamingEnumeration;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

import org.csap.alerts.AlertsController;
import org.csap.alerts.MonitorMbean;
import org.csap.docs.CsapDoc;
import org.csap.helpers.CsapSimpleCache;
import org.csap.security.CustomUserDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.core.env.Environment;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@RestController("csapInformation")
@ConfigurationProperties(prefix = "csap.info")
@RequestMapping("${csap.baseContext:/csap}")
@CsapDoc(title = "CSAP Information Controller", type = CsapDoc.OTHER, 
	notes = "@Inject CsapInformation into your spring beans for convenient access to CSAP variables and configuration settings (workingDir, etc.)")
public class CsapInformation {

	public CsapInformation( ) {

	}

	@Value("${csap.baseContext:/csap}")
	public String csapBaseContext;

	@Autowired
	CsapBootConfig config;

	@Autowired(required = false)
	CsapPerformance performanceConfig;

	@Autowired(required = false)
	CsapSecurityConfiguration securityConfig;

	final Logger logger = LoggerFactory.getLogger( getClass() );

	ObjectMapper jacksonMapper = new ObjectMapper();

	@RequestMapping(value = "/identity")
	@CsapDoc(notes = "Shows User Identity from security information", baseUrl = "/csap")
	public ObjectNode identity ()
			throws Exception {

		ObjectNode idNode = jacksonMapper.createObjectNode();
		if ( SecurityContextHolder.getContext().getAuthentication() == null ) {
			idNode.put( "authentication-source", "disabled" );
			return idNode;
		}
		UserDetails person = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
		CustomUserDetails customUser = null;
		idNode.put( "userid", person.getUsername() );

		logger.debug( "Security Principle: {}", person );
		if ( person instanceof CustomUserDetails ) {
			idNode.put( "authentication-source", "LDAP" );
			idNode.put( "url", securityConfig.getUrl() );

			customUser = (CustomUserDetails) person;
			idNode.put( "name-cn", customUser.getCn()[0] );
			idNode.put( "mail", customUser.getMail() );
			idNode.put( "mail-extended", customUser.getAllAttributesInConfigFile().get( "mail" ).toString() );

		} else {
			idNode.put( "authentication-source", "In Memory" );
		}

		Collection<? extends GrantedAuthority> gaArray = SecurityContextHolder.getContext().getAuthentication().getAuthorities();
		ArrayNode rolesNode = idNode.putArray( "user-roles" );
		for ( GrantedAuthority ga : gaArray ) {
			rolesNode.add( ga.toString() );
		}

		if ( customUser != null ) {
			ObjectNode customNode = idNode.putObject( "custom-user-attributes" );
			NamingEnumeration<String> idEnum = customUser.getAllAttributesInConfigFile().getIDs();

			while (idEnum.hasMore()) {

				String id = idEnum.next();
				String attributeValues = customUser.getAllAttributesInConfigFile().get( id ).get().toString();
				customNode.put( id, attributeValues );
			}
		}

		return idNode;
	}

	@RequestMapping(value = "/csapInfo")
	@CsapDoc(notes = "Primary configuration data, including csap environment variabale and Servlet information", baseUrl = "/csap")
	public ObjectNode showSecureConfiguration (
												HttpServletRequest request )
			throws Exception {

		logger.debug( "Getting data" );

		ObjectNode infoNode = jacksonMapper.createObjectNode();

		ObjectNode csapNode = infoNode.putObject( "csapEnvironmentVariables" );

		csapNode.put( "getName", getName() );
		csapNode.put( "getVersion", getVersion() );
		csapNode.put( "getLoadBalancerUrl", getLoadBalancerUrl() );
		csapNode.put( "getLifecycle", getLifecycle() );
		csapNode.put( "getPort", getHttpPort() );
		csapNode.put( "getCluster", getCluster() );

		ObjectNode commonNode = infoNode.putObject( "csapCommonConfig" );
		ArrayNode monitorUrls = commonNode.putArray( "monitorUrls" );
		for ( String url : performanceConfig.getMonitorUrls() ) {
			monitorUrls.add( url );
		}

		ObjectNode servlet = infoNode.putObject( "servlet" );

		ObjectNode core = servlet.putObject( "core" );
		core.put( "getRemoteUser", request.getRemoteUser() );
		core.put( "getRemoteAddr", request.getRemoteAddr() );
		core.put( "getRemoteHost", request.getRemoteHost() );
		core.put( "getRemotePort", request.getRemotePort() );
		core.put( "getServerName", request.getServerName() );
		core.put( "getServletPath", request.getServletPath() );
		core.put( "getRequestURI", request.getRequestURI() );

		ObjectNode requestHeaders = servlet.putObject( "requestHeaders" );
		Enumeration<String> names = request.getHeaderNames();
		while (names.hasMoreElements()) {
			String name = (String) names.nextElement();
			if ( name.startsWith( "cookie" ) ) {
				continue;
			}
			String value = request.getHeader( name );

			requestHeaders.put( name, value );
		}

		ObjectNode cookieNode = servlet.putObject( "cookies" );
		if ( request.getCookies() != null ) {
			for ( Cookie cookie : request.getCookies() ) {
				cookieNode.put( cookie.getName(), cookie.getPath() + " , " + cookie.getDomain() + " ,"
						+ cookie.getSecure() + " ," + cookie.getValue() );

			}
		}

		ObjectNode requestAtt = servlet.putObject( "attributes" );
		names = request.getAttributeNames();
		while (names.hasMoreElements()) {
			String name = (String) names.nextElement();

			String value = request.getHeader( name );

			requestAtt.put( name, value );
		}

		return infoNode;

	}

	@Autowired(required = false)
	private CacheManager cacheManager;

	@CsapDoc(notes = "Show and clear ehcache entires, show CSAP simple cache entries", baseUrl = "/csap")
	@RequestMapping(value = "/cache/show")
	public ObjectNode cacheShow ( HttpServletRequest request ) {

		ObjectNode cacheEntries = jacksonMapper.createObjectNode();
		cacheEntries.put( "clear all",
			request.getRequestURL() + "/../clear/all" );

		cacheManager.getCacheNames()
			.stream()
			.map( cacheManager::getCache )
			.map( Cache::getNativeCache )
			.filter( net.sf.ehcache.Cache.class::isInstance )
			.map( net.sf.ehcache.Cache.class::cast )
			.forEach( cache -> {
				ObjectNode cacheEntry = cacheEntries.putObject( cache.getName() );
				cacheEntry.put( "clear", request.getRequestURL() + "/../clear/" + cache.getName() );
				List<?> keys = cache.getKeys();
				if ( null != keys ) {
					for ( int i = 0; i < keys.size(); i++ ) {
						Object key = keys.get( i );
						String value = "null";
						if ( cache.get( key ) != null && cache.get( key ).getObjectValue() != null ) {
							value = cache.get( key ).getObjectValue().toString();
						}
						;
						cacheEntry.put( key.toString(), value );
					}
				}
			} );

		ArrayNode simpleCacheArray = cacheEntries.putArray( "CsapSimpleCache" );

		CsapSimpleCache.getCacheReferences().forEach( cache -> {
			ObjectNode simple = simpleCacheArray.addObject();
			simple.put( "class", cache.getClassName() );
			simple.put( "description", cache.getDescription() );
			simple.put( "maxAge", cache.getMaxAgeFormatted() );
			simple.put( "currentAge", cache.getCurrentAgeFormatted() );
		} );

		return cacheEntries;
	}

	@CsapDoc(notes = "Empty cache on the server ")
	@GetMapping(value = "/cache/clear/{cacheToClear}")
	public ObjectNode cacheClear (
									@PathVariable String cacheToClear,
									HttpServletRequest request ) {

		ObjectNode resultNode = jacksonMapper.createObjectNode();

		resultNode.put( "clearing", cacheToClear );
		resultNode.put( "show",
			request.getRequestURL() + "/../../show" );

		logger.warn( "Clearing caches: {} ", cacheToClear );

		cacheManager.getCacheNames()
			.stream()
			.filter( ( cacheName ) -> cacheName.equals( cacheToClear )
					|| cacheToClear.equals( "all" ) )
			.map( cacheManager::getCache )
			.map( Cache::getNativeCache )
			.filter( net.sf.ehcache.Cache.class::isInstance )
			.map( net.sf.ehcache.Cache.class::cast )
			.forEach( net.sf.ehcache.Cache::removeAll );

		// for(String cacheName : cacheNames){
		//
		// if ( cacheName)
		// net.sf.ehcache.Cache cache =
		// (net.sf.ehcache.Cache)cacheManager.getCache(cacheName).getNativeCache();
		// cache.removeAll();
		// }
		resultNode.put( "completed", LocalDateTime.now().format( DateTimeFormatter.ofPattern( "HH:mm:ss,   MMMM d  uuuu " ) ) );
		return resultNode;
	}

	@Autowired
	private Environment springEnv;

	public Map<String, String> buildToolsMap () {
		Map<String, String> tools = new LinkedHashMap<>();

		tools.put( "CSAP Performance", getCsapBaseContext() + "/health" );
		tools.put( "CSAP API Browser", getDocUrl() + "/nav" );
		tools.put( "CSAP Identity", getCsapBaseContext() + "/identity" );
		tools.put( "CSAP Information", getCsapBaseContext() + "/csapInfo" );
		tools.put( "CSAP Cache", getCsapBaseContext() + "/cache/show" );
		tools.put( "Simon", getSimonUrl() );
		// tools.put( "Cache - clear", "/cache/clear" );

		String bootActuatorContext = springEnv.getProperty( "management.context-path" );

		if ( bootActuatorContext == null ) {
			bootActuatorContext = "";
		}
		logger.debug( "bootActuatorContext: {}", bootActuatorContext );

		tools.put( "Host Environment", bootActuatorContext + "/env" );

		tools.put( "Thread Dump", bootActuatorContext + "/dump" );
		tools.put( "Http Trace", bootActuatorContext + "/trace" );
		tools.put( "Boot Info", bootActuatorContext + "/info" );
		tools.put( "Boot Config", bootActuatorContext + "/configprops" );
		tools.put( "Boot Health", bootActuatorContext + "/health" );
		tools.put( "Boot Metrics", bootActuatorContext + "/metrics" );
		tools.put( "Boot Beans", bootActuatorContext + "/beans" );
		tools.put( "Boot Url Mappings", bootActuatorContext + "/mappings" );

		return tools;
	}

	private String name;
	private String loadBalancerUrl;
	private String lifecycle = "dev";
	private String version;
	private String httpPort;
	private String workingDir;
	private String cluster;
	private String domain = "yourcompany.com";

	public String getTime () {
		return LocalDateTime.now().format( DateTimeFormatter.ofPattern( "HH:mm:ss,   MMMM d  uuuu " ) );
	}

	public String getName () {
		return name;
	}

	public void setName ( String name ) {
		this.name = name;
	}

	public String getLoadBalancerUrl () {
		return loadBalancerUrl;
	}

	public void setLoadBalancerUrl ( String loadBalancerUrl ) {
		this.loadBalancerUrl = loadBalancerUrl;
	}

	public String getVersion () {
		return version;
	}

	public void setVersion ( String version ) {
		this.version = version;
	}

	public String getHttpPort () {
//		return httpPort;
		String serverPort = springEnv.getProperty( "server.port" );
		if ( serverPort == null  ) {
			serverPort = "7777";
		}
		return serverPort ;
	}

	public void setHttpPort ( String port ) {
		this.httpPort = port;
	}

	public String getFullServiceUrl () {
		return "http://" + getHostName() + "." + getDomain() + ":" + getHttpPort() + getHttpContext ();
	}
	
	public String getFullServiceCsapUrl () {
		return getFullServiceUrl() + getCsapBaseContext()   ;
	}

	public String getHttpContext () {
		String context = springEnv.getProperty( "server.context-path" );
		if ( context == null ||
				(context.length() == 1 && context.equals( "/" )) ) {
			context = "";
		}
		return context;
	}

	public String getCluster () {
		return cluster;
	}

	public void setCluster ( String cluster ) {
		this.cluster = cluster;
	}

	public String getLifecycle () {
		return lifecycle;
	}

	public void setLifecycle ( String lifecycle ) {
		this.lifecycle = lifecycle;
	}

	public String getHostName () {
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

	public String toString () {
		StringBuilder infoBuilder = new StringBuilder();
		infoBuilder.append( "\n === csap.information:" );
		infoBuilder.append( "\n\t name:  " + getName() );
		infoBuilder.append( "\n\t httpPort:  " + getHttpPort() );
		infoBuilder.append( "\n\t lb url:  " + getLoadBalancerUrl() );
		infoBuilder.append( "\n\t direct url:  " + getFullServiceUrl() );
		infoBuilder.append( "\n\t version:  " + getVersion() );
		infoBuilder.append( "\n\t workingDir:  " + getWorkingDir() );
		infoBuilder.append( "\n\t baseContext:  " + csapBaseContext );
		infoBuilder.append( "\n" );

		return infoBuilder.toString();
	}

	public String getWorkingDir () {
		return workingDir;
	}

	public void setWorkingDir ( String workingDir ) {
		this.workingDir = workingDir;
	}

	public String getDomain () {
		return domain;
	}

	public void setDomain ( String domain ) {
		this.domain = domain;
	}

	public String getCsapBaseContext () {
		return csapBaseContext;
	}

	public String getFullHealthUrl () {
		return getFullServiceCsapUrl() + AlertsController.HEALTH_URL;
	}
	public String getSimonUrl () {
		return getCsapBaseContext() + "/simon";
	}

	public String getDocUrl () {
		return getCsapBaseContext() + "/docs";
	}
}
