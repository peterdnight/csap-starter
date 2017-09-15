package org.csap.alerts;

import java.util.concurrent.TimeUnit;

import org.csap.helpers.CsapSimpleCache;
import org.csap.integations.CsapPerformance.CustomHealth;
import org.javasimon.Sample;
import org.javasimon.utils.SimonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class AlertInstance {

	final static Logger logger = LoggerFactory.getLogger( AlertInstance.class );
	public enum AlertItem {
		id( "id" ), type( "type" ), host( "host" ), service( "service" ),
		description( "description" ), collected( "collected" ), limit( "limit" ),
		formatedTime( "time" ), timestamp( "ts" ), count( "count" );

		public String json;

		private AlertItem( String jsonField ) {
			this.json = jsonField;
		}

		static public boolean isSameId ( JsonNode a, JsonNode b ) {

			String first = a.get( AlertItem.id.json ).asText() +
					a.get( AlertItem.type.json ).asText();
			if ( a.has( AlertItem.host.json ) ) {
				first += a.get( AlertItem.host.json ).asText() +
						a.get( AlertItem.service.json ).asText();
			}
			

			String second = b.get( AlertItem.id.json ).asText() + b.get( AlertItem.type.json ).asText();
			
			// support for extended compares if host and service are added (CSAP admin)
			if ( b.has( AlertItem.host.json ) ) {
				second += b.get( AlertItem.host.json ).asText() +
						b.get( AlertItem.service.json ).asText();
			}
			
			logger.debug("first: {}, second: {} ", first, second) ;

			return first.equals( second );
		}
	}


	private String id;
	private String sampleName;
	private String userid;
	private boolean enabled=true;
	private long meanTimeNano = Long.MAX_VALUE;
	private long maxTime = Long.MAX_VALUE;
	private long maxTimeNano = Long.MAX_VALUE;
	private int collectTime = 30;
	private String collectUnits = TimeUnit.SECONDS.name();
	private String meanUnits = TimeUnit.MILLISECONDS.name();
	private String maxUnits = TimeUnit.MILLISECONDS.name();
	private boolean ignoreNull = false;

	private long occurencesMax = Long.MAX_VALUE;
	private long occurencesMin = Long.MIN_VALUE;
	private long meanTime = Long.MAX_VALUE;

	private boolean pendingFirstCollection = true;
	private Sample lastCollectedSample = null;
	
	private CustomHealth customHealth = null;

	public AlertInstance() {
		
	}
	
	public AlertInstance(String id, long occurencesMax, CustomHealth customHealth) {
		this.id = id ;
		this.occurencesMax = occurencesMax ;
		this.setCustomHealth( customHealth ) ;
	}

	/**
	 * post spring initialization - update java simon attributes to nanos (used
	 * natively) - update csapSimpleCache to ms
	 */
	public void updateTimersFromUnits () {

		setSampleName( "CsapHealth" + getCollectionSeconds() );
		setMeanTimeNano( CsapSimpleCache.parseTimeUnit( getMeanUnits(), TimeUnit.MILLISECONDS ).toNanos( getMeanTime() ) );
		setMaxTimeNano( CsapSimpleCache.parseTimeUnit( getMaxUnits(), TimeUnit.MILLISECONDS ).toNanos( getMaxTime() ) );

	}

	/**
	 * Used for scheduling collection
	 * 
	 * @return
	 */
	public long getCollectionSeconds () {

		long collectionSeconds = CsapSimpleCache.parseTimeUnit( getCollectUnits(), TimeUnit.SECONDS ).toSeconds( getCollectTime() );
		return collectionSeconds;
	}

	public String getId () {
		return id;
	}

	public void setId ( String id ) {
		this.id = id;
	}

	public long getOccurencesMax () {
		return occurencesMax;
	}

	public void setOccurencesMax ( long occurences ) {
		this.occurencesMax = occurences;
	}

	public long getMeanTime () {
		return meanTime;
	}

	/**
	 * samples collect with mean value larger then specified will trigger health
	 * alert
	 * 
	 * @param meanTime
	 */
	public void setMeanTime ( long meanTime ) {
		this.meanTime = meanTime;
	}

	public long getMaxTime () {
		return maxTime;
	}

	/**
	 * samples collect with a max value larger then specified will trigger
	 * health alert
	 * 
	 * @param maxTime
	 */
	public void setMaxTime ( long maxTime ) {
		this.maxTime = maxTime;
	}

	public long getMeanTimeNano () {
		return meanTimeNano;
	}

	public void setMeanTimeNano ( long meanTimeNano ) {
		this.meanTimeNano = meanTimeNano;
	}

	public long getMaxTimeNano () {
		return maxTimeNano;
	}

	public void setMaxTimeNano ( long maxTimeNano ) {
		this.maxTimeNano = maxTimeNano;
	}

	public String getMeanUnits () {
		return meanUnits;
	}

	/**
	 * 
	 * maximum mean time. Default: MILLISECONDS. Also supported: SECONDS,
	 * MINUTES, HOURS, .. Related: collect-time
	 * 
	 * @param collectUnits
	 * @see java.util.concurrent.TimeUnit
	 */
	public void setMeanUnits ( String timeUnits ) {
		this.meanUnits = timeUnits;
	}

	/**
	 * Attribute collection counter, default: 30. Default units is seconds Note:
	 * collections are triggered by external collection being invoked (eg by
	 * CSAP Service Collector) Related: collect-units
	 * 
	 * @param interval
	 */
	public void setCollectTime ( int interval ) {
		this.collectTime = interval;
	}

	public int getCollectTime () {
		return collectTime;
	}

	public String getCollectUnits () {
		return collectUnits;
	}

	/**
	 * 
	 * collection time unit specified. Default: SECONDS. Also supported:
	 * MINUTES, HOURS, DAYS Related: collect-time
	 * 
	 * @param collectUnits
	 * @see java.util.concurrent.TimeUnit
	 */
	public void setCollectUnits ( String collectUnits ) {
		this.collectUnits = collectUnits;
	}

	/**
	 * the minimum number of occurrences before alert is triggered
	 * 
	 * @return
	 */
	public long getOccurencesMin () {
		return occurencesMin;
	}

	public void setOccurencesMin ( long occurencesMin ) {
		this.occurencesMin = occurencesMin;
	}

	/**
	 * default: milliseconds Supports: milliseconds, seconds
	 * 
	 * @return
	 */
	public String getMaxUnits () {
		return maxUnits;
	}

	/**
	 * 
	 * maximum max time units. Default: MILLISECONDS. Also supported: SECONDS,
	 * MINUTES, HOURS, .. Related: collect-time
	 * 
	 * @param maximum
	 *            allowed unit
	 * @see java.util.concurrent.TimeUnit
	 */
	public void setMaxUnits ( String maxUnits ) {
		this.maxUnits = maxUnits;
	}

	public boolean isIgnoreNull () {
		return ignoreNull;
	}

	public void setIgnoreNull ( boolean ignoreNull ) {
		this.ignoreNull = ignoreNull;
	}

	@Override
	public String toString () {

		String result = "\n\t\t" + id;
		if ( occurencesMax < Long.MAX_VALUE )
			result += " occurences-max: " + occurencesMax;
		if ( occurencesMin > Long.MIN_VALUE )
			result += " occurences-min: " + occurencesMin;
		if ( meanTime < Long.MAX_VALUE )
			result += " meanTime: " + SimonUtils.presentNanoTime( getMeanTimeNano() );
		if ( maxTime < Long.MAX_VALUE )
			result += " maxTime: " + SimonUtils.presentNanoTime( getMaxTimeNano() );
		// result += " collection: " + csapSimpleCache.getMaxAgeFormatted();
		result += " collection: " + getCollectTime() + " " + getCollectUnits();

		if ( ignoreNull )
			result += " (Empty instances ignored)";

		return result;
	}

	private ObjectMapper jacksonMapper = new ObjectMapper();

	private final static String NOT_USED = "-";

	public ObjectNode toJson () {
		ObjectNode item = jacksonMapper.createObjectNode();
		item.put( AlertInstance.AlertItem.id.json, getId() );
		// item.put( "collection", csapSimpleCache.getMaxAgeFormatted() );
		item.put( "collection", getCollectTime() + " " + getCollectUnits() );

		item.put( "occurencesMax", NOT_USED );
		item.put( "occurencesMin", NOT_USED );
		item.put( "meanTime", NOT_USED );
		item.put( "maxTime", NOT_USED );
		item.put( "enabled", isEnabled() );
		
		if ( getUserid() != null ) {
			item.put( "userid", getUserid() );
		}

		if ( occurencesMax < Long.MAX_VALUE )
			item.put( "occurencesMax", occurencesMax );
		if ( occurencesMin > Long.MIN_VALUE )
			item.put( "occurencesMin", occurencesMin );
		if ( meanTime < Long.MAX_VALUE )
			item.put( "meanTime", SimonUtils.presentNanoTime( getMeanTimeNano() ) );
		if ( maxTime < Long.MAX_VALUE )
			item.put( "maxTime", SimonUtils.presentNanoTime( getMaxTimeNano() ) );

		return item;
	}

	public String getSampleName () {
		return sampleName;
	}

	public void setSampleName ( String sampleName ) {
		this.sampleName = sampleName;
	}

	public Sample getLastCollectedSample () {
		return lastCollectedSample;
	}

	public void setLastCollectedSample ( Sample lastCollectedSample ) {
		this.lastCollectedSample = lastCollectedSample;
	}

	public boolean isPendingFirstCollection () {
		return pendingFirstCollection;
	}

	public void setPendingFirstCollection ( boolean pendingFirstCollection ) {
		this.pendingFirstCollection = pendingFirstCollection;
	}

	public boolean isEnabled () {
		return enabled;
	}

	public void setEnabled ( boolean enabled ) {
		this.enabled = enabled;
	}

	public CustomHealth getCustomHealth () {
		return customHealth;
	}

	public void setCustomHealth ( CustomHealth customHealth ) {
		this.customHealth = customHealth;
	}

	public String getUserid () {
		return userid;
	}

	public void setUserid ( String userid ) {
		this.userid = userid;
	}

}
