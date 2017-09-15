package org.csap.alerts;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;

import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.csap.alerts.AlertInstance.AlertItem;
import org.csap.alerts.MonitorMbean.Report;
import org.csap.helpers.CsapSimpleCache;
import org.csap.integations.CsapInformation;
import org.csap.integations.CsapPerformance;
import org.csap.integations.CsapPerformance.CustomHealth;
import org.javasimon.Counter;
import org.javasimon.CounterSample;
import org.javasimon.Sample;
import org.javasimon.Simon;
import org.javasimon.SimonManager;
import org.javasimon.Split;
import org.javasimon.Stopwatch;
import org.javasimon.StopwatchSample;
import org.javasimon.utils.SimonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring4.SpringTemplateEngine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
public class AlertProcessor {

	final static Logger logger = LoggerFactory.getLogger( AlertProcessor.class );

	@Autowired
	CsapInformation csapInformation;

	@PreDestroy
	public void cleanup () {

		if ( scheduledExecutorService != null ) {
			logger.info( "\n\n ********* Cleaning up jobs \n\n" );
			scheduledExecutorService.shutdownNow();

		}
	}

	@Autowired
	AlertSettings alertConfig;

	private AlertInstance undefinedAlert = new AlertInstance( CsapGlobalId.UNDEFINED_ALERTS.id, 0, null );
	private AlertInstance failRunAlert = new AlertInstance( CsapGlobalId.HEALTH_REPORT_FAIL.id, 0, null );

	private CsapSimpleCache emailTimer;
	private CsapSimpleCache throttleTimer;

	@PostConstruct
	public void initialize () {
		logger.debug( "configuring email and alert throttles" );

		emailTimer = CsapSimpleCache.builder(
			alertConfig.getNotify().getFrequency(),
			CsapSimpleCache.parseTimeUnit(
				alertConfig.getNotify().getTimeUnit(),
				TimeUnit.HOURS ),
			AlertSettings.class,
			"Email Notifications" );

		throttleTimer = CsapSimpleCache.builder(
			alertConfig.getThrottle().getFrequency(),
			CsapSimpleCache.parseTimeUnit(
				alertConfig.getThrottle().getTimeUnit(),
				TimeUnit.HOURS ),
			AlertSettings.class,
			"Alert Throttle" );

		initializeHealthJobs();

		// add health report job
		scheduledExecutorService
			.scheduleAtFixedRate( this::buildHealthReport,
				alertConfig.getReport().getIntervalSeconds(),
				alertConfig.getReport().getIntervalSeconds(),
				TimeUnit.SECONDS );

		// add alert jobs - each configured alert is triggered based on
		// configuration
		for ( AlertInstance alertInstance : alertConfig.getLimits() ) {
			logger.debug( "Scheduling job every {} seconds : {}", alertInstance.getCollectionSeconds(), alertInstance );

			scheduledExecutorService
				.scheduleAtFixedRate(
					() -> collectAlertSample( alertInstance ),
					alertInstance.getCollectionSeconds(),
					alertInstance.getCollectionSeconds(),
					TimeUnit.SECONDS );
		}

		// add context alets

		alertConfig.getAllAlertInstances().add( getUndefinedAlert() );
		alertConfig.getAllAlertInstances().add( getFailRunAlert() );
	}

	public void collectAlertSample ( AlertInstance alert ) {

		try {
			if ( alertConfig.isDebug() ) {
				logger.info( "Collection sample: {} ", alert.getId() );
			}
			alert.setPendingFirstCollection( false );
			Simon s = SimonManager.getSimon( alert.getId() );
			if ( s == null ) {
				// no instances yet
				return;
			}
			// Store sample for reports
			alert.setLastCollectedSample( s.sampleIncrement( alert.getSampleName() ) );
		} catch (Exception e) {
			logger.error( "Failed to collect", e );
		}

	}

