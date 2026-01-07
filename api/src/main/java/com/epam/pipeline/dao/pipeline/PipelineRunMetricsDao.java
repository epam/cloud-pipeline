/*
 * Copyright 2026 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.entity.run.PipelineRunPerformanceMetric;
import com.epam.pipeline.entity.run.PipelineRunPerformanceMetrics;
import com.epam.pipeline.entity.run.PipelineRunPerformanceMetricsType;
import org.apache.commons.collections4.ListUtils;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcDaoSupport;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

public class PipelineRunMetricsDao extends NamedParameterJdbcDaoSupport {

    private String loadRunMetricsByIdQuery;
    private String putRunMetricsByIdQuery;

    @Transactional(propagation = Propagation.MANDATORY)
    public void createRunMetrics(final PipelineRunPerformanceMetrics metrics) {
        final MapSqlParameterSource[] parameters = PipelineRunPerformanceMetricsParameters.getParameters(metrics);
        getNamedParameterJdbcTemplate().batchUpdate(putRunMetricsByIdQuery, parameters);
    }

    @Transactional(propagation = Propagation.SUPPORTS)
    public PipelineRunPerformanceMetrics loadRunMetrics(final Long runId) {
        return getJdbcTemplate().query(loadRunMetricsByIdQuery,
                PipelineRunPerformanceMetricsParameters.getExtractor(), runId);
    }

    public enum PipelineRunPerformanceMetricsParameters {
        RUN_ID,
        METRIC_TYPE,
        MAX_VALUE,
        AVG_VALUE,
        CAPACITY;

        static MapSqlParameterSource[] getParameters(final PipelineRunPerformanceMetrics metrics) {
            return ListUtils.emptyIfNull(metrics.getMetrics())
                    .stream()
                    .map(metric -> {
                        MapSqlParameterSource params = new MapSqlParameterSource();
                        params.addValue(RUN_ID.name(), metrics.getRunId());
                        params.addValue(METRIC_TYPE.name(), metric.getType().name());
                        params.addValue(MAX_VALUE.name(), metric.getMax());
                        params.addValue(AVG_VALUE.name(), metric.getAvg());
                        params.addValue(CAPACITY.name(), metric.getCapacity());
                        return params;
                    }).toArray(MapSqlParameterSource[]::new);
        }

        static ResultSetExtractor<PipelineRunPerformanceMetrics> getExtractor() {
            return (rs) -> {
                long runId = -1;
                List<PipelineRunPerformanceMetric> metrics = new ArrayList<>();
                while (rs.next()) {
                    if (runId == -1) {
                        runId = rs.getLong(RUN_ID.name());
                    }
                    metrics.add(
                        PipelineRunPerformanceMetric.builder().type(
                                PipelineRunPerformanceMetricsType.valueOf(rs.getString(METRIC_TYPE.name()))
                        ).max(rs.getInt(MAX_VALUE.name()))
                        .avg(rs.getInt(AVG_VALUE.name()))
                        .capacity(rs.getLong(CAPACITY.name())).build()
                    );
                }
                return new PipelineRunPerformanceMetrics(runId, metrics);
            };
        }
    }

    @Required
    public void setLoadRunMetricsByIdQuery(final String loadRunMetricsByIdQuery) {
        this.loadRunMetricsByIdQuery = loadRunMetricsByIdQuery;
    }

    @Required
    public void setPutRunMetricsByIdQuery(final String putRunMetricsByIdQuery) {
        this.putRunMetricsByIdQuery = putRunMetricsByIdQuery;
    }
}
