/*
 * Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.manager.cluster.performancemonitoring.gcp;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dao.region.CloudRegionDao;
import com.epam.pipeline.entity.cluster.NodeInstance;
import com.epam.pipeline.entity.cluster.monitoring.MonitoringMetrics;
import com.epam.pipeline.entity.cluster.monitoring.MonitoringStats;
import com.epam.pipeline.entity.cluster.monitoring.gpu.GpuMetricsGranularity;
import com.epam.pipeline.entity.cluster.monitoring.gpu.GpuMonitoringStats;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.entity.region.GCPRegion;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.exception.ObjectNotFoundException;
import com.epam.pipeline.exception.PipelineException;
import com.epam.pipeline.manager.cloud.gcp.GCPClient;
import com.epam.pipeline.manager.cloud.gcp.wrappers.GCPInstancesClientWrapper;
import com.epam.pipeline.manager.cloud.gcp.wrappers.GCPMachineTypesClientWrapper;
import com.epam.pipeline.manager.cloud.gcp.wrappers.GCPMetricServiceClientWrapper;
import com.epam.pipeline.manager.cluster.KubernetesConstants;
import com.epam.pipeline.manager.cluster.MonitoringReportType;
import com.epam.pipeline.manager.cluster.NodesManager;
import com.epam.pipeline.manager.cluster.performancemonitoring.UsageMonitoringManager;
import com.epam.pipeline.manager.cluster.writer.AbstractMonitoringStatsWriter;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.utils.CommonUtils;
import com.google.cloud.compute.v1.Instance;
import com.google.cloud.compute.v1.MachineType;
import com.google.cloud.monitoring.v3.MetricServiceClient;
import com.google.monitoring.v3.Aggregation;
import com.google.monitoring.v3.ListTimeSeriesRequest;
import com.google.monitoring.v3.Point;
import com.google.monitoring.v3.ProjectName;
import com.google.monitoring.v3.TimeInterval;
import com.google.monitoring.v3.TimeSeries;
import com.google.protobuf.util.Timestamps;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.TriConsumer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static java.time.format.DateTimeFormatter.ISO_DATE_TIME;
import static java.time.format.DateTimeFormatter.ISO_INSTANT;

/**
 * Service to monitor and retrieve performance metrics for GCP-based nodes.
 */
@Service
@Slf4j
@ConditionalOnProperty(name = "monitoring.backend", havingValue = "gcp")
public class GCPMonitoringService implements UsageMonitoringManager {

    private static final int FALLBACK_INTERVALS_NUMBER = 10;
    private static final Duration FALLBACK_MONITORING_PERIOD = Duration.ofHours(1);
    private static final Duration FALLBACK_MINIMAL_INTERVAL = Duration.ofMinutes(1);
    private static final String METRIC_TEMPLATE = "metric.type = \"%s\" AND resource.type = \"gce_instance\" " +
            "AND resource.labels.instance_id = \"%s\"";
    private static final String DISK_USED = "agent.googleapis.com/disk/bytes_used";
    private static final String MEMORY_USED = "agent.googleapis.com/memory/bytes_used";
    private static final String GPU_UTILIZATION = "agent.googleapis.com/gpu/utilization";
    private static final String GPU_MEMORY_USED = "agent.googleapis.com/gpu/memory/bytes_used";
    private static final String CPU_UTILIZATION = "compute.googleapis.com/instance/cpu/utilization";
    private static final String TRAFFIC_SENT = "compute.googleapis.com/instance/network/sent_bytes_count";
    private static final String GPU_PROCESSES_UTILIZATION = "agent.googleapis.com/gpu/processes/utilization";
    private static final String TRAFFIC_RECEIVED = "compute.googleapis.com/instance/network/received_bytes_count";
    public static final String MODEL = "model";
    public static final String STATE = "state";
    public static final String DEVICE = "device";
    private static final String STATE_USED = "used";
    private static final String STATE_FREE = "free";
    public static final String GPU_NUMBER = "gpu_number";
    private static final String NETWORK_SUMMARY = "summary";
    public static final String MEMORY_STATE = "memory_state";
    public static final int MILLS_PER_SEC = 1000;
    private static final Long BYTES_PER_MB = 1024L * 1024L;
    public static final double PERCENT_MULTIPLIER = 100.0;

    private final GCPClient gcpClient;
    private final NodesManager nodesManager;
    private final MessageHelper messageHelper;
    private final CloudRegionDao cloudRegionDao;
    private final PreferenceManager preferenceManager;
    private final Map<MonitoringReportType, AbstractMonitoringStatsWriter> statsWriters;
    private final BiFunction<TimeSeries, String, String> keyRetriever = (ts, key) ->
        ts.getMetric().getLabelsMap().get(key);

