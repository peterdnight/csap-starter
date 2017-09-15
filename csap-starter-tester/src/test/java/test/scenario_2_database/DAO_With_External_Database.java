package test.scenario_2_database;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;

import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sample.Csap_Tester_Application;
import org.sample.jpa.Demo_DataAccessObject;
import org.sample.jpa.JobSchedule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;

import a_setup.InitializeLogging;

/**
 * 
 * Tests using an external DB (Oracle), note JPA context requires -javaagent see
 * ECLIPSE_SETUP.
 * 
 * NOte the use of @Transactional which wraps every test to rollback DB commits.
 * Note this includes the setup invoked for each
 * 
 * @TransactionConfiguration(defaultRollback=true) (no need to specify, test
 *                                                 context provides by default)
 * 
 * @author pnightin
 * 
 * @see <a href=
 *      "http://static.springsource.org/spring/docs/3.2.x/spring-framework-reference/html/testing.html#testing-introduction">
 *      Spring Test Reference Guide</a>
 * 
 * @see <a href=
 *      "http://static.springsource.org/spring/docs/3.2.x/spring-framework-reference/html/new-in-3.2.html#new-in-3.2-testing">
 *      SpringMvc Test </a>
 * 
 * 
 * @see <a href=
 *      "http://static.springsource.org/spring/docs/3.2.x/spring-framework-reference/html/new-in-3.1.html#new-in-3.1-test-context-profiles">
 *      TestContext </a>
 * 
 * 
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest ( classes = Csap_Tester_Application.class )
@ActiveProfiles("junit")
@Transactional
public class DAO_With_External_Database {

	final static private Logger logger = LoggerFactory.getLogger( DAO_With_External_Database.class );

	@Autowired
	private Demo_DataAccessObject helloDao;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		InitializeLogging.printTestHeader( logger.getName() );
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}

	@Before
	public void setUp() throws Exception {

		logger.info( InitializeLogging.TC_HEAD
				+ "Initializing in memory db" );
		for (int i = 0; i < 10; i++) {

			JobSchedule jobScheduleInput = new JobSchedule();
			jobScheduleInput.setJndiName( "test Jndi name" );
			// jobScheduleInput.setScheduleObjid(System.currentTimeMillis());
			// //
			// Never provide this as it is generated
			jobScheduleInput.setEventMessageText( "My test" );
			jobScheduleInput.setEventDescription( TEST_TOKEN
					+ System.currentTimeMillis() );
			jobScheduleInput.setMessageSelectorText( TEST_TOKEN );
			jobScheduleInput.setNextRunIntervalText( "sysdate+6/24" );
			jobScheduleInput.setNextRunTime( new Date() );
			jobScheduleInput.setStatusCd( "INACTIVE" );

			// String message = "Inserting: " + jobScheduleInput;
			// logger.info(SpringJavaConfig_GlobalContextTest.TC_HEAD +
			// message);

			jobScheduleInput = helloDao.addSchedule( jobScheduleInput );
			// logger.info("Result: " + jobScheduleInput);

		}
	}

	@After
	public void tearDown() throws Exception {
	}

	private static String TEST_TOKEN = "OracleUnitTestToken";

	/**
	 * Note the use of @Transactional and @Rollback to avoid impacting Oracle DB
	 */
	@Test
	public void delete_all_data_matching_filter() {

		String message = "Verifying no data is present after a bulk delete";
		logger.info( InitializeLogging.TC_HEAD + message );

		String results = helloDao
				.removeBulkDataJpql( TEST_TOKEN );
		logger.info( "Result: " + results );

		assertThat( helloDao.getCountJpql( TEST_TOKEN ) )
				.as( "Inserted a recpred" )
				.isEqualTo( 0 );
	}

	@Test
	public void insert_record_into_db() {
		JobSchedule jobScheduleInput = new JobSchedule();
		jobScheduleInput.setJndiName( "test Jndi name" );
		// jobScheduleInput.setScheduleObjid(System.currentTimeMillis()); //
		// Never provide this as it is generated
		jobScheduleInput.setEventMessageText( "My test" );
		jobScheduleInput
				.setEventDescription( TEST_TOKEN
						+ System.currentTimeMillis() );
		jobScheduleInput.setMessageSelectorText( TEST_TOKEN );
		jobScheduleInput.setNextRunIntervalText( "sysdate+6/24" );
		jobScheduleInput.setNextRunTime( new Date() );
		jobScheduleInput.setStatusCd( "INACTIVE" );

		String message = "Inserting: " + jobScheduleInput;
		logger.info( InitializeLogging.TC_HEAD + message );

		jobScheduleInput = helloDao.addSchedule( jobScheduleInput );
		logger.info( "Result: " + jobScheduleInput );

		assertThat( jobScheduleInput.getScheduleObjid() )
				.as( "Inserted a record" )
				.isGreaterThanOrEqualTo( 0 );

	}

	@Test
	public void show_data_using_jpql() {

		String message = "Test show data via Jpql";
		logger.info( InitializeLogging.TC_HEAD + message );

		String result = helloDao.showScheduleItemsJpql( TEST_TOKEN, 10 );
		logger.info( "Result: " + result );

		assertThat( StringUtils.countMatches( result, "Desc: " + TEST_TOKEN ) )
				.as( "Multi result query" )
				.isEqualTo( 10 );

	}
}
