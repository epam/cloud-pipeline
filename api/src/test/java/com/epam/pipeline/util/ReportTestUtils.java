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

package com.epam.pipeline.util;

import com.epam.pipeline.entity.utils.DateUtils;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public interface ReportTestUtils {
    int HOURS_IN_DAY = 24;
    int DAYS_IN_MONTH = 31;
    int LOG_MINUTES_INTERVAL = 10;

    static LocalDateTime monthStart() {
        return DateUtils.nowUTC().toLocalDate()
                .minusYears(1)
                .withMonth(1)
                .withDayOfMonth(1)
                .atStartOfDay();
    }

    static LocalDateTime monthEnd(final LocalDateTime from) {
        return from.toLocalDate().withDayOfMonth(DAYS_IN_MONTH).atTime(LocalTime.MAX);
    }

    static LocalDateTime dayStart() {
        return DateUtils.nowUTC().minusDays(1).toLocalDate().atStartOfDay();
    }

    static LocalDateTime dayEnd(final LocalDateTime from) {
        return from.toLocalDate().atTime(LocalTime.MAX);
    }

    static List<LocalDateTime> buildTimeIntervals(final LocalDateTime from, final LocalDateTime to) {
        LocalDateTime intervalTime = from;
        final List<LocalDateTime> timeIntervals = new ArrayList<>();
        while (!intervalTime.isAfter(to)) {
            timeIntervals.add(intervalTime);
            intervalTime = intervalTime.plus(LOG_MINUTES_INTERVAL, ChronoUnit.MINUTES);
        }
        return timeIntervals;
    }
}
