package org.csap.integations;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.csap.helpers.CsapRestTemplateFactory;
import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.jasypt.spring31.properties.EncryptablePropertySourcesPlaceholderConfigurer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.yaml.snakeyaml.Yaml;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 *
 * Property file loading with optional override properties loaded from $HOME/csapOverRide.properties. <br>
 * <br>
 * Includes support for reading encrypted properties Note: property files may be mixed with both encrypted and non
 * encrypted entries. eg. <br>
 * <br>
 * <br>
 *
 * factorySample.madeup.user=theSampleUser<br>
 * factorySample.madeup.password=ENC( IlmCW3t72xMelXziSZQRWjXsMbXderPo70SxOFvgyuRBCk+1xpwZGg==)<br>
 * <br>
 * <br>
 *
 * Encrypted entries can be generated via the CS-AP console. Desktop testing can use the example in
 * SpringJavaConfig_GlobalContextTest.java to encrypt.
 *
 * @author pnightin
 *
 * @see EncryptablePropertySourcesPlaceholderConfigurer
 * @see PropertySourcesPlaceholderConfigurer
 *
 *
 *
 * @see <a href="http://www.jasypt.org/spring31.html"> Jascrypt </a>
 *
 * @see <a href=
 *      "http://static.springsource.org/spring/docs/3.2.x/spring-framework-reference/html/beans.html#beans-factory-extension-bpp">
 * Spring Unified Properties </a>
 *
 * @see <a href=
 *      "http://blog.springsource.com/2011/02/15/spring-3-1-m1-unified-property-management/">
 * Spring Property docs </a>
 *
 *
 */
@Configuration("CsapEncryptableProperties")
@ConditionalOnProperty("csap.encryptable-properties.enabled")
@ConfigurationProperties(prefix = "csap.encryptableProperties")
// these will not load due to lifecycle ordering of PropertySources; they are
// manually injected below
public class CsapEncryptableProperties {

	static Logger logger = LoggerFactory.getLogger( CsapEncryptableProperties.class );

	public final static String ENV_VARIABLE = "CSAP_ID";
	public final static String ALGORITHM_ENV_VARIABLE = "CSAP_ALGORITHM";

	// either file on FS, or be env var
	private String token = "willBeOverwritten";
	private String algorithm = "PBEWITHMD5ANDTRIPLEDES";

	// Note the spring injection of environment - which includes env vars.
	// --- this prevents @ConfigurationProperties from being injected
	@Bean
	static public EncryptablePropertySourcesPlaceholderConfigurer globalPropertySources(
			Environment env, StandardPBEStringEncryptor encryptorWithCsapOverrideKey) {

		List<Resource> locations = getFilesListedInApplicationYml( env );

		// Switch to jasypt extension of spring
		// PropertySourcesPlaceholderConfigurer
		// --- this enables property values to be encoded.
		// -- eg. jdbc.pass=IMF_ADMIN becomes jdbc.pass=ENC(...)
		// public PropertySourcesPlaceholderConfigurer
		// globalProperties(Environment env) {
		// PropertySourcesPlaceholderConfigurer p = new
		// PropertySourcesPlaceholderConfigurer();
		EncryptablePropertySourcesPlaceholderConfigurer encryptedProperiesConfigurer = new EncryptablePropertySourcesPlaceholderConfigurer(
				encryptorWithCsapOverrideKey );

		// Optionally - override the above with settings
		File optionalOverRideFile = new File( env.getProperty( "HOME" ) + "/csapOverRide.properties" );

		if ( optionalOverRideFile.canRead() ) {
			logger.debug( "Found property override file: " + optionalOverRideFile.getAbsolutePath() );
			builder.append( "\n\t Property Override File  : " + optionalOverRideFile.getAbsolutePath() );
			locations.add( new FileSystemResource( optionalOverRideFile ) );
		}

		encryptedProperiesConfigurer.setLocations( locations.toArray( new Resource[0] ) );
		// p.setIgnoreResourceNotFound( false );
		encryptedProperiesConfigurer.setIgnoreResourceNotFound( true );
		encryptedProperiesConfigurer.setIgnoreUnresolvablePlaceholders( true );

		// encryptedProperiesConfigurer.setPlaceholderPrefix( "$CSAP{" );
		try {
			for ( Resource r : locations ) {

				if ( r.exists() ) {
					builder.append( "\n\t Resource Location  : " + r + " location: " + r.getURI() );
				} else {
					builder.append( "\n\t Resource Not Found : " + r );
				}

			}
		} catch ( Exception e ) {
			logger.error( "Failed listing resources", e );
		}
		builder.append( "\n" );

		logger.debug( builder.toString() );

		return encryptedProperiesConfigurer;
	}

