package org.sample.jpa;

import java.util.Iterator;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.MappedSuperclass;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaDelete;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.ParameterExpression;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import org.sample.input.http.ui.rest.MsgAndDbRequests;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.uaihebert.factory.EasyCriteriaFactory;
import com.uaihebert.model.EasyCriteria;

/**
 * 
 * 
 * @see <a
 *      href="http://en.wikibooks.org/wiki/Java_Persistence">
 *      JPA @ wikibooks</a>
 *      
 * Simple JPA DAO - Note the use of the @MappedSuperclass that enables DAO to define named queries that typically span multiple
 * entities.
 * 
 * @see <a
 *      href="http://static.springsource.org/spring/docs/3.2.x/spring-framework-reference/html/orm.html#orm-jpa">
 *      Spring Jpa docs </a>
 * 
 * 
 * @see <a href="http://www.ibm.com/developerworks/java/library/j-typesafejpa/">
 *      Critical to understand JPA Criteria API </a>
 * 
 * 
 * @see <a href="http://docs.oracle.com/javaee/6/tutorial/doc/bnbtg.html"> JEE
 *      JPA tutorial</a>
 * 
 * @see <a
 *      href="http://paddyweblog.blogspot.com/2010/04/some-examples-of-criteria-api-jpa-20.html">
 *      JPQL and Criteria API comparisons </a>
 *      
 * 
 * @author pnightin
 * 
 */
@Transactional(readOnly = true)
@Service
@MappedSuperclass
@NamedQueries({
		@NamedQuery(name = Demo_DataAccessObject.FIND_ALL, query = "select j from JobSchedule j where j.eventDescription like :"
				+ Demo_DataAccessObject.FILTER_PARAM + " ORDER BY j.scheduleObjid"),
		@NamedQuery(name = Demo_DataAccessObject.DELETE_ALL, query = "delete JobSchedule j where j.eventDescription like :"
				+ Demo_DataAccessObject.FILTER_PARAM),
		@NamedQuery(name = Demo_DataAccessObject.COUNT_ALL, query = "SELECT COUNT(j) from JobSchedule j where j.eventDescription  like :"
				+ Demo_DataAccessObject.FILTER_PARAM) })
public class Demo_DataAccessObject {

	// public static final String TEST_DATA_HQL =
	// "select j from JobSchedule j where j.eventDescription like '%" +
	// TEST_TOKEN +"%' ORDER BY j.scheduleObjid";
	// public static final String COUNT_HQL =
	// "SELECT COUNT(j) from JobSchedule j where j.eventDescription like '%" +
	// TEST_TOKEN + "%'";

	public static final String FIND_ALL = "HelloDao.FindAll";
	public static final String DELETE_ALL = "HelloDao.DeleteAll";
	public static final String COUNT_ALL = "HelloDao.CountAll";
	public static final String FILTER_PARAM = "FILTER_PARAM";

	final private Logger logger = LoggerFactory.getLogger( getClass() );

	@PersistenceContext
	private EntityManager entityManager;

	// Bad form to put transaction directly into DAO - should go into biz
	// delegate
	@Transactional(readOnly = false, propagation = Propagation.REQUIRED)
	public JobSchedule addSchedule(JobSchedule jobSchedule) {

		logger.debug("Got Here");

		try {
			entityManager.persist(jobSchedule);
			//Thread.sleep( 5000 );
			// Never inside here - this causes hib do choke since id is only
			// available after the commit
			// logger.info("Persisted entity, id via seq from db is: "
			// + jobSchedule.getScheduleObjid());
		} catch (Exception e) {
			logger.error("Failed to persist: ", e);
		}
		return jobSchedule;
	}

