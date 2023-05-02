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
import com.epam.pipeline.manager.preference.AbstractSystemPreference;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import org.junit.Before;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import static com.epam.pipeline.test.creator.user.UserCreatorUtils.getPipelineUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class LdapBlockedUsersManagerTest {

    private static final int PAGE_SIZE = 2;
    private static final String NAME_ATTRIBUTE = "nameAttribute";
    private static final String USER_FILTER = "${USERS_LIST}";
    private static final String USER_NAME_INVALID = "Name not found";
    private static final String USER_NAME_1 = "name1";
    private static final String USER_NAME_2 = "name2";
    private static final String USER_NAME_3 = "name3";
    private static final String USER_NAME_4 = "name4";
    private static final String USER_NAME_5 = "name5";
    private static final Long ID_1 = 1L;
    private static final Long ID_2 = 2L;
    private static final Long ID_3 = 3L;
    private static final Long ID_4 = 4L;
    private static final Long ID_5 = 5L;

    private final LdapManager ldapManager = mock(LdapManager.class);
    private final PreferenceManager preferenceManager = mock(PreferenceManager.class);
    private final LdapBlockedUsersManager manager = new LdapBlockedUsersManager(ldapManager, preferenceManager);

    @Before
    public void setUp() throws Exception {
        set(SystemPreferences.LDAP_BLOCKED_USERS_FILTER_PAGE_SIZE, PAGE_SIZE);
        set(SystemPreferences.LDAP_BLOCKED_USER_SEARCH_METHOD,
                LdapBlockedUserSearchMethod.LOAD_ACTIVE_AND_INTERCEPT.name());
        set(SystemPreferences.LDAP_BLOCKED_USER_FILTER, USER_FILTER);
        set(SystemPreferences.LDAP_BLOCKED_USER_NAME_ATTRIBUTE, NAME_ATTRIBUTE);
        set(SystemPreferences.LDAP_INVALID_USER_ENTRIES, Collections.singleton(USER_NAME_INVALID));
    }

    @Test
    public void shouldReturnBlockedUsersUsingLoadBlockedMethod() {
        set(SystemPreferences.LDAP_BLOCKED_USER_SEARCH_METHOD, LdapBlockedUserSearchMethod.LOAD_BLOCKED.name());

        final PipelineUser user1 = user(USER_NAME_1, ID_1);
        final PipelineUser user2 = user(USER_NAME_2, ID_2);
        final PipelineUser user3 = user(USER_NAME_3, ID_3);
        final PipelineUser user4 = user(USER_NAME_4, ID_4);
        final PipelineUser user5 = user(USER_NAME_5, ID_5);

        doReturn(ldapResponse(user1, user2))
                .doReturn(ldapResponse(user3))
                .doReturn(ldapResponse(user5))
                .when(ldapManager).search(any(), any());

        final List<PipelineUser> result = manager.filterBlockedUsers(Arrays.asList(user1, user2, user3, user4, user5));

        assertThat(result).containsExactlyInAnyOrder(user3);
    }

    @Test
    public void filterShouldReturnBlockedUsersUsingLoadActiveMethod() {
        final PipelineUser user1 = user(USER_NAME_1, ID_1);
        final PipelineUser user2 = user(USER_NAME_2, ID_2);
        final PipelineUser user3 = user(USER_NAME_3, ID_3);
        final PipelineUser user4 = user(USER_NAME_4, ID_4);
        final PipelineUser user5 = user(USER_NAME_5, ID_5);

        doReturn(ldapResponse(user1, user2))
                .doReturn(ldapResponse(user3))
                .doReturn(ldapResponse(user5))
                .when(ldapManager).search(any(), any());

        final List<PipelineUser> result = manager.filterBlockedUsers(Arrays.asList(user1, user2, user3, user4, user5));

        assertThat(result).containsExactlyInAnyOrder(user4);
    }

    @Test
    public void filterShouldIgnorePagesWithInvalidEntitiesUsingLoadBlockedMethod() {
        set(SystemPreferences.LDAP_BLOCKED_USER_SEARCH_METHOD, LdapBlockedUserSearchMethod.LOAD_BLOCKED.name());

        final PipelineUser user1 = user(USER_NAME_1, ID_1);
        final PipelineUser user2 = user(USER_NAME_INVALID, ID_2);
        final PipelineUser user3 = user(USER_NAME_3, ID_3);
        final PipelineUser user4 = user(USER_NAME_4, ID_4);
        final PipelineUser user5 = user(USER_NAME_INVALID, ID_5);

        doReturn(ldapResponse(user1, user2))
                .doReturn(ldapResponse(user3))
                .doReturn(ldapResponse(user5))
                .when(ldapManager).search(any(), any());

        final List<PipelineUser> result = manager.filterBlockedUsers(Arrays.asList(user1, user2, user3, user4, user5));

        assertThat(result).containsExactlyInAnyOrder(user3);
    }

    @Test
    public void filterShouldIgnorePagesWithInvalidEntitiesUsingLoadActiveMethod() {
        final PipelineUser user1 = user(USER_NAME_1, ID_1);
        final PipelineUser user2 = user(USER_NAME_INVALID, ID_2);
        final PipelineUser user3 = user(USER_NAME_3, ID_3);
        final PipelineUser user4 = user(USER_NAME_4, ID_4);
        final PipelineUser user5 = user(USER_NAME_INVALID, ID_5);

        doReturn(ldapResponse(user1, user2))
                .doReturn(ldapResponse(user3))
                .doReturn(ldapResponse(user5))
                .when(ldapManager).search(any(), any());

        final List<PipelineUser> result = manager.filterBlockedUsers(Arrays.asList(user1, user2, user3, user4, user5));

        assertThat(result).containsExactlyInAnyOrder(user4);
    }

    @Test
    public void filterShouldIgnoreFullPagesUsingLoadBlockedMethod() {
        set(SystemPreferences.LDAP_BLOCKED_USER_SEARCH_METHOD, LdapBlockedUserSearchMethod.LOAD_BLOCKED.name());

        final PipelineUser user1 = user(USER_NAME_1, ID_1);
        final PipelineUser user2 = user(USER_NAME_2, ID_2);
        final PipelineUser user3 = user(USER_NAME_3, ID_3);
        final PipelineUser user4 = user(USER_NAME_4, ID_4);
        final PipelineUser user5 = user(USER_NAME_5, ID_5);

        doReturn(ldapResponse(user1, user2))
                .doReturn(ldapResponse(user3))
                .doReturn(ldapResponse(user5))
                .when(ldapManager).search(any(), any());

        final List<PipelineUser> result = manager.filterBlockedUsers(Arrays.asList(user1, user2, user3, user4, user5));

        assertThat(result).containsExactlyInAnyOrder(user3);
    }

    @Test
    public void filterShouldIgnoreEmptyPagesUsingLoadActiveMethod() {
        final PipelineUser user1 = user(USER_NAME_1, ID_1);
        final PipelineUser user2 = user(USER_NAME_2, ID_2);
        final PipelineUser user3 = user(USER_NAME_3, ID_3);
        final PipelineUser user4 = user(USER_NAME_4, ID_4);
        final PipelineUser user5 = user(USER_NAME_5, ID_5);

        doReturn(ldapResponse(user1))
                .doReturn(emptyLdapResponse())
                .doReturn(ldapResponse(user5))
                .when(ldapManager).search(any(), any());

        final List<PipelineUser> result = manager.filterBlockedUsers(Arrays.asList(user1, user2, user3, user4, user5));

        assertThat(result).containsExactlyInAnyOrder(user2);
    }

    @Test
    public void filterShouldUsePaginatedLdapFilters() {
        final PipelineUser user1 = user(USER_NAME_1, ID_1);
        final PipelineUser user2 = user(USER_NAME_2, ID_2);
        final PipelineUser user3 = user(USER_NAME_3, ID_3);
        final PipelineUser user4 = user(USER_NAME_4, ID_4);
        final PipelineUser user5 = user(USER_NAME_5, ID_5);

        manager.filterBlockedUsers(Arrays.asList(user1, user2, user3, user4, user5));

        verify(ldapManager, times(3)).search(any(), any());
        verify(ldapManager).search(request(PAGE_SIZE), "(|(nameAttribute=name1)(nameAttribute=name2))");
        verify(ldapManager).search(request(PAGE_SIZE), "(|(nameAttribute=name3)(nameAttribute=name4))");
        verify(ldapManager).search(request(1), "(|(nameAttribute=name5))");
    }

    private void set(final AbstractSystemPreference<?> key, final Object value) {
        doReturn(value).when(preferenceManager).getPreference(key);
    }

    private LdapSearchRequest request(final int page) {
        return LdapSearchRequest.builder()
                .size(page)
                .type(LdapEntityType.USER)
                .nameAttribute(NAME_ATTRIBUTE)
                .build();
    }

    private PipelineUser user(final String userName, final Long id) {
        final PipelineUser pipelineUser = getPipelineUser(userName, id);
        pipelineUser.setBlocked(false);
        pipelineUser.setLastLoginDate(LocalDateTime.now());
        return pipelineUser;
    }

    private LdapSearchResponse ldapResponse(final PipelineUser... users) {
        List<LdapEntity> result = Arrays.stream(users)
                .map(PipelineUser::getUserName)
                .map(user -> user.toUpperCase(Locale.ROOT))
                .map(user -> new LdapEntity(user, LdapEntityType.USER, null)).collect(Collectors.toList());
        return LdapSearchResponse.completed(result);
    }

    private LdapSearchResponse emptyLdapResponse() {
        return LdapSearchResponse.completed(Collections.emptyList());
    }
}
