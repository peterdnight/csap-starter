package org.sample;

import java.io.PrintWriter;
import java.security.Principal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import org.apache.commons.lang3.text.WordUtils;
import org.csap.CsapMonitor;
import org.csap.docs.CsapDoc;
import org.csap.integations.CsapInformation;
import org.csap.security.CustomUserDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@CsapMonitor ( prefix = "LandPage" )
@CsapDoc ( title = "CSAP Landing Page Controller" , type = CsapDoc.PUBLIC , notes = {
		"Landing page provides simple technology demonstrations. Refer to @CsapDoc java doc for more usage examples.",
		"<a class='pushButton' target='_blank' href='https://github.com/csap-platform/csap-core/wiki'>learn more</a>",
		"<img class='csapDocImage' src='CSAP_BASE/images/csapboot.png' />" } )
public class LandingPage {

	final Logger logger = LoggerFactory.getLogger( getClass() );

	

	@Inject
	CsapInformation csapInfo;
	
	@GetMapping ( value = "/" )
	public String get ( Model springViewModel ) {

		springViewModel.addAttribute( "helpPageExample", LandingPage.class.getCanonicalName() );
		springViewModel.addAttribute( "docsController",  csapInfo.getDocUrl() + "/class" );

		return "LandingPage";
	}

	@GetMapping ( "/maxConnections" )
	public String maxConnections ( Model springViewModel ) {

		return "maxConnections";
	}

	@CsapDoc ( linkTests = {
			"Test1", "TestWithOptionalParam"
	} , linkGetParams = {
			"param=value_1,anotherParam=Peter",
			"sampleOptionalList=test1"
	} , notes = {
			"Show the current time", "This is for demo only" } )
	// @RequestMapping("/currentTime")
	@GetMapping ( "/currentTime" )
	public void currentTime (
								@RequestParam ( value = "mySampleParam" , required = false , defaultValue = "1.0" ) double sampleForTesting,
								@RequestParam (required = false) ArrayList<String> sampleOptionalList,
								String a,
								PrintWriter writer ) {

		String formatedTime = LocalDateTime.now().format( DateTimeFormatter.ofPattern( "HH:mm:ss,   MMMM d  uuuu " ) );

		logger.info( "Time now is: {}, sampleOptionalList: {}, a: {}", formatedTime, sampleOptionalList, a );

		writer.println( "currentTime: " + formatedTime );

		return;
	}

	@CsapDoc ( linkTests = { "Test1", "Test2" } , linkPostParams = { "param=value_1,anotherParam=Peter", "param=value_2" } , notes = {
			"Get the logged in user if security is enabled" } )
	@RequestMapping ( "/currentUser" )
	public void currentUser ( PrintWriter writer, Principal principle ) {

		logger.info( "SpringMvc writer" );

		if ( principle != null ) {
			writer.println( "logged in user: " + principle.getName() );
		} else {
			writer.println( "logged in user: principle is null - verify security is configured" );
		}

		return;
	}

	@RequestMapping ( "/currentUserDetails" )
	public void currentUserDetails ( PrintWriter writer ) {

		logger.info( "SpringMvc writer" );
		CustomUserDetails userDetails = (CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
		;
		writer.println( "logged in user email: " + userDetails.getMail() );
		writer.println( "\n\n user information: \n" + WordUtils.wrap( userDetails.toString(), 80 ) );

		return;
	}

	@Inject
	HelloService helloService;

	@RequestMapping ( "/testAsync" )
	@ResponseBody
	public String testAsync (
								@RequestParam ( value = "delaySeconds" , required = false , defaultValue = "5" ) int delaySeconds )
			throws Exception {
		String message = "Hello from " + this.getClass().getSimpleName()
				+ " at " + LocalDateTime.now().format( DateTimeFormatter.ofPattern( "hh:mm:ss" ) );
		helloService.printMessage( message, delaySeconds );
		return "Look in logs for async to complete in: " + delaySeconds + " seconds";
	}

	@RequestMapping ( "/testNullPointer" )
	public String testNullPointer () {

		if ( System.currentTimeMillis() > 1 ) {
			throw new NullPointerException( "For testing only" );
		}

		return "hello";
	}

	@RequestMapping ( "/missingTemplate" )
	public String missingTempate ( Model springViewModel ) {

		logger.info( "Sample thymeleaf controller" );

		springViewModel.addAttribute( "dateTime",
			LocalDateTime.now().format( DateTimeFormatter.ofPattern( "HH:mm:ss,   MMMM d  uuuu " ) ) );

		// templates are in: resources/templates/*.html
		// leading "/" is critical when running in a jar
		return "/missingTemplate";
	}

	@RequestMapping ( "/malformedTemplate" )
	public String malformedTemplate ( Model springViewModel ) {

		logger.info( "Sample thymeleaf controller" );

		springViewModel.addAttribute( "dateTime",
			LocalDateTime.now().format( DateTimeFormatter.ofPattern( "HH:mm:ss,   MMMM d  uuuu " ) ) );

		// templates are in: resources/templates/*.html
		// leading "/" is critical when running in a jar
		return "/MalformedExample";
	}

}
