//package org.sample.input.jmx;
//
//import javax.inject.Inject;
//
//import org.apache.commons.logging.Log;
//import org.apache.commons.logging.LogFactory;
//import org.sample.input.http.ui.rest.MsgAndDbRequests;
//import org.sample.input.jms.SimpleJms;
//import org.sample.jpa.Demo_DataAccessObject;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.jms.config.JmsListenerEndpointRegistry;
//import org.springframework.jms.listener.DefaultMessageListenerContainer;
//import org.springframework.jmx.export.annotation.ManagedMetric;
//import org.springframework.jmx.export.annotation.ManagedResource;
//import org.springframework.jmx.support.MetricType;
//import org.springframework.stereotype.Service;
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.fasterxml.jackson.databind.node.ObjectNode;
//
///**
// * 
// */
//@Service
//@ManagedResource(objectName = "org.csap:application=sample,name=PerformanceMonitor", description = "Exports performance data to external systems")
//public class PerformanceMonitor {
//
//	protected final Log logger = LogFactory.getLog( getClass() );
//
//	ObjectMapper jacksonMapper = new ObjectMapper();
//
//	@Inject
//	private Demo_DataAccessObject testDao;
//
//	/**
//	 * 
//	 * HealthChecks will invoke this method at predefined intervals
//	 * 
//	 * @return
//	 */
//	@ManagedMetric(category = "PERFORMANCE ", displayName = "HealthStatus", description = "Hits DB and does a record count, Availability indicated by  true or false", metricType = MetricType.GAUGE)
//	public boolean getHealthStatus() {
//		// TODO Eng teams run logic checks; return false in the event of
//		// anything
//		// requiring operational intervention
//
//		try {
//			// Do not take very long. Collect in a background thread if longer
//			// then 30ms
//			// Adding random to make graphs look exciting
//			// Thread.sleep(10 + javaRandom.nextInt(20));
//			long count = testDao.getCountCriteria(MsgAndDbRequests.TEST_TOKEN );
//			ObjectNode result = testDao.showScheduleItemsWithFilter(MsgAndDbRequests.TEST_TOKEN, 10 );
//			// logger.debug("HealthCheck invoked, count: " + count);
//			if ( logger.isDebugEnabled() )
//				logger.debug( "Count: " + count + " Records: "
//						+ jacksonMapper.writerWithDefaultPrettyPrinter().writeValueAsString( result ) );
//		} catch (Throwable e) {
//			logger.error( "Failed HealthStatis on DB queries", e );
//			return false;
//		}
//		return true;
//	}
//
//	/**
//	 * 
//	 * Completely dependent on configuration - jms threads can be fully elastic
//	 * based on load, or ramp and hold. For Factory Sample - it is fully
//	 * dynamic.
//	 * 
//	 */
//
//	// Jms is optionally disabled, so optional injection
//	@Autowired(required = false)
//	JmsListenerEndpointRegistry jmsRegistry;
//	
//	@Autowired(required = false)
//	SimpleJms simpleJms;
//
//	@ManagedMetric(category = "UTILIZATION", displayName = "Number of JMS listeners running", description = "Some time dependent value", metricType = MetricType.COUNTER)
//	synchronized public int getJmsActive() {
//
//		int count = -1; // indicates jms is disabled
//		if ( simpleJms != null ) {
//			try {
//				count = ((DefaultMessageListenerContainer) jmsRegistry
//						.getListenerContainer( SimpleJms.TEST_JMS_LISTENER_ID )).getActiveConsumerCount();
//			} catch (Exception e) {
//				logger.error( "Failed to get count", e );
//			}
//		}
//
//		return count; // Number of messages processed
//	}
//
//}
