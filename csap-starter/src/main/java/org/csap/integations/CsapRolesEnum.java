/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.csap.integations;

/**
 *
 * @author pnightin
 */
public enum CsapRolesEnum {
		admin("@CsapSecurityConfiguration.adminGroup"),
		infra("@CsapSecurityConfiguration.infraGroup"),
		build("@CsapSecurityConfiguration.buildGroup"),
		view("@CsapSecurityConfiguration.viewGroup"), ;

	public String value;
	
	private CsapRolesEnum(String value) {
		this.value = value;
	}
}
