/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.report;

import com.epam.pipeline.dto.report.UsersUsageInfo;
import com.epam.pipeline.dto.report.UsersUsageReportFilterVO;
import com.epam.pipeline.dto.user.OnlineUsers;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.user.OnlineUsersService;
import com.epam.pipeline.manager.user.UserManager;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.epam.pipeline.manager.report.ReportUtils.buildTimeIntervals;
import static com.epam.pipeline.manager.report.ReportUtils.calculateSampleMedian;
import static com.epam.pipeline.manager.report.ReportUtils.dateInInterval;

@Service
@RequiredArgsConstructor
public class UsersUsageReportService {
    private final OnlineUsersService onlineUsersService;
    private final UserManager userManager;

    public List<UsersUsageInfo> loadUsersUsage(final UsersUsageReportFilterVO filter) {
        final UsersUsageReportFilterVO preparedFilter = prepareFilter(Objects.isNull(filter)
                ? new UsersUsageReportFilterVO()
                : filter);
        final LocalDateTime start = preparedFilter.getFrom();
        final LocalDateTime end = preparedFilter.getTo();
        final Set<Long> users = prepareUsers(filter);
        final List<OnlineUsers> onlineUsers = onlineUsersService.getUsersByPeriod(start, end, users);
        if (ChronoUnit.HOURS == filter.getInterval()) {
            return calculateDayUsersUsageByHour(onlineUsers, start, end, users);
        }
        if (ChronoUnit.DAYS == filter.getInterval()) {
            return buildMonthUsersUsage(onlineUsers, start, end, users);
        }
        throw new UnsupportedOperationException(String.format("Time interval '%s' is not supported for now",
                filter.getInterval().name()));
    }

    private UsersUsageReportFilterVO prepareFilter(final UsersUsageReportFilterVO filter) {
        Assert.notNull(filter.getInterval(), "Interval must be specified");
        if (Objects.isNull(filter.getFrom())) {
            filter.setFrom(DateUtils.nowUTC().toLocalDate().atStartOfDay());
        }
        filter.setTo(buildEndOfInterval(filter.getTo()));
        Assert.state(!filter.getFrom().isAfter(filter.getTo()), "'from' date must be before 'to' date");
        return filter;
    }

    private LocalDateTime buildEndOfInterval(final LocalDateTime to) {
        final LocalDateTime now = DateUtils.nowUTC();
        if (Objects.isNull(to)) {
            return now;
        }
        return to.isAfter(now) ? now : to;
    }

    private UsersUsageInfo buildHourUsersUsageInfo(final List<OnlineUsers> users, final LocalDateTime from,
                                                   final Set<Long> filterUsers) {
        final LocalDateTime to = from.plusHours(1);
        final List<Long> usersInInterval = users.stream()
                .filter(user -> dateInInterval(user.getLogDate(), from, to))
                .flatMap(user -> user.getUserIds().stream())
                .distinct()
                .filter(userId -> CollectionUtils.isEmpty(filterUsers) || filterUsers.contains(userId))
                .collect(Collectors.toList());
        final List<String> userNamesInInterval = userManager.loadUsersById(usersInInterval).stream()
                .map(PipelineUser::getUserName)
                .collect(Collectors.toList());
        return UsersUsageInfo.builder()
                .activeUsers(userNamesInInterval)
                .activeUsersCount(usersInInterval.size())
                .periodStart(from)
                .periodEnd(to)
                .build();
    }

    private List<UsersUsageInfo> calculateDayUsersUsageByHour(final List<OnlineUsers> users, final LocalDateTime from,
                                                              final LocalDateTime to, final Set<Long> filterUsers) {
        final List<LocalDateTime> intervals = buildTimeIntervals(from, to, ChronoUnit.HOURS);
        return intervals.stream()
                .map(interval -> buildHourUsersUsageInfo(users, interval, filterUsers))
                .collect(Collectors.toList());
    }

    private UsersUsageInfo calculateDailyUsersUsageInfo(final List<OnlineUsers> users, final LocalDateTime from,
                                                        final Set<Long> filterUsers) {
        final LocalDateTime to = from.plusDays(1);
        final List<UsersUsageInfo> usersUsageByHour = calculateDayUsersUsageByHour(users, from, to, filterUsers);
        final List<String> totalUsersInInterval = usersUsageByHour.stream()
                .flatMap(usage -> usage.getActiveUsers().stream())
                .distinct()
                .collect(Collectors.toList());
        return UsersUsageInfo.builder()
                .totalUsers(totalUsersInInterval)
                .activeUsersCount(calculateSampleMedian(UsersUsageInfo::getActiveUsersCount, usersUsageByHour))
                .totalUsersCount(totalUsersInInterval.size())
                .periodStart(from)
                .periodEnd(to)
                .build();
    }

    private List<UsersUsageInfo> buildMonthUsersUsage(final List<OnlineUsers> users, final LocalDateTime from,
                                                      final LocalDateTime to, final Set<Long> filterUsers) {
        final List<LocalDateTime> intervals = buildTimeIntervals(from, to, ChronoUnit.DAYS);
        return intervals.stream()
                .map(interval -> calculateDailyUsersUsageInfo(users, interval, filterUsers))
                .collect(Collectors.toList());
    }

    private Set<Long> prepareUsers(final UsersUsageReportFilterVO filter) {
        if (CollectionUtils.isEmpty(filter.getRoles()) && CollectionUtils.isEmpty(filter.getUsers())) {
            return null;
        }
        final Set<Long> users = new HashSet<>();
        if (CollectionUtils.isNotEmpty(filter.getRoles())) {
            users.addAll(userManager.loadUsersByRoles(filter.getRoles()).stream()
                    .map(PipelineUser::getId)
                    .collect(Collectors.toList()));
        }
        if (CollectionUtils.isNotEmpty(filter.getUsers())) {
            users.addAll(userManager.loadUsersByNames(filter.getUsers()).stream()
                    .map(PipelineUser::getId)
                    .collect(Collectors.toList()));
        }
        return users;
    }
}
