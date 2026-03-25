/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.cluster.cleaner;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dao.pipeline.RunLogDao;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.PipelineTask;
import com.epam.pipeline.entity.pipeline.RunLog;
import com.epam.pipeline.manager.pipeline.PipelineRunCRUDService;
import com.epam.pipeline.manager.pipeline.RunLogManager;
import com.epam.pipeline.manager.pipeline.RunLogStorageManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
@SuppressWarnings("PMD.AvoidCatchingGenericException")
public class RunLogMigrationCleaner implements RunCleaner {

    private final RunLogManager runLogManager;
    private final RunLogDao runLogDao;
    private final RunLogStorageManager runLogStorageManager;
    private final PipelineRunCRUDService runCRUDService;
    private final MessageHelper messageHelper;

    @Value("${runs.console.log.task:Console}")
    private String consoleLogTask;

    public RunLogMigrationCleaner(final RunLogManager runLogManager,
                                  final RunLogDao runLogDao,
                                  final RunLogStorageManager runLogStorageManager,
                                  final PipelineRunCRUDService runCRUDService,
                                  final MessageHelper messageHelper) {
        this.runLogManager = runLogManager;
        this.runLogDao = runLogDao;
        this.runLogStorageManager = runLogStorageManager;
        this.runCRUDService = runCRUDService;
        this.messageHelper = messageHelper;
    }

    @Override
    public void cleanResources(final PipelineRun run) {
        if (Objects.isNull(run)) {
            return;
        }
        cleanResources(run.getId());
    }

    @Override
    public void cleanResources(final Long runId) {
        try {
            migrateRunLogsToStorage(runId);
        } catch (Exception e) {
            log.error("Failed to migrate run logs to storage for run {}: {}", runId, e.getMessage(), e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void migrateRunLogsToStorage(final Long runId) {
        if (!runLogStorageManager.isRunLogMigrationConfigured()) {
            log.warn(messageHelper.getMessage(MessageConstants.WARN_RUN_LOG_STORAGE_NOT_CONFIGURED));
            return;
        }
        log.debug("Migrating logs for run: {}, as it was configured.", runId);

        final PipelineRun run = runCRUDService.loadRunById(runId);

        if (StringUtils.isNotBlank(run.getLogsStoragePath())) {
            log.warn(messageHelper.getMessage(MessageConstants.WARN_RUN_LOG_MIGRATED, runId,
                    run.getLogsStoragePath()));
            return;
        }

        final List<RunLog> runLogs = runLogDao.loadAllLogsForRun(runId);
        if (runLogs.isEmpty()) {
            log.debug("No logs found in DB for run {}, skipping migration.", runId);
            return;
        }

        final List<PipelineTask> tasks = runLogManager.loadTasksByRunId(runId);
        final Map<String, PipelineTask> tasksByName = tasks.stream()
                .collect(Collectors.toMap(
                    task -> PipelineTask.buildTaskId(task.getName(), task.getParameters()),
                    task -> task,
                    (existing, duplicate) -> existing));

        final Map<PipelineTask, List<RunLog>> logsByTask = runLogs.stream()
                .collect(Collectors.groupingBy(logEntry -> {
                    final String taskId = Optional.ofNullable(logEntry.getTask())
                            .map(t -> PipelineTask.buildTaskId(t.getName(), t.getParameters()))
                            .orElseGet(() -> StringUtils.defaultString(
                                    logEntry.getTaskName(), consoleLogTask));
                    return tasksByName.getOrDefault(taskId, new PipelineTask(taskId));
                }));

        String runLogStoragePath = runLogStorageManager.saveLogsToStorage(runId, logsByTask);
        runCRUDService.updatePipelineRunLogStoragePath(run, runLogStoragePath);
        runLogDao.deleteTaskByRunIdsIn(Collections.singletonList(runId), false);

        log.info(messageHelper.getMessage(MessageConstants.INFO_RUN_LOG_MIGRATED, runId));
    }
}
