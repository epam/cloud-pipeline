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

package com.epam.pipeline.manager.pipeline;

import com.epam.pipeline.dao.pipeline.PipelineRunResultDao;
import com.epam.pipeline.entity.pipeline.run.PipelineRunResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
public class PipelineRunResultManager {

    @Autowired
    private PipelineRunResultDao pipelineRunResultDao;

    @Transactional(propagation = Propagation.REQUIRED)
    public void addPipelineRunResults(final List<PipelineRunResult> results) {
        results.forEach(PipelineRunResult::validate);
        pipelineRunResultDao.addPipelineRunResults(results);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public List<PipelineRunResult> loadPipelineRunResultsForRun(final long runId) {
        return pipelineRunResultDao.loadPipelineRunResultsForRun(runId);
    }

}
