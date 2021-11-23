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

package com.epam.pipeline.manager.user;

import com.epam.pipeline.entity.ldap.LdapSearchRequest;
import com.epam.pipeline.entity.ldap.LdapSearchResponse;
import com.epam.pipeline.entity.user.DefaultRoles;
import com.epam.pipeline.entity.user.GroupStatus;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.user.Role;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.ldap.LdapManager;
import com.epam.pipeline.manager.notification.NotificationManager;
import com.epam.pipeline.manager.preference.AbstractSystemPreference;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.SchedulerLock;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class InactiveUsersMonitoringServiceCore {

    private final UserManager userManager;
    private final NotificationManager notificationManager;
    private final PreferenceManager preferenceManager;
    private final LdapManager ldapManager;

    /**
     * Finds inactive users and notifies about them. Inactive user is a user who meets at least one of the
     * following conditions:
     * - user blocked for a long period of time
     * - user belongs to the group that was blocked for a long period of time
     * - user have never login
     * - user have not login for a long period of time
     *
     * Also, this method finds LDAP blocked non-admin users, blocks them into Cloud Pipeline system and
     * adds them to notification.
     */
    @SchedulerLock(name = "InactiveUsersMonitoringService_monitor", lockAtMostForString = "PT10M")
    public void monitor() {
        if (monitoringDisabled()) {
            log.debug("Inactive users monitoring is not enabled");
            return;
        }

        log.debug("Started inactive users monitoring");
        final Collection<PipelineUser> allUsers = userManager.loadAllUsers();
        if (CollectionUtils.isEmpty(allUsers)) {
            log.debug("No users found to monitor");
            return;
        }

        final List<PipelineUser> inactiveUsers = findInactivePipelineUsers(allUsers);
        final List<PipelineUser> ldapBlockedUsers = findAndBlockLdapBlockedUsers(allUsers);
        notificationManager.notifyInactiveUsers(inactiveUsers, ldapBlockedUsers);
        log.debug("Finished inactive users monitoring");
    }

    private boolean monitoringDisabled() {
        final Boolean monitoringEnabled = preferenceManager.getPreference(
                SystemPreferences.SYSTEM_USER_MONITOR_ENABLED);
        return Objects.isNull(monitoringEnabled) || !monitoringEnabled;
    }

    private List<PipelineUser> findAndBlockLdapBlockedUsers(final Collection<PipelineUser> allUsers) {
        return allUsers.stream()
                .filter(pipelineUser -> !pipelineUser.isBlocked())
                .filter(pipelineUser -> !userIsAdmin(pipelineUser))
                .filter(this::ldapBlocked)
                .map(user -> userManager.updateUserBlockingStatus(user.getId(), true))
                .collect(Collectors.toList());
    }

    private List<PipelineUser> findInactivePipelineUsers(final Collection<PipelineUser> allUsers) {
        final Integer userBlockedDays = parseIntPreference(SystemPreferences.SYSTEM_USER_MONITOR_BLOCKED_DAYS);
        final Integer userIdleDays = parseIntPreference(SystemPreferences.SYSTEM_USER_MONITOR_IDLE_DAYS);

        final LocalDateTime now = DateUtils.nowUTC();
        final Map<String, GroupStatus> blockedGroups = ListUtils.emptyIfNull(
                userManager.loadAllGroupsBlockingStatuses()).stream()
                .filter(GroupStatus::isBlocked)
                .filter(groupStatus -> allowedPeriodExceeded(now, groupStatus.getLastModifiedData(), userBlockedDays))
                .collect(Collectors.toMap(GroupStatus::getGroupName, Function.identity()));

        return allUsers.stream()
                .filter(user -> shouldNotify(user, now, userBlockedDays, userIdleDays, blockedGroups))
                .collect(Collectors.toList());
    }

    private Integer parseIntPreference(final AbstractSystemPreference.IntPreference preference) {
        final Integer result = preferenceManager.getPreference(preference);
        if (Objects.isNull(result)) {
            return null;
        }
        return result < 0 ? null : result;
    }

    private boolean ldapBlocked(final PipelineUser user) {
        final LdapSearchResponse ldapSearchResponse = ldapManager
                .searchBlockedUser(LdapSearchRequest.forUser(user.getUserName()));
        return Objects.nonNull(ldapSearchResponse) && CollectionUtils.isNotEmpty(ldapSearchResponse.getEntities());
    }

    private boolean userIsAdmin(final PipelineUser pipelineUser) {
        return pipelineUser.isAdmin() || ListUtils.emptyIfNull(pipelineUser.getRoles()).stream()
                .map(Role::getName)
                .anyMatch(role -> DefaultRoles.ROLE_ADMIN.getName().equals(role));
    }

    private boolean shouldNotify(final PipelineUser user, final LocalDateTime now, final Integer userBlockedDays,
                                 final Integer userIdleDays, final Map<String, GroupStatus> blockedGroups) {
        return shouldNotifyIdleUsers(user, now, userIdleDays)
                || shouldNotifyBlockedUsers(user, now, userBlockedDays, blockedGroups);
    }

    private boolean shouldNotifyBlockedUsers(final PipelineUser user, final LocalDateTime now,
                                             final Integer userBlockedDays,
                                             final Map<String, GroupStatus> blockedGroups) {
        return Objects.nonNull(userBlockedDays)
                && (user.isBlocked() && allowedPeriodExceeded(now, user.getBlockDate(), userBlockedDays)
                || hasBlockedGroup(user.getGroups(), blockedGroups));
    }

    private boolean shouldNotifyIdleUsers(final PipelineUser user, final LocalDateTime now,
                                          final Integer userIdleDays) {
        return Objects.nonNull(userIdleDays)
                && allowedPeriodExceeded(now, findUserLastActionDate(user), userIdleDays);
    }

    private LocalDateTime findUserLastActionDate(final PipelineUser user) {
        if (Objects.nonNull(user.getLastLoginDate())) {
            return user.getLastLoginDate();
        }
        return Objects.isNull(user.getFirstLoginDate())
                ? user.getRegistrationDate()
                : user.getFirstLoginDate();
    }

    private boolean allowedPeriodExceeded(final LocalDateTime now, final LocalDateTime actionDate,
                                          final Integer threshold) {
        return Objects.nonNull(actionDate) && DateUtils.daysBetweenDates(now, actionDate) > threshold;
    }

    private boolean hasBlockedGroup(final List<String> userGroups, final Map<String, GroupStatus> blockedGroups) {
        if (MapUtils.isEmpty(blockedGroups) || CollectionUtils.isEmpty(userGroups)) {
            return false;
        }
        return blockedGroups.entrySet().stream()
                .anyMatch(entry -> userGroups.contains(entry.getKey()));
    }
}
