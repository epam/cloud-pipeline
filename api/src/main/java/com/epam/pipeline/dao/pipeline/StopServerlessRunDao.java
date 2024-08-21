/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
import com.epam.pipeline.dao.DaoUtils;
import com.epam.pipeline.dao.JdbcTemplateReadOnlyWrapper;
import com.epam.pipeline.entity.pipeline.StopServerlessRun;
import org.apache.commons.collections4.ListUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcDaoSupport;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public class StopServerlessRunDao extends NamedParameterJdbcDaoSupport {

    @Autowired
    private DaoHelper daoHelper;

    @Autowired
    private JdbcTemplateReadOnlyWrapper jdbcTemplateReadOnlyWrapper;

    private String serverlessRunSequenceQuery;
    private String saveServerlessRunQuery;
    private String updateServerlessRunQuery;
    private String loadAllServerlessRunsQuery;
    private String deleteByRunIdServerlessRunQuery;
    private String loadServerlessRunByRunIdQuery;
    private String loadServerlessByStatusRunningQuery;
    private String deleteByRunIdsServerlessRunQuery;

    @Transactional(propagation = Propagation.MANDATORY)
    public Long createServerlessRunId() {
        return daoHelper.createId(serverlessRunSequenceQuery);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void createServerlessRun(final StopServerlessRun run) {
        final Long id = createServerlessRunId();
        run.setId(id);
        getNamedParameterJdbcTemplate().update(saveServerlessRunQuery,
                StopServerlessRunParameters.getParameters(run));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void updateServerlessRun(final StopServerlessRun run) {
        getNamedParameterJdbcTemplate().update(updateServerlessRunQuery,
                StopServerlessRunParameters.getParameters(run));
    }

    public List<StopServerlessRun> loadAll() {
        return getJdbcTemplate().query(loadAllServerlessRunsQuery, StopServerlessRunParameters.getRowMapper());
    }

    public Optional<StopServerlessRun> loadByRunId(final Long runId) {
        return getJdbcTemplate()
                .query(loadServerlessRunByRunIdQuery, StopServerlessRunParameters.getRowMapper(), runId).stream()
                .findFirst();
    }

    public List<StopServerlessRun> loadByStatusRunning() {
        return ListUtils.emptyIfNull(getNamedParameterJdbcTemplate()
                .query(loadServerlessByStatusRunningQuery, StopServerlessRunParameters.getRowMapper()));
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void deleteByRunId(final Long runId) {
        getJdbcTemplate().update(deleteByRunIdServerlessRunQuery, runId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteByRunIdIn(final List<Long> runIds, final boolean dryRun) {
        final MapSqlParameterSource params = DaoUtils.longListParams(runIds);
        getNamedParameterJdbcTemplate(dryRun).update(deleteByRunIdsServerlessRunQuery, params);
    }

    public enum StopServerlessRunParameters {
        ID,
        RUN_ID,
        LAST_UPDATE,
        STOP_AFTER;

        static MapSqlParameterSource getParameters(final StopServerlessRun run) {
            final MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue(ID.name(), run.getId());
            params.addValue(RUN_ID.name(), run.getRunId());
            params.addValue(LAST_UPDATE.name(), run.getLastUpdate());
            params.addValue(STOP_AFTER.name(), run.getStopAfter());
            return params;
        }

        static RowMapper<StopServerlessRun> getRowMapper() {
            return (rs, rowNum) -> {
                final StopServerlessRun run = new StopServerlessRun();
                run.setId(rs.getLong(ID.name()));
                run.setRunId(rs.getLong(RUN_ID.name()));
                run.setLastUpdate(rs.getTimestamp(LAST_UPDATE.name()).toLocalDateTime());
                run.setStopAfter(rs.getLong(STOP_AFTER.name()));
                return run;
            };
        }
    }

    @Required
    public void setServerlessRunSequenceQuery(final String serverlessRunSequenceQuery) {
        this.serverlessRunSequenceQuery = serverlessRunSequenceQuery;
    }

    @Required
    public void setLoadAllServerlessRunsQuery(final String loadAllServerlessRunsQuery) {
        this.loadAllServerlessRunsQuery = loadAllServerlessRunsQuery;
    }

    @Required
    public void setSaveServerlessRunQuery(final String saveServerlessRunQuery) {
        this.saveServerlessRunQuery = saveServerlessRunQuery;
    }

    @Required
    public void setUpdateServerlessRunQuery(final String updateServerlessRunQuery) {
        this.updateServerlessRunQuery = updateServerlessRunQuery;
    }

    @Required
    public void setDeleteByRunIdServerlessRunQuery(final String deleteByRunIdServerlessRunQuery) {
        this.deleteByRunIdServerlessRunQuery = deleteByRunIdServerlessRunQuery;
    }

    @Required
    public void setLoadServerlessRunByRunIdQuery(final String loadServerlessRunByRunIdQuery) {
        this.loadServerlessRunByRunIdQuery = loadServerlessRunByRunIdQuery;
    }

    @Required
    public void setLoadServerlessByStatusRunningQuery(final String loadServerlessByStatusRunningQuery) {
        this.loadServerlessByStatusRunningQuery = loadServerlessByStatusRunningQuery;
    }

    @Required
    public void setDeleteByRunIdsServerlessRunQuery(final String deleteByRunIdsServerlessRunQuery) {
        this.deleteByRunIdsServerlessRunQuery = deleteByRunIdsServerlessRunQuery;
    }

    private NamedParameterJdbcTemplate getNamedParameterJdbcTemplate(final boolean dryRun) {
        return dryRun ? jdbcTemplateReadOnlyWrapper : getNamedParameterJdbcTemplate();
    }
}
