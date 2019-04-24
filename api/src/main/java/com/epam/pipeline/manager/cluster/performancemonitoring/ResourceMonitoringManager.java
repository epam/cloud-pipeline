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
import com.epam.pipeline.manager.notification.NotificationSettingsManager;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.math3.util.Precision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.scheduling.AbstractSchedulingManager;
import io.reactivex.Observable;

import static com.epam.pipeline.manager.preference.SystemPreferences.*;

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
public class ResourceMonitoringManager extends AbstractSchedulingManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ResourceMonitoringManager.class);

    private static final int MILLIS = 1000;
    private static final double PERCENT = 100.0;
    private static final double ONE_THOUSANDTH = 0.001;

    private PipelineRunManager pipelineRunManager;
    private NotificationManager notificationManager;
    private InstanceOfferManager instanceOfferManager;
    private MonitoringESDao monitoringDao;
    private MessageHelper messageHelper;
    private NotificationSettingsManager notificationSettingsManager;
    private Map<String, InstanceType> instanceTypeMap = new HashMap<>();

    @Autowired
    public ResourceMonitoringManager(PipelineRunManager pipelineRunManager,
                                     PreferenceManager preferenceManager,
                                     NotificationManager notificationManager,
                                     InstanceOfferManager instanceOfferManager,
                                     MonitoringESDao monitoringDao,
                                     TaskScheduler scheduler,
                                     NotificationSettingsManager notificationSettingsManager,
                                     MessageHelper messageHelper) {
        this.pipelineRunManager = pipelineRunManager;
        this.messageHelper = messageHelper;
        this.preferenceManager = preferenceManager;
        this.notificationManager = notificationManager;
        this.instanceOfferManager = instanceOfferManager;
        this.monitoringDao = monitoringDao;
        this.scheduler = scheduler;
        this.notificationSettingsManager = notificationSettingsManager;
    }

    @PostConstruct
    public void init() {
        Observable<List<InstanceType>> instanceTypesObservable = instanceOfferManager.getAllInstanceTypesObservable();

        instanceTypesObservable.subscribe(instanceTypes -> instanceTypeMap = instanceTypes.stream()
                .collect(Collectors.toMap(InstanceType::getName, t -> t, (t1, t2) -> t1)));

        scheduleFixedDelay(this::monitorResourceUsage, SystemPreferences.SYSTEM_RESOURCE_MONITORING_PERIOD,
                           "Resource Usage Monitoring");
    }

    @Scheduled(cron = "0 0 0 ? * *")
    public void removeOldIndices() {
        monitoringDao.deleteIndices(preferenceManager.getPreference(
            SystemPreferences.SYSTEM_RESOURCE_MONITORING_STATS_RETENTION_PERIOD));
    }

    public void monitorResourceUsage() {
        Map<String, PipelineRun> running = pipelineRunManager.loadRunningPipelineRuns().stream()
                .collect(Collectors.toMap(PipelineRun::getPodId, r -> r));

        processIdleRuns(running);
        processOverloadedRuns(running);
    }

    private void processOverloadedRuns(Map<String, PipelineRun> running) {
        final List<Pair<PipelineRun, Map<String, Double>>> runsToNotify = new ArrayList<>();
        Long timeout = notificationSettingsManager.load(NotificationType.HIGH_CONSUMED_RESOURCES).getResendDelay();
        final Map<String, Double> thresholds = getThresholds();
        LOGGER.debug("Checking memory and disk stats for pipelines: " + String.join(", ", running.keySet()));

        LocalDateTime now = DateUtils.nowUTC();
        Map<String, Map<String, Double>> metrics = Stream.of(ELKUsageMetric.MEM, ELKUsageMetric.FS)
                .collect(Collectors.toMap(
                        ELKUsageMetric::getName,
                        metric-> monitoringDao.loadUsageRateMetrics(
                            metric, running.keySet(), now.minusMinutes(timeout), now))
                );

        running.forEach((pod, run) -> {
            Map<String, Double> podMetrics = metrics.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, metric -> metric.getValue().get(pod)));

            if (isPodUnderPressure(podMetrics, thresholds)) {
                runsToNotify.add(new ImmutablePair<>(run, podMetrics));
            }
        });

        notificationManager.notifyHighResourceConsumingRuns(runsToNotify, NotificationType.HIGH_CONSUMED_RESOURCES);
    }

    private boolean isPodUnderPressure(Map<String, Double> podMetrics,
                                       Map<String, Double> thresholds) {
        return thresholds.entrySet()
                .stream()
                .anyMatch(
                        metricThreshold -> Precision.compareTo(
                                podMetrics.get(metricThreshold.getKey()),
                                metricThreshold.getValue(), ONE_THOUSANDTH
                        ) > 0
                );
    }

    private Map<String, Double> getThresholds() {
        HashMap<String, Double> result = new HashMap<>();
        result.put(ELKUsageMetric.MEM.getName(),
                preferenceManager.getPreference(SYSTEM_MEMORY_THRESHOLD_PERCENT) / PERCENT);
        result.put(ELKUsageMetric.FS.getName(),
                preferenceManager.getPreference(SYSTEM_DISK_THRESHOLD_PERCENT) / PERCENT);
        return result;
    }

    private void processIdleRuns(Map<String, PipelineRun> running) {
        int idleTimeout = preferenceManager.getPreference(SYSTEM_MAX_IDLE_TIMEOUT_MINUTES);

        Map<String, PipelineRun> notProlongedRuns = running.entrySet().stream()
                .filter(e -> DateUtils.nowUTC().isAfter(e.getValue().getProlongedAtTime()
                        .plus(idleTimeout, ChronoUnit.MINUTES)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        LOGGER.debug("Checking cpu stats for pipelines: " + String.join(", ", notProlongedRuns.keySet()));

        LocalDateTime now = DateUtils.nowUTC();
        Map<String, Double> cpuMetrics = monitoringDao.loadUsageRateMetrics(ELKUsageMetric.CPU,
                notProlongedRuns.keySet(), now.minusMinutes(idleTimeout), now);

        LOGGER.debug("CPU Metrics received: " + cpuMetrics.entrySet().stream().map(e -> e.getKey() + ":" + e.getValue())
            .collect(Collectors.joining(", ")));

        double idleCpuLevel = preferenceManager.getPreference(SYSTEM_IDLE_CPU_THRESHOLD_PERCENT) / PERCENT;
        int actionTimeout = preferenceManager.getPreference(SYSTEM_IDLE_ACTION_TIMEOUT_MINUTES);
        IdleRunAction action = IdleRunAction.valueOf(
            preferenceManager.getPreference(SystemPreferences.SYSTEM_IDLE_ACTION));

        List<PipelineRun> runsToUpdate = processRuns(notProlongedRuns, cpuMetrics, idleCpuLevel, actionTimeout, action);
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
            LOGGER.info(messageHelper.getMessage(MessageConstants.INFO_RUN_IDLE_NOTIFY, run.getPodId(), cpuUsageRate));
        } else { // run was already notified - we need to take some action
            performActionOnIdleRun(run, action, cpuUsageRate, actionTimeout, pipelinesToNotify, runsToUpdate);
        }
    }

    private void performActionOnIdleRun(PipelineRun run, IdleRunAction action, double cpuUsageRate, int actionTimeout,
                                        List<Pair<PipelineRun, Double>> pipelinesToNotify,
                                        List<PipelineRun> runsToUpdate) {
        if (run.getLastIdleNotificationTime().isBefore(DateUtils.nowUTC().minusMinutes(actionTimeout))) {
            LOGGER.info(messageHelper.getMessage(MessageConstants.INFO_RUN_IDLE_ACTION, run.getPodId(), cpuUsageRate,
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
