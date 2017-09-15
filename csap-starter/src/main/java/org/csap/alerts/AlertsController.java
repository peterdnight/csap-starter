package org.csap.alerts;

import java.security.Principal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.csap.docs.CsapDoc;
import org.csap.integations.CsapSecurityConfiguration;
import org.csap.integations.CsapPerformance.CustomHealth;
import org.javasimon.Counter;
import org.javasimon.CounterSample;
import org.javasimon.Simon;
import org.javasimon.SimonManager;
import org.javasimon.Stopwatch;
import org.javasimon.StopwatchSample;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Controller
@ConfigurationProperties(prefix = "csap")
@RequestMapping("${csap.baseContext:/csap}")
@CsapDoc(title = "CSAP Performance Alerts", type = CsapDoc.OTHER, notes = "Provides both dashboard and rest API for alerts")
public class AlertsController {

	final Logger logger = LoggerFactory.getLogger( getClass() );


	@Autowired
	AlertProcessor alertProcessor;

	@Autowired
	AlertSettings alertSettings;

	public final static String HEALTH_URL = "/health";

	@CsapDoc(notes = "Health Dashboard for viewing alerts", baseUrl = "/csap")
	@GetMapping(HEALTH_URL)
	public String healthDashboard ( ModelMap springViewModel ) {

		HashMap<String, String> settings = new HashMap<>();
		settings.put( "Health Report Interval", alertSettings.getReport().getIntervalSeconds() + " seconds");
		settings.put( "Maximum items to store", alertSettings.getRememberCount() + "" );
		settings.put( "Email Notifications", alertSettings.getNotify().toString() );
		settings.put( "Alert Throttles", alertSettings.getThrottle().toString() );

		springViewModel.addAttribute( "csapPageLabel", "CSAP Health" );
		springViewModel.addAttribute( "settings", settings );
		springViewModel.addAttribute( "definitions", alertSettings.getAllAlertDefinitions() );
		
		springViewModel.addAttribute( "customCollect", alertSettings.getReport().getFrequency() + " " + alertSettings.getReport().getTimeUnit() );
		springViewModel.addAttribute( "maxBacklog", alertSettings.getRememberCount() );
		return "csap/alerts/health";
	}

	private ObjectMapper jacksonMapper = new ObjectMapper();

