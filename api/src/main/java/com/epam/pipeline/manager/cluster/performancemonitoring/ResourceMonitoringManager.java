/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.cluster.performancemonitoring;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.PostConstruct;

import com.epam.pipeline.entity.cluster.monitoring.ELKUsageMetric;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.math3.util.Precision;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dao.monitoring.MonitoringESDao;
import com.epam.pipeline.entity.cluster.InstanceType;
import com.epam.pipeline.entity.monitoring.IdleRunAction;
import com.epam.pipeline.entity.notification.NotificationSettings.NotificationType;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.cluster.InstanceOfferManager;
import com.epam.pipeline.manager.notification.NotificationManager;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.scheduling.AbstractSchedulingManager;
import io.reactivex.Observable;

/**
 *  A service component for monitoring resource usage.
 *  Polls cpu usage of running pipelines statistics from Kubernetes on a configured schedule.
 *
 *  Performs the following actions:
 *  If a pipeline's resource usage for a configured timeout is below a configured threshold,
 *  a notification will be sent. If resource usage is still low during a configured action timeout, one of configured
 *  actions will be taken: notify, force pause of force stop of a  run.
 */
@Service
@ConditionalOnProperty("monitoring.elasticsearch.url")
@Slf4j
public class ResourceMonitoringManager extends AbstractSchedulingManager {
    private static final int MILLIS = 1000;
    private static final double PERCENT = 100.0;
    private static final double ONE_THOUSANDTH = 0.001;

    private final PipelineRunManager pipelineRunManager;
    private final NotificationManager notificationManager;
    private final InstanceOfferManager instanceOfferManager;
    private final MonitoringESDao monitoringDao;
    private final MessageHelper messageHelper;

    @Autowired
    public ResourceMonitoringManager(PipelineRunManager pipelineRunManager,
                                     PreferenceManager preferenceManager,
                                     NotificationManager notificationManager,
                                     InstanceOfferManager instanceOfferManager,
                                     MonitoringESDao monitoringDao,
                                     TaskScheduler scheduler,
                                     MessageHelper messageHelper) {
        this.pipelineRunManager = pipelineRunManager;
        this.messageHelper = messageHelper;
        this.preferenceManager = preferenceManager;
        this.notificationManager = notificationManager;
        this.instanceOfferManager = instanceOfferManager;
        this.monitoringDao = monitoringDao;
        this.scheduler = scheduler;
    }

    private Map<String, InstanceType> instanceTypeMap = new HashMap<>();

    @PostConstruct
    public void init() {
        Observable<List<InstanceType>> instanceTypesObservable = instanceOfferManager.getAllInstanceTypesObservable();

        instanceTypesObservable.subscribe(instanceTypes -> instanceTypeMap = instanceTypes.stream()
                .collect(Collectors.toMap(InstanceType::getName, t -> t, (t1, t2) -> t1)));

        scheduleFixedDelaySecured(this::monitorResourceUsage, SystemPreferences.SYSTEM_RESOURCE_MONITORING_PERIOD,
                "Resource Usage Monitoring");
    }

    @Scheduled(cron = "0 0 0 ? * *")
    public void removeOldIndices() {
        monitoringDao.deleteIndices(preferenceManager.getPreference(
            SystemPreferences.SYSTEM_RESOURCE_MONITORING_STATS_RETENTION_PERIOD));
    }

    public void monitorResourceUsage() {
        List<PipelineRun> runs = pipelineRunManager.loadRunningPipelineRuns();

        processIdleRuns(runs);
        processOverloadedRuns(runs);
    }

    private void processOverloadedRuns(final List<PipelineRun> runs) {
        final Map<String, PipelineRun> running = runs.stream()
                .collect(Collectors.toMap(p -> p.getInstance().getNodeName(), r -> r));
        final int timeRange = preferenceManager.getPreference(SystemPreferences.SYSTEM_MONITORING_METRIC_TIME_RANGE);
        final Map<String, Double> thresholds = getThresholds();
        log.debug("Checking memory and disk stats for pipelines: " + String.join(", ", running.keySet()));

        final LocalDateTime now = DateUtils.nowUTC();
        final Map<String, Map<String, Double>> metrics = Stream.of(ELKUsageMetric.MEM, ELKUsageMetric.FS)
                .collect(Collectors.toMap(ELKUsageMetric::getName, metric ->
                        monitoringDao.loadMetrics(metric, running.keySet(),
                            now.minusMinutes(timeRange), now)));

        log.debug("memory and disk metrics received: " + metrics.entrySet().stream()
                .map(e -> e.getKey() + ": { " + e.getValue().entrySet().stream()
                        .map(metric -> metric.getKey() + ":" + metric.getValue())
                        .collect(Collectors.joining(", ")) + " }"
                )
                .collect(Collectors.joining("; ")));

        final List<Pair<PipelineRun, Map<String, Double>>> runsToNotify = running.entrySet()
                .stream()
                .map(nodeAndRun -> matchRunAndMetrics(metrics, nodeAndRun))
                .filter(pod -> isPodUnderPressure(pod.getValue(), thresholds))
                .collect(Collectors.toList());

        notificationManager.notifyHighResourceConsumingRuns(runsToNotify, NotificationType.HIGH_CONSUMED_RESOURCES);
    }

