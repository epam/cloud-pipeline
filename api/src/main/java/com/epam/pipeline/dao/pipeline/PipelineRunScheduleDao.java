/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.dao.pipeline;

import com.epam.pipeline.dao.DaoHelper;
import com.epam.pipeline.entity.pipeline.run.RunSchedule;
import com.epam.pipeline.entity.pipeline.run.RunScheduledAction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcDaoSupport;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;
import java.util.stream.Collectors;

public class PipelineRunScheduleDao  extends NamedParameterJdbcDaoSupport {

    @Autowired
    private DaoHelper daoHelper;

    private String scheduleSequence;
    private String createRunScheduleQuery;
    private String updateRunScheduleQuery;
    private String deleteRunScheduleQuery;
    private String deleteRunSchedulesForRunQuery;
    private String loadRunScheduleQuery;
    private String loadAllRunSchedulesQuery;
    private String loadAllRunSchedulesByRunIdQuery;

    @Transactional(propagation = Propagation.MANDATORY)
    public Long createScheduleId() {
        return daoHelper.createId(scheduleSequence);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void createRunSchedules(final List<RunSchedule> schedules) {
        final MapSqlParameterSource[] params = schedules.stream()
            .peek(s -> s.setId(this.createScheduleId()))
            .map(RunScheduleParameters::getParameters)
            .collect(Collectors.toList())
            .toArray(new MapSqlParameterSource[schedules.size()]);
        getNamedParameterJdbcTemplate().batchUpdate(createRunScheduleQuery, params);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void updateRunSchedules(final List<RunSchedule> schedules) {
        final MapSqlParameterSource[] params = schedules.stream()
            .map(RunScheduleParameters::getParameters)
            .collect(Collectors.toList())
            .toArray(new MapSqlParameterSource[schedules.size()]);
        getNamedParameterJdbcTemplate().batchUpdate(updateRunScheduleQuery, params);
    }

    public List<RunSchedule> loadAllRunSchedulesByRunId(final Long runId) {
        return getJdbcTemplate().query(loadAllRunSchedulesByRunIdQuery,
                                       PipelineRunScheduleDao.RunScheduleParameters.getRowMapper(), runId);
    }

    public List<RunSchedule> loadAllRunSchedules() {
        return getJdbcTemplate().query(loadAllRunSchedulesQuery,
                                       PipelineRunScheduleDao.RunScheduleParameters.getRowMapper());
    }

    public Optional<RunSchedule> loadRunSchedule(final Long id) {
        return getJdbcTemplate()
            .query(loadRunScheduleQuery,
                   PipelineRunScheduleDao.RunScheduleParameters.getRowMapper(), id).stream().findFirst();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteRunSchedules(final List<Long> ids) {
        final MapSqlParameterSource[] params = ids.stream()
            .map(RunScheduleParameters::getId)
            .collect(Collectors.toList())
            .toArray(new MapSqlParameterSource[ids.size()]);
        getNamedParameterJdbcTemplate().batchUpdate(deleteRunScheduleQuery, params);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteRunSchedulesForRun(final Long runId) {
        getJdbcTemplate().update(deleteRunSchedulesForRunQuery, runId);
    }

    enum RunScheduleParameters {
        ID,
        ACTION,
        RUN_ID,
        CRON_EXPRESSION,
        CREATED_DATE,
        TIME_ZONE;

        static MapSqlParameterSource getParameters(final RunSchedule schedule) {
            final MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue(ID.name(), schedule.getId());
            params.addValue(ACTION.name(), schedule.getAction().getId());
            params.addValue(RUN_ID.name(), schedule.getRunId());
            params.addValue(CRON_EXPRESSION.name(), schedule.getCronExpression());
            params.addValue(CREATED_DATE.name(), schedule.getCreatedDate());
            params.addValue(TIME_ZONE.name(), schedule.getTimeZone().getDisplayName());
            return params;
        }

        static MapSqlParameterSource getId(final Long id) {
            final MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue(RunScheduleParameters.ID.name(), id);
            return params;
        }

        static RowMapper<RunSchedule> getRowMapper() {
            return (rs, rowNum) -> {
                final RunSchedule schedule = new RunSchedule();
                schedule.setId(rs.getLong(ID.name()));
                schedule.setAction(RunScheduledAction.getById(rs.getLong(ACTION.name())));
                schedule.setRunId(rs.getLong(RUN_ID.name()));
                schedule.setCronExpression(rs.getString(CRON_EXPRESSION.name()));
                schedule.setCreatedDate(new Date(rs.getTimestamp(CREATED_DATE.name()).getTime()));
                schedule.setTimeZone(TimeZone.getTimeZone(rs.getString(TIME_ZONE.name())));
                return schedule;
            };
        }
    }

    @Required
    public void setScheduleSequence(final String scheduleSequence) {
        this.scheduleSequence = scheduleSequence;
    }

    @Required
    public void setCreateRunScheduleQuery(final String createRunScheduleQuery) {
        this.createRunScheduleQuery = createRunScheduleQuery;
    }

    @Required
    public void setUpdateRunScheduleQuery(final String updateRunScheduleQuery) {
        this.updateRunScheduleQuery = updateRunScheduleQuery;
    }

    @Required
    public void setDeleteRunScheduleQuery(final String deleteRunScheduleQuery) {
        this.deleteRunScheduleQuery = deleteRunScheduleQuery;
    }

    @Required
    public void setLoadRunScheduleQuery(final String loadRunScheduleQuery) {
        this.loadRunScheduleQuery = loadRunScheduleQuery;
    }

    @Required
    public void setLoadAllRunSchedulesByRunIdQuery(final String loadAllRunSchedulesByRunIdQuery) {
        this.loadAllRunSchedulesByRunIdQuery = loadAllRunSchedulesByRunIdQuery;
    }

    @Required
    public void setLoadAllRunSchedulesQuery(final String loadAllRunSchedulesQuery) {
        this.loadAllRunSchedulesQuery = loadAllRunSchedulesQuery;
    }

    @Required
    public void setDeleteRunSchedulesForRunQuery(final String deleteRunSchedulesForRunQuery) {
        this.deleteRunSchedulesForRunQuery = deleteRunSchedulesForRunQuery;
    }

}
