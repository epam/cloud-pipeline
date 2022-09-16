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
import com.epam.pipeline.manager.ldap.LdapBlockedUsersManager;
import com.epam.pipeline.manager.notification.NotificationManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.SchedulerLock;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class BlockedUsersMonitoringServiceCore {
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
        if (monitoringDisabled()) {
            log.debug("Blocked users monitoring is not enabled");
            return;
        }

        log.debug("Started blocked users monitoring");
        final Collection<PipelineUser> allUsers = userManager.loadAllUsers();
        if (CollectionUtils.isEmpty(allUsers)) {
            log.debug("No users found to monitor");
            return;
        }

        final List<PipelineUser> ldapBlockedUsers = findAndBlockLdapBlockedUsers(allUsers);
        notificationManager.notifyPipelineUsers(ldapBlockedUsers, NotificationType.LDAP_BLOCKED_USERS);
        log.debug("Finished blocked users monitoring");
    }

    private boolean monitoringDisabled() {
        final Boolean monitoringEnabled = preferenceManager.getPreference(
                SystemPreferences.SYSTEM_LDAP_USER_BLOCK_MONITOR_ENABLED);
        return Objects.isNull(monitoringEnabled) || !monitoringEnabled;
    }

    private List<PipelineUser> findAndBlockLdapBlockedUsers(final Collection<PipelineUser> allUsers) {
        final List<PipelineUser> activeNonAdmins = allUsers.stream()
                .filter(pipelineUser -> !pipelineUser.isBlocked())
                .filter(pipelineUser -> !userIsAdmin(pipelineUser))
                .filter(pipelineUser -> !hasRole(pipelineUser, DefaultRoles.ROLE_SERVICE_ACCOUNT.getName()))
                .collect(Collectors.toList());

        log.debug("Fetch from DB {} active non admin users. " +
                "Will query LDAP with this list and sync blocking status.", activeNonAdmins.size());

        final List<PipelineUser> blockedUsers = ldapBlockedUsersManager.filterBlockedUsers(activeNonAdmins);
        log.debug("Found {} new blocked user in total.", blockedUsers.size());
        return blockedUsers.stream()
                .map(user -> userManager.updateUserBlockingStatus(user.getId(), true))
                .collect(Collectors.toList());
    }

    private boolean userIsAdmin(final PipelineUser pipelineUser) {
        return pipelineUser.isAdmin() || hasRole(pipelineUser, DefaultRoles.ROLE_ADMIN.getName());
    }

    private boolean hasRole(final PipelineUser pipelineUser, final String roleName) {
        return ListUtils.emptyIfNull(pipelineUser.getRoles())
                .stream()
                .map(Role::getName)
                .anyMatch(roleName::equals);
    }
}
