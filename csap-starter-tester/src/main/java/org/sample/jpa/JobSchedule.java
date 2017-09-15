package org.sample.jpa;

import java.io.Serializable;
import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;


/**
 * The persistent class for the JOB_SCHEDULE database table.
 * 
 * @see JpaConfig
 * 
 * @see <a
 *      href="http://static.springsource.org/spring/docs/3.2.0.RELEASE/spring-framework-reference/htmlsingle/#orm-jpa">
 *      Spring Jpa docs </a>
 * 
 * 
 * 
 * 
 */
@Entity
@Table(name = "JOB_SCHEDULE")
public class JobSchedule implements Serializable {
	private static final long serialVersionUID = 1L;

	
	// Allocation size matching DB seems to be criical
	@Id
	@SequenceGenerator(name = "generator", sequenceName = "JOB_SCHEDULE_SEQ", allocationSize=1)
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "generator")
	@Column(name = "SCHEDULE_OBJID")
	private Long scheduleObjid=-1L;
	public Long  getScheduleObjid() {
		return this.scheduleObjid;
	}
	
	@Temporal(TemporalType.DATE)
	@Column(name = "CREATE_DATE")
	private Date createDate;

	@Column(name = "CREATE_ORA_LOGIN")
	private String createOraLogin;

	@Column(name = "CREATE_USER_ID")
	private String createUserId;

	@Column(name = "EVENT_DESCRIPTION")
	private String eventDescription;

	@Column(name = "EVENT_MESSAGE_TEXT")
	private String eventMessageText;

	@Column(name = "JNDI_NAME")
	private String jndiName;

	@Temporal(TemporalType.DATE)
	@Column(name = "LAST_INVOKE_TIME")
	private Date lastInvokeTime;

	@Column(name = "MESSAGE_SELECTOR_TEXT")
	private String messageSelectorText;

	@Column(name = "NEXT_RUN_INTERVAL_TEXT")
	private String nextRunIntervalText;

	@Temporal(TemporalType.DATE)
	@Column(name = "NEXT_RUN_TIME")
	private Date nextRunTime;

	@Column(name = "STATUS_CD")
	private String statusCd;

	@Temporal(TemporalType.DATE)
	@Column(name = "UPDATE_DATE")
	private Date updateDate;

	@Column(name = "UPDATE_ORA_LOGIN")
	private String updateOraLogin;

	@Column(name = "UPDATE_USER_ID")
	private String updateUserId;

	public JobSchedule() {
	}



	public void setScheduleObjid(long scheduleObjid) {
		this.scheduleObjid = scheduleObjid;
	}

	public Date getCreateDate() {
		return this.createDate;
	}

	public void setCreateDate(Date createDate) {
		this.createDate = createDate;
	}

	public String getCreateOraLogin() {
		return this.createOraLogin;
	}

	public void setCreateOraLogin(String createOraLogin) {
		this.createOraLogin = createOraLogin;
	}

	public String getCreateUserId() {
		return this.createUserId;
	}

	public void setCreateUserId(String createUserId) {
		this.createUserId = createUserId;
	}

	public String getEventDescription() {
		return this.eventDescription;
	}

	public void setEventDescription(String eventDescription) {
		this.eventDescription = eventDescription;
	}

	public String getEventMessageText() {
		return this.eventMessageText;
	}

	public void setEventMessageText(String eventMessageText) {
		this.eventMessageText = eventMessageText;
	}

	public String getJndiName() {
		return this.jndiName;
	}

	public void setJndiName(String jndiName) {
		this.jndiName = jndiName;
	}

	public Date getLastInvokeTime() {
		return this.lastInvokeTime;
	}

	public void setLastInvokeTime(Date lastInvokeTime) {
		this.lastInvokeTime = lastInvokeTime;
	}

	public String getMessageSelectorText() {
		return this.messageSelectorText;
	}

	public void setMessageSelectorText(String messageSelectorText) {
		this.messageSelectorText = messageSelectorText;
	}

	public String getNextRunIntervalText() {
		return this.nextRunIntervalText;
	}

	public void setNextRunIntervalText(String nextRunIntervalText) {
		this.nextRunIntervalText = nextRunIntervalText;
	}

	public Date getNextRunTime() {
		return this.nextRunTime;
	}

	public void setNextRunTime(Date nextRunTime) {
		this.nextRunTime = nextRunTime;
	}

	public String getStatusCd() {
		return this.statusCd;
	}

	public void setStatusCd(String statusCd) {
		this.statusCd = statusCd;
	}

	public Date getUpdateDate() {
		return this.updateDate;
	}

	public void setUpdateDate(Date updateDate) {
		this.updateDate = updateDate;
	}

	public String getUpdateOraLogin() {
		return this.updateOraLogin;
	}

	public void setUpdateOraLogin(String updateOraLogin) {
		this.updateOraLogin = updateOraLogin;
	}

	public String getUpdateUserId() {
		return this.updateUserId;
	}

	public void setUpdateUserId(String updateUserId) {
		this.updateUserId = updateUserId;
	}

	@Override
	public String toString() {
		// TODO Auto-generated method stub
		return "id: " + getScheduleObjid() + " Selector: " + getMessageSelectorText() + " Desc: " + getEventDescription();
	}



}