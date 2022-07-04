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

import com.epam.pipeline.entity.ldap.LdapBlockedUserSearchMethod;
import com.epam.pipeline.entity.ldap.LdapEntity;
import com.epam.pipeline.entity.ldap.LdapEntityType;
import com.epam.pipeline.entity.ldap.LdapSearchRequest;
import com.epam.pipeline.entity.ldap.LdapSearchResponse;
import com.epam.pipeline.entity.ldap.LdapSearchResponseType;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.google.common.collect.Lists;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("PMD.AvoidCatchingGenericException")
public class LdapBlockedUsersManager {
    private static final String USER_LIST = "${USERS_LIST}";
    private static final String TODAY = "${TODAY}";
    private static final LocalDateTime LDAP_MIN_DATE_TIME = LocalDateTime.of(1601, 1, 1,
            0, 0, 0, 0);
    private static final int MILLS_TO_100_NANOS = 10000;

    private final LdapManager ldapManager;
    private final PreferenceManager preferenceManager;

    /**
     * This method filters out LDAP non blocked users from source users list
     * @param users source users
     * @return returns LDAP blocked users only
     */
    public List<PipelineUser> filterBlockedUsers(final List<PipelineUser> users) {
        final Integer pageSize = preferenceManager.getPreference(SystemPreferences.LDAP_BLOCKED_USERS_FILTER_PAGE_SIZE);
        final String filterTemplate = preferenceManager.getPreference(SystemPreferences.LDAP_BLOCKED_USER_FILTER);
        final String nameAttribute = preferenceManager.getPreference(
                SystemPreferences.LDAP_BLOCKED_USER_NAME_ATTRIBUTE);
        final LdapBlockedUserSearchMethod ldapSearchMethod = chooseLdapSearchMethod();

        log.debug(String.format("LDAP search method: %s, filterTemplate: %s, nameAttribute: %s",
                ldapSearchMethod.name(), filterTemplate, nameAttribute));

        final List<List<PipelineUser>> userPatches = Lists.partition(users, pageSize);
        return userPatches.stream()
                .flatMap(patch -> findLdapBlockedUsers(patch, ldapSearchMethod, filterTemplate, nameAttribute).stream())
                .collect(Collectors.toList());
    }

    private List<PipelineUser> findLdapBlockedUsers(final List<PipelineUser> patch,
                                                    final LdapBlockedUserSearchMethod searchMethod,
                                                    final String filterTemplate,
                                                    final String nameAttribute) {
        final String filter = buildFilter(filterTemplate, patch, nameAttribute);

        final Set<String> userNamesFromLdap = ListUtils.emptyIfNull(
                queryLdap(patch.size(), filter, nameAttribute)
                ).stream()
                .filter(Objects::nonNull)
                .map(LdapEntity::getName)
                .filter(StringUtils::isNotBlank)
                .map(StringUtils::upperCase)
                .collect(Collectors.toSet());

        final Map<String, PipelineUser> usersByName = patch.stream()
                .collect(Collectors.toMap(user -> StringUtils.upperCase(user.getUserName()),
                        Function.identity()));

        switch (searchMethod) {
            case LOAD_BLOCKED:
                return userNamesFromLdap.stream()
                        .filter(usersByName::containsKey)
                        .map(usersByName::get)
                        .collect(Collectors.toList());
            case LOAD_ACTIVE_AND_INTERCEPT:
                return usersByName.entrySet().stream()
                        .filter(pu -> !userNamesFromLdap.contains(pu.getKey()))
                        .map(Map.Entry::getValue).collect(Collectors.toList());
            default:
                throw new IllegalArgumentException("Unsupported search method: " + searchMethod.name());
        }
    }

    private LdapBlockedUserSearchMethod chooseLdapSearchMethod() {
        final String ldapSearchMethodPref = preferenceManager.getPreference(
                SystemPreferences.LDAP_BLOCKED_USER_SEARCH_METHOD
        );
        return StringUtils.isEmpty(ldapSearchMethodPref)
                ? LdapBlockedUserSearchMethod.LOAD_BLOCKED
                : LdapBlockedUserSearchMethod.valueOf(ldapSearchMethodPref);
    }

    private String buildLdapTodayFilter() {
        return String.valueOf(ldapTimestamp());
    }

    /**
     * The LDAP timestamp is the number of 100-nanosecond intervals since Jan 1, 1601 UTC.
     * @return 18 digit LDAP timestamp
     */
    private long ldapTimestamp() {
        final Duration duration = Duration.between(LDAP_MIN_DATE_TIME, DateUtils.nowUTC());
        return duration.toMillis() * MILLS_TO_100_NANOS;
    }

    private String buildUserListFilter(final List<PipelineUser> patch, final String nameAttribute) {
        return String.format("(|%s)", ListUtils.emptyIfNull(patch).stream()
                .map(PipelineUser::getUserName)
                .map(userName -> String.format("(%s=%s)", nameAttribute, userName))
                .collect(Collectors.joining()));
    }

    private String buildFilter(final String template, final List<PipelineUser> users,
                               final String nameAttribute) {
        return StringUtils.trim(template
                .replace(USER_LIST, buildUserListFilter(users, nameAttribute))
                .replace(TODAY, buildLdapTodayFilter()));
    }

    private List<LdapEntity> queryLdap(final Integer pageSize, final String filter, final String nameAttribute) {
        final LdapSearchRequest request = LdapSearchRequest.builder()
                .size(pageSize)
                .type(LdapEntityType.USER)
                .nameAttribute(nameAttribute)
                .build();
        final LdapSearchResponse ldapSearchResponse = queryLdap(request, filter);
        if (Objects.isNull(ldapSearchResponse)
                || LdapSearchResponseType.TIMED_OUT.equals(ldapSearchResponse.getType())) {
            log.debug("LDAP response was not received");
            return Collections.emptyList();
        }
        return ldapSearchResponse.getEntities();
    }

    private LdapSearchResponse queryLdap(final LdapSearchRequest request, final String filter) {
        try {
            return ldapManager.search(request, filter);
        } catch (Exception e) {
            log.warn("LDAP request failed.", e);
            return null;
        }
    }
}
