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

import com.amazonaws.util.StringInputStream;
import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dao.monitoring.MonitoringESDao;
import com.epam.pipeline.dao.monitoring.metricrequester.AbstractMetricRequester;
import com.epam.pipeline.entity.cluster.NodeInstance;
import com.epam.pipeline.entity.cluster.monitoring.ELKUsageMetric;
import com.epam.pipeline.entity.cluster.monitoring.MonitoringStats;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.cluster.KubernetesConstants;
import com.epam.pipeline.manager.cluster.NodesManager;
import com.epam.pipeline.manager.cluster.writer.MonitoringStatsWriter;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import lombok.RequiredArgsConstructor;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "monitoring.backend", havingValue = "elastic")
public class ESMonitoringManager implements UsageMonitoringManager {

    private static final ELKUsageMetric[] MONITORING_METRICS = {ELKUsageMetric.CPU, ELKUsageMetric.MEM,
        ELKUsageMetric.FS, ELKUsageMetric.NETWORK};
    private static final Duration FALLBACK_MONITORING_PERIOD = Duration.ofHours(1);
    private static final Duration FALLBACK_MINIMAL_INTERVAL = Duration.ofMinutes(1);
    private static final int FALLBACK_INTERVALS_NUMBER = 10;
    public static final int TWO = 2;

    private final RestHighLevelClient client;
    private final MonitoringESDao monitoringDao;
    private final MessageHelper messageHelper;
    private final PreferenceManager preferenceManager;
    private final NodesManager nodesManager;

    @Override
    public List<MonitoringStats> getStatsForNode(final String nodeName, final LocalDateTime from,
                                                 final LocalDateTime to) {
        final LocalDateTime requestedStart = Optional.ofNullable(from).orElseGet(() -> creationDate(nodeName));
        final LocalDateTime oldestMonitoring = oldestMonitoringDate();
        final LocalDateTime start = requestedStart.isAfter(oldestMonitoring) ? requestedStart : oldestMonitoring;
        final LocalDateTime end = Optional.ofNullable(to).orElseGet(DateUtils::nowUTC);
        final Duration interval = interval(start, end);
        return end.isAfter(start) && end.isAfter(oldestMonitoring)
                ? getStats(nodeName, start, end, interval)
                : Collections.emptyList();
    }

    @Override
    public InputStream getStatsForNodeAsInputStream(final String nodeName,
                                                    final LocalDateTime from,
                                                    final LocalDateTime to,
                                                    final Duration interval) {
        final LocalDateTime requestedStart = Optional.ofNullable(from).orElseGet(() -> creationDate(nodeName));
        final LocalDateTime oldestMonitoring = oldestMonitoringDate();
        final LocalDateTime start = requestedStart.isAfter(oldestMonitoring) ? requestedStart : oldestMonitoring;
        final LocalDateTime end = Optional.ofNullable(to).orElseGet(DateUtils::nowUTC);
        final Duration minDuration = minimalDuration();
        final Duration adjustedDuration = interval.compareTo(minDuration) < 0
                                          ? minDuration
                                          : interval;
        final List<MonitoringStats> monitoringStats = getStats(nodeName, start, end, adjustedDuration);
        final MonitoringStatsWriter statsWriter = new MonitoringStatsWriter();
        try {
            return new StringInputStream(statsWriter.convertStatsToCsvString(monitoringStats));
        } catch (IOException e) {
            throw new IllegalStateException("Written csv stats file encoding differs from UTF-8.", e);
        }
    }

    @Override
    public long getPodDiskSpaceAvailable(final String nodeName, final String podId, final String dockerImage) {
        final Duration duration = minimalDuration();
        final MonitoringStats.DisksUsage.DiskStats diskStats =
                AbstractMetricRequester.getStatsRequester(ELKUsageMetric.POD_FS, client)
                        .requestStats(nodeName,
                                DateUtils.nowUTC().minus(duration.multipliedBy(Math.max(numberOfIntervals(), TWO))),
                                DateUtils.nowUTC(),
                                duration
                        )
                        .stream()
                        .max(Comparator.comparing(MonitoringStats::getStartTime,
                                Comparator.comparing(this::asDateTime)))
                        .map(MonitoringStats::getDisksUsage)
                        .map(MonitoringStats.DisksUsage::getStatsByDevices)
                        //get disks stats (in fact here only one stats for one disk)
                        .orElse(Collections.emptyMap())
                        .values().stream()
                        //get stats for the only Pod disk
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException(messageHelper.getMessage(
                                MessageConstants.ERROR_GET_NODE_STAT, nodeName)));
        return diskStats.getCapacity() - diskStats.getUsableSpace();
    }

    private LocalDateTime oldestMonitoringDate() {
        return monitoringDao.oldestIndexDate().orElseGet(this::fallbackMonitoringStart);
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

    private boolean isMonitoringStatsComplete(final MonitoringStats monitoringStats) {
        return monitoringStats.getCpuUsage() != null
                && monitoringStats.getMemoryUsage() != null
                && monitoringStats.getDisksUsage() != null
                && monitoringStats.getNetworkUsage() != null;
    }

    private LocalDateTime asMonitoringDateTime(final String dateTimeString) {
        return LocalDateTime.parse(dateTimeString, MonitoringConstants.FORMATTER);
    }

    private LocalDateTime asDateTime(final String dateTimeString) {
        return LocalDateTime.parse(dateTimeString, MonitoringConstants.DATE_TIME_FORMATTER);
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
}
