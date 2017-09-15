package test.scenario_2_database;

import static org.junit.Assert.assertTrue;

import java.util.Date;

import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sample.BootEnterpriseApplication;
import org.sample.jpa.Demo_DataAccessObject;
import org.sample.jpa.JobSchedule;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import a_setup.InitializeLogging;

/**
 * 
 * Tests using an embedded DB (HSQLDB), note JPA context requires -javaagent see  ECLIPSE_SETUP
 * 
 * NOte the  use of @Transactional which wraps every test to rollback db commits. Note this includes the setup invoked for each
 * @TransactionConfiguration(defaultRollback=true) (no need to specify, test context provides by default)
 * 
 * @author pnightin
 * 
 * 
 * @see <a
 *      href="http://static.springsource.org/spring/docs/3.2.x/spring-framework-reference/html/jdbc.html#jdbc-embedded-database-support">
 *      Spring Test: Embedding a DB</a>
 * 
 * @see <a
 *      href="http://static.springsource.org/spring/docs/3.2.x/spring-framework-reference/html/testing.html#testing-introduction">
 *      Spring Test Reference Guide</a>
 * 
 * @see <a
 *      href="http://static.springsource.org/spring/docs/3.2.x/spring-framework-reference/html/new-in-3.2.html#new-in-3.2-testing">
 *      SpringMvc Test </a>
 * 
 * 
 * @see <a
 *      href="http://static.springsource.org/spring/docs/3.2.x/spring-framework-reference/html/new-in-3.1.html#new-in-3.1-test-context-profiles">
 *      TestContext </a>
 * 
 * 
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest ( classes = BootEnterpriseApplication.class )
@ActiveProfiles("junit")
@Transactional
public class DAO_With_Embedded_DB {

	final static private org.slf4j.Logger logger = LoggerFactory.getLogger( DAO_With_Embedded_DB.class );

	@Autowired
	private Demo_DataAccessObject helloDao;

	//static private EmbeddedDatabase db;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {

		InitializeLogging.printTestHeader( logger.getName()) ;
		
		logger.info("Initializing in memory db");


		// creates an HSQL in-memory database populated from default scripts
		// classpath:schema.sql and classpath:data.sql
		// db = new EmbeddedDatabaseBuilder().addDefaultScripts().build();
		
		//db = new EmbeddedDatabaseBuilder().build();
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		//db.shutdown();
	}

	private static String TEST_TOKEN = "HsqlUnitTestToken";

	public static boolean init = true;

	@Before
	public void setUp() throws Exception {

			logger.info(InitializeLogging.TC_HEAD
					+ "Initializing in memory db");
			for (int i = 0; i < 10; i++) {

				JobSchedule jobScheduleInput = new JobSchedule();
				jobScheduleInput.setJndiName("test Jndi name");
				// jobScheduleInput.setScheduleObjid(System.currentTimeMillis());
				// //
				// Never provide this as it is generated
				jobScheduleInput.setEventMessageText("My test");
				jobScheduleInput.setEventDescription(TEST_TOKEN
						+ System.currentTimeMillis());
				jobScheduleInput.setMessageSelectorText(TEST_TOKEN);
				jobScheduleInput.setNextRunIntervalText("sysdate+6/24");
				jobScheduleInput.setNextRunTime(new Date());
				jobScheduleInput.setStatusCd("INACTIVE");

				// String message = "Inserting: " + jobScheduleInput;
				// logger.info(SpringJavaConfig_GlobalContextTest.TC_HEAD +
				// message);

				jobScheduleInput = helloDao.addSchedule(jobScheduleInput);
				// logger.info("Result: " + jobScheduleInput);

			}

	}

	@After
	public void tearDown() throws Exception {
		// db.shutdown();
	}

	@Test
	public void removeBulkData() {

		String message = "Verifying no data is present after a bulk delete";
		logger.info(InitializeLogging.TC_HEAD + message);

		String results = helloDao.removeBulkDataJpql(TEST_TOKEN);
		logger.info("Result: " + results);

		assertTrue(message,
				helloDao.getCountJpql(TEST_TOKEN) == 0);

	}

	@Test
	public void removeBulkDataWithNamedQuery() {

		String message = "Verifying no data is present after a bulk delete using named query";
		logger.info(InitializeLogging.TC_HEAD + message);

		String results = helloDao.removeBulkDataJpqlNamed(TEST_TOKEN);
		logger.info("Result: " + results);



	}

	@Test
	public void removeBulkDataWithNewJpa2_1Criteria() {

		String message = "Verifying no data is present after a bulk delete using named query";
		logger.info(InitializeLogging.TC_HEAD + message);

		String results = helloDao.removeBulkDataWithCriteria(TEST_TOKEN);
		logger.info("Result: " + results);

		assertTrue(message,
				helloDao.getCountJpql(TEST_TOKEN) == 0);

	}
	
	
	@Test
	public void add_item_to_database() {
		JobSchedule jobScheduleInput = new JobSchedule();
		jobScheduleInput.setJndiName("test Jndi name");
		// jobScheduleInput.setScheduleObjid(System.currentTimeMillis()); //
		// Never provide this as it is generated
		jobScheduleInput.setEventMessageText("My test");
		jobScheduleInput
				.setEventDescription("Spring Consumer ======> My test String: "
						+ System.currentTimeMillis());
		jobScheduleInput.setMessageSelectorText("My test");
		jobScheduleInput.setNextRunIntervalText("sysdate+6/24");
		jobScheduleInput.setNextRunTime(new Date());
		jobScheduleInput.setStatusCd("INACTIVE");

		String message = "Inserting: " + jobScheduleInput;
		logger.info(InitializeLogging.TC_HEAD + message);

		jobScheduleInput = helloDao.addSchedule(jobScheduleInput);
		logger.info("Result: " + jobScheduleInput);
		assertTrue(message, jobScheduleInput.getScheduleObjid() >= 0);

	}

	@Test
	public void testCriteriaCount()  {

		String message = "Test testCriteriaCount: ";
		logger.info(InitializeLogging.TC_HEAD + message);

		//Thread.sleep(5000);
		long count = helloDao.getCountCriteria(TEST_TOKEN);
		logger.info("Result: " + count);
		assertTrue(message, count == 10);

	}

	@Test
	public void showTestDataWithJpql() {

		String message = "Test show data via Jpql";
		logger.info(InitializeLogging.TC_HEAD + message);

		String result = helloDao.showScheduleItemsJpql(TEST_TOKEN, 10);
		logger.info("Result: " + result);
		assertTrue(message, StringUtils.countMatches(result, "Desc: " + TEST_TOKEN) == 10);

	}

	ObjectMapper jacksonMapper = new ObjectMapper();

	@Test
	public void showTestDataWithCriteria() throws Exception {

		String message = "Test show data via criteria";
		logger.info(InitializeLogging.TC_HEAD + message);

		ObjectNode resultNode = helloDao.showScheduleItemsWithFilter(
				TEST_TOKEN, 10);
		logger.info("Result: "
				+ jacksonMapper.writerWithDefaultPrettyPrinter()
						.writeValueAsString(resultNode));
		assertTrue(message, resultNode.get("count").asInt() == 10);

	}

	@Test
	public void showTestDataWithEz() throws Exception {

		String message = "Test show data via ez criteria API";
		logger.info(InitializeLogging.TC_HEAD + message);

		ObjectNode resultNode = helloDao.showScheduleItemsWithEz(
				TEST_TOKEN, 10);
		logger.info("Result: "
				+ jacksonMapper.writerWithDefaultPrettyPrinter()
						.writeValueAsString(resultNode));
		assertTrue(message, resultNode.get("count").asInt() == 10);

	}


	@Test
	public void testEzCriteriaCount()  {

		String message = "Test testCriteriaCount via Ez Api: ";
		logger.info(InitializeLogging.TC_HEAD + message);

		//Thread.sleep(5000);
		long count = helloDao.getCountEzCriteria(TEST_TOKEN);
		logger.info("Result: " + count);
		assertTrue(message, count == 10);

	}

}
