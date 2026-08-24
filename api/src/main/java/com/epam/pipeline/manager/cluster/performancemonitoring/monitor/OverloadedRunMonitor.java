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
import com.epam.pipeline.entity.notification.NotificationType;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.cluster.performancemonitoring.monitor.common.RunMonitorUtils;
import com.epam.pipeline.manager.notification.NotificationManager;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.math3.util.Precision;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@Slf4j
public class OverloadedRunMonitor implements RunMonitor {

    private static final double PERCENT = 100.0;
    private static final double ONE_THOUSANDTH = 0.001;
    private static final long ONE = 1L;
    private static final String UTILIZATION_LEVEL_HIGH = "PRESSURE";
    private static final String TRUE_VALUE_STRING = "true";

    private final PipelineRunManager pipelineRunManager;
    private final NotificationManager notificationManager;
    private final MonitoringESDao monitoringDao;
    private final MessageHelper messageHelper;
    private final PreferenceManager preferenceManager;

    @Autowired
    public OverloadedRunMonitor(final PipelineRunManager pipelineRunManager,
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
        final Map<String, PipelineRun> running = RunMonitorUtils.groupedByNode(runs, messageHelper);
        final int timeRange = preferenceManager.getPreference(
                SystemPreferences.SYSTEM_MONITORING_METRIC_TIME_RANGE);
        final Map<ELKUsageMetric, Double> thresholds = getThresholds();
        log.debug(messageHelper.getMessage(MessageConstants.DEBUG_RUN_METRICS_REQUEST,
                "MEMORY, DISK ", running.size(), String.join(", ", running.keySet())));

        final LocalDateTime now = DateUtils.nowUTC();
        final Map<ELKUsageMetric, Map<String, Double>> metrics = Stream.of(ELKUsageMetric.MEM, ELKUsageMetric.FS)
                .collect(Collectors.toMap(metric -> metric, metric ->
                        monitoringDao.loadMetrics(metric, running.keySet(),
                                now.minusMinutes(timeRange + ONE), now)));

        log.debug(messageHelper.getMessage(MessageConstants.DEBUG_MEMORY_METRICS, metrics.entrySet().stream()
                .map(e -> e.getKey().getName() + ": { " + e.getValue().entrySet().stream()
                        .map(metric -> metric.getKey() + ":" + metric.getValue())
                        .collect(Collectors.joining(", ")) + " }"
                )
                .collect(Collectors.joining("; "))));

        final List<Pair<PipelineRun, Map<ELKUsageMetric, Double>>> runsToNotify = running.entrySet()
                .stream()
                .map(nodeAndRun -> matchRunAndMetrics(metrics, nodeAndRun))
                .filter(pod -> isPodUnderPressure(pod.getValue(), thresholds))
                .collect(Collectors.toList());

        final List<PipelineRun> runsToUpdateTags = getRunsToUpdatePressuredTags(running, runsToNotify);
        notificationManager.notifyHighResourceConsumingRuns(runsToNotify, NotificationType.HIGH_CONSUMED_RESOURCES);
        pipelineRunManager.updateRunsTags(runsToUpdateTags);
    }

    private Map<ELKUsageMetric, Double> getThresholds() {
        final HashMap<ELKUsageMetric, Double> result = new HashMap<>();
        result.put(ELKUsageMetric.MEM,
                preferenceManager.getPreference(SystemPreferences.SYSTEM_MEMORY_THRESHOLD_PERCENT) / PERCENT);
        result.put(ELKUsageMetric.FS,
                preferenceManager.getPreference(SystemPreferences.SYSTEM_DISK_THRESHOLD_PERCENT) / PERCENT);
        return result;
    }

    private Pair<PipelineRun, Map<ELKUsageMetric, Double>> matchRunAndMetrics(
            final Map<ELKUsageMetric, Map<String, Double>> metrics,
            final Map.Entry<String, PipelineRun> podAndRun) {
        final Map<ELKUsageMetric, Double> podMetrics = metrics.entrySet()
                .stream()
                .collect(HashMap::new,
                    (m, e) -> m.put(e.getKey(), e.getValue().get(podAndRun.getKey())), Map::putAll);
        return new ImmutablePair<>(podAndRun.getValue(), podMetrics);
    }

    private boolean isPodUnderPressure(final Map<ELKUsageMetric, Double> podMetrics,
                                        final Map<ELKUsageMetric, Double> thresholds) {
        return thresholds.entrySet()
                .stream()
                .anyMatch(metricThreshold -> {
                    Double podValue = podMetrics.get(metricThreshold.getKey());
                    return podValue != null && !Double.isInfinite(podValue) &&
                            Precision.compareTo(podValue, metricThreshold.getValue(), ONE_THOUSANDTH) > 0;
                });
    }

    private List<PipelineRun> getRunsToUpdatePressuredTags(
            final Map<String, PipelineRun> running,
            final List<Pair<PipelineRun, Map<ELKUsageMetric, Double>>> runsToNotify) {
        final Set<Long> runsIdToNotify = runsToNotify
                .stream()
                .map(p -> p.getLeft().getId())
                .collect(Collectors.toSet());
        final Stream<PipelineRun> runsToAddTag = running.values()
                .stream()
                .filter(r -> runsIdToNotify.contains(r.getId()))
                .filter(r -> !r.hasTag(UTILIZATION_LEVEL_HIGH))
                .peek(r -> {
                    r.addTag(UTILIZATION_LEVEL_HIGH, TRUE_VALUE_STRING);
                    Optional.ofNullable(RunMonitorUtils.getTimestampTag(UTILIZATION_LEVEL_HIGH, preferenceManager))
                            .ifPresent(tag -> r.addTag(tag, DateUtils.nowUTCStr()));
                });
        final Stream<PipelineRun> runsToRemoveTag = running.values()
                .stream()
                .filter(r -> !runsIdToNotify.contains(r.getId()))
                .filter(r -> r.hasTag(UTILIZATION_LEVEL_HIGH))
                .peek(r -> {
                    r.removeTag(UTILIZATION_LEVEL_HIGH);
                    r.removeTag(RunMonitorUtils.getTimestampTag(UTILIZATION_LEVEL_HIGH, preferenceManager));
                });
        return Stream.concat(runsToAddTag, runsToRemoveTag).collect(Collectors.toList());
    }
}