	private ScheduledExecutorService scheduledExecutorService = null;

	AtomicInteger counterCollections = new AtomicInteger();

	protected void initializeHealthJobs () {

		// Initialize healthReport
		healthReport = jacksonMapper.createObjectNode();
		healthReport.put( Report.collectionCount.json, counterCollections.get() );
		healthReport.put( Report.healthy.json, true );
		healthReport.put( "note", "Pending First Run" );

		String scheduleName = AlertProcessor.class.getSimpleName() + "_" + alertConfig.getReport().getIntervalSeconds();
		BasicThreadFactory schedFactory = new BasicThreadFactory.Builder()

			.namingPattern( scheduleName + "-%d" )
			.daemon( true )
			.priority( Thread.NORM_PRIORITY )
			.build();
		// Single collection thread
		scheduledExecutorService = Executors
			.newScheduledThreadPool( 1, schedFactory );

		logger.info( "Adding Job: {}", scheduleName );
	}

	@Bean
	public JavaMailSender csapMailSender () {
		JavaMailSenderImpl sender = new JavaMailSenderImpl();

		Properties properties = new Properties();
		// properties.put("mail.smtp.auth", auth);
		properties.put( "mail.smtp.timeout", alertConfig.getNotify().getEmailTimeOutMs() );
		properties.put( "mail.smtp.connectiontimeout", alertConfig.getNotify().getEmailTimeOutMs() );
		// properties.put("mail.smtp.starttls.enable", starttls);

		sender.setJavaMailProperties( properties );
		sender.setHost( alertConfig.getNotify().getEmailHost() );
		sender.setPort( alertConfig.getNotify().getEmailPort() );
		// sender.setProtocol(protocol);
		// sender.setUsername(username);
		// sender.setPassword(password);

		return sender;
	}

	@Inject
	SpringTemplateEngine springTemplateEngine;

	public static String EMAIL_DISABLED = "Email notifications disabled";

	private ObjectMapper jacksonMapper = new ObjectMapper();

	private ArrayNode alertHistory = jacksonMapper.createArrayNode();
	private ArrayNode alertsThrottled = jacksonMapper.createArrayNode();

	/**
	 * Called every 30 seconds: if healthy - then no email
	 * 
	 * @param healthReport
	 */
	public void addReport ( ObjectNode healthReport ) {

		logger.debug( "backlog Size: {} ", alertsForEmail.size() );
		while (alertsForEmail.size() > alertConfig.getNotify().getEmailMaxAlerts()) {
			alertsForEmail.remove( 0 );
		}

		if ( alertConfig.isDebug() && alertHistory.size() > alertConfig.getRememberCount() ) {
			logger.info( "Current alert count: {} is larger then configured: {} - oldest items are being purged",
				alertHistory.size(), alertConfig.getRememberCount() );
		}
		while (alertHistory.size() > alertConfig.getRememberCount()) {
			alertHistory.remove( 0 );
		}

		try {
			ArrayList<ObjectNode> activeAlerts = jacksonMapper.readValue( healthReport.get( Report.limitsExceeded.json ).traverse(),
				new TypeReference<ArrayList<ObjectNode>>() {
				} );

			// String foundTime = LocalDateTime.now().format(
			// DateTimeFormatter.ofPattern( "h:mm:ss a" ) ) ;
			long now = System.currentTimeMillis();
			String foundTime = LocalDateTime.now().format( DateTimeFormatter.ofPattern( "HH:mm:ss , MMM d" ) );
			activeAlerts.forEach( item -> {
				item.put( AlertInstance.AlertItem.formatedTime.json, foundTime );
				item.put( AlertInstance.AlertItem.timestamp.json, now );
				item.put( AlertInstance.AlertItem.count.json, 1 );
			} );

			// increment counters and dates - or add
			activeAlerts.forEach( activeAlert -> {
				int matchCount = 0;
				int lastMatchIndex = 0;
				int index = 0;
				for ( JsonNode throttledEvent : getAlertsThrottled() ) {
					if ( AlertInstance.AlertItem.isSameId( activeAlert, throttledEvent ) ) {
						matchCount++;
						lastMatchIndex = index;
					}
					index++;
				}
				if ( matchCount >= alertConfig.getThrottle().getCount() ) {
					// update the count
					int oldCount = getAlertsThrottled()
						.get( lastMatchIndex )
						.get( AlertInstance.AlertItem.count.json )
						.asInt();
					activeAlert.put( AlertInstance.AlertItem.count.json, 1 + oldCount );

					// remove the oldest
					getAlertsThrottled().remove( lastMatchIndex );

				}
				// add the newest
				getAlertsThrottled().add( activeAlert );

			} );

			if ( getThrottleTimer().isExpired() ) {
				// Always add in memory browsing
				alertHistory.addAll( getAlertsThrottled() );
				getThrottleTimer().reset();
				getAlertsThrottled().removeAll();
			}

			// Email support
			if ( alertConfig.getNotify().getEmails() == null ) {
				logger.debug( "Email notifications are disabled." );
			} else {
				sendAlertEmail( healthReport, activeAlerts );
			}

		} catch (Exception e) {

			logger.error( "Failed to send message", e );
		}

		return;
	}

