package test.scenario_3_http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.apache.commons.lang3.text.WordUtils;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sample.Csap_Tester_Application;
import org.sample.input.http.ui.rest.MsgAndDbRequests;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import a_setup.InitializeLogging;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest ( classes = Csap_Tester_Application.class )
@WebAppConfiguration
@ActiveProfiles("junit")
public class Spring_Http_Endpoints {
	final static private Logger logger = LoggerFactory.getLogger( Spring_Http_Endpoints.class );

	@Autowired
	private WebApplicationContext wac;

	private MockMvc mockMvc;

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
		this.mockMvc = MockMvcBuilders.webAppContextSetup( this.wac ).build();

	}

	private static String TEST_TOKEN = "MvcTestToken";

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void http_get_landing_page() throws Exception {
		logger.info( InitializeLogging.TC_HEAD + "simple mvc test" );
		// mock does much validation.....
		ResultActions resultActions = mockMvc.perform(
				get(  "/" )
						.param( "sampleParam1", "sampleValue1" )
						.param( "sampleParam2", "sampleValue2" )
						.accept( MediaType.TEXT_PLAIN ) );

		//
		String result = resultActions
				.andExpect( status().isOk() )
				.andExpect( content().contentType( "text/html;charset=UTF-8" ) )
				.andReturn().getResponse().getContentAsString();
		logger.info( "First 100 characters:\n {} " ,  result.substring( 0, 100 ) );

		assertThat( result )
				.contains( "hello") ;

	}
	
	ObjectMapper jacksonMapper = new ObjectMapper();

	@Test
	public void http_get_cached_endpoint() throws Exception {

		String message = "Hitting controller fronted with @Cacheable";
		logger.info( InitializeLogging.TC_HEAD + message );

		// mock does much validation.....
		ResultActions resultActions = mockMvc.perform(
				get( Csap_Tester_Application.API_URL + "/simpleCacheExample" )
						.param( "key", TEST_TOKEN )
						.accept( MediaType.APPLICATION_JSON ) );

		// But you could do full parsing of the Json result if needed
		ObjectNode responseJsonNode = (ObjectNode) jacksonMapper
				.readTree( resultActions.andReturn().getResponse().getContentAsString() );

		logger.info( "responseJsonNode:\n"
				+ jacksonMapper.writerWithDefaultPrettyPrinter().writeValueAsString( responseJsonNode ) );

		// MvcResult mvcResult = resultActions
		// .andExpect( status().isOk() )
		// .andExpect( content().contentType( MediaType.APPLICATION_JSON ) )
		// .andExpect( jsonPath( "$.key" ).exists() )
		// .andExpect( jsonPath( "$.message" ).exists() ).andReturn();

		// Mock validates the existence. But we can get very explicit using the
		// result

		assertTrue( message, responseJsonNode.get( "key" ).asText().equals( TEST_TOKEN ) );

		resultActions = mockMvc.perform(
				get( Csap_Tester_Application.API_URL + "/simpleCacheExample" )
						.param( "key", TEST_TOKEN )
						.accept( MediaType.APPLICATION_JSON ) );

		// But you could do full parsing of the Json result if needed
		ObjectNode responseJsonNode2 = (ObjectNode) jacksonMapper
				.readTree( resultActions.andReturn().getResponse().getContentAsString() );
		logger.info( "responseJsonNode2:\n"
				+ jacksonMapper.writerWithDefaultPrettyPrinter().writeValueAsString( responseJsonNode ) );

		// mvcResult = resultActions
		// .andExpect( status().isOk() )
		// .andExpect( content().contentType( MediaType.APPLICATION_JSON ) )
		// .andExpect( jsonPath( "$.key" ).exists() )
		// .andExpect( jsonPath( "$.message" ).exists() ).andReturn();

		// ensure cached entry returned

		assertThat( responseJsonNode.get( "timestamp" ).asText() )
				.isEqualTo( responseJsonNode2.get( "timestamp" ).asText()  );
	}
	

	@Test
	public void http_get_hello_endpoint() throws Exception {
		logger.info( InitializeLogging.TC_HEAD + "simple mvc test" );
		// mock does much validation.....
		ResultActions resultActions = mockMvc.perform(
				get( Csap_Tester_Application.SPRINGREST_URL + "/hello" )
						.param( "sampleParam1", "sampleValue1" )
						.param( "sampleParam2", "sampleValue2" )
						.accept( MediaType.TEXT_PLAIN ) );

		//
		String result = resultActions
				.andExpect( status().isOk() )
				.andExpect( content().contentType( "text/plain;charset=UTF-8" ) )
				.andReturn().getResponse().getContentAsString();
		logger.info( "result:\n" + result );

		assertThat( result )
				.contains( "hello") ;

	}
	

	@Test
	public void http_get_endpoint_using_java_8_lamdas () throws Exception {
		logger.info( InitializeLogging.TC_HEAD + "simple mvc test for java 8" );
		// mock does much validation.....
		ResultActions resultActions = mockMvc.perform(
				get( Csap_Tester_Application.SPRINGREST_URL + "/helloJava8" )
						.param( "sampleParam1", "sampleValue1" )
						.param( "sampleParam2", "sampleValue2" )
						.accept( MediaType.TEXT_PLAIN ));

		//
		String result = resultActions
				.andExpect( status().isOk() )
				.andExpect( content().contentType( "text/plain;charset=UTF-8" ) )
				.andReturn().getResponse().getContentAsString();
		logger.info( "result:\n" + result );

		assertThat( result )
				.contains(MsgAndDbRequests.JAVA8_MESSAGE + "123" ) ;

	}
}
