package org.sample;

import java.util.ArrayList;
import java.util.IntSummaryStatistics;
import java.util.List;

import javax.inject.Inject;

import org.csap.CsapBootApplication;
import org.csap.alerts.AlertProcessor;
import org.csap.helpers.CsapRestTemplateFactory;
import org.csap.helpers.CsapSimpleCache;
import org.csap.helpers.JvmStatsCollector;
import org.csap.helpers.JvmStatsCollector.CollectionResults;
import org.csap.integations.CsapInformation;
import org.csap.integations.CsapPerformance;
import org.csap.integations.CsapRolesEnum;
import org.csap.integations.CsapSecurityConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;
import org.springframework.web.servlet.resource.VersionResourceResolver;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 
 * 
 * Annotations like @Async and @Cacheable are very useful. By default - they are
 * ONLY available when invoked an external Bean - not in the calling class. This
 * is due to ease of configuration using the Spring default AOP support.
 * 
 * There are complexities associated with enable AspjectJ AOP - carefully
 * evaluate whether use cases justify.
 * 
 * To enable in-class weaving, JVM needs: jvm start parameters: -javagent
 * spring-instrument-4.2.5.RELEASE.jar annotation on
 * class: @EnableLoadTimeWeaving(aspectjWeaving = AspectJWeaving.ENABLED)
 * 
 * 
 * @author pnightin
 *
 */
@CsapBootApplication
@EnableAsync
public class CsapStarterDemo extends WebMvcConfigurerAdapter {

	final Logger logger = LoggerFactory.getLogger( CsapStarterDemo.class );

	ObjectMapper jacksonMapper = new ObjectMapper();

	public static void main ( String[] args ) {

		SpringApplication.run( CsapStarterDemo.class, args );

		// CsapCommonConfiguration wires in ldap, perf monitors, ...
	}

	public enum SimonIds {

		// add as many as needed. Optionally read values
		// from limits.yml
		exceptions( "health.exceptions" ),
		nullPointer( "health.nullPointer" );

		public String id;

