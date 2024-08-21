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

import com.epam.pipeline.dao.DaoUtils;
import com.epam.pipeline.dao.JdbcTemplateReadOnlyWrapper;
import com.epam.pipeline.entity.pipeline.run.RestartRun;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.ListUtils;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcDaoSupport;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.sql.Timestamp;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@RequiredArgsConstructor
public class RestartRunDao extends NamedParameterJdbcDaoSupport {

    private final JdbcTemplateReadOnlyWrapper jdbcTemplateReadOnlyWrapper;

    private String createPipelineRestartRunQuery;
    private String countPipelineRestartRunQuery;
    private String loadPipelineRestartedRunForParentRunQuery;
    private String loadAllRestartedRunsQuery;
    private String loadAllRestartedRunsForInitialRunQuery;
    private String loadRestartRunByIdQuery;
    private String deleteRestartRunByIdsQuery;

    @Transactional(propagation = Propagation.MANDATORY)
    public void createPipelineRestartRun(RestartRun restartRun) {
        getNamedParameterJdbcTemplate().update(createPipelineRestartRunQuery,
                RestartRunDao.PipelineRestartRunParameters.getParameters(restartRun));
    }

    public Integer countPipelineRestartRuns(Long restartingRunId) {
        Assert.isTrue(!Objects.isNull(restartingRunId), "Restarting Run ID is required");
        return getJdbcTemplate().queryForObject(countPipelineRestartRunQuery, Integer.class, restartingRunId);
    }

    public RestartRun loadPipelineRestartedRunForParentRun(Long parentRunId) {
        Assert.isTrue(!Objects.isNull(parentRunId), "Parent Run ID is required");
        return getJdbcTemplate().queryForObject(loadPipelineRestartedRunForParentRunQuery,
                PipelineRestartRunParameters.getRowMapper(), parentRunId);
    }

    public List<RestartRun> loadAllRestartedRuns() {
        return getJdbcTemplate().query(loadAllRestartedRunsQuery, PipelineRestartRunParameters.getRowMapper());
    }

    public List<RestartRun> loadAllRestartedRunsForInitialRun(Long runId) {
        return getJdbcTemplate().query(loadAllRestartedRunsForInitialRunQuery,
                PipelineRestartRunParameters.getRowMapper(), runId, runId);
    }

    public Optional<RestartRun> loadRestartedRunById(final Long id) {
        return ListUtils.emptyIfNull(getJdbcTemplate()
                .query(loadRestartRunByIdQuery, PipelineRestartRunParameters.getRowMapper(), id)).stream()
                .findFirst();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteRestartRunByIdsIn(final List<Long> runIds, final boolean dryRun) {
        final MapSqlParameterSource params = DaoUtils.longListParams(runIds);
        getNamedParameterJdbcTemplate(dryRun).update(deleteRestartRunByIdsQuery, params);
    }

    enum PipelineRestartRunParameters {
        PARENT_RUN_ID,
        RESTARTED_RUN_ID,
        DATE;

        static MapSqlParameterSource getParameters(RestartRun run) {
            MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue(PARENT_RUN_ID.name(), run.getParentRunId());
            params.addValue(RESTARTED_RUN_ID.name(), run.getRestartedRunId());
            params.addValue(DATE.name(), run.getDate());
            return params;
        }

        static RowMapper<RestartRun> getRowMapper() {
            return (rs, rowNum) -> {
                RestartRun restartRun = new RestartRun();
                restartRun.setParentRunId(rs.getLong(PARENT_RUN_ID.name()));
                restartRun.setRestartedRunId(rs.getLong(RESTARTED_RUN_ID.name()));
                Timestamp timestamp = rs.getTimestamp(DATE.name());
                if (!rs.wasNull()) {
                    restartRun.setDate(new Date(timestamp.getTime())); // convert to UTC
                }
                return restartRun;
            };
        }
    }

    @Required
    public void setCreatePipelineRestartRunQuery(String createPipelineRestartRunQuery) {
        this.createPipelineRestartRunQuery = createPipelineRestartRunQuery;
    }

    @Required
    public void setCountPipelineRestartRunQuery(String countPipelineRestartRunQuery) {
        this.countPipelineRestartRunQuery = countPipelineRestartRunQuery;
    }

    @Required
    public void setLoadPipelineRestartedRunForParentRunQuery(String loadPipelineRestartedRunForParentRunQuery) {
        this.loadPipelineRestartedRunForParentRunQuery = loadPipelineRestartedRunForParentRunQuery;
    }

    @Required
    public void setLoadAllRestartedRunsQuery(String loadAllRestartedRunsQuery) {
        this.loadAllRestartedRunsQuery = loadAllRestartedRunsQuery;
    }

    @Required
    public void setLoadAllRestartedRunsForInitialRunQuery(String loadAllRestartedRunsForInitialRunQuery) {
        this.loadAllRestartedRunsForInitialRunQuery = loadAllRestartedRunsForInitialRunQuery;
    }

    @Required
    public void setLoadRestartRunByIdQuery(final String loadRestartRunByIdQuery) {
        this.loadRestartRunByIdQuery = loadRestartRunByIdQuery;
    }

    @Required
    public void setDeleteRestartRunByIdsQuery(final String deleteRestartRunByIdsQuery) {
        this.deleteRestartRunByIdsQuery = deleteRestartRunByIdsQuery;
    }

    private NamedParameterJdbcTemplate getNamedParameterJdbcTemplate(final boolean dryRun) {
        return dryRun ? jdbcTemplateReadOnlyWrapper : getNamedParameterJdbcTemplate();
    }
}
