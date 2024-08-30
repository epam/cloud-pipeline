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
import org.junit.Test;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class UsersUsageReportServiceTest {
    private static final String USERNAME1 = "user1";
    private static final String USERNAME2 = "user2";
    private static final List<Long> USERS = Arrays.asList(1L, 2L);
    private static final List<String> USER_NAMES = Arrays.asList(USERNAME1, USERNAME2);
    private static final Set<Long> FILTER_USERS = Collections.singleton(1L);
    private static final List<String> FILTER_USER_NAMES = Collections.singletonList(USERNAME1);
    private static final int HOURS_IN_DAY = 24;
    private static final int JANUARY_DAYS_COUNT = 31;
    private static final int LOG_MINUTES_INTERVAL = 10;

    private final OnlineUsersService onlineUsersService = mock(OnlineUsersService.class);
    private final UserManager userManager = mock(UserManager.class);
    private final UsersUsageReportService usersUsageReportService =
            new UsersUsageReportService(onlineUsersService, userManager);

    @Test
    public void shouldCalculateDailyUsersUsageWithoutUsersFilter() {
        final LocalDateTime from = DateUtils.nowUTC().minusDays(1).toLocalDate().atStartOfDay();
        final LocalDateTime to = from.toLocalDate().atTime(LocalTime.MAX);

        final UsersUsageReportFilterVO filter = UsersUsageReportFilterVO.builder()
                .interval(ChronoUnit.HOURS)
                .from(from)
                .to(to)
                .build();
        doReturn(generateOnlineUsers(from, to)).when(onlineUsersService).getUsersByPeriod(from, to, null);
        doReturn(mockedUsers()).when(userManager).loadUsersById(USERS);

        final UsersUsageInfo expectedFirstResult = UsersUsageInfo.builder()
                .periodStart(from)
                .periodEnd(from.plusHours(1))
                .activeUsers(USER_NAMES)
                .activeUsersCount(USER_NAMES.size())
                .build();
        final UsersUsageInfo expectedLastResult = UsersUsageInfo.builder()
                .periodStart(from.plusHours(HOURS_IN_DAY - 1))
                .periodEnd(from.plusHours(HOURS_IN_DAY))
                .activeUsers(USER_NAMES)
                .activeUsersCount(USER_NAMES.size())
                .build();
        final List<UsersUsageInfo> result = usersUsageReportService.loadUsersUsage(filter);
        assertThat(result).hasSize(HOURS_IN_DAY);
        assertThat(result.get(0)).isEqualTo(expectedFirstResult);
        assertThat(result.get(HOURS_IN_DAY - 1)).isEqualTo(expectedLastResult);
    }

    @Test
    public void shouldCalculateDailyUsersUsageWithUsersFilter() {
        final LocalDateTime from = DateUtils.nowUTC().minusDays(1).toLocalDate().atStartOfDay();
        final LocalDateTime to = from.toLocalDate().atTime(LocalTime.MAX);

        final UsersUsageReportFilterVO filter = UsersUsageReportFilterVO.builder()
                .interval(ChronoUnit.HOURS)
                .from(from)
                .to(to)
                .users(FILTER_USER_NAMES)
                .build();
        doReturn(generateOnlineUsers(from, to)).when(onlineUsersService).getUsersByPeriod(from, to, FILTER_USERS);
        doReturn(mockedFilterUsers()).when(userManager).loadUsersById(Collections.singletonList(1L));
        doReturn(mockedFilterUsers()).when(userManager).loadUsersByNames(Collections.singletonList(USERNAME1));

        final UsersUsageInfo expectedFirstResult = UsersUsageInfo.builder()
                .periodStart(from)
                .periodEnd(from.plusHours(1))
                .activeUsers(FILTER_USER_NAMES)
                .activeUsersCount(FILTER_USER_NAMES.size())
                .build();
        final UsersUsageInfo expectedLastResult = UsersUsageInfo.builder()
                .periodStart(from.plusHours(HOURS_IN_DAY - 1))
                .periodEnd(from.plusHours(HOURS_IN_DAY))
                .activeUsers(FILTER_USER_NAMES)
                .activeUsersCount(FILTER_USER_NAMES.size())
                .build();
        final List<UsersUsageInfo> result = usersUsageReportService.loadUsersUsage(filter);
        assertThat(result).hasSize(HOURS_IN_DAY);
        assertThat(result.get(0)).isEqualTo(expectedFirstResult);
        assertThat(result.get(HOURS_IN_DAY - 1)).isEqualTo(expectedLastResult);
    }

    @Test
    public void shouldCalculateMonthlyUsersUsageWithoutUsersFilter() {
        final LocalDateTime from = DateUtils.nowUTC().toLocalDate()
                .minusYears(1)
                .withMonth(1)
                .withDayOfMonth(1)
                .atStartOfDay();
        final LocalDateTime to = from.toLocalDate().withDayOfMonth(JANUARY_DAYS_COUNT).atTime(LocalTime.MAX);

        final UsersUsageReportFilterVO filter = UsersUsageReportFilterVO.builder()
                .interval(ChronoUnit.DAYS)
                .from(from)
                .to(to)
                .build();
        doReturn(generateOnlineUsers(from, to)).when(onlineUsersService).getUsersByPeriod(from, to, null);
        doReturn(mockedUsers()).when(userManager).loadUsersById(USERS);

        final UsersUsageInfo expectedFirstResult = UsersUsageInfo.builder()
                .periodStart(from)
                .periodEnd(from.plusDays(1))
                .totalUsers(USER_NAMES)
                .totalUsersCount(USER_NAMES.size())
                .activeUsersCount(USER_NAMES.size())
                .build();
        final UsersUsageInfo expectedLastResult = UsersUsageInfo.builder()
                .periodStart(from.plusDays(JANUARY_DAYS_COUNT - 1))
                .periodEnd(from.plusDays(JANUARY_DAYS_COUNT))
                .totalUsers(USER_NAMES)
                .totalUsersCount(USER_NAMES.size())
                .activeUsersCount(USER_NAMES.size())
                .build();
        final List<UsersUsageInfo> result = usersUsageReportService.loadUsersUsage(filter);
        assertThat(result).hasSize(JANUARY_DAYS_COUNT);
        assertThat(result.get(0)).isEqualTo(expectedFirstResult);
        assertThat(result.get(JANUARY_DAYS_COUNT - 1)).isEqualTo(expectedLastResult);
    }

    @Test
    public void shouldCalculateMonthlyUsersUsageWithUsersFilter() {
        final LocalDateTime from = DateUtils.nowUTC().toLocalDate()
                .minusYears(1)
                .withMonth(1)
                .withDayOfMonth(1)
                .atStartOfDay();
        final LocalDateTime to = from.toLocalDate().withDayOfMonth(JANUARY_DAYS_COUNT).atTime(LocalTime.MAX);

        final UsersUsageReportFilterVO filter = UsersUsageReportFilterVO.builder()
                .interval(ChronoUnit.DAYS)
                .from(from)
                .to(to)
                .users(FILTER_USER_NAMES)
                .build();
        doReturn(generateOnlineUsers(from, to)).when(onlineUsersService).getUsersByPeriod(from, to, FILTER_USERS);
        doReturn(mockedFilterUsers()).when(userManager).loadUsersById(Collections.singletonList(1L));
        doReturn(mockedFilterUsers()).when(userManager).loadUsersByNames(Collections.singletonList(USERNAME1));

        final UsersUsageInfo expectedFirstResult = UsersUsageInfo.builder()
                .periodStart(from)
                .periodEnd(from.plusDays(1))
                .totalUsers(FILTER_USER_NAMES)
                .totalUsersCount(FILTER_USER_NAMES.size())
                .activeUsersCount(FILTER_USER_NAMES.size())
                .build();
        final UsersUsageInfo expectedLastResult = UsersUsageInfo.builder()
                .periodStart(from.plusDays(JANUARY_DAYS_COUNT - 1))
                .periodEnd(from.plusDays(JANUARY_DAYS_COUNT))
                .totalUsers(FILTER_USER_NAMES)
                .totalUsersCount(FILTER_USER_NAMES.size())
                .activeUsersCount(FILTER_USER_NAMES.size())
                .build();
        final List<UsersUsageInfo> result = usersUsageReportService.loadUsersUsage(filter);
        assertThat(result).hasSize(JANUARY_DAYS_COUNT);
        assertThat(result.get(0)).isEqualTo(expectedFirstResult);
        assertThat(result.get(JANUARY_DAYS_COUNT - 1)).isEqualTo(expectedLastResult);
    }

    private List<OnlineUsers> generateOnlineUsers(final LocalDateTime from, final LocalDateTime to) {
        return buildTimeIntervals(from, to).stream()
                .map(this::onlineUsers)
                .collect(Collectors.toList());
    }

    private OnlineUsers onlineUsers(final LocalDateTime logTime) {
        return OnlineUsers.builder()
                .logDate(logTime)
                .userIds(USERS)
                .build();
    }

    private List<LocalDateTime> buildTimeIntervals(final LocalDateTime from, final LocalDateTime to) {
        LocalDateTime intervalTime = from;
        final List<LocalDateTime> timeIntervals = new ArrayList<>();
        while (!intervalTime.isAfter(to)) {
            timeIntervals.add(intervalTime);
            intervalTime = intervalTime.plus(LOG_MINUTES_INTERVAL, ChronoUnit.MINUTES);
        }
        return timeIntervals;
    }

    private List<PipelineUser> mockedUsers() {
        return Arrays.asList(pipelineUser(1L, USERNAME1), pipelineUser(2L, USERNAME2));
    }

    private PipelineUser pipelineUser(final Long id, final String name) {
        final PipelineUser user = new PipelineUser();
        user.setId(id);
        user.setUserName(name);
        return user;
    }

    private List<PipelineUser> mockedFilterUsers() {
        return Collections.singletonList(pipelineUser(1L, USERNAME1));
    }
}
