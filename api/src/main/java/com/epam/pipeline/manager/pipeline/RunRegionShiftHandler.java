/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.pipeline.RunLog;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.pipeline.run.RestartRun;
import com.epam.pipeline.entity.pipeline.run.parameter.PipelineRunParameter;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.entity.region.RunRegionShiftPolicy;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.region.CloudRegionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RunRegionShiftHandler {
    private static final String RESTART_TASK = "RestartPipelineRun";

    private final PipelineRunManager pipelineRunManager;
    private final CloudRegionManager cloudRegionManager;
    private final RestartRunManager restartRunManager;
    private final RunLogManager runLogManager;
    private final MessageHelper messageHelper;

    public PipelineRun restartRunInAnotherRegion(final long currentRunId) {
        final PipelineRun parentRun = findParentRun(currentRunId);

        final CloudProvider cloudProvider = Optional.ofNullable(parentRun.getInstance())
                .map(RunInstance::getCloudProvider)
                .orElse(null);
        if (Objects.isNull(cloudProvider)) {
            log.debug("Failed to determine cloud provider for run '{}'", parentRun.getId());
            addRunLog(parentRun, messageHelper.getMessage(
                    MessageConstants.ERROR_RESTART_RUN_FAILURE, parentRun.getId()), TaskStatus.FAILURE);
            return null;
        }

        final Map<Long, ? extends AbstractCloudRegion> availableRegions = ListUtils.emptyIfNull(
                        cloudRegionManager.loadAll()).stream()
                .filter(region -> cloudProvider.equals(region.getProvider()))
                .filter(this::isShiftRunEnabled)
                .collect(Collectors.toMap(AbstractCloudRegion::getId, Function.identity()));

        if (validate(parentRun, availableRegions)) {
            return null;
        }

        final Long nextRegionId = findNextRegion(parentRun, availableRegions);
        if (Objects.isNull(nextRegionId)) {
            logRestartFailure(parentRun, messageHelper.getMessage(
                    MessageConstants.ERROR_RUN_NEXT_REGION_NOT_FOUND, parentRun.getId()));
            return null;
        }

        pipelineRunManager.stop(currentRunId);

        Optional.ofNullable(parentRun.getInstance()).ifPresent(instance -> instance.setCloudRegionId(nextRegionId));
        final PipelineRun newRun = pipelineRunManager.restartRun(parentRun);
        addRunLog(parentRun, messageHelper.getMessage(
                MessageConstants.INFO_RESTART_RUN_SUCCESS, parentRun.getId(), nextRegionId), TaskStatus.STOPPED);
        return newRun;
    }

    private boolean validate(final PipelineRun parentRun,
                             final Map<Long, ? extends AbstractCloudRegion> availableRegions) {
        final Long currentRegionId = parentRun.getInstance().getCloudRegionId();
        if (Objects.isNull(currentRegionId)) {
            log.debug("Failed to find region for run '{}'", parentRun.getId());
            addRunLog(parentRun, messageHelper.getMessage(
                    MessageConstants.ERROR_RESTART_RUN_FAILURE, parentRun.getId()), TaskStatus.FAILURE);
            return true;
        }

        if (!availableRegions.containsKey(currentRegionId)) {
            logRestartFailure(parentRun, messageHelper.getMessage(
                    MessageConstants.ERROR_RUN_REGION_SHIFT_FORBIDDEN, currentRegionId));
            return true;
        }

        if (availableRegions.size() == 1) {
            logRestartFailure(parentRun, messageHelper.getMessage(
                    MessageConstants.ERROR_RUN_NEXT_REGION_NOT_FOUND, parentRun.getId()));
            return true;
        }

        if (!validateRunParameters(parentRun)) {
            logRestartFailure(parentRun, messageHelper.getMessage(
                    MessageConstants.ERROR_RUN_PARAMETERS_CLOUD_DEPENDENT, parentRun.getId()));
            return true;
        }

        if (Objects.nonNull(parentRun.getNodeCount()) && parentRun.getNodeCount() > 0) {
            logRestartFailure(parentRun, messageHelper.getMessage(
                    MessageConstants.ERROR_RESTART_CLUSTER_FORBIDDEN, parentRun.getId()));
            return true;
        }

        if (parentRun.isWorkerRun()) {
            logRestartFailure(parentRun, messageHelper.getMessage(
                    MessageConstants.ERROR_RESTART_WORKER_FORBIDDEN, parentRun.getId()));
            return true;
        }
        return false;
    }

    private Long findNextRegion(final PipelineRun parentRun,
                                final Map<Long, ? extends AbstractCloudRegion> availableRegions) {
        final List<Long> previousRunIds = ListUtils.emptyIfNull(parentRun.getRestartedRuns()).stream()
                .map(RestartRun::getRestartedRunId)
                .collect(Collectors.toList());
        final List<Long> retriedRegions = ListUtils.emptyIfNull(
                pipelineRunManager.loadPipelineRuns(previousRunIds)).stream()
                .map(run -> Optional.ofNullable(run.getInstance())
                        .map(RunInstance::getCloudRegionId)
                        .orElse(null))
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        retriedRegions.add(parentRun.getInstance().getCloudRegionId());
        return availableRegions.keySet().stream()
                .filter(region -> !retriedRegions.contains(region))
                .findFirst()
                .orElse(null);
    }

    private boolean validateRunParameters(final PipelineRun parentRun) {
        final List<String> storagePrefixes = DataStorageType.getIds().stream()
                .map(storageType -> storageType.toLowerCase(Locale.ROOT))
                .map(storageType -> storageType + "://")
                .collect(Collectors.toList());
        return ListUtils.emptyIfNull(parentRun.getPipelineRunParameters()).stream()
                .anyMatch(parameter -> validateRunParameter(parameter, storagePrefixes));
    }

    private boolean validateRunParameter(final PipelineRunParameter parameter, final List<String> storagePrefixes) {
        return Optional.ofNullable(parameter.getValue())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .map(value -> storagePrefixes.stream().noneMatch(value::startsWith))
                .orElse(Boolean.TRUE);
    }

    private PipelineRun findParentRun(final Long runId) {
        final Long parentRunId = restartRunManager.findRestartRunById(runId)
                .map(RestartRun::getParentRunId)
                .orElse(runId);
        final PipelineRun run = pipelineRunManager.loadPipelineRunWithRestartedRuns(parentRunId);
        if (!Objects.equals(parentRunId, runId)) {
            setSpotToParent(runId, run);
        }
        return run;
    }

    private void setSpotToParent(final Long runId, final PipelineRun parentRun) {
        Optional.ofNullable(parentRun.getInstance())
                .ifPresent(parentInstance -> parentInstance.setSpot(
                        Optional.ofNullable(pipelineRunManager.loadPipelineRun(runId).getInstance())
                                .map(RunInstance::getSpot)
                                .orElse(parentInstance.getSpot())
                ));
    }

    private boolean isShiftRunEnabled(final AbstractCloudRegion region) {
        return Optional.ofNullable(region.getRunShiftPolicy())
                .map(RunRegionShiftPolicy::isShiftEnabled)
                .orElse(Boolean.FALSE);
    }

    private void logRestartFailure(final PipelineRun run, final String logMessage) {
        log.debug(logMessage);
        addRunLog(run, logMessage, TaskStatus.FAILURE);
    }

    private void addRunLog(final PipelineRun run, final String logMessage, final TaskStatus status) {
        final RunLog runLog = RunLog.builder()
                .date(DateUtils.now())
                .runId(run.getId())
                .instance(run.getPodId())
                .status(status)
                .taskName(RESTART_TASK)
                .logText(logMessage)
                .build();
        runLogManager.saveLog(runLog);
    }
}