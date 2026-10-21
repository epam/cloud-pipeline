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
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.math3.util.Precision;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
@Slf4j
public class CpuIdleRunMonitor extends AbstractIdleRunMonitor {

    @Autowired
    public CpuIdleRunMonitor(final PipelineRunManager pipelineRunManager,
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
        return IdleMonitoringType.CPU;
    }

    @Override
    public int order() {
        // Must run before AbsoluteIdleRunMonitor (order=3), which checks for the IDLE_CPU tag set here.
        return 0;
    }

    @Override
    public void monitor(final List<PipelineRun> runs) {
        final Map<String, PipelineRun> running = groupedByNode(runs);
        final IdleMonitoringConfig conf = getIdleConfig(getType());
        if (!isIdleConfigReadyForProcessing(conf, getType())) {
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
                processIdleRun(run, getType(), usageRate, conf.getActionTimeoutMinutes(),
                        conf.getAction(), runsToNotify, runsToUpdateTags);
            } else if (isIdleTagged(run, getType())) {
                log.debug(messageHelper.getMessage(MessageConstants.DEBUG_RUN_NOT_IDLED,
                        run.getPodId(), getType().name(), usageRate));
                processFormerIdleRun(run, runsToUpdateTags, getType());
            }
        }
        notificationManager.notifyIdleRuns(runsToNotify, getType().getNotificationType(),
                conf.getThresholdPercent());
        pipelineRunManager.updateRunsTags(runsToUpdateTags);
    }
}