	@Transactional(readOnly = false, propagation = Propagation.REQUIRED)
	public String removeBulkDataJpql(String eventFilter) {

		logger.debug("Got Here");
		StringBuilder resultsBuf = new StringBuilder("\nUsers deleted using "
				+ this.getClass().getName() + ".removeTestData\nFilter \n"
				+ eventFilter + "===>\n");

		try {
			
			// JPA named queries in hibernate 4.3 through a illegal state
//			Query q = entityManager.createNamedQuery(DELETE_ALL);
//			q.setParameter(FILTER_PARAM, "%" + eventFilter + "%");
//			int deletedEntities = q.executeUpdate();
			
			Query q = entityManager.createQuery("delete JobSchedule j where j.eventDescription like :" + FILTER_PARAM);
			q.setParameter(FILTER_PARAM, "%" + eventFilter + "%");
			int deletedEntities = q.executeUpdate();
			
			resultsBuf.append("Total Items deleted: " + deletedEntities);

		} catch (Throwable e) {
			resultsBuf.append("\n *** Got Exception: "
					+ MsgAndDbRequests.getCustomStackTrace(e));
			logger.error(resultsBuf.toString(), e);
		}

		return resultsBuf.toString();
	}
	
	/**
	 * Use a named query to delete bulk data. Latest hibernate seems to choke on this
	 * 
	 * @param eventFilter
	 * @return
	 */
	@Transactional(readOnly = false, propagation = Propagation.REQUIRED)
	public String removeBulkDataJpqlNamed(String eventFilter) {

		logger.debug("Got Here");
		StringBuilder resultsBuf = new StringBuilder("\nUsers deleted using "
				+ this.getClass().getName() + ".removeTestData\nFilter \n"
				+ eventFilter + "===>\n");

		try {
			
			// JPA named queries in hibernate 4.3 through a illegal state
			Query q = entityManager.createNamedQuery(DELETE_ALL);
			q.setParameter(FILTER_PARAM, "%" + eventFilter + "%");
			int deletedEntities = q.executeUpdate();
			
			resultsBuf.append("Total Items deleted: " + deletedEntities);

		} catch (Throwable e) {
			resultsBuf.append("\n *** Got Exception: "
					+ MsgAndDbRequests.getCustomStackTrace(e));
			logger.error(resultsBuf.toString(), e);
		}

		return resultsBuf.toString();
	}
	
	/**
	 * 
	 * Much awaited bulk apis in JPA 2.1
	 * 
	 * @see http://en.wikibooks.org/wiki/Java_Persistence/Criteria#CriteriaDelete_.28JPA_2.1.29
	 * 
	 * @param eventFilter
	 * @return
	 * 
	 */
	@Transactional(readOnly = false, propagation = Propagation.REQUIRED)
	public String removeBulkDataWithCriteria(String eventFilter) {

		logger.debug("Got Here");
		StringBuilder resultsBuf = new StringBuilder("\nUsers deleted using "
				+ this.getClass().getName() + ".removeTestData\nFilter \n"
				+ eventFilter + "===>\n");

		try {
			
			CriteriaBuilder cb = entityManager.getCriteriaBuilder();
			 
			// Deletes all Employee's making more than 100,000.
			CriteriaDelete<JobSchedule> delete = cb.createCriteriaDelete(JobSchedule.class);
			Root jobScheduleEntity = delete.from(JobSchedule.class);
			delete.where(cb.like (jobScheduleEntity.get("eventDescription"), "%" + eventFilter + "%"));
			Query query = entityManager.createQuery(delete);
			int deletedEntities = query.executeUpdate();
			
			
			resultsBuf.append("Total Items deleted: " + deletedEntities);

		} catch (Throwable e) {
			resultsBuf.append("\n *** Got Exception: "
					+ MsgAndDbRequests.getCustomStackTrace(e));
			logger.error(resultsBuf.toString(), e);
		}

		return resultsBuf.toString();
	}
	