	private List<JsonNode> alertsForEmail = new ArrayList<>();

	private void sendAlertEmail ( ObjectNode healthReport, ArrayList<ObjectNode> limits ) {
		if ( csapMailSender() == null ) {
			logger.warn( "Java Mail is disabled - update spring.mail in application.yml" );
			return;
		}
		alertsForEmail.addAll( limits );

		if ( alertsForEmail.size() == 0 ) {
			logger.debug( "No items in backlog" );
			return;
		}

		if ( !getEmailTimer().isExpired() ) {
			int collectionCount = healthReport.get( Report.collectionCount.json ).asInt();

			if ( collectionCount % 120 == 0 ) {
				// do not overload with messages - only output 1 per hour
				logger.info( "Notification not sent because interval is not met: {}. Last notification was sent: {}",
					getEmailTimer().getMaxAgeFormatted(),
					getEmailTimer().getCurrentAgeFormatted() );
			} else {

				logger.debug( "Notification not sent because interval is not met: {}. Last notification was sent: {}",
					getEmailTimer().getMaxAgeFormatted(),
					getEmailTimer().getCurrentAgeFormatted() );
			}

			return;
		}

		// Set up variables for template processing
		Context context = new Context();
		context.setVariable( "appUrl", csapInformation.getLoadBalancerUrl() );
		context.setVariable( "healthUrl", csapInformation.getFullHealthUrl() );
		context.setVariable( "life", csapInformation.getLifecycle() );
		context.setVariable( "service", csapInformation.getName() );
		context.setVariable( "host", csapInformation.getHostName() );
		context.setVariable( "dateTime", LocalDateTime.now().format( DateTimeFormatter.ofPattern( "h:mm:ss a, MMMM d" ) ) );

		context.setVariable( "limits", alertsForEmail );
		String testBody = springTemplateEngine.process( "csap/alerts/email", context );

		logger.info( "{} Type {} : \n\t to: {}\n\t message: {}",
			csapInformation.getName(), "Health", alertConfig.getNotify(),
			alertsForEmail );

		csapMailSender().send( mimeMessage -> {
			MimeMessageHelper messageHelper = new MimeMessageHelper( mimeMessage, true, "UTF-8" );
			messageHelper.setTo( alertConfig.getNotify().getEmails() );
			// messageHelper.setCc( CsapUser.currentUsersEmailAddress()
			// );
			messageHelper.setFrom( "csap@yourCompany.com" );
			messageHelper.setSubject( "CSAP Notification: " + csapInformation.getName() + " - " + csapInformation.getLifecycle() );
			messageHelper.setText( testBody, true );

			messageHelper.addAttachment( "report.json",
				new ByteArrayResource( jacksonMapper
					.writerWithDefaultPrettyPrinter()
					.writeValueAsString( healthReport )
					.getBytes() ) );
		} );

		getEmailTimer().reset();
		alertsForEmail.clear();
	}

