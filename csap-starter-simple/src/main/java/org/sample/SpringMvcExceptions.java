package org.sample;

import com.fasterxml.jackson.core.JsonParseException;
import java.io.IOException;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.javasimon.SimonManager;
import org.sample.CsapStarterDemo.SimonIds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

@ControllerAdvice
public class SpringMvcExceptions {

	final Logger logger = LoggerFactory.getLogger( getClass() );

	/**
	 * Default handler. Note the Springs error handling does not extend into {@link Throwables} - they will fall through
	 * to Servlet container. eg. OutOfMemoryError - will not invoke any of the handlers below.
	 *
	 * So - you still MUST define a error page in web.xml
	 *
	 * @param e
	 */
	@ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR, reason = "Exception during processing, examine server Logs")
	@ExceptionHandler(Exception.class)
	public void defaultHandler(Exception e) {
		// logger.warn("Controller  exception: " + e.getMessage());
		logger.warn( "Controller  exception: ", e );
		SimonManager.getCounter( SimonIds.exceptions.id ).increase();
	}

	@ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR, reason = "Exception during processing, examine server Logs")
	@ExceptionHandler(NullPointerException.class)
	public void handleNullPointer(Exception e) {
		logger.warn( "Controller null pointer: ", e );
		SimonManager.getCounter( SimonIds.nullPointer.id ).increase();
	}

	@ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR, reason = "Exception during processing, examine server Logs")
	@ExceptionHandler(JsonParseException.class)
	public void handleJsonParsing(Exception e) {
		logger.warn( "Controller json parsing: ", e );
		SimonManager.getCounter( SimonIds.exceptions.id ).increase();
	}

	// ClientAbort which extends ioexception cannot have response written cannot
	// have a response written
	@ExceptionHandler(IOException.class)
	public void handleIOException(Exception e, HttpServletResponse response) {
		String stackFrames = ExceptionUtils.getStackTrace( e );
		if ( stackFrames.contains( "ClientAbortException" ) ) {
			logger.info( "ClientAbortException found: {}", e.getMessage() );
		} else {
			logger.warn( "IOException found.  ", e );
			try {
				response.setStatus( HttpStatus.INTERNAL_SERVER_ERROR.value() );
				response.getWriter().print( HttpStatus.INTERNAL_SERVER_ERROR.value() + " : Exception during processing, examine server Logs" );
			} catch ( IOException e1 ) {
				logger.info( "ClientAbortException found: {}", e.getMessage() );
			}
		}
	}

}
