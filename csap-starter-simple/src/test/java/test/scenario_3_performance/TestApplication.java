//package test.scenario_3_performance;
//
//import org.csap.integations.CsapBootConfig;
//import org.csap.integations.CsapInformation;
//import org.csap.integations.CsapPerformance;
//import org.csap.integations.CsapSecurityConfiguration;
//import org.csap.integations.CsapSecurityRestFilter;
//import org.csap.integations.CsapServiceLocator;
//import org.sample.BootReferenceApplication;
//import org.springframework.boot.autoconfigure.SpringBootApplication;
//import org.springframework.cache.annotation.Cacheable;
//import org.springframework.cache.annotation.EnableCaching;
//import org.springframework.context.annotation.ComponentScan;
//import org.springframework.context.annotation.ComponentScan.Filter;
//import org.springframework.context.annotation.FilterType;
//import org.springframework.context.annotation.Import;
//
//@SpringBootApplication
//@EnableCaching
//@ComponentScan (
//
//		basePackages = {"org.sample", "org.csap"} ,
//
//		excludeFilters = {
//				@Filter ( type = FilterType.ASSIGNABLE_TYPE , value = {
//						BootReferenceApplication.class,
//						CsapPerformance.class,
//						CsapSecurityConfiguration.class,
//						CsapSecurityRestFilter.class
//				} )
//		} )
//public class TestApplication {
//
//}
