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
import com.epam.pipeline.entity.pipeline.run.EngineRunTaskSortVO;
import com.epam.pipeline.entity.pipeline.run.EngineRunTask;
import com.epam.pipeline.entity.pipeline.run.EngineRunTaskFilter;
import com.epam.pipeline.entity.run.EngineRunTaskStatsEntity;
import com.epam.pipeline.entity.pipeline.run.EngineTaskStatus;
import com.epam.pipeline.entity.pipeline.run.EngineType;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class EngineRunTaskDao extends DryRunJdbcDaoSupport {
    private static final Pattern WHERE_PATTERN = Pattern.compile("@WHERE@");
    private static final Pattern SORT_PATTERN = Pattern.compile("@SORT@");
    private static final int CLAUSE_LENGTH = 200;
    private static final String AND = " AND ";

    private String upsertEngineRunTaskQuery;
    private String deleteEngineRunTaskByRunIdsQuery;
    private String loadEngineRunTasksStatsByRunIdAndTypeQuery;
    private String findEngineRunTaskByRunIdAndTypeQuery;
    private String countEngineRunTaskByRunIdAndTypeQuery;
    private String loadEngineRunTasksByKeysQuery;

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

    public List<EngineRunTaskStatsEntity> loadStats(final Long runId, final EngineType engineType) {
        return getNamedParameterJdbcTemplate().query(loadEngineRunTasksStatsByRunIdAndTypeQuery,
                Parameters.buildRunIdAndTypeParameter(runId, engineType),
                Parameters.getStatsRowMapper());
    }

    public List<EngineRunTask> filterTasksByRunIdAndTypeAndFilter(final Long runId, final EngineType engineType,
                                                                  final EngineRunTaskFilter filter) {
        final MapSqlParameterSource parameters = Parameters.buildRunIdAndTypeParameter(runId, engineType)
                .addValue(Parameters.LIMIT.name(), filter.getPageSize())
                .addValue(Parameters.OFFSET.name(), (filter.getPage() - 1) * filter.getPageSize());

        final String query = buildQuerySort(
                buildQueryFilter(findEngineRunTaskByRunIdAndTypeQuery, filter), filter.getSorts());
        return getNamedParameterJdbcTemplate().query(query, parameters, Parameters.getRowMapper());
    }

    public int countTasksByRunIdAndTypeAndFilter(final Long runId, final EngineType engineType,
                                                 final EngineRunTaskFilter filter) {
        final MapSqlParameterSource parameters = Parameters.buildRunIdAndTypeParameter(runId, engineType);
        final String query = buildQueryFilter(countEngineRunTaskByRunIdAndTypeQuery, filter);
        return getNamedParameterJdbcTemplate().queryForObject(query, parameters, Integer.class);
    }

    private String buildQueryFilter(final String query, final EngineRunTaskFilter filter) {
        final StringBuilder whereBuilder = new StringBuilder(CLAUSE_LENGTH);
        if (CollectionUtils.isNotEmpty(filter.getStatuses())) {
            whereBuilder
                    .append(AND)
                    .append("r.status IN (")
                    .append(filter.getStatuses().stream()
                            .map(EngineTaskStatus::name)
                            .map(status -> "'" + status + "'")
                            .collect(Collectors.joining(", ")))
                    .append(')');
        }
        if (StringUtils.isNotBlank(filter.getTaskId())) {
            ilike(whereBuilder.append(AND), "r.task_id", filter.getTaskId());
        }
        if (StringUtils.isNotBlank(filter.getTaskGroup())) {
            ilike(whereBuilder.append(AND), "r.task_group", filter.getTaskGroup());
        }
        if (StringUtils.isNotBlank(filter.getTaskTag())) {
            ilike(whereBuilder.append(AND), "r.task_tag", filter.getTaskTag());
        }
        if (StringUtils.isNotBlank(filter.getTaskKey())) {
            ilike(whereBuilder.append(AND), "r.task_key", filter.getTaskKey());
        }
        return WHERE_PATTERN.matcher(query).replaceFirst(whereBuilder.toString());
    }

    private void ilike(final StringBuilder queryBuilder, final String column, final String keyword) {
        queryBuilder
                .append(column)
                .append(" ILIKE '%")
                .append(keyword)
                .append("%'");
    }

    private String buildQuerySort(final String query, final List<EngineRunTaskSortVO> sorts) {
        final StringBuilder sortBuilder = new StringBuilder(CLAUSE_LENGTH);
        if (CollectionUtils.isNotEmpty(sorts)) {
            sortBuilder.append(" ORDER BY ")
                    .append(sorts.stream()
                            .map(this::buildDbSorting)
                            .collect(Collectors.joining(", ")))
                    .append(" NULLS LAST");
        }
        return SORT_PATTERN.matcher(query).replaceFirst(sortBuilder.toString());
    }

    private String buildDbSorting(final EngineRunTaskSortVO sorting) {
        return "r." + sorting.getColumn().getDbColumn() + " " + (sorting.isDescending() ? "DESC" : "ASC");
    }

    public List<EngineRunTask> loadEngineTasksByTaskKeys(final EngineType engineType, final List<String> taskKeys) {
        final MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue(Parameters.ENGINE_TYPE.name(), engineType)
                .addValue(Parameters.TASK_ID.name(), taskKeys);

        return getNamedParameterJdbcTemplate().query(loadEngineRunTasksByKeysQuery, parameters, Parameters.getRowMapper());

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
        TASKS_COUNT,
        LIMIT,
        OFFSET;

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
                    .duration(getLong(rs, DURATION.name()))
                    .startDateTime(getDataTime(rs, START_DATE.name()))
                    .endDateTime(getDataTime(rs, END_DATE.name()))
                    .attributes(rs.getString(DATA.name()))
                    .build();
        }

        private static Long getLong(final ResultSet rs, final String column) throws SQLException {
            final long result = rs.getLong(column);
            return rs.wasNull() ? null : result;
        }

        private static Date getDataTime(final ResultSet rs, final String column) throws SQLException {
            return Optional.ofNullable(rs.getTimestamp(column))
                    .map(Timestamp::getTime)
                    .map(Date::new)
                    .orElse(null);
        }

        private static RowMapper<EngineRunTaskStatsEntity> getStatsRowMapper() {
            return (rs, rowNum) -> EngineRunTaskStatsEntity.builder()
                    .engineType(EngineType.valueOf(rs.getString(ENGINE_TYPE.name())))
                    .taskGroup(rs.getString(TASK_GROUP.name()))
                    .status(EngineTaskStatus.valueOf(rs.getString(STATUS.name())))
                    .tasksCount(rs.getLong(TASKS_COUNT.name()))
                    .startDateTime(getDataTime(rs, START_DATE.name()))
                    .build();
        }

        private static MapSqlParameterSource buildRunIdAndTypeParameter(final Long runId, final EngineType type) {
            return new MapSqlParameterSource()
                    .addValue(RUN_ID.name(), runId)
                    .addValue(ENGINE_TYPE.name(), type.name());
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
    public void setLoadEngineRunTasksByKeysQuery(final String loadEngineRunTasksByKeysQuery) {
        this.loadEngineRunTasksByKeysQuery = loadEngineRunTasksByKeysQuery;
    }

    @Required
    public void setLoadEngineRunTasksStatsByRunIdAndTypeQuery(final String loadEngineRunTasksStatsByRunIdAndTypeQuery) {
        this.loadEngineRunTasksStatsByRunIdAndTypeQuery = loadEngineRunTasksStatsByRunIdAndTypeQuery;
    }

    @Required
    public void setFindEngineRunTaskByRunIdAndTypeQuery(final String findEngineRunTaskByRunIdAndTypeQuery) {
        this.findEngineRunTaskByRunIdAndTypeQuery = findEngineRunTaskByRunIdAndTypeQuery;
    }

    @Required
    public void setCountEngineRunTaskByRunIdAndTypeQuery(final String countEngineRunTaskByRunIdAndTypeQuery) {
        this.countEngineRunTaskByRunIdAndTypeQuery = countEngineRunTaskByRunIdAndTypeQuery;
    }
}
