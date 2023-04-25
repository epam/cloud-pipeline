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
import com.epam.pipeline.utils.StreamUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("PMD.AvoidCatchingGenericException")
public class LdapBlockedUsersManager {

    private static final String USER_LIST = "${USERS_LIST}";
    private static final String TODAY = "${TODAY}";
    private static final LocalDateTime LDAP_MIN_DATE_TIME = LocalDateTime.of(1601, 1, 1, 0, 0, 0, 0);
    private static final int MILLS_TO_100_NANOS = 10000;

    private final LdapManager ldapManager;
    private final PreferenceManager preferenceManager;

    /**
     * This method filters out LDAP non-blocked users from source users list
     *
     * @param users source users
     * @return returns LDAP blocked users only
     */
    public List<PipelineUser> filterBlockedUsers(final List<PipelineUser> users) {
        final Integer pageSize = preferenceManager.getPreference(SystemPreferences.LDAP_BLOCKED_USERS_FILTER_PAGE_SIZE);
        final String filterTemplate = preferenceManager.getPreference(SystemPreferences.LDAP_BLOCKED_USER_FILTER);
        final String nameAttribute = preferenceManager.getPreference(
                SystemPreferences.LDAP_BLOCKED_USER_NAME_ATTRIBUTE);
        final LdapBlockedUserSearchMethod method = getLdapSearchMethod();

        log.debug("Querying LDAP users ({}) using {} method...", users.size(), method.name());
        final List<PipelineUser> blockedUsers = StreamUtils.chunked(users.stream(), pageSize)
                .map(patch -> findLdapBlockedUsers(patch, method, filterTemplate, nameAttribute))
                .flatMap(Collection::stream)
                .collect(Collectors.toList());

        if (blockedUsers.isEmpty()) {
            return Collections.emptyList();
        }

        log.info("Detected blocked LDAP users ({}): {}", blockedUsers.size(), getUserNamesString(blockedUsers));
        return blockedUsers;
    }

    // We need to make sure, when configure SystemPreference, that we are consistent between `searchMethod`
    // and `filterTemplate`. e.g. if we have LOAD_ACTIVE_AND_INTERCEPT then we also should have `filterTemplate`
    // that actually will search users that are active.
    private List<PipelineUser> findLdapBlockedUsers(final List<PipelineUser> patch,
                                                    final LdapBlockedUserSearchMethod method,
                                                    final String filterTemplate,
                                                    final String nameAttribute) {
        final String filter = buildFilter(filterTemplate, patch, nameAttribute);
        final List<LdapEntity> entities = queryLdap(patch.size(), filter, nameAttribute);

        if (entities.isEmpty() && method == LdapBlockedUserSearchMethod.LOAD_ACTIVE_AND_INTERCEPT) {
            log.warn("Skipping LDAP users chunk ({}) processing because all entities seem to be blocked " +
                    "with empty LDAP response...", patch.size());
            log.warn("Considering LDAP users chunk as unblocked: {}...", getUserNamesString(patch));
            return Collections.emptyList();
        }

        if (entities.size() == patch.size() && method == LdapBlockedUserSearchMethod.LOAD_BLOCKED) {
            log.warn("Skipping LDAP users chunk ({}) processing because all entities seem to be blocked " +
                    "with {} entities LDAP response...", patch.size(), entities.size());
            log.warn("Considering LDAP users chunk as unblocked: {}...", getUserNamesString(patch));
            return Collections.emptyList();
        }

        final List<LdapEntity> invalidEntities = resolveInvalidEntities(entities);
        if (!invalidEntities.isEmpty()) {
            log.warn("Skipping LDAP users chunk processing because LDAP response has invalid entries ({})...",
                    getEntityNamesString(invalidEntities));
            log.warn("Considering LDAP users chunk as unblocked: {}...", getUserNamesString(patch));
            return Collections.emptyList();
        }

        return resolveBlockedUsers(patch, method, entities);
    }

    private List<PipelineUser> resolveBlockedUsers(final List<PipelineUser> patch,
                                                   final LdapBlockedUserSearchMethod method,
                                                   final List<LdapEntity> entities) {
        final Set<String> entityNames = getEntityNames(entities);
        return patch.stream()
                .filter(user -> matches(user, entityNames, method))
                .collect(Collectors.toList());
    }

    private Set<String> getEntityNames(final List<LdapEntity> entities) {
        return entities.stream()
                .map(LdapEntity::getName)
                .filter(StringUtils::isNotBlank)
                .map(StringUtils::upperCase)
                .collect(Collectors.toSet());
    }

    private boolean matches(final PipelineUser user, final Set<String> names,
                            final LdapBlockedUserSearchMethod method) {
        switch (method) {
            case LOAD_BLOCKED:
                return names.contains(StringUtils.upperCase(user.getUserName()));
            case LOAD_ACTIVE_AND_INTERCEPT:
                return !names.contains(StringUtils.upperCase(user.getUserName()));
            default:
                throw new IllegalArgumentException("Unsupported search method: " + method.name());
        }
    }

    private List<LdapEntity> resolveInvalidEntities(final List<LdapEntity> entities) {
        final Set<String> invalidEntityNames = getLdapInvalidEntityNames();
        return entities.stream()
                .filter(entity -> invalidEntityNames.contains(StringUtils.upperCase(entity.getName())))
                .collect(Collectors.toList());
    }

    private Set<String> getLdapInvalidEntityNames() {
        return Optional.of(SystemPreferences.LDAP_INVALID_USER_ENTRIES)
                .map(preferenceManager::getPreference)
                .orElseGet(Collections::emptySet)
                .stream()
                .map(StringUtils::upperCase)
                .collect(Collectors.toSet());
    }

    private LdapBlockedUserSearchMethod getLdapSearchMethod() {
        return Optional.of(SystemPreferences.LDAP_BLOCKED_USER_SEARCH_METHOD)
                .map(preferenceManager::getPreference)
                .filter(StringUtils::isNotBlank)
                .map(LdapBlockedUserSearchMethod::valueOf)
                .orElse(LdapBlockedUserSearchMethod.LOAD_BLOCKED);
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
        final Optional<LdapSearchResponse> response = queryLdap(request, filter);
        final LdapSearchResponseType responseType = response
                .map(LdapSearchResponse::getType)
                .orElse(LdapSearchResponseType.TIMED_OUT);
        switch (responseType) {
            case COMPLETED:
                log.debug("LDAP response has been received successfully.");
                return response.map(LdapSearchResponse::getEntities)
                        .map(Collection::stream)
                        .orElseGet(Stream::empty)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
            case TRUNCATED:
                log.warn("LDAP response has been received and it was truncated.");
                return Collections.emptyList();
            case TIMED_OUT:
            default:
                log.warn("LDAP response has not been received.");
                return Collections.emptyList();
        }
    }

    private Optional<LdapSearchResponse> queryLdap(final LdapSearchRequest request, final String filter) {
        try {
            log.debug("Querying LDAP using filter {}...", filter);
            final LdapSearchResponse response = ldapManager.search(request, filter);
            log.debug("Received LDAP response {}.", response);
            return Optional.ofNullable(response);
        } catch (Exception e) {
            log.warn("Failed to query LDAP.", e);
            return Optional.empty();
        }
    }

    private String getUserNamesString(final List<PipelineUser> users) {
        return users.stream().map(PipelineUser::getUserName).collect(Collectors.joining(", "));
    }

    private String getEntityNamesString(final List<LdapEntity> invalidEntities) {
        return invalidEntities.stream().map(LdapEntity::getName).collect(Collectors.joining(", "));
    }
}
