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
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import static com.epam.pipeline.test.creator.user.UserCreatorUtils.getPipelineUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class LdapBlockedUsersManagerTest {
    private static final int PAGE_SIZE = 2;
    private static final String NAME_ATTRIBUTE = "nameAttribute";
    private static final String USER_NAME_1 = "name1";
    private static final String USER_NAME_2 = "name2";
    private static final String USER_NAME_3 = "name3";
    private static final Long ID_1 = 1L;
    private static final Long ID_2 = 2L;
    private static final Long ID_3 = 3L;

    private final LdapManager ldapManager = mock(LdapManager.class);
    private final PreferenceManager preferenceManager = mock(PreferenceManager.class);
    private final LdapBlockedUsersManager blockedUsersManager = new LdapBlockedUsersManager(ldapManager,
            preferenceManager);

    @Test
    public void shouldReturnBlockedUsersOnly() {
        final PipelineUser blockedUser = user(USER_NAME_1, ID_1);
        final PipelineUser user1 = user(USER_NAME_2, ID_2);
        final PipelineUser user2 = user(USER_NAME_3, ID_3);

        doReturn(PAGE_SIZE).when(preferenceManager).getPreference(
                SystemPreferences.LDAP_BLOCKED_USERS_FILTER_PAGE_SIZE);
        doReturn("${USERS_LIST}").when(preferenceManager).getPreference(
                SystemPreferences.LDAP_BLOCKED_USER_FILTER);
        doReturn(NAME_ATTRIBUTE).when(preferenceManager).getPreference(
                SystemPreferences.LDAP_BLOCKED_USER_NAME_ATTRIBUTE);
        final String expectedFilter1 = "(|(nameAttribute=name1)(nameAttribute=name2))";
        final String expectedFilter2 = "(|(nameAttribute=name3))";
        final LdapSearchRequest request1 = LdapSearchRequest.builder()
                .size(PAGE_SIZE)
                .type(LdapEntityType.USER)
                .nameAttribute(NAME_ATTRIBUTE)
                .build();
        final LdapSearchRequest request2 = LdapSearchRequest.builder()
                .size(1) // since only one user remains
                .type(LdapEntityType.USER)
                .nameAttribute(NAME_ATTRIBUTE)
                .build();
        doReturn(ldapResponse(USER_NAME_1.toUpperCase(Locale.ROOT))).when(ldapManager)
                .search(request1, expectedFilter1);
        doReturn(emptyLdapResponse()).when(ldapManager).search(request2, expectedFilter2);

        final List<PipelineUser> result = blockedUsersManager.filterBlockedUsers(
                Arrays.asList(blockedUser, user1, user2));

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo(blockedUser);

        verify(ldapManager).search(request1, expectedFilter1);
        verify(ldapManager).search(request2, expectedFilter2);
    }

    @Test
    public void shouldReturnBlockedUsersOnlyWhenSearchMethodIsFindActiveAndIntersect() {
        final PipelineUser user1 = user(USER_NAME_1, ID_1);
        final PipelineUser blockedUser = user(USER_NAME_2, ID_2);
        final PipelineUser user3 = user(USER_NAME_3, ID_3);

        doReturn(PAGE_SIZE).when(preferenceManager).getPreference(
                SystemPreferences.LDAP_BLOCKED_USERS_FILTER_PAGE_SIZE);
        doReturn(LdapBlockedUserSearchMethod.LOAD_ACTIVE_AND_INTERCEPT.name())
                .when(preferenceManager).getPreference(SystemPreferences.LDAP_BLOCKED_USER_SEARCH_METHOD);
        doReturn("${USERS_LIST}").when(preferenceManager).getPreference(
                SystemPreferences.LDAP_BLOCKED_USER_FILTER);
        doReturn(NAME_ATTRIBUTE).when(preferenceManager).getPreference(
                SystemPreferences.LDAP_BLOCKED_USER_NAME_ATTRIBUTE);
        final String expectedFilter1 = "(|(nameAttribute=name1)(nameAttribute=name2))";
        final String expectedFilter2 = "(|(nameAttribute=name3))";
        final LdapSearchRequest request1 = LdapSearchRequest.builder()
                .size(PAGE_SIZE)
                .type(LdapEntityType.USER)
                .nameAttribute(NAME_ATTRIBUTE)
                .build();
        final LdapSearchRequest request2 = LdapSearchRequest.builder()
                .size(1) // since only one user remains
                .type(LdapEntityType.USER)
                .nameAttribute(NAME_ATTRIBUTE)
                .build();
        doReturn(ldapResponse(USER_NAME_1.toUpperCase(Locale.ROOT)))
                .when(ldapManager).search(request1, expectedFilter1);
        doReturn(ldapResponse(USER_NAME_3.toUpperCase(Locale.ROOT)))
                .when(ldapManager).search(request2, expectedFilter2);

        final List<PipelineUser> result = blockedUsersManager.filterBlockedUsers(
                Arrays.asList(user1, blockedUser, user3));

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo(blockedUser);

        verify(ldapManager).search(request1, expectedFilter1);
        verify(ldapManager).search(request2, expectedFilter2);
    }

    private PipelineUser user(final String userName, final Long id) {
        final PipelineUser pipelineUser = getPipelineUser(userName, id);
        pipelineUser.setBlocked(false);
        pipelineUser.setLastLoginDate(LocalDateTime.now());
        return pipelineUser;
    }

    private LdapSearchResponse ldapResponse(final String... userNames) {
        List<LdapEntity> result = Arrays.stream(userNames)
                .map(user -> new LdapEntity(user, LdapEntityType.USER, null)).collect(Collectors.toList());
        return LdapSearchResponse.completed(result);
    }

    private LdapSearchResponse emptyLdapResponse() {
        return LdapSearchResponse.completed(Collections.emptyList());
    }
}