	@CsapDoc(notes = "Get metric details", baseUrl = "/csap")
	@GetMapping(value = "/metric", produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public ObjectNode metric (
								@RequestParam("name") String name,
								@RequestParam(value = "sampleName", required = false, defaultValue = SAMPLE_UI) String sampleName ) {

		logger.debug( "name: {} sample: {} ", name, sampleName );
		ObjectNode results = jacksonMapper.createObjectNode();

		Simon s = SimonManager.getSimon( name );
		results.put( "name", name );
		if ( s instanceof Stopwatch ) {
			StopwatchSample sample = ((Stopwatch) s).sampleIncrementNoReset( sampleName );
			results.put( "firstUsage", getFormatedTime( sample.getFirstUsage() ) );
			results.put( "lastUsage", getFormatedTime( sample.getLastUsage() ) );
			results.put( "maxTimeStamp", getFormatedTime( sample.getMaxTimestamp() ) );
			results.put( "details", sample.toString() );

		} else if ( s instanceof Counter ) {
			CounterSample sample = ((Counter) s).sampleIncrementNoReset( sampleName );
			results.put( "firstUsage", getFormatedTime( sample.getFirstUsage() ) );
			results.put( "lastUsage", getFormatedTime( sample.getLastUsage() ) );
			results.put( "maxTimeStamp", getFormatedTime( sample.getMaxTimestamp() ) );
			results.put( "details", sample.toString() );
		}
		;

		return results;

	}

	
	@CsapDoc(notes = "Enable/disable the meter from ui", baseUrl = "/csap")
	@GetMapping(value = "/toggleMeter", produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public ObjectNode toggleMeter ( 
	                                @RequestParam("id") String id, 
	                                @RequestParam("enabled") boolean isEnabled, 
	                                Principal user) {

		ObjectNode results = jacksonMapper.createObjectNode();

		String userid = "SecurityDisabled" ;
		if ( user != null ) {
			userid =  user.getName() ;
		}
		logger.info( "User: {}  setting: {} to: {}" , userid, id, isEnabled );
		results.put( "id", id );
		results.put( "enabled", isEnabled );
		results.put( "result", alertSettings.updateLimitEnabled( id, isEnabled, userid ) );

		return results;

	}
	
	@CsapDoc(notes = "Clear the data", baseUrl = "/csap")
	@GetMapping(value = "/clearMetrics", produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public ObjectNode clearMetrics (
										@RequestParam(value = "unit", required = false, defaultValue = "MILLISECONDS") String units,
										@RequestParam(value = "filters", required = false, defaultValue = "") String filters ) {

		ObjectNode results = jacksonMapper.createObjectNode();

		int numStops = 0;
		int numCounters = 0;

		for ( String name : SimonManager.getSimonNames() ) {

			Simon s = SimonManager.getSimon( name );
			if ( s instanceof Stopwatch ) {
				numStops++;
				StopwatchSample sample = ((Stopwatch) s).sampleIncrement( SAMPLE_UI );

			} else if ( s instanceof Counter ) {
				numCounters++;
				CounterSample sample = ((Counter) s).sampleIncrement( SAMPLE_UI );
			}

		}
		;

		results.put( "numStops", numStops );
		results.put( "numCounters", numCounters );

		return results;

	}

	public final String SAMPLE_UI = "csapUI";

	@CsapDoc(notes = "Get the metrics", baseUrl = "/csap")
	@GetMapping(value = "/metrics", produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public ObjectNode metrics (
								@RequestParam(value = "sampleName", required = false, defaultValue = SAMPLE_UI) String sampleName,
								@RequestParam(value = "unit", required = false, defaultValue = "MILLISECONDS") String units,
								@RequestParam(value = "filters", required = false, defaultValue = "") String filters ) {

		logger.debug( "sampleName: {}, units: {}", sampleName, units );
		ObjectNode results = jacksonMapper.createObjectNode();

		results.set( "healthReport", alertProcessor.getHealthReport() );

		ArrayNode rows = results.putArray( "rows" );

		SimonManager.getSimonNames().forEach( name -> {

			Simon s = SimonManager.getSimon( name );
			if ( s instanceof Stopwatch ) {
				StopwatchSample sample = ((Stopwatch) s).sampleIncrementNoReset( sampleName );

				ObjectNode columns = rows.addObject();
				columns.put( "name", name );
				ArrayNode data = columns.putArray( "data" );

				data.add( sample.getCounter() );
				data.add( TimeUnit.NANOSECONDS.toMillis( Math.round( sample.getMean() ) ) );
				data.add( TimeUnit.NANOSECONDS.toMillis( sample.getMin() ) );
				data.add( TimeUnit.NANOSECONDS.toMillis( sample.getMax() ) );
				data.add( TimeUnit.NANOSECONDS.toMillis( sample.getTotal() ) );

			} else if ( s instanceof Counter ) {

				CounterSample sample = ((Counter) s).sampleIncrementNoReset( sampleName );
				ObjectNode columns = rows.addObject();
				columns.put( "name", name );
				ArrayNode data = columns.putArray( "data" );
				data.add( sample.getCounter() );
			}

		} );

		return results;
	}

	@CsapDoc(notes = "Health data showing alerts. Default hours is 4 - and testing is 0", baseUrl = "/csap")
	@GetMapping(value = "/report", produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public ObjectNode report (
								@RequestParam(value = "hours", required = false, defaultValue = "4") int hours,
								@RequestParam(value = "testCount", required = false, defaultValue = "0") int testCount ) {
		ObjectNode results = jacksonMapper.createObjectNode();

		ArrayNode alertsTriggered;

		if ( testCount == 0 ) {
			alertsTriggered = alertProcessor.getAllAlerts();
		} else {
			results.put( "testCount", testCount );
			alertsTriggered = jacksonMapper.createArrayNode();
			Random rg = new Random();

			for ( int i = 0; i < testCount; i++ ) {
				ObjectNode t = alertsTriggered.addObject();
				long now = System.currentTimeMillis();
				long itemTimeGenerated = now - rg.nextInt( (int) TimeUnit.DAYS.toMillis( 1 ) );
				t.put( "ts", itemTimeGenerated );

				// String foundTime = LocalDateTime.now().format(
				// DateTimeFormatter.ofPattern( "HH:mm:ss , MMM d" ) ) ;
				t.put( "id", "test.simon." + rg.nextInt( 20 ) );
				t.put( "type", "type" + rg.nextInt( 20 ) );
				t.put( "time", getFormatedTime( itemTimeGenerated ) );
				t.put( "host", "testHost" + rg.nextInt( 10 ) );
				t.put( "service", "testService" + rg.nextInt( 10 ) );
				t.put( "description", "description" + rg.nextInt( 20 ) );
			}
		}

		if ( hours > 0 ) {

			ArrayNode filteredByHours = jacksonMapper.createArrayNode();

			long now = System.currentTimeMillis();
			alertsTriggered.forEach( item -> {
				if ( item.has( "ts" ) ) {
					long itemTime = item.get( "ts" ).asLong();

					if ( (now - itemTime) < TimeUnit.HOURS.toMillis( hours ) ) {
						filteredByHours.add( item );
					}

				}
			} );

			alertsTriggered = filteredByHours;

		}

		results.set( "triggered", alertsTriggered );

		return results;
	}

	SimpleDateFormat timeDayFormat = new SimpleDateFormat( "HH:mm:ss , MMM d" );

	private String getFormatedTime ( long tstamp ) {

		Date d = new Date( tstamp );
		return timeDayFormat.format( d );
	}

}
