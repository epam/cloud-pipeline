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
import org.apache.commons.math3.stat.descriptive.rank.Median;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UsersUsageReportService {
    private final OnlineUsersService onlineUsersService;
    private final UserManager userManager;

    public List<UsersUsageInfo> loadUsersUsage(final UsersUsageReportFilterVO filter) {
        final UsersUsageReportFilterVO preparedFilter = prepareFilter(Objects.isNull(filter)
                ? new UsersUsageReportFilterVO()
                : filter);
        final LocalDateTime start = preparedFilter.getFrom().atStartOfDay();
        final LocalDateTime end = buildEndOfInterval(preparedFilter.getTo());
        final List<OnlineUsers> onlineUsers = onlineUsersService.getUsersByPeriod(start, end,
                preparedFilter.getUsers());
        if (ChronoUnit.HOURS == filter.getInterval()) {
            return calculateDayUsersUsageByHour(onlineUsers, start, end, preparedFilter.getUsers());
        }
        if (ChronoUnit.DAYS == filter.getInterval()) {
            return buildMonthUsersUsage(onlineUsers, start, end, preparedFilter.getUsers());
        }
        throw new UnsupportedOperationException(String.format("Time interval '%s' is not supported for now",
                filter.getInterval().name()));
    }

    private UsersUsageReportFilterVO prepareFilter(final UsersUsageReportFilterVO filter) {
        Assert.notNull(filter.getInterval(), "Interval must be specified");
        if (Objects.isNull(filter.getFrom())) {
            filter.setFrom(DateUtils.nowUTC().toLocalDate());
        }
        if (Objects.isNull(filter.getTo())) {
            filter.setTo(DateUtils.nowUTC().toLocalDate());
        }
        Assert.state(!filter.getFrom().isAfter(filter.getTo()), "'from' date must be before 'to' date");
        if (CollectionUtils.isNotEmpty(filter.getRoles())) {
            if (Objects.isNull(filter.getUsers())) {
                filter.setUsers(new HashSet<>());
            }
            filter.getUsers().addAll(userManager.loadUsersByRoles(filter.getRoles()).stream()
                    .map(PipelineUser::getId)
                    .collect(Collectors.toList()));
        }
        return filter;
    }

    private List<LocalDateTime> buildTimeIntervals(final LocalDateTime from, final LocalDateTime to,
                                                   final ChronoUnit intervalStep) {
        LocalDateTime intervalTime = from;
        final List<LocalDateTime> timeIntervals = new ArrayList<>();
        while (intervalTime.isBefore(to)) {
            timeIntervals.add(intervalTime);
            intervalTime = intervalTime.plus(1, intervalStep);
        }
        return timeIntervals;
    }

    private LocalDateTime buildEndOfInterval(final LocalDate to) {
        final LocalDateTime end = to.atTime(LocalTime.MAX);
        final LocalDateTime now = DateUtils.nowUTC();
        return end.isAfter(now) ? now : end;
    }

    private UsersUsageInfo buildHourUsersUsageInfo(final List<OnlineUsers> users, final LocalDateTime from,
                                                   final Set<Long> filterUsers) {
        final LocalDateTime to = from.plusHours(1);
        final List<Long> usersInInterval = users.stream()
                .filter(user -> user.getLogDate().isBefore(to) && !user.getLogDate().isBefore(from))
                .flatMap(user -> user.getUserIds().stream())
                .distinct()
                .filter(userId -> CollectionUtils.isEmpty(filterUsers) || filterUsers.contains(userId))
                .collect(Collectors.toList());
        return UsersUsageInfo.builder()
                .activeUsers(usersInInterval)
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
        final List<Long> totalUsersInInterval = usersUsageByHour.stream()
                .flatMap(usage -> usage.getActiveUsers().stream())
                .distinct()
                .collect(Collectors.toList());
        return UsersUsageInfo.builder()
                .totalUsers(totalUsersInInterval)
                .activeUsersCount(calculateMedianUsersCount(usersUsageByHour))
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

    private Integer calculateMedianUsersCount(final List<UsersUsageInfo> usersUsageByHour) {
        final double[] sample = usersUsageByHour.stream()
                .mapToDouble(UsersUsageInfo::getActiveUsersCount)
                .toArray();
        return (int) Math.round(new Median().evaluate(sample));
    }
}
