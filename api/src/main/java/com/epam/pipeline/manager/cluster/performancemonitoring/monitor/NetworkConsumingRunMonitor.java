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

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dao.monitoring.MonitoringESDao;
import com.epam.pipeline.entity.cluster.monitoring.ELKUsageMetric;
import com.epam.pipeline.entity.monitoring.NetworkConsumingRunAction;
import com.epam.pipeline.entity.notification.NotificationType;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.notification.NotificationManager;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Component
@Slf4j
public class NetworkConsumingRunMonitor implements RunMonitor {

    private static final long ONE = 1L;
    private static final String NETWORK_CONSUMING_LEVEL_HIGH = "NETWORK_PRESSURE";
    private static final String TRUE_VALUE_STRING = "true";

    private final PipelineRunManager pipelineRunManager;
    private final NotificationManager notificationManager;
    private final MonitoringESDao monitoringDao;
    private final MessageHelper messageHelper;
    private final PreferenceManager preferenceManager;

    @Autowired
    public NetworkConsumingRunMonitor(final PipelineRunManager pipelineRunManager,
                                       final NotificationManager notificationManager,
                                       final MonitoringESDao monitoringDao,
                                       final MessageHelper messageHelper,
                                       final PreferenceManager preferenceManager) {
        this.pipelineRunManager = pipelineRunManager;
        this.notificationManager = notificationManager;
        this.monitoringDao = monitoringDao;
        this.messageHelper = messageHelper;
        this.preferenceManager = preferenceManager;
    }

    @Override
    public void monitor(final List<PipelineRun> runs) {
        final double bandwidthLimit = preferenceManager.getPreference(
                SystemPreferences.SYSTEM_POD_BANDWIDTH_LIMIT);
        final int actionTimeout = preferenceManager.getPreference(
                SystemPreferences.SYSTEM_POD_BANDWIDTH_ACTION_BACKOFF_PERIOD);
        final NetworkConsumingRunAction action = NetworkConsumingRunAction.valueOf(preferenceManager
                .getPreference(SystemPreferences.SYSTEM_POD_BANDWIDTH_ACTION));

        if (bandwidthLimit <= 0) {
            log.debug(messageHelper.getMessage(MessageConstants.DEBUG_RUN_NOT_NETWORK_CONSUMING_DISABLED));
            return;
        }

        final Map<String, PipelineRun> running = RunMonitorUtils.groupedByNode(runs, messageHelper);
        final int bandwidthLimitTimeout = preferenceManager.getPreference(
                SystemPreferences.SYSTEM_MAX_POD_BANDWIDTH_LIMIT_TIMEOUT_MINUTES);

        log.debug(messageHelper.getMessage(MessageConstants.DEBUG_RUN_METRICS_REQUEST,
                "NETWORK", running.size(), String.join(", ", running.keySet())));

        final LocalDateTime now = DateUtils.nowUTC();
        final Map<String, Double> networkMetrics = monitoringDao.loadMetrics(ELKUsageMetric.NETWORK,
                running.keySet(), now.minusMinutes(bandwidthLimitTimeout + ONE), now);
        log.debug(messageHelper.getMessage(MessageConstants.DEBUG_NETWORK_RUN_METRICS_RECEIVED,
                networkMetrics.entrySet().stream()
                        .map(e -> e.getKey() + ":" + e.getValue())
                        .collect(java.util.stream.Collectors.joining(", ")))
        );

        processHighNetworkConsumingRuns(running, networkMetrics, bandwidthLimit, actionTimeout, action);
    }

    private void processHighNetworkConsumingRuns(final Map<String, PipelineRun> running,
                                                  final Map<String, Double> networkMetrics,
                                                  final double bandwidthLimit,
                                                  final int actionTimeout,
                                                  final NetworkConsumingRunAction action) {
        final List<PipelineRun> runsToUpdateNotificationTime = new ArrayList<>(running.size());
        final List<Pair<PipelineRun, Double>> runsToNotify = new ArrayList<>(running.size());
        final List<PipelineRun> runsToUpdateTags = new ArrayList<>(running.size());
        for (Map.Entry<String, PipelineRun> entry : running.entrySet()) {
            final PipelineRun run = entry.getValue();
            final Double bandwidth = networkMetrics.get(entry.getKey());
            if (bandwidth != null) {
                if (bandwidth >= bandwidthLimit) {
                    processHighNetworkConsumingRun(run, actionTimeout, action, runsToNotify,
                            runsToUpdateNotificationTime, bandwidth, runsToUpdateTags);
                } else if (run.getLastNetworkConsumptionNotificationTime() != null) {
                    log.debug(messageHelper.getMessage(MessageConstants.DEBUG_RUN_NOT_NETWORK_CONSUMING,
                            run.getPodId(), bandwidth));
                    processFormerHighNetworkConsumingRun(run, runsToUpdateNotificationTime, runsToUpdateTags);
                }
            }
        }
        notificationManager.notifyHighNetworkConsumingRuns(runsToNotify,
                NotificationType.HIGH_CONSUMED_NETWORK_BANDWIDTH);
        pipelineRunManager.updatePipelineRunsLastNotification(runsToUpdateNotificationTime);
        pipelineRunManager.updateRunsTags(runsToUpdateTags);
    }

