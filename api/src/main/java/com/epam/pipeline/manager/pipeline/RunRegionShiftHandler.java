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
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.region.CloudRegionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.BooleanUtils;
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

    static final String CP_CAP_RESCHEDULE_RUN_PARAM = "CP_CAP_RESCHEDULE_RUN";

    private static final String RESTART_TASK = "RestartPipelineRun";

    private final PipelineRunManager pipelineRunManager;
    private final CloudRegionManager cloudRegionManager;
    private final RestartRunManager restartRunManager;
    private final RunLogManager runLogManager;
    private final MessageHelper messageHelper;
    private final PreferenceManager preferenceManager;

    public PipelineRun restartRunInAnotherRegion(final long currentRunId) {
        final Long parentRunId = restartRunManager.findRestartRunById(currentRunId)
                .map(RestartRun::getParentRunId)
                .orElse(currentRunId);
        final PipelineRun parentRun = pipelineRunManager.loadPipelineRunWithRestartedRuns(parentRunId);
        final PipelineRun currentRun = Objects.equals(currentRunId, parentRunId)
                ? parentRun
                : pipelineRunManager.loadPipelineRun(currentRunId);

        addRunLog(parentRun, messageHelper.getMessage(
                MessageConstants.ERROR_RUN_START_FAILURE, currentRunId), TaskStatus.FAILURE, currentRun);

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

        if (!validate(parentRun, currentRun, availableRegions)) {
            return null;
        }

        final Long nextRegionId = findNextRegion(parentRun, availableRegions);
        if (Objects.isNull(nextRegionId)) {
            logRestartFailure(parentRun, messageHelper.getMessage(
                    MessageConstants.ERROR_RUN_NEXT_REGION_NOT_FOUND, currentRunId), currentRun);
            return null;
        }

        pipelineRunManager.stop(currentRunId);

        Optional.ofNullable(parentRun.getInstance()).ifPresent(instance -> instance.setCloudRegionId(nextRegionId));
        Optional.ofNullable(parentRun.getInstance()).ifPresent(instance -> instance.setSpot(false));
        final PipelineRun newRun = pipelineRunManager.restartRun(parentRun);
        final String nextRegionName = availableRegions.get(nextRegionId).getName();
        addRunLog(parentRun, messageHelper.getMessage(MessageConstants.INFO_RESTART_RUN_SUCCESS, newRun.getId(),
                nextRegionName), TaskStatus.STOPPED, currentRun);
        return newRun;
    }

    private boolean validate(final PipelineRun parentRun, final PipelineRun currentRun,
                             final Map<Long, ? extends AbstractCloudRegion> availableRegions) {
        final Long currentRegionId = parentRun.getInstance().getCloudRegionId();
        if (Objects.isNull(currentRegionId)) {
            log.debug("Failed to find region for run '{}'", parentRun.getId());
            addRunLog(parentRun, messageHelper.getMessage(
                    MessageConstants.ERROR_RESTART_RUN_FAILURE, parentRun.getId()), TaskStatus.FAILURE);
            return false;
        }

        if (!availableRegions.containsKey(currentRegionId)) {
            final String regionName = cloudRegionManager.load(currentRegionId).getName();
            logRestartFailure(parentRun, messageHelper.getMessage(
                    MessageConstants.ERROR_RUN_REGION_RELAUNCH_FORBIDDEN, regionName), currentRun);
            return false;
        }

        if (availableRegions.size() == 1) {
            logRestartFailure(parentRun, messageHelper.getMessage(
                    MessageConstants.ERROR_RUN_NEXT_REGION_NOT_FOUND, currentRun.getId()), currentRun);
            return false;
        }

        if (!isRunParametersValid(parentRun)) {
            logRestartFailure(parentRun, messageHelper.getMessage(
                    MessageConstants.ERROR_RUN_PARAMETERS_CLOUD_DEPENDENT, currentRun.getId()), currentRun);
            return false;
        }

        if (Objects.nonNull(parentRun.getNodeCount()) && parentRun.getNodeCount() > 0) {
            logRestartFailure(parentRun, messageHelper.getMessage(
                    MessageConstants.ERROR_RESTART_CLUSTER_FORBIDDEN, currentRun.getId()), currentRun);
            return false;
        }

        if (parentRun.isWorkerRun()) {
            logRestartFailure(parentRun, messageHelper.getMessage(
                    MessageConstants.ERROR_RESTART_WORKER_FORBIDDEN, currentRun.getId()), currentRun);
            return false;
        }

        final boolean shouldReschedule = parentRun.getParameterValue(CP_CAP_RESCHEDULE_RUN_PARAM)
                .map(BooleanUtils::toBoolean)
                .orElse(preferenceManager.getPreference(SystemPreferences.LAUNCH_RUN_RESCHEDULE_ENABLED));

        if (!shouldReschedule) {
            logRestartFailure(parentRun, messageHelper.getMessage(
                    MessageConstants.ERROR_RUN_IS_NOT_CONFIGURED_FOR_RESTART, parentRun.getId()), parentRun);
            return false;
        }

        return true;
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

    private boolean isRunParametersValid(final PipelineRun parentRun) {
        if (CollectionUtils.isEmpty(parentRun.getPipelineRunParameters())) {
            return Boolean.TRUE;
        }
        final List<String> storagePrefixes = DataStorageType.getIds().stream()
                .map(storageType -> storageType.toLowerCase(Locale.ROOT))
                .map(storageType -> storageType + "://")
                .collect(Collectors.toList());
        return parentRun.getPipelineRunParameters().stream()
                .noneMatch(parameter -> isRunParameterInvalid(parameter, storagePrefixes));
    }

    private boolean isRunParameterInvalid(final PipelineRunParameter parameter, final List<String> storagePrefixes) {
        return Optional.ofNullable(parameter.getValue())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .map(value -> storagePrefixes.stream().anyMatch(value::startsWith))
                .orElse(Boolean.FALSE);
    }

    private boolean isShiftRunEnabled(final AbstractCloudRegion region) {
        return Optional.ofNullable(region.getRunShiftPolicy())
                .map(RunRegionShiftPolicy::isShiftEnabled)
                .orElse(Boolean.FALSE);
    }

    private void logRestartFailure(final PipelineRun parentRun, final String logMessage, final PipelineRun currentRun) {
        log.debug(logMessage);
        addRunLog(parentRun, logMessage, TaskStatus.FAILURE, currentRun);
    }

    private void addRunLog(final PipelineRun parentRun, final String logMessage, final TaskStatus status,
                           final PipelineRun currentRun) {
        addRunLog(parentRun, logMessage, status);
        if (!Objects.equals(parentRun.getId(), currentRun.getId())) {
            addRunLog(currentRun, logMessage, status);
        }
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
