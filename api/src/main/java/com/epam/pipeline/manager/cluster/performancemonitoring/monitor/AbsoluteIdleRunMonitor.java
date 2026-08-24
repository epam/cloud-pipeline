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

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dao.monitoring.MonitoringESDao;
import com.epam.pipeline.entity.monitoring.IdleMonitoringConfig;
import com.epam.pipeline.entity.monitoring.IdleMonitoringType;
import com.epam.pipeline.entity.notification.NotificationType;
import com.epam.pipeline.entity.pipeline.PipelineRun;
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
import java.util.Objects;

@Component
@Slf4j
public class AbsoluteIdleRunMonitor extends AbstractIdleRunMonitor {

    @Autowired
    public AbsoluteIdleRunMonitor(final PipelineRunManager pipelineRunManager,
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
        return IdleMonitoringType.ABSOLUTE;
    }

    @Override
    public int order() {
        return 3;
    }

    @Override
    public void monitor(final List<PipelineRun> runs) {
        final IdleMonitoringConfig absoluteConf = findEnabledIdleConfig(getType());
        if (absoluteConf == null) {
            log.debug("ABSOLUTE idle monitoring config is not configured or disabled, skipping idle check.");
            return;
        }
        if (Objects.isNull(absoluteConf.getActionTimeoutMinutes())) {
            log.warn("ABSOLUTE idle monitoring config misses action timeout, skipping idle check.");
            return;
        }

        final int actionTimeout = absoluteConf.getActionTimeoutMinutes();

        final List<Pair<PipelineRun, Double>> runsToNotify = new ArrayList<>(runs.size());
        final List<PipelineRun> runsToUpdateTags = new ArrayList<>(runs.size());
        for (final PipelineRun run : ListUtils.emptyIfNull(runs)) {
            if (isAbsolutelyIdle(run)) {
                processIdleRun(run, getType(), ZERO_USAGE_RATE, actionTimeout,
                        absoluteConf.getAction(), runsToNotify, runsToUpdateTags);
            } else if (isIdleTagged(run, getType())) {
                processFormerIdleRun(run, runsToUpdateTags, getType());
            }
        }
        notificationManager.notifyIdleRuns(runsToNotify, getType().getNotificationType(), ZERO_USAGE_RATE);
        pipelineRunManager.updateRunsTags(runsToUpdateTags);
    }

    private boolean isAbsolutelyIdle(final PipelineRun run) {
        if (!isIdleTagged(run, IdleMonitoringType.CPU)) {
            return false;
        }
        return !hasGpu(run) || isIdleTagged(run, IdleMonitoringType.GPU);
    }
}