    private Pair<PipelineRun, Map<String, Double>> matchRunAndMetrics(final Map<String, Map<String, Double>> metrics,
                                                                      final Map.Entry<String, PipelineRun> podAndRun) {
        final Map<String, Double> podMetrics = metrics.entrySet()
                .stream()
                .collect(HashMap::new,
                    (m, e) -> m.put(e.getKey(), e.getValue().get(podAndRun.getKey())),
                        Map::putAll);

        return new ImmutablePair<>(podAndRun.getValue(), podMetrics);
    }

    private boolean isPodUnderPressure(final Map<String, Double> podMetrics, final Map<String, Double> thresholds) {
        return thresholds.entrySet()
                .stream()
                .anyMatch(
                        metricThreshold -> {
                            Double podValue = podMetrics.get(metricThreshold.getKey());
                            return podValue != null && !Double.isInfinite(podValue) &&
                                    Precision.compareTo(podValue, metricThreshold.getValue(), ONE_THOUSANDTH) > 0;
                        }
                );
    }

    private Map<String, Double> getThresholds() {
        final HashMap<String, Double> result = new HashMap<>();
        result.put(ELKUsageMetric.MEM.getName(),
                preferenceManager.getPreference(SystemPreferences.SYSTEM_MEMORY_THRESHOLD_PERCENT) / PERCENT);
        result.put(ELKUsageMetric.FS.getName(),
                preferenceManager.getPreference(SystemPreferences.SYSTEM_DISK_THRESHOLD_PERCENT) / PERCENT);
        return result;
    }

