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
import com.epam.pipeline.manager.user.OnlineUsersService;
import com.epam.pipeline.manager.user.UserManager;
import org.junit.Test;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.epam.pipeline.util.ReportTestUtils.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class UsersUsageReportServiceTest {
    private static final List<Long> USERS = Arrays.asList(1L, 2L);
    private static final Set<Long> FILTER_USERS = Collections.singleton(1L);
    private static final List<Long> EXPECTED_FILTER_USERS = new ArrayList<>(FILTER_USERS);

    private final OnlineUsersService onlineUsersService = mock(OnlineUsersService.class);
    private final UserManager userManager = mock(UserManager.class);
    private final UsersUsageReportService usersUsageReportService =
            new UsersUsageReportService(onlineUsersService, userManager);

    @Test
    public void shouldCalculateDailyUsersUsageWithoutUsersFilter() {
        final LocalDateTime from = dayStart();
        final LocalDateTime to = dayEnd(from);

        final UsersUsageReportFilterVO filter = UsersUsageReportFilterVO.builder()
                .interval(ChronoUnit.HOURS)
                .from(from.toLocalDate())
                .to(to.toLocalDate())
                .build();
        doReturn(generateOnlineUsers(from, to)).when(onlineUsersService).getUsersByPeriod(from, to, null);

        final UsersUsageInfo expectedFirstResult = UsersUsageInfo.builder()
                .periodStart(from)
                .periodEnd(from.plusHours(1))
                .activeUsers(USERS)
                .activeUsersCount(USERS.size())
                .build();
        final UsersUsageInfo expectedLastResult = UsersUsageInfo.builder()
                .periodStart(from.plusHours(HOURS_IN_DAY - 1))
                .periodEnd(from.plusHours(HOURS_IN_DAY))
                .activeUsers(USERS)
                .activeUsersCount(USERS.size())
                .build();
        final List<UsersUsageInfo> result = usersUsageReportService.loadUsersUsage(filter);
        assertThat(result).hasSize(HOURS_IN_DAY);
        assertThat(result.get(0)).isEqualTo(expectedFirstResult);
        assertThat(result.get(HOURS_IN_DAY - 1)).isEqualTo(expectedLastResult);
    }

    @Test
    public void shouldCalculateDailyUsersUsageWithUsersFilter() {
        final LocalDateTime from = dayStart();
        final LocalDateTime to = dayEnd(from);

        final UsersUsageReportFilterVO filter = UsersUsageReportFilterVO.builder()
                .interval(ChronoUnit.HOURS)
                .from(from.toLocalDate())
                .to(to.toLocalDate())
                .users(FILTER_USERS)
                .build();
        doReturn(generateOnlineUsers(from, to)).when(onlineUsersService).getUsersByPeriod(from, to, FILTER_USERS);

        final UsersUsageInfo expectedFirstResult = UsersUsageInfo.builder()
                .periodStart(from)
                .periodEnd(from.plusHours(1))
                .activeUsers(EXPECTED_FILTER_USERS)
                .activeUsersCount(EXPECTED_FILTER_USERS.size())
                .build();
        final UsersUsageInfo expectedLastResult = UsersUsageInfo.builder()
                .periodStart(from.plusHours(HOURS_IN_DAY - 1))
                .periodEnd(from.plusHours(HOURS_IN_DAY))
                .activeUsers(EXPECTED_FILTER_USERS)
                .activeUsersCount(EXPECTED_FILTER_USERS.size())
                .build();
        final List<UsersUsageInfo> result = usersUsageReportService.loadUsersUsage(filter);
        assertThat(result).hasSize(HOURS_IN_DAY);
        assertThat(result.get(0)).isEqualTo(expectedFirstResult);
        assertThat(result.get(HOURS_IN_DAY - 1)).isEqualTo(expectedLastResult);
    }

    @Test
    public void shouldCalculateMonthlyUsersUsageWithoutUsersFilter() {
        final LocalDateTime from = monthStart();
        final LocalDateTime to = monthEnd(from);

        final UsersUsageReportFilterVO filter = UsersUsageReportFilterVO.builder()
                .interval(ChronoUnit.DAYS)
                .from(from.toLocalDate())
                .to(to.toLocalDate())
                .build();
        doReturn(generateOnlineUsers(from, to)).when(onlineUsersService).getUsersByPeriod(from, to, null);

        final UsersUsageInfo expectedFirstResult = UsersUsageInfo.builder()
                .periodStart(from)
                .periodEnd(from.plusDays(1))
                .totalUsers(USERS)
                .totalUsersCount(USERS.size())
                .activeUsersCount(USERS.size())
                .build();
        final UsersUsageInfo expectedLastResult = UsersUsageInfo.builder()
                .periodStart(from.plusDays(DAYS_IN_MONTH - 1))
                .periodEnd(from.plusDays(DAYS_IN_MONTH))
                .totalUsers(USERS)
                .totalUsersCount(USERS.size())
                .activeUsersCount(USERS.size())
                .build();
        final List<UsersUsageInfo> result = usersUsageReportService.loadUsersUsage(filter);
        assertThat(result).hasSize(DAYS_IN_MONTH);
        assertThat(result.get(0)).isEqualTo(expectedFirstResult);
        assertThat(result.get(DAYS_IN_MONTH - 1)).isEqualTo(expectedLastResult);
    }

    @Test
    public void shouldCalculateMonthlyUsersUsageWithUsersFilter() {
        final LocalDateTime from = monthStart();
        final LocalDateTime to = monthEnd(from);

        final UsersUsageReportFilterVO filter = UsersUsageReportFilterVO.builder()
                .interval(ChronoUnit.DAYS)
                .from(from.toLocalDate())
                .to(to.toLocalDate())
                .users(FILTER_USERS)
                .build();
        doReturn(generateOnlineUsers(from, to)).when(onlineUsersService).getUsersByPeriod(from, to, FILTER_USERS);

        final UsersUsageInfo expectedFirstResult = UsersUsageInfo.builder()
                .periodStart(from)
                .periodEnd(from.plusDays(1))
                .totalUsers(EXPECTED_FILTER_USERS)
                .totalUsersCount(EXPECTED_FILTER_USERS.size())
                .activeUsersCount(EXPECTED_FILTER_USERS.size())
                .build();
        final UsersUsageInfo expectedLastResult = UsersUsageInfo.builder()
                .periodStart(from.plusDays(DAYS_IN_MONTH - 1))
                .periodEnd(from.plusDays(DAYS_IN_MONTH))
                .totalUsers(EXPECTED_FILTER_USERS)
                .totalUsersCount(EXPECTED_FILTER_USERS.size())
                .activeUsersCount(EXPECTED_FILTER_USERS.size())
                .build();
        final List<UsersUsageInfo> result = usersUsageReportService.loadUsersUsage(filter);
        assertThat(result).hasSize(DAYS_IN_MONTH);
        assertThat(result.get(0)).isEqualTo(expectedFirstResult);
        assertThat(result.get(DAYS_IN_MONTH - 1)).isEqualTo(expectedLastResult);
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
}