	private final static String PROP_FILES_YAML_PATH = "/csap/encryptable-properties/property-files";
	private final static String ALGORITH_YAML_PATH = "/csap/encryptable-properties/algorithm";
	private final static String TOKEN_YAML_PATH = "/csap/encryptable-properties/token";

	/**
	 *
	 * ---- PropertySourcesPlaceholderConfigurer cannot themselves be injected. - assume it is located in
	 * application.yml, and manually parse - yaml parsing is not straight forward as yaml syntax allows for multiple
	 * docs in a single file
	 *
	 * @return
	 */
	private static List<Resource> getFilesListedInApplicationYml(Environment env) {

		List<String> activeProfiles = Arrays.asList( env.getActiveProfiles() );
		List<String> rawLocations = new ArrayList<String>();
		List<Resource> resourcesToLoad = new ArrayList<Resource>();
		ClassPathResource appYmlFile = new ClassPathResource( "application.yml" );

		rawLocations.addAll( buildPropertyFileResources( activeProfiles, appYmlFile ) );

		for ( String profile : activeProfiles ) {
			appYmlFile = new ClassPathResource( "application-" + profile + ".yml" );

			if ( appYmlFile.exists() ) {
				rawLocations.addAll( buildPropertyFileResources( activeProfiles, appYmlFile ) );
			}
		}
		logger.debug( "processing rawLocations: {} ", rawLocations );

		resourcesToLoad = rawLocations.stream()
				.distinct()
				.map( resourceIdentifier -> {
					Resource resource = null;
					if ( resourceIdentifier.startsWith( "${" ) ) {
						// ${someProp}/path
						String sysPropertyName = resourceIdentifier.substring( 2,
								resourceIdentifier.lastIndexOf( "}" ) );
						String sysPropertyValue = System.getProperty( sysPropertyName );
						String fixedPath = resourceIdentifier
								.substring( resourceIdentifier.lastIndexOf( "}" ) + 1 );
						logger.debug( "sysPropertyName: {} , sysPropertyValue: {}", sysPropertyName,
								sysPropertyValue );
						resource = new FileSystemResource(
								sysPropertyValue + fixedPath );
					} else {
						// Using classpath resource
						resource = new ClassPathResource( resourceIdentifier );
					}
					return resource;
				} )
				.collect( Collectors.toList() );

		logger.debug( " Property Files Full: {}", resourcesToLoad );
		return resourcesToLoad;
	}

