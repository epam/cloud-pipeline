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

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dao.monitoring.MonitoringESDao;
import com.epam.pipeline.dao.monitoring.metricrequester.AbstractMetricRequester;
import com.epam.pipeline.dao.monitoring.metricrequester.GPUAggregationRequester;
import com.epam.pipeline.dao.monitoring.metricrequester.GPUDetailsRequester;
import com.epam.pipeline.dao.monitoring.metricrequester.HeapsterElasticRestHighLevelClient;
import com.epam.pipeline.entity.cluster.NodeInstance;
import com.epam.pipeline.entity.cluster.monitoring.ELKUsageMetric;
import com.epam.pipeline.entity.cluster.monitoring.MonitoringStats;
import com.epam.pipeline.entity.cluster.monitoring.gpu.GpuMetricsGranularity;
import com.epam.pipeline.entity.cluster.monitoring.gpu.GpuMonitoringStats;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.cluster.KubernetesConstants;
import com.epam.pipeline.manager.cluster.MonitoringReportType;
import com.epam.pipeline.manager.cluster.NodesManager;
import com.epam.pipeline.manager.cluster.writer.AbstractMonitoringStatsWriter;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.utils.CommonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.elasticsearch.ElasticsearchStatusException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Slf4j
@ConditionalOnProperty(name = "monitoring.backend", havingValue = "elastic")
public class ESMonitoringManager implements UsageMonitoringManager {

    private static final ELKUsageMetric[] MONITORING_METRICS = {ELKUsageMetric.CPU, ELKUsageMetric.MEM,
        ELKUsageMetric.FS, ELKUsageMetric.NETWORK};
    private static final Duration FALLBACK_MONITORING_PERIOD = Duration.ofHours(1);
    private static final Duration FALLBACK_MINIMAL_INTERVAL = Duration.ofMinutes(1);
    private static final int FALLBACK_INTERVALS_NUMBER = 10;
    private static final int TWO = 2;
    private static final String SWAP_FILESYSTEM = "tmpfs";
    private static final String HEAPSTER_INDEX_NAME_TOKEN = "heapster-";
    private static final String GPU_STAT_INDEX_NAME_TOKEN = "cp-gpu-monitor-";

    private final HeapsterElasticRestHighLevelClient client;
    private final MonitoringESDao monitoringDao;
    private final MessageHelper messageHelper;
    private final PreferenceManager preferenceManager;
    private final NodesManager nodesManager;
    private final Map<MonitoringReportType, AbstractMonitoringStatsWriter> statsWriters;

    public ESMonitoringManager(final HeapsterElasticRestHighLevelClient client,
                               final MonitoringESDao monitoringDao,
                               final MessageHelper messageHelper,
                               final PreferenceManager preferenceManager,
                               final NodesManager nodesManager,
                               final List<AbstractMonitoringStatsWriter> writers) {
        this.client = client;
        this.monitoringDao = monitoringDao;
        this.messageHelper = messageHelper;
        this.preferenceManager = preferenceManager;
        this.nodesManager = nodesManager;
        this.statsWriters = CommonUtils.groupByKey(writers, AbstractMonitoringStatsWriter::getReportType);
    }

    @Override
    public List<MonitoringStats> getStatsForNode(final String nodeName, final LocalDateTime from,
                                                 final LocalDateTime to) {
        final LocalDateTime requestedStart = Optional.ofNullable(from).orElseGet(() -> creationDate(nodeName));
        final LocalDateTime oldestMonitoring = oldestMonitoringDate(HEAPSTER_INDEX_NAME_TOKEN);
        final LocalDateTime start = requestedStart.isAfter(oldestMonitoring) ? requestedStart : oldestMonitoring;
        final LocalDateTime end = Optional.ofNullable(to).orElseGet(DateUtils::nowUTC);
        final Duration interval = interval(start, end);
        return end.isAfter(start) && end.isAfter(oldestMonitoring)
                ? getStats(nodeName, start, end, interval)
                : Collections.emptyList();
    }

