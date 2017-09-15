package test.scenario_3_performance;

import static org.assertj.core.api.Assertions.assertThat;

import javax.inject.Inject;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sample.CsapStarterDemo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.databind.ObjectMapper;

import a_setup.InitializeLogging;


@RunWith(SpringRunner.class)
@SpringBootTest(classes = CsapStarterDemo.class, webEnvironment=WebEnvironment.DEFINED_PORT)
@ActiveProfiles("mockJunit")
public class Performance_Console_Using_server_port {
	final static private Logger logger = LoggerFactory.getLogger( Performance_Console_Using_server_port.class );
	

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {

		InitializeLogging.printTestHeader( logger.getName() );

	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		// db.shutdown();
	}

	@Before
	public void setUp() throws Exception {
		logger.info( "Hitting landing page so that simon has results" );
		//ResponseEntity<String> response = restTemplate.getForEntity( "http://localhost:8080/", String.class ) ;
	}


	@After
	public void tearDown() throws Exception {
	}

	ObjectMapper jacksonMapper = new ObjectMapper();
	
	 
	@Inject
	RestTemplateBuilder restTemplateBuilder;
	

	@Autowired
	private Environment springEnvironment;

	@Test
	public void validate_csap_health_using_rest_template() throws Exception {

		String port = springEnvironment.getProperty( "server.port" );
		String healthUrl = "http://localhost:" + port +"/csap/health" ;
		
		logger.info( InitializeLogging.TC_HEAD + "hitting performance url: {}" , healthUrl);
		// mock does much validation.....

		TestRestTemplate restTemplate = new TestRestTemplate( restTemplateBuilder );
		
		ResponseEntity<String> response = restTemplate.getForEntity( healthUrl, String.class ) ;
		
		logger.info( "result:\n" + response );

		assertThat( response.getBody() )
				.contains( "<table id=\"metricTable\" class=\"simple\">") ;
	}
	

}