	private static List<String> buildPropertyFileResources(
			List<String> activeProfiles,
			ClassPathResource appYmlFile) {
		int numberOfYamlDocumentsInAppYml = 0;
		List<String> results = new ArrayList<String>();
		try {
			InputStream input = appYmlFile.getInputStream() ;
			Yaml yaml = new Yaml();

			// snake parser loads a collection of yaml documents
			for ( Object rawApplicationProfile : yaml.loadAll( input ) ) {

				numberOfYamlDocumentsInAppYml++;
				logger.debug( "Processing {} section: {}", appYmlFile, numberOfYamlDocumentsInAppYml );

				ObjectNode applicationProfileNode = jacksonMapper.convertValue( (LinkedHashMap) rawApplicationProfile,
						ObjectNode.class );

				String nodeProfile = applicationProfileNode.at( "/spring/profiles" ).asText( "default" );
				logger.debug( "Profile: {} , Active Profiles: {}",
						nodeProfile, activeProfiles );
				if ( nodeProfile.equals( "default" )
						|| (activeProfiles.contains( nodeProfile )) ) {

					if ( !applicationProfileNode.at( PROP_FILES_YAML_PATH ).isMissingNode() ) {

						ArrayNode filesArray = (ArrayNode) applicationProfileNode.at( PROP_FILES_YAML_PATH );
						logger.debug( "profile: {} , filesArray: {} ", nodeProfile, filesArray );

						// Stream<JsonNode> fileNodeStream =
						// StreamSupport.stream(filesArray.spliterator(), false)
						// ;
						Stream<JsonNode> fileNodeStream = IntStream.range( 0, filesArray.size() )
								.mapToObj( filesArray::get );
						fileNodeStream
								.map( fileNode -> fileNode.asText() )
								.forEach( resourceIdentifier -> {
									logger.debug( "{} Found: {} ", nodeProfile, resourceIdentifier );
									results.add( resourceIdentifier );
								} );

					}
				}
			}

		} catch ( Exception e ) {
			logger.error( "Failed to find files: {} , reason: {}", appYmlFile,  CsapRestTemplateFactory.getFilteredStackTrace( e, "csap" ) );
		}

		return results;
	}

	public static ObjectMapper jacksonMapper = new ObjectMapper();
	private static String CSAP_LDAP_ENABLED = "csap.security.ldap-enabled";

	// public static void demoForYamlParsing() {
	//
	// boolean result = false;
	// try {
	// StringBuilder sb = new StringBuilder( "application.yml property files: "
	// );
	//
	// ClassPathResource appYmlFile = new ClassPathResource( "application.yml"
	// );
	//
	// InputStream input = new FileInputStream( appYmlFile.getFile() );
	// Yaml yaml = new Yaml();
	//
	// HashMap<String, HashMap<String, String>> profileToData = new
	// HashMap<String, HashMap<String, String>>();
	// int counter = 0;
	// for (Object data : yaml.loadAll( input )) {
	//
	// LinkedHashMap docMap = (LinkedHashMap) data;
	// ObjectNode jsonNode = jacksonMapper.convertValue( docMap,
	// ObjectNode.class );
	// if ( jsonNode.at( "/csap/security/ldap-enabled" ) != null ) {
	// sb.append( "\n jsonNode: " + counter + "\n" + jsonNode.at(
	// "/csap/security/ldap-enabled" ) );
	// }
	// counter++;
	// }
	// logger.info( sb.toString() );
	// } catch (Exception e) {
	// logger.warn( "Failed to get {}", CSAP_LDAP_ENABLED, e );
	// }
	//
	// logger.debug( "isLdapEnabledInApplicationYml: {} ", result );
	//
	// return;
	// }
	static StringBuilder builder = new StringBuilder( "\n === csap.encryptable-properties: " );

	public String toString() {
		return builder.toString();
	}

