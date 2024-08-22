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
import com.epam.pipeline.entity.pipeline.run.RunStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

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
                            final Integer runsChunkSize, final Integer ownersChunkSize,
                            final boolean dryRun) {
        final AtomicInteger counter = new AtomicInteger();

        ListUtils.partition(Arrays.asList(ownersAndDates.keySet().toArray()), ownersChunkSize).forEach(chunk -> {
            final Map<String, Date> ownersAndDatesChunk = ownersAndDates.entrySet().stream()
                    .filter(entry -> chunk.contains(entry.getKey()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            log.debug("Starting archive processing for '{}'/'{}' owners.",
                    ownersAndDatesChunk.size(), ownersAndDates.size());
            archiveRunsChunk(ownersAndDatesChunk, terminalStates, runsChunkSize, counter, dryRun);
        });

        log.debug("Transferring runs to archive completed. Total archived runs count: '{}'", counter.get());
    }

    private void archiveRunsChunk(final Map<String, Date> ownersAndDates, final List<Long> terminalStates,
                                  final Integer runsChunkSize, final AtomicInteger counter, final boolean dryRun) {
        int offset = 0;
        List<PipelineRun> runsToArchive = fetchRunsToArchive(ownersAndDates, terminalStates, runsChunkSize, dryRun,
                offset);

        if (CollectionUtils.isEmpty(runsToArchive)) {
            log.debug("No runs found to archive.");
            return;
        }

        while (!runsToArchive.isEmpty()) {
            final int masterRunsCount = runsToArchive.size();
            final List<Long> runIds = runsToArchive.stream().map(PipelineRun::getId).collect(Collectors.toList());
            if (dryRun) {
                log.debug("Loaded master runs ids: '{}'", runIds.stream()
                        .map(String::valueOf).collect(Collectors.joining(", ")));
            }
            log.debug("Loading child runs to archive...");
            final List<PipelineRun> children = ListUtils.emptyIfNull(
                    pipelineRunDao.loadRunsByParentRuns(runIds, dryRun));
            log.debug("Loaded '{}' child runs to archive.", children.size());
            if (dryRun && !children.isEmpty()) {
                log.debug("Loaded child runs ids: '{}'", runIds.stream()
                        .map(String::valueOf).collect(Collectors.joining(", ")));
            }
            runsToArchive.addAll(children);
            runIds.addAll(children.stream().map(PipelineRun::getId).collect(Collectors.toList()));
            log.debug("Loading runs statuses to archive...");
            final List<RunStatus> runStatuses = runStatusDao.loadRunStatus(runIds, false, dryRun);
            log.debug("Loaded '{}' run statuses to archive.", runStatuses.size());

            batchInsertToArchive(runsToArchive, runStatuses, dryRun);
            deleteRunsAndDependents(runIds, dryRun);
            counter.addAndGet(runIds.size());

            offset = dryRun ? offset + masterRunsCount : 0;
            runsToArchive = fetchRunsToArchive(ownersAndDates, terminalStates, runsChunkSize, dryRun, offset);
        }
    }

    private void deleteRunsAndDependents(final List<Long> runIds, final boolean dryRun) {
        log.debug("Deleting run sids...");
        pipelineRunDao.deleteRunSidsByRunIdIn(runIds, dryRun);
        log.debug("Run sids deleted. Deleting run logs...");
        runLogDao.deleteTaskByRunIdsIn(runIds, dryRun);
        log.debug("Run logs deleted. Deleting restart runs...");
        restartRunDao.deleteRestartRunByIdsIn(runIds, dryRun);
        log.debug("Restart runs deleted. Deleting run service urls...");
        runServiceUrlDao.deleteByRunIdsIn(runIds, dryRun);
        log.debug("Run service urls deleted. Deleting run statuses...");
        runStatusDao.deleteRunStatusByRunIdsIn(runIds, dryRun);
        log.debug("Run statuses deleted. Deleting stop serverless runs info...");
        stopServerlessRunDao.deleteByRunIdIn(runIds, dryRun);
        log.debug("Stop serverless runs info deleted. Deleting runs...");
        pipelineRunDao.deleteRunByIdIn(runIds, dryRun);
        log.debug("'{}' runs deleted.", runIds.size());
    }

    private List<PipelineRun> fetchRunsToArchive(final Map<String, Date> ownersAndDates,
                                                 final List<Long> terminalStates, final Integer chunkSize,
                                                 final boolean dryRun, final int offset) {
        log.debug("Loading master runs to archive...");
        final List<PipelineRun> runsToArchive = ListUtils.emptyIfNull(pipelineRunDao
                .loadRunsByOwnerAndEndDateBeforeAndStatusIn(ownersAndDates, terminalStates, chunkSize, dryRun, offset));
        log.debug("Loaded '{}' master runs to archive.", runsToArchive.size());
        return runsToArchive;
    }

    private void batchInsertToArchive(final List<PipelineRun> runsToArchive, final List<RunStatus> runStatuses,
                                      final boolean dryRun) {
        log.debug("Transferring '{}' runs to archive...", runsToArchive.size());
        if (dryRun) {
            log.debug("Skipping batch insert archived runs.");
        } else {
            archiveRunDao.batchInsertArchiveRuns(runsToArchive);
        }
        log.debug("'{}' runs inserted to archive. Transferring run statuses...", runsToArchive.size());
        if (dryRun) {
            log.debug("Skipping batch insert archived run statuses.");
        } else {
            archiveRunDao.batchInsertArchiveRunsStatusChange(runStatuses);
        }
        log.debug("'{}' run statuses inserted to archive", runStatuses.size());
    }
}