    public GCPMonitoringService(final GCPClient gcpClient,
                                final NodesManager nodesManager,
                                final MessageHelper messageHelper,
                                final CloudRegionDao cloudRegionDao,
                                final PreferenceManager preferenceManager,
                                final List<AbstractMonitoringStatsWriter> writers) {
        this.gcpClient = gcpClient;
        this.nodesManager = nodesManager;
        this.messageHelper = messageHelper;
        this.cloudRegionDao = cloudRegionDao;
        this.preferenceManager = preferenceManager;
        this.statsWriters = CommonUtils.groupByKey(writers, AbstractMonitoringStatsWriter::getReportType);
    }

    @Override
    public List<MonitoringStats> getStatsForNode(final String nodeName,
                                                 @Nullable final LocalDateTime from,
                                                 @Nullable final LocalDateTime to) {
        final LocalDateTime start = Optional.ofNullable(from).orElseGet(() -> creationDate(nodeName));
        final LocalDateTime end = Optional.ofNullable(to).orElseGet(DateUtils::nowUTC);
        final Duration duration = calculateInterval(start, end);
        return end.isAfter(start) ? collectNodeStats(nodeName, start, end, duration) : Collections.emptyList();
    }

    @Override
    public InputStream getStatsForNodeAsInputStream(final String nodeName,
                                                    @Nullable final LocalDateTime from,
                                                    @Nullable final LocalDateTime to,
                                                    final Duration interval,
                                                    final MonitoringReportType type) {
        final LocalDateTime start = Optional.ofNullable(from).orElseGet(() -> creationDate(nodeName));
        final LocalDateTime end = Optional.ofNullable(to).orElseGet(DateUtils::nowUTC);
        final Duration minDuration = minimalDuration();
        final Duration adjustedDuration = interval.compareTo(minDuration) < 0 ? minDuration : interval;
        final AbstractMonitoringStatsWriter statsWriter = Optional.ofNullable(statsWriters.get(type))
                .orElseThrow(() -> new IllegalArgumentException(
                        messageHelper.getMessage(MessageConstants.ERROR_UNSUPPORTED_STATS_FILE_TYPE)));
        return statsWriter.convertStatsToFile(collectNodeStats(nodeName, start, end, adjustedDuration));
    }

    @Override
    public long getDiskSpaceAvailable(final String nodeName,
                                      final String podId,
                                      final String dockerImage) {
        return 0;
    }

    @Override
    public GpuMonitoringStats getGpuStatsForNode(final String nodeName,
                                                 @Nullable final LocalDateTime from,
                                                 @Nullable final LocalDateTime to,
                                                 final List<GpuMetricsGranularity> granularity,
                                                 final boolean squashCharts) {

        if (nodeName == null || nodeName.isEmpty()) {
            throw new IllegalArgumentException("Node name cannot be null or empty.");
        }
        if (squashCharts) {
            throw new IllegalArgumentException("Squash charts must be false.");
        }
        if (!granularity.equals(Collections.singletonList(GpuMetricsGranularity.ALL))) {
            throw new IllegalArgumentException("Granularity must be [ALL].");
        }

        final LocalDateTime start = Optional.ofNullable(from).orElseGet(() -> creationDate(nodeName));
        final LocalDateTime end = Optional.ofNullable(to).orElseGet(DateUtils::nowUTC);

        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("End time must be after start time.");
        }

        final Duration duration = calculateInterval(start, end);

        List<MonitoringStats> charts = getGpuCharts(nodeName, start, end, duration);

        if (!charts.isEmpty()) {
            final MonitoringStats global = new MonitoringStats();
            global.setStartTime(ISO_INSTANT.format(start.toInstant(ZoneOffset.UTC)));
            global.setEndTime(ISO_INSTANT.format(end.toInstant(ZoneOffset.UTC)));
            global.setMillsInPeriod(Duration.between(start, end).toMillis());
            global.setGpuDeviceName(charts.get(0).getGpuDeviceName());
            global.setGpuUsage(calculateGlobalGpuUsage(charts));

            return GpuMonitoringStats.builder()
                    .charts(charts)
                    .global(global)
                    .build();
        }

