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
import com.epam.pipeline.entity.monitoring.IdleMonitoringConfig;
import com.epam.pipeline.entity.monitoring.IdleMonitoringType;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.manager.cluster.performancemonitoring.monitor.common.InstanceTypeCache;
import com.epam.pipeline.manager.notification.NotificationManager;
import com.epam.pipeline.manager.pipeline.PipelineRunDockerOperationManager;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
@Slf4j
public class GpuIdleRunMonitor extends AbstractIdleRunMonitor {

    @Autowired
    public GpuIdleRunMonitor(final PipelineRunManager pipelineRunManager,
                              final PipelineRunDockerOperationManager pipelineRunDockerOperationManager,
                              final NotificationManager notificationManager,
                              final MonitoringESDao monitoringDao,
                              final MessageHelper messageHelper,
                              final PreferenceManager preferenceManager,
                              final InstanceTypeCache instanceTypeCache) {
        super(pipelineRunManager, pipelineRunDockerOperationManager, notificationManager, monitoringDao,
                messageHelper, preferenceManager, instanceTypeCache);
    }

    @Override
    protected IdleMonitoringType getType() {
        return IdleMonitoringType.GPU;
    }

    @Override
    public int order() {
        // Must run before AbsoluteIdleRunMonitor (order=3), which checks for the IDLE_GPU tag set here.
        return 1;
    }

    @Override
    public void monitor(final List<PipelineRun> runs) {
        final Map<String, PipelineRun> running = groupedByNode(filterGpuRuns(runs));
        final IdleMonitoringConfig conf = getIdleConfig(getType());
        if (!isIdleConfigReadyForProcessing(conf, getType())) {
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
                processIdleRun(run, getType(), activeGPUs, conf.getActionTimeoutMinutes(),
                        conf.getAction(), runsToNotify, runsToUpdateTags);
            } else if (isIdleTagged(run, getType())) {
                log.debug(messageHelper.getMessage(MessageConstants.DEBUG_RUN_NOT_IDLED,
                        run.getPodId(), getType().name(), activeGPUs));
                processFormerIdleRun(run, runsToUpdateTags, getType());
            }
        }
        notificationManager.notifyIdleRuns(runsToNotify, getType().getNotificationType(), ZERO_USAGE_RATE);
        pipelineRunManager.updateRunsTags(runsToUpdateTags);
    }

    protected List<PipelineRun> filterGpuRuns(final List<PipelineRun> runs) {
        return ListUtils.emptyIfNull(runs).stream()
                .filter(this::hasGpu)
                .collect(Collectors.toList());
    }
}