    private void processIdleRuns(final List<PipelineRun> runs) {
        final Map<String, PipelineRun> running = runs.stream()
                .collect(Collectors.toMap(PipelineRun::getPodId, r -> r));

        final int idleTimeout = preferenceManager.getPreference(SystemPreferences.SYSTEM_MAX_IDLE_TIMEOUT_MINUTES);

        final Map<String, PipelineRun> notProlongedRuns = running.entrySet().stream()
                .filter(e -> DateUtils.nowUTC().isAfter(e.getValue().getProlongedAtTime()
                        .plus(idleTimeout, ChronoUnit.MINUTES)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        log.debug("Checking cpu stats for pipelines: " + String.join(", ", notProlongedRuns.keySet()));

        final LocalDateTime now = DateUtils.nowUTC();
        final Map<String, Double> cpuMetrics = monitoringDao.loadMetrics(ELKUsageMetric.CPU,
                notProlongedRuns.keySet(), now.minusMinutes(idleTimeout), now);

        log.debug("CPU Metrics received: " + cpuMetrics.entrySet().stream().map(e -> e.getKey() + ":" + e.getValue())
            .collect(Collectors.joining(", ")));

        final double idleCpuLevel = preferenceManager.getPreference(
                SystemPreferences.SYSTEM_IDLE_CPU_THRESHOLD_PERCENT) / PERCENT;
        final int actionTimeout = preferenceManager.getPreference(
                SystemPreferences.SYSTEM_IDLE_ACTION_TIMEOUT_MINUTES);
        final IdleRunAction action = IdleRunAction.valueOf(preferenceManager
                .getPreference(SystemPreferences.SYSTEM_IDLE_ACTION));

        final List<PipelineRun> runsToUpdate = processRuns(notProlongedRuns, cpuMetrics,
                idleCpuLevel, actionTimeout, action);
        pipelineRunManager.updatePipelineRunsLastNotification(runsToUpdate);
    }

    private List<PipelineRun> processRuns(Map<String, PipelineRun> running, Map<String, Double> cpuMetrics,
                                          double idleCpuLevel, int actionTimeout, IdleRunAction action) {
        List<PipelineRun> runsToUpdate = new ArrayList<>(running.size());
        List<Pair<PipelineRun, Double>> runsToNotify = new ArrayList<>(running.size());

        for (Map.Entry<String, PipelineRun> entry : running.entrySet()) {
            PipelineRun run = entry.getValue();
            if (run.isNonPause()) {
                continue;
            }
            Double metric = cpuMetrics.get(entry.getKey());
            if (metric != null) {
                InstanceType type = instanceTypeMap.getOrDefault(run.getInstance().getNodeType(),
                        InstanceType.builder().vCPU(1).build());
                double cpuUsageRate = metric / MILLIS / type.getVCPU();
                if (Precision.compareTo(cpuUsageRate, idleCpuLevel, ONE_THOUSANDTH) < 0) {
                    processIdleRun(run, actionTimeout, action, runsToNotify, runsToUpdate, cpuUsageRate);
                } else if (run.getLastIdleNotificationTime() != null) { // No action is longer needed, clear timeout
                    run.setLastIdleNotificationTime(null);
                    runsToUpdate.add(run);
                }
            }
        }

        notificationManager.notifyIdleRuns(runsToNotify, NotificationType.IDLE_RUN);
        return runsToUpdate;
    }

    private void processIdleRun(PipelineRun run, int actionTimeout, IdleRunAction action,
                                List<Pair<PipelineRun, Double>> pipelinesToNotify, List<PipelineRun> runsToUpdate,
                                Double cpuUsageRate) {
        if (run.getLastIdleNotificationTime() == null) { // first notification - set notification time and notify
            run.setLastIdleNotificationTime(DateUtils.nowUTC());
            runsToUpdate.add(run);
            pipelinesToNotify.add(new ImmutablePair<>(run, cpuUsageRate));
            log.info(messageHelper.getMessage(MessageConstants.INFO_RUN_IDLE_NOTIFY, run.getPodId(), cpuUsageRate));
        } else { // run was already notified - we need to take some action
            performActionOnIdleRun(run, action, cpuUsageRate, actionTimeout, pipelinesToNotify, runsToUpdate);
        }
    }

    private void performActionOnIdleRun(PipelineRun run, IdleRunAction action, double cpuUsageRate, int actionTimeout,
                                        List<Pair<PipelineRun, Double>> pipelinesToNotify,
                                        List<PipelineRun> runsToUpdate) {
        if (run.getLastIdleNotificationTime().isBefore(DateUtils.nowUTC().minusMinutes(actionTimeout))) {
            log.info(messageHelper.getMessage(MessageConstants.INFO_RUN_IDLE_ACTION, run.getPodId(), cpuUsageRate,
                                                 action));
            switch (action) {
                case PAUSE:
                    if (run.getInstance().getSpot()) {
                        performNotify(run, cpuUsageRate, pipelinesToNotify);
                    } else {
                        performPause(run, cpuUsageRate);
                    }

                    break;
                case PAUSE_OR_STOP:
                    if (run.getInstance().getSpot()) {
                        performStop(run, cpuUsageRate);
                    } else {
                        performPause(run, cpuUsageRate);
                    }

                    break;
                case STOP:
                    performStop(run, cpuUsageRate);
                    break;
                default:
                    performNotify(run, cpuUsageRate, pipelinesToNotify);
            }

            runsToUpdate.add(run);
        }
    }

    private void performNotify(PipelineRun run, double cpuUsageRate,
                               List<Pair<PipelineRun, Double>> pipelinesToNotify) {
        run.setLastIdleNotificationTime(DateUtils.nowUTC());
        pipelinesToNotify.add(new ImmutablePair<>(run, cpuUsageRate));
    }

    private void performStop(PipelineRun run, double cpuUsageRate) {
        pipelineRunManager.stop(run.getId());
        notificationManager.notifyIdleRuns(Collections.singletonList(new ImmutablePair<>(run, cpuUsageRate)),
            NotificationType.IDLE_RUN_STOPPED);
    }

    private void performPause(PipelineRun run, double cpuUsageRate) {
        run.setLastIdleNotificationTime(null);
        pipelineRunManager.pauseRun(run.getId(), true);
        notificationManager.notifyIdleRuns(Collections.singletonList(new ImmutablePair<>(run, cpuUsageRate)),
            NotificationType.IDLE_RUN_PAUSED);
    }
}
