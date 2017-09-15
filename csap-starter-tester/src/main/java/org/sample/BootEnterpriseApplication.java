package org.sample;

import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.ApplicationPath;

import org.apache.commons.dbcp2.BasicDataSource;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.csap.CsapMicroService;
import org.csap.helpers.CsapRestTemplateFactory;
import org.csap.integations.CsapPerformance;
import org.csap.integations.CsapRolesEnum;
import org.csap.integations.CsapSecurityConfiguration;
import org.glassfish.jersey.server.ResourceConfig;
import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.jasypt.exceptions.EncryptionOperationNotPossibleException;
import org.sample.input.http.jersey.HelloJaxrsResource;
import org.sample.input.http.jersey.JerseyEventListener;
import org.sample.input.http.jersey.JerseyExceptionProvider;
import org.sample.input.http.jersey.JerseyResource;
import org.sample.input.http.jersey.SimpleConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;
import org.springframework.web.servlet.resource.VersionResourceResolver;

// Consider use of: @CsapBootApplication or @SpringBootApplication 
@CsapMicroService
@ConfigurationProperties ( prefix = "my-service-configuration" )
@Aspect
public class BootEnterpriseApplication extends WebMvcConfigurerAdapter {

	final static Logger logger = LoggerFactory.getLogger( BootEnterpriseApplication.class );

	public static void main ( String[] args ) {

		SpringApplication.run( BootEnterpriseApplication.class, args );
	}

	/**
	 * Statics used through out app
	 */
	public final static String SIMPLE_CACHE_EXAMPLE = "sampleCacheWithNoExpirations";
	public final static String TIMEOUT_CACHE_EXAMPLE = "sampleCacheWith10SecondEviction";
	public final static String JMS_REPORT_CACHE = "jmsReportCache";
	public final static String BASE_URL = "/";
	public final static String SPRINGAPP_URL = BASE_URL + "spring-app";
	public final static String SPRINGREST_URL = BASE_URL + "spring-rest";
	public final static String LARGE_PARAM_URL = "/largePayload";
	public final static String API_URL = BASE_URL + "api";
	public final static String JERSEY_URL = BASE_URL + "jersey";

	public static boolean isRunningOnDesktop () {
		if ( System.getenv( "STAGING" ) == null ) {
			return true;
		}
		return false;
	}

	int ONE_YEAR_SECONDS = 60 * 60 * 24 * 365;
	final String VERSION = "1.1";

	// https://spring.io/blog/2014/07/24/spring-framework-4-1-handling-static-web-resources
	// http://www.mscharhag.com/spring/resource-versioning-with-spring-mvc
	@Override
	public void addResourceHandlers ( ResourceHandlerRegistry registry ) {

		if ( isRunningOnDesktop() ) {
			logger.warn( "\n\n\n Desktop detected: Caching DISABLED \n\n\n" );
			ONE_YEAR_SECONDS = 0;
		}

		VersionResourceResolver versionResolver = new VersionResourceResolver()
			// .addFixedVersionStrategy( version, "/**/*.js" ) //Enable this
			// if we use a JavaScript module loader
			.addContentVersionStrategy( "/**" );

		// A Handler With Versioning - note images in css files need to be
		// resolved.
		registry.addResourceHandler( "/**/*.js", "/**/*.css", "/**/*.png", "/**/*.gif", "/**/*.jpg" )
			.addResourceLocations( "classpath:/static/", "classpath:/public/" )
			.setCachePeriod( ONE_YEAR_SECONDS )
			.resourceChain( true )
			.addResolver( versionResolver );

	}

	// Default post limit is 2MB
	// @Bean
	// EmbeddedServletContainerCustomizer containerCustomizer() throws Exception
	// {
	// return (ConfigurableEmbeddedServletContainer container) -> {
	// if (container instanceof TomcatEmbeddedServletContainerFactory) {
	// TomcatEmbeddedServletContainerFactory tomcat =
	// (TomcatEmbeddedServletContainerFactory) container;
	// tomcat.addConnectorCustomizers(
	// (connector) -> {
	// connector.setMaxPostSize(4*1024*1024); // 4 MB
	// }
	// );
	// }
	// };
	// }
	// @formatter:off
	@Bean
	@ConditionalOnProperty("csap.security.enabled")
	public CsapSecurityConfiguration.CustomHttpSecurity httpAuthorization() {

		// ref
		// https://docs.spring.io/spring-security/site/docs/3.0.x/reference/el-access.html
		// String rootAcl="hasRole('ROLE_AUTHENTICATED')";

		return (httpSecurity -> {
			httpSecurity
				.authorizeRequests()
					
					// Public assets
					.antMatchers( "/webjars/**", "/noAuth/**", "/js/**", "/css/**", "/images/**" )
						.permitAll()

					// jersey api uses caching filter
					.antMatchers( SPRINGREST_URL + LARGE_PARAM_URL + "/**" )
						.permitAll()

					// jersey api uses caching filter
					.antMatchers( "/jersey/**" )
						.permitAll()

					// simple method to ensure ACL page is displayed
					.antMatchers( "/testAclFailure" )
						.hasRole( "NonExistGroupToTriggerAuthFailure" )

					// authorize using CSAP admin group - this app includes destructive
					.anyRequest()
						.access( CsapSecurityConfiguration.hasAnyRole( CsapRolesEnum.admin ) );
//			// tests.
//					.antMatchers( "/**", "/more/**", "/andMoreUrls/**" )
//					.access( "ROLE_<something in LDAP>" );

		});

	}
	// @formatter:off
	
	
	// configure @Scheduled thread pool;
	@Bean
	public TaskScheduler taskScheduler () {
		ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
		scheduler.setThreadNamePrefix( BootEnterpriseApplication.class.getSimpleName() + "@Scheduler" );
		scheduler.setPoolSize( 1 );
		return scheduler;
	}

