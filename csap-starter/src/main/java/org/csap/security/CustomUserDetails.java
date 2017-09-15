package org.csap.security;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.naming.directory.Attributes;

import org.springframework.ldap.core.DirContextAdapter;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.security.ldap.LdapUtils;
import org.springframework.security.ldap.userdetails.LdapUserDetails;
import org.springframework.security.ldap.userdetails.LdapUserDetailsImpl;
import org.springframework.util.Assert;

/**
 * List of attributes is availabe using your ldap browser
 * 
 * @author pnightin
 *
 */
public class CustomUserDetails  extends LdapUserDetailsImpl {

	/**
	 * 
	 */
	private static final long serialVersionUID = 7009791352292197412L;
	private String usersHost;
    private String mail;
    private String description;
    private String telephoneNumber;
    private List<String> cn = new ArrayList<String>();
    private Attributes allAttributesInConfigFile = null;

    protected CustomUserDetails() {
    }
    public String getUsersHost() {
		return usersHost;
	}


	public String getMail() {
		return mail;
	}


	public Attributes getAllAttributesInConfigFile() {
		return allAttributesInConfigFile;
	}


    public String[] getCn() {
        return cn.toArray(new String[cn.size()]);
    }

    public String getDescription() {
        return description;
    }

    public String getTelephoneNumber() {
        return telephoneNumber;
    }

    protected void populateContext(DirContextAdapter adapter) {
        adapter.setAttributeValue("usersHost", getUsersHost());
        adapter.setAttributeValue("mail", getMail());
        adapter.setAttributeValues("cn", getCn());
        adapter.setAttributeValue("description", getDescription());
        adapter.setAttributeValue("telephoneNumber", getTelephoneNumber());
        

        if(getPassword() != null) {
            adapter.setAttributeValue("userPassword", getPassword());
        } 
        adapter.setAttributeValues("objectclass", new String[] {"top", "person"});
        this.populateContext(adapter) ;
    }

    public static class Essence extends LdapUserDetailsImpl.Essence {

        public Essence() {
        }

        public Essence(DirContextOperations ctx) {
            super(ctx);
            setCn(ctx.getStringAttributes("cn"));
            setUsersHost(ctx.getStringAttribute("usersHosts"));
            setMail(ctx.getStringAttribute("mail"));
            setDescription(ctx.getStringAttribute("description"));
            setTelephoneNumber(ctx.getStringAttribute("telephoneNumber"));
            Object passo = ctx.getObjectAttribute("userPassword");

            if(passo != null) {
                String password = LdapUtils.convertPasswordToString(passo);
                setPassword(password);
            } 
            // Here is some magic. Password is not in attributes by policy. Instead we use an equally hard
            // to guess attribute.
            setPassword(ctx.getStringAttribute("pwdLastSet")) ;
            setAllAttributes( ctx.getAttributes() ) ;
        }

        public Essence(CustomUserDetails copyMe) {
            super(copyMe);
            setMail(copyMe.mail);
            setUsersHost(copyMe.usersHost);
            setDescription(copyMe.getDescription());
            setTelephoneNumber(copyMe.getTelephoneNumber());
            setAllAttributes(copyMe.getAllAttributesInConfigFile());
            ((CustomUserDetails) instance).cn = new ArrayList<String>(copyMe.cn);
        }

        protected LdapUserDetailsImpl createTarget() {
            return new CustomUserDetails();
        }

        public void setMail(String mail) {
            ((CustomUserDetails) instance).mail = mail;
        }
        
        public void setUsersHost(String usersHost) {
            ((CustomUserDetails) instance).usersHost = usersHost;
        }
        
        public void setAllAttributes(Attributes attr) {
            ((CustomUserDetails) instance).allAttributesInConfigFile = attr;
        }

        public void setCn(String[] cn) {
            ((CustomUserDetails) instance).cn = Arrays.asList(cn);
        }

        public void addCn(String value) {
            ((CustomUserDetails) instance).cn.add(value);
        }

        public void setTelephoneNumber(String tel) {
            ((CustomUserDetails) instance).telephoneNumber = tel;
        }

        public void setDescription(String desc) {
            ((CustomUserDetails) instance).description = desc;
        }

        public LdapUserDetails createUserDetails() {
        	CustomUserDetails p = (CustomUserDetails) super.createUserDetails();
            //Assert.hasLength(p.sn);
            Assert.notNull(p.cn);
            Assert.notEmpty(p.cn);
            // TODO: Check contents for null entries
            return p;
        }
    }
    
}


