/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.entity.cluster.schedule;

import lombok.Data;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;

@Data
public class ScheduleEntry {
    private DayOfWeek from;
    private LocalTime fromTime;
    private DayOfWeek to;
    private LocalTime toTime;

    public boolean isActive(final LocalDateTime timestamp) {
        final LocalDateTime start = getFromDay(timestamp).with(fromTime);
        final LocalDateTime end = getToDay(timestamp, start).with(toTime);
        return timestamp.compareTo(start) >= 0 && timestamp.isBefore(end);
    }

    private LocalDateTime getFromDay(final LocalDateTime timestamp) {
        return timestamp.getDayOfWeek().equals(from) ? timestamp :
                timestamp.with(TemporalAdjusters.previous(from));
    }

    private LocalDateTime getToDay(final LocalDateTime timestamp,
                                   final LocalDateTime start) {
        if (timestamp.getDayOfWeek().equals(to)) {
            return timestamp;
        }
        if (from.equals(to)) {
            return start;
        }
        return start.with(TemporalAdjusters.next(to));
    }
}
