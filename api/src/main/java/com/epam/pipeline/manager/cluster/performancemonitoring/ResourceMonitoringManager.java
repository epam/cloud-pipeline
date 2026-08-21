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

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dao.monitoring.MonitoringESDao;
import com.epam.pipeline.entity.cluster.InstanceType;
import com.epam.pipeline.entity.cluster.monitoring.ELKUsageMetric;
import com.epam.pipeline.entity.monitoring.*;
import com.epam.pipeline.entity.notification.NotificationType;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.pipeline.StopServerlessRun;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.pipeline.run.RunStatus;
import com.epam.pipeline.entity.run.PipelineRunEmergencyTermAction;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.cluster.InstanceOfferManager;
import com.epam.pipeline.manager.cluster.NodesManager;
import com.epam.pipeline.manager.notification.NotificationManager;
import com.epam.pipeline.manager.pipeline.PipelineRunDockerOperationManager;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.pipeline.RunStatusManager;
import com.epam.pipeline.manager.pipeline.StopServerlessRunManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.scheduling.AbstractSchedulingManager;
import javax.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.SchedulerLock;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.math3.util.Precision;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.epam.pipeline.manager.preference.SystemPreferences.SYSTEM_IDLE_MONITORING_CONFIG;

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

    public static final String NETWORK_CONSUMING_LEVEL_HIGH = "NETWORK_PRESSURE";
    public static final String UTILIZATION_LEVEL_HIGH = "PRESSURE";
    public static final String TRUE_VALUE_STRING = "true";

    public static final List<String> IDLE_TAGS = Arrays.stream(IdleMonitoringType.values())
            .map(IdleMonitoringType::getTag)
            .collect(Collectors.toList());

    private final ResourceMonitoringManagerCore core;

    @Autowired
    public ResourceMonitoringManager(final ResourceMonitoringManagerCore core) {
        this.core = core;
    }

    @PostConstruct
    public void init() {
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
        private static final String WORK_FINISHED_TAG = "WORK_FINISHED";
        public static final String CP_TERMINATE_RUN_ON_CLEANUP_TIMEOUT_MIN_PARAM =
                "CP_TERMINATE_RUN_ON_CLEANUP_TIMEOUT_MIN";
        public static final double ZERO_USAGE_RATE = 0.0;

        private final PipelineRunManager pipelineRunManager;
        private final RunStatusManager runStatusManager;
        private final PipelineRunDockerOperationManager pipelineRunDockerOperationManager;
        private final NotificationManager notificationManager;
        private final MonitoringESDao monitoringDao;
        private final MessageHelper messageHelper;
        private final PreferenceManager preferenceManager;
        private final StopServerlessRunManager stopServerlessRunManager;
        private final InstanceOfferManager instanceOfferManager;
        private final NodesManager nodesManager;

        private volatile Map<String, InstanceType> instanceTypes = Collections.emptyMap();

        @Autowired
        ResourceMonitoringManagerCore(final PipelineRunManager pipelineRunManager,
                                      final PipelineRunDockerOperationManager pipelineRunDockerOperationManager,
                                      final NotificationManager notificationManager,
                                      final MonitoringESDao monitoringDao,
                                      final MessageHelper messageHelper,
                                      final PreferenceManager preferenceManager,
                                      final StopServerlessRunManager stopServerlessRunManager,
                                      final InstanceOfferManager instanceOfferManager,
                                      final RunStatusManager runStatusManager,
                                      final NodesManager nodesManager) {
            this.pipelineRunManager = pipelineRunManager;
            this.pipelineRunDockerOperationManager = pipelineRunDockerOperationManager;
            this.messageHelper = messageHelper;
            this.notificationManager = notificationManager;
            this.monitoringDao = monitoringDao;
            this.preferenceManager = preferenceManager;
            this.stopServerlessRunManager = stopServerlessRunManager;
            this.instanceOfferManager = instanceOfferManager;
            this.runStatusManager = runStatusManager;
            this.nodesManager = nodesManager;
        }

        @PostConstruct
        public void initInstanceTypes() {
            refreshInstanceTypes();
        }

        @Scheduled(cron = "0 0 0 ? * *")
        public void refreshInstanceTypes() {
            instanceTypes = computeInstanceTypes();
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
            processCPUIdleRuns(runs);
            processGPUIdleRuns(runs);
            processAbsoluteIdleRuns(runs);
            processHighNetworkConsumingRuns(runs);
            processOverloadedRuns(runs);
            processStuckRuns(runs);
            processPausingResumingRuns();
            processServerlessRuns();
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

        private void processStuckRuns(final List<PipelineRun> runs) {
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
                    .peek(r -> {
                        r.addTag(UTILIZATION_LEVEL_HIGH, TRUE_VALUE_STRING);
                        Optional.ofNullable(getTimestampTag(UTILIZATION_LEVEL_HIGH))
                                .ifPresent(tag -> r.addTag(tag, DateUtils.nowUTCStr()));
                    });
            final Stream<PipelineRun> runsToRemoveTag = running.values()
                    .stream()
                    .filter(r -> !runsIdToNotify.contains(r.getId()))
                    .filter(r -> r.hasTag(UTILIZATION_LEVEL_HIGH))
                    .peek(r -> {
                        r.removeTag(UTILIZATION_LEVEL_HIGH);
                        r.removeTag(getTimestampTag(UTILIZATION_LEVEL_HIGH));
                    });
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

        private void processCPUIdleRuns(final List<PipelineRun> runs) {
            final Map<String, PipelineRun> running = groupedByNode(runs);
            final IdleMonitoringConfig conf = findEnabledIdleConfig(IdleMonitoringType.CPU);
            if (!isIdleConfigReadyForProcessing(conf, IdleMonitoringType.CPU)) {
                return;
            }

            final double idleLevel = conf.getThresholdPercent() / PERCENT;
            final int idleGracePeriod = conf.getGracePeriodMinutes();
            final Map<String, PipelineRun> notProlongedRuns = filterNotProlongedRuns(running, idleGracePeriod);
            final Map<String, Double> metrics = loadIdleMetrics(
                    ELKUsageMetric.CPU, notProlongedRuns.keySet(), idleGracePeriod
            );
            final List<Pair<PipelineRun, Double>> runsToNotify = new ArrayList<>(notProlongedRuns.size());
            final List<PipelineRun> runsToUpdateTags = new ArrayList<>(notProlongedRuns.size());
            for (Map.Entry<String, PipelineRun> entry : notProlongedRuns.entrySet()) {
                final PipelineRun run = entry.getValue();
                final Double metric = metrics.get(entry.getKey());
                if (Objects.isNull(metric)) {
                    continue;
                }
                final double usageRate = metric / MILLIS / instanceVCPU(run);
                if (Precision.compareTo(usageRate, idleLevel, ONE_THOUSANDTH) < 0) {
                    processIdleRun(run, IdleMonitoringType.CPU, usageRate, conf.getActionTimeoutMinutes(),
                            conf.getAction(), runsToNotify, runsToUpdateTags);
                } else if (isIdleTagged(run, IdleMonitoringType.CPU)) {
                    log.debug(messageHelper.getMessage(MessageConstants.DEBUG_RUN_NOT_IDLED,
                            run.getPodId(), IdleMonitoringType.CPU.name(), usageRate));
                    processFormerIdleRun(run, runsToUpdateTags, IdleMonitoringType.CPU);
                }
            }
            notificationManager.notifyIdleRuns(runsToNotify, NotificationType.IDLE_CPU_RUN, conf.getThresholdPercent());
            pipelineRunManager.updateRunsTags(runsToUpdateTags);
        }

        private void processGPUIdleRuns(final List<PipelineRun> runs) {
            final Map<String, PipelineRun> running = groupedByNode(filterGpuRuns(runs));
            final IdleMonitoringConfig conf = findEnabledIdleConfig(IdleMonitoringType.GPU);
            if (!isIdleConfigReadyForProcessing(conf, IdleMonitoringType.GPU)) {
                return;
            }

            final int idleGracePeriod = conf.getGracePeriodMinutes();
            final Map<String, PipelineRun> notProlongedRuns = filterNotProlongedRuns(running, idleGracePeriod);
            final Map<String, Double> activeGPUsByRuns = loadIdleMetrics(
                    ELKUsageMetric.GPU_AGGS, notProlongedRuns.keySet(), idleGracePeriod
            );
            final List<Pair<PipelineRun, Double>> runsToNotify = new ArrayList<>(notProlongedRuns.size());
            final List<PipelineRun> runsToUpdateTags = new ArrayList<>(notProlongedRuns.size());
            for (Map.Entry<String, PipelineRun> entry : notProlongedRuns.entrySet()) {
                final PipelineRun run = entry.getValue();
                final Double activeGPUs = activeGPUsByRuns.get(entry.getKey());
                if (Objects.isNull(activeGPUs)) {
                    continue;
                }
                if (activeGPUs <= ZERO_USAGE_RATE) {
                    processIdleRun(run, IdleMonitoringType.GPU, activeGPUs, conf.getActionTimeoutMinutes(),
                            conf.getAction(), runsToNotify, runsToUpdateTags);
                } else if (isIdleTagged(run, IdleMonitoringType.GPU)) {
                    log.debug(messageHelper.getMessage(MessageConstants.DEBUG_RUN_NOT_IDLED,
                            run.getPodId(), IdleMonitoringType.GPU.name(), activeGPUs));
                    processFormerIdleRun(run, runsToUpdateTags, IdleMonitoringType.GPU);
                }
            }
            notificationManager.notifyIdleRuns(runsToNotify, NotificationType.IDLE_GPU_RUN, ZERO_USAGE_RATE);
            pipelineRunManager.updateRunsTags(runsToUpdateTags);
        }

        private boolean isIdleConfigReadyForProcessing(final IdleMonitoringConfig conf,
                                                        final IdleMonitoringType type) {
            if (conf == null) {
                log.debug("{} idle monitoring config is not configured or disabled, skipping idle check.",
                        type.name());
                return false;
            }
            if (Objects.isNull(conf.getGracePeriodMinutes()) || Objects.isNull(conf.getActionTimeoutMinutes())) {
                log.warn("{} idle monitoring config misses grace period or action timeout, skipping idle check.",
                        type.name());
                return false;
            }
            return true;
        }

        private Map<String, PipelineRun> filterNotProlongedRuns(final Map<String, PipelineRun> running,
                                                                  final int idleGracePeriod) {
            return running.entrySet().stream()
                    .filter(e -> Optional.ofNullable(e.getValue().getProlongedAtTime())
                            .map(timestamp -> DateUtils.nowUTC().isAfter(timestamp.plusMinutes(idleGracePeriod)))
                            .orElse(Boolean.FALSE))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        }

        private Map<String, Double> loadIdleMetrics(final ELKUsageMetric usageMetric, final Set<String> nodes,
                                                     final int idleGracePeriod) {
            log.debug(messageHelper.getMessage(MessageConstants.DEBUG_RUN_METRICS_REQUEST, usageMetric.getName(),
                    nodes.size(), String.join(", ", nodes)));
            final LocalDateTime now = DateUtils.nowUTC();
            final Map<String, Double> metrics = monitoringDao.loadMetrics(usageMetric, nodes,
                    now.minusMinutes(idleGracePeriod + ONE), now);
            log.debug(messageHelper.getMessage(MessageConstants.DEBUG_RUN_METRICS_RECEIVED,
                    usageMetric.getName(),
                    metrics.entrySet().stream().map(e -> e.getKey() + ":" + e.getValue())
                            .collect(Collectors.joining(", "))));
            return metrics;
        }

        private IdleMonitoringConfig findEnabledIdleConfig(
                final IdleMonitoringType monitoringType) {
            return ListUtils.emptyIfNull(preferenceManager.getPreference(SYSTEM_IDLE_MONITORING_CONFIG))
                    .stream()
                    .filter(IdleMonitoringConfig::isEnabled)
                    .filter(config -> config.getType() == monitoringType)
                    .findFirst()
                    .orElse(null);
        }

        private List<PipelineRun> filterGpuRuns(final List<PipelineRun> runs) {
            return ListUtils.emptyIfNull(runs).stream()
                    .filter(this::hasGpu)
                    .collect(Collectors.toList());
        }

        private void processAbsoluteIdleRuns(final List<PipelineRun> runs) {
            final IdleMonitoringConfig absoluteConf = findEnabledIdleConfig(IdleMonitoringType.ABSOLUTE);
            if (absoluteConf == null) {
                log.debug("ABSOLUTE idle monitoring config is not configured or disabled, skipping idle check.");
                return;
            }
            if (Objects.isNull(absoluteConf.getActionTimeoutMinutes())) {
                log.warn("ABSOLUTE idle monitoring config misses action timeout, skipping idle check.");
                return;
            }

            final int actionTimeout = absoluteConf.getActionTimeoutMinutes();
            final IdleRunAction action = absoluteConf.getAction();

            final List<Pair<PipelineRun, Double>> runsToNotify = new ArrayList<>(runs.size());
            final List<PipelineRun> runsToUpdateTags = new ArrayList<>(runs.size());
            for (final PipelineRun run : ListUtils.emptyIfNull(runs)) {
                if (isAbsolutelyIdle(run)) {
                    processIdleRun(
                            run, IdleMonitoringType.ABSOLUTE, ZERO_USAGE_RATE, actionTimeout, action,
                            runsToNotify, runsToUpdateTags
                    );
                } else if (isIdleTagged(run, IdleMonitoringType.ABSOLUTE)) {
                    processFormerIdleRun(run, runsToUpdateTags, IdleMonitoringType.ABSOLUTE);
                }
            }
            notificationManager.notifyIdleRuns(runsToNotify, NotificationType.IDLE_RUN, ZERO_USAGE_RATE);
            pipelineRunManager.updateRunsTags(runsToUpdateTags);
        }

        private boolean isAbsolutelyIdle(final PipelineRun run) {
            if (!isIdleTagged(run, IdleMonitoringType.CPU)) {
                return false;
            }
            return !hasGpu(run) || isIdleTagged(run, IdleMonitoringType.GPU);
        }

        private boolean hasGpu(final PipelineRun run) {
            return Optional.ofNullable(run.getInstance())
                    .map(RunInstance::getNodeType)
                    .map(instanceTypes::get)
                    .map(type -> type.getGpu() > 0)
                    .orElse(false);
        }

        private Map<String, InstanceType> computeInstanceTypes() {
            return ListUtils.emptyIfNull(instanceOfferManager.getAllInstanceTypes()).stream()
                    .collect(Collectors.toMap(InstanceType::getName, Function.identity(), (t1, t2) -> t1));
        }

        private int instanceVCPU(final PipelineRun run) {
            return instanceTypes.getOrDefault(run.getInstance().getNodeType(),
                    InstanceType.builder().vCPU(1).build()).getVCPU();
        }

        private boolean isIdleTagged(final PipelineRun run, final IdleMonitoringType type) {
            return MapUtils.emptyIfNull(run.getTags()).containsKey(type.getTag());
        }

        private void processIdleRun(final PipelineRun run, final IdleMonitoringType monitoringType,
                                    final Double usageRate, final int actionTimeout, final IdleRunAction action,
                                    final List<Pair<PipelineRun, Double>> pipelinesToNotify,
                                    final List<PipelineRun> runsToUpdateTags) {

            final String tag = monitoringType.getTag();

            if (shouldPerformActionOnIdleRun(run, actionTimeout, monitoringType)) {
                performActionOnIdleRun(run, action, usageRate, pipelinesToNotify, runsToUpdateTags, monitoringType);
                return;
            }

            if (!isIdleTagged(run, monitoringType)) {
                run.addTag(tag, TRUE_VALUE_STRING);
                Optional.ofNullable(getTimestampTag(monitoringType))
                        .ifPresent(dateTag -> run.addTag(dateTag, DateUtils.nowUTCStr()));
                runsToUpdateTags.add(run);
            }
            pipelinesToNotify.add(new ImmutablePair<>(run, usageRate));
            log.info(messageHelper.getMessage(MessageConstants.INFO_RUN_IDLE_NOTIFY,
                    run.getPodId(), monitoringType.name(), usageRate));
        }

        private boolean shouldPerformActionOnIdleRun(final PipelineRun run, final int actionTimeout,
                                                     final IdleMonitoringType monitoringType) {
            final String timestampTag = getTimestampTag(monitoringType);
            final String date = MapUtils.emptyIfNull(run.getTags()).get(timestampTag);
            if (StringUtils.isBlank(date)) {
                return false;
            }
            try {
                return DateUtils.strToUTCDate(date).isBefore(DateUtils.nowUTC().minusMinutes(actionTimeout));
            } catch (DateTimeParseException e) {
                log.error("Failed to parse idle timestamp tag {} for run {}: {}", timestampTag, run.getId(), date, e);
                return false;
            }
        }

        private void processFormerIdleRun(final PipelineRun run, final List<PipelineRun> runsToUpdateTags,
                                          final IdleMonitoringType monitoringType) {
            run.removeTag(monitoringType.getTag());
            Optional.ofNullable(getTimestampTag(monitoringType)).ifPresent(run::removeTag);
            notificationManager.removeNotificationTimestamps(
                    run.getId(),
                    NotificationType.getById(monitoringType.getNotificationTypeId())
            );
            runsToUpdateTags.add(run);
        }

        private void performActionOnIdleRun(final PipelineRun run, IdleRunAction action,
                                            final double resourceUsageRate,
                                            final List<Pair<PipelineRun, Double>> pipelinesToNotify,
                                            final List<PipelineRun> runsToUpdateTags,
                                            final IdleMonitoringType monitoringType) {

            log.info(messageHelper.getMessage(MessageConstants.INFO_RUN_IDLE_ACTION, run.getPodId(),
                    monitoringType, resourceUsageRate, action));

            switch (action) {
                case PAUSE:
                    if (run.getInstance().getSpot()) {
                        performNotify(run, resourceUsageRate, pipelinesToNotify);
                    } else {
                        performPause(run, resourceUsageRate);
                    }

                    break;
                case PAUSE_OR_STOP:
                    if (run.getInstance().getSpot()) {
                        performStop(run, resourceUsageRate, monitoringType, runsToUpdateTags);
                    } else {
                        performPause(run, resourceUsageRate);
                    }

                    break;
                case STOP:
                    performStop(run, resourceUsageRate, monitoringType, runsToUpdateTags);
                    break;
                default:
                    performNotify(run, resourceUsageRate, pipelinesToNotify);
            }
        }

        private void performNotify(final PipelineRun run, final double cpuUsageRate,
                                   final List<Pair<PipelineRun, Double>> pipelinesToNotify) {
            pipelinesToNotify.add(new ImmutablePair<>(run, cpuUsageRate));
        }

        private void processHighNetworkConsumingRuns(final List<PipelineRun> runs) {
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

            final Map<String, PipelineRun> running = groupedByNode(runs);

            final int bandwidthLimitTimeout = preferenceManager.getPreference(
                    SystemPreferences.SYSTEM_MAX_POD_BANDWIDTH_LIMIT_TIMEOUT_MINUTES);

            log.debug(messageHelper.getMessage(MessageConstants.DEBUG_RUN_METRICS_REQUEST,
                    "NETWORK", running.size(), String.join(", ", running.keySet())));

            final LocalDateTime now = DateUtils.nowUTC();
            final Map<String, Double> networkMetrics = monitoringDao.loadMetrics(ELKUsageMetric.NETWORK,
                    running.keySet(), now.minusMinutes(bandwidthLimitTimeout + ONE), now);
            log.debug(messageHelper.getMessage(MessageConstants.DEBUG_NETWORK_RUN_METRICS_RECEIVED,
                    networkMetrics.entrySet().stream().map(e -> e.getKey() + ":" + e.getValue())
                            .collect(Collectors.joining(", ")))
            );

            processHighNetworkConsumingRuns(running, networkMetrics, bandwidthLimit, actionTimeout, action);
        }

        private void processHighNetworkConsumingRun(PipelineRun run, int actionTimeout,
                                                    NetworkConsumingRunAction action,
                                                    List<Pair<PipelineRun, Double>> runsToNotify,
                                                    List<PipelineRun> runsToUpdateNotificationTime,
                                                    Double bandwidth, List<PipelineRun> runsToUpdateTags) {
            if (Objects.isNull(run.getLastNetworkConsumptionNotificationTime())) {
                run.addTag(NETWORK_CONSUMING_LEVEL_HIGH, TRUE_VALUE_STRING);
                Optional.ofNullable(getTimestampTag(NETWORK_CONSUMING_LEVEL_HIGH))
                        .ifPresent(tag -> run.addTag(tag, DateUtils.nowUTCStr()));
                runsToUpdateTags.add(run);

                log.info(messageHelper.getMessage(MessageConstants.INFO_RUN_HIGH_NETWORK_CONSUMPTION_NOTIFY,
                        run.getPodId(), bandwidth));

                performHighNetworkConsumingNotify(run, bandwidth, runsToNotify, runsToUpdateNotificationTime);
            } else if (shouldPerformActionOnNetworkConsumingRun(run, actionTimeout)) {
                performActionOnNetworkConsumingRun(run, action, bandwidth, runsToNotify,
                        runsToUpdateNotificationTime);
            }

        }

        private void processHighNetworkConsumingRuns(Map<String, PipelineRun> running,
                                                     Map<String, Double> networkMetrics,
                                                     double bandwidthLimit,
                                                     int actionTimeout, NetworkConsumingRunAction action) {
            final List<PipelineRun> runsToUpdateNotificationTime = new ArrayList<>(running.size());
            final List<Pair<PipelineRun, Double>> runsToNotify = new ArrayList<>(running.size());
            final List<PipelineRun> runsToUpdateTags = new ArrayList<>(running.size());
            for (Map.Entry<String, PipelineRun> entry : running.entrySet()) {
                PipelineRun run = entry.getValue();
                Double bandwidth = networkMetrics.get(entry.getKey());
                if (bandwidth != null) {
                    if (bandwidth >= bandwidthLimit) {
                        processHighNetworkConsumingRun(run, actionTimeout, action, runsToNotify,
                                runsToUpdateNotificationTime, bandwidth, runsToUpdateTags);
                    } else if (run.getLastNetworkConsumptionNotificationTime() != null) {
                        // No action is longer needed, clear timeout
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

        private void processFormerHighNetworkConsumingRun(final PipelineRun run,
                                                          final List<PipelineRun> runsToUpdateNotificationTime,
                                                          final List<PipelineRun> runsToUpdateTags) {
            run.setLastNetworkConsumptionNotificationTime(null);
            run.removeTag(NETWORK_CONSUMING_LEVEL_HIGH);
            run.removeTag(getTimestampTag(NETWORK_CONSUMING_LEVEL_HIGH));
            runsToUpdateNotificationTime.add(run);
            runsToUpdateTags.add(run);
        }

        private boolean shouldPerformActionOnNetworkConsumingRun(final PipelineRun run, final int actionTimeout) {
            return  actionTimeout > 0 && Objects.nonNull(run.getLastNetworkConsumptionNotificationTime()) &&
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

        private void performStop(final PipelineRun run,
                                 final double usageRate,
                                 final IdleMonitoringType monitoringType,
                                 final List<PipelineRun> runsToUpdateTags) {
            if (run.isNonPause() || run.isClusterRun()) {
                log.debug(messageHelper.getMessage(MessageConstants.DEBUG_RUN_IDLE_SKIP_CHECK, run.getPodId()));
                return;
            }
            pipelineRunManager.stop(run.getId());
            final String stopTag = preferenceManager.getPreference(SystemPreferences.SYSTEM_RUN_TAG_STOP_REASON);
            if (StringUtils.isNotBlank(stopTag)) {
                run.addTag(stopTag, monitoringType.getTag());
                runsToUpdateTags.add(run);
            }
            notificationManager.notifyIdleRuns(Collections.singletonList(new ImmutablePair<>(run, usageRate)),
                    NotificationType.IDLE_RUN_STOPPED, 0.0);
        }

        private void performPause(PipelineRun run, double usageRate) {
            if (run.isNonPause() || run.isClusterRun()) {
                log.debug(messageHelper.getMessage(MessageConstants.DEBUG_RUN_IDLE_SKIP_CHECK, run.getPodId()));
                return;
            }
            if (preferenceManager.findPreference(SystemPreferences.SYSTEM_MAINTENANCE_MODE).orElse(false)) {
                log.debug(messageHelper.getMessage(MessageConstants.ERROR_RUN_OPERATION_FORBIDDEN));
                return;
            }
            pipelineRunDockerOperationManager.pauseRun(run.getId(), true);
            notificationManager.notifyIdleRuns(Collections.singletonList(new ImmutablePair<>(run, usageRate)),
                    NotificationType.IDLE_RUN_PAUSED, 0.0);
        }

        private void processServerlessRuns() {
            final List<StopServerlessRun> activeServerlessRuns = ListUtils.emptyIfNull(
                    stopServerlessRunManager.loadActiveServerlessRuns());
            activeServerlessRuns.stream()
                    .filter(this::serverlessRunIsExpired)
                    .forEach(run -> pipelineRunManager.stopServerlessRun(run.getRunId()));
        }

        private boolean serverlessRunIsExpired(final StopServerlessRun run) {
            final Long timeout = getTimeoutMinutes(run);
            return Objects.nonNull(timeout) && run.getLastUpdate().isBefore(LocalDateTime.now().minusMinutes(timeout));
        }

        private Long getTimeoutMinutes(final StopServerlessRun run) {
            return Objects.nonNull(run.getStopAfter())
                    ? run.getStopAfter()
                    : preferenceManager.getPreference(SystemPreferences.LAUNCH_SERVERLESS_STOP_TIMEOUT).longValue();
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
                    .map(status ->
                            status.getTimestamp().isBefore(DateUtils.nowUTC().minusMinutes(actionTimeout)))
                    .orElse(false);
        }

        private String getTimestampTag(final String tag) {
            final String suffix = preferenceManager.getPreference(SystemPreferences.SYSTEM_RUN_TAG_DATE_SUFFIX);
            return StringUtils.isNotEmpty(suffix) ? tag + suffix : null;
        }

        private String getTimestampTag(final IdleMonitoringType monitoringType) {
            return getTimestampTag(monitoringType.getTag());
        }
    }
}
