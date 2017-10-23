package test.scenario_3_performance;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.sample.CsapStarterDemo;
import org.sample.LandingPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlPage;

import a_setup.InitializeLogging;

@RunWith(SpringRunner.class)
@WebMvcTest(controllers = {LandingPage.class})
@ContextConfiguration(classes = { CsapStarterDemo.class } )
@ActiveProfiles("htmlUnit") // disables performance filters which do not work
public class Landing_Page_Using_Web_Client {
	final  private Logger logger = LoggerFactory.getLogger( getClass() );

	@Autowired
    private WebClient webClient ;
	
//	@Autowired
//	private MockMvc mvc;

	@Autowired
	private Environment springEnvironment;
	

	
	@Autowired
	private ApplicationContext applicationContext;
	 
	@Test   
	public void validate_landing_page_with_test_web_client() throws Exception {
		
		String port = springEnvironment.getProperty( "server.port" );
		String landingPageUrl = "http://localhost:" + port +"/" ;
		
		logger.info( InitializeLogging.TC_HEAD + "simple mvc test url: {}, beans loaded: {}", 
			landingPageUrl,
			applicationContext.getBeanDefinitionCount() );
		// https://docs.spring.io/spring-boot/docs/current/reference/html/boot-features-testing.html
		// http://docs.spring.io/spring/docs/current/spring-framework-reference/htmlsingle/#spring-mvc-test-server-htmlunit-mah

		
		HtmlPage landingPage = webClient.getPage(landingPageUrl);

		logger.debug( "Full page: {}" ,  landingPage );
		logger.info( "csapPageVersion: {}" ,  landingPage.getElementById( "csapPageVersion" ).getTextContent() );

		assertThat( landingPage.getElementById( "csapPageVersion" ).getTextContent() )
				.contains( "1.0-Desktop") ;
	}
}
