package org.sample.input.jms;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;

import javax.inject.Inject;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.ObjectMessage;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sample.JmsConfig;
import org.sample.input.http.ui.rest.MsgAndDbRequests;
import org.sample.input.http.ui.rest.TestObjectForMessaging;
import org.sample.jpa.Demo_DataAccessObject;
import org.sample.jpa.JobSchedule;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "my-service-configuration.jms")
@ConditionalOnBean(JmsConfig.class)
public class SimpleJms {
	
	public SimpleJms() {
		logger.debug( "\n\n =========  building  ====== \n\n" );
	}

	protected final Log logger = LogFactory.getLog( getClass() );


	// JmsListener has lots of options for getting headers,etc based on
	// method signature
	
	//
	//         NOTE: same destination, but 2 listeners with 2 signatures means
	//               object is consumed on either alternately, but different handling

	@JmsListener(destination = "${q.simple}", containerFactory="defaultJmsListenerContainerFactory" )
	public void processSimplePayload(@Payload String data) {
		logger.info( "Received: " + data );

	}

	
	// note the use of default containerFactory, which only has default config. rarely/never practical - for demo only
	@JmsListener(destination = "${q.simple}" )
	public void processSimplePayload(Message message) {
		logger.info( "Received on q.simple: " + message );

	}
	
	//
	//
	//      ======  LT Scenario
	//
	//
	
	
	
	@Inject
	private Demo_DataAccessObject testDao;
	private static final int MAXIMUM_MESSAGE_DELIVERIES = 5;
	
	private int burnDbIterations = 2; // spring overwrites,
	private int burnDbInserts = 2; // spring overwrites,
	
	public final static String TEST_JMS_LISTENER_ID="IdForAccessingContainerManagement";
	