    @Override
    public GpuMonitoringStats getGpuStatsForNode(final String nodeName, final LocalDateTime from,
                                                 final LocalDateTime to,
                                                 final List<GpuMetricsGranularity> granularity,
                                                 final boolean squashCharts) {
        final LocalDateTime requestedStart = Optional.ofNullable(from).orElseGet(() -> creationDate(nodeName));
        final LocalDateTime oldestMonitoring = oldestMonitoringDate(GPU_STAT_INDEX_NAME_TOKEN);
        final LocalDateTime start = requestedStart.isAfter(oldestMonitoring) ? requestedStart : oldestMonitoring;
        final LocalDateTime end = Optional.ofNullable(to).orElseGet(DateUtils::nowUTC);
        final Duration totalDuration = Duration.between(start, end);
        final Duration interval = squashCharts ? totalDuration : interval(start, end);
        return end.isAfter(start) && end.isAfter(oldestMonitoring)
                ? getGpuStats(nodeName, start, end, interval, totalDuration, granularity)
                : GpuMonitoringStats.builder().build();
    }

    @Override
    public InputStream getStatsForNodeAsInputStream(final String nodeName,
                                                    final LocalDateTime from,
                                                    final LocalDateTime to,
                                                    final Duration interval,
                                                    final MonitoringReportType type) {
        final LocalDateTime requestedStart = Optional.ofNullable(from).orElseGet(() -> creationDate(nodeName));
        final LocalDateTime oldestMonitoring = oldestMonitoringDate(HEAPSTER_INDEX_NAME_TOKEN);
        final LocalDateTime start = requestedStart.isAfter(oldestMonitoring) ? requestedStart : oldestMonitoring;
        final LocalDateTime end = Optional.ofNullable(to).orElseGet(DateUtils::nowUTC);
        final Duration minDuration = minimalDuration();
        final Duration adjustedDuration = interval.compareTo(minDuration) < 0
                                          ? minDuration
                                          : interval;
        final AbstractMonitoringStatsWriter statsWriter = Optional.ofNullable(statsWriters.get(type))
            .orElseThrow(() -> new IllegalArgumentException(
                messageHelper.getMessage(MessageConstants.ERROR_UNSUPPORTED_STATS_FILE_TYPE)));
        return statsWriter.convertStatsToFile(getStats(nodeName, start, end, adjustedDuration));
    }

    @Override
    public long getDiskSpaceAvailable(final String nodeName, final String podId, final String dockerImage) {
        final Duration duration = minimalDuration();
        final List<MonitoringStats> monitoringStats = AbstractMetricRequester
                .getStatsRequester(ELKUsageMetric.FS, client)
                .requestStats(nodeName,
                        DateUtils.nowUTC().minus(duration.multipliedBy(Math.max(numberOfIntervals(), TWO))),
                        DateUtils.nowUTC(),
                        duration
                );
        Assert.isTrue(CollectionUtils.isNotEmpty(monitoringStats),
                messageHelper.getMessage(MessageConstants.ERROR_GET_NODE_STAT, nodeName));
        final MonitoringStats.DisksUsage.DiskStats diskStats = monitoringStats.stream()
                .collect(Collectors.groupingBy(MonitoringStats::getStartTime, Collectors.reducing(this::mergeStats)))
                .values().stream()
                .filter(Optional::isPresent)
                .map(Optional::get)
                .max(Comparator.comparing(MonitoringStats::getStartTime,
                        Comparator.comparing(this::asMonitoringDateTime)))
                .map(MonitoringStats::getDisksUsage)
                .map(MonitoringStats.DisksUsage::getStatsByDevices)
                .orElseThrow(() -> new IllegalArgumentException(
                        messageHelper.getMessage(MessageConstants.ERROR_GET_NODE_STAT, nodeName)))
                .entrySet().stream()
                .filter(it -> !SWAP_FILESYSTEM.equalsIgnoreCase(it.getKey()))
                .map(Map.Entry::getValue)
                .reduce(new MonitoringStats.DisksUsage.DiskStats(), this::merged);
        return diskStats.getCapacity() - diskStats.getUsableSpace();
    }

