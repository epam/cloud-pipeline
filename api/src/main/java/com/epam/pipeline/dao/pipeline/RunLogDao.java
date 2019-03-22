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

import java.sql.Timestamp;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.epam.pipeline.entity.pipeline.PipelineTask;
import com.epam.pipeline.entity.pipeline.RunLog;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcDaoSupport;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public class RunLogDao extends NamedParameterJdbcDaoSupport {

    private String createPipelineLogQuery;
    private String loadAllLogsByRunIdQuery;
    private String loadAllLogsForTaskQuery;
    private String loadTasksByRunIdQuery;
    private String loadTaskForInstanceQuery;
    private String loadTaskStatusQuery;
    private String deleteLogsByPipelineQuery;

    @Transactional(propagation = Propagation.MANDATORY)
    public void createRunLog(RunLog runLog) {
        getNamedParameterJdbcTemplate().update(createPipelineLogQuery, PipelineLogParameters
                .getParameters(runLog));
    }

    @Transactional(propagation = Propagation.SUPPORTS)
    public List<RunLog> loadAllLogsForRun(Long runId) {
        return getJdbcTemplate().query(loadAllLogsByRunIdQuery,
                PipelineLogParameters.getRowMapper(), runId);
    }

    @Transactional(propagation = Propagation.SUPPORTS)
    public List<RunLog> loadAllLogsForTask(Long runId, String taskName) {
        return getJdbcTemplate().query(loadAllLogsForTaskQuery,
                PipelineLogParameters.getRowMapper(), runId, taskName);
    }

    @Transactional(propagation = Propagation.SUPPORTS)
    public List<PipelineTask> loadTasksForRun(Long runId) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue(PipelineLogParameters.RUN_ID.name(), runId);
        List<PipelineTask> result =  getNamedParameterJdbcTemplate().query(loadTasksByRunIdQuery,
                params, PipelineLogParameters.getTaskRowMapper(true));
        return result.stream().filter(Objects::nonNull).collect(Collectors.toList());
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
    public void deleteLogsForPipeline(Long id) {
        getJdbcTemplate().update(deleteLogsByPipelineQuery, id);
    }

    enum PipelineLogParameters {
        RUN_ID,
        LOG_DATE,
        STATUS,
        LOG_TEXT,
        TASK_NAME,
        INSTANCE,
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

    @Required
    public void setCreatePipelineLogQuery(String createPipelineLogQuery) {
        this.createPipelineLogQuery = createPipelineLogQuery;
    }

    @Required
    public void setLoadAllLogsByRunIdQuery(String loadAllLogsByRunIdQuery) {
        this.loadAllLogsByRunIdQuery = loadAllLogsByRunIdQuery;
    }

    @Required
    public void setLoadTasksByRunIdQuery(String loadTasksByRunIdQuery) {
        this.loadTasksByRunIdQuery = loadTasksByRunIdQuery;
    }

    @Required
    public void setLoadAllLogsForTaskQuery(String loadAllLogsForTaskQuery) {
        this.loadAllLogsForTaskQuery = loadAllLogsForTaskQuery;
    }

    @Required
    public void setLoadTaskForInstanceQuery(String loadTaskForInstanceQuery) {
        this.loadTaskForInstanceQuery = loadTaskForInstanceQuery;
    }

    @Required
    public void setLoadTaskStatusQuery(String loadTaskStatusQuery) {
        this.loadTaskStatusQuery = loadTaskStatusQuery;
    }

    @Required
    public void setDeleteLogsByPipelineQuery(String deleteLogsByPipelineQuery) {
        this.deleteLogsByPipelineQuery = deleteLogsByPipelineQuery;
    }
}
