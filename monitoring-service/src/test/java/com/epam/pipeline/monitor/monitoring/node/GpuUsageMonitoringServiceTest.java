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
import com.epam.pipeline.monitor.model.node.GpuUsageStats;
import com.epam.pipeline.monitor.model.node.GpuUsageSummary;
import com.epam.pipeline.monitor.model.node.GpuUsages;
import com.epam.pipeline.monitor.rest.CloudPipelineAPIClient;
import com.epam.pipeline.monitor.service.InstanceTypesLoader;
import com.epam.pipeline.monitor.service.elasticsearch.MonitoringElasticsearchService;
import com.epam.pipeline.monitor.service.reporter.NodeReporterService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

class GpuUsageMonitoringServiceTest {
    private static final String TEST = "test";
    private static final String NODE_1 = "node1";
    private static final String NODE_2 = "node2";
    private static final String GPU_ID_1 = "0";
    private static final String GPU_ID_2 = "1";
    private static final int GPU_UTILIZATION = 80;
    private static final int MEMORY_USED = 2000;
    private static final int TOTAL_MEMORY = 20000;
    private static final int MEMORY_UTILIZATION_1 = 10;
    private static final int TEST_VALUE_1 = 100;
    private static final int TEST_VALUE_2 = 50;

    private final CloudPipelineAPIClient cloudPipelineClient = mock(CloudPipelineAPIClient.class);
    private final NodeReporterService nodeReporterService = mock(NodeReporterService.class);
    private final MonitoringElasticsearchService monitoringElasticsearchService =
            mock(MonitoringElasticsearchService.class);
    private final InstanceTypesLoader instanceTypesLoader = mock(InstanceTypesLoader.class);
    private final GpuUsageMonitoringService monitor = new GpuUsageMonitoringService(
            TEST, cloudPipelineClient, nodeReporterService, monitoringElasticsearchService, instanceTypesLoader);

    @Test
    void shouldSkipProcessIfNotRequired() {
        when(cloudPipelineClient.getBooleanPreference(TEST)).thenReturn(false);
        monitor.monitor();
        verifyZeroInteractions(nodeReporterService, monitoringElasticsearchService);
    }

    @Test
    void shouldSkipProcessIfNoUsagesLoaded() {
        when(cloudPipelineClient.getBooleanPreference(TEST)).thenReturn(true);
        when(nodeReporterService.collectGpuUsages(any())).thenReturn(null);
        monitor.monitor();
        verifyZeroInteractions(monitoringElasticsearchService);
    }

    // case example: unexpected error during node reporter query
    @Test
    void shouldProcessEmptyUsages() {
        final List<GpuUsages> usages = Collections.singletonList(GpuUsages.builder().build());
        when(cloudPipelineClient.getBooleanPreference(TEST)).thenReturn(true);
        when(nodeReporterService.collectGpuUsages(any())).thenReturn(usages);
        monitor.monitor();
        verify(monitoringElasticsearchService).saveGpuUsages(Collections.emptyList());
    }