		private SimonIds( String simonId ) {
			this.id = simonId;
		}
	}

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
		registry
			.addResourceHandler( "/**/*.js", "/**/*.css", "/**/*.png", "/**/*.gif", "/**/*.jpg" )
			.addResourceLocations( "classpath:/static/", "classpath:/public/" )
			.setCachePeriod( ONE_YEAR_SECONDS )
			.resourceChain( true )
			.addResolver( versionResolver );

	}

	/**
	 * 
	 * CsapPerformance uses application.yml to configure simon alerts
	 * 
	 * Optionally - use this to add custom health logic (such as examining jms
	 * queue backlogs, etc.) - note csap performance APIs can be used to
	 * retrieve the data
	 * 
	 */
	@Bean
	@ConditionalOnBean ( AlertProcessor.class )
	public CsapPerformance.CustomHealth myHealth () {

		// Push any work into background thread to avoid blocking collection

		CsapPerformance.CustomHealth health = new CsapPerformance.CustomHealth() {

			@Autowired
			AlertProcessor alertProcessor;

			@Override
			public boolean isHealthy ( ObjectNode healthReport )
					throws Exception {
				logger.debug( "Invoking custom health" );

				get_healthReportItems().forEach( reason -> {

					// default implementation uses CSAP alert
					alertProcessor.addFailure( this, healthReport, reason );
				} );

				if ( get_healthReportItems().size() > 0 )
					return false;
				return true;
			}

			@Override
			public String getComponentName () {
				return CsapStarterDemo.class.getName();
			}
		};

		return health;

	}

	private volatile List<String> _healthReportItems = new ArrayList<>();

	private List<String> get_healthReportItems () {
		return _healthReportItems;
	}

	@Scheduled ( fixedRate = 30 * CsapSimpleCache.SECOND_IN_MS )
	public void myHealth_customLogic () {
		logger.debug( "Checking now" );
		List<String> latestIssues = new ArrayList<>();
		if ( false ) {
			latestIssues.add( ".....Description of issue1 found...." );
			latestIssues.add( ".....Description of issue2 found...." );
		}
		_healthReportItems = latestIssues;
	}

	// @formatter:off
	/**
	 * 
	 * Example: Retrieve and review performance data collected by CSAP, and perform custom analytics.
	 * Reference service has barebone implementation; other services such as CsAgent have extensive Application collection
	 * 
	 * CSAP collection apis are documented at: https://github.com/csap-platform/csap-core/wiki/Application-Portal
	 * - refer to /api/agent/collection
	 * 
	 */
	@Scheduled ( fixedRate = 5 * CsapSimpleCache.MINUTE_IN_MS )
	public void myHealth_reviewCollectionTrends () {
		logger.debug( "Checking now" );
		List<String> pendingHealthIssues = new ArrayList<>();
		
		if ( isRunningOnDesktop() ) {
			logger.info("Skipping resource health checks") ;
			return;
		}
		
		// apis: /agent/collection/application, /agent/collection/java, /agent/collection/os, /agent/collection/process
		String agentCollectApi = getAgentUrl( "/agent/collection/java", "/0/", 10 ) ;
		
		try {
			
			ObjectNode myPerformanceData = agentTemplate.getForObject( agentCollectApi, ObjectNode.class );
			logger.debug("{} Collected data: {}", agentCollectApi, myPerformanceData);
			
			ArrayNode activeThreadCollection = (ArrayNode) myPerformanceData.at( "/data/jvmThreadCount_" + getMyServiceId() ) ;
			logger.debug( "Last 10 intervals: {}", activeThreadCollection );
			
			// repeat the above - but transform to list for lamba operations
			ArrayList<Integer> myLast10ThreadIntervals = jacksonMapper.readValue(
				myPerformanceData.at( "/data/jvmThreadCount_" + getMyServiceId() ).traverse(),
				new TypeReference<ArrayList<Integer>>() {
				} );
			
			
			// trivial analysis - but this could be as complex as needed - and very specific to service domain
			IntSummaryStatistics myThreadStats = 
					myLast10ThreadIntervals
						.stream()
						.mapToInt( x -> x )
						.summaryStatistics() ;
			
			myLast10ThreadIntervals
				.stream()
				.forEach(  threadIntervalValue -> {
					if ( threadIntervalValue > 2 * myThreadStats.getAverage() ) {
						pendingHealthIssues.add( "High Thread Count: " + threadIntervalValue );
					}
				});
			
		} catch (Exception e) {
	
			logger.error( "Failed to get data from : {}, {}", 
				agentCollectApi, 
				CsapRestTemplateFactory.getFilteredStackTrace( e, "csap" ) 
				);
			pendingHealthIssues.add( "Failed to get performance data from: " + agentCollectApi );
		}
		
		_healthReportItems = pendingHealthIssues;
	}
	// @formatter:on
	
	private String getMyServiceId() {
		if ( isRunningOnDesktop() ) {
			return "BootReference_8291" ;
		}
		return csapInfo.getName() + "_" + csapInfo.getHttpPort();
	}

	private String getAgentUrl ( String type, String collectionInterval, int numberOfSamples ) {
		String url = "http://" + csapInfo.getHostName() + ":8011/CsAgent/api" + type
				+ "/" + getMyServiceId();

		if ( isRunningOnDesktop() ) {
			url = "http://csap-dev01.yourcompany.com:8011/CsAgent/api" + type + "/" + getMyServiceId();
		}

		url += collectionInterval + numberOfSamples;

		logger.debug( "api: {}", url );

		return url;
	}

	@Bean
	public CsapRestTemplateFactory csapRestFactory () {

		return new CsapRestTemplateFactory();
	}

	@Bean ( name = "csAgentRestTemplate" )
	public RestTemplate getAgentConnection ( CsapRestTemplateFactory factory ) {

		RestTemplate agentTemplate = factory.buildJsonTemplate( "agentApi", 6, 10, 3, 1, 10 );
		return agentTemplate;
	}

	@Autowired
	@Qualifier ( "csAgentRestTemplate" )
	RestTemplate agentTemplate;

	@Inject
	CsapInformation csapInfo;

	// configure @Scheduled thread pool;
	@Bean
	public TaskScheduler taskScheduler () {
		ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
		scheduler.setThreadNamePrefix( CsapStarterDemo.class.getSimpleName() + "@Scheduler" );
		scheduler.setPoolSize( 1 );
		return scheduler;
	}

	// configure @Async thread pool. Use named pools for workload segregation:
	// @Async("CsapAsync")

	final public static String ASYNC_EXECUTOR = "CsapAsynExecutor";

	@Bean ( ASYNC_EXECUTOR )
	public TaskExecutor taskExecutor () {
		ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
		taskExecutor.setThreadNamePrefix( CsapStarterDemo.class.getSimpleName() + "@Async" );
		taskExecutor.setMaxPoolSize( 5 );
		taskExecutor.setQueueCapacity( 100 );
		taskExecutor.afterPropertiesSet();
		return taskExecutor;
	}

	/**
	 * 
	 * Component specific security rules
	 * 
	 * @return
	 */
	@Bean
	@ConditionalOnProperty ( "csap.security.enabled" )
	public CsapSecurityConfiguration.CustomHttpSecurity mySecurityPolicy () {

		// http://blog.netgloo.com/2014/09/28/spring-boot-enable-the-csrf-check-selectively-only-for-some-requests/

		// @formatter:off
		CsapSecurityConfiguration.CustomHttpSecurity mySecurity = (httpSecurity -> {
			httpSecurity
			
				// CSRF adds complexity - refer to
				// https://docs.spring.io/spring-security/site/docs/current/reference/htmlsingle/#csrf
				// csap.security.csrf also needs to be enabled or this will be ignored
				.csrf()
					.requireCsrfProtectionMatcher( CsapSecurityConfiguration.buildRequestMatcher( "/login*" ) )
					.and()

				// do not run security against opensource jars: "/webjars/**", "/css/**" , "/js/**", "/images/**"
				.authorizeRequests()
					.antMatchers(  "/helloNoSecurity" )
					.permitAll()
				//
				//
				.antMatchers( "/testAclFailure" )
					.hasRole( "NonExistGroupToTriggerAuthFailure" )
				//
				//
				.antMatchers( "/someUrlNeedingAdmin1", "/anotherUrlNeedingAdmin" )
					.access( CsapSecurityConfiguration.hasAnyRole( CsapRolesEnum.view )
						+ " OR "
						+ CsapSecurityConfiguration.hasAnyRole( CsapRolesEnum.admin ) )
				//
				//
				.anyRequest()
					.access( CsapSecurityConfiguration.hasAnyRole( CsapRolesEnum.view ) );
		});
		// @formatter:on

		return mySecurity;

	}

	// alternate definitions for
	// return ( ( currentCollection, healthReport ) -> {
	// return true;
	// });

	// CsapPerformance.CustomHealth myCustomHealth = new
	// CsapPerformance.CustomHealth() {
	//
	// @Override
	// public boolean isHealthy ( int currentCollection, StringBuilder
	// healthReport ){
	// logger.info("Invoking custom Heal") ;
	//
	// return true;
	// }
	// };

	// return myCustomHealth ;

}
