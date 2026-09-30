/*
 * Copyright 2026 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.git;

import com.epam.pipeline.entity.git.GitToken;
import com.epam.pipeline.entity.notification.NotificationMessage;
import com.epam.pipeline.entity.preference.Preference;
import com.epam.pipeline.entity.user.DefaultRoles;
import com.epam.pipeline.entity.user.ExtendedRole;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.exception.git.GitClientException;
import com.epam.pipeline.exception.git.UnexpectedResponseStatusException;
import com.epam.pipeline.manager.notification.NotificationManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.user.RoleManager;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyListOf;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

public class GitTokenRotationServiceCoreTest {

    private static final String HOST = "https://gitlab.local";
    private static final String OLD_TOKEN = "old-token";
    private static final String NEW_TOKEN = "new-token";
    private static final String USER_NAME = "root";
    private static final String TOKEN_NAME = "CloudPipeline";
    private static final int USER_ID = 1;
    private static final Long GIT_USER_ID = 1L;
    private static final Long OLD_TOKEN_ID = 10L;
    private static final Long NEW_TOKEN_ID = 11L;
    private static final Long ADMIN_1 = 100L;
    private static final Long ADMIN_2 = 101L;
    private static final int THRESHOLD = 30;
    private static final int LIFETIME = 365;
    private static final String API_SCOPE = "api";
    private static final String SUDO_SCOPE = "sudo";

    private final PreferenceManager preferenceManager = mock(PreferenceManager.class);
    private final GitManager gitManager = mock(GitManager.class);
    private final NotificationManager notificationManager = mock(NotificationManager.class);
    private final RoleManager roleManager = mock(RoleManager.class);
    private final GitlabClient oldClient = mock(GitlabClient.class);
    private final GitlabClient newClient = mock(GitlabClient.class);
    private final Preference tokenPreference = new Preference(SystemPreferences.GIT_TOKEN.getKey(), OLD_TOKEN);

    private final GitTokenRotationServiceCore core = new GitTokenRotationServiceCore(
            preferenceManager, gitManager, notificationManager, roleManager);

    @Before
    public void setUp() {
        doReturn(true).when(preferenceManager).getPreference(SystemPreferences.GIT_TOKEN_ROTATION_ENABLED);
        doReturn(HOST).when(preferenceManager).getPreference(SystemPreferences.GIT_HOST);
        doReturn(OLD_TOKEN).when(preferenceManager).getPreference(SystemPreferences.GIT_TOKEN);
        doReturn(USER_ID).when(preferenceManager).getPreference(SystemPreferences.GIT_USER_ID);
        doReturn(USER_NAME).when(preferenceManager).getPreference(SystemPreferences.GIT_USER_NAME);
        doReturn(THRESHOLD).when(preferenceManager)
                .getPreference(SystemPreferences.GIT_TOKEN_ROTATION_THRESHOLD_DAYS);
        doReturn(LIFETIME).when(preferenceManager)
                .getPreference(SystemPreferences.GIT_TOKEN_ROTATION_LIFETIME_DAYS);
        doReturn(tokenPreference).when(preferenceManager).getSystemPreference(SystemPreferences.GIT_TOKEN);
        doReturn(oldClient).when(gitManager).getGitlabClient(HOST, OLD_TOKEN, GIT_USER_ID, USER_NAME);
        doReturn(newClient).when(gitManager).getGitlabClient(HOST, NEW_TOKEN, GIT_USER_ID, USER_NAME);
        doReturn(GitToken.builder().id(NEW_TOKEN_ID).token(NEW_TOKEN).build()).when(oldClient)
                .issueImpersonationToken(anyString(), anyLong(), any(LocalDate.class),
                        anyListOf(String.class));
        final ExtendedRole admins = new ExtendedRole();
        admins.setUsers(Arrays.asList(user(ADMIN_1), user(ADMIN_2)));
        doReturn(admins).when(roleManager).loadRoleWithUsers(DefaultRoles.ROLE_ADMIN.getId());
    }

    @Test
    public void shouldIssueStoreAndRevokeTokenNearExpiry() {
        doReturn(currentToken(THRESHOLD - 1)).when(oldClient).getCurrentToken();

        core.rotate();

        final InOrder order = inOrder(oldClient, preferenceManager, newClient);
        order.verify(oldClient).issueImpersonationToken(TOKEN_NAME, GIT_USER_ID, todayUtc().plusDays(LIFETIME),
                Collections.singletonList(API_SCOPE));
        order.verify(preferenceManager).update(anyListOf(Preference.class));
        order.verify(newClient).revokeImpersonationToken(GIT_USER_ID, OLD_TOKEN_ID);
        assertThat(tokenPreference.getValue()).isEqualTo(NEW_TOKEN);
        verify(oldClient, never()).revokeImpersonationToken(anyLong(), anyLong());
        verify(notificationManager, never()).saveNotification(any());
    }

    @Test
    public void shouldRotateTokenOnThreshold() {
        doReturn(currentToken(THRESHOLD)).when(oldClient).getCurrentToken();

        core.rotate();

        verify(preferenceManager).update(anyListOf(Preference.class));
        verify(newClient).revokeImpersonationToken(GIT_USER_ID, OLD_TOKEN_ID);
    }

    @Test
    public void shouldUseDefaultNameIfTokenHasNone() {
        final GitToken token = currentToken(1);
        token.setName(null);
        doReturn(token).when(oldClient).getCurrentToken();

        core.rotate();

        verify(oldClient).issueImpersonationToken(eq(TOKEN_NAME), eq(GIT_USER_ID), any(LocalDate.class),
                anyListOf(String.class));
    }

    @Test
    public void shouldKeepScopesOfCurrentToken() {
        final GitToken token = currentToken(1);
        token.setScopes(Arrays.asList(API_SCOPE, SUDO_SCOPE));
        doReturn(token).when(oldClient).getCurrentToken();

        core.rotate();

        verify(oldClient).issueImpersonationToken(TOKEN_NAME, GIT_USER_ID, todayUtc().plusDays(LIFETIME),
                Arrays.asList(API_SCOPE, SUDO_SCOPE));
    }

    @Test
    public void shouldSkipSilentlyIfGitlabCannotReportToken() {
        doThrow(new UnexpectedResponseStatusException(HttpStatus.NOT_FOUND)).when(oldClient).getCurrentToken();

        core.rotate();

        assertNotRotated();
        verify(notificationManager, never()).saveNotification(any());
    }

    @Test
    public void shouldNotifyIfTokenIsEmpty() {
        doReturn(null).when(oldClient).getCurrentToken();

        core.rotate();

        assertNotRotated();
        assertAdminsNotified();
    }

    @Test
    public void shouldRevokeAndNotStoreIssuedTokenWithoutValue() {
        doReturn(currentToken(1)).when(oldClient).getCurrentToken();
        doReturn(GitToken.builder().id(NEW_TOKEN_ID).build()).when(oldClient)
                .issueImpersonationToken(anyString(), anyLong(), any(LocalDate.class), anyListOf(String.class));

        core.rotate();

        verify(preferenceManager, never()).update(anyListOf(Preference.class));
        verify(oldClient).revokeImpersonationToken(GIT_USER_ID, NEW_TOKEN_ID);
        verify(oldClient, never()).revokeImpersonationToken(GIT_USER_ID, OLD_TOKEN_ID);
        assertThat(tokenPreference.getValue()).isEqualTo(OLD_TOKEN);
        assertAdminsNotified();
    }

    @Test
    public void shouldNotRotateTokenWithoutExpiry() {
        final GitToken token = currentToken(1);
        token.setExpires(null);
        doReturn(token).when(oldClient).getCurrentToken();

        core.rotate();

        assertNotRotated();
        verify(notificationManager, never()).saveNotification(any());
    }

    @Test
    public void shouldNotRotateTokenFarFromExpiry() {
        doReturn(currentToken(THRESHOLD + 1)).when(oldClient).getCurrentToken();

        core.rotate();

        assertNotRotated();
        verify(notificationManager, never()).saveNotification(any());
    }

    @Test
    public void shouldDoNothingIfRotationIsDisabled() {
        doReturn(false).when(preferenceManager).getPreference(SystemPreferences.GIT_TOKEN_ROTATION_ENABLED);

        core.rotate();

        verifyZeroInteractions(gitManager, notificationManager);
        verify(preferenceManager, never()).update(anyListOf(Preference.class));
    }

    @Test
    public void shouldDoNothingIfGitIsNotConfigured() {
        doReturn(null).when(preferenceManager).getPreference(SystemPreferences.GIT_TOKEN);

        core.rotate();

        verifyZeroInteractions(gitManager, notificationManager);
        verify(preferenceManager, never()).update(anyListOf(Preference.class));
    }

    @Test
    public void shouldNotifyAndKeepTokenIfTokenCannotBeLoaded() {
        doThrow(new GitClientException("404 Not Found")).when(oldClient).getCurrentToken();

        core.rotate();

        assertNotRotated();
        assertAdminsNotified();
    }

    @Test
    public void shouldNotRotateTokenOfAnotherUser() {
        final GitToken token = currentToken(1);
        token.setUserId(GIT_USER_ID + 1);
        doReturn(token).when(oldClient).getCurrentToken();

        core.rotate();

        assertNotRotated();
        assertAdminsNotified();
    }

    @Test
    public void shouldNotifyAndKeepTokenIfIssueFails() {
        doReturn(currentToken(1)).when(oldClient).getCurrentToken();
        doThrow(new GitClientException("400 Bad Request")).when(oldClient)
                .issueImpersonationToken(anyString(), anyLong(), any(LocalDate.class),
                        anyListOf(String.class));

        core.rotate();

        verify(preferenceManager, never()).update(anyListOf(Preference.class));
        verify(oldClient, never()).revokeImpersonationToken(anyLong(), anyLong());
        verify(newClient, never()).revokeImpersonationToken(anyLong(), anyLong());
        assertThat(tokenPreference.getValue()).isEqualTo(OLD_TOKEN);
        assertAdminsNotified();
    }

    @Test
    public void shouldRevokeNewTokenAndKeepOldIfStoreFails() {
        doReturn(currentToken(1)).when(oldClient).getCurrentToken();
        doThrow(new IllegalArgumentException("invalid")).when(preferenceManager)
                .update(anyListOf(Preference.class));

        core.rotate();

        verify(oldClient).revokeImpersonationToken(GIT_USER_ID, NEW_TOKEN_ID);
        verify(oldClient, never()).revokeImpersonationToken(GIT_USER_ID, OLD_TOKEN_ID);
        verify(newClient, never()).revokeImpersonationToken(anyLong(), anyLong());
        assertAdminsNotified();
    }

    @Test
    public void shouldNotifyIfOldTokenCannotBeRevoked() {
        doReturn(currentToken(1)).when(oldClient).getCurrentToken();
        doThrow(new GitClientException("500")).when(newClient).revokeImpersonationToken(GIT_USER_ID, OLD_TOKEN_ID);

        core.rotate();

        verify(preferenceManager).update(anyListOf(Preference.class));
        assertThat(tokenPreference.getValue()).isEqualTo(NEW_TOKEN);
        assertAdminsNotified();
    }

    @Test
    public void shouldNotFailIfNotificationFails() {
        doThrow(new GitClientException("404 Not Found")).when(oldClient).getCurrentToken();
        doThrow(new IllegalStateException("db")).when(notificationManager).saveNotification(any());

        core.rotate();

        assertNotRotated();
    }

    private void assertNotRotated() {
        verify(oldClient, never()).issueImpersonationToken(anyString(), anyLong(), any(LocalDate.class),
                anyListOf(String.class));
        verify(preferenceManager, never()).update(anyListOf(Preference.class));
        verify(oldClient, never()).revokeImpersonationToken(anyLong(), anyLong());
        verify(newClient, never()).revokeImpersonationToken(anyLong(), anyLong());
        assertThat(tokenPreference.getValue()).isEqualTo(OLD_TOKEN);
    }

    private void assertAdminsNotified() {
        final ArgumentCaptor<NotificationMessage> captor = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(notificationManager).saveNotification(captor.capture());
        final NotificationMessage message = captor.getValue();
        assertThat(message.getToUserId()).isEqualTo(ADMIN_1);
        assertThat(message.getCopyUserIds()).containsExactly(ADMIN_2);
        assertThat(message.getSubject()).isNotBlank();
        assertThat(message.getBody()).isNotBlank();
    }

    private static GitToken currentToken(final int daysLeft) {
        final LocalDate expires = todayUtc().plusDays(daysLeft);
        return GitToken.builder()
                .id(OLD_TOKEN_ID)
                .name(TOKEN_NAME)
                .userId(GIT_USER_ID)
                .active(true)
                .expires(Date.from(expires.atStartOfDay(ZoneId.systemDefault()).toInstant()))
                .build();
    }

    private static LocalDate todayUtc() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    private static PipelineUser user(final Long id) {
        final PipelineUser user = new PipelineUser();
        user.setId(id);
        return user;
    }
}
