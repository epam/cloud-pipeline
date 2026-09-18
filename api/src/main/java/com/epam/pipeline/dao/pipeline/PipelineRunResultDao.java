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

import com.epam.pipeline.dao.DryRunJdbcDaoSupport;
import com.epam.pipeline.entity.pipeline.run.PipelineRunResult;
import lombok.Setter;
import org.apache.commons.collections4.ListUtils;

import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PipelineRunResultDao extends DryRunJdbcDaoSupport {

    @Setter
    private String addPipelineRunResultQuery;
    @Setter
    private String loadPipelineRunResultQuery;
    @Setter
    private String deletePipelineRunResultsByRunIdQuery;

    @Transactional(propagation = Propagation.MANDATORY)
    public void addPipelineRunResults(final List<PipelineRunResult> results) {
        getNamedParameterJdbcTemplate().batchUpdate(
                addPipelineRunResultQuery,
                PipelineRunResultParameters.getParameters(results)
        );
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public List<PipelineRunResult> loadPipelineRunResultsForRun(final long runId) {
        final MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue(PipelineRunResultParameters.RUN_ID.name(), runId);

        return getNamedParameterJdbcTemplate().query(
                loadPipelineRunResultQuery,
                params,
                PipelineRunResultParameters.getExtractor()
        );
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deletePipelineRunResultsByRunIds(final List<Long> runIds, boolean dryRun) {
        final MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue(PipelineRunResultParameters.RUN_ID.name(), runIds);
        getNamedParameterJdbcTemplate(dryRun).update(deletePipelineRunResultsByRunIdQuery, params);
    }

    public enum PipelineRunResultParameters {
        RUN_ID,
        NAME,
        FILE_MASK,
        PATH;

        static MapSqlParameterSource[] getParameters(List<PipelineRunResult> results) {
            return ListUtils.emptyIfNull(results)
                    .stream()
                    .flatMap(result -> {
                        return ListUtils.emptyIfNull(result.getItems())
                                .stream()
                                .map(i -> {
                                    MapSqlParameterSource params = new MapSqlParameterSource();
                                    params.addValue(RUN_ID.name(), result.getRunId());
                                    params.addValue(NAME.name(), result.getName());
                                    params.addValue(FILE_MASK.name(), result.getFileMask().trim());
                                    params.addValue(PATH.name(), i);
                                    return params;
                                });
                    }
            ).toArray(MapSqlParameterSource[]::new);
        }

        static ResultSetExtractor<List<PipelineRunResult>> getExtractor() {
            return (rs) -> {
                final Map<String, PipelineRunResult> result = new HashMap<>();
                while (rs.next()) {
                    final String name = rs.getString(NAME.name());
                    final Long runId = rs.getLong(RUN_ID.name());
                    final String mask = rs.getString(FILE_MASK.name());
                    final PipelineRunResult runResult = result.computeIfAbsent(
                            name, k -> PipelineRunResult.builder()
                                    .runId(runId)
                                    .name(k)
                                    .fileMask(mask)
                                    .items(new ArrayList<>())
                                    .build()
                    );

                    runResult.addItem(rs.getString(PATH.name()));
                }
                return new ArrayList<>(result.values());
            };
        }
    }

}
