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

package com.epam.pipeline.manager.pipeline;

import com.epam.pipeline.dao.pipeline.StopServerlessRunDao;
import com.epam.pipeline.entity.pipeline.StopServerlessRun;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class StopServerlessRunManager {

    private final StopServerlessRunDao stopServerlessRunDao;

    public List<StopServerlessRun> loadActiveServerlessRuns() {
        return stopServerlessRunDao.loadByStatusRunning();
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void createServerlessRun(final StopServerlessRun run) {
        stopServerlessRunDao.createServerlessRun(run);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void updateServerlessRun(final StopServerlessRun run) {
        stopServerlessRunDao.updateServerlessRun(run);
    }

    public List<StopServerlessRun> loadAll() {
        return stopServerlessRunDao.loadAll();
    }

    public Optional<StopServerlessRun> loadByRunId(final Long runId) {
        return stopServerlessRunDao.loadByRunId(runId);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void deleteByRunId(final Long runId) {
        stopServerlessRunDao.deleteByRunId(runId);
    }
}
