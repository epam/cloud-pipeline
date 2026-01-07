/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.entity.cluster.monitoring.MonitoringStats;
import com.epam.pipeline.entity.cluster.monitoring.gpu.GpuMetricsGranularity;
import com.epam.pipeline.entity.cluster.monitoring.gpu.GpuMonitoringStats;
import com.epam.pipeline.entity.cluster.monitoring.platform.network.NetworkEventFilter;
import com.epam.pipeline.entity.cluster.monitoring.platform.histogram.HistogramBin;
import com.epam.pipeline.entity.cluster.monitoring.platform.histogram.HistogramType;
import com.epam.pipeline.entity.run.PipelineRunPerformanceMetric;
import com.epam.pipeline.entity.run.PipelineRunPerformanceMetrics;
import com.epam.pipeline.entity.run.PipelineRunPerformanceMetricsType;
import com.epam.pipeline.manager.cluster.MonitoringReportType;
import org.springframework.util.Assert;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Node usage monitoring manager.
 */
public interface UsageMonitoringManager {

    /**
     * Retrieves monitoring stats for node.
     *
     * @param nodeName Cluster node name.
     * @return List of monitoring stats.
     */
    default List<MonitoringStats> getStatsForNode(String nodeName) {
        return getStatsForNode(nodeName, null, null);
    }

    /**
     * Retrieves monitoring stats for node.
     *
     * @param nodeName Cluster node name.
     * @return List of monitoring stats.
     */
    default PipelineRunPerformanceMetrics getStatsForRun(Long runId, String nodeName) {
        final List<MonitoringStats> statsForNode = getStatsForNode(nodeName, null, null, null);
        Assert.state(statsForNode.size() == 1, "There should be only 1 monitoring stat!");
        return new PipelineRunPerformanceMetrics(
                runId,
                mapToRunPerformanceMetric(statsForNode.get(0))
        );
    }

    /**
     * Retrieves monitoring stats for node.
     *
     * @param nodeName Cluster node name.
     * @param from Minimal date for collecting stats.
     * @param to Maximal date for collecting stats.
     * @return List of monitoring stats.
     */
    List<MonitoringStats> getStatsForNode(String nodeName,
                                          @Nullable LocalDateTime from,
                                          @Nullable LocalDateTime to);

    /**
     * Retrieves GPU monitoring stats for node.
     *
     * @param nodeName Cluster node name.
     * @param from Minimal date for collecting stats.
     * @param to Maximal date for collecting stats.
     * @param granularity the list of granularity levels to load GPU usages
     * @param squashCharts if specified charts shall be squashed into one
     * @return GPU usage statistics.
     */
    GpuMonitoringStats getGpuStatsForNode(String nodeName,
                                          @Nullable LocalDateTime from,
                                          @Nullable LocalDateTime to,
                                          List<GpuMetricsGranularity> granularity,
                                          boolean squashCharts);

    /**
     * Retrieves monitoring stats for node as input stream.
     *
     * @param nodeName Cluster node name.
     * @param from Minimal date for collecting stats.
     * @param to Maximal date for collecting stats.
     * @param interval period of stats collecting
     * @return stream, containing required information in .csv format
     */
    InputStream getStatsForNodeAsInputStream(String nodeName,
                                             @Nullable LocalDateTime from,
                                             @Nullable LocalDateTime to,
                                             Duration interval,
                                             MonitoringReportType type);

    /**
     * Retrieves number of bytes that available on a pod or node disk.
     *
     * @param nodeName Cluster node name.
     * @param podId
     * @param dockerImage of the container of the pod.
     * @return available bytes amount.
     */
    long getDiskSpaceAvailable(String nodeName,
                               String podId,
                               String dockerImage);

    NetworkEventFilter getPlatformNetworkStatsFilters();

    List<HistogramBin> getPlatformNetworkStats(HistogramType histogramType,
                                               LocalDateTime from, LocalDateTime to,
                                               Integer intervals, NetworkEventFilter filter);

    default List<PipelineRunPerformanceMetric> mapToRunPerformanceMetric(MonitoringStats stat) {
        final List<PipelineRunPerformanceMetric> result = new ArrayList<>();
        if (stat.getCpuUsage() != null) {
            result.add(
                PipelineRunPerformanceMetric.builder()
                        .type(PipelineRunPerformanceMetricsType.CPU)
                        .capacity(stat.getContainerSpec().getNumberOfCores())
                        .max(stat.getCpuUsage().getMax())
                        .avg(stat.getCpuUsage().getLoad()).build()
            );
        }
        if (stat.getMemoryUsage() != null) {
            result.add(
                PipelineRunPerformanceMetric.builder()
                        .type(PipelineRunPerformanceMetricsType.MEMORY)
                        .capacity(stat.getContainerSpec().getMaxMemory())
                        .max(stat.getMemoryUsage().getMax())
                        .avg(stat.getMemoryUsage().getUsage())
                        .build()
            );
        }
        return result;
    }
}
