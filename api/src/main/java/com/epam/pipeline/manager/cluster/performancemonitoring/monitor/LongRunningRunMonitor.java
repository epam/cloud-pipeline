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

import com.epam.pipeline.entity.notification.NotificationSettings;
import com.epam.pipeline.entity.notification.NotificationType;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.pipeline.run.RunStatus;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.notification.NotificationManager;
import com.epam.pipeline.manager.notification.NotificationSettingsManager;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.pipeline.RunStatusManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.utils.RunDurationUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@Slf4j
public class LongRunningRunMonitor implements RunMonitor {

    private final NotificationManager notificationManager;
    private final NotificationSettingsManager notificationSettingsManager;
    private final PipelineRunManager pipelineRunManager;
    private final RunStatusManager runStatusManager;
    private final PreferenceManager preferenceManager;

    @Autowired
    public LongRunningRunMonitor(final NotificationManager notificationManager,
                                  final NotificationSettingsManager notificationSettingsManager,
                                  final PipelineRunManager pipelineRunManager,
                                  final RunStatusManager runStatusManager,
                                  final PreferenceManager preferenceManager) {
        this.notificationManager = notificationManager;
        this.notificationSettingsManager = notificationSettingsManager;
        this.pipelineRunManager = pipelineRunManager;
        this.runStatusManager = runStatusManager;
        this.preferenceManager = preferenceManager;
    }

    @Override
    public void monitor(final List<PipelineRun> runs) {
        ListUtils.emptyIfNull(runs).stream()
                .filter(run -> !run.isWorkerRun())
                .forEach(run -> {
                    final NotificationType type = StringUtils.isEmpty(run.getPodIP())
                            ? NotificationType.LONG_INIT
                            : NotificationType.LONG_RUNNING;
                    notifyIfExceedsThreshold(run, type);
                });
    }

    private void notifyIfExceedsThreshold(final PipelineRun run, final NotificationType type) {
        final NotificationSettings settings = notificationSettingsManager.load(type);
        if (settings == null || !settings.isEnabled()) {
            log.warn("Notification settings not found or disabled for type: {}", type);
            return;
        }
        final long threshold = settings.getThreshold();
        if (threshold <= 0) {
            return;
        }
        final long duration = runningDurationOf(run);
        if (duration >= threshold
                && checkNeedOfNotificationResend(run.getLastNotificationTime(), settings.getResendDelay())) {
            notificationManager.notifyLongRunningTask(run, duration, type, settings);
            run.setLastNotificationTime(DateUtils.now());
            pipelineRunManager.updatePipelineRunLastNotification(run);
            tagRunWithLongOperation(run, type);
        }
    }

    private void tagRunWithLongOperation(final PipelineRun run, final NotificationType type) {
        final String tag = type.name();
        if (run.addTag(tag, TRUE_VALUE_STRING)) {
            Optional.ofNullable(getTimestampTag(tag))
                    .ifPresent(timestampTag -> run.addTag(timestampTag, DateUtils.nowUTCStr()));
            log.debug("Successfully set tag {} for run: {}.", tag, run.getId());
            pipelineRunManager.updateRunsTags(Collections.singletonList(run));
        } else {
            log.debug("Run: {} already has {} tag.", run.getId(), tag);
        }
    }

    private String getTimestampTag(final String tag) {
        final String suffix = preferenceManager.getPreference(SystemPreferences.SYSTEM_RUN_TAG_DATE_SUFFIX);
        return StringUtils.isNotEmpty(suffix) ? tag + suffix : null;
    }

    private long runningDurationOf(final PipelineRun run) {
        return Optional.of(run)
                .map(PipelineRun::getId)
                .map(runStatusManager::loadRunStatus)
                .filter(CollectionUtils::isNotEmpty)
                .map(statuses -> statuses.stream()
                        .sorted(Comparator.comparing(RunStatus::getTimestamp))
                        .collect(Collectors.toList()))
                .map(statuses -> {
                    final RunStatus last = statuses.get(statuses.size() - 1);
                    return TaskStatus.RUNNING.equals(last.getStatus())
                            ? Duration.between(last.getTimestamp(), DateUtils.nowUTC()).getSeconds()
                            : 0L;
                })
                .filter(duration -> duration > 0)
                .orElseGet(() -> RunDurationUtils.getOverallDuration(run).getSeconds());
    }

    private boolean checkNeedOfNotificationResend(final Date lastNotificationDate, final long resendDelay) {
        return lastNotificationDate == null
                || resendDelay != 0
                && Duration.between(lastNotificationDate.toInstant(),
                        DateUtils.now().toInstant()).abs().getSeconds() >= resendDelay;
    }
}
