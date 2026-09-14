/*
 * Copyright 2017-2025 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.cluster.performancemonitoring.monitor;

import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.run.PipelineRunEmergencyTermAction;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.cluster.NodesManager;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

@Component
@Slf4j
public class StuckRunMonitor implements RunMonitor {

    private static final String WORK_FINISHED_TAG = "WORK_FINISHED";
    private static final String CP_TERMINATE_RUN_ON_CLEANUP_TIMEOUT_MIN_PARAM =
            "CP_TERMINATE_RUN_ON_CLEANUP_TIMEOUT_MIN";

    private final PipelineRunManager pipelineRunManager;
    private final PreferenceManager preferenceManager;
    private final NodesManager nodesManager;

    @Autowired
    public StuckRunMonitor(final PipelineRunManager pipelineRunManager,
                            final PreferenceManager preferenceManager,
                            final NodesManager nodesManager) {
        this.pipelineRunManager = pipelineRunManager;
        this.preferenceManager = preferenceManager;
        this.nodesManager = nodesManager;
    }

    @Override
    public void monitor(final List<PipelineRun> runs) {
        log.info("Start emergency runs termination cycle.");
        final PipelineRunEmergencyTermAction termAction =
                preferenceManager.getPreference(SystemPreferences.LAUNCH_RUN_EMERGENCY_TERM_ACTION);
        final Integer defaultRunEmergencyTermDelay = preferenceManager.getPreference(
                SystemPreferences.LAUNCH_RUN_EMERGENCY_TERM_DELAY_MIN);

        if (termAction.equals(PipelineRunEmergencyTermAction.DISABLED)) {
            log.info("Emergency run termination disabled. Will not check running runs!");
            return;
        }

        CollectionUtils.emptyIfNull(runs).stream()
                .filter(run -> MapUtils.emptyIfNull(run.getTags()).containsKey(WORK_FINISHED_TAG))
                .forEach(run -> {
                    final int emergencyTerminationDelay = Optional.ofNullable(
                            MapUtils.emptyIfNull(run.getEnvVars()).get(CP_TERMINATE_RUN_ON_CLEANUP_TIMEOUT_MIN_PARAM)
                    ).map(v -> {
                        try {
                            return Integer.parseInt(v);
                        } catch (NumberFormatException e) {
                            log.warn("Can't parse CP_TERMINATE_RUN_ON_CLEANUP_TIMEOUT_MIN: {} for run: {}." +
                                     " Will use default one!", v, run.getId());
                            return null;
                        }
                    }).orElse(defaultRunEmergencyTermDelay);

                    try {
                        final LocalDateTime workFinishedTime =
                                DateUtils.strToUTCDate(run.getTags().get(WORK_FINISHED_TAG));
                        if (workFinishedTime.isBefore(DateUtils.nowUTC().minusMinutes(emergencyTerminationDelay))) {
                            log.warn("Run: {} marked as finished on: {} and should be stopped forcefully, action: {}",
                                    run.getId(), workFinishedTime, termAction);
                            performEmergencyTermAction(run, termAction);
                        } else {
                            log.debug("Run: {} marked as finished on: {}, waiting period: {} min. Skipping.",
                                    run.getId(), workFinishedTime, emergencyTerminationDelay);
                        }
                    } catch (DateTimeParseException e) {
                        log.error("Problem to parse date while processing possibly stuck run: {}", run.getId());
                    }
                });
    }

    private void performEmergencyTermAction(final PipelineRun run,
                                             final PipelineRunEmergencyTermAction termAction) {
        switch (termAction) {
            case STOP:
                pipelineRunManager.updatePipelineStatusIfNotFinal(run.getId(), TaskStatus.STOPPED);
                break;
            case TERMINATE_NODE:
                final String nodeName = Optional.ofNullable(run.getInstance())
                        .map(RunInstance::getNodeName).orElse(null);
                if (nodeName != null) {
                    nodesManager.terminateNode(nodeName);
                } else {
                    log.error("Can't get node name for run: {}", run.getId());
                }
                break;
            default:
                break;
        }
    }
}
