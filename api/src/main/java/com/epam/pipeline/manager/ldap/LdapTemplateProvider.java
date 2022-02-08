package com.epam.pipeline.manager.ldap;

import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LdapTemplateProvider {

    private static final String[] FALLBACK_URLS = new String[]{"ldap://localhost:389"};
    private static final String FALLBACK_USERNAME = "";
    private static final String FALLBACK_PASSWORD = "";

    private final PreferenceManager preferenceManager;

    public LdapTemplate get() {
        final LdapContextSource contextSource = new LdapContextSource();
        contextSource.setUrls(urls());
        contextSource.setUserDn(username());
        contextSource.setPassword(password());
        contextSource.afterPropertiesSet();
        return new LdapTemplate(contextSource);
    }

    private String[] urls() {
        return preferenceManager.findPreference(SystemPreferences.LDAP_URLS)
                .filter(StringUtils::isNotBlank)
                .map(urls -> StringUtils.split(urls, ','))
                .orElse(FALLBACK_URLS);
    }

    private String username() {
        return preferenceManager.findPreference(SystemPreferences.LDAP_USERNAME)
                .filter(StringUtils::isNotBlank)
                .orElse(FALLBACK_USERNAME);
    }

    private String password() {
        return preferenceManager.findPreference(SystemPreferences.LDAP_PASSWORD)
                .filter(StringUtils::isNotBlank)
                .orElse(FALLBACK_PASSWORD);
    }
}
