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

package com.epam.pipeline.entity.cluster.pool;

import org.junit.Test;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class NodeScheduleTest {

    private static final LocalTime TEN_AM = LocalTime.of(10, 0, 0);
    private static final LocalTime SIX_PM = LocalTime.of(18, 0, 0);
    private static final LocalTime TWELVE_AM = LocalTime.of(12, 0);

    @Test
    public void shouldBeActiveAtScheduleStart() {
        final NodeSchedule nodeSchedule = createWorkDaySchedule();
        final LocalDateTime scheduleStartTimestamp = getTimestamp(DayOfWeek.MONDAY, TEN_AM);
        assertTrue(nodeSchedule.isActive(scheduleStartTimestamp));
    }

    @Test
    public void shouldBeActiveBetweenStartAndEnd() {
        final NodeSchedule nodeSchedule = createWorkDaySchedule();
        final LocalDateTime betweenTimestamp = getTimestamp(DayOfWeek.TUESDAY);
        assertTrue(nodeSchedule.isActive(betweenTimestamp));
    }

    @Test
    public void shouldNotBeActiveAtScheduleEnd() {
        final NodeSchedule nodeSchedule = createWorkDaySchedule();
        final LocalDateTime fridayAfterTimestamp = getTimestamp(DayOfWeek.FRIDAY, SIX_PM);
        assertFalse(nodeSchedule.isActive(fridayAfterTimestamp));
    }

    @Test
    public void shouldNotBeActiveOutsideSchedule() {
        final NodeSchedule nodeSchedule = createWorkDaySchedule();
        final LocalDateTime saturdayTimestamp = getTimestamp(DayOfWeek.SATURDAY);
        assertFalse(nodeSchedule.isActive(saturdayTimestamp));
        final LocalDateTime sundayTimestamp = getTimestamp(DayOfWeek.SUNDAY);
        assertFalse(nodeSchedule.isActive(sundayTimestamp));
    }

    @Test
    public void shouldNotBeActiveBeforeScheduleStart() {
        final NodeSchedule nodeSchedule = createWorkDaySchedule();
        final LocalDateTime scheduleStartTimestamp = getTimestamp(DayOfWeek.MONDAY, TEN_AM.minusSeconds(1));
        assertFalse(nodeSchedule.isActive(scheduleStartTimestamp));
    }

    @Test
    public void shouldNotBeActiveAfterScheduleEnd() {
        final NodeSchedule nodeSchedule = createWorkDaySchedule();
        final LocalDateTime scheduleStartTimestamp = getTimestamp(DayOfWeek.FRIDAY, SIX_PM.plusSeconds(1));
        assertFalse(nodeSchedule.isActive(scheduleStartTimestamp));
    }

    @Test
    public void shouldBeActiveBetweenStartAndEndForOneDaySchedule() {
        final NodeSchedule nodeSchedule = createMondaySchedule();
        final LocalDateTime betweenTimestamp = getTimestamp(DayOfWeek.MONDAY, TWELVE_AM);
        assertTrue(nodeSchedule.isActive(betweenTimestamp));
    }

    @Test
    public void shouldNotBeActiveOutsideStartAndEndForOneDaySchedule() {
        final NodeSchedule nodeSchedule = createMondaySchedule();
        final LocalDateTime otherDayTimestamp = getTimestamp(DayOfWeek.TUESDAY);
        assertFalse(nodeSchedule.isActive(otherDayTimestamp));

        final LocalDateTime beforeStart = getTimestamp(DayOfWeek.MONDAY, TEN_AM.minusSeconds(1));
        assertFalse(nodeSchedule.isActive(beforeStart));

        final LocalDateTime afterEnd = getTimestamp(DayOfWeek.MONDAY, SIX_PM.plusSeconds(1));
        assertFalse(nodeSchedule.isActive(afterEnd));
    }

    @Test
    public void shouldBeActiveIfAnyOfSchedulesMatch() {
        final NodeSchedule schedule = createWorkDaySchedule();
        schedule.getScheduleEntries()
                .add(createScheduleEntry(DayOfWeek.SATURDAY, TEN_AM, DayOfWeek.SUNDAY, SIX_PM));
        assertTrue(schedule.isActive(getTimestamp(DayOfWeek.SATURDAY, TWELVE_AM)));
    }

    public LocalDateTime getTimestamp(final DayOfWeek dayOfWeek, final LocalTime time) {
        return LocalDateTime.now()
                .with(TemporalAdjusters.previous(dayOfWeek))
                .with(time);
    }

    public LocalDateTime getTimestamp(final DayOfWeek dayOfWeek) {
        return LocalDateTime.now()
                .with(TemporalAdjusters.previous(dayOfWeek));
    }

    public NodeSchedule createWorkDaySchedule() {
        return createSchedule(DayOfWeek.MONDAY, TEN_AM, DayOfWeek.FRIDAY, SIX_PM);
    }

    public NodeSchedule createMondaySchedule() {
        return createSchedule(DayOfWeek.MONDAY, TEN_AM, DayOfWeek.MONDAY, SIX_PM);
    }

    public NodeSchedule createSchedule(final DayOfWeek from, final LocalTime fromTime,
                                       final DayOfWeek to, final LocalTime toTime) {
        final NodeSchedule nodeSchedule = new NodeSchedule();
        final List<ScheduleEntry> entries = new ArrayList<>();
        final ScheduleEntry entry = createScheduleEntry(from, fromTime, to, toTime);
        entries.add(entry);
        nodeSchedule.setScheduleEntries(entries);
        return nodeSchedule;
    }

    public ScheduleEntry createScheduleEntry(final DayOfWeek from, final LocalTime fromTime,
                                             final DayOfWeek to, final LocalTime toTime) {
        final ScheduleEntry entry = new ScheduleEntry();
        entry.setFrom(from);
        entry.setFromTime(fromTime);
        entry.setTo(to);
        entry.setToTime(toTime);
        return entry;
    }
}