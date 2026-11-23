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

import com.epam.pipeline.config.JsonMapper;
import com.epam.pipeline.dao.DaoUtils;
import com.epam.pipeline.dao.DryRunJdbcDaoSupport;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.pipeline.run.RunStatus;
import com.epam.pipeline.entity.pipeline.run.RunStatusInfo;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
public class RunStatusDao extends DryRunJdbcDaoSupport {

    private String createRunStatusQuery;
    private String loadRunStatusQuery;
    private String loadRunStatusByListQuery;
    private String loadRunStatusByListWithArchivedQuery;
    private String deleteRunStatusQuery;
    private String deleteRunStatusByIdsQuery;
    private String updatePriceInLastRunStatusChangeQuery;

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

    @Transactional(propagation = Propagation.MANDATORY)
    public void updatePriceForCurrentActiveRunStatus(PipelineRun pipelineRun) {
        final RunStatusInfo runStatusInfo = RunStatusInfo.of(pipelineRun.getPricePerHour(),
                pipelineRun.getComputePricePerHour(), pipelineRun.getDiskPricePerHour());
        if (runStatusInfo == null) {
            log.debug("Skipping run status price update for run {} as its compute price is not resolved yet.",
                    pipelineRun.getId());
            return;
        }
        final MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue(RunStatusParameters.RUN_ID.name(), pipelineRun.getId());
        params.addValue(RunStatusParameters.RUN_STATUS_INFO.name(),
                JsonMapper.convertDataToJsonStringForQuery(runStatusInfo));
        getNamedParameterJdbcTemplate().update(updatePriceInLastRunStatusChangeQuery, params);
    }

    enum RunStatusParameters {
        RUN_ID,
        STATUS,
        REASON,
        DATE,
        RUN_STATUS_INFO;

        static MapSqlParameterSource getParameters(RunStatus runStatus) {
            MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue(RUN_ID.name(), runStatus.getRunId());
            params.addValue(STATUS.name(), runStatus.getStatus().getId());
            params.addValue(DATE.name(), runStatus.getTimestamp());
            params.addValue(REASON.name(), runStatus.getReason());
            params.addValue(RUN_STATUS_INFO.name(),
                    Optional.ofNullable(runStatus.getRunStatusInfo())
                            .map(JsonMapper::convertDataToJsonStringForQuery)
                            .orElse(null));
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

                final String runStatusInfoJson = rs.getString(RUN_STATUS_INFO.name());
                if (!rs.wasNull()) {
                    restartRun.setRunStatusInfo(JsonMapper.parseData(runStatusInfoJson,
                            new TypeReference<RunStatusInfo>() {}));
                }

                return restartRun;
            };
        }

    }

    public void setCreateRunStatusQuery(final String createRunStatusQuery) {
        this.createRunStatusQuery = createRunStatusQuery;
    }

    public void setLoadRunStatusQuery(final String loadRunStatusQuery) {
        this.loadRunStatusQuery = loadRunStatusQuery;
    }

    public void setLoadRunStatusByListQuery(final String loadRunStatusByListQuery) {
        this.loadRunStatusByListQuery = loadRunStatusByListQuery;
    }

    public void setDeleteRunStatusQuery(final String deleteRunStatusQuery) {
        this.deleteRunStatusQuery = deleteRunStatusQuery;
    }

    public void setDeleteRunStatusByIdsQuery(final String deleteRunStatusByIdsQuery) {
        this.deleteRunStatusByIdsQuery = deleteRunStatusByIdsQuery;
    }

    public void setLoadRunStatusByListWithArchivedQuery(final String loadRunStatusByListWithArchivedQuery) {
        this.loadRunStatusByListWithArchivedQuery = loadRunStatusByListWithArchivedQuery;
    }

    public void setUpdatePriceInLastRunStatusChangeQuery(final String updatePriceInLastRunStatusChangeQuery) {
        this.updatePriceInLastRunStatusChangeQuery = updatePriceInLastRunStatusChangeQuery;
    }
}