	private ArrayNode getAlertHistory () {
		return alertHistory;
	}

	public ArrayNode getAllAlerts () {
		ArrayNode all = jacksonMapper.createArrayNode();
		all.addAll( getAlertHistory() );
		all.addAll( getAlertsThrottled() );

		return all;
	}

	public CsapSimpleCache getEmailTimer () {
		return emailTimer;
	}

	public CsapSimpleCache getThrottleTimer () {
		return throttleTimer;
	}

	public ArrayNode getAlertsThrottled () {
		return alertsThrottled;
	}

	volatile ObjectNode healthReport = null;

	SimpleDateFormat timeDayFormat = new SimpleDateFormat( "HH:mm:ss , MMM d" );

	private void buildHealthReport () {
		ObjectNode latestReport = jacksonMapper.createObjectNode();
		latestReport.put( Report.collectionCount.json, counterCollections.incrementAndGet() );
		latestReport.put( Report.lastCollected.json, timeDayFormat.format( new Date() ) );
		latestReport.put( Report.healthy.json, true );
		latestReport.putArray( Report.undefined.json );
		latestReport.putArray( Report.pending.json );
		latestReport.putArray( Report.limitsExceeded.json );

		boolean isHealthy = false;

		try {
			Split collectionTimer = SimonManager.getStopwatch( CsapGlobalId.HEALTH_REPORT.id ).start();

			// 1.n counters and times will determine health
			checkConfiguredLimits( latestReport );

			collectionTimer.stop();

			isHealthy = latestReport.get( Report.healthy.json ).asBoolean();
		} catch (Exception e) {
			addFailure( latestReport, failRunAlert, e.getClass().getSimpleName(), 1, 0, false );
			logger.error( "Failed running health report", e );
		}

		if ( isHealthy ) {
			SimonManager.getCounter( CsapGlobalId.HEALTH_REPORT_PASS.id ).increase();
			if ( alertConfig.isDebug() ) {
				logger.info( "\n *** HealthReport: \n {}", printReport( latestReport ) );
			}
		} else {
			SimonManager.getCounter( CsapGlobalId.HEALTH_REPORT_FAIL.id ).increase();
			logger.warn( "\n *** HealthReport: \n {}", printReport( latestReport ) );
		}

		addReport( latestReport );
		healthReport = latestReport;
		return;
	}

	public String printReport ( ObjectNode report ) {
		try {
			return jacksonMapper.writerWithDefaultPrettyPrinter().writeValueAsString( report );
		} catch (JsonProcessingException e) {
			logger.warn( "Failed to parse", e );
			return "Failed to parse report";
		}
	}

