package org.sample.input.http.jersey;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.stereotype.Component;


/**
 * 
 * Straight JAXRS without any Spring Wiring. Note that the paths are relative to
 * the path in the jersey init param
 * 
 * @see <a
 *      href="http://jersey.java.net/nonav/documentation/latest/user-guide.html#d4e1847">
 *      Jersey Spring Docs </a>
 * 
 * @author pnightin
 * 
 */

@Component
@Path("/helloworld")
public class HelloJaxrsResource {
	protected final Log logger = LogFactory.getLog(getClass());

	// The Java method will process HTTP GET requests
	@GET
	// The Java method will produce content identified by the MIME Media
	// type "text/plain"
	@Produces("text/plain")
	public String getClichedMessage() {
		logger.info("Simple hello");
		// Return some cliched textual content
		return "Hello World";
	}
}
