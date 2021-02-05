/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.test.creator.cluster.pool;

import com.epam.pipeline.entity.cluster.pool.NodeSchedule;
import com.epam.pipeline.entity.cluster.pool.ScheduleEntry;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public final class NodeScheduleCreatorUtils {

    public static final String SCHEDULE_NAME = "test";
    public static final LocalTime TEN_AM = LocalTime.of(10, 0, 0);
    public static final LocalTime SIX_PM = LocalTime.of(18, 0, 0);
    private NodeScheduleCreatorUtils() {
        // no op
    }

    public static NodeSchedule getWorkDaySchedule() {
        return getNodeSchedule(DayOfWeek.MONDAY, TEN_AM, DayOfWeek.FRIDAY, SIX_PM);
    }

    public static NodeSchedule getNodeSchedule(final DayOfWeek from, final LocalTime fromTime,
                                       final DayOfWeek to, final LocalTime toTime) {
        final NodeSchedule nodeSchedule = new NodeSchedule();
        nodeSchedule.setName(SCHEDULE_NAME);
        nodeSchedule.setCreated(LocalDateTime.now());
        final List<ScheduleEntry> entries = new ArrayList<>();
        final ScheduleEntry entry = createScheduleEntry(from, fromTime, to, toTime);
        entries.add(entry);
        nodeSchedule.setScheduleEntries(entries);
        return nodeSchedule;
    }

    public static ScheduleEntry createScheduleEntry(final DayOfWeek from, final LocalTime fromTime,
                                             final DayOfWeek to, final LocalTime toTime) {
        final ScheduleEntry entry = new ScheduleEntry();
        entry.setFrom(from);
        entry.setFromTime(fromTime);
        entry.setTo(to);
        entry.setToTime(toTime);
        return entry;
    }
}