        return GpuMonitoringStats.builder().build();
    }
    
    private List<MonitoringStats> getGpuCharts(final String nodeName, final LocalDateTime start,
                                               final LocalDateTime end, final Duration duration) {
        final GCPRegion region = getGcpRegion();
        final VmDetails vmDetails = fetchVmDetails(nodeName, region);
        final long instanceId = vmDetails.getId();

        try (GCPMetricServiceClientWrapper metricsClient = gcpClient.buildMetricsClient(region)) {
            final TimeInterval interval = buildTimeInterval(start, end);

            final Aggregation mean = buildAggregation(duration, Aggregation.Aligner.ALIGN_MEAN);
            final Aggregation max = buildAggregation(duration, Aggregation.Aligner.ALIGN_MAX);
            final Aggregation min = buildAggregation(duration, Aggregation.Aligner.ALIGN_MIN);

            final Map<Long, MonitoringStats> statsMap = new HashMap<>();

            collectGpuStats(region, metricsClient, instanceId, interval, mean, max, min, statsMap, vmDetails);
            collectGpuMemoryStats(region, metricsClient, instanceId, interval, mean, max, min, statsMap, vmDetails);
            collectActiveGpus(region, metricsClient, instanceId, interval, mean, max, min, statsMap, vmDetails);
            calculateGpuMemoryUtilization(statsMap);
            calculateCrossGpuUsage(statsMap);

            return statsMap.values().stream()
                    .sorted(Comparator.comparing(MonitoringStats::getStartTime,
                            Comparator.comparing(this::parseMonitoringDateTime)))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("Failed to collect GPU stats for node {}: {}", nodeName, e.getMessage(), e);
            throw new PipelineException(e);
        }
    }

    private void collectGpuStats(final GCPRegion gcpRegion,
                                 final GCPMetricServiceClientWrapper client,
                                 final Long instanceId,
                                 final TimeInterval interval,
                                 final Aggregation meanAgg,
                                 final Aggregation maxAgg,
                                 final Aggregation minAgg,
                                 final Map<Long, MonitoringStats> statsMap,
                                 final VmDetails vmDetails) {
        final String filter = String.format(METRIC_TEMPLATE, GPU_UTILIZATION, instanceId);
        processMetric(gcpRegion, client, filter, interval, meanAgg, statsMap, vmDetails,
            (stats, point, timeSeries) -> {
                Map<String, MonitoringStats.GPUUsage> gpuDetails = stats.getGpuDetails();
                if (gpuDetails == null) {
                    gpuDetails = new HashMap<>();
                    stats.setGpuDetails(gpuDetails);
                }
                gpuDetails.computeIfAbsent(keyRetriever.apply(timeSeries, GPU_NUMBER),
                    k -> MonitoringStats.GPUUsage.builder()
                        .gpuMemoryUtilization(initMonitoringMetrics())
                        .gpuUtilization(initMonitoringMetrics())
                        .gpuMemoryFree(initMonitoringMetrics())
                        .gpuMemoryUsed(initMonitoringMetrics())
                        .activeGpus(initMonitoringMetrics())
                        .build())
                    .getGpuUtilization()
                    .setAverage(point.getValue().getDoubleValue());
                stats.setGpuDeviceName(keyRetriever.apply(timeSeries, MODEL));
            });
        processMetric(gcpRegion, client, filter, interval, maxAgg, statsMap, null,
            (stats, point, timeSeries) -> stats.getGpuDetails()
                .get(keyRetriever.apply(timeSeries, GPU_NUMBER))
                .getGpuUtilization().setMax(point.getValue().getDoubleValue()));

        processMetric(gcpRegion, client, filter, interval, minAgg, statsMap, null,
            (stats, point, timeSeries) -> stats.getGpuDetails()
                .get(keyRetriever.apply(timeSeries, GPU_NUMBER))
                .getGpuUtilization().setMin(point.getValue().getDoubleValue()));
    }

    private void collectGpuMemoryStats(GCPRegion gcpRegion, GCPMetricServiceClientWrapper client,
                                       long instanceId, TimeInterval interval,
                                       Aggregation meanAgg, Aggregation maxAgg, Aggregation minAgg,
                                       Map<Long, MonitoringStats> statsMap, VmDetails vmDetails) {
        final String filter = String.format(METRIC_TEMPLATE, GPU_MEMORY_USED, instanceId);

        processMetric(gcpRegion, client, filter, interval, meanAgg, statsMap, vmDetails, (stats, point, timeSeries) -> {
            final String gpuId = keyRetriever.apply(timeSeries, GPU_NUMBER);
            final MonitoringStats.GPUUsage gpuUsage = stats.getGpuDetails().get(gpuId);
            final String memoryState = keyRetriever.apply(timeSeries, MEMORY_STATE);
            final double value = point.getValue().hasDoubleValue() ?
                point.getValue().getDoubleValue() : point.getValue().getInt64Value();
            if (STATE_USED.equals(memoryState)) {
                gpuUsage.getGpuMemoryUsed().setAverage(value);
            } else if (STATE_FREE.equals(memoryState)) {
                gpuUsage.getGpuMemoryFree().setAverage(value);
            }
        });

        processMetric(gcpRegion, client, filter, interval, maxAgg, statsMap, null, (stats, point, timeSeries) -> {
            final String gpuId = keyRetriever.apply(timeSeries, GPU_NUMBER);
            final String memoryState = keyRetriever.apply(timeSeries, MEMORY_STATE);
            final double value = point.getValue().hasDoubleValue() ?
                    point.getValue().getDoubleValue() : point.getValue().getInt64Value();
            if (STATE_USED.equals(memoryState)) {
                stats.getGpuDetails().get(gpuId).getGpuMemoryUsed().setMax(value);
            } else if (STATE_FREE.equals(memoryState)) {
                stats.getGpuDetails().get(gpuId).getGpuMemoryFree().setMax(value);
            }
        });

        processMetric(gcpRegion, client, filter, interval, minAgg, statsMap, null, (stats, point, timeSeries) -> {
            final String gpuId = keyRetriever.apply(timeSeries, GPU_NUMBER);
            final String memoryState = keyRetriever.apply(timeSeries, MEMORY_STATE);
            final double value = point.getValue().hasDoubleValue() ?
                point.getValue().getDoubleValue() : point.getValue().getInt64Value();
            if (STATE_USED.equals(memoryState)) {
                stats.getGpuDetails().get(gpuId).getGpuMemoryUsed().setMin(value);
            } else if (STATE_FREE.equals(memoryState)) {
                stats.getGpuDetails().get(gpuId).getGpuMemoryFree().setMin(value);
            }
        });
    }

    private void calculateGpuMemoryUtilization(Map<Long, MonitoringStats> statsMap) {
        statsMap.forEach((timestamp, stats) -> {
            final Map<String, MonitoringStats.GPUUsage> gpuDetails = stats.getGpuDetails();
            if (gpuDetails == null) {
                return;
            }
            gpuDetails.forEach((gpuId, gpuUsage) -> {
                final MonitoringMetrics used = gpuUsage.getGpuMemoryUsed();
                final MonitoringMetrics free = gpuUsage.getGpuMemoryFree();
                final MonitoringMetrics utilization = MonitoringMetrics.builder().build();
                gpuUsage.setGpuMemoryUtilization(utilization);

                final double minTotal = used.getMin() + free.getMin();
                utilization.setMin(minTotal > 0 ? (used.getMin() / minTotal) * PERCENT_MULTIPLIER : 0.0);
                final double maxTotal = used.getMax() + free.getMax();
                utilization.setMax(maxTotal > 0 ? (used.getMax() / maxTotal) * PERCENT_MULTIPLIER : 0.0);
                final double avgTotal = used.getAverage() + free.getAverage();
                utilization.setAverage(avgTotal > 0 ? (used.getAverage() / avgTotal) * PERCENT_MULTIPLIER : 0.0);
            });
        });
    }

    private void collectActiveGpus(final GCPRegion gcpRegion,
                                   final GCPMetricServiceClientWrapper client,
                                   final long instanceId,
                                   final TimeInterval interval,
                                   final Aggregation meanAgg,
                                   final Aggregation maxAgg,
                                   final Aggregation minAgg,
                                   final Map<Long, MonitoringStats> statsMap,
                                   final VmDetails vmDetails) {
        final String filter = String.format(METRIC_TEMPLATE, GPU_PROCESSES_UTILIZATION, instanceId);

        //cumulative avg
        processMetric(gcpRegion, client, filter, interval, meanAgg, statsMap, vmDetails, (stats, point, timeSeries) -> {
            String gpuId = keyRetriever.apply(timeSeries, GPU_NUMBER);
            MonitoringMetrics gpuMetric = stats.getGpuDetails().get(gpuId).getActiveGpus();
            gpuMetric.setAverage(gpuMetric.getAverage() + point.getValue().getDoubleValue());
        });

        //cumulative max
        processMetric(gcpRegion, client, filter, interval, maxAgg, statsMap, null, (stats, point, timeSeries) -> {
            String gpuId = keyRetriever.apply(timeSeries, GPU_NUMBER);
            MonitoringMetrics gpuMetric = stats.getGpuDetails().get(gpuId).getActiveGpus();
            gpuMetric.setMax(gpuMetric.getMax() + point.getValue().getDoubleValue());
        });

        //cumulative min
        processMetric(gcpRegion, client, filter, interval, minAgg, statsMap, null, (stats, point, timeSeries) -> {
            String gpuId = keyRetriever.apply(timeSeries, GPU_NUMBER);
            MonitoringMetrics gpuMetric = stats.getGpuDetails().get(gpuId).getActiveGpus();
            gpuMetric.setMin(gpuMetric.getMin() + point.getValue().getDoubleValue());
        });
    }

    private MonitoringStats.GPUUsage calculateGlobalGpuUsage(List<MonitoringStats> statsList) {
        final MonitoringStats.GPUUsage globalUsage = MonitoringStats.GPUUsage.builder()
                .activeGpus(initMonitoringMetrics())
                .gpuUtilization(initMonitoringMetrics())
                .gpuMemoryUtilization(initMonitoringMetrics())
                .build();

        if (statsList.isEmpty()) {
            return globalUsage;
        }

        globalUsage.getGpuUtilization().setAverage(
                statsList.stream()
                        .mapToDouble(s -> s.getGpuUsage().getGpuUtilization().getAverage())
                        .average()
                        .orElse(0.0));
        globalUsage.getGpuMemoryUtilization().setAverage(
                statsList.stream()
                        .mapToDouble(s -> s.getGpuUsage().getGpuMemoryUtilization().getAverage())
                        .average()
                        .orElse(0.0));
        globalUsage.getActiveGpus().setAverage(
                statsList.stream()
                        .mapToDouble(s -> s.getGpuUsage().getActiveGpus().getAverage())
                        .average()
                        .orElse(0.0));

        globalUsage.getGpuUtilization().setMin(
                statsList.stream()
                        .mapToDouble(s -> s.getGpuUsage().getGpuUtilization().getMin())
                        .min()
                        .orElse(0.0));
        globalUsage.getGpuUtilization().setMax(
                statsList.stream()
                        .mapToDouble(s -> s.getGpuUsage().getGpuUtilization().getMax())
                        .max()
                        .orElse(0.0));

        globalUsage.getGpuMemoryUtilization().setMin(
                statsList.stream()
                        .mapToDouble(s -> s.getGpuUsage().getGpuMemoryUtilization().getMin())
                        .min()
                        .orElse(0.0));
        globalUsage.getGpuMemoryUtilization().setMax(
                statsList.stream()
                        .mapToDouble(s -> s.getGpuUsage().getGpuMemoryUtilization().getMax())
                        .max()
                        .orElse(0.0));

        globalUsage.getActiveGpus().setMin(
                statsList.stream()
                        .mapToDouble(s -> s.getGpuUsage().getActiveGpus().getMin())
                        .min()
                        .orElse(0.0));
        globalUsage.getActiveGpus().setMax(
                statsList.stream()
                        .mapToDouble(s -> s.getGpuUsage().getActiveGpus().getMax())
                        .max()
                        .orElse(0.0));

        return globalUsage;
    }

    private void calculateCrossGpuUsage(Map<Long, MonitoringStats> statsMap) {
        statsMap.forEach((timestamp, stats) -> {
            final Map<String, MonitoringStats.GPUUsage> gpuDetails = stats.getGpuDetails();
            if (gpuDetails == null || gpuDetails.isEmpty()) {
                stats.setGpuUsage(MonitoringStats.GPUUsage.builder()
                        .gpuUtilization(initMonitoringMetrics())
                        .gpuMemoryUtilization(initMonitoringMetrics())
                        .activeGpus(initMonitoringMetrics())
                        .build());
                return;
            }

            final MonitoringStats.GPUUsage crossGpuUsage = MonitoringStats.GPUUsage.builder()
                    .gpuUtilization(initMonitoringMetrics())
                    .gpuMemoryUtilization(initMonitoringMetrics())
                    .activeGpus(initMonitoringMetrics())
                    .build();
            stats.setGpuUsage(crossGpuUsage);

            crossGpuUsage.getGpuUtilization().setAverage(
                    gpuDetails.values().stream()
                            .mapToDouble(g -> g.getGpuUtilization().getAverage())
                            .average()
                            .orElse(0.0));
            crossGpuUsage.getGpuMemoryUtilization().setAverage(
                    gpuDetails.values().stream()
                            .mapToDouble(g -> g.getGpuMemoryUtilization().getAverage())
                            .average()
                            .orElse(0.0));
            crossGpuUsage.getActiveGpus().setAverage(
                    gpuDetails.values().stream()
                            .mapToDouble(g -> g.getActiveGpus().getAverage())
                            .average()
                            .orElse(0.0));

            crossGpuUsage.getGpuUtilization().setMin(
                    gpuDetails.values().stream()
                            .mapToDouble(g -> g.getGpuUtilization().getMin())
                            .min()
                            .orElse(0.0));
            crossGpuUsage.getGpuUtilization().setMax(
                    gpuDetails.values().stream()
                            .mapToDouble(g -> g.getGpuUtilization().getMax())
                            .max()
                            .orElse(0.0));

            crossGpuUsage.getGpuMemoryUtilization().setMin(
                    gpuDetails.values().stream()
                            .mapToDouble(g -> g.getGpuMemoryUtilization().getMin())
                            .min()
                            .orElse(0.0));
            crossGpuUsage.getGpuMemoryUtilization().setMax(
                    gpuDetails.values().stream()
                            .mapToDouble(g -> g.getGpuMemoryUtilization().getMax())
                            .max()
                            .orElse(0.0));

            crossGpuUsage.getActiveGpus().setMin(
                    gpuDetails.values().stream()
                            .mapToDouble(g -> g.getActiveGpus().getMin())
                            .min()
                            .orElse(0.0));
            crossGpuUsage.getActiveGpus().setMax(
                    gpuDetails.values().stream()
                            .mapToDouble(g -> g.getActiveGpus().getMax())
                            .max()
                            .orElse(0.0));
        });
    }

    protected List<MonitoringStats> collectNodeStats(final String nodeName,
                                                     final LocalDateTime start,
                                                     final LocalDateTime end,
                                                     final Duration duration) {
        final GCPRegion region = getGcpRegion();
        final VmDetails vmDetails = fetchVmDetails(nodeName, region);
        final long instanceId = vmDetails.getId();

        try (GCPMetricServiceClientWrapper metricsClient = gcpClient.buildMetricsClient(region)) {
            final TimeInterval timeInterval = buildTimeInterval(start, end);
            final Aggregation meanAgg = buildAggregation(duration, Aggregation.Aligner.ALIGN_MEAN);
            final Aggregation maxAgg = buildAggregation(duration, Aggregation.Aligner.ALIGN_MAX);
            final Aggregation sumAgg = buildAggregation(duration, Aggregation.Aligner.ALIGN_SUM);

            final Map<Long, MonitoringStats> statsMap = new HashMap<>();
            collectCpuStats(region, metricsClient, instanceId, timeInterval, meanAgg, maxAgg, statsMap, vmDetails);
            collectMemoryStats(region, metricsClient, instanceId, timeInterval, meanAgg, maxAgg, statsMap, vmDetails);
            collectDiskStats(region, metricsClient, instanceId, timeInterval, meanAgg, statsMap);
            collectNetworkStats(region, metricsClient, instanceId, timeInterval, sumAgg, statsMap);

            return statsMap.values().stream()
                    .sorted(Comparator.comparing(MonitoringStats::getStartTime,
                            Comparator.comparing(this::parseMonitoringDateTime)))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("Failed to collect stats for node {}: {}", nodeName, e.getMessage(), e);
            throw new PipelineException(e);
        }
    }

    private void collectCpuStats(final GCPRegion gcpRegion,
                                 final GCPMetricServiceClientWrapper client,
                                 final Long instanceId,
                                 final TimeInterval interval,
                                 final Aggregation meanAgg,
                                 final Aggregation maxAgg,
                                 final Map<Long, MonitoringStats> statsMap,
                                 final VmDetails vmDetails) {
        final String filter = String.format(METRIC_TEMPLATE, CPU_UTILIZATION, instanceId);
        processMetric(gcpRegion, client, filter, interval, meanAgg, statsMap, vmDetails,
            (stats, point, timeSeries) -> {
                MonitoringStats.CPUUsage cpuUsage = stats.getCpuUsage();
                if (cpuUsage == null) {
                    cpuUsage = new MonitoringStats.CPUUsage();
                    stats.setCpuUsage(cpuUsage);
                }
                cpuUsage.setLoad(point.getValue().getDoubleValue());
            });
        processMetric(gcpRegion, client, filter, interval, maxAgg, statsMap, null,
            (stats, point, timeSeries) -> stats.getCpuUsage().setMax(point.getValue().getDoubleValue()));
    }
    private MonitoringMetrics initMonitoringMetrics() {
        return MonitoringMetrics.builder().average(0.0).max(0.0).min(0.0).build();
    }

    private void collectMemoryStats(final GCPRegion gcpRegion,
                                    final GCPMetricServiceClientWrapper client,
                                    final Long instanceId,
                                    final TimeInterval interval,
                                    final Aggregation meanAgg,
                                    final Aggregation maxAgg,
                                    final Map<Long, MonitoringStats> statsMap,
                                    final VmDetails vmDetails) {
        final String filter = String.format(METRIC_TEMPLATE, MEMORY_USED, instanceId);
        final long capacity = vmDetails.getMemoryMb() * BYTES_PER_MB;
        processMetric(gcpRegion, client, filter, interval, meanAgg, statsMap, vmDetails,
            (stats, point, timeSeries) -> {
                MonitoringStats.MemoryUsage memoryUsage = stats.getMemoryUsage();
                if (memoryUsage == null) {
                    memoryUsage = new MonitoringStats.MemoryUsage();
                    memoryUsage.setCapacity(capacity);
                    stats.setMemoryUsage(memoryUsage);
                }
                if (STATE_USED.equals(keyRetriever.apply(timeSeries, STATE))) {
                    memoryUsage.setUsage((long) point.getValue().getDoubleValue());
                }
            });
        processMetric(gcpRegion, client, filter, interval, maxAgg, statsMap, vmDetails,
            (stats, point, timeSeries) -> {
                if (STATE_USED.equals(keyRetriever.apply(timeSeries, STATE))) {
                    stats.getMemoryUsage().setMax((long) point.getValue().getDoubleValue());
                }
            });
    }

    private void collectDiskStats(final GCPRegion gcpRegion,
                                  final GCPMetricServiceClientWrapper client,
                                  final Long instanceId,
                                  final TimeInterval interval,
                                  final Aggregation meanAgg,
                                  final Map<Long, MonitoringStats> statsMap) {
        final String filter = String.format(METRIC_TEMPLATE, DISK_USED, instanceId);
        processMetric(gcpRegion, client, filter, interval, meanAgg, statsMap, null,
            (stats, point, timeSeries) -> {
                MonitoringStats.DisksUsage disksUsage = stats.getDisksUsage();
                if (disksUsage == null) {
                    disksUsage = new MonitoringStats.DisksUsage();
                    stats.setDisksUsage(disksUsage);
                }
                final String device = keyRetriever.apply(timeSeries, DEVICE);
                final String state = keyRetriever.apply(timeSeries, STATE);
                final MonitoringStats.DisksUsage.DiskStats diskStats = disksUsage.getStatsByDevices()
                        .computeIfAbsent(device, k -> new MonitoringStats.DisksUsage.DiskStats());
                final long value = (long) point.getValue().getDoubleValue();
                diskStats.setCapacity(diskStats.getCapacity() + value);
                if (STATE_FREE.equals(state)) {
                    diskStats.setUsableSpace(value);
                }
            });
    }

    private void collectNetworkStats(final GCPRegion gcpRegion,
                                     final GCPMetricServiceClientWrapper client,
                                     final Long instanceId,
                                     final TimeInterval interval,
                                     final Aggregation meanAgg,
                                     final Map<Long, MonitoringStats> statsMap) {
        final String receivedFilter = String.format(METRIC_TEMPLATE, TRAFFIC_RECEIVED, instanceId);
        processMetric(gcpRegion, client, receivedFilter, interval, meanAgg, statsMap, null,
            (stats, point, timeSeries) -> {
                MonitoringStats.NetworkUsage networkUsage = stats.getNetworkUsage();
                if (networkUsage == null) {
                    networkUsage = new MonitoringStats.NetworkUsage();
                    stats.setNetworkUsage(networkUsage);
                }
                final MonitoringStats.NetworkUsage.NetworkStats networkStats = networkUsage.getStatsByInterface()
                        .computeIfAbsent(NETWORK_SUMMARY, k -> new MonitoringStats.NetworkUsage.NetworkStats());
                networkStats.setRxBytes(point.getValue().getInt64Value());
            });

        final String sentFilter = String.format(METRIC_TEMPLATE, TRAFFIC_SENT, instanceId);
        processMetric(gcpRegion, client, sentFilter, interval, meanAgg, statsMap, null,
            (stats, point, timeSeries) -> {
                MonitoringStats.NetworkUsage networkUsage = stats.getNetworkUsage();
                if (networkUsage == null) {
                    networkUsage = new MonitoringStats.NetworkUsage();
                    stats.setNetworkUsage(networkUsage);
                }
                final MonitoringStats.NetworkUsage.NetworkStats networkStats = networkUsage.getStatsByInterface()
                        .computeIfAbsent(NETWORK_SUMMARY, k -> new MonitoringStats.NetworkUsage.NetworkStats());
                networkStats.setTxBytes(point.getValue().getInt64Value());
            });
    }

    private void processMetric(final GCPRegion gcpRegion,
                               final GCPMetricServiceClientWrapper client,
                               final String filter,
                               final TimeInterval interval,
                               final Aggregation agg,
                               final Map<Long, MonitoringStats> statsMap,
                               final VmDetails vmDetails,
                               final TriConsumer<MonitoringStats, Point, TimeSeries> updater) {
        final ListTimeSeriesRequest request = buildTimeSeriesRequest(gcpRegion.getProject(), filter, interval, agg);
        final MetricServiceClient.ListTimeSeriesPagedResponse response = client.listTimeSeries(request);
        response.getPage().iterateAll().forEach(timeSeries -> {
            final TimeSeries finalTimeSeries = timeSeries;
            timeSeries.getPointsList().forEach(point -> {
                final long endTimeSec = point.getInterval().getEndTime().getSeconds();
                final MonitoringStats stat = statsMap.computeIfAbsent(endTimeSec,
                    k -> buildMonitoringStats(
                        point, agg.getAlignmentPeriod().getSeconds() * MILLS_PER_SEC, vmDetails));
                updater.accept(stat, point, finalTimeSeries);
            });
        });
    }

    private MonitoringStats buildMonitoringStats(final Point point,
                                                 final Long millsInPeriod,
                                                 final VmDetails vmDetails) {
        final MonitoringStats stat = new MonitoringStats();
        final Long endTimeSec = point.getInterval().getEndTime().getSeconds();
        stat.setMillsInPeriod(millsInPeriod);
        stat.setStartTime(ISO_INSTANT.format(Instant.ofEpochSecond(endTimeSec - millsInPeriod / MILLS_PER_SEC)));
        stat.setEndTime(ISO_INSTANT.format(Instant.ofEpochSecond(endTimeSec)));
        final MonitoringStats.ContainerSpec containerSpec = new MonitoringStats.ContainerSpec();
        if (vmDetails != null) {
            containerSpec.setNumberOfCores(vmDetails.getVcpus());
            containerSpec.setMaxMemory(vmDetails.getMemoryMb() * BYTES_PER_MB);
        }
        stat.setContainerSpec(containerSpec);
        return stat;
    }

    private VmDetails fetchVmDetails(final String nodeName,
                                     final GCPRegion gcpRegion) {
        try {
            final String zone = nodesManager.getNode(nodeName).getRegion();
            return getVMDetails(gcpRegion.getProject(), nodeName, zone, gcpRegion);
        } catch (IOException e) {
            log.error("Failed to retrieve GCP Compute Instance details for node {}: {}", nodeName, e.getMessage(), e);
            throw new PipelineException(e);
        }
    }

    private VmDetails getVMDetails(final String projectId,
                                   final String instanceName,
                                   final String zone,
                                   final GCPRegion gcpRegion) throws IOException {
        try (GCPInstancesClientWrapper instancesClient = gcpClient.buildInstancesClient(gcpRegion);
             GCPMachineTypesClientWrapper machineTypesClient = gcpClient.buildMachineTypesClient(gcpRegion)) {
            final Instance instance = instancesClient.get(projectId, zone, instanceName);
            final String machineTypeUrl = instance.getMachineType();
            final String[] parts = machineTypeUrl.split("/");
            final String machineTypeName = parts[parts.length - 1];
            final MachineType machineType = machineTypesClient.get(projectId, zone, machineTypeName);
            return new VmDetails(instance.getId(), machineType.getGuestCpus(), machineType.getMemoryMb());
        }
    }

    private GCPRegion getGcpRegion() {
        return cloudRegionDao.loadDefaultRegion()
                .filter(r -> CloudProvider.GCP == r.getProvider())
                .map(GCPRegion.class::cast)
                .orElseThrow(() -> new ObjectNotFoundException("No default GCP region configured"));
    }

    private TimeInterval buildTimeInterval(final LocalDateTime start,
                                           final LocalDateTime end) {
        return TimeInterval.newBuilder()
                .setStartTime(Timestamps.fromSeconds(start.toEpochSecond(ZoneOffset.UTC)))
                .setEndTime(Timestamps.fromSeconds(end.toEpochSecond(ZoneOffset.UTC)))
                .build();
    }

    private Aggregation buildAggregation(final Duration duration,
                                         final Aggregation.Aligner aligner) {
        return Aggregation.newBuilder()
                .setAlignmentPeriod(com.google.protobuf.Duration.newBuilder().setSeconds(duration.getSeconds()).build())
                .setPerSeriesAligner(aligner)
                .build();
    }

    private ListTimeSeriesRequest buildTimeSeriesRequest(final String projectId,
                                                         final String filter,
                                                         final TimeInterval interval,
                                                         final Aggregation agg) {
        return ListTimeSeriesRequest.newBuilder()
                .setName(ProjectName.of(projectId).toString())
                .setFilter(filter)
                .setInterval(interval)
                .setView(ListTimeSeriesRequest.TimeSeriesView.FULL)
                .setAggregation(agg)
                .build();
    }

    private Duration calculateInterval(final LocalDateTime start,
                                       final LocalDateTime end) {
        final Duration requested = Duration.between(start, end).dividedBy(Math.max(1, numberOfIntervals()));
        final Duration minimal = minimalDuration();
        return requested.compareTo(minimal) < 0 ? minimal : requested;
    }

    private LocalDateTime creationDate(final String nodeName) {
        return nodesManager.findNode(nodeName)
                .map(NodeInstance::getCreationTimestamp)
                .map(it -> LocalDateTime.parse(it, KubernetesConstants.KUBE_DATE_FORMATTER))
                .orElseGet(this::fallbackMonitoringStart);
    }

    private LocalDateTime fallbackMonitoringStart() {
        return DateUtils.nowUTC().minus(FALLBACK_MONITORING_PERIOD);
    }

    private Duration minimalDuration() {
        return Optional.of(SystemPreferences.CLUSTER_MONITORING_GCP_MINIMAL_INTERVAL)
                .map(preferenceManager::getPreference)
                .map(Duration::ofMillis)
                .orElse(FALLBACK_MINIMAL_INTERVAL);
    }

    private int numberOfIntervals() {
        return Optional.of(SystemPreferences.CLUSTER_MONITORING_GCP_INTERVALS_NUMBER)
                .map(preferenceManager::getPreference)
                .orElse(FALLBACK_INTERVALS_NUMBER);
    }

    private LocalDateTime parseMonitoringDateTime(final String dateTimeString) {
        return LocalDateTime.parse(dateTimeString, ISO_DATE_TIME);
    }

    @Data
    @AllArgsConstructor
    static class VmDetails {
        private final Long id;
        private final Integer vcpus;
        private final Integer memoryMb;
    }
}