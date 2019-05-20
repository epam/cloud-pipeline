/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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
import com.epam.pipeline.dao.monitoring.metricrequester.AbstractMetricRequester;
import com.epam.pipeline.entity.BaseEntity;
import com.epam.pipeline.entity.cluster.monitoring.ELKUsageMetric;
import com.epam.pipeline.entity.cluster.monitoring.MonitoringStats;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.cluster.NodesManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import lombok.RequiredArgsConstructor;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.SignStyle;
import java.time.temporal.ChronoField;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class ESMonitoringManager implements UsageMonitoringManager {

    private static final ELKUsageMetric[] MONITORING_METRICS = {ELKUsageMetric.CPU, ELKUsageMetric.MEM,
        ELKUsageMetric.FS, ELKUsageMetric.NETWORK};
    private static final DateTimeFormatter FORMATTER = new DateTimeFormatterBuilder()
            // Example: 2019-05-17T10:24:23.033Z
            .appendValue(ChronoField.YEAR, 4, 10, SignStyle.EXCEEDS_PAD).appendLiteral('-')
            .appendValue(ChronoField.MONTH_OF_YEAR, 2).appendLiteral('-')
            .appendValue(ChronoField.DAY_OF_MONTH, 2).appendLiteral("T")
            .appendValue(ChronoField.HOUR_OF_DAY, 2).appendLiteral(":")
            .appendValue(ChronoField.MINUTE_OF_HOUR, 2).appendLiteral(":")
            .appendValue(ChronoField.SECOND_OF_MINUTE, 2).appendLiteral(".")
            .appendFraction(ChronoField.NANO_OF_SECOND, 3, 3, false).appendLiteral("Z")
            .toFormatter();
    private static final Duration FALLBACK_MONITORING_PERIOD = Duration.ofHours(1);
    private static final Duration FALLBACK_MINIMAL_INTERVAL = Duration.ofMinutes(1);
    private static final int FALLBACK_INTERVALS_NUMBER = 10;

    private final RestHighLevelClient client;
    private final PreferenceManager preferenceManager;
    private final NodesManager nodesManager;
    private final MessageHelper messageHelper;

    @Override
    public List<MonitoringStats> getStatsForNode(final String nodeName, final LocalDateTime from,
                                                 final LocalDateTime to) {
        final LocalDateTime start;
        final LocalDateTime end;
        if (from == null || to == null) {
            start = creationDate(nodeName);
            end = DateUtils.nowUTC();
        } else {
            start = from;
            end = to;
        }
        return getStats(nodeName, start, end);
    }

    private List<MonitoringStats> getStats(final String nodeName, final LocalDateTime start, final LocalDateTime end) {
        Assert.isTrue(start.isBefore(end), messageHelper.getMessage(
                MessageConstants.ERROR_CLUSTER_MONITORING_NEGATIVE_INTERVAL, start, end));
        final Duration interval = interval(start, end);
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
                .peek(stats -> addStatsDuration(stats, interval))
                .sorted(Comparator.comparing(MonitoringStats::getStartTime))
                .collect(Collectors.toList());
    }

    private LocalDateTime creationDate(final String nodeName) {
        return nodesManager.findNode(nodeName)
                .map(BaseEntity::getCreatedDate)
                .map(Date::toInstant)
                .map(it -> it.atZone(ZoneOffset.UTC))
                .map(ZonedDateTime::toLocalDateTime)
                .orElseGet(() -> DateUtils.nowUTC().minus(FALLBACK_MONITORING_PERIOD));
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

    private void addStatsDuration(final MonitoringStats stats, final Duration interval) {
        final LocalDateTime start = LocalDateTime.parse(stats.getStartTime(), FORMATTER);
        final LocalDateTime end = start.plus(interval);
        stats.setEndTime(FORMATTER.format(end));
        stats.setMillsInPeriod(interval.toMillis());
    }
}
