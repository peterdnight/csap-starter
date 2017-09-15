package org.csap;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.csap.integations.CsapBootConfig;
import org.csap.integations.CsapEncryptableProperties;
import org.csap.integations.CsapInformation;
import org.csap.integations.CsapPerformance;
import org.csap.integations.CsapSecurityConfiguration;
import org.csap.integations.CsapServiceLocator;
import org.csap.integations.CsapWebServerConfig;
import org.jasypt.spring31.properties.EncryptablePropertySourcesPlaceholderConfigurer;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.security.SecurityAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.annotation.AliasFor;

/**
 * 
 * CSAP Boot - provides enterprise integration by importing
 * {@link CsapBootConfig }.
 * 
 * Integrations are configured using application.yml to selectively enable and
 * configure integrations. Refer to
 * <a href="https://github.com/csap-platform/csap-starter">Code
 * Samples</a> for configuration examples. <br>
 * Integrations include:
 * 
 * <ul>
 * <li>{@link CsapInformation }: provides CSAP configuration (jvmName, ports,
 * etc) and Servlet/Header/Cookie information</li>
 * 
 * <li>{@link CsapEncryptableProperties }: provides a fully configured
 * {@link EncryptablePropertySourcesPlaceholderConfigurer}. Note Spring only
 * allows a single instance of {@link PropertySourcesPlaceholderConfigurer}.
 * Configured to use CSAP env variables for algorithm and key when deployed in
 * labs.</li>
 * 
 * <li>{@link CsapPerformance }: provides full {@link org.javasimon.Stopwatch
 * JavaSimon} integration. Any spring services annotated with @
 * {@link CsapMonitor} will be included in metrics, along with any jdbc or
 * monitored urls.</li>
 * 
 * <li>{@link CsapSecurityConfiguration }: provides a complete security solution
 * based on configurable settings driven from active directory. Includes SSO
 * across jvms, extensible ACLs, login pages, ...</li>
 * 
 * <li>{@link CsapServiceLocator }: provides client side loadbalancing,
 * including service lookup, and multiple strategies (round-robin, least busy,
 * ...)</li>
 * 
 * <li>{@link CsapWebServerConfig }: provides appliance loadbalancing using
 * Apache ModJk and Httpd.</li>
 * 
 * <li>CSAP Opensouce UI Framework: provides extensive set of the most common
 * javascript and css components available Note: CSAP BootUtils provides either
 * js/css directly or via <a href="http://www.webjars.org/">webjars</a> along
 * with html templates that can be included directly into client projects.</li>
 * 
 * </ul>
 * 
 * <br/>
 * <br/>
 * 
 * 
 * Spring Supplied:<br/>
 * Indicates a {@link Configuration configuration} class that declares one or
 * more {@link Bean @Bean} methods and also triggers
 * {@link EnableAutoConfiguration auto-configuration} and {@link ComponentScan
 * component scanning}. This is a convenience annotation that is equivalent to
 * declaring {@code @Configuration}, {@code @EnableAutoConfiguration} and
 * {@code @ComponentScan}.
 *
 *
 *
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@Configuration
@EnableAutoConfiguration(exclude = { SecurityAutoConfiguration.class })
@ComponentScan
@Import(CsapBootConfig.class)
public @interface CsapBootApplication {

	/**
	 * Exclude specific auto-configuration classes such that they will never be
	 * applied.
	 * 
	 * @return the classes to exclude
	 */
	Class<?>[]exclude() default {};

	/**
	 * Exclude specific auto-configuration class names such that they will never
	 * be applied.
	 * 
	 * @return the class names to exclude
	 * @since 1.3.0
	 */
	String[]excludeName() default {};

	/**
	 * Base packages to scan for annotated components. Use
	 * {@link #scanBasePackageClasses} for a type-safe alternative to
	 * String-based package names.
	 * 
	 * @return base packages to scan
	 * @since 1.3.0
	 */
	@AliasFor(annotation = ComponentScan.class, attribute = "basePackages")
	String[]scanBasePackages() default {};

	/**
	 * Type-safe alternative to {@link #scanBasePackages} for specifying the
	 * packages to scan for annotated components. The package of each class
	 * specified will be scanned.
	 * <p>
	 * Consider creating a special no-op marker class or interface in each
	 * package that serves no purpose other than being referenced by this
	 * attribute.
	 * 
	 * @return base packages to scan
	 * @since 1.3.0
	 */
	@AliasFor(annotation = ComponentScan.class, attribute = "basePackageClasses")
	Class<?>[]scanBasePackageClasses() default {};

}
