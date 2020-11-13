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

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dao.pipeline.PipelineRunDao;
import com.epam.pipeline.entity.pipeline.DockerRegistry;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.pipeline.RunLog;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.pipeline.run.RunStatus;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.cluster.performancemonitoring.UsageMonitoringManager;
import com.epam.pipeline.manager.docker.DockerContainerOperationManager;
import com.epam.pipeline.manager.docker.DockerRegistryManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * This class contains methods for pipeline run routine related to docker container operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PipelineRunDockerOperationManager {
    private final DockerContainerOperationManager dockerContainerOperationManager;
    private final PipelineRunManager pipelineRunManager;
    private final DockerRegistryManager dockerRegistryManager;
    private final ToolManager toolManager;
    private final PipelineRunDao pipelineRunDao;
    private final PipelineRunCRUDService runCRUDService;
    private final UsageMonitoringManager usageMonitoringManager;
    private final RunLogManager runLogManager;
    private final RunStatusManager runStatusManager;
    private final MessageHelper messageHelper;
    private final PreferenceManager preferenceManager;

    /**
     * Commits docker image and push it to a docker registry from specified run
     * @param id {@link PipelineRun} id for pipeline run to be committed
     * @param registryId {@link DockerRegistry} id where new image will be pushed
     * @param deleteFiles if true files from pipeline working directory will be cleaned
     * @param stopPipeline if true pipeline will be stopped after commit
     * @param checkSize if true method will check if free disk space is enough for commit operation
     * @return  {@link PipelineRun} to be committed
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public PipelineRun commitRun(Long id, Long registryId, String newImageName, boolean deleteFiles,
                                 boolean stopPipeline, boolean checkSize) {
        if (checkSize) {
            Assert.state(checkFreeSpaceAvailable(id),
                    messageHelper.getMessage(MessageConstants.ERROR_INSTANCE_DISK_NOT_ENOUGH));
        }

        PipelineRun pipelineRun = pipelineRunDao.loadPipelineRun(id);
        DockerRegistry dockerRegistry = dockerRegistryManager.load(registryId);
        Assert.notNull(pipelineRun,
                messageHelper.getMessage(MessageConstants.ERROR_RUN_PIPELINES_NOT_FOUND, id));
        Assert.state(pipelineRun.getStatus() == TaskStatus.RUNNING,
                messageHelper.getMessage(MessageConstants.ERROR_PIPELINE_RUN_FINISHED, id));
        Assert.notNull(dockerRegistry,
                messageHelper.getMessage(MessageConstants.ERROR_REGISTRY_NOT_FOUND, registryId));
        String dockerImageFromRun = retrieveImageName(pipelineRun);
        String resolvedImageName = StringUtils.isEmpty(newImageName) ? dockerImageFromRun : newImageName;

        //check that there is no tool with this name in another registry
        toolManager.assertThatToolUniqueAcrossRegistries(resolvedImageName, dockerRegistry.getPath());

        return dockerContainerOperationManager.commitContainer(
                pipelineRun,
                dockerRegistry,
                resolvedImageName,
                deleteFiles,
                stopPipeline
        );
    }

    /**
     * Pauses pipeline run for specified {@code runId}.
     * @param runId {@link PipelineRun} id for pipeline run to be paused
     * @param checkSize if true method will check if free disk space is enough for commit operation
     * @return paused {@link PipelineRun}
     */
    public PipelineRun pauseRun(Long runId, boolean checkSize) {
        if (checkSize) {
            Assert.state(checkFreeSpaceAvailable(runId), MessageConstants.ERROR_INSTANCE_DISK_NOT_ENOUGH);
        }
        PipelineRun pipelineRun = loadRunForPauseResume(runId);
        Assert.isTrue(pipelineRun.getInitialized(),
                messageHelper.getMessage(MessageConstants.ERROR_PIPELINE_RUN_NOT_INITIALIZED, runId));
        Assert.notNull(pipelineRun.getDockerImage(),
                messageHelper.getMessage(MessageConstants.ERROR_DOCKER_IMAGE_NOT_FOUND, runId));
        Assert.state(pipelineRun.getStatus() == TaskStatus.RUNNING,
                messageHelper.getMessage(MessageConstants.ERROR_PIPELINE_RUN_FINISHED, runId));
        pipelineRun.setStatus(TaskStatus.PAUSING);
        runCRUDService.updateRunStatus(pipelineRun);
        dockerContainerOperationManager.pauseRun(pipelineRun, false);
        return pipelineRun;
    }

    /**
     * Resumes pipeline run for specified {@code runId}.
     * @param runId {@link PipelineRun} id for pipeline run to be resumed
     * @return resumed {@link PipelineRun}
     */
    public PipelineRun resumeRun(Long runId) {
        PipelineRun pipelineRun = loadRunForPauseResume(runId);
        Assert.state(pipelineRun.getStatus() == TaskStatus.PAUSED,
                messageHelper.getMessage(MessageConstants.ERROR_PIPELINE_RUN_NOT_STOPPED, runId));
        if (StringUtils.isEmpty(pipelineRun.getActualCmd())) {
            throw new IllegalArgumentException(messageHelper.getMessage(
                    MessageConstants.ERROR_ACTUAL_CMD_NOT_FOUND, runId));
        }
        Tool tool = toolManager.loadByNameOrId(pipelineRun.getDockerImage());
        pipelineRun.setStatus(TaskStatus.RESUMING);
        // prolong the run here in order to get rid off idle notification right after resume
        pipelineRunManager.prolongIdleRun(pipelineRun.getId());
        runCRUDService.updateRunStatus(pipelineRun);
        dockerContainerOperationManager.resumeRun(pipelineRun, tool.getEndpoints());
        return pipelineRun;
    }

    /**
     * Checks that free node space is enough. Calls before commit/pause operations.
     * @param runId {@link PipelineRun} id for pipeline run
     * @return true if free space is enough
     */
    public Boolean checkFreeSpaceAvailable(final Long runId) {
        final PipelineRun pipelineRun = pipelineRunDao.loadPipelineRun(runId);
        Assert.notNull(pipelineRun,
                messageHelper.getMessage(MessageConstants.ERROR_RUN_PIPELINES_NOT_FOUND, runId));
        final long availableDisk = usageMonitoringManager.getDiskSpaceAvailable(
                pipelineRun.getInstance().getNodeName(), pipelineRun.getPodId(), pipelineRun.getDockerImage());
        final long requiredImageSize = (long)Math.ceil(
                (double)toolManager.getCurrentImageSize(pipelineRun.getDockerImage())
                        * preferenceManager.getPreference(SystemPreferences.CLUSTER_DOCKER_EXTRA_MULTI) / 2);
        log.debug("Run {} available disk: {} required for image size: {}", runId, availableDisk, requiredImageSize);
        if (availableDisk < requiredImageSize) {
            log.warn("Free disk space is not enough");
            return false;
        }
        return true;
    }

    /**
     * Reruns pause and resume operations if them still in progress. This method shall be executed at the
     * application startup.
     */
    public void rerunPauseAndResume() {
        final List<PipelineRun> pausingRuns = pipelineRunManager
                .loadRunsByStatuses(Collections.singletonList(TaskStatus.PAUSING));
        ListUtils.emptyIfNull(pausingRuns).forEach(this::rerunPauseRun);

        final List<PipelineRun> resumingRuns = pipelineRunManager
                .loadRunsByStatuses(Collections.singletonList(TaskStatus.RESUMING));
        ListUtils.emptyIfNull(resumingRuns).forEach(this::rerunResumeRun);
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private void rerunPauseRun(final PipelineRun run) {
        try {
            if (!needToRerunPause(run)) {
                log.debug("Run '{}' is in PAUSING state but pause operation cannot be relaunched", run.getId());
                return;
            }
            log.debug("Pause run operation will be relaunched for run '{}'", run.getId());
            dockerContainerOperationManager.pauseRun(run, true);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private void rerunResumeRun(final PipelineRun run) {
        try {
            log.debug("Resume run operation will be relaunched for run '{}'", run.getId());
            final Tool tool = toolManager.loadByNameOrId(run.getDockerImage());
            dockerContainerOperationManager.resumeRun(run, tool.getEndpoints());
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    private PipelineRun loadRunForPauseResume(Long runId) {
        PipelineRun pipelineRun = pipelineRunDao.loadPipelineRun(runId);
        verifyPipelineRunForPauseResume(pipelineRun, runId);
        pipelineRun.setSshPassword(pipelineRunDao.loadSshPassword(runId));
        return pipelineRun;
    }

    private void verifyPipelineRunForPauseResume(PipelineRun pipelineRun, Long runId) {
        Assert.notNull(pipelineRun,
                messageHelper.getMessage(MessageConstants.ERROR_RUN_PIPELINES_NOT_FOUND, runId));
        Assert.notNull(pipelineRun.getId(),
                messageHelper.getMessage(MessageConstants.ERROR_PIPELINE_RUN_ID_NOT_FOUND, runId));
        Assert.notNull(pipelineRun.getInstance(), messageHelper.getMessage(
                MessageConstants.ERROR_INSTANCE_NOT_FOUND, runId));
        RunInstance instance = pipelineRun.getInstance();
        Assert.notNull(instance.getNodeId(), messageHelper.getMessage(
                MessageConstants.ERROR_INSTANCE_ID_NOT_FOUND, runId));
        Assert.notNull(instance.getNodeIP(), messageHelper.getMessage(
                MessageConstants.ERROR_INSTANCE_IP_NOT_FOUND, runId));
        Assert.isTrue(!instance.getSpot(), messageHelper.getMessage(MessageConstants.ERROR_ON_DEMAND_REQUIRED));
        Assert.notNull(pipelineRun.getPodId(),
                messageHelper.getMessage(MessageConstants.ERROR_POD_ID_NOT_FOUND, runId));
    }

    private String retrieveImageName(PipelineRun pipelineRun) {
        String[] registryAndDockerImageFromRun = pipelineRun.getActualDockerImage().split("/");
        return registryAndDockerImageFromRun.length == 1
                ? registryAndDockerImageFromRun[0]
                : registryAndDockerImageFromRun[1];
    }

    private boolean needToRerunPause(final PipelineRun run) {
        final Optional<LocalDateTime> lastStatusUpdateDate = ListUtils
                .emptyIfNull(runStatusManager.loadRunStatus(run.getId())).stream()
                .filter(status -> TaskStatus.PAUSING.equals(status.getStatus()))
                .map(RunStatus::getTimestamp)
                .max(LocalDateTime::compareTo);
        if (!lastStatusUpdateDate.isPresent()) {
            return false;
        }
        final List<RunLog> runLogs = runLogManager.loadAllLogsForTask(run.getId(),
                DockerContainerOperationManager.PAUSE_RUN_TASK);
        final Optional<Date> lastSuccessTaskDate = ListUtils.emptyIfNull(runLogs).stream()
                .filter(log -> TaskStatus.SUCCESS.equals(log.getStatus()))
                .map(RunLog::getDate)
                .max(Date::compareTo);
        return lastSuccessTaskDate.isPresent()
                && DateUtils.convertDateToLocalDateTime(lastSuccessTaskDate.get())
                .isAfter(lastStatusUpdateDate.get());
    }
}
