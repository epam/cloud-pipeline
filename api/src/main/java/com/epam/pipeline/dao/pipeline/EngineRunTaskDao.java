/*
 * Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.dao.DaoUtils;
import com.epam.pipeline.dao.DryRunJdbcDaoSupport;
import com.epam.pipeline.entity.pipeline.run.EngineRunTask;
import com.epam.pipeline.entity.pipeline.run.EngineRunTaskStatsEntity;
import com.epam.pipeline.entity.pipeline.run.EngineTaskStatus;
import com.epam.pipeline.entity.pipeline.run.EngineType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
public class EngineRunTaskDao extends DryRunJdbcDaoSupport {

    private String upsertEngineRunTaskQuery;
    private String deleteEngineRunTaskByRunIdsQuery;
    private String findEngineRunTaskByRunIdQuery;
    private String loadEngineRunTasksStatsByRunIdQuery;

    @Transactional(propagation = Propagation.MANDATORY)
    public List<EngineRunTask> batchUpsert(final List<EngineRunTask> tasks) {
        getNamedParameterJdbcTemplate().batchUpdate(upsertEngineRunTaskQuery,
                EngineRunTaskDao.Parameters.getBatchParameters(tasks));
        return tasks;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteByRunIdIn(final List<Long> runIds, final boolean dryRun) {
        final MapSqlParameterSource params = DaoUtils.longListParams(runIds);
        getNamedParameterJdbcTemplate(dryRun).update(deleteEngineRunTaskByRunIdsQuery, params);
    }

    public List<EngineRunTask> findByRunId(final Long runId) {
        return getNamedParameterJdbcTemplate().query(findEngineRunTaskByRunIdQuery,
                Parameters.buildRunIdParameter(runId), Parameters.getRowMapper());
    }

    public List<EngineRunTaskStatsEntity> loadStats(final Long runId) {
        return getNamedParameterJdbcTemplate().query(loadEngineRunTasksStatsByRunIdQuery,
                Parameters.buildRunIdParameter(runId), Parameters.getStatsRowMapper());
    }

    enum Parameters {
        TASK_ID,
        TASK_NAME,
        TASK_KEY,
        TASK_GROUP,
        PARENT_ID,
        ENGINE_TYPE,
        STATUS,
        DATA,
        START_DATE,
        END_DATE,
        RUN_ID,
        DURATION,
        TASK_TAG,
        TASKS_COUNT;

        private static MapSqlParameterSource[] getBatchParameters(final List<EngineRunTask> tasks) {
            return tasks.stream()
                    .map(Parameters::getParameters)
                    .toArray(MapSqlParameterSource[]::new);
        }

        private static MapSqlParameterSource getParameters(final EngineRunTask task) {
            return new MapSqlParameterSource()
                    .addValue(TASK_ID.name(), task.getTaskId())
                    .addValue(TASK_NAME.name(), task.getTaskName())
                    .addValue(TASK_KEY.name(), task.getTaskKey())
                    .addValue(TASK_TAG.name(), task.getTaskTag())
                    .addValue(TASK_GROUP.name(), task.getTaskGroup())
                    .addValue(PARENT_ID.name(), task.getParentId())
                    .addValue(ENGINE_TYPE.name(), task.getEngineType().name())
                    .addValue(STATUS.name(), task.getStatus().name())
                    .addValue(DATA.name(), task.getAttributes())
                    .addValue(START_DATE.name(), task.getStartDateTime())
                    .addValue(END_DATE.name(), task.getEndDateTime())
                    .addValue(RUN_ID.name(), task.getRunId())
                    .addValue(DURATION.name(), task.getDuration());
        }

        private static RowMapper<EngineRunTask> getRowMapper() {
            return (rs, rowNum) -> EngineRunTask.builder()
                    .runId(rs.getLong(RUN_ID.name()))
                    .taskId(rs.getString(TASK_ID.name()))
                    .taskName(rs.getString(TASK_NAME.name()))
                    .taskKey(rs.getString(TASK_KEY.name()))
                    .taskTag(rs.getString(TASK_TAG.name()))
                    .taskGroup(rs.getString(TASK_GROUP.name()))
                    .parentId(rs.getString(PARENT_ID.name()))
                    .engineType(EngineType.valueOf(rs.getString(ENGINE_TYPE.name())))
                    .status(EngineTaskStatus.valueOf(rs.getString(STATUS.name())))
                    .duration(rs.getLong(DURATION.name()))
                    .startDateTime(rs.getDate(START_DATE.name()))
                    .endDateTime(rs.getDate(END_DATE.name()))
                    .build();
        }

        private static RowMapper<EngineRunTaskStatsEntity> getStatsRowMapper() {
            return (rs, rowNum) -> EngineRunTaskStatsEntity.builder()
                    .engineType(EngineType.valueOf(rs.getString(ENGINE_TYPE.name())))
                    .taskGroup(rs.getString(TASK_GROUP.name()))
                    .status(EngineTaskStatus.valueOf(rs.getString(STATUS.name())))
                    .tasksCount(rs.getLong(TASKS_COUNT.name()))
                    .build();
        }

        private static MapSqlParameterSource buildRunIdParameter(final Long runId) {
            return new MapSqlParameterSource().addValue(RUN_ID.name(), runId);
        }
    }

    @Required
    public void setUpsertEngineRunTaskQuery(final String upsertEngineRunTaskQuery) {
        this.upsertEngineRunTaskQuery = upsertEngineRunTaskQuery;
    }

    @Required
    public void setDeleteEngineRunTaskByRunIdsQuery(final String deleteEngineRunTaskByRunIdsQuery) {
        this.deleteEngineRunTaskByRunIdsQuery = deleteEngineRunTaskByRunIdsQuery;
    }

    @Required
    public void setFindEngineRunTaskByRunIdQuery(final String findEngineRunTaskByRunIdQuery) {
        this.findEngineRunTaskByRunIdQuery = findEngineRunTaskByRunIdQuery;
    }

    @Required
    public void setLoadEngineRunTasksStatsByRunIdQuery(final String loadEngineRunTasksStatsByRunIdQuery) {
        this.loadEngineRunTasksStatsByRunIdQuery = loadEngineRunTasksStatsByRunIdQuery;
    }
}
