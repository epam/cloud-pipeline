/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.dao.pipeline.RestartRunDao;
import com.epam.pipeline.entity.pipeline.run.RestartRun;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RestartRunManager {

    @Autowired
    private RestartRunDao restartRunDao;

    /**
     * Creates restart run for failure spot run
     * @param restartRun
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void createRestartRun(RestartRun restartRun) {
        restartRunDao.createPipelineRestartRun(restartRun);
    }

    /**
     * Counts the number of spot runs which was restarted from failure spot run
     * @param restartingRunId id of run which will be restarted
     * @return the number of {@link RestartRun} which was restarted
     */
    public Integer countRestartRuns(Long restartingRunId) {
        return restartRunDao.countPipelineRestartRuns(restartingRunId);
    }

    /**
     * Loads full list of restarted runs for initial run
     * @param initialRunId
     * @return
     */
    public List<RestartRun> loadRestartedRunsForInitialRun(Long initialRunId) {
        return restartRunDao.loadAllRestartedRunsForInitialRun(initialRunId);
    }
}
