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
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.exception.git.UnexpectedResponseStatusException;
import com.epam.pipeline.manager.notification.NotificationManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.user.RoleManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.SchedulerLock;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Rotates the git.token preference - the impersonation token of the Gitlab admin user, that the platform
 * uses to access its own Gitlab.
 *
 * Gitlab has no rotate endpoint for impersonation tokens. So once the token is close to its expiry,
 * a new impersonation token is issued, stored in the git.token preference, and only then the old token
 * is revoked. A token without an expiry date is never rotated.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@SuppressWarnings("PMD.AvoidCatchingGenericException")
public class GitTokenRotationServiceCore {

    private static final String DEFAULT_TOKEN_NAME = "CloudPipeline";
    private static final String DEFAULT_TOKEN_SCOPE = "api";
    private static final String NOTIFICATION_SUBJECT = "Git token rotation failed";
    private static final String CANNOT_LOAD_TOKEN = "Cannot load the git.token details from Gitlab. "
            + "The token is not rotated.";
    private static final String CANNOT_ISSUE_TOKEN = "Cannot issue a new git.token. The current token is kept.";

    private final PreferenceManager preferenceManager;
    private final GitManager gitManager;
    private final NotificationManager notificationManager;
    private final RoleManager roleManager;

    @SchedulerLock(name = "GitTokenRotationService_rotate", lockAtMostForString = "PT10M")
    public void rotate() {
        if (!Boolean.TRUE.equals(preferenceManager.getPreference(SystemPreferences.GIT_TOKEN_ROTATION_ENABLED))) {
            log.debug("Git token rotation is disabled.");
            return;
        }
        final String host = preferenceManager.getPreference(SystemPreferences.GIT_HOST);
        final String token = preferenceManager.getPreference(SystemPreferences.GIT_TOKEN);
        final Integer userId = preferenceManager.getPreference(SystemPreferences.GIT_USER_ID);
        final String userName = preferenceManager.getPreference(SystemPreferences.GIT_USER_NAME);
        if (StringUtils.isBlank(host) || StringUtils.isBlank(token) || Objects.isNull(userId)) {
            log.debug("Git token rotation is skipped: Git preferences are not configured.");
            return;
        }
        final Long gitUserId = userId.longValue();
        final GitlabClient currentClient = gitManager.getGitlabClient(host, token, gitUserId, userName);

        final GitToken currentToken;
        try {
            currentToken = currentClient.getCurrentToken();
        } catch (UnexpectedResponseStatusException e) {
            if (e.getStatus() == HttpStatus.NOT_FOUND) {
                log.debug("Git token rotation is skipped: Gitlab older than 15.5 cannot report the token expiry.");
                return;
            }
            fail(CANNOT_LOAD_TOKEN, e);
            return;
        } catch (RuntimeException e) {
            fail(CANNOT_LOAD_TOKEN, e);
            return;
        }
        if (Objects.isNull(currentToken)) {
            fail(CANNOT_LOAD_TOKEN, null);
            return;
        }
        if (Objects.isNull(currentToken.getExpires())) {
            log.debug("Git token rotation is skipped: git.token has no expiry date.");
            return;
        }
        if (Objects.nonNull(currentToken.getUserId()) && !currentToken.getUserId().equals(gitUserId)) {
            fail(String.format("The git.token belongs to Gitlab user %d, but git.user.id is %d. "
                    + "The token is not rotated.", currentToken.getUserId(), gitUserId), null);
            return;
        }

        // Gitlab keeps expiry as a date in its time zone, which is UTC by default. The date is parsed
        // in the JVM time zone, so the same zone restores it.
        final LocalDate today = LocalDate.now(ZoneOffset.UTC);
        final LocalDate expires = currentToken.getExpires().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        final long daysLeft = ChronoUnit.DAYS.between(today, expires);
        final int threshold = preferenceManager.getPreference(SystemPreferences.GIT_TOKEN_ROTATION_THRESHOLD_DAYS);
        if (daysLeft > threshold) {
            log.debug("Git token rotation is skipped: git.token expires in {} days.", daysLeft);
            return;
        }

        final int lifetime = preferenceManager.getPreference(SystemPreferences.GIT_TOKEN_ROTATION_LIFETIME_DAYS);
        if (lifetime <= threshold) {
            log.warn("Git token lifetime ({} days) does not exceed the rotation threshold ({} days): "
                    + "git.token will be rotated on every check.", lifetime, threshold);
        }
        log.info("Rotating git.token (id {}), that expires in {} days.", currentToken.getId(), daysLeft);

        final GitToken newToken;
        try {
            newToken = currentClient.issueImpersonationToken(
                    StringUtils.defaultIfBlank(currentToken.getName(), DEFAULT_TOKEN_NAME), gitUserId,
                    today.plusDays(lifetime), getScopes(currentToken));
        } catch (RuntimeException e) {
            fail(CANNOT_ISSUE_TOKEN, e);
            return;
        }
        if (Objects.isNull(newToken) || StringUtils.isBlank(newToken.getToken())) {
            if (Objects.nonNull(newToken) && Objects.nonNull(newToken.getId())) {
                revokeUnusedToken(currentClient, gitUserId, newToken);
            }
            fail(CANNOT_ISSUE_TOKEN, null);
            return;
        }

        try {
            storeToken(newToken.getToken());
        } catch (RuntimeException e) {
            // the error message may hold the new token value, so it is neither logged nor sent
            log.error("Cannot store the new git.token: {}.", e.getClass().getName());
            revokeUnusedToken(currentClient, gitUserId, newToken);
            fail("Cannot store the new git.token. The current token is kept.", null);
            return;
        }
        log.info("Stored a new git.token (id {}), that expires on {}.", newToken.getId(), today.plusDays(lifetime));

        try {
            gitManager.getGitlabClient(host, newToken.getToken(), gitUserId, userName)
                    .revokeImpersonationToken(gitUserId, currentToken.getId());
            log.info("Revoked the previous git.token (id {}).", currentToken.getId());
        } catch (RuntimeException e) {
            fail(String.format("A new git.token is stored, but the previous one (id %d) cannot be revoked. "
                    + "Revoke it in Gitlab manually.", currentToken.getId()), e);
        }
    }

