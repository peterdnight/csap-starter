package org.csap.security;

import java.util.Arrays;
import java.util.Collections;

import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ldap.core.ContextMapper;
import org.springframework.ldap.core.DirContextAdapter;
import org.springframework.ldap.core.DistinguishedName;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class CsapUser implements ContextMapper {
	static final Logger logger = LoggerFactory.getLogger( CsapUser.class );
	public static final String SYS_USER = "System";

	public static CustomUserDetails currentUsersLdapDetails () {
		String userName = SYS_USER;
		try {
			CustomUserDetails person = (CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
			return person;
		} catch (Exception e) {
			// logger.error( "Failed to get user from Spring security context"
			// );
			CustomUserDetails person;
		}
		return null;
	}
	
	public static UserDetails currentUserDetails () {
		try {
			UserDetails person = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
			return person;
		} catch (Exception e) {
			// logger.error( "Failed to get user from Spring security context"
			// );
			CustomUserDetails person;
		}
		return null;
	}

	public static String currentUsersID () {
		String userName = SYS_USER;
		try {
			// use non ldap
			UserDetails person = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
			//CustomUserDetails person = (CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
			userName = person.getUsername();
		} catch (Exception e) {
			logger.warn( "Failed to get user from Spring security context" );
		}
		return userName;
	}

	public static String currentUsersEmailAddress () {
		String userName = "csap@yourcompany.com";
		try {
			CustomUserDetails person = (CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
			userName = person.getMail();
		} catch (Exception e) {
			logger.warn( "Failed to get user from Spring security context" );
			// CustomUserDetails person ;
		}
		return userName;
	}

	private String mail;
	private String manager;

	private String telephoneNumber;
	private String title;
	private String location;

	public String getLocation () {
		return location;
	}

	public void setLocation ( String location ) {
		this.location = location;
	}

	public String fullName;

	public String getFullName () {
		return fullName;
	}

	public void setFullName ( String fullName ) {
		this.fullName = fullName;
	}

	public String getMail () {
		return mail;
	}

	public String getManager () {
		return manager;
	}

	public String getTelephoneNumber () {
		return telephoneNumber;
	}

	public String getTitle () {
		return title;
	}

	ObjectMapper jacksonMapper2 = new ObjectMapper();
	ObjectNode attibutesJson = null;

	@JsonIgnore
	public ObjectNode getAttibutesJson () {
		return attibutesJson;
	}

	public void setAttibutesJson ( ObjectNode attibutesJson ) {
		this.attibutesJson = attibutesJson;
	}

	@Override
	public Object mapFromContext ( Object ctx ) {
		attibutesJson = jacksonMapper2.createObjectNode();

		DirContextAdapter context = (DirContextAdapter) ctx;

		// StringBuilder builder = new StringBuilder("\n LDAP attributes: ");
		Attributes attributes = context.getAttributes();
		for ( String id : Collections.list( attributes.getIDs() ) ) {
			Attribute attribute = attributes.get( id );
			// builder.append("\n" + StringUtils.leftPad(id, 20) + "\t size: " +
			// attribute.size());

			ArrayNode itemJSON = attibutesJson.putArray( id );
			try {
				for ( Object val : Collections.list( attribute.getAll() ) ) {

					if ( !id.equals( "memberOf" ) ) {
						itemJSON.add( val.toString() );
					} else {
						DistinguishedName dn = new DistinguishedName( val.toString() );
						// logger.info(dn);
						itemJSON.add( dn.getValue( "cn" ) );
					}
				}
			} catch (NamingException e) {
				logger.error( "Failed to get item", e );
			}

		}
		// logger.info(builder.toString());
		setUserid( context.getStringAttribute( "userid" ) );
		setEmployeeType( context.getStringAttribute( "employeeType" ) );
		setFullName( context.getStringAttribute( "cn" ) );
		setTelephoneNumber( context.getStringAttribute( "telephoneNumber" ) );
		// setMemberOf(context.getStringAttributes("memberOf"));
		setMemberOf( context.getStringAttributes( "memberOf" ) );

		// logger.info("========================> List:" + Arrays.asList(
		// context.getObjectAttributes("memberOf")).toString() );
		setMail( context.getStringAttribute( "mail" ) );
		try {
			DistinguishedName managerDn = new DistinguishedName(
				context.getStringAttribute( "manager" ) );
			setManager( managerDn.getValue( "uid" ) );
		} catch (Exception e) {
			// Ignodre
		}
		setTitle( context.getStringAttribute( "title" ) );
		setLocation( context.getStringAttribute( "l" ) + ", " + context.getStringAttribute( "st" )
				+ ", " + context.getStringAttribute( "co" ) );
		// setLastName(context.getStringAttribute("sn"));

		return this;
	}

	String[] memberOf = new String[ 0 ];
	String[] memberRoles = new String[ 0 ]; // used by Spring security

	public String[] getMemberOf () {
		return memberOf;
	}

	public boolean isMemberOf ( String group ) {
		return Arrays.asList( getMemberOf() ).contains( group );
	}

	private void setMemberOf ( String[] inputAttributes ) {

		String[] stringAttributes = inputAttributes;

		if ( stringAttributes == null ) {
			stringAttributes = new String[] { "cn=NoMemberships" };
		}
		memberOf = new String[ stringAttributes.length ];
		memberRoles = new String[ stringAttributes.length ];

		for ( int i = 0; i < stringAttributes.length; i++ ) {
			DistinguishedName dn = new DistinguishedName( stringAttributes[i] );
			memberOf[i] = dn.getValue( "cn" );
			memberRoles[i] = dn.getValue( "cn" );
			;
		}

	}

	/**
	 * We use the same role management as UI, which prefixes roles as noted
	 * 
	 * Spring security prefixes "ROLE_" and switches to uppercase. Group params
	 * MUST be passed in this way
	 * 
	 * @param groupNameWithRole
	 * @return
	 */
	public boolean isAuthorized ( String groupNameWithRole ) {

		if ( groupNameWithRole.equalsIgnoreCase( "ROLE_AUTHENTICATED" ) )
			return true;

		// Spring Security does not use groups directory. Instead it shifts to
		// uppercase and prepends role.

		for ( String role : memberRoles ) {
			String springSecurityRole = "ROLE_" + role.toUpperCase();
			if ( springSecurityRole.equals( groupNameWithRole ) ) {
				return true;
			}
		}

		return false;
	}

	// This keeps calls efficient with minimal attributes required
	public final static String[] PRIMARY_ATTRIBUTES = { "employeeType", "manager", "userid", "mail", "cn",
			"title", "telephoneNumber", "l", "st", "co", "memberOf" };

	public void setMail ( String mail ) {
		this.mail = mail;
	}

	public void setManager ( String manager ) {
		this.manager = manager;
	}

	public void setTelephoneNumber ( String telephoneNumber ) {
		this.telephoneNumber = telephoneNumber;
	}

	public void setTitle ( String title ) {
		this.title = title;
	}

	public String getUserid () {
		return userid;
	}

	public void setUserid ( String userid ) {
		this.userid = userid;
	}

	public String getEmployeeType () {
		return employeeType;
	}

	public void setEmployeeType ( String employeeType ) {
		this.employeeType = employeeType;
	}

	public String userid;
	public String employeeType;

	@Override
	public String toString () {
		return "CsapUser [userid=" + userid + ", employeeType=" + employeeType + ", mail=" + mail
				+ ", title=" + title
				+ ", manager=" + manager + ", telephoneNumber=" + telephoneNumber
				+ ", fullName=" + fullName
				+ ", location: " + location
				+ ", memberOf: " + Arrays.asList( getMemberOf() ).toString() + "]";
	}

}
