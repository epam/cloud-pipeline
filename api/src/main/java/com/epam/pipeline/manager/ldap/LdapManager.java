package com.epam.pipeline.manager.ldap;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.ldap.LdapEntity;
import com.epam.pipeline.entity.ldap.LdapEntityType;
import com.epam.pipeline.entity.ldap.LdapSearchRequest;
import com.epam.pipeline.entity.ldap.LdapSearchResponse;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ldap.TimeLimitExceededException;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.query.LdapQuery;
import org.springframework.ldap.query.LdapQueryBuilder;
import org.springframework.ldap.query.SearchScope;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class LdapManager {

    private static final String FALLBACK_BASE_PATH = "";
    private static final String FALLBACK_USER_FILTER = "(&(objectClass=person)(cn=*%s*))";
    private static final String FALLBACK_GROUP_FILTER = "(&(objectClass=group)(cn=*%s*))";
    private static final String[] FALLBACK_ENTITY_ATTRIBUTES = null;
    private static final int FALLBACK_RESPONSE_SIZE = 10;
    private static final int FALLBACK_RESPONSE_TIMEOUT = 60000;

    private final LdapTemplate ldapTemplate;
    private final LdapEntityMapper ldapEntityMapper;
    private final PreferenceManager preferenceManager;
    private final MessageHelper messageHelper;

    /**
     * Searches for entities in the configured LDAP servers by the given request.
     */
    public LdapSearchResponse search(final LdapSearchRequest request) {
        Assert.notNull(request.getType(), messageHelper.getMessage(MessageConstants.ERROR_LDAP_SEARCH_TYPE_MISSING));
        final int size = responseSize();
        try {
            return truncated(search(request, size), size);
        } catch (TimeLimitExceededException e) {
            log.warn(String.format("Time limit was exceeded during LDAP search request %s", request), e);
            return timedOut();
        }
    }

    private List<LdapEntity> search(final LdapSearchRequest request, final int size) {
        return search(queryFor(request, size), mapperFor(request));
    }

    private List<LdapEntity> search(final LdapQuery query, final AttributesMapper<LdapEntity> mapper) {
        return ldapTemplate.search(query, mapper);
    }

    private LdapQuery queryFor(final LdapSearchRequest request, final int size) {
        return LdapQueryBuilder.query()
                .base(basePath())
                .searchScope(SearchScope.SUBTREE)
                .countLimit(size)
                .timeLimit(responseTimeout())
                .attributes(attributes())
                .filter(filterFor(request));
    }

    private String filterFor(final LdapSearchRequest request) {
        return String.format(filterFor(request.getType()), StringUtils.trimToEmpty(request.getQuery()));
    }

    public String filterFor(final LdapEntityType type) {
        switch (type) {
            case USER:
                return userFilter();
            case GROUP:
                return groupFilter();
            default:
                throw new RuntimeException(String.format("Unrecognized entity type %s", type));
        }
    }

    private AttributesMapper<LdapEntity> mapperFor(final LdapSearchRequest request) {
        return attributes -> ldapEntityMapper.map(attributes, request.getType());
    }

    private LdapSearchResponse truncated(final List<LdapEntity> entities, final int size) {
        return entities.size() >= size
                ? LdapSearchResponse.truncated(entities)
                : LdapSearchResponse.completed(entities);
    }

    private LdapSearchResponse timedOut() {
        return LdapSearchResponse.timedOut();
    }

    private String basePath() {
        return preferenceManager.findPreference(SystemPreferences.LDAP_BASE_PATH)
                .orElse(FALLBACK_BASE_PATH);
    }

    private String[] attributes() {
        return preferenceManager.findPreference(SystemPreferences.LDAP_ENTITY_ATTRIBUTES)
                .filter(StringUtils::isNotBlank)
                .map(attributes -> attributes.split(","))
                .orElse(FALLBACK_ENTITY_ATTRIBUTES);
    }

    private int responseSize() {
        return preferenceManager.findPreference(SystemPreferences.LDAP_RESPONSE_SIZE)
                .orElse(FALLBACK_RESPONSE_SIZE);
    }

    private int responseTimeout() {
        return preferenceManager.findPreference(SystemPreferences.LDAP_RESPONSE_TIMEOUT)
                .orElse(FALLBACK_RESPONSE_TIMEOUT);
    }

    private String userFilter() {
        return preferenceManager.findPreference(SystemPreferences.LDAP_USER_FILTER)
                .orElse(FALLBACK_USER_FILTER);
    }

    private String groupFilter() {
        return preferenceManager.findPreference(SystemPreferences.LDAP_GROUP_FILTER)
                .orElse(FALLBACK_GROUP_FILTER);
    }
}
