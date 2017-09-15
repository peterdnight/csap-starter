package org.sample;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

import org.csap.alerts.AlertProcessor;
import org.csap.integations.CsapPerformance;
import org.javasimon.SimonManager;
import org.javasimon.Split;
import org.sample.BootEnterpriseApplication.HealthSettings;
import org.sample.input.http.ui.rest.MsgAndDbRequests;
import org.sample.input.http.ui.windows.JmsController;
import org.sample.input.jms.SimpleJms;
import org.sample.jpa.Demo_DataAccessObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.config.JmsListenerEndpointRegistry;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import org.springframework.jmx.export.annotation.ManagedMetric;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.jmx.support.MetricType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Configuration
public class HealthMonitor {

	private Logger logger = LoggerFactory.getLogger( getClass() );
	

	ObjectMapper jacksonMapper = new ObjectMapper();

	@Inject
	private Demo_DataAccessObject testDao;

	@Inject
	private BootEnterpriseApplication myApp;
	

	@PostConstruct
	public void showConfiguration () {
		logger.info( "checking health every minute using user: {} at: {}", myApp.getDb().getUsername(), myApp.getDb().getUrl() );
	}

	private final long SECONDS = 1000;

	@Scheduled(initialDelay = 10 * SECONDS, fixedDelay = 60 * SECONDS)
	public void databaseMonitor () {
		Split monitorTimer = SimonManager.getStopwatch( "HealthMonitor.databaseMonitor" ).start();
		List<String> latestIssues = new ArrayList<>();
		try {

			long count = testDao.getCountCriteria( MsgAndDbRequests.TEST_TOKEN );
			ObjectNode result = testDao.showScheduleItemsWithFilter( MsgAndDbRequests.TEST_TOKEN, 10 );
			logger.debug( "Count: {} Records:\n {}", count, jacksonMapper.writerWithDefaultPrettyPrinter().writeValueAsString( result ) );
		} catch (Throwable e) {
			logger.error( "Failed HealthStatis on DB queries", e );
			latestIssues.add( "Exception QueryingDB: " + myApp.getDb().getUrl() + " Exception: " + e.getClass().getSimpleName() );
		}
		dbFailures = latestIssues;
		monitorTimer.stop() ;
	}
	


	@Inject
	private JmsController jmsController ;
	
	@Scheduled(initialDelay = 10 * SECONDS, fixedDelay = 60 * SECONDS)
	public void jmsProcessingMonitor () {

		HealthSettings settings= myApp.getJmsBacklogHealth() ;
		
		if (settings.getHost() == null ) {
			return;
		}

		Split monitorTimer = SimonManager.getStopwatch( "HealthMonitor.jmsProcessingMonitor" ).start();
		List<String> latestIssues = new ArrayList<>();
		try {
			
			ObjectNode result = jmsController.buildHungReport( 
				settings.getExpression(), 
				settings.getBacklogQ(), 
				settings.getProcessedQ(), 
				settings.getSampleCount(), 
				settings.getBaseUrl(),
				settings.getHost() ) ;
			
			
			logger.debug( "hung: {} Report: {}", 
				result.at( "/hungReports/0/isHung" ).asBoolean(), 
				jacksonMapper.writerWithDefaultPrettyPrinter().writeValueAsString( result ) );

			
			if ( result.at( "/hungReports/0/isHung" ).isMissingNode() ) {
				latestIssues.add( "Unable to query JMS: " +  settings.getHost() );
			} else if ( result.at( "/hungReports/0/isHung" ).asBoolean() ) {
				latestIssues.add( "Processing Q is not showing activity"  );
			}
			
		} catch (Throwable e) {
			logger.error( "Failed HealthStatis on DB queries", e );
			latestIssues.add( "Exception QueryingJms: " + " Exception: " + e.getClass().getSimpleName() );
		}
		monitorTimer.stop() ;
		setJmsFailures( latestIssues );
	}

	private volatile List<String> dbFailures = new ArrayList<>();
	private volatile List<String> jmsFailures = new ArrayList<>();

	public List<String> getDbFailures () {
		return dbFailures;
	}

	@Bean
	@ConditionalOnBean(AlertProcessor.class)
	public CsapPerformance.CustomHealth myHealth () {

		// Push any work into background thread to avoid blocking collection

		CsapPerformance.CustomHealth health = new CsapPerformance.CustomHealth() {

			@Autowired
			AlertProcessor alertProcessor;
			
			@Override
			public boolean isHealthy ( ObjectNode healthReport )
					throws Exception {
				logger.debug( "Invoking custom health" );

				getDbFailures().forEach( reason -> {
					alertProcessor.addFailure( this, healthReport, reason );
				} );
				
				getJmsFailures().forEach( reason -> {
					alertProcessor.addFailure( this, healthReport, reason );
				} );

				if ( getDbFailures().size() > 0 || getJmsFailures().size() > 0)
					return false;
				return true;
			}

			@Override
			public String getComponentName () {
				return HealthMonitor.class.getName();
			}
		};

		return health;

	}
	
	public List<String> getJmsFailures () {
		return jmsFailures;
	}

	public void setJmsFailures ( List<String> jmsFailures ) {
		this.jmsFailures = jmsFailures;
	}

	/**
	 * 
	 * JMX endpoint for collecting JMS active listenr
	 * 
	 * @author pnightin
	 *
	 */

	@Service
	@ManagedResource(objectName = "org.csap:application=sample,name=PerformanceMonitor", description = "Exports performance data to external systems")
	public class JmsHealth {
		// Jms is optionally disabled, so optional injection
		@Autowired(required = false)
		JmsListenerEndpointRegistry jmsRegistry;

		@Autowired(required = false)
		SimpleJms simpleJms;

		@ManagedMetric(category = "UTILIZATION", displayName = "Number of JMS listeners running", description = "Some time dependent value", metricType = MetricType.COUNTER)
		synchronized public int getJmsActive () {

			int count = -1; // indicates jms is disabled
			if ( simpleJms != null ) {
				try {
					count = ((DefaultMessageListenerContainer) jmsRegistry
						.getListenerContainer( SimpleJms.TEST_JMS_LISTENER_ID )).getActiveConsumerCount();
				} catch (Exception e) {
					logger.error( "Failed to get count", e );
				}
			}

			return count; // Number of messages processed
		}
	}

}
