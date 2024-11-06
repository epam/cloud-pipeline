/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.monitor.config;

import com.epam.pipeline.monitor.monitoring.SchedulingService;
import com.epam.pipeline.monitor.monitoring.node.GpuUsageMonitoringService;
import com.epam.pipeline.monitor.monitoring.pool.NodePoolMonitoringService;
import com.epam.pipeline.monitor.monitoring.pool.NodePoolUsageCleanerService;
import com.epam.pipeline.monitor.monitoring.run.ArchiveRunsMonitoringService;
import com.epam.pipeline.monitor.monitoring.user.OnlineUsersCleanerService;
import com.epam.pipeline.monitor.monitoring.user.OnlineUsersMonitoringService;
import com.epam.pipeline.monitor.rest.CloudPipelineAPIClient;
import com.epam.pipeline.monitor.service.preference.PreferencesService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;

@Configuration
public class MonitoringConfiguration {

    @Bean
    public SchedulingService nodePoolMonitor(final TaskScheduler scheduler,
                                             final NodePoolMonitoringService monitoringService,
                                             final CloudPipelineAPIClient client,
                                             @Value("${preference.name.usage.node.pool.delay}")
                                                 final String monitorDelayPreferenceName,
                                             final PreferencesService preferencesService) {

        return new SchedulingService(scheduler, monitoringService, client, monitorDelayPreferenceName,
                preferencesService, "NodePoolUsageMonitor");
    }

    @Bean
    public SchedulingService nodePoolUsageCleaner(final TaskScheduler scheduler,
                                                  final NodePoolUsageCleanerService monitoringService,
                                                  final CloudPipelineAPIClient client,
                                                  @Value("${preference.name.usage.node.pool.clean.delay}")
                                                      final String monitorDelayPreferenceName,
                                                  final PreferencesService preferencesService) {
        return new SchedulingService(scheduler, monitoringService, client, monitorDelayPreferenceName,
                preferencesService, "NodePoolUsageCleaner");
    }

    @Bean
    public SchedulingService onlineUsersMonitor(final TaskScheduler scheduler,
                                                final OnlineUsersMonitoringService monitoringService,
                                                final CloudPipelineAPIClient client,
                                                @Value("${preference.name.usage.users.monitor.delay}")
                                                    final String monitorDelayPreferenceName,
                                                final PreferencesService preferencesService) {
        return new SchedulingService(scheduler, monitoringService, client, monitorDelayPreferenceName,
                preferencesService, "UsageUserMonitor");
    }

    @Bean
    public SchedulingService onlineUsersCleaner(final TaskScheduler scheduler,
                                                final OnlineUsersCleanerService monitoringService,
                                                final CloudPipelineAPIClient client,
                                                @Value("${preference.name.usage.users.clean.delay}")
                                                    final String monitorDelayPreferenceName,
                                                final PreferencesService preferencesService) {
        return new SchedulingService(scheduler, monitoringService, client, monitorDelayPreferenceName,
                preferencesService, "UsageUserCleaner");
    }

    @Bean
    public SchedulingService gpuUsageMonitor(final TaskScheduler scheduler,
                                             final GpuUsageMonitoringService monitoringService,
                                             final CloudPipelineAPIClient client,
                                             @Value("${preference.name.usage.node.gpu.delay}")
                                                 final String monitorDelayPreferenceName,
                                             final PreferencesService preferencesService) {
        return new SchedulingService(scheduler, monitoringService, client, monitorDelayPreferenceName,
                preferencesService, "NodeGpuUsageMonitor");
    }

    @Bean
    public SchedulingService archiveRunsMonitor(final TaskScheduler scheduler,
                                                final ArchiveRunsMonitoringService monitoringService,
                                                final CloudPipelineAPIClient client,
                                                @Value("${preference.name.archive.runs.monitor.delay}")
                                                    final String monitorDelayPreferenceName,
                                                final PreferencesService preferencesService) {
        return new SchedulingService(scheduler, monitoringService, client, monitorDelayPreferenceName,
                preferencesService, "ArchiveRunsMonitor");
    }
}