	// configure @Async thread pool. Use named pools for workload segregation:
	// @Async("CsapAsync")

	final public static String ASYNC_EXECUTOR = "CsapAsynExecutor";

	@Bean(ASYNC_EXECUTOR)
	public TaskExecutor taskExecutor () {
		ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
		taskExecutor.setThreadNamePrefix( BootEnterpriseApplication.class.getSimpleName() + "@Async" );
		taskExecutor.setMaxPoolSize( 5 );
		taskExecutor.setQueueCapacity( 100 );
		taskExecutor.afterPropertiesSet();
		return taskExecutor;
	}
	
	

	/**
	 * 
	 * ============== Rest Templates use for demos
	 * 
	 */
	@Bean(name = "aTrivialRestSampleId")
	public RestTemplate getRestTemplate() {
		RestTemplate restTemplate = new RestTemplate();
		List<HttpMessageConverter<?>> messageConverters = new ArrayList<HttpMessageConverter<?>>();
		messageConverters.add( new SimpleConverter() );
		restTemplate.setMessageConverters( messageConverters );
		return restTemplate;
	}

	@Bean
	public CsapRestTemplateFactory csapRestFactory() {
		
		return new CsapRestTemplateFactory();
	}
	
	@Bean(name = "csAgentRestTemplate")
	public RestTemplate csAgentRestTemplate( CsapRestTemplateFactory factory) {
		
		return factory.buildJsonTemplate( "agentApi", 6, 10, 3, 1, 10 );
	}
	
	@Bean(name = "jmsQueueQueryTemplate")
	public RestTemplate jmsQueueQueryTemplate( CsapRestTemplateFactory factory ) {
		
		return factory.buildJsonTemplate( "JmsProcessingMonitor", 1, 40, 3, 1, 60 );
	}

	/**
	 * 
	 * =================== Jersey integration
	 */
	@Configuration
	@ApplicationPath("/jersey")
	public static class JerseyConfig extends ResourceConfig {
		public JerseyConfig() {

			// register( new JerseyEventListener( ) ) ;
//			logger.warn( "\n\n =============== Spring Jersey Initialization ============\n"
//					+ "base url: /jersey\t\t Packages Scanned: org.sample.input.http.jersey"
//					+ "jersey debug will appear if you set debug on JerseyEventListener"
//					+ "\n\n" );
			
			// BUG in jersey prevents resource scanning when running from spring boot jar
			// workaround: https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-1.4-Release-Notes#jersey-classpath-scanning-limitations
			// workaround - CSAP run off extracted jars - so do eclipse and IDES - so this can be ignored.
			//
			// packages( "org.sample.input.http.jersey" );
			
			// alternately - load explicitly - for use with executable jars and skipping need to extract into tmp.
			logger.warn( "Using jersey explicit registration" );
			register( HelloJaxrsResource.class) ;
			register( JerseyEventListener.class) ;
			register( JerseyExceptionProvider.class) ;
			register( JerseyResource.class) ;

		}
	}



;

	// Use CsapPropertyEncrypter for decrypt support
	// @Value("${db.password}")
	// private String password;