	private void checkConfiguredLimits ( ObjectNode healthReport ) {

		// stopWatches
		for ( AlertInstance alert : alertConfig.getLimits() ) {
			logger.debug( "Checking: {}", alert );

			if ( alert.isPendingFirstCollection() ) {

				// first collection not done yet
				ArrayNode pending = (ArrayNode) healthReport.get( Report.pending.json );
				pending.add( alert.getId() );
			}

			Simon latestSimon = SimonManager.getSimon( alert.getId() );
			if ( latestSimon == null ) {
				if ( !alert.isPendingFirstCollection() && !alert.isIgnoreNull() ) {
					logger.debug( "Did not find: {}", alert.getId() );
					ArrayNode missingAlert = (ArrayNode) healthReport.get( Report.undefined.json );
					missingAlert.add( alert.getId() );

					addFailure( healthReport, undefinedAlert,
						alert.getId(), "Verify alert id or add ignore null to configuration" );

					SimonManager.getCounter( CsapGlobalId.UNDEFINED_ALERTS.id ).increase();
				}
			} else {
				if ( latestSimon instanceof Stopwatch ) {
					isStopWatchHeathy( healthReport, alert );
				} else if ( latestSimon instanceof Counter ) {
					isCounterHeathy( healthReport, alert );
				}
			}

		}

		for ( AlertInstance alert : alertConfig.getAllAlertInstances() ) {

			if ( alert.getCustomHealth() != null ) {
				String alertComponentName = alert.getCustomHealth().getComponentName();
				logger.debug( "Invoking Custom Health API for: {}", alertComponentName, alert.getCustomHealth().getClass().getName() );
				try {
					boolean isHealthy = alert.getCustomHealth().isHealthy( healthReport );
					if ( !isHealthy ) {
						SimonManager.getCounter( alert.getId() ).increase();
					} else {
						SimonManager.getCounter( alertComponentName + ".passed" ).increase();
					}

				} catch (Exception e) {
					addFailure( healthReport, alert, e.getClass().getSimpleName(), 1, 0, false );
					logger.error( "Failed to execute custom health	: {}", alertComponentName, e );
				}
			}
			;

		}

		return;
	}

	private void isCounterHeathy ( ObjectNode healthReport, AlertInstance alertInstance ) {
		logger.debug( "Stopwatch: {}", alertInstance.getId() );

		boolean errorsFound = false;
		// check the last collection interval
		if ( alertInstance.getLastCollectedSample() != null ) {
			CounterSample sampleFromLastInterval = (CounterSample) alertInstance.getLastCollectedSample();

			if ( sampleFromLastInterval.getCounter() < alertInstance.getOccurencesMin() ) {
				addFailure( healthReport, alertInstance,
					"Occurences - Min", sampleFromLastInterval.getCounter(), alertInstance.getOccurencesMin(), false );

			}

			if ( sampleFromLastInterval.getCounter() > alertInstance.getOccurencesMax() ) {
				errorsFound = true;
				addFailure( healthReport, alertInstance,
					"Occurences - Max", sampleFromLastInterval.getCounter(), alertInstance.getOccurencesMax(), false );

			}
		}

		// Also check latest samples for MAX - will report earlier.
		// -- do not double report errors
		if ( !errorsFound ) {
			CounterSample sampleFromCurrentInterval = SimonManager.getCounter(
				alertInstance.getId() )
				.sampleIncrementNoReset( alertInstance.getSampleName() );

			if ( sampleFromCurrentInterval.getCounter() > alertInstance.getOccurencesMax() ) {

				addFailure( healthReport, alertInstance,
					"Occurences - Max", sampleFromCurrentInterval.getCounter(),
					alertInstance.getOccurencesMax(), false );

			}
		}
	}

