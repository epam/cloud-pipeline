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

import com.epam.pipeline.entity.monitoring.LongPausedRunAction;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.pipeline.run.RunStatus;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.notification.NotificationManager;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.pipeline.RunStatusManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Slf4j
public class LongPausedRunMonitor implements RunMonitor {

    private final PipelineRunManager pipelineRunManager;
    private final NotificationManager notificationManager;
    private final RunStatusManager runStatusManager;
    private final PreferenceManager preferenceManager;

    @Autowired
    public LongPausedRunMonitor(final PipelineRunManager pipelineRunManager,
                                 final NotificationManager notificationManager,
                                 final RunStatusManager runStatusManager,
                                 final PreferenceManager preferenceManager) {
        this.pipelineRunManager = pipelineRunManager;
        this.notificationManager = notificationManager;
        this.runStatusManager = runStatusManager;
        this.preferenceManager = preferenceManager;
    }

    @Override
    public void monitor(final List<PipelineRun> runs) {
        processPausingResumingRuns();
        processLongPausedRuns();
    }

    private void processPausingResumingRuns() {
        final List<PipelineRun> runsWithStatuses = pipelineRunManager
                .loadRunsByStatuses(Arrays.asList(TaskStatus.PAUSING, TaskStatus.RESUMING))
                .stream()
                .map(run -> pipelineRunManager.loadPipelineRunWithRestartedRuns(run.getId()))
                .collect(Collectors.toList());
        if (CollectionUtils.isNotEmpty(runsWithStatuses)) {
            notificationManager.notifyStuckInStatusRuns(runsWithStatuses);
        }
    }

    private void processLongPausedRuns() {
        final LongPausedRunAction action = LongPausedRunAction.valueOf(preferenceManager.getPreference(
                SystemPreferences.SYSTEM_LONG_PAUSED_ACTION));
        final int actionTimeout = preferenceManager.getPreference(
                SystemPreferences.SYSTEM_LONG_PAUSED_ACTION_TIMEOUT_MINUTES);

        final List<PipelineRun> pausedRuns = pipelineRunManager
                .loadRunsByStatuses(Collections.singletonList(TaskStatus.PAUSED));
        if (CollectionUtils.isEmpty(pausedRuns)) {
            return;
        }
        final Map<Long, List<RunStatus>> statuses = runStatusManager.loadRunStatus(
                pausedRuns.stream()
                        .map(PipelineRun::getId)
                        .collect(Collectors.toList()), false);

        pausedRuns.forEach(run -> run.setRunStatuses(statuses.get(run.getId())));
        processLongPausedRuns(pausedRuns, action, actionTimeout);
    }

    private void processLongPausedRuns(final List<PipelineRun> pausedRuns,
                                        final LongPausedRunAction action,
                                        final int actionTimeout) {
        if (CollectionUtils.isEmpty(pausedRuns)) {
            return;
        }
        if (LongPausedRunAction.STOP.equals(action)) {
            final Map<Boolean, List<PipelineRun>> runs = pausedRuns.stream()
                    .collect(Collectors.partitioningBy(
                        run -> !run.isNonPause() && isReadyForAction(run, actionTimeout)));
            final List<PipelineRun> runsToStop = ListUtils.emptyIfNull(runs.get(true));
            final List<PipelineRun> terminatedRuns =
                    ListUtils.emptyIfNull(notificationManager.notifyLongPausedRunsBeforeStop(runsToStop))
                            .stream()
                            .map(run -> pipelineRunManager.terminateRun(run.getId()))
                            .collect(Collectors.toList());

            final String stopTag = preferenceManager.getPreference(SystemPreferences.SYSTEM_RUN_TAG_STOP_REASON);
            if (StringUtils.isNotBlank(stopTag)) {
                terminatedRuns.forEach(run -> run.addTag(stopTag, "LONG_PAUSED"));
                pipelineRunManager.updateRunsTags(terminatedRuns);
            }

            final List<PipelineRun> runsToNotify = ListUtils.emptyIfNull(runs.get(false));
            notificationManager.notifyLongPausedRuns(runsToNotify);
        } else {
            notificationManager.notifyLongPausedRuns(pausedRuns);
        }
    }

    private boolean isReadyForAction(final PipelineRun pausedRun, final int actionTimeout) {
        return ListUtils.emptyIfNull(pausedRun.getRunStatuses()).stream()
                .filter(status -> TaskStatus.PAUSED.equals(status.getStatus()))
                .max(Comparator.comparing(RunStatus::getTimestamp))
                .map(status -> status.getTimestamp().isBefore(DateUtils.nowUTC().minusMinutes(actionTimeout)))
                .orElse(false);
    }
}
