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

package com.epam.pipeline.manager.cluster.performancemonitoring;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.PostConstruct;

import com.epam.pipeline.entity.cluster.monitoring.ELKUsageMetric;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import net.javacrumbs.shedlock.core.SchedulerLock;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.math3.util.Precision;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
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
 * A service component for monitoring resource usage.
 * Polls cpu usage of running pipelines statistics from Kubernetes on a configured schedule.
 * <p>
 * Performs the following actions:
 * If a pipeline's resource usage for a configured timeout is below a configured threshold,
 * a notification will be sent. If resource usage is still low during a configured action timeout, one of configured
 * actions will be taken: notify, force pause of force stop of a  run.
 */
@Service
@ConditionalOnProperty("monitoring.elasticsearch.url")
@Slf4j
public class ResourceMonitoringManager extends AbstractSchedulingManager {

    public static final String UTILIZATION_LEVEL_LOW = "IDLE";
    public static final String UTILIZATION_LEVEL_HIGH = "PRESSURE";
    public static final String TRUE_VALUE_STRING = "true";

    private final InstanceOfferManager instanceOfferManager;
    private final ResourceMonitoringManagerCore core;

    @Autowired
    public ResourceMonitoringManager(final InstanceOfferManager instanceOfferManager,
                                     final ResourceMonitoringManagerCore core) {
        this.instanceOfferManager = instanceOfferManager;
        this.core = core;
    }

    @PostConstruct
    public void init() {
        Observable<List<InstanceType>> instanceTypesObservable = instanceOfferManager.getAllInstanceTypesObservable();
        instanceTypesObservable
                .subscribe(instanceTypes -> core.updateInstanceMap(
                        instanceTypes.stream().collect(
                                Collectors.toMap(InstanceType::getName, t -> t, (t1, t2) -> t1))));
        scheduleFixedDelaySecured(core::monitorResourceUsage, SystemPreferences.SYSTEM_RESOURCE_MONITORING_PERIOD,
                "Resource Usage Monitoring");
    }

    public void monitorResourceUsage() {
        core.monitorResourceUsage();
    }

    @Component
    static class ResourceMonitoringManagerCore {

        private static final int MILLIS = 1000;
        private static final double PERCENT = 100.0;
        private static final double ONE_THOUSANDTH = 0.001;
        private static final long ONE = 1L;

        private final PipelineRunManager pipelineRunManager;
        private final NotificationManager notificationManager;
        private final MonitoringESDao monitoringDao;
        private final MessageHelper messageHelper;
        private final PreferenceManager preferenceManager;
        private Map<String, InstanceType> instanceTypeMap = new HashMap<>();

        @Autowired
        ResourceMonitoringManagerCore(final PipelineRunManager pipelineRunManager,
                                      final NotificationManager notificationManager,
                                      final MonitoringESDao monitoringDao,
                                      final MessageHelper messageHelper,
                                      final PreferenceManager preferenceManager) {
            this.pipelineRunManager = pipelineRunManager;
            this.messageHelper = messageHelper;
            this.notificationManager = notificationManager;
            this.monitoringDao = monitoringDao;
            this.preferenceManager = preferenceManager;
        }

        @Scheduled(cron = "0 0 0 ? * *")
        @SchedulerLock(name = "ResourceMonitoringManager_removeOldIndices", lockAtMostForString = "PT1H")
        public void removeOldIndices() {
            monitoringDao.deleteIndices(preferenceManager.getPreference(
                    SystemPreferences.SYSTEM_RESOURCE_MONITORING_STATS_RETENTION_PERIOD));
        }