    private void processHighNetworkConsumingRun(final PipelineRun run, final int actionTimeout,
                                                 final NetworkConsumingRunAction action,
                                                 final List<Pair<PipelineRun, Double>> runsToNotify,
                                                 final List<PipelineRun> runsToUpdateNotificationTime,
                                                 final Double bandwidth,
                                                 final List<PipelineRun> runsToUpdateTags) {
        if (Objects.isNull(run.getLastNetworkConsumptionNotificationTime())) {
            run.addTag(NETWORK_CONSUMING_LEVEL_HIGH, TRUE_VALUE_STRING);
            Optional.ofNullable(RunMonitorUtils.getTimestampTag(NETWORK_CONSUMING_LEVEL_HIGH, preferenceManager))
                    .ifPresent(tag -> run.addTag(tag, DateUtils.nowUTCStr()));
            runsToUpdateTags.add(run);
            log.info(messageHelper.getMessage(MessageConstants.INFO_RUN_HIGH_NETWORK_CONSUMPTION_NOTIFY,
                    run.getPodId(), bandwidth));
            performHighNetworkConsumingNotify(run, bandwidth, runsToNotify, runsToUpdateNotificationTime);
        } else if (shouldPerformActionOnNetworkConsumingRun(run, actionTimeout)) {
            performActionOnNetworkConsumingRun(run, action, bandwidth, runsToNotify, runsToUpdateNotificationTime);
        }
    }

    private void processFormerHighNetworkConsumingRun(final PipelineRun run,
                                                       final List<PipelineRun> runsToUpdateNotificationTime,
                                                       final List<PipelineRun> runsToUpdateTags) {
        run.setLastNetworkConsumptionNotificationTime(null);
        run.removeTag(NETWORK_CONSUMING_LEVEL_HIGH);
        run.removeTag(RunMonitorUtils.getTimestampTag(NETWORK_CONSUMING_LEVEL_HIGH, preferenceManager));
        runsToUpdateNotificationTime.add(run);
        runsToUpdateTags.add(run);
    }

    private boolean shouldPerformActionOnNetworkConsumingRun(final PipelineRun run, final int actionTimeout) {
        return actionTimeout > 0 && Objects.nonNull(run.getLastNetworkConsumptionNotificationTime()) &&
                run.getLastNetworkConsumptionNotificationTime()
                        .isBefore(DateUtils.nowUTC().minusMinutes(actionTimeout));
    }

    private void performActionOnNetworkConsumingRun(final PipelineRun run,
                                                     final NetworkConsumingRunAction action,
                                                     final double bandwidth,
                                                     final List<Pair<PipelineRun, Double>> runsToNotify,
                                                     final List<PipelineRun> runsToUpdateNotificationTime) {
        log.info(messageHelper.getMessage(MessageConstants.INFO_RUN_HIGH_NETWORK_CONSUMPTION_ACTION,
                run.getPodId(), bandwidth, action.name()));
        switch (action) {
            case LIMIT_BANDWIDTH:
//                    TODO
                break;
            case NOTIFY:
            default:
                performHighNetworkConsumingNotify(run, bandwidth, runsToNotify, runsToUpdateNotificationTime);
                break;
        }
    }

    private void performHighNetworkConsumingNotify(final PipelineRun run, final double networkBandwidthLevel,
                                                    final List<Pair<PipelineRun, Double>> pipelinesToNotify,
                                                    final List<PipelineRun> runsToUpdateNotificationTime) {
        run.setLastNetworkConsumptionNotificationTime(DateUtils.nowUTC());
        pipelinesToNotify.add(new ImmutablePair<>(run, networkBandwidthLevel));
        runsToUpdateNotificationTime.add(run);
    }
}
