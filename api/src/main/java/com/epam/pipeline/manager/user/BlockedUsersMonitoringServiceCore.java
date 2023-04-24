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
import com.epam.pipeline.entity.user.Role;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.ldap.LdapBlockedUsersManager;
import com.epam.pipeline.manager.notification.NotificationManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.utils.CommonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.SchedulerLock;
import org.apache.commons.collections4.ListUtils;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class BlockedUsersMonitoringServiceCore {

    private static final Duration FALLBACK_BLOCK_GRACE_PERIOD = Duration.ofDays(7);

    private final UserManager userManager;
    private final NotificationManager notificationManager;
    private final PreferenceManager preferenceManager;
    private final LdapBlockedUsersManager ldapBlockedUsersManager;

    /**
     * Finds LDAP blocked non-admin users, blocks them into Cloud Pipeline system and
     * notifies about them.
     */
    @SchedulerLock(name = "BlockedUsersMonitoringService_monitor", lockAtMostForString = "PT10M")
    public void monitor() {
        if (isMonitoringDisabled()) {
            log.debug("Blocked users monitoring is not enabled");
            return;
        }

        log.debug("Started blocked users monitoring");
        final LocalDateTime now = DateUtils.nowUTC();
        final Duration grace = getBlockGracePeriod();

        final List<PipelineUser> users = collectUsers();

        final List<PipelineUser> externallyBlockedUsers = collectExternallyBlockedUsers(users);
        markExternallyBlockedUsers(externallyBlockedUsers);

        final List<PipelineUser> externallyUnblockedUsers = CommonUtils.subtract(users, externallyBlockedUsers);
        unmarkExternallyUnblockedUsers(externallyUnblockedUsers);

        final List<PipelineUser> blockingUsers = resolveBlockingUsers(externallyBlockedUsers, now, grace);
        final List<PipelineUser> blockedUsers = blockUsers(blockingUsers);
        notifyBlockedUsers(blockedUsers);

        final List<PipelineUser> blockPostponedUsers = CommonUtils.subtract(externallyBlockedUsers, blockingUsers);
        notifyBlockPostponedUsers(blockPostponedUsers, now, grace);

        log.debug("Finished blocked users monitoring");
    }

    private boolean isMonitoringDisabled() {
        return Optional.of(SystemPreferences.SYSTEM_LDAP_USER_BLOCK_MONITOR_ENABLED)
                .map(preferenceManager::getPreference)
                .map(it -> !it)
                .orElse(true);
    }

    private Duration getBlockGracePeriod() {
        return Optional.of(SystemPreferences.SYSTEM_LDAP_USER_BLOCK_MONITOR_GRACE_PERIOD_DAYS)
                .map(preferenceManager::getPreference)
                .map(Duration::ofDays)
                .orElse(FALLBACK_BLOCK_GRACE_PERIOD);
    }

    private List<PipelineUser> collectUsers() {
        log.debug("Collecting users...");
        return userManager.loadAllUsers().stream()
                .filter(pipelineUser -> !pipelineUser.isBlocked()
                        && !pipelineUser.isAdmin()
                        && !hasRole(pipelineUser, DefaultRoles.ROLE_ADMIN.getName())
                        && !hasRole(pipelineUser, DefaultRoles.ROLE_SERVICE_ACCOUNT.getName()))
                .collect(Collectors.toList());
    }

    private boolean hasRole(final PipelineUser user, final String role) {
        return ListUtils.emptyIfNull(user.getRoles())
                .stream()
                .map(Role::getName)
                .anyMatch(role::equals);
    }

    private List<PipelineUser> collectExternallyBlockedUsers(final List<PipelineUser> users) {
        log.debug("Collecting external users...");
        return ldapBlockedUsersManager.filterBlockedUsers(users);
    }

    public void markExternallyBlockedUsers(final List<PipelineUser> users) {
        users.stream()
                .filter(user -> user.getExternalBlockDate() == null)
                .peek(user -> user.setExternalBlockDate(DateUtils.nowUTC()))
                .peek(user -> log.debug("Marking user {} as externally blocked...", user.getUserName()))
                .forEach(user -> userManager.updateExternalBlockDate(user.getId(), user.getExternalBlockDate()));
    }

    private void unmarkExternallyUnblockedUsers(final List<PipelineUser> users) {
        users.stream()
                .filter(user -> user.getExternalBlockDate() != null)
                .peek(user -> user.setExternalBlockDate(null))
                .peek(user -> log.debug("Unmarking user {} as externally blocked...", user.getUserName()))
                .forEach(user -> userManager.updateExternalBlockDate(user.getId(), user.getExternalBlockDate()));
    }

    private List<PipelineUser> resolveBlockingUsers(final List<PipelineUser> users,
                                                    final LocalDateTime now,
                                                    final Duration grace) {
        return users.stream()
                .filter(user -> user.getExternalBlockDate().plus(grace).isBefore(now))
                .collect(Collectors.toList());
    }

    private List<PipelineUser> blockUsers(final List<PipelineUser> users) {
        log.debug("Blocking users ({})...", users.size());
        return users.stream()
                .map(user -> {
                    log.info("Blocking user {}...", user.getUserName());
                    return userManager.updateUserBlockingStatus(user.getId(), true);
                })
                .collect(Collectors.toList());
    }

    private void notifyBlockedUsers(final List<PipelineUser> users) {
        log.debug("Notifying blocked users ({})...", users.size());
        notificationManager.notifyPipelineUsers(users, NotificationType.LDAP_BLOCKED_USERS);
    }

    private void notifyBlockPostponedUsers(final List<PipelineUser> users, final LocalDateTime now,
                                           final Duration duration) {
        log.debug("Notifying block postponed users ({})...", users.size());
        for (final PipelineUser user : users) {
            final LocalDateTime blockDate = user.getExternalBlockDate().plus(duration);
            log.debug("Postponing blocking of {} user to {} days...", user.getUserName(),
                    Duration.between(now, blockDate).toDays());
        }
        notificationManager.notifyPipelineUsers(users, NotificationType.LDAP_BLOCKED_POSTPONED_USERS);
    }
}
