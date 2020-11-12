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

package com.epam.pipeline.dao.cluster.schedule;

import com.epam.pipeline.entity.cluster.schedule.NodeSchedule;
import com.epam.pipeline.entity.cluster.schedule.ScheduleEntry;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcDaoSupport;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Collections;
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
        return null;
    }

    public Optional<NodeSchedule> find(final Long id) {
        final List<NodeSchedule> result = getJdbcTemplate().query(loadScheduleByIdQuery,
                NodeScheduleParameters.getNodeExtractor(), id);
        return ListUtils.emptyIfNull(result).stream().findFirst();
    }

    public List<NodeSchedule> loadAll() {
        return getJdbcTemplate().query(loadAllSchedulesQuery, NodeScheduleParameters.getNodeExtractor());
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
            params.addValue(FROM_TIME.name(), entry.getFromTime());
            params.addValue(TO_DAY_OF_WEEK.name(), entry.getTo().getValue());
            params.addValue(TO_TIME.name(), entry.getToTime());
            return params;
        }

        static ResultSetExtractor<List<NodeSchedule>> getNodeExtractor() {
            return (rs) -> {
                final Map<Long, NodeSchedule> schedules = new HashMap<>();
                while (rs.next()) {
                    final Long scheduleId = rs.getLong(SCHEDULE_ID.name());
                    if (!schedules.containsKey(scheduleId)) {
                        final NodeSchedule nodeSchedule = parseNodeSchedule(rs, scheduleId);
                        schedules.put(scheduleId, nodeSchedule);
                    }
                    final NodeSchedule schedule = schedules.get(scheduleId);
                    int fromOrdinal = rs.getInt(FROM_DAY_OF_WEEK.name());
                    if (fromOrdinal >= 0) {
                        final ScheduleEntry entry = parseScheduleEntry(rs);
                        schedule.getScheduleEntries().add(entry);
                    }
                }
                if (MapUtils.isEmpty(schedules)) {
                    return Collections.emptyList();
                }
                return new ArrayList<>(schedules.values());
            };
        }

        private static ScheduleEntry parseScheduleEntry(final ResultSet rs) throws SQLException {
            final ScheduleEntry entry = new ScheduleEntry();
            entry.setFrom(DayOfWeek.of(rs.getInt(FROM_DAY_OF_WEEK.name())));
            entry.setFromTime(rs.getTime(FROM_TIME.name()).toLocalTime());
            entry.setTo(DayOfWeek.of(rs.getInt(TO_DAY_OF_WEEK.name())));
            entry.setToTime(rs.getTime(TO_TIME.name()).toLocalTime());
            return entry;
        }

        private static NodeSchedule parseNodeSchedule(final ResultSet rs,
                                                      final Long scheduleId) throws SQLException {
            final NodeSchedule nodeSchedule = new NodeSchedule();
            nodeSchedule.setId(scheduleId);
            nodeSchedule.setName(rs.getString(SCHEDULE_NAME.name()));
            nodeSchedule.setCreated(rs.getTimestamp(SCHEDULE_CREATED_DATE.name()).toLocalDateTime());
            nodeSchedule.setScheduleEntries(new ArrayList<>());
            return nodeSchedule;
        }
    }
}