        @SchedulerLock(name = "ResourceMonitoringManager_monitorResourceUsage", lockAtMostForString = "PT10M")
        public void monitorResourceUsage() {
            List<PipelineRun> runs = pipelineRunManager.loadRunningPipelineRuns();
            processIdleRuns(runs);
            processOverloadedRuns(runs);
            processPausingResumingRuns();
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

        private void processOverloadedRuns(final List<PipelineRun> runs) {
            final Map<String, PipelineRun> running = groupedByNode(runs);
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

        private Map<String, PipelineRun> groupedByNode(final List<PipelineRun> runs) {
            return runs.stream()
                    .filter(r -> {
                        final boolean hasNodeName = Objects.nonNull(r.getInstance())
                                && Objects.nonNull(r.getInstance().getNodeName());
                        if (!hasNodeName) {
                            log.debug(messageHelper.getMessage(
                                    MessageConstants.DEBUG_RUN_HAS_NOT_NODE_NAME, r.getId()));
                        }
                        return hasNodeName;
                    })
                    .collect(Collectors.toMap(r -> r.getInstance().getNodeName(), r -> r));
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
                    .peek(r -> r.addTag(UTILIZATION_LEVEL_HIGH, TRUE_VALUE_STRING));
            final Stream<PipelineRun> runsToRemoveTag = running.values()
                    .stream()
                    .filter(r -> !runsIdToNotify.contains(r.getId()))
                    .filter(r -> r.hasTag(UTILIZATION_LEVEL_HIGH))
                    .peek(r -> r.removeTag(UTILIZATION_LEVEL_HIGH));
            return Stream.concat(runsToAddTag, runsToRemoveTag).collect(Collectors.toList());
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
                    .anyMatch(
                            metricThreshold -> {
                                Double podValue = podMetrics.get(metricThreshold.getKey());
                                return podValue != null && !Double.isInfinite(podValue) &&
                                        Precision.compareTo(podValue, metricThreshold.getValue(), ONE_THOUSANDTH) > 0;
                            }
                    );
        }

        private Map<ELKUsageMetric, Double> getThresholds() {
            final HashMap<ELKUsageMetric, Double> result = new HashMap<>();
            result.put(ELKUsageMetric.MEM,
                    preferenceManager.getPreference(SystemPreferences.SYSTEM_MEMORY_THRESHOLD_PERCENT) / PERCENT);
            result.put(ELKUsageMetric.FS,
                    preferenceManager.getPreference(SystemPreferences.SYSTEM_DISK_THRESHOLD_PERCENT) / PERCENT);
            return result;
        }

        private void processIdleRuns(final List<PipelineRun> runs) {
            final Map<String, PipelineRun> running = groupedByNode(runs);

            final int idleTimeout = preferenceManager.getPreference(SystemPreferences.SYSTEM_MAX_IDLE_TIMEOUT_MINUTES);

            final Map<String, PipelineRun> notProlongedRuns = running.entrySet().stream()
                    .filter(e -> Optional.ofNullable(e.getValue().getProlongedAtTime())
                            .map(timestamp -> DateUtils.nowUTC()
                                    .isAfter(timestamp.plus(idleTimeout, ChronoUnit.MINUTES)))
                            .orElse(Boolean.FALSE))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            log.debug(messageHelper.getMessage(MessageConstants.DEBUG_RUN_METRICS_REQUEST,
                    "CPU", notProlongedRuns.size(), String.join(", ", notProlongedRuns.keySet())));

            final LocalDateTime now = DateUtils.nowUTC();
            final Map<String, Double> cpuMetrics = monitoringDao.loadMetrics(ELKUsageMetric.CPU,
                    notProlongedRuns.keySet(), now.minusMinutes(idleTimeout + ONE), now);
            log.debug(messageHelper.getMessage(MessageConstants.DEBUG_CPU_RUN_METRICS_RECEIVED,
                    cpuMetrics.entrySet().stream().map(e -> e.getKey() + ":" + e.getValue())
                            .collect(Collectors.joining(", ")))
            );

            final double idleCpuLevel = preferenceManager.getPreference(
                    SystemPreferences.SYSTEM_IDLE_CPU_THRESHOLD_PERCENT) / PERCENT;
            final int actionTimeout = preferenceManager.getPreference(
                    SystemPreferences.SYSTEM_IDLE_ACTION_TIMEOUT_MINUTES);
            final IdleRunAction action = IdleRunAction.valueOf(preferenceManager
                    .getPreference(SystemPreferences.SYSTEM_IDLE_ACTION));

            processRuns(notProlongedRuns, cpuMetrics, idleCpuLevel, actionTimeout, action);
        }

        private void processRuns(Map<String, PipelineRun> running, Map<String, Double> cpuMetrics,
                                 double idleCpuLevel, int actionTimeout, IdleRunAction action) {
            List<PipelineRun> runsToUpdateNotificationTime = new ArrayList<>(running.size());
            List<Pair<PipelineRun, Double>> runsToNotify = new ArrayList<>(running.size());
            final List<PipelineRun> runsToUpdateTags = new ArrayList<>(running.size());
            for (Map.Entry<String, PipelineRun> entry : running.entrySet()) {
                PipelineRun run = entry.getValue();
                Double metric = cpuMetrics.get(entry.getKey());
                if (metric != null) {
                    InstanceType type = instanceTypeMap.getOrDefault(run.getInstance().getNodeType(),
                            InstanceType.builder().vCPU(1).build());
                    double cpuUsageRate = metric / MILLIS / type.getVCPU();
                    if (Precision.compareTo(cpuUsageRate, idleCpuLevel, ONE_THOUSANDTH) < 0) {
                        processIdleRun(run, actionTimeout, action, runsToNotify,
                                runsToUpdateNotificationTime, cpuUsageRate, runsToUpdateTags);
                    } else if (run.getLastIdleNotificationTime() != null) { // No action is longer needed, clear timeout
                        log.debug(messageHelper.getMessage(MessageConstants.DEBUG_RUN_NOT_IDLED,
                                run.getPodId(), cpuUsageRate));
                        processFormerIdleRun(run, runsToUpdateNotificationTime, runsToUpdateTags);
                    }
                }
            }
            notificationManager.notifyIdleRuns(runsToNotify, NotificationType.IDLE_RUN);
            pipelineRunManager.updatePipelineRunsLastNotification(runsToUpdateNotificationTime);
            pipelineRunManager.updateRunsTags(runsToUpdateTags);
        }

        private void processIdleRun(PipelineRun run, int actionTimeout, IdleRunAction action,
                                    List<Pair<PipelineRun, Double>> pipelinesToNotify,
                                    List<PipelineRun> runsToUpdateNotificationTime, Double cpuUsageRate,
                                    List<PipelineRun> runsToUpdateTags) {
            if (run.getLastIdleNotificationTime() == null) { // first notification - set notification time and notify
                run.setLastIdleNotificationTime(DateUtils.nowUTC());
                run.addTag(UTILIZATION_LEVEL_LOW, TRUE_VALUE_STRING);
                runsToUpdateNotificationTime.add(run);
                runsToUpdateTags.add(run);
                pipelinesToNotify.add(new ImmutablePair<>(run, cpuUsageRate));
                log.info(messageHelper.getMessage(MessageConstants.INFO_RUN_IDLE_NOTIFY, run.getPodId(), cpuUsageRate));
            } else { // run was already notified - we need to take some action
                performActionOnIdleRun(run, action, cpuUsageRate,
                        actionTimeout, pipelinesToNotify, runsToUpdateNotificationTime);
            }
        }

        private void processFormerIdleRun(final PipelineRun run, final List<PipelineRun> runsToUpdateNotificationTime,
                                          final List<PipelineRun> runsToUpdateTags) {
            run.setLastIdleNotificationTime(null);
            run.removeTag(UTILIZATION_LEVEL_LOW);
            runsToUpdateNotificationTime.add(run);
            runsToUpdateTags.add(run);
        }

        private void performActionOnIdleRun(PipelineRun run, IdleRunAction action,
                                            double cpuUsageRate, int actionTimeout,
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
            if (run.isNonPause() || run.isClusterRun()) {
                log.debug(messageHelper.getMessage(MessageConstants.DEBUG_RUN_IDLE_SKIP_CHECK, run.getPodId()));
                return;
            }
            pipelineRunManager.stop(run.getId());
            notificationManager.notifyIdleRuns(Collections.singletonList(new ImmutablePair<>(run, cpuUsageRate)),
                    NotificationType.IDLE_RUN_STOPPED);
        }

        private void performPause(PipelineRun run, double cpuUsageRate) {
            if (run.isNonPause() || run.isClusterRun()) {
                log.debug(messageHelper.getMessage(MessageConstants.DEBUG_RUN_IDLE_SKIP_CHECK, run.getPodId()));
                return;
            }
            run.setLastIdleNotificationTime(null);
            pipelineRunManager.pauseRun(run.getId(), true);
            notificationManager.notifyIdleRuns(Collections.singletonList(new ImmutablePair<>(run, cpuUsageRate)),
                    NotificationType.IDLE_RUN_PAUSED);
        }

        public void updateInstanceMap(Map<String, InstanceType> types) {
            instanceTypeMap.clear();
            instanceTypeMap.putAll(types);
        }
    }
}