	/**
	 * encryption bean use env variables as the primary attributes; then it reverts to using properties
	 *
	 * @param env
	 * @return
	 */
	@Bean
	public StandardPBEStringEncryptor encryptorWithCsapOverrideKey(Environment env) {
		StandardPBEStringEncryptor encryptor = new StandardPBEStringEncryptor();

		builder.append( "\n\t Default algorithm and key loaded from csap.encryptable-properties in application.yaml." );
		builder.append( "\n\t\t Files /etc/csap.token and $HOME/etc/csap.token, and then env vars will be checked for custom settings." );
		builder.append( "\n\t token:" );

		updateSettingsFromYaml( env ); // Default to using token and algorithm from csap in yaml

		File key = new File( "/etc/csap.token" );
		if ( !key.exists() ) {
			// choice 3 - use home dir on systems with restriced access
			key = new File( System.getenv( "HOME" ) + "/etc/csap.token" );
		}

		if ( key.exists() && key.isFile() && key.canRead() ) {
			try {
				setToken( FileUtils.readFileToString( key ) );
				builder.append( "loaded from file: " + key.getAbsolutePath() );
				// logger.warn( "Setting token from file: " + key.getAbsolutePath() );
			} catch ( IOException e1 ) {
				logger.error( "Failed to read key file: {}", key.getAbsolutePath(), e1 );
			}

		} else if ( env.getProperty( ENV_VARIABLE ) != null ) {
			builder.append( "loaded from environment variable: " + ENV_VARIABLE );
			//logger.warn( "Setting token from file: " + ENV_VARIABLE );
			setToken( env.getProperty( ENV_VARIABLE ) );
		} else  {
			builder.append( "default used" );
		}

		logger.debug( builder.toString() );
		encryptor.setPassword( getToken() );

		// Use same steps for algorithm
		builder.append( "\n\t algorithm: " );
		if ( env.getProperty( ALGORITHM_ENV_VARIABLE ) != null ) {
			encryptor.setAlgorithm( env.getProperty( ALGORITHM_ENV_VARIABLE ) );
			builder.append(  getAlgorithm()  );
			builder.append( "\tEnv variable used: " + ALGORITHM_ENV_VARIABLE  );
		} else {
			encryptor.setAlgorithm( getAlgorithm() );
			builder.append(  getAlgorithm()  );
		}

		logger.debug( builder.toString() );

		return encryptor;
	}

	public void updateSettingsFromYaml(Environment env) {

		List<String> activeProfiles = Arrays.asList( env.getActiveProfiles() );
		try {

			ClassPathResource defaultApplicationYml = new ClassPathResource( "application.yml" );
			processEncyptionSettingsInYamlFile( activeProfiles, defaultApplicationYml );

			for ( String profile : activeProfiles ) {
				ClassPathResource profileResource = new ClassPathResource( "application-" + profile + ".yml" );

				if ( profileResource.exists() ) {
					processEncyptionSettingsInYamlFile( activeProfiles, profileResource );
				}
			}

			// logger.info( sb.toString() );
		} catch ( Exception e ) {
			logger.warn( "Failed to get {}", CSAP_LDAP_ENABLED, e );
		}

		return;
	}

	private void processEncyptionSettingsInYamlFile(List<String> activeProfiles, ClassPathResource appYmlFile)
			throws FileNotFoundException, IOException {

		logger.debug( "Processing: {} , activeProfiles: {}", appYmlFile, activeProfiles );

		// InputStream input = new FileInputStream( appYmlFile.getFile() );
		InputStream input = appYmlFile.getInputStream() ;
		Yaml yamlLoader = new Yaml();

		for ( Object rawApplicationProfile : yamlLoader.loadAll( input ) ) {

			// use json for familiar api
			ObjectNode applicationProfileNode = jacksonMapper.convertValue( (LinkedHashMap) rawApplicationProfile,
					ObjectNode.class );
			String nodeProfile = applicationProfileNode.at( "/spring/profiles" ).asText( "default" );

			logger.debug( "Profile: {} , Active Profiles: {}",
					nodeProfile, activeProfiles );

			if ( nodeProfile.equals( "default" )
					|| (activeProfiles.contains( nodeProfile )) ) {

				if ( !applicationProfileNode.at( ALGORITH_YAML_PATH ).isMissingNode() ) {
					String foundAlgorithm = applicationProfileNode.at( ALGORITH_YAML_PATH ).asText();
					logger.debug( "Updating algorithm: {} from profile: {}", foundAlgorithm, nodeProfile );
					setAlgorithm( foundAlgorithm );
				}

				if ( !applicationProfileNode.at( TOKEN_YAML_PATH ).isMissingNode() ) {
					String foundToken = applicationProfileNode.at( TOKEN_YAML_PATH ).asText();
					logger.debug( "Updating token: {} from profile: {}", foundToken, nodeProfile );
					setToken( foundToken );
				}

			}
		}
	}

	public String getToken() {
		return token;
	}

	public void setToken(String token) {
		this.token = token;
	}

	public String getAlgorithm() {
		return algorithm;
	}

	public void setAlgorithm(String algorithm) {
		this.algorithm = algorithm;
	}

}
