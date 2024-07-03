/*
 * Copyright 2022 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.user;

import com.epam.pipeline.entity.notification.NotificationType;
import com.epam.pipeline.entity.user.DefaultRoles;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.ldap.LdapBlockedUsersManager;
import com.epam.pipeline.manager.notification.NotificationManager;
import com.epam.pipeline.manager.preference.AbstractSystemPreference;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static com.epam.pipeline.test.creator.user.UserCreatorUtils.getPipelineUser;
import static com.epam.pipeline.util.CustomMatchers.matches;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class BlockedUsersMonitoringServiceCoreTest {

    private static final String USER_NAME_1 = "name1";
    private static final String USER_NAME_2 = "name2";
    private static final String USER_NAME_3 = "name3";
    private static final String USER_NAME_4 = "name4";
    private static final Long ID_1 = 1L;
    private static final Long ID_2 = 2L;
    private static final Long ID_3 = 3L;
    private static final Long ID_4 = 4L;
    private static final Integer GRACE_DURATION_DAYS = 7;
    private static final Duration GRACE_DURATION = Duration.ofDays(GRACE_DURATION_DAYS);
    private static final LocalDateTime NOW = DateUtils.nowUTC();
    private static final LocalDateTime PENDING = NOW.minus(GRACE_DURATION.dividedBy(2));
    private static final LocalDateTime EXPIRED = NOW.minus(GRACE_DURATION.multipliedBy(2));

    private final UserManager userManager = mock(UserManager.class);
    private final NotificationManager notificationManager = mock(NotificationManager.class);
    private final PreferenceManager preferenceManager = mock(PreferenceManager.class);
    private final LdapBlockedUsersManager ldapManager = mock(LdapBlockedUsersManager.class);
    private final BlockedUsersMonitoringServiceCore monitoringService = new BlockedUsersMonitoringServiceCore(
            userManager, notificationManager, preferenceManager, ldapManager);

    @Before
    public void setup() {
        set(SystemPreferences.SYSTEM_LDAP_USER_BLOCK_MONITOR_ENABLED, true);
        set(SystemPreferences.SYSTEM_LDAP_USER_BLOCK_MONITOR_GRACE_PERIOD_DAYS, GRACE_DURATION_DAYS);
    }

    @Test
    public void monitorShouldBlockUsersThatAreExternallyBlockedLongerThanGracePeriod() {
        final PipelineUser user1 = user(USER_NAME_1, ID_1);
        user1.setExternalBlockDate(EXPIRED);
        final PipelineUser user2 = user(USER_NAME_2, ID_2);
        user2.setExternalBlockDate(PENDING);
        final PipelineUser user3 = user(USER_NAME_3, ID_3);
        final PipelineUser user4 = user(USER_NAME_4, ID_4);
        mockUsers(Arrays.asList(user1, user2, user3, user4), Arrays.asList(user1, user2, user3));

        monitoringService.monitor();

        verify(userManager, times(1)).updateUserBlockingStatus(any(), anyBoolean());
        verify(userManager).updateUserBlockingStatus(user1.getId(), true);
    }

    @Test
    public void monitorShouldNotifyBlockedUsers() {
        final PipelineUser user1 = user(USER_NAME_1, ID_1);
        user1.setExternalBlockDate(EXPIRED);
        final PipelineUser user2 = user(USER_NAME_2, ID_2);
        user2.setExternalBlockDate(PENDING);
        final PipelineUser user3 = user(USER_NAME_3, ID_3);
        final PipelineUser user4 = user(USER_NAME_4, ID_4);
        mockUsers(Arrays.asList(user1, user2, user3, user4), Arrays.asList(user1, user2, user3));

        monitoringService.monitor();

        verify(notificationManager, times(1)).notifyPipelineUsers(any(), eq(NotificationType.LDAP_BLOCKED_USERS));
        verify(notificationManager).notifyPipelineUsers(Collections.singletonList(user1),
                NotificationType.LDAP_BLOCKED_USERS);
    }

    @Test
    public void monitorShouldNotifyDeferredBlockedUsers() {
        final PipelineUser user1 = user(USER_NAME_1, ID_1);
        user1.setExternalBlockDate(EXPIRED);
        final PipelineUser user2 = user(USER_NAME_2, ID_2);
        user2.setExternalBlockDate(PENDING);
        final PipelineUser user3 = user(USER_NAME_3, ID_3);
        final PipelineUser user4 = user(USER_NAME_4, ID_4);
        mockUsers(Arrays.asList(user1, user2, user3, user4), Arrays.asList(user1, user2, user3));

        monitoringService.monitor();

        verify(notificationManager, times(1)).notifyPipelineUsers(any(),
                eq(NotificationType.LDAP_BLOCKED_POSTPONED_USERS));
        verify(notificationManager).notifyPipelineUsers(Arrays.asList(user2, user3),
                NotificationType.LDAP_BLOCKED_POSTPONED_USERS);
    }

    @Test
    public void monitorShouldAddExternalBlockDateForExternallyBlockedUsers() {
        final PipelineUser user1 = user(USER_NAME_1, ID_1);
        user1.setExternalBlockDate(EXPIRED);
        final PipelineUser user2 = user(USER_NAME_2, ID_2);
        user2.setExternalBlockDate(PENDING);
        final PipelineUser user3 = user(USER_NAME_3, ID_3);
        final PipelineUser user4 = user(USER_NAME_3, ID_4);
        mockUsers(Arrays.asList(user1, user2, user3, user4), Arrays.asList(user1, user2, user3));

        monitoringService.monitor();

        verify(userManager, times(1)).updateExternalBlockDate(any(), any());
        verify(userManager).updateExternalBlockDate(eq(user3.getId()), argThat(matches(Objects::nonNull)));
    }

    @Test
    public void monitorShouldRemoveExternalBlockDateForExternallyUnblockedUsers() {
        final PipelineUser user = user(USER_NAME_1, ID_1);
        user.setExternalBlockDate(NOW);
        mockUsers(Collections.singletonList(user));

        monitoringService.monitor();

        verify(userManager, times(1)).updateExternalBlockDate(any(), any());
        verify(userManager).updateExternalBlockDate(eq(user.getId()), argThat(matches(Objects::isNull)));
    }

    @Test
    public void monitorShouldSkipUserWithAdminRole() {
        final PipelineUser user = user(USER_NAME_1, ID_1);
        user.setRoles(Collections.singletonList(DefaultRoles.ROLE_ADMIN.getRole()));
        mockUsers(Collections.singletonList(user));

        monitoringService.monitor();

        verify(ldapManager).filterBlockedUsers(Collections.emptyList());
    }

    @Test
    public void monitorShouldSkipUserWithAdminFlag() {
        final PipelineUser user = user(USER_NAME_1, ID_1);
        user.setAdmin(true);
        mockUsers(Collections.singletonList(user));

        monitoringService.monitor();

        verify(ldapManager).filterBlockedUsers(Collections.emptyList());
    }

    @Test
    public void monitorShouldSkipUserWithServiceRole() {
        final PipelineUser user = user(USER_NAME_1, ID_1);
        user.setRoles(Collections.singletonList(DefaultRoles.ROLE_SERVICE_ACCOUNT.getRole()));
        mockUsers(Collections.singletonList(user));

        monitoringService.monitor();

        verify(ldapManager).filterBlockedUsers(Collections.emptyList());
    }

    @Test
    public void monitorShouldSkipUserWithBlockFlag() {
        final PipelineUser user = user(USER_NAME_1, ID_1);
        user.setBlocked(true);
        mockUsers(Collections.singletonList(user));

        monitoringService.monitor();

        verify(ldapManager).filterBlockedUsers(Collections.emptyList());
    }

    private void set(final AbstractSystemPreference<?> key, final Object value) {
        doReturn(value).when(preferenceManager).getPreference(key);
    }

    private void mockUsers(final List<PipelineUser> users) {
        mockUsers(users, Collections.emptyList());
    }

    private void mockUsers(final List<PipelineUser> users, final List<PipelineUser> blockedUsers) {
        doReturn(users).when(userManager).loadAllUsers();
        doReturn(blockedUsers).when(ldapManager).filterBlockedUsers(users);
        doAnswer(invocation -> users.stream()
                .filter(user -> user.getId().equals(invocation.getArguments()[0]))
                .findFirst()
                .orElseThrow(IllegalArgumentException::new))
                .when(userManager).updateUserBlockingStatus(any(), anyBoolean());
    }

    private PipelineUser user(final String userName, final Long id) {
        final PipelineUser pipelineUser = getPipelineUser(userName, id);
        pipelineUser.setBlocked(false);
        pipelineUser.setLastLoginDate(LocalDateTime.now());
        return pipelineUser;
    }
}
