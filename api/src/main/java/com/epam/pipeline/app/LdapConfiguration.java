package com.epam.pipeline.app;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ldap.core.ContextSource;
import org.springframework.ldap.core.LdapTemplate;

@Configuration
@RequiredArgsConstructor
public class LdapConfiguration {
    @Bean
    public LdapTemplate ldapTemplate(final ContextSource contextSource) {
        return new LdapTemplate(contextSource);
    }
}
