/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

import java.sql.Timestamp;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.epam.pipeline.controller.vo.run.OffsetPagingFilter;
import com.epam.pipeline.dao.DaoUtils;
import com.epam.pipeline.dao.JdbcTemplateReadOnlyWrapper;
import com.epam.pipeline.entity.pipeline.PipelineTask;
import com.epam.pipeline.entity.pipeline.RunLog;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.exception.pipeline.RunLogException;
import com.epam.pipeline.utils.CommonUtils;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcDaoSupport;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
public class RunLogDao extends NamedParameterJdbcDaoSupport {

    private final JdbcTemplateReadOnlyWrapper jdbcTemplateReadOnlyWrapper;

    @Setter(onMethod_={@Required}) private String createPipelineLogQuery;
    @Setter(onMethod_={@Required}) private String loadLogsByRunIdQueryDesc;
    @Setter(onMethod_={@Required}) private String loadLogsByRunIdQueryAsc;
    @Setter(onMethod_={@Required}) private String loadLogsForTaskQueryDesc;
    @Setter(onMethod_={@Required}) private String loadLogsForTaskQueryAsc;
    @Setter(onMethod_={@Required}) private String loadTasksByRunIdQuery;
    @Setter(onMethod_={@Required}) private String loadTaskForInstanceQuery;
    @Setter(onMethod_={@Required}) private String loadTaskStatusQuery;
    @Setter(onMethod_={@Required}) private String deleteRunLogByRunIdsQuery;

    @Transactional(propagation = Propagation.MANDATORY)
    public void createRunLog(RunLog runLog) {
        getNamedParameterJdbcTemplate().update(createPipelineLogQuery, PipelineLogParameters
                .getParameters(runLog));
    }

