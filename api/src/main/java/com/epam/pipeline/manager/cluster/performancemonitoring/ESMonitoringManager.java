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

import com.epam.pipeline.dao.monitoring.metricrequester.AbstractMetricRequester;
import com.epam.pipeline.entity.cluster.monitoring.ELKUsageMetric;
import com.epam.pipeline.entity.cluster.monitoring.MonitoringStats;
import com.epam.pipeline.entity.utils.DateUtils;
import lombok.RequiredArgsConstructor;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Collections;
import java.util.Comparator;
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
            .appendPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .toFormatter();

    private final RestHighLevelClient client;

    @Override
    public List<MonitoringStats> getStatsForNode(final String nodeName) {
        final Duration interval = Duration.ofMinutes(1);
        final Duration monitoringPeriod = Duration.ofHours(1);
        final LocalDateTime end = DateUtils.nowUTC();
        final LocalDateTime start = end.minus(monitoringPeriod);
        return Stream.of(MONITORING_METRICS)
                .map(it -> AbstractMetricRequester.getStatsRequester(it, client))
                .map(it -> it.requestStats(Collections.singletonList(nodeName), start, end, interval))
                .flatMap(List::stream)
                .collect(Collectors.groupingBy(MonitoringStats::getStartTime, Collectors.reducing(this::mergeStats)))
                .values()
                .stream()
                .filter(Optional::isPresent)
                .map(Optional::get)
                .peek(stats -> {
                    final LocalDateTime intervalStart = LocalDateTime.parse(stats.getStartTime(), FORMATTER);
                    final LocalDateTime intervalEnd = intervalStart.plus(interval);
                    stats.setEndTime(FORMATTER.format(intervalEnd));
                    stats.setMillsInPeriod(interval.toMillis());
                })
                .sorted(Comparator.comparing(MonitoringStats::getStartTime))
                .collect(Collectors.toList());
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
}
