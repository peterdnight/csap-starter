package test.scenario_3_performance;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.sample.CsapStarterDemo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlPage;

import a_setup.InitializeLogging;

@RunWith(SpringRunner.class)
//@WebMvcTest(includeFilters={"org.sample"})  // health is a condition bean so this will not work. See landing page example
@SpringBootTest(classes = CsapStarterDemo.class)
@AutoConfigureWebMvc
@AutoConfigureMockMvc
@ActiveProfiles("junit") // disables performance filters which do not work
public class Csap_Health_Page_Using_Html_Unit {


	final static private Logger logger = LoggerFactory.getLogger( Csap_Health_Page_Using_Html_Unit.class );

	@Autowired
    private WebClient webClient ;
	

	@Autowired
	private Environment springEnvironment;
	
	@Test   
	public void validate_csap_health() throws Exception {
		
		
		String port = springEnvironment.getProperty( "server.port" );
		String healthUrl = "http://localhost:" + port +"/csap/health" ;
		logger.info( InitializeLogging.TC_HEAD + " using webClient to hit health url: {}", healthUrl );
		// https://docs.spring.io/spring-boot/docs/current/reference/html/boot-features-testing.html
		// http://docs.spring.io/spring/docs/current/spring-framework-reference/htmlsingle/#spring-mvc-test-server-htmlunit-mah

		
		HtmlPage createMsgFormPage = webClient.getPage(healthUrl);

		logger.debug( "Full page: {}" ,  createMsgFormPage );
		logger.info( "Metric Table: {}" ,  createMsgFormPage.getElementById( "metricTable" ).getTextContent() );
		logger.info( "Test Assert"  );

		assertThat( createMsgFormPage.getElementById( "metricTable" ).getTextContent() )
				.contains( "Retrieving Health Reports") ;
	}
}
