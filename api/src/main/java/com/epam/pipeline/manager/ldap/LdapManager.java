/*
 * Copyright 2021 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.manager.ldap;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.ldap.LdapEntity;
import com.epam.pipeline.entity.ldap.LdapEntityType;
import com.epam.pipeline.entity.ldap.LdapSearchRequest;
import com.epam.pipeline.entity.ldap.LdapSearchResponse;
import com.epam.pipeline.exception.ldap.LdapException;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ldap.TimeLimitExceededException;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.query.LdapQuery;
import org.springframework.ldap.query.LdapQueryBuilder;
import org.springframework.ldap.query.SearchScope;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

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
        return search(request, defaultFilter(request));
    }

    public LdapSearchResponse search(final LdapSearchRequest request, final String filter) {
        Assert.notNull(request.getType(), messageHelper.getMessage(MessageConstants.ERROR_LDAP_SEARCH_TYPE_MISSING));
        if (Objects.isNull(request.getSize())) {
            request.setSize(responseSize());
        }

        try {
            return truncated(ldapSearch(request, filter), request.getSize());
        } catch (TimeLimitExceededException e) {
            log.warn(String.format("Time limit was exceeded during LDAP search request %s", request), e);
            return timedOut();
        }
    }

    private List<LdapEntity> ldapSearch(final LdapSearchRequest request, final String filter) {
        return ldapTemplate.search(queryFor(request, filter), mapperFor(request));
    }

    private LdapQuery queryFor(final LdapSearchRequest request, final String filter) {
        return LdapQueryBuilder.query()
                .base(basePath())
                .searchScope(SearchScope.SUBTREE)
                .countLimit(request.getSize())
                .timeLimit(responseTimeout())
                .attributes(buildAttributes(request))
                .filter(filter);
    }

    private String defaultFilter(final LdapSearchRequest request) {
        return String.format(defaultFilter(request.getType()), StringUtils.trimToEmpty(request.getQuery()));
    }

    private String defaultFilter(final LdapEntityType type) {
        switch (type) {
            case USER:
                return userFilter();
            case GROUP:
                return groupFilter();
            default:
                throw new LdapException(String.format("Unrecognized entity type %s", type));
        }
    }

    private AttributesMapper<LdapEntity> mapperFor(final LdapSearchRequest request) {
        return attributes -> ldapEntityMapper.map(attributes, request.getType(), request.getNameAttribute());
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

    private String[] defaultAttributes() {
        return preferenceManager.findPreference(SystemPreferences.LDAP_ENTITY_ATTRIBUTES)
                .filter(StringUtils::isNotBlank)
                .map(attributes -> attributes.split(","))
                .orElse(FALLBACK_ENTITY_ATTRIBUTES);
    }

    private String[] buildAttributes(final LdapSearchRequest request) {
        final Set<String> requestAttributes = new HashSet<>(ListUtils.emptyIfNull(request.getAttributes()));
        final String[] defaultAttributes = defaultAttributes();
        requestAttributes.addAll(Arrays.asList(defaultAttributes));
        if (StringUtils.isNotBlank(request.getNameAttribute())) {
            requestAttributes.add(request.getNameAttribute());
        }
        return requestAttributes.toArray(new String[]{});
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
