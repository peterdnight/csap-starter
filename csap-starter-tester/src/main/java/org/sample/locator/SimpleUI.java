package org.sample.locator;

import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class SimpleUI {

	final Logger logger = LoggerFactory.getLogger( SimpleUI.class );
	/**
	 * 
	 * Simple example swapping out jsp for thymeleaf
	 * 
	 * @see http://www.thymeleaf.org/doc/articles/thvsjsp.html
	 * 
	 * @param springViewModel
	 * @return
	 */
	@RequestMapping("/missingTemplate")
	public String missingTempate(Model springViewModel) {
		
		logger.info( "Sample thymeleaf controller" );
		
		springViewModel.addAttribute( "dateTime", 
				LocalDateTime.now().format( DateTimeFormatter.ofPattern( "HH:mm:ss,   MMMM d  uuuu " ) )) ;
		
		// templates are in: resources/templates/*.html
		// leading "/" is critical when running in a jar
		
		return "/missingTempate" ;
	} 
	

	@RequestMapping("/currentTime")
	public void viewHello(PrintWriter writer) {
		
		logger.info( "SpringMvc writer" );
		
		writer.println( "currentTime: " + LocalDateTime.now().format( DateTimeFormatter.ofPattern( "HH:mm:ss,   MMMM d  uuuu " ) )) ;
		
		// templates are in: resources/templates/*.html
		// leading "/" is critical when running in a jar
		
		return ;
	}

}
