/*
 * Copyright 2007-2013 The Europeana Foundation
 *
 *  Licenced under the EUPL, Version 1.1 (the "Licence") and subsequent versions as approved
 *  by the European Commission;
 *  You may not use this work except in compliance with the Licence.
 *
 *  You may obtain a copy of the Licence at:
 *  http://joinup.ec.europa.eu/software/page/eupl
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under
 *  the Licence is distributed on an "AS IS" basis, without warranties or conditions of
 *  any kind, either express or implied.
 *  See the Licence for the specific language governing permissions and limitations under
 *  the Licence.
 */
package eu.europeana.metis.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.security.ldap.DefaultLdapUsernameToDnMapper;
import org.springframework.security.ldap.userdetails.InetOrgPersonContextMapper;
import org.springframework.security.ldap.userdetails.LdapUserDetailsManager;

/**
 * The configuration is for Metis User authentication properties.
 * @author alena
 *
 */
@Configuration
@PropertySource("classpath:/authentication.properties")
public class MetisLdapManagerConfig {
	@Value("${ldif.url}")
	private String url;
	@Value("${ldif.dn}")
	private String dn;
	@Value("${ldif.pwd}")
	private String pwd;
	@Value("${ldif.base}")
	private String base;
	@Value("${ldif.clean}")
	private String clean;
	@Value("${ldap.url}")
	private String ldapUrl;
	@Value("${ldap.manager.dn}")
	private String ldapManagerDn;
	@Value("${ldap.manager.pwd}")
	private String ldapPwd;
	@Value("${ldap.manager.base.dn}")
	private String ldapBaseDn;

	@Bean
	public LdapContextSource contextSource() {
		LdapContextSource ldapContextSource = new LdapContextSource();
		ldapContextSource.setUrl(ldapUrl);
		ldapContextSource.setUserDn(ldapManagerDn);
		ldapContextSource.setPassword(ldapPwd);
		return ldapContextSource;
	}
	
	@Bean
	public LdapTemplate ldapTemplate() {
	    return new LdapTemplate(contextSource());
	}

	@Bean
	public InetOrgPersonContextMapper inetOrgPersonContextMapper() {
	    return new InetOrgPersonContextMapper();
	}
	
	@Bean
	public DefaultLdapUsernameToDnMapper defaultLdapUsernameToDnMapper() {
	    return new DefaultLdapUsernameToDnMapper("ou=users", "uid"); // "uid"
	}

	@Bean
	public LdapUserDetailsManager ldapUserDetailsManager() {
	    LdapUserDetailsManager userManager = new LdapUserDetailsManager(contextSource());
	    userManager.setGroupSearchBase("ou=roles,ou=metis_authentication");
	    userManager.setUserDetailsMapper(inetOrgPersonContextMapper());
	    userManager.setUsernameMapper(defaultLdapUsernameToDnMapper());
	    userManager.setGroupRoleAttributeName("cn");
	    userManager.setGroupMemberAttributeName("member");
	    return userManager;
	}

	@Bean
	public static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
		return new PropertySourcesPlaceholderConfigurer();
	}
}