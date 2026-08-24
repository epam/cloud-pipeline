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
import com.epam.pipeline.entity.cluster.InstanceType;
import com.epam.pipeline.entity.cluster.monitoring.ELKUsageMetric;
import com.epam.pipeline.entity.monitoring.IdleMonitoringConfig;
import com.epam.pipeline.entity.monitoring.IdleMonitoringType;
import com.epam.pipeline.entity.monitoring.IdleRunAction;
import com.epam.pipeline.entity.notification.NotificationType;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.notification.NotificationManager;
import com.epam.pipeline.manager.pipeline.PipelineRunDockerOperationManager;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.epam.pipeline.manager.preference.SystemPreferences.SYSTEM_IDLE_MONITORING_CONFIG;

@Slf4j
public abstract class AbstractIdleRunMonitor implements RunMonitor {

    protected static final double PERCENT = 100.0;
    protected static final int MILLIS = 1000;
    protected static final double ONE_THOUSANDTH = 0.001;
    protected static final long ONE = 1L;
    protected static final double ZERO_USAGE_RATE = 0.0;
    protected static final String TRUE_VALUE_STRING = "true";

    protected final PipelineRunManager pipelineRunManager;
    protected final PipelineRunDockerOperationManager pipelineRunDockerOperationManager;
    protected final NotificationManager notificationManager;
    protected final MonitoringESDao monitoringDao;
    protected final MessageHelper messageHelper;
    protected final PreferenceManager preferenceManager;
    protected final InstanceTypeCache instanceTypeCache;

    protected AbstractIdleRunMonitor(final PipelineRunManager pipelineRunManager,
                                     final PipelineRunDockerOperationManager pipelineRunDockerOperationManager,
                                     final NotificationManager notificationManager,
                                     final MonitoringESDao monitoringDao,
                                     final MessageHelper messageHelper,
                                     final PreferenceManager preferenceManager,
                                     final InstanceTypeCache instanceTypeCache) {
        this.pipelineRunManager = pipelineRunManager;
        this.pipelineRunDockerOperationManager = pipelineRunDockerOperationManager;
        this.notificationManager = notificationManager;
        this.monitoringDao = monitoringDao;
        this.messageHelper = messageHelper;
        this.preferenceManager = preferenceManager;
        this.instanceTypeCache = instanceTypeCache;
    }

    protected abstract IdleMonitoringType getType();

    protected IdleMonitoringConfig findEnabledIdleConfig(final IdleMonitoringType monitoringType) {
        return ListUtils.emptyIfNull(preferenceManager.getPreference(SYSTEM_IDLE_MONITORING_CONFIG))
                .stream()
                .filter(IdleMonitoringConfig::isEnabled)
                .filter(config -> config.getType() == monitoringType)
                .findFirst()
                .orElse(null);
    }

    protected boolean isIdleConfigReadyForProcessing(final IdleMonitoringConfig conf,
                                                      final IdleMonitoringType type) {
        if (conf == null) {
            log.debug("{} idle monitoring config is not configured or disabled, skipping idle check.", type.name());
            return false;
        }
        if (Objects.isNull(conf.getGracePeriodMinutes()) || Objects.isNull(conf.getActionTimeoutMinutes())) {
            log.warn("{} idle monitoring config misses grace period or action timeout, skipping idle check.",
                    type.name());
            return false;
        }
        return true;
    }

    protected Map<String, PipelineRun> filterNotProlongedRuns(final Map<String, PipelineRun> running,
                                                               final int idleGracePeriod) {
        return running.entrySet().stream()
                .filter(e -> Optional.ofNullable(e.getValue().getProlongedAtTime())
                        .map(timestamp -> DateUtils.nowUTC().isAfter(timestamp.plusMinutes(idleGracePeriod)))
                        .orElse(Boolean.FALSE))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    protected Map<String, Double> loadIdleMetrics(final ELKUsageMetric usageMetric, final Set<String> nodes,
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

    protected boolean isIdleTagged(final PipelineRun run, final IdleMonitoringType type) {
        return MapUtils.emptyIfNull(run.getTags()).containsKey(type.getTag());
    }

    protected boolean hasGpu(final PipelineRun run) {
        return Optional.ofNullable(run.getInstance())
                .map(RunInstance::getNodeType)
                .map(instanceTypeCache.get()::get)
                .map(type -> type.getGpu() > 0)
                .orElse(false);
    }

    protected int instanceVCPU(final PipelineRun run) {
        return instanceTypeCache.get()
                .getOrDefault(run.getInstance().getNodeType(), InstanceType.builder().vCPU(1).build())
                .getVCPU();
    }

    protected List<PipelineRun> filterGpuRuns(final List<PipelineRun> runs) {
        return ListUtils.emptyIfNull(runs).stream()
                .filter(this::hasGpu)
                .collect(Collectors.toList());
    }

    protected void processIdleRun(final PipelineRun run, final IdleMonitoringType monitoringType,
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

    protected void processFormerIdleRun(final PipelineRun run, final List<PipelineRun> runsToUpdateTags,
                                         final IdleMonitoringType monitoringType) {
        run.removeTag(monitoringType.getTag());
        Optional.ofNullable(getTimestampTag(monitoringType)).ifPresent(run::removeTag);
        notificationManager.removeNotificationTimestamps(run.getId(), monitoringType.getNotificationType());
        runsToUpdateTags.add(run);
    }

    protected void performActionOnIdleRun(final PipelineRun run, final IdleRunAction action,
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

    protected boolean shouldPerformActionOnIdleRun(final PipelineRun run, final int actionTimeout,
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

    protected String getTimestampTag(final IdleMonitoringType monitoringType) {
        return getTimestampTag(monitoringType.getTag());
    }

    protected String getTimestampTag(final String tag) {
        return RunMonitorUtils.getTimestampTag(tag, preferenceManager);
    }

    private void performNotify(final PipelineRun run, final double usageRate,
                                final List<Pair<PipelineRun, Double>> pipelinesToNotify) {
        pipelinesToNotify.add(new ImmutablePair<>(run, usageRate));
    }

    private void performPause(final PipelineRun run, final double usageRate) {
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

    private void performStop(final PipelineRun run, final double usageRate,
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
}
