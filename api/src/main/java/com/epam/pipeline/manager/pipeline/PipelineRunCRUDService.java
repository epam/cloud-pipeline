/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.manager.pipeline;

import com.epam.pipeline.dao.pipeline.PipelineRunDao;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

//TODO: Move all CRUD and DB persistence methods from PipelineRunManager to this class
@Service
@RequiredArgsConstructor
public class PipelineRunCRUDService {

    private final PipelineRunDao pipelineRunDao;

    @Transactional(propagation = Propagation.REQUIRED)
    public PipelineRun updateRunStatus(PipelineRun run) {
        updatePrettyUrlForFinishedRun(run);
        pipelineRunDao.updateRunStatus(run);
        return run;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void updatePrettyUrlForFinishedRun(PipelineRun run) {
        if (run.getStatus().isFinal() && StringUtils.hasText(run.getPrettyUrl())) {
            run.setPrettyUrl(null);
            pipelineRunDao.updateRun(run);
        }
    }
}