	@JmsListener(id=TEST_JMS_LISTENER_ID, destination = "${q.scenario}" , containerFactory="defaultJmsListenerContainerFactory" )
	public void processRawMessageForTestScenarios(Message message, Session session) throws JMSException {

		// although this should be done in Spring config file, here is an
		// example using a constructed Jamon monitor
		if ( logger.isDebugEnabled() )
			logger.debug( " Got message: " + message + " \n\ton Session:"
					+ session );

		// Test for exception / transaction handling:
		// Any exceptions push the message back onto the queue for processing
		// you can use a JTA XA mgr, or handle duplicates
		// ref. http://forum.springsource.org/showthread.php?t=65650 for XA
		// support
		logger.debug( "Max attempts redelivery needs to be implemented" );

		if ( message instanceof ObjectMessage ) {
			ObjectMessage jmsObjectMsg = (ObjectMessage) message;
			try {
				logger.info( " Got ObjectMessage" );
				TestObjectForMessaging testObject = (TestObjectForMessaging) jmsObjectMsg
						.getObject();
				logger.info( " ObjectMessage value: " + testObject );
			} catch (Exception e) {
				logger.error(
						"Likely - poorly labelled tibco exception. Caused by classloaders (read sender message).",
						e );
			}
		} else if ( message instanceof TextMessage ) {
			TextMessage jmsTextMsg = (TextMessage) message;

			String payloadText = jmsTextMsg.getText();

			logger.info( "===> Received message, payload size: (payloads padded with ~20 chars for a counter message): "
					+ payloadText.length() );

			JobSchedule jobScheduleInput = new JobSchedule();

			jobScheduleInput.setJndiName( "test Jndi name" );
			jobScheduleInput.setEventMessageText(MsgAndDbRequests.TEST_TOKEN );

			if ( payloadText.contains( "sleep" ) ) {
				try {
					logger.info( "Sleeping for 15 seconds to test threading" );
					Thread.sleep( 15000 );
				} catch (InterruptedException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
			} else if ( payloadText.contains( "burnCpu" ) ) {
				burnCpu();
			} else if ( payloadText.contains( "burnDb" ) ) {
				payloadText = burnDb( payloadText );
			} else if ( payloadText.contains( "burnNice" ) ) {
				burnNice();
			} else if ( payloadText.contains( "noDb" ) ) {
				logger.debug( "Skipping DB" );
			} else {

				int origLen = payloadText.length();
				if ( payloadText.length() > 50 )
					payloadText = payloadText.substring( 0, 50 )
							+ " == rest truncated to fit in DB ==";
				logger.debug( "Payload size: " + origLen
						+ " -- truncated to 50: " + payloadText );

				jobScheduleInput
						.setEventDescription("Spring Consumer ======>" + MsgAndDbRequests.TEST_TOKEN
								+ payloadText );
				jobScheduleInput.setMessageSelectorText(MsgAndDbRequests.TEST_TOKEN );
				jobScheduleInput.setNextRunIntervalText( "sysdate+6/24" );
				jobScheduleInput.setNextRunTime( new Date() );
				jobScheduleInput.setStatusCd( "INACTIVE" );

				try {
					jobScheduleInput = testDao.addSchedule( jobScheduleInput );
				} catch (Exception e) {
					// org.hibernate.QueryTimeoutException can occure
					invokePersistenceFailure( jobScheduleInput, e );
				}

				/**
				 * Contrived case for testing exceptions
				 */
				if ( jmsTextMsg.getText().indexOf( "exception" ) != -1 ) {
					if ( message.getJMSRedelivered() ) {
						int deliveryCount = ((Integer) message
								.getObjectProperty( "JMSXDeliveryCount" ))
										.intValue();
						if ( deliveryCount < MAXIMUM_MESSAGE_DELIVERIES ) {
							throw new RuntimeException(
									"Testing exceptin handlings"
											+ message
													.getIntProperty( "JMSXDeliveryCount" ) );
						} else {
							// Need to handle this : log to db, send email to
							// support team, etc.
							logger.error( "Maximum number of attempts exceeded  - app needs to implement" );
						}
					} else {
						// First time through - throw a runtime
						throw new RuntimeException( "Testing exceptin handlings" );
					}

				}
			}
		} else {
			logger.error( "Not dealing with non text messages" );
		}
	}

	public void invokePersistenceFailure(JobSchedule item, Exception e) {
		logger.error( "Failed to save: " + item + "\n " + e.getMessage() );
		if ( logger.isDebugEnabled() ) {
			logger.error( "Stack", e );
		}

	}

	private void burnNice() {
		try {

			logger.info(
					"\n\n ==========================Burning CPU Nice Start========================================" );
			ArrayList<String> burnCpuList = new ArrayList<String>();
			for (int i = 0; i < 5000; i++) {
				burnCpuList.add( "asdfa sdf asd fa sdf asd f asd f  "
						+ System.currentTimeMillis() + "asdf as df asd fsd" );
			}
			for (int i = 0; i < 50; i++) {
				Thread.sleep( 10 );
				Collections.sort( (ArrayList) burnCpuList.clone() );
			}
			logger.info(
					"\n\n ==========================Burning CPU Nice End========================================" );

		} catch (InterruptedException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
	}

	private String burnDb(String outputMax) {
		try {

			long start = System.currentTimeMillis();
			logger.info( "\n ==========================Burning burnDb Start, Iterations: "
					+ burnDbIterations + " Inserts: " + burnDbInserts + "\n\n\n" );
			ArrayList<String> burnCpuList = new ArrayList<String>();
			int origLen = outputMax.length();
			if ( outputMax.length() > 40 )
				outputMax = outputMax.substring( 0, 40 )
						+ " == rest truncated to fit in DB ==";
			for (int i = 0; i < burnDbIterations; i++) {
				for (int j = 0; j < burnDbInserts; j++) {
					JobSchedule jobSchedBurn = new JobSchedule();

					jobSchedBurn.setJndiName( "test Jndi name" );
					jobSchedBurn.setEventMessageText(MsgAndDbRequests.TEST_TOKEN );

					jobSchedBurn.setEventDescription( Thread.currentThread()
							.getName() + ":" + j + ":" + outputMax );
					jobSchedBurn.setMessageSelectorText(MsgAndDbRequests.TEST_TOKEN );
					jobSchedBurn.setNextRunIntervalText( "sysdate+6/24" );
					jobSchedBurn.setNextRunTime( new Date() );
					jobSchedBurn.setStatusCd( "INACTIVE" );
					jobSchedBurn = testDao.addSchedule( jobSchedBurn );
				}
				String results = testDao
						.removeTestDataOneByOne( "select j from JobSchedule j where j.eventDescription like '%"
								+ Thread.currentThread().getName() + "%'" );

				if ( logger.isDebugEnabled() )
					logger.debug( results );

				logger.info( "\n====== Completed run :" + i + " of " + burnDbIterations
						+ ", Number of items inserted, then bulk deleted:"
						+ burnDbInserts + "\n" );
			}

			logger.info( "\n\n ==========================Burning burnDb End, Iterations: "
					+ burnDbIterations
					+ " Inserts And Deletes: "
					+ (burnDbIterations * burnDbInserts)
					+ " Time: "
					+ ((System.currentTimeMillis() - start) / 1000) + "s" );
			Thread.sleep( 10 );
		} catch (InterruptedException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		return outputMax;
	}

	private void burnCpu() {
		try {

			logger.info( "\n\n ==========================Burning CPU Start========================================" );
			ArrayList<String> burnCpuList = new ArrayList<String>();
			for (int i = 0; i < 5000; i++) {
				burnCpuList.add( "asdfa sdf asd fa sdf asd f asd f  "
						+ System.currentTimeMillis() + "asdf as df asd fsd" );
			}
			for (int i = 0; i < 50000; i++) {
				Collections.sort( (ArrayList) burnCpuList.clone() );
			}
			logger.info( "\n\n ==========================Burning CPU End========================================" );
			Thread.sleep( 10 );
		} catch (InterruptedException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
	}


	public int getBurnDbIterations() {
		return burnDbIterations;
	}


	public void setBurnDbIterations(int burnDbIterations) {
		this.burnDbIterations = burnDbIterations;
	}


	public int getBurnDbInserts() {
		return burnDbInserts;
	}


	public void setBurnDbInserts(int burnDbInserts) {
		this.burnDbInserts = burnDbInserts;
	}
}