	private void isStopWatchHeathy ( ObjectNode healthReport, AlertInstance alertInstance ) {
		logger.debug( "Stopwatch: {}", alertInstance.getId() );

		boolean errorsFound = false;

		// check the last collection interval
		if ( alertInstance.getLastCollectedSample() != null ) {
			StopwatchSample sample = (StopwatchSample) alertInstance.getLastCollectedSample();

			if ( sample.getCounter() < alertInstance.getOccurencesMin() ) {

				addFailure( healthReport, alertInstance,
					"Occurences - Min", sample.getCounter(), alertInstance.getOccurencesMin(), false );

			}

			if ( sample.getCounter() > alertInstance.getOccurencesMax() ) {
				errorsFound = true;
				addFailure( healthReport, alertInstance,
					"Occurences - Max", sample.getCounter(), alertInstance.getOccurencesMax(), false );

			}

			if ( sample.getMean() > alertInstance.getMeanTimeNano() ) {

				addFailure( healthReport, alertInstance,
					"Time - Mean", Math.round( sample.getMean() ), alertInstance.getMeanTimeNano(), true );
			}

			if ( sample.getMax() > alertInstance.getMaxTimeNano() ) {

				errorsFound = true;
				addFailure( healthReport, alertInstance,
					"Time - Max", sample.getMax(), alertInstance.getMaxTimeNano(), true );

			}
		}

		// Also check latest samples for MAX - will report in next health
		// report, versus wating for completion of interval
		// -- do not double report errors
		if ( !errorsFound ) {
			StopwatchSample sampleFromCurrentInterval = SimonManager.getStopwatch(
				alertInstance.getId() )
				.sampleIncrementNoReset( alertInstance.getSampleName() );

			if ( sampleFromCurrentInterval.getCounter() > alertInstance.getOccurencesMax() ) {

				addFailure( healthReport, alertInstance,
					"Occurences - Max", sampleFromCurrentInterval.getCounter(),
					alertInstance.getOccurencesMax(), false );

			}

			if ( sampleFromCurrentInterval.getMax() > alertInstance.getMaxTimeNano() ) {

				addFailure( healthReport, alertInstance,
					"Time - Max", sampleFromCurrentInterval.getMax(), alertInstance.getMaxTimeNano(), true );

			}
		}
		return;
	}

	public void addFailure ( CustomHealth health, ObjectNode healthReport,  String description ) {

		Optional<AlertInstance> instance = alertConfig.findAlertForComponent( health.getComponentName() );

		if ( instance.isPresent() ) {
			addFailure( healthReport, instance.get(), "CustomHealth", description );
		} else {
			logger.error( "Failed to locate component: ", health.getComponentName() );
		}

	}

	private void addFailure (
								ObjectNode healthReport, AlertInstance alertInstance, String type, long collected, long limit,
								boolean isTime ) {

		addFailure( healthReport, alertInstance, type, null, collected, limit, isTime );
	}

	private void addFailure ( ObjectNode healthReport, AlertInstance alertInstance, String type, String description ) {
		addFailure( healthReport, alertInstance, type, description, -1, -1, false );

	}

	private void addFailure (
								ObjectNode healthReport, AlertInstance alertInstance, String type,
								String description, long collected, long limit,
								boolean isTime ) {
		// report.append( "\n Category: " + id
		// + " " + type + ": " + SimonUtils.presentNanoTime( collected )
		// + " Limit: " + SimonUtils.presentNanoTime( limit ) );

		if ( alertInstance.isEnabled() ) {
			healthReport.put( Report.healthy.json, false );
		} else {
			logger.debug( "Alert has been suppressed in black box: {} ", alertInstance.getId() );
		}
		ArrayNode limitsExceeded = (ArrayNode) healthReport.get( "limitsExceeded" );
		ObjectNode item = limitsExceeded.addObject();
		item.put( AlertInstance.AlertItem.id.json, alertInstance.getId() );
		item.put( AlertInstance.AlertItem.type.json, type );
		if ( description != null ) {
			item.put( AlertInstance.AlertItem.description.json, description );
		} else {
			item.put( AlertInstance.AlertItem.collected.json, collected );
			item.put( AlertInstance.AlertItem.limit.json, limit );
			if ( isTime ) {
				item.put( AlertInstance.AlertItem.description.json, " Collected: " + SimonUtils.presentNanoTime( collected ) +
						", Limit: " + SimonUtils.presentNanoTime( limit ) );
			} else {
				item.put( AlertInstance.AlertItem.description.json, " Collected: " + collected +
						", Limit: " + limit );
			}
		}
	}

	public ObjectNode getHealthReport () {
		return healthReport;
	}

	public void setHealthReport ( ObjectNode healthReport ) {
		this.healthReport = healthReport;
	}

	public AlertInstance getUndefinedAlert () {
		return undefinedAlert;
	}

	public AlertInstance getFailRunAlert () {
		return failRunAlert;
	}

}
