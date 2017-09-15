package org.sample;


import javax.inject.Inject;

import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.csap.CsapMonitor;
import org.csap.docs.CsapDoc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Controller
@CsapMonitor
@RequestMapping("/")
@CsapDoc(title = "Core tests Dashboard", type=CsapDoc.PUBLIC,
		notes = {"The test application provides an extensive collection of integration tests, including both the " +
				"Operating System(kernel open file limits, etc.), as well as  many common Java projects/platforms."
		, "<a class='pushButton' target='_blank' href='https://github.com/csap-platform/csap-core/wiki'>learn more</a>"
		, "<img class='csapDocImage' src='../images/csapboot.png' />"})
public class CoreTestsDashboard {

	protected final Log logger = LogFactory.getLog( getClass() );

	@Autowired(required=false)
	JmsTemplate jmsTemplate;
	
	@Autowired(required=false)
	JmsConfig jmsConfig;

	@Inject
	BasicDataSource dataSource;

	@RequestMapping(method = RequestMethod.GET)
	public String get(Model springViewModel ) {


		springViewModel.addAttribute( "dataSource",  dataSource );
		String query="select 1 from dual" ;
		if ( dataSource.getUrl().contains( "hsqldb" )) query="SELECT 1 FROM INFORMATION_SCHEMA.SYSTEM_USERS" ;
		springViewModel.addAttribute( "query",  query );

		String broker = "disabled";
		String queue = "disabled";

		if ( jmsConfig != null ) {
			broker = jmsConfig.getBrokerInfo();
			queue = jmsConfig.getScenarioQueueName();
		}
		
		// optionally override the page name - default is the servicename
		// springViewModel.addAttribute( "csapPageLabel", "Demo99" );
		
		springViewModel.addAttribute( "broker", broker );
		springViewModel.addAttribute( "queue", queue );
		springViewModel.addAttribute( "JAPI_URL", BootEnterpriseApplication.JERSEY_URL );
		springViewModel.addAttribute( "context", "/BootEnterprise" );

		return "coreTests";
	}

	// Used by welcome page
	@RequestMapping("/test")
	public void launch(Model model) {
		get( model );
	}

	@RequestMapping("/sampleRedirect")
	public String sampleRedirect() {
		return "redirect:/LandingPage";
	}

}