    private List<String> getScopes(final GitToken token) {
        final List<String> scopes = ListUtils.emptyIfNull(token.getScopes()).stream()
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toList());
        return scopes.isEmpty() ? Collections.singletonList(DEFAULT_TOKEN_SCOPE) : scopes;
    }

    private void storeToken(final String value) {
        final Preference preference = preferenceManager.getSystemPreference(SystemPreferences.GIT_TOKEN);
        preference.setValue(value);
        preferenceManager.update(Collections.singletonList(preference));
    }

    private void revokeUnusedToken(final GitlabClient client, final Long gitUserId, final GitToken token) {
        try {
            client.revokeImpersonationToken(gitUserId, token.getId());
        } catch (RuntimeException e) {
            log.error(String.format("Cannot revoke the unused git token (id %d). Revoke it in Gitlab manually.",
                    token.getId()), e);
        }
    }

    private void fail(final String message, final Exception e) {
        log.error(message, e);
        try {
            notifyAdmins(message + (Objects.isNull(e) ? "" : " Cause: " + e.getMessage()));
        } catch (RuntimeException notificationError) {
            log.error("Cannot send a notification on a git.token rotation failure.", notificationError);
        }
    }

    private void notifyAdmins(final String text) {
        final List<Long> adminIds = ListUtils.emptyIfNull(
                roleManager.loadRoleWithUsers(DefaultRoles.ROLE_ADMIN.getId()).getUsers()).stream()
                .map(PipelineUser::getId)
                .collect(Collectors.toList());
        if (adminIds.isEmpty()) {
            log.warn("No admin users found to notify on a git.token rotation failure.");
            return;
        }
        final NotificationMessage message = new NotificationMessage();
        message.setSubject(NOTIFICATION_SUBJECT);
        message.setBody(text);
        message.setToUserId(adminIds.get(0));
        message.setCopyUserIds(adminIds.subList(1, adminIds.size()));
        notificationManager.saveNotification(message);
    }
}