	/**
	 * @param filter
	 * @return
	 */
	@Transactional(readOnly = false, propagation = Propagation.REQUIRED)
	public String removeTestDataOneByOne(String filter) {

		logger.debug("Got Here");
		StringBuilder resultsBuf = new StringBuilder("\nUsers deleted using "
				+ this.getClass().getName() + ".removeTestData\nFilter \n"
				+ filter + "===>\n");

		try {
			// Query q =
			// entityManager.createQuery("select j from JobSchedule j where j.eventDescription like '%*******%'");
			Query q = entityManager.createQuery(filter);

			@SuppressWarnings("unchecked")
			Iterator<JobSchedule> jobSchedIter = (Iterator<JobSchedule>) q
					.getResultList().iterator();

			logger.error("Horrible way to delete data - this should be a bulk delete");
			int i = 0;
			while (jobSchedIter.hasNext()) {
				JobSchedule jobSched = jobSchedIter.next();

				if (i++ < 10)
					resultsBuf.append(jobSched + "\n");

				entityManager.remove(jobSched);

			}
			resultsBuf.append("Total Items deleted: " + i);

		} catch (Throwable e) {
			resultsBuf.append("\n *** Got Exception: "
					+ MsgAndDbRequests.getCustomStackTrace(e));
			logger.error(resultsBuf.toString(), e);
		}

		return resultsBuf.toString();
	}

	public String showScheduleItemsJpql(String eventFilter, int maxResults) {
		logger.debug("Got Here");
		StringBuffer userBuf = new StringBuffer("\nResult from:"
				+ this.getClass().getName()
				+ ".showScheduleItemsJpql() query:\n" + eventFilter + "\n");

		try {

			Query q = entityManager.createNamedQuery(FIND_ALL).setMaxResults(
					maxResults);
			q.setParameter(FILTER_PARAM, "%" + eventFilter + "%");

			@SuppressWarnings("unchecked")
			Iterator<JobSchedule> sampleIter = (Iterator<JobSchedule>) q
					.getResultList().iterator();

			while (sampleIter.hasNext()) {
				JobSchedule user = sampleIter.next();
				userBuf.append(user + "\n");
			}
		} catch (Throwable e) {
			userBuf.append("\n *** Got Exception: "
					+ MsgAndDbRequests.getCustomStackTrace(e));
			logger.error(userBuf.toString(), e);
		}

		return userBuf.toString();

	}

	ObjectMapper jacksonMapper = new ObjectMapper();

	/**
	 * This looks longer, so why bother? Read the references at the top of the
	 * class
	 * 
	 * @param filter
	 * @param maxResults
	 * 
	 * @return String containing the records in raw text
	 * 
	 *         {@link Demo_DataAccessObject#showScheduleItems(String, int)}
	 * 
	 */
	public ObjectNode showScheduleItemsWithFilter(String filter, int maxResults) {
		logger.debug("Got Here");

		ObjectNode resultNode = jacksonMapper.createObjectNode();
		resultNode.put("count", getCountCriteria(filter));
		ArrayNode dataArrayNode = resultNode.putArray("data");
		try {

			CriteriaBuilder builder = entityManager.getCriteriaBuilder();
			CriteriaQuery<JobSchedule> criteriaQuery = builder
					.createQuery(JobSchedule.class);

			Root<JobSchedule> jobScheduleRoot = criteriaQuery
					.from(JobSchedule.class);

			ParameterExpression<String> paramExp = builder.parameter(
					String.class, "paramHack");
			Predicate condition = builder.like(
					jobScheduleRoot.<String> get("eventDescription"), paramExp);

			criteriaQuery.where(condition);

			TypedQuery<JobSchedule> typedQuery = entityManager.createQuery(
					criteriaQuery).setMaxResults(maxResults);
			typedQuery.setParameter("paramHack", "%" + filter + "%");

			List<JobSchedule> jobs = typedQuery.getResultList();

			Iterator<JobSchedule> sampleIter = jobs.iterator();

			while (sampleIter.hasNext()) {
				JobSchedule user = sampleIter.next();
				ObjectNode jobNode = jacksonMapper.createObjectNode();
				jobNode.put("id", user.getScheduleObjid());
				jobNode.put("description", user.getEventDescription());
				dataArrayNode.add(jobNode);
			}
		} catch (Throwable e) {
			resultNode.put("error",
					MsgAndDbRequests.getCustomStackTrace(e));
			logger.error("Failed querying db", e);
		}

		return resultNode;

	}
	