	@Bean(destroyMethod = "close")
	public BasicDataSource helloDataSource(StandardPBEStringEncryptor encryptor) {
		BasicDataSource helloDataSource = new BasicDataSource();
		helloDataSource.setDriverClassName( getDb().getDriverClassName() );
		helloDataSource.setUrl( getDb().getUrl() );
		helloDataSource.setUsername( getDb().getUsername() );
		helloDataSource.setPassword( getDb().getPassword() );
		try {
			helloDataSource.setPassword( encryptor.decrypt( getDb().getPassword() ) );
		} catch (EncryptionOperationNotPossibleException e) {
			logger.warn( "Password is not encrypted. Use CSAP encrypt to generate" );
		}
		// helloDataSource.setMaxWait(500);
		helloDataSource.setMaxWaitMillis( 500 );
		helloDataSource.setTestWhileIdle( true );
		helloDataSource.setMinEvictableIdleTimeMillis( getDb().getIdleEvictionMs()  );
		helloDataSource.setTimeBetweenEvictionRunsMillis( getDb().getIdleEvictionMs() );
		helloDataSource.setMaxIdle( getDb().getMaxIdle() ); 
		helloDataSource.setMaxTotal( getDb().getMaxActive() );

		StringBuilder builder = new StringBuilder();
		builder.append( "\n\n ==========================" );
		builder.append( "\n Constructed DB Connection Pool with: " );
		builder.append( "\n Url: " + helloDataSource.getUrl() );
		builder.append( "\n getUsername: " + helloDataSource.getUsername() );
		builder.append( "\n getMaxWait: " + helloDataSource.getMaxWaitMillis() );
		builder.append( "\n Time before marked idle: " + helloDataSource.getMinEvictableIdleTimeMillis() );
		builder.append( "\n getTimeBetweenEvictionRunsMillis: " + helloDataSource.getTimeBetweenEvictionRunsMillis() );
		builder.append( "\n Max Idle Connections: " + helloDataSource.getMaxIdle() );
		builder.append( "\n Max Total Connections: " + helloDataSource.getMaxTotal() );
		builder.append( "\n getInitialSize: " + helloDataSource.getInitialSize() );
		builder.append( "\n " );
		builder.append( "\n==========================\n\n" );

		logger.warn( builder.toString() );
		return helloDataSource;

	}

	public static final String JPA_PC = "within(org.sample.jpa.Demo_DataAccessObject)";

	/**
	 * 
	 * java simon monitoring
	 * 
	 */
	@Pointcut("within(org.apache.commons.dbcp2.BasicDataSource*)")
	private void dbcpPC() {
	};

	@Around("dbcpPC()")
	public Object dbcpAdvice(ProceedingJoinPoint pjp)
			throws Throwable {

		Object obj = CsapPerformance.executeSimon( pjp, "dbcp." );

		return obj;
	}

	@Pointcut(JPA_PC)
	private void jpaPC() {
	};

	@Around("jpaPC()")
	public Object jpaAdvice(ProceedingJoinPoint pjp)
			throws Throwable {

		Object obj = CsapPerformance.executeSimon( pjp, "jpa." );

		return obj;
	}
	
	public DatabaseSettings getDb () {
		return db;
	}

	public void setDb ( DatabaseSettings db ) {
		this.db = db;
	}
	

	public HealthSettings getJmsBacklogHealth () {
		return jmsBacklogHealth;
	}

	public void setJmsBacklogHealth ( HealthSettings health ) {
		this.jmsBacklogHealth = health;
	}

	private DatabaseSettings db = new DatabaseSettings() ;
	
	public class DatabaseSettings {
		private String url;
		private String driverClassName;
		private String username;

		private String password ;
		private int maxActive = 10;
		private int maxIdle = 10;
		private long idleEvictionMs = 3000 ;
		public String getUrl () {
			return url;
		}
		public void setUrl ( String url ) {
			this.url = url;
		}
		public String getDriverClassName () {
			return driverClassName;
		}
		public void setDriverClassName ( String driverClassName ) {
			this.driverClassName = driverClassName;
		}
		public String getUsername () {
			return username;
		}
		public void setUsername ( String username ) {
			this.username = username;
		}
		public int getMaxActive () {
			return maxActive;
		}
		public void setMaxActive ( int maxActive ) {
			this.maxActive = maxActive;
		}
		public int getMaxIdle () {
			return maxIdle;
		}
		public void setMaxIdle ( int maxIdle ) {
			this.maxIdle = maxIdle;
		}
		public long getIdleEvictionMs () {
			return idleEvictionMs;
		}
		public void setIdleEvictionMs ( long idleEvictionMs ) {
			this.idleEvictionMs = idleEvictionMs;
		}
		public String getPassword () {
			return password;
		}
		public void setPassword ( String password ) {
			
			if ( password.equals( "CHANGE_ME" ) ) {
				logger.error( "specified password is {}. Set the dbPass environment variable or update application.yml file", password );
				System.exit( 99 );
			}
			this.password = password;
		}
	}


	private HealthSettings jmsBacklogHealth = new HealthSettings() ;
	
	public class HealthSettings {
		 private String baseUrl ;
		 private String host ;
		 private String backlogQ ;
		 private String processedQ ;
		 private int sampleCount ;
		 private String expression ;
		public String getBaseUrl () {
			return baseUrl;
		}
		public void setBaseUrl ( String baseUrl ) {
			this.baseUrl = baseUrl;
		}
		public String getBacklogQ () {
			return backlogQ;
		}
		public void setBacklogQ ( String backlogQ ) {
			this.backlogQ = backlogQ;
		}
		public String getProcessedQ () {
			return processedQ;
		}
		public void setProcessedQ ( String processedQ ) {
			this.processedQ = processedQ;
		}
		public int getSampleCount () {
			return sampleCount;
		}
		public void setSampleCount ( int sampleCount ) {
			this.sampleCount = sampleCount;
		}
		public String getExpression () {
			return expression;
		}
		public void setExpression ( String expression ) {
			this.expression = expression;
		}
		public String getHost () {
			return host;
		}
		public void setHost ( String host ) {
			this.host = host;
		}
		
	}
	

}
