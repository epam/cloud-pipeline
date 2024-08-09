/*
 * Copyright 2024 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.dao.pipeline.ArchiveRunDao;
import com.epam.pipeline.dao.pipeline.PipelineRunDao;
import com.epam.pipeline.dao.pipeline.RestartRunDao;
import com.epam.pipeline.dao.pipeline.RunLogDao;
import com.epam.pipeline.dao.pipeline.RunStatusDao;
import com.epam.pipeline.dao.pipeline.StopServerlessRunDao;
import com.epam.pipeline.dao.run.RunServiceUrlDao;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;

@Service
@Slf4j
@RequiredArgsConstructor
public class ArchiveRunCoreService {
    private final ArchiveRunDao archiveRunDao;
    private final PipelineRunDao pipelineRunDao;
    private final RunLogDao runLogDao;
    private final RestartRunDao restartRunDao;
    private final RunServiceUrlDao runServiceUrlDao;
    private final RunStatusDao runStatusDao;
    private final StopServerlessRunDao stopServerlessRunDao;

    @Transactional(propagation = Propagation.REQUIRED)
    public void archiveRuns(final Map<String, Date> ownersAndDates, final List<Long> terminalStates,
                            final Integer chunkSize) {
        List<PipelineRun> runsToArchive = ListUtils.emptyIfNull(pipelineRunDao
                .loadRunsByOwnerAndEndDateBeforeAndStatusIn(ownersAndDates, terminalStates, chunkSize));

        if (CollectionUtils.isEmpty(runsToArchive)) {
            log.debug("No runs found to archive.");
            return;
        }

        int totalArchivedRuns = 0;
        while (!runsToArchive.isEmpty()) {
            final List<Long> runIds = runsToArchive.stream().map(PipelineRun::getId).collect(toList());
            final List<PipelineRun> children = ListUtils.emptyIfNull(pipelineRunDao.loadRunsByParentRuns(runIds));
            runsToArchive.addAll(children);
            runIds.addAll(children.stream().map(PipelineRun::getId).collect(toList()));

            log.debug("Transferring '{}' runs to archive.", runsToArchive.size());
            archiveRunDao.batchInsertArchiveRuns(runsToArchive);
            archiveRunDao.batchInsertArchiveRunsStatusChange(runStatusDao.loadRunStatus(runIds, false));
            deleteRunsAndDependents(runIds);
            totalArchivedRuns += runIds.size();

            runsToArchive = ListUtils.emptyIfNull(pipelineRunDao
                    .loadRunsByOwnerAndEndDateBeforeAndStatusIn(ownersAndDates, terminalStates, chunkSize));
        }
        log.debug("Transferring runs to archive completed. Total archived runs count: '{}'", totalArchivedRuns);
    }

    private void deleteRunsAndDependents(final List<Long> runIds) {
        pipelineRunDao.deleteRunSidsByRunIdIn(runIds);
        runLogDao.deleteTaskByRunIdsIn(runIds);
        restartRunDao.deleteRestartRunByIdsIn(runIds);
        runServiceUrlDao.deleteByRunIdsIn(runIds);
        runStatusDao.deleteRunStatusByRunIdsIn(runIds);
        stopServerlessRunDao.deleteByRunIdIn(runIds);
        pipelineRunDao.deleteRunByIdIn(runIds);
    }
}
