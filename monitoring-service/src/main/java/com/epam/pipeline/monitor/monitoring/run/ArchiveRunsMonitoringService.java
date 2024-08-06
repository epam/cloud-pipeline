/*
 * Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.monitor.monitoring.run;

import com.epam.pipeline.monitor.monitoring.MonitoringService;
import com.epam.pipeline.monitor.rest.CloudPipelineAPIClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

@Slf4j
public class ArchiveRunsMonitoringService implements MonitoringService {

    private final CloudPipelineAPIClient client;
    private final String monitorEnabledPreferenceName;

    public ArchiveRunsMonitoringService(final CloudPipelineAPIClient client,
                                        @Value("${preference.name.archive.runs.monitor.enable}")
                                        final String monitorEnabledPreferenceName) {
        this.client = client;
        this.monitorEnabledPreferenceName = monitorEnabledPreferenceName;
    }

    @Override
    public void monitor() {
        if (!client.getBooleanPreference(monitorEnabledPreferenceName)) {
            log.debug("Archive runs monitor is not enabled");
            return;
        }

        client.archiveRuns();
        log.debug("Finished archive runs monitoring");
    }
}
