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

import com.epam.pipeline.entity.reporter.NodeReporterGpuUsages;
import com.epam.pipeline.monitor.model.node.GpuUsageSummary;
import com.epam.pipeline.monitor.model.node.GpuUsageStats;
import com.epam.pipeline.monitor.model.node.GpuUsages;
import com.epam.pipeline.monitor.monitoring.MonitoringService;
import com.epam.pipeline.monitor.rest.CloudPipelineAPIClient;
import com.epam.pipeline.monitor.service.InstanceTypesLoader;
import com.epam.pipeline.monitor.service.elasticsearch.MonitoringElasticsearchService;
import com.epam.pipeline.monitor.service.reporter.NodeReporterService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class GpuUsageMonitoringService implements MonitoringService {
    private static final int TO_PERCENTAGE = 100;

    private final String monitorEnabledPreferenceName;
    private final CloudPipelineAPIClient cloudPipelineClient;
    private final NodeReporterService nodeReporterService;
    private final MonitoringElasticsearchService monitoringElasticsearchService;
    private final InstanceTypesLoader instanceTypesLoader;

    public GpuUsageMonitoringService(@Value("${preference.name.usage.node.gpu.enable}")
                                         final String monitorEnabledPreferenceName,
                                     final CloudPipelineAPIClient cloudPipelineClient,
                                     final NodeReporterService nodeReporterService,
                                     final MonitoringElasticsearchService monitoringElasticsearchService,
                                     final InstanceTypesLoader instanceTypesLoader) {
        this.monitorEnabledPreferenceName = monitorEnabledPreferenceName;
        this.cloudPipelineClient = cloudPipelineClient;
        this.nodeReporterService = nodeReporterService;
        this.monitoringElasticsearchService = monitoringElasticsearchService;
        this.instanceTypesLoader = instanceTypesLoader;
    }

    @Override
    public void monitor() {
        if (!cloudPipelineClient.getBooleanPreference(monitorEnabledPreferenceName)) {
            log.debug("Gpu usage monitor is not enabled");
            return;
        }
        log.info("Collecting gpu usages...");
        final Set<String> gpuInstanceTypes = instanceTypesLoader.loadGpuInstanceTypes();
        final List<GpuUsages> usages = nodeReporterService.collectGpuUsages(gpuInstanceTypes);

        if (CollectionUtils.isEmpty(usages)) {
            log.info("Gpu usages not found. Finishing gpu usages monitoring.");
            return;
        }
        final List<GpuUsages> usagesWithSummaries = usages.stream()
                .map(this::tryFillSummaries)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        monitoringElasticsearchService.saveGpuUsages(usagesWithSummaries);
        log.info("Finishing gpu usages monitoring.");
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private GpuUsages tryFillSummaries(final GpuUsages usage) {
        try {
            if (CollectionUtils.isEmpty(usage.getUsages())) {
                return null;
            }
            usage.setUsages(usage.getUsages().stream()
                    .peek(value -> value.setMemoryUtilization(
                            calculateUtilization(value.getMemoryUsed(), value.getMemoryTotal())))
                    .collect(Collectors.toList()));
            usage.setStats(collectSummaries(usage.getUsages()));
            return usage;
        } catch (Exception e) {
            log.error("Failed to collect gpu statistics.", e);
            return null;
        }
    }

    private GpuUsageStats collectSummaries(final List<NodeReporterGpuUsages> usages) {
        final IntSummaryStatistics gpuUtilizationSummary = usages.stream()
                .mapToInt(NodeReporterGpuUsages::getUtilizationGpu)
                .summaryStatistics();
        final IntSummaryStatistics memoryUtilizationSummary = usages.stream()
                .mapToInt(NodeReporterGpuUsages::getMemoryUtilization)
                .summaryStatistics();
        final IntSummaryStatistics memoryUsageSummary = usages.stream()
                .mapToInt(NodeReporterGpuUsages::getMemoryUsed)
                .summaryStatistics();

        final GpuUsageSummary averagesSummary = GpuUsageSummary.builder()
                .gpuUtilization((int) Math.round(gpuUtilizationSummary.getAverage()))
                .memoryUtilization((int) Math.round(memoryUtilizationSummary.getAverage()))
                .memoryUsage((int) Math.round(memoryUsageSummary.getAverage()))
                .build();
        final GpuUsageSummary minSummary = GpuUsageSummary.builder()
                .gpuUtilization(gpuUtilizationSummary.getMin())
                .memoryUtilization(memoryUtilizationSummary.getMin())
                .memoryUsage(memoryUsageSummary.getMin())
                .build();
        final GpuUsageSummary maxSummary = GpuUsageSummary.builder()
                .gpuUtilization(gpuUtilizationSummary.getMax())
                .memoryUtilization(memoryUtilizationSummary.getMax())
                .memoryUsage(memoryUsageSummary.getMax())
                .build();

        final long activeDevices = usages.stream()
                .map(NodeReporterGpuUsages::getUtilizationGpu)
                .filter(value -> value > 0)
                .count();

        final String deviceName = usages.stream()
                .findFirst()
                .map(NodeReporterGpuUsages::getName)
                .orElse(null);

        return GpuUsageStats.builder()
                .deviceName(deviceName)
                .average(averagesSummary)
                .min(minSummary)
                .max(maxSummary)
                .activeGpusUtilization(calculateUtilization((int) activeDevices, usages.size()))
                .build();
    }

    private Integer calculateUtilization(final int value, final int totalValue) {
        return (int) (((double) value / totalValue) * TO_PERCENTAGE);
    }
}
