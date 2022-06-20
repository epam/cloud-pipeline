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

package com.epam.pipeline.dao.cluster.pool;

import com.epam.pipeline.entity.cluster.pool.NodeSchedule;
import com.epam.pipeline.entity.cluster.pool.ScheduleEntry;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.ListUtils;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcDaoSupport;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RequiredArgsConstructor
public class NodeScheduleDao extends NamedParameterJdbcDaoSupport {

    private final String insertScheduleQuery;
    private final String insertScheduleEntryQuery;
    private final String updateScheduleQuery;
    private final String deleteScheduleQuery;
    private final String deleteScheduleEntriesQuery;
    private final String loadScheduleByIdQuery;
    private final String loadAllSchedulesQuery;

    @Transactional(propagation = Propagation.MANDATORY)
    public NodeSchedule create(final NodeSchedule schedule) {
        final KeyHolder keyHolder = new GeneratedKeyHolder();
        getNamedParameterJdbcTemplate()
                .update(insertScheduleQuery,
                        NodeScheduleParameters.getParameters(schedule),
                        keyHolder,
                        new String[] { "id" });
        schedule.setId(keyHolder.getKey().longValue());
        insertScheduleEntries(schedule);
        return schedule;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public NodeSchedule update(final NodeSchedule schedule) {
        getNamedParameterJdbcTemplate()
                .update(updateScheduleQuery, NodeScheduleParameters.getParameters(schedule));
        deleteScheduleEntries(schedule.getId());
        insertScheduleEntries(schedule);
        return schedule;
    }

    public Optional<NodeSchedule> find(final Long id) {
        final List<NodeSchedule> result = getJdbcTemplate().query(loadScheduleByIdQuery,
                NodeScheduleParameters.getExtractor(), id);
        return ListUtils.emptyIfNull(result).stream().findFirst();
    }

    public List<NodeSchedule> loadAll() {
        return getJdbcTemplate().query(loadAllSchedulesQuery, NodeScheduleParameters.getExtractor());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void delete(final Long scheduleId) {
        getJdbcTemplate().update(deleteScheduleQuery, scheduleId);
    }

    private void insertScheduleEntries(final NodeSchedule schedule) {
        final MapSqlParameterSource[] params = ListUtils.emptyIfNull(schedule.getScheduleEntries())
                .stream().map(entry -> NodeScheduleParameters.getParameters(entry, schedule.getId()))
                .toArray(MapSqlParameterSource[]::new);
        getNamedParameterJdbcTemplate().batchUpdate(insertScheduleEntryQuery, params);
    }

    private void deleteScheduleEntries(final Long scheduleId) {
        getJdbcTemplate().update(deleteScheduleEntriesQuery, scheduleId);
    }

    enum NodeScheduleParameters {
        SCHEDULE_ID,
        SCHEDULE_NAME,
        SCHEDULE_CREATED_DATE,
        FROM_DAY_OF_WEEK,
        FROM_TIME,
        TO_DAY_OF_WEEK,
        TO_TIME;

        static MapSqlParameterSource getParameters(final NodeSchedule schedule) {
            final MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue(SCHEDULE_NAME.name(), schedule.getName());
            params.addValue(SCHEDULE_ID.name(), schedule.getId());
            params.addValue(SCHEDULE_CREATED_DATE.name(), schedule.getCreated());
            return params;
        }

        static MapSqlParameterSource getParameters(final ScheduleEntry entry, final long scheduleId) {
            final MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue(SCHEDULE_ID.name(), scheduleId);
            params.addValue(FROM_DAY_OF_WEEK.name(), entry.getFrom().getValue());
            params.addValue(FROM_TIME.name(), entry.getFromTime().toString().equals("00:00") ? LocalDateTime.MIN :
                    entry.getFromTime());
            params.addValue(TO_DAY_OF_WEEK.name(), entry.getTo().getValue());
            params.addValue(TO_TIME.name(), entry.getToTime().toString().equals("00:00") ? LocalDateTime.MIN :
                    entry.getToTime());
            return params;
        }

        static ResultSetExtractor<List<NodeSchedule>> getExtractor() {
            return (rs) -> {
                final Map<Long, NodeSchedule> schedules = new HashMap<>();
                while (rs.next()) {
                    final Long scheduleId = rs.getLong(SCHEDULE_ID.name());
                    if (!schedules.containsKey(scheduleId)) {
                        final NodeSchedule schedule = parseNodeSchedule(rs);
                        schedules.put(scheduleId, schedule);
                    }
                    final NodeSchedule schedule = schedules.get(scheduleId);
                    final ScheduleEntry entry = parseScheduleEntry(rs);
                    schedule.getScheduleEntries().add(entry);
                }
                return new ArrayList<>(schedules.values());
            };
        }

        static ScheduleEntry parseScheduleEntry(final ResultSet rs) throws SQLException {
            final ScheduleEntry entry = new ScheduleEntry();
            entry.setFrom(DayOfWeek.of(rs.getInt(FROM_DAY_OF_WEEK.name())));
            final Time fromTime = rs.getTime(FROM_TIME.name());
            entry.setFromTime(!rs.wasNull() ? fromTime.toLocalTime() : LocalTime.MIN);
            entry.setTo(DayOfWeek.of(rs.getInt(TO_DAY_OF_WEEK.name())));
            final Time toTime = rs.getTime(TO_TIME.name());
            entry.setToTime(!rs.wasNull() ? toTime.toLocalTime() : LocalTime.MIN);
            return entry;
        }

        static NodeSchedule parseNodeSchedule(final ResultSet rs) throws SQLException {
            final NodeSchedule nodeSchedule = new NodeSchedule();
            nodeSchedule.setId(rs.getLong(SCHEDULE_ID.name()));
            nodeSchedule.setName(rs.getString(SCHEDULE_NAME.name()));
            nodeSchedule.setCreated(rs.getTimestamp(SCHEDULE_CREATED_DATE.name()).toLocalDateTime());
            nodeSchedule.setScheduleEntries(new ArrayList<>());
            return nodeSchedule;
        }
    }
}
