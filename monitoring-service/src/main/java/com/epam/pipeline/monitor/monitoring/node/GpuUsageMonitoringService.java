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

package com.epam.pipeline.monitor.monitoring.node;

import com.epam.pipeline.monitor.model.node.NodeGpuUsages;
import com.epam.pipeline.monitor.monitoring.MonitoringService;
import com.epam.pipeline.monitor.rest.CloudPipelineAPIClient;
import com.epam.pipeline.monitor.service.elasticsearch.MonitoringElasticsearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class GpuUsageMonitoringService implements MonitoringService {

    private final String monitorEnabledPreferenceName;
    private final CloudPipelineAPIClient cloudPipelineClient;
    private final NodeReporterService nodeReporterService;
    private final MonitoringElasticsearchService monitoringElasticsearchService;

    public GpuUsageMonitoringService(@Value("${preference.name.usage.node.gpu.enable}")
                                         final String monitorEnabledPreferenceName,
                                     final CloudPipelineAPIClient cloudPipelineClient,
                                     final NodeReporterService nodeReporterService,
                                     final MonitoringElasticsearchService monitoringElasticsearchService) {
        this.monitorEnabledPreferenceName = monitorEnabledPreferenceName;
        this.cloudPipelineClient = cloudPipelineClient;
        this.nodeReporterService = nodeReporterService;
        this.monitoringElasticsearchService = monitoringElasticsearchService;
    }

    @Override
    public void monitor() {
        if (!cloudPipelineClient.getBooleanPreference(monitorEnabledPreferenceName)) {
            log.debug("Gpu usage monitor is not enabled");
            return;
        }
        log.info("Collecting gpu usages...");
        final List<NodeGpuUsages> usages = nodeReporterService.collectGpuUsages();
        monitoringElasticsearchService.saveGpuUsages(usages);
    }
}
