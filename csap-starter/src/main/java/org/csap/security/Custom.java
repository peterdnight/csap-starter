package org.csap.security;

import java.util.ArrayList;
import java.util.Collection;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;

public class Custom implements GrantedAuthoritiesMapper {
	

	private static final Log logger = LogFactory
			.getLog(Custom.class);

	@Override
	public Collection<? extends GrantedAuthority> mapAuthorities(
			Collection<? extends GrantedAuthority> authorities) {
		// TODO Auto-generated method stub
		StringBuilder builder = new StringBuilder("Authorities mapped: ") ;
		
		Collection<GrantedAuthority> ga = new ArrayList<GrantedAuthority>() ;
		int i=0 ;
		for (GrantedAuthority grantedAuthority : authorities) {
			builder.append( grantedAuthority.toString() ) ;
			 builder.append(", \t") ;
			if ( i++ > 6) { 
				builder.append("\n") ;
				i=0;
			}
			ga.add(grantedAuthority) ;
		}
		ga.add( new SimpleGrantedAuthority( "ROLE_AUTHENTICATED"));
		
		if ( logger.isDebugEnabled() )
			logger.debug(builder.toString()); ;
		return ga;
	}

}
