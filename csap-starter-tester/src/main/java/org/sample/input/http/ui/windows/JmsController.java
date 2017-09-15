package org.sample.input.http.ui.windows;

import java.io.IOException;
import java.util.ArrayList;

import javax.inject.Inject;

import org.csap.helpers.CsapRestTemplateFactory;
import org.javasimon.SimonManager;
import org.javasimon.Split;
import org.sample.BootEnterpriseApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Controller
@RequestMapping ( "/jms" )
public class JmsController {

	Logger logger = LoggerFactory.getLogger( getClass() );

	ObjectMapper jacksonMapper = new ObjectMapper();

	@Inject
	private RestTemplate jmsQueueQueryTemplate;

	@GetMapping ( "/stats" )
	public String showStats ( ModelMap springViewModel ) {
		logger.debug( "Getting q stats" );

		springViewModel.addAttribute( "csapPageLabel", "JMS Queue Checker" );
		springViewModel.addAttribute( "healthSettings", myApp.getJmsBacklogHealth() );

		return "jms/stats";
	}

	@Inject
	private BootEnterpriseApplication myApp;

	@GetMapping ( "/hungReport" )
	@ResponseBody
//	@Cacheable(BootEnterpriseApplication.JMS_REPORT_CACHE)
	public ObjectNode hungReport (
									@RequestParam String backlogQ,
									@RequestParam String processedQ,
									@RequestParam String hostPattern,
									@RequestParam String expression,
									@RequestParam int hostCount,
									@RequestParam int sampleCount )
			throws Exception {

		String[] hosts = new String[hostCount];
		hostPattern = hostPattern.replaceAll( "\\*", "" );

		logger.info( "Getting q stats for host pattern: {}", hostPattern );

		for ( int i = 0; i < hosts.length; i++ ) {
			int hostSuffix = i + 1;
			hosts[i] = hostPattern + hostSuffix;
			if ( i < 10 )
				hosts[i] = hostPattern + "0" + hostSuffix;
		}

		ObjectNode result = buildHungReport(
			expression, backlogQ, processedQ, sampleCount * 2,
			myApp.getJmsBacklogHealth().getBaseUrl(), hosts );

		return result;
	}

	public ObjectNode buildHungReport (
										String spelExpression, String backlogQ, String processedQ,
										int sampleCount, String urlBase, String... hosts ) {

		Split jmsStatusTime = SimonManager.getStopwatch( "jms.buildHungReport" ).start();
		ObjectNode report = jacksonMapper.createObjectNode();
		ArrayNode hostReports = report.putArray( "hungReports" );
		ArrayNode hungHosts = report.putArray( "hungNodes" );
		for ( String host : hosts ) {
			ObjectNode hostReport = hostReports.addObject();
			hostReport.put( "host", host );
			String url = urlBase.replace( "HOST", host ) + sampleCount;
			try {

				ObjectNode restResponse = jmsQueueQueryTemplate.getForObject( url, ObjectNode.class );
				

				logger.debug( "Url: {} response: {}", url, jacksonMapper.writerWithDefaultPrettyPrinter()
					.writeValueAsString( restResponse.at( "/data" ) ) );

				ArrayList<Integer> deviceBacklog = jacksonMapper.readValue(
					restResponse.at( backlogQ )
						.traverse(),
					new TypeReference<ArrayList<Integer>>() {
					} );

				int total = deviceBacklog.stream().mapToInt( Integer::intValue ).sum();

				logger.debug( "Total: {} deviceBacklog: {}", total, deviceBacklog );

				hostReport.put( "deviceBacklog", deviceBacklog.toString() );
				ArrayList<Integer> deviceDispatched = jacksonMapper.readValue(
					restResponse.at( processedQ )
						.traverse(),
					new TypeReference<ArrayList<Integer>>() {
					} );

				int dtotal = deviceDispatched.stream().mapToInt( Integer::intValue ).sum();

				logger.debug( "Total: {} DeviceDispatched: {}", dtotal, deviceDispatched );
				hostReport.put( "deviceDispatched", deviceDispatched.toString() );

				EvaluationContext context = new StandardEvaluationContext( this );
				context.setVariable( "backlog", deviceBacklog );
				context.setVariable( "processed", deviceDispatched );

				ExpressionParser parser = new SpelExpressionParser();
				Expression exp = parser.parseExpression( spelExpression );

				boolean isHung = (Boolean) exp.getValue( context );
				logger.debug( "host: {} hung:  {}", host, isHung );

				hostReport.put( "isHung", isHung );

				if ( isHung )
					hungHosts.add( host );
			} catch (Exception e) {

				hostReport.put( "error", "Exception: " + e.getClass().getName() + "\n" + e.getMessage() );
				//logger.error( "{} Failed host: {}, \n {}", url, host , CsapRestTemplateFactory.getFilteredStackTrace( e, "org.sample" ) );
			}
		}
		jmsStatusTime.stop();
		logger.debug( "report: {}", report );

		return report;
	}

	public boolean isQueueHung ( ArrayList<Integer> backlog, ArrayList<Integer> processed ) {

		logger.debug( "backlog: {} , processed: {}", backlog, processed );

		boolean allAreHung = true;
		for ( int i = 0; i < backlog.size(); i++ ) {
			if ( backlog.get( i ) > 0 &&
					(processed.get( i ) == 0) ) {
				allAreHung = allAreHung && true;
			} else {
				allAreHung = false;
			}
		}

		return allAreHung;
	}

}
