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

package com.epam.pipeline.manager.cluster.performancemonitoring;

import com.epam.pipeline.dao.monitoring.MonitoringESDao;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.manager.cluster.performancemonitoring.monitor.RunMonitor;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.scheduling.AbstractSchedulingManager;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.SchedulerLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Schedules and orchestrates resource monitoring for active runs.
 * Monitoring logic is delegated to individual {@link RunMonitor} beans.
 */
@Service
@ConditionalOnProperty("monitoring.elasticsearch.url")
@Slf4j
public class ResourceMonitoringManager extends AbstractSchedulingManager {

    private final PipelineRunManager pipelineRunManager;
    private final MonitoringESDao monitoringDao;
    private final PreferenceManager preferenceManager;
    private final List<RunMonitor> monitors;

    @Autowired
    public ResourceMonitoringManager(final PipelineRunManager pipelineRunManager,
                                      final MonitoringESDao monitoringDao,
                                      final PreferenceManager preferenceManager,
                                      final List<RunMonitor> monitors) {
        this.pipelineRunManager = pipelineRunManager;
        this.monitoringDao = monitoringDao;
        this.preferenceManager = preferenceManager;
        this.monitors = monitors.stream()
                .sorted(Comparator.comparingInt(RunMonitor::order))
                .collect(Collectors.toList());
    }

    @PostConstruct
    public void init() {
        scheduleFixedDelaySecured(this::monitorResourceUsage, SystemPreferences.SYSTEM_RESOURCE_MONITORING_PERIOD,
                "Resource Usage Monitoring");
    }

    @Scheduled(cron = "0 0 0 ? * *")
    @SchedulerLock(name = "ResourceMonitoringManager_removeOldIndices", lockAtMostForString = "PT1H")
    public void removeOldIndices() {
        monitoringDao.deleteIndices(preferenceManager.getPreference(
                SystemPreferences.SYSTEM_RESOURCE_MONITORING_STATS_RETENTION_PERIOD));
    }

    @SchedulerLock(name = "ResourceMonitoringManager_monitorResourceUsage", lockAtMostForString = "PT10M")
    public void monitorResourceUsage() {
        final List<PipelineRun> runs = pipelineRunManager.loadRunningPipelineRuns();
        monitors.forEach(m -> m.monitor(runs));
    }
}
