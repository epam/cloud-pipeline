/*
 * Copyright 2023 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.dao.run;

import com.epam.pipeline.dao.DaoUtils;
import com.epam.pipeline.entity.pipeline.run.PipelineRunServiceUrl;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.ListUtils;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcDaoSupport;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@RequiredArgsConstructor
public class RunServiceUrlDao extends NamedParameterJdbcDaoSupport {

    private final String loadByRunIdsQuery;
    private final String loadByRunIdQuery;
    private final String loadByRunIdAndRegionQuery;
    private final String createServiceUrlQuery;
    private final String updateServiceUrlQuery;
    private final String deleteServiceUrlByRunIdQuery;
    private final String deleteServiceUrlByIdQuery;
    private final String deleteServiceUrlByRunIdsQuery;

    public List<PipelineRunServiceUrl> findByRunIds(final List<Long> runIds) {
        final MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("IDS", runIds);
        return getNamedParameterJdbcTemplate().query(loadByRunIdsQuery, params,
                Parameters.getRowMapper());
    }

    public List<PipelineRunServiceUrl> findByRunId(final Long runId) {
        return getJdbcTemplate()
                .query(loadByRunIdQuery, Parameters.getRowMapper(), runId);
    }

    public Optional<PipelineRunServiceUrl> findByPipelineRunIdAndRegion(final Long runId,
                                                                        final String region) {
        final List<PipelineRunServiceUrl> results = getJdbcTemplate()
                .query(loadByRunIdAndRegionQuery, Parameters.getRowMapper(), runId, region);
        return ListUtils.emptyIfNull(results).stream().findFirst();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void save(final PipelineRunServiceUrl serviceUrl) {
        if (Objects.isNull(serviceUrl.getId())) {
            create(serviceUrl);
        } else {
            update(serviceUrl);
        }
    }

    private void update(final PipelineRunServiceUrl serviceUrl) {
        getNamedParameterJdbcTemplate()
                .update(updateServiceUrlQuery, Parameters.getParameters(serviceUrl));

    }

    private void create(final PipelineRunServiceUrl serviceUrl) {
        final KeyHolder keyHolder = new GeneratedKeyHolder();
        getNamedParameterJdbcTemplate()
                .update(createServiceUrlQuery, Parameters.getParameters(serviceUrl), keyHolder,
                        new String[] { Parameters.ID.name().toLowerCase() });
        serviceUrl.setId(keyHolder.getKey().longValue());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteByPipelineRunId(final Long runId) {
        getJdbcTemplate().update(deleteServiceUrlByRunIdQuery, runId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteById(final Long id) {
        getJdbcTemplate().update(deleteServiceUrlByIdQuery, id);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteByRunIdsIn(final List<Long> runIds) {
        final MapSqlParameterSource params = DaoUtils.longListParams(runIds);
        getNamedParameterJdbcTemplate().update(deleteServiceUrlByRunIdsQuery, params);
    }

    enum Parameters {
        ID,
        PIPELINE_RUN_ID,
        REGION,
        SERVICE_URL;

        static MapSqlParameterSource getParameters(final PipelineRunServiceUrl serviceUrl) {
            final MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue(ID.name(), serviceUrl.getId());
            params.addValue(PIPELINE_RUN_ID.name(), serviceUrl.getPipelineRunId());
            params.addValue(REGION.name(), serviceUrl.getRegion());
            params.addValue(SERVICE_URL.name(), serviceUrl.getServiceUrl());
            return params;
        }

        static RowMapper<PipelineRunServiceUrl> getRowMapper() {
            return (rs, rowNum) -> {
                final PipelineRunServiceUrl pipelineRunServiceUrl = new PipelineRunServiceUrl();
                pipelineRunServiceUrl.setId(rs.getLong(ID.name()));
                pipelineRunServiceUrl.setPipelineRunId(rs.getLong(PIPELINE_RUN_ID.name()));
                pipelineRunServiceUrl.setRegion(rs.getString(REGION.name()));
                pipelineRunServiceUrl.setServiceUrl(rs.getString(SERVICE_URL.name()));
                return pipelineRunServiceUrl;
            };
        }
    }
}