	/**
	 * Experimental: JPA Wrapper framework - Compare lines of code with the above.
	 * 
	 * @see <a href="http://easycriteria.uaihebert.com"> 
	 *     Ez Wrapper Documentation</a>
	 * 
	 * @param filter
	 * @param maxResults
	 * @return
	 */
	public ObjectNode showScheduleItemsWithEz(String filter, int maxResults) {
		logger.warn("\n\n =========== Experimental! Filter: " + filter + " =========================\n\n");

		ObjectNode resultNode = jacksonMapper.createObjectNode();
		resultNode.put("count", getCountCriteria(filter));
		ArrayNode dataArrayNode = resultNode.putArray("data");
		try {

			// 2 lines replaces 10 lines - easier to read
			EasyCriteria<JobSchedule> easyCriteria = EasyCriteriaFactory.createQueryCriteria(entityManager,JobSchedule.class);
			List<JobSchedule> jobs = easyCriteria.andStringLike("eventDescription", "%"+filter+"%").getResultList();

			Iterator<JobSchedule> sampleIter = jobs.iterator();

			while (sampleIter.hasNext()) {
				JobSchedule user = sampleIter.next();
				ObjectNode jobNode = jacksonMapper.createObjectNode();
				jobNode.put("id", user.getScheduleObjid());
				jobNode.put("description", user.getEventDescription());
				dataArrayNode.add(jobNode);
			}
		} catch (Throwable e) {
			resultNode.put("error",
					MsgAndDbRequests.getCustomStackTrace(e));
			logger.error("Failed querying db", e);
		}

		return resultNode;

	}

	public long getCountJpql(String eventFilter) {
		logger.debug("Got Here");

		StringBuffer userBuf = new StringBuffer("\nResult from:"
				+ this.getClass().getName() + ".showScheduleItems() query:\n"
				+ eventFilter + "\n");

		Query q = entityManager.createNamedQuery(COUNT_ALL);
		q.setParameter(FILTER_PARAM, "%" + eventFilter + "%");

		long num = (Long) q.getSingleResult();

		return num;

	}

	/**
	 * 
	 * @see http://www.ibm.com/developerworks/java/library/j-typesafejpa/
	 * @see http://planet.jboss.org/post/
	 *      a_more_concise_way_to_generate_the_jpa_2_metamodel_in_maven
	 * @see http 
	 *      ://paddyweblog.blogspot.com/2010/04/some-examples-of-criteria-api
	 *      -jpa-20.html
	 * 
	 * @param filter
	 * @return
	 */
	public long getCountCriteria(String filter) {
		logger.debug("Got Here" + filter);

		CriteriaBuilder builder = entityManager.getCriteriaBuilder();

		CriteriaQuery<Long> criteriaQuery = builder.createQuery(Long.class);

		Root<JobSchedule> jobScheduleRoot = criteriaQuery
				.from(JobSchedule.class);

		criteriaQuery.select(builder.count(jobScheduleRoot));

		// For ease of use, jpamodelgen is not used in the reference project
		// - recommend to configure eclipse project with jpamodelgen eg. this
		// should be
		// Predicate condition = qb.gt(p.get(JobSchedule_.age), 20);
		ParameterExpression<String> paramExp = builder.parameter(String.class,
				FILTER_PARAM);
		Predicate condition = builder.like(
				jobScheduleRoot.<String> get("eventDescription"), paramExp);
		criteriaQuery.where(condition);

		TypedQuery<Long> typedQuery = entityManager.createQuery(criteriaQuery);
		typedQuery.setParameter(FILTER_PARAM, "%" + filter + "%");
		long num = typedQuery.getSingleResult();
		// long num =
		// entityManager.createQuery(criteriaQuery).getSingleResult();

		return num;

	}
	
	/**
	 * @see <a href="http://easycriteria.uaihebert.com"> 
	 *     Ez Wrapper Documentation</a>
	 * 
	 * @param filter
	 * @return
	 */
	public long getCountEzCriteria(String filter) {
		logger.debug("Got Here: " + filter);

		// 2 lines replaces 10 lines - easier to read
		EasyCriteria<JobSchedule> easyCriteria = EasyCriteriaFactory.createQueryCriteria(entityManager,JobSchedule.class);
		long num = easyCriteria.andStringLike("eventDescription", "%"+filter+"%").count();

		return num;

	}
}