    @Test
    void shouldProcessGpuUsages() {
        final NodeReporterGpuUsages usage1 = NodeReporterGpuUsages.builder()
                .name(TEST)
                .index(GPU_ID_1)
                .utilizationGpu(GPU_UTILIZATION)
                .memoryUsed(MEMORY_USED)
                .memoryTotal(TOTAL_MEMORY)
                .build();
        final NodeReporterGpuUsages usage2 = NodeReporterGpuUsages.builder()
                .name(TEST)
                .index(GPU_ID_2)
                .utilizationGpu(GPU_UTILIZATION)
                .memoryUsed(MEMORY_USED)
                .memoryTotal(TOTAL_MEMORY)
                .build();

        final NodeReporterGpuUsages usage3 = NodeReporterGpuUsages.builder()
                .name(TEST)
                .index(GPU_ID_1)
                .utilizationGpu(GPU_UTILIZATION)
                .memoryUsed(MEMORY_USED)
                .memoryTotal(TOTAL_MEMORY)
                .build();
        final NodeReporterGpuUsages usage4 = NodeReporterGpuUsages.builder()
                .name(TEST)
                .index(GPU_ID_2)
                .utilizationGpu(0)
                .memoryUsed(0)
                .memoryTotal(TOTAL_MEMORY)
                .build();

        final GpuUsages gpuUsage1 = GpuUsages.builder()
                .timestamp(LocalDateTime.now())
                .nodename(NODE_1)
                .usages(Arrays.asList(usage1, usage2))
                .build();

        final GpuUsages gpuUsage2 = GpuUsages.builder()
                .timestamp(LocalDateTime.now())
                .nodename(NODE_2)
                .usages(Arrays.asList(usage3, usage4))
                .build();

        final List<GpuUsages> usages = Arrays.asList(gpuUsage1, gpuUsage2);
        when(cloudPipelineClient.getBooleanPreference(TEST)).thenReturn(true);
        when(nodeReporterService.collectGpuUsages(any())).thenReturn(usages);

        monitor.monitor();

        verify(monitoringElasticsearchService)
                .saveGpuUsages(Arrays.asList(expectedGpuUsages1(gpuUsage1), expectedGpuUsages2(gpuUsage2)));
    }

    private static GpuUsages expectedGpuUsages1(final GpuUsages gpuUsage1) {
        final GpuUsageSummary average = GpuUsageSummary.builder()
                .gpuUtilization(GPU_UTILIZATION)
                .memoryUsage(MEMORY_USED)
                .memoryUtilization(MEMORY_UTILIZATION_1)
                .build();
        final GpuUsageSummary min = GpuUsageSummary.builder()
                .gpuUtilization(GPU_UTILIZATION)
                .memoryUsage(MEMORY_USED)
                .memoryUtilization(MEMORY_UTILIZATION_1)
                .build();
        final GpuUsageSummary max = GpuUsageSummary.builder()
                .gpuUtilization(GPU_UTILIZATION)
                .memoryUsage(MEMORY_USED)
                .memoryUtilization(MEMORY_UTILIZATION_1)
                .build();
        final GpuUsageStats stats = GpuUsageStats.builder()
                .average(average)
                .min(min)
                .max(max)
                .activeGpusUtilization(TEST_VALUE_1)
                .build();

        return GpuUsages.builder()
                .nodename(gpuUsage1.getNodename())
                .timestamp(gpuUsage1.getTimestamp())
                .usages(new ArrayList<>(gpuUsage1.getUsages().stream()
                        .peek(value -> value.setMemoryUtilization(MEMORY_UTILIZATION_1))
                        .collect(Collectors.toList())))
                .stats(stats)
                .build();
    }

    private static GpuUsages expectedGpuUsages2(final GpuUsages gpuUsage2) {
        final GpuUsageSummary average = GpuUsageSummary.builder()
                .gpuUtilization(GPU_UTILIZATION / 2)
                .memoryUsage(MEMORY_USED / 2)
                .memoryUtilization(MEMORY_UTILIZATION_1 / 2)
                .build();
        final GpuUsageSummary min = GpuUsageSummary.builder()
                .gpuUtilization(0)
                .memoryUsage(0)
                .memoryUtilization(0)
                .build();
        final GpuUsageSummary max = GpuUsageSummary.builder()
                .gpuUtilization(GPU_UTILIZATION)
                .memoryUsage(MEMORY_USED)
                .memoryUtilization(MEMORY_UTILIZATION_1)
                .build();
        final GpuUsageStats stats = GpuUsageStats.builder()
                .average(average)
                .min(min)
                .max(max)
                .activeGpusUtilization(TEST_VALUE_2)
                .build();

        return GpuUsages.builder()
                .nodename(gpuUsage2.getNodename())
                .timestamp(gpuUsage2.getTimestamp())
                .usages(new ArrayList<>(gpuUsage2.getUsages().stream()
                        .peek(value -> value.setMemoryUtilization(
                                GPU_ID_1.equals(value.getIndex()) ? MEMORY_UTILIZATION_1 : 0))
                        .collect(Collectors.toList())))
                .stats(stats)
                .build();
    }
}
