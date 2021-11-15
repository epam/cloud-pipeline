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

import com.epam.pipeline.entity.user.GroupStatus;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.notification.NotificationManager;
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

    @SchedulerLock(name = "InactiveUsersMonitoringService_monitor", lockAtMostForString = "PT10M")
    public void monitor() {
        final Integer userBlockedDays = preferenceManager.getPreference(
                SystemPreferences.SYSTEM_USER_MONITOR_BLOCKED_DAYS);
        final Integer userIdelDays = preferenceManager.getPreference(
                SystemPreferences.SYSTEM_USER_MONITOR_IDEL_DAYS);

        if (Objects.isNull(userIdelDays) && Objects.isNull(userBlockedDays)) {
            log.debug("Inactive users monitoring is not enabled");
            return;
        }

        log.debug("Started inactive users monitoring");
        final Collection<PipelineUser> allUsers = userManager.loadAllUsers();
        if (CollectionUtils.isEmpty(allUsers)) {
            return;
        }

        final LocalDateTime now = DateUtils.nowUTC();
        final Map<String, GroupStatus> blockedGroups = ListUtils.emptyIfNull(
                userManager.loadAllGroupsBlockingStatuses()).stream()
                .filter(GroupStatus::isBlocked)
                .filter(groupStatus -> allowedPeriodExceeded(now, groupStatus.getLastModifiedData(), userBlockedDays))
                .collect(Collectors.toMap(GroupStatus::getGroupName, Function.identity()));

        final List<PipelineUser> inactiveUsers = allUsers.stream()
                .filter(user -> shouldNotify(user, now, userBlockedDays, userIdelDays, blockedGroups))
                .collect(Collectors.toList());

        notificationManager.notifyInactiveUsers(inactiveUsers);
        log.debug("Finished inactive users monitoring");
    }

    private boolean shouldNotify(final PipelineUser user, final LocalDateTime now, final Integer userBlockedDays,
                                 final Integer userIdelDays, final Map<String, GroupStatus> blockedGroups) {
        return shouldNotifyIdelUsers(user, now, userIdelDays)
                || shouldNotifyBlockedUsers(user, now, userBlockedDays, blockedGroups);
    }

    private boolean shouldNotifyBlockedUsers(final PipelineUser user, final LocalDateTime now,
                                             final Integer userBlockedDays,
                                             final Map<String, GroupStatus> blockedGroups) {
        return Objects.nonNull(userBlockedDays)
                && (user.isBlocked() && allowedPeriodExceeded(now, user.getBlockDate(), userBlockedDays)
                || hasBlockedGroup(user.getGroups(), blockedGroups));
    }

    private boolean shouldNotifyIdelUsers(final PipelineUser user, final LocalDateTime now,
                                          final Integer userIdelDays) {
        return Objects.nonNull(userIdelDays)
                && allowedPeriodExceeded(now, findUserLastActionDate(user), userIdelDays);
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
