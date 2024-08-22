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
import com.epam.pipeline.dao.DryRunJdbcDaoSupport;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.pipeline.run.RunStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
public class RunStatusDao extends DryRunJdbcDaoSupport {

    private String createRunStatusQuery;
    private String loadRunStatusQuery;
    private String loadRunStatusByListQuery;
    private String loadRunStatusByListWithArchivedQuery;
    private String deleteRunStatusQuery;
    private String deleteRunStatusByIdsQuery;

    @Transactional(propagation = Propagation.MANDATORY)
    public void saveStatus(RunStatus runStatus) {
        getNamedParameterJdbcTemplate().update(createRunStatusQuery,
                RunStatusParameters.getParameters(runStatus));
    }

    public List<RunStatus> loadRunStatus(Long runId) {
        return getJdbcTemplate().query(loadRunStatusQuery, RunStatusParameters.getRowMapper(), runId);
    }

    public List<RunStatus> loadRunStatus(final List<Long> runIds, final boolean archive) {
        return loadRunStatus(runIds, archive, false);
    }

    public List<RunStatus> loadRunStatus(final List<Long> runIds, final boolean archive, final boolean dryRun) {
        final MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("IDS", runIds);

        final String query = archive
                ? loadRunStatusByListWithArchivedQuery
                : loadRunStatusByListQuery;

        return getNamedParameterJdbcTemplate(dryRun).query(query, params, RunStatusParameters.getRowMapper());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteRunStatus(Long runId) {
        getJdbcTemplate().update(deleteRunStatusQuery, runId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteRunStatusByRunIdsIn(final List<Long> runIds, final boolean dryRun) {
        final MapSqlParameterSource params = DaoUtils.longListParams(runIds);
        getNamedParameterJdbcTemplate(dryRun).update(deleteRunStatusByIdsQuery, params);
    }

    enum RunStatusParameters {
        RUN_ID,
        STATUS,
        REASON,
        DATE;

        static MapSqlParameterSource getParameters(RunStatus runStatus) {
            MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue(RUN_ID.name(), runStatus.getRunId());
            params.addValue(STATUS.name(), runStatus.getStatus().getId());
            params.addValue(DATE.name(), runStatus.getTimestamp());
            params.addValue(REASON.name(), runStatus.getReason());
            return params;
        }

        static RowMapper<RunStatus> getRowMapper() {
            return (rs, rowNum) -> {
                RunStatus restartRun = new RunStatus();
                restartRun.setRunId(rs.getLong(RUN_ID.name()));
                restartRun.setStatus(TaskStatus.getById(rs.getLong(STATUS.name())));
                restartRun.setTimestamp(rs.getTimestamp(DATE.name()).toLocalDateTime());

                final String reason = rs.getString(REASON.name());
                if (!rs.wasNull()) {
                    restartRun.setReason(reason);
                }

                return restartRun;
            };
        }

    }

    @Required
    public void setCreateRunStatusQuery(final String createRunStatusQuery) {
        this.createRunStatusQuery = createRunStatusQuery;
    }

    @Required
    public void setLoadRunStatusQuery(final String loadRunStatusQuery) {
        this.loadRunStatusQuery = loadRunStatusQuery;
    }

    @Required
    public void setLoadRunStatusByListQuery(final String loadRunStatusByListQuery) {
        this.loadRunStatusByListQuery = loadRunStatusByListQuery;
    }

    @Required
    public void setDeleteRunStatusQuery(final String deleteRunStatusQuery) {
        this.deleteRunStatusQuery = deleteRunStatusQuery;
    }

    @Required
    public void setDeleteRunStatusByIdsQuery(final String deleteRunStatusByIdsQuery) {
        this.deleteRunStatusByIdsQuery = deleteRunStatusByIdsQuery;
    }

    @Required
    public void setLoadRunStatusByListWithArchivedQuery(final String loadRunStatusByListWithArchivedQuery) {
        this.loadRunStatusByListWithArchivedQuery = loadRunStatusByListWithArchivedQuery;
    }
}
