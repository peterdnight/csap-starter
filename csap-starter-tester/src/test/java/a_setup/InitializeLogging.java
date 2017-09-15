package a_setup;

import java.net.URL;

import org.apache.commons.lang3.text.WordUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InitializeLogging {

	final static private Logger logger = LoggerFactory.getLogger( InitializeLogging.class );

	public static String TC_HEAD = "\n\n ========================= UNIT TEST =========================== \n\n";

	private static boolean isJvmInfoPrinted = false;

	public static void printTestHeader ( String description ) {

		if ( !isJvmInfoPrinted ) {
			isJvmInfoPrinted = true;
			System.out.println( "Working Directory = " +
					System.getProperty( "user.dir" ) );
			StringBuffer sbuf = new StringBuffer();
			// Dump log4j first - if it does not work, nothing will
			String resource = "a_setup/log4j2-junit.yml";
			URL configFile = ClassLoader.getSystemResource( resource );

			if ( configFile == null ) {
				System.out.println( "ERROR: Failed to find log configuration file in classpath: " + resource );

				System.exit( 99 );
			}
			try {
				sbuf.append( "\n\n ** " + resource + " found in: " + configFile.toURI().getPath() );
				
				LoggerContext context = (org.apache.logging.log4j.core.LoggerContext) LogManager.getContext(false);
				// this will force a reconfiguration
				context.setConfigLocation(configFile.toURI());
			} catch (Exception e) {
				
				System.out.println( "ERROR: Failed to resolve path: " + resource );
				System.exit( 99 );
			}

			// Now dump nicely formatted classpath.
			sbuf.append( "\n\n ====== JVM Classpath is: \n"
					+ WordUtils.wrap( System.getProperty( "java.class.path" ).replaceAll( ";", " " ), 140 ) );
			System.out.println( sbuf );
		}

		// https://logging.apache.org/log4j/2.0/faq.html
		logger.info( "\n\n *********************  {}  ***********************\n\n", description );
	}
	
	
	@Test
	public void testLog() {
		
		printTestHeader( logger.getName() ) ;
		
		logger.info( "Got here" );
	}

}
