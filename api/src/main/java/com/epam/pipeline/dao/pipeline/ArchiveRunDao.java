/*
 * Copyright 2024 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.entity.pipeline.PipelineRun;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcDaoSupport;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
public class ArchiveRunDao extends NamedParameterJdbcDaoSupport  {

    private final PipelineRunDao pipelineRunDao;

    private String createArchiveRunsQuery;

    @Transactional(propagation = Propagation.MANDATORY)
    public void batchInsertArchiveRuns(final List<PipelineRun> runs) {
        if (CollectionUtils.isEmpty(runs)) {
            return;
        }
        final MapSqlParameterSource[] params = pipelineRunDao.getParamsForBatchUpdate(runs);
        getNamedParameterJdbcTemplate().batchUpdate(createArchiveRunsQuery, params);
    }

    @Required
    public void setCreateArchiveRunsQuery(final String createArchiveRunsQuery) {
        this.createArchiveRunsQuery = createArchiveRunsQuery;
    }
}
