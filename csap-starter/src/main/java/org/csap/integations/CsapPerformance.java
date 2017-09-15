package org.csap.integations;

import java.util.Arrays;
import java.util.Optional;

import org.apache.catalina.core.ApplicationContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.csap.CsapMonitor;
import org.csap.alerts.AlertInstance;
import org.csap.alerts.AlertProcessor;
import org.csap.alerts.AlertSettings;
import org.csap.alerts.MonitorMbean;
import org.javasimon.SimonManager;
import org.javasimon.Split;
import org.javasimon.console.SimonConsoleServlet;
import org.javasimon.javaee.SimonServletFilter;
import org.javasimon.jmx.SimonManagerMXBeanImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.node.ObjectNode;

@Configuration ( "CsapPerformance" )
@ConditionalOnProperty ( prefix = "csap.performance" , name = "enabled" )
@ComponentScan ( "org.csap.alerts" )
@ConfigurationProperties ( prefix = "csap.performance" )
public class CsapPerformance {

	final static Logger logger = LoggerFactory.getLogger( CsapPerformance.class );

	@Autowired
	CsapInformation csapInformation;

	@Autowired
	AlertSettings alertSettings;

	/**
	 * 
	 * =================== Performance integration
	 */

	@Bean
	public ServletRegistrationBean performanceConsole () {

		ServletRegistrationBean simonRegistration = new ServletRegistrationBean(
			new SimonConsoleServlet(), csapInformation.getSimonUrl() + "/*" );

		simonRegistration.addInitParameter( SimonConsoleServlet.URL_PREFIX_INIT_PARAMETER, csapInformation.getSimonUrl() );
		performanceTimerRegistration();
		return simonRegistration;
	}

	private String[] monitorUrls = { "/", "/security" };

	public String toString () {
		StringBuilder infoBuilder = new StringBuilder();
		infoBuilder.append( "\n === csap.performance:" );
		infoBuilder.append( "\n\t JavaSimon Mbean:  " + SIMON_MBEAN );
		infoBuilder.append( "\n\t JavaSimon Console Url: " + csapInformation.getSimonUrl() );
		infoBuilder.append( "\n\t Monitored Beans: @CsapMonitor" );
		infoBuilder.append( "\n\t Monitored urls: " );
		for ( String url : getMonitorUrls() )
			infoBuilder.append( " " + url );

		infoBuilder.append( "\n\t CsapPerformance MBean:  " + MonitorMbean.PERFORMANCE_MBEAN );
		infoBuilder.append( "\n\t Alert Settings: " + alertSettings );
		infoBuilder.append( "\n" );

		return infoBuilder.toString();
	}

	@Bean
	public FilterRegistrationBean performanceTimerRegistration () {
		logger.debug( "Collecting metrics for: {}", Arrays.asList( monitorUrls ).toString() );
		FilterRegistrationBean performanceTimerRegistration = new FilterRegistrationBean(
			new SimonServletFilter() );
		performanceTimerRegistration.addUrlPatterns( monitorUrls );

		performanceTimerRegistration.addInitParameter( SimonServletFilter.INIT_PARAM_PREFIX, "http" );
		performanceTimerRegistration.addInitParameter( SimonServletFilter.INIT_PARAM_STOPWATCH_SOURCE_PROPS,
			"includeHttpMethodName=ALWAYS" );
		performanceTimerRegistration.addInitParameter( SimonServletFilter.INIT_PARAM_REPORT_THRESHOLD_MS, "500" );

		return performanceTimerRegistration;
	}

	public static Object executeSimon ( ProceedingJoinPoint pjp, String desc )
			throws Throwable {

		String timerId = desc + pjp.getTarget().getClass().getSimpleName()
				+ "." + pjp.getSignature().getName() + "()";

		Split split = SimonManager.getStopwatch( timerId ).start();
		Object obj = pjp.proceed();
		split.stop();
		return obj;

	}

	public static Object incrementCsapMonitor ( ProceedingJoinPoint pjp )
			throws Throwable {

		Class c = pjp.getTarget().getClass();
		String prefix = c.getSimpleName();
		if ( c.isAnnotationPresent( CsapMonitor.class ) ) {
			CsapMonitor monitor = (CsapMonitor) c.getAnnotation( CsapMonitor.class );
			String specified = monitor.prefix();
			logger.debug( "specified: {}", specified );
			if ( specified.equals( CsapMonitor.CLASS_NAME_WITH_PACKAGE ) ) {
				prefix = c.getName();
			} else if ( !specified.equals( CsapMonitor.CLASS_NAME) ) {
				prefix = specified;
			}

		}
		String timerId = prefix + "." + pjp.getSignature().getName() + "()";

		Split split = SimonManager.getStopwatch( timerId ).start();
		Object obj = pjp.proceed();
		split.stop();
		return obj;

	}

	// ================= JAVA SIMON registration

	public final static String SIMON_MBEAN = "org.csap:application=CsapPerformance,name=SimonManager";

	@Component
	@ManagedResource ( objectName = SIMON_MBEAN , description = "Simon Mbeans" )
	public static class JavaSimonMbean extends SimonManagerMXBeanImpl {
		private static final long serialVersionUID = 1L;

		public JavaSimonMbean( ) {
			super( SimonManager.manager() );
		}

	}

	@Configuration
	@Order ( Ordered.LOWEST_PRECEDENCE )
	@Aspect
	public static class Default_Collection {

		public Default_Collection( ) {
			logger.debug( "\n\n\n Collecting rest controller and sprin" );
		}

		// @Pointcut("within(@org.springframework.stereotype.Controller *)")
		@Pointcut ( "within(@org.csap.CsapMonitor *)" )
		private void mvcPC () {
		};

		// @Pointcut("within(@org.springframework.web.bind.annotation.RestController
		// *)")
		// private void restPC() {
		// };

		@Around ( "mvcPC()" )
		public Object mvcAdvice ( ProceedingJoinPoint pjp )
				throws Throwable {

			Object obj = incrementCsapMonitor( pjp );

			return obj;
		}
	}

	public String[] getMonitorUrls () {
		return monitorUrls;
	}

	public void setMonitorUrls ( String[] monitorUrls ) {
		this.monitorUrls = monitorUrls;
	}

	/**
	 *
	 * Enable custom http access rules to be implemented. All instance of
	 * CustomHttpSecurity will be invoked
	 *
	 */
	public interface CustomHealth {

		public boolean isHealthy ( ObjectNode healthReport )
				throws Exception;

		public String getComponentName ();

	}

}
