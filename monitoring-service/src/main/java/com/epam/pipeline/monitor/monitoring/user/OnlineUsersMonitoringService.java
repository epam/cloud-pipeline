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

package com.epam.pipeline.monitor.monitoring.user;

import com.epam.pipeline.monitor.monitoring.AbstractSchedulingService;
import com.epam.pipeline.monitor.rest.CloudPipelineAPIClient;
import com.epam.pipeline.monitor.service.preference.PreferencesService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

@Service
public class OnlineUsersMonitoringService extends AbstractSchedulingService {
    private final OnlineUsersMonitoringServiceCore core;
    private final String monitorDelayPreferenceName;

    public OnlineUsersMonitoringService(final TaskScheduler scheduler,
                                        final OnlineUsersMonitoringServiceCore core,
                                        final CloudPipelineAPIClient client,
                                        @Value("${preference.name.usage.users.monitor.delay}")
                                        final String monitorDelayPreferenceName,
                                        final PreferencesService preferencesService) {
        super(scheduler, client, preferencesService);
        this.core = core;
        this.monitorDelayPreferenceName = monitorDelayPreferenceName;
    }

    @PostConstruct
    public void init() {
        scheduleFixedDelay(core::monitor, monitorDelayPreferenceName, "UsageUserMonitor");
    }
}