    private LocalDateTime oldestMonitoringDate(final String... indexPrefixes) {
        return monitoringDao.oldestIndexDate(indexPrefixes).orElseGet(this::fallbackMonitoringStart);
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

    private GpuMonitoringStats getGpuStats(final String nodeName, final LocalDateTime start, final LocalDateTime end,
                                           final Duration interval, final Duration totalDuration,
                                           final List<GpuMetricsGranularity> granularity) {
        try {
            final GPUAggregationRequester aggregationRequester = new GPUAggregationRequester(client);
            final GpuMonitoringStats.GpuMonitoringStatsBuilder results = GpuMonitoringStats.builder();
            if (GpuMetricsGranularity.hasGlobal(granularity)) {
                results.global(aggregationRequester.requestStats(nodeName, start, end, totalDuration).stream()
                        .findFirst()
                        .flatMap(stats -> statsWithinRegion(stats, start, end, totalDuration))
                        .orElse(null));
            }
            return results.charts(getGpuCharts(granularity, aggregationRequester, interval, nodeName, start, end))
                    .build();
        } catch (ElasticsearchStatusException e) {
            if (e.getDetailedMessage().contains("index_not_found_exception")) {
                log.error("GPU monitor index doesn't exist for node '{}'", nodeName, e);
                return GpuMonitoringStats.builder().build();
            }
            throw e;
        }
    }

    private List<MonitoringStats> getStats(final String nodeName, final LocalDateTime start, final LocalDateTime end,
                                           final Duration interval) {
        return Stream.of(MONITORING_METRICS)
                .map(it -> AbstractMetricRequester.getStatsRequester(it, client))
                .map(it -> it.requestStats(nodeName, start, end, interval))
                .flatMap(List::stream)
                .collect(Collectors.groupingBy(MonitoringStats::getStartTime, Collectors.reducing(this::mergeStats)))
                .values()
                .stream()
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(this::isMonitoringStatsComplete)
                .map(stats -> statsWithinRegion(stats, start, end, interval))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .sorted(Comparator.comparing(MonitoringStats::getStartTime,
                        Comparator.comparing(this::asMonitoringDateTime)))
                .collect(Collectors.toList());
    }

    private Duration interval(final LocalDateTime start, final LocalDateTime end) {
        final Duration requested = Duration.between(start, end).dividedBy(Math.max(1, numberOfIntervals() - 1));
        final Duration minimal = minimalDuration();
        return requested.compareTo(minimal) < 0 ? minimal : requested;
    }

    private Duration minimalDuration() {
        return Optional.of(SystemPreferences.CLUSTER_MONITORING_ELASTIC_MINIMAL_INTERVAL)
                .map(preferenceManager::getPreference)
                .map(Duration::ofMillis)
                .orElse(FALLBACK_MINIMAL_INTERVAL);
    }

    private int numberOfIntervals() {
        return Optional.of(SystemPreferences.CLUSTER_MONITORING_ELASTIC_INTERVALS_NUMBER)
                .map(preferenceManager::getPreference)
                .orElse(FALLBACK_INTERVALS_NUMBER);
    }

    private MonitoringStats mergeStats(final MonitoringStats first, final MonitoringStats second) {
        final Optional<MonitoringStats> original = Optional.of(first);
        first.setCpuUsage(original.map(MonitoringStats::getCpuUsage).orElseGet(second::getCpuUsage));
        first.setGpuUsage(original.map(MonitoringStats::getGpuUsage).orElseGet(second::getGpuUsage));
        first.setMemoryUsage(original.map(MonitoringStats::getMemoryUsage).orElseGet(second::getMemoryUsage));
        first.setNetworkUsage(original.map(MonitoringStats::getNetworkUsage).orElseGet(second::getNetworkUsage));
        if (first.getDisksUsage() != null && second.getDisksUsage() != null) {
            if (first.getDisksUsage().getStatsByDevices() != null
                    && second.getDisksUsage().getStatsByDevices() != null) {
                first.getDisksUsage().getStatsByDevices().putAll(second.getDisksUsage().getStatsByDevices());
            } else {
                first.getDisksUsage().setStatsByDevices(Optional.ofNullable(first.getDisksUsage().getStatsByDevices())
                        .orElse(second.getDisksUsage().getStatsByDevices()));
            }
            first.getDisksUsage().getStatsByDevices().putAll(second.getDisksUsage().getStatsByDevices());
        } else {
            first.setDisksUsage(original.map(MonitoringStats::getDisksUsage).orElseGet(second::getDisksUsage));
        }
        if (first.getContainerSpec() != null && second.getContainerSpec() != null) {
            final long maxMemory = Math.max(first.getContainerSpec().getMaxMemory(),
                    second.getContainerSpec().getMaxMemory());
            final int numberOfCores = Math.max(first.getContainerSpec().getNumberOfCores(),
                    second.getContainerSpec().getNumberOfCores());
            first.getContainerSpec().setMaxMemory(maxMemory);
            first.getContainerSpec().setNumberOfCores(numberOfCores);
        } else {
            first.setContainerSpec(original.map(MonitoringStats::getContainerSpec).orElseGet(second::getContainerSpec));
        }
        return first;
    }

    private MonitoringStats mergeGpuStats(final MonitoringStats first, final MonitoringStats second) {
        final Optional<MonitoringStats> original = Optional.of(first);
        first.setGpuUsage(original.map(MonitoringStats::getGpuUsage).orElseGet(second::getGpuUsage));
        first.setGpuDetails(original.map(MonitoringStats::getGpuDetails).orElseGet(second::getGpuDetails));
        return first;
    }

    private boolean isMonitoringStatsComplete(final MonitoringStats monitoringStats) {
        return monitoringStats.getCpuUsage() != null
                && monitoringStats.getMemoryUsage() != null
                && monitoringStats.getDisksUsage() != null
                && monitoringStats.getNetworkUsage() != null;
    }

    private LocalDateTime asMonitoringDateTime(final String dateTimeString) {
        return LocalDateTime.parse(dateTimeString, MonitoringConstants.FORMATTER);
    }

    private Optional<MonitoringStats> statsWithinRegion(final MonitoringStats stats, final LocalDateTime regionStart,
                                              final LocalDateTime regionEnd, final Duration interval) {
        final LocalDateTime intervalStart = asMonitoringDateTime(stats.getStartTime());
        final LocalDateTime intervalEnd = intervalStart.plus(interval);
        final LocalDateTime start = intervalStart.isAfter(regionStart) ? intervalStart : regionStart;
        final LocalDateTime end = intervalEnd.isBefore(regionEnd) ? intervalEnd : regionEnd;
        final Duration actualInterval = Duration.between(start, end);
        if (actualInterval.isNegative() || actualInterval.isZero()) {
            return Optional.empty();
        }
        stats.setStartTime(MonitoringConstants.FORMATTER.format(start));
        stats.setEndTime(MonitoringConstants.FORMATTER.format(end));
        stats.setMillsInPeriod(actualInterval.toMillis());
        return Optional.of(stats);
    }

    private MonitoringStats.DisksUsage.DiskStats merged(final MonitoringStats.DisksUsage.DiskStats s1,
                                                        final MonitoringStats.DisksUsage.DiskStats s2) {
        final MonitoringStats.DisksUsage.DiskStats s3 = new MonitoringStats.DisksUsage.DiskStats();
        s3.setCapacity(s1.getCapacity() + s2.getCapacity());
        s3.setUsableSpace(s1.getUsableSpace() + s2.getUsableSpace());
        return s3;
    }

    private List<MonitoringStats> getGpuCharts(final List<GpuMetricsGranularity> loadTypes,
                                               final GPUAggregationRequester aggregationRequester,
                                               final Duration interval, final String nodeName,
                                               final LocalDateTime start, final LocalDateTime end) {
        if (!GpuMetricsGranularity.hasAggregations(loadTypes) && !GpuMetricsGranularity.hasDetails(loadTypes)) {
            return null;
        }
        final List<MonitoringStats> charts = new ArrayList<>();
        if (GpuMetricsGranularity.hasAggregations(loadTypes)) {
            charts.addAll(requestCharts(aggregationRequester, interval, nodeName, start, end));
        }
        if (GpuMetricsGranularity.hasDetails(loadTypes)) {
            final GPUDetailsRequester detailsRequester = new GPUDetailsRequester(client);
            charts.addAll(requestCharts(detailsRequester, interval, nodeName, start, end));
        }
        if (GpuMetricsGranularity.hasDetails(loadTypes) && GpuMetricsGranularity.hasAggregations(loadTypes)) {
            return charts.stream()
                    .collect(Collectors.groupingBy(MonitoringStats::getStartTime,
                            Collectors.reducing(this::mergeGpuStats)))
                    .values().stream()
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .filter(stats -> stats.getGpuUsage() != null && stats.getGpuDetails() != null)
                    .map(stats -> statsWithinRegion(stats, start, end, interval))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .sorted(Comparator.comparing(MonitoringStats::getStartTime,
                            Comparator.comparing(this::asMonitoringDateTime)))
                    .collect(Collectors.toList());
        }
        return charts.stream()
                .sorted(Comparator.comparing(MonitoringStats::getStartTime,
                        Comparator.comparing(this::asMonitoringDateTime)))
                .collect(Collectors.toList());
    }

    private List<MonitoringStats> requestCharts(final AbstractMetricRequester requester, final Duration interval,
                                                final String nodeName, final LocalDateTime start,
                                                final LocalDateTime end) {
        return requester.requestStats(nodeName, start, end, interval).stream()
                .map(stats -> statsWithinRegion(stats, start, end, interval).orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}
