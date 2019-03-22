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

import com.epam.pipeline.dao.pipeline.RunStatusDao;
import com.epam.pipeline.entity.pipeline.run.RunStatus;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.ListUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RunStatusManager {

    private final RunStatusDao runStatusDao;

    @Transactional(propagation = Propagation.REQUIRED)
    public void saveStatus(final RunStatus runStatus) {
        List<RunStatus> runStatuses = loadRunStatus(runStatus.getRunId());
        Optional<RunStatus> lastStatus = ListUtils.emptyIfNull(runStatuses).stream()
                .max(Comparator.comparing(RunStatus::getTimestamp));
        //check that status really changed
        if (lastStatus.isPresent() && lastStatus.get().getStatus() == runStatus.getStatus()) {
            return;
        }
        runStatusDao.saveStatus(runStatus);
    }

    public List<RunStatus> loadRunStatus(final Long runId) {
        return runStatusDao.loadRunStatus(runId);
    }

    public Map<Long, List<RunStatus>> loadRunStatus(final List<Long> runId) {
        return runStatusDao.loadRunStatus(runId).stream().collect(Collectors.groupingBy(RunStatus::getRunId));
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void deleteRunStatus(final Long runId) {
        runStatusDao.deleteRunStatus(runId);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void deleteRunStatusForPipeline(final Long pipelineId) {
        runStatusDao.deleteRunStatusForPipeline(pipelineId);
    }
}