    @Transactional(propagation = Propagation.SUPPORTS)
    public List<RunLog> loadLogsForRun(Long runId, OffsetPagingFilter filter) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue(PipelineLogParameters.RUN_ID.name(), runId);
        params.addValue(PipelineLogParameters.OFFSET.name(), filter.getOffset());
        params.addValue(PipelineLogParameters.LIMIT.name(), filter.getLimit());
        switch (filter.getOrder()) {
            case ASC:
                return ListUtils.emptyIfNull(getNamedParameterJdbcTemplate()
                        .query(loadLogsByRunIdQueryAsc, params, PipelineLogParameters.getRowMapper()));
            case DESC:
                return CommonUtils.reversed(ListUtils.emptyIfNull(getNamedParameterJdbcTemplate()
                        .query(loadLogsByRunIdQueryDesc, params, PipelineLogParameters.getRowMapper())));
            default:
                throw new RunLogException("Unsupported order");
        }
    }

    @Transactional(propagation = Propagation.SUPPORTS)
    public List<RunLog> loadLogsForTask(Long runId, String taskName, OffsetPagingFilter filter) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue(PipelineLogParameters.RUN_ID.name(), runId);
        params.addValue(PipelineLogParameters.TASK_NAME.name(), taskName);
        params.addValue(PipelineLogParameters.OFFSET.name(), filter.getOffset());
        params.addValue(PipelineLogParameters.LIMIT.name(), filter.getLimit());
        switch (filter.getOrder()) {
            case ASC:
                return ListUtils.emptyIfNull(getNamedParameterJdbcTemplate()
                        .query(loadLogsForTaskQueryAsc, params, PipelineLogParameters.getRowMapper()));
            case DESC:
                return CommonUtils.reversed(ListUtils.emptyIfNull(getNamedParameterJdbcTemplate()
                        .query(loadLogsForTaskQueryDesc, params, PipelineLogParameters.getRowMapper())));
            default:
                throw new RunLogException("Unsupported order");
        }
    }

    @Transactional(propagation = Propagation.SUPPORTS)
    public List<PipelineTask> loadTasksForRun(Long runId) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue(PipelineLogParameters.RUN_ID.name(), runId);
        return ListUtils.emptyIfNull(getNamedParameterJdbcTemplate()
                .query(loadTasksByRunIdQuery, params, PipelineLogParameters.getTaskRowMapper(true)))
                .stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Transactional(propagation = Propagation.SUPPORTS)
    public PipelineTask loadTaskStatus(Long runId, String taskName) {
        List<PipelineTask> result =  getJdbcTemplate().query(loadTaskStatusQuery,
                PipelineLogParameters.getTaskRowMapper(false), runId, taskName);
        return CollectionUtils.isEmpty(result) ? null : result.get(0);
    }

    @Transactional(propagation = Propagation.SUPPORTS)
    public List<PipelineTask> loadTaskByInstance(Long runId, String instance) {
        return getJdbcTemplate().query(loadTaskForInstanceQuery,
                PipelineLogParameters.getTaskRowMapper(false), runId, instance);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteTaskByRunIdsIn(final List<Long> runIds, final boolean dryRun) {
        final MapSqlParameterSource params = DaoUtils.longListParams(runIds);

        getNamedParameterJdbcTemplate(dryRun).update(deleteRunLogByRunIdsQuery, params);
    }

    enum PipelineLogParameters {
        RUN_ID,
        LOG_DATE,
        STATUS,
        LOG_TEXT,
        TASK_NAME,
        INSTANCE,
        OFFSET,
        LIMIT,
        CREATED,
        STARTED,
        FINISHED;

        static MapSqlParameterSource getParameters(RunLog runLog) {
            MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue(RUN_ID.name(), runLog.getRunId());
            params.addValue(LOG_DATE.name(), runLog.getDate());
            params.addValue(STATUS.name(), runLog.getStatus().getId());
            params.addValue(LOG_TEXT.name(), runLog.getLogText());
            params.addValue(TASK_NAME.name(), runLog.getTaskName());
            params.addValue(INSTANCE.name(), runLog.getInstance());
            return params;
        }

        static RowMapper<PipelineTask> getTaskRowMapper(boolean extended) {
            return (rs, rowNum) -> {
                String name = rs.getString(TASK_NAME.name());
                if (!rs.wasNull()) {
                    PipelineTask task =  new PipelineTask(name);
                    Long statusId = rs.getLong(STATUS.name());
                    if (!rs.wasNull()) {
                        task.setStatus(TaskStatus.getById(statusId));
                    }
                    String pod = rs.getString(INSTANCE.name());
                    if (!rs.wasNull()) {
                        task.setInstance(pod);
                    }
                    if (extended) {
                        task.setCreated(getDateValue(rs.getTimestamp(CREATED.name())));
                        task.setStarted(getDateValue(rs.getTimestamp(STARTED.name())));
                        task.setFinished(getDateValue(rs.getTimestamp(FINISHED.name())));
                    }
                    return task;
                } else {
                    return null;
                }
            };
        }

        static Date getDateValue(Timestamp time) {
            if (time == null) {
                return null;
            }
            return new Date(time.getTime());
        }

        static RowMapper<RunLog> getRowMapper() {
            return (rs, rowNum) -> {
                RunLog runLog = new RunLog();
                runLog.setRunId(rs.getLong(RUN_ID.name()));
                runLog.setDate(new Date(rs.getTimestamp(LOG_DATE.name()).getTime()));
                runLog.setStatus(TaskStatus.getById(rs.getLong(STATUS.name())));
                String text = rs.getString(LOG_TEXT.name());
                if (!rs.wasNull()) {
                    runLog.setLogText(text);
                }
                String taskName = rs.getString(TASK_NAME.name());
                if (!rs.wasNull()) {
                    runLog.setTask(new PipelineTask(taskName));
                }
                String instance = rs.getString(INSTANCE.name());
                if (!rs.wasNull()) {
                    runLog.setInstance(instance);
                }
                return runLog;
            };
        }
    }

    private NamedParameterJdbcTemplate getNamedParameterJdbcTemplate(final boolean dryRun) {
        return dryRun ? jdbcTemplateReadOnlyWrapper : getNamedParameterJdbcTemplate();
    }
}
