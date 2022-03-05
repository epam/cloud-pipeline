/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.report.pool;

import com.epam.pipeline.dto.report.NodePoolReportType;
import com.epam.pipeline.dto.report.ReportFilter;
import com.epam.pipeline.dto.report.NodePoolUsageReport;
import com.epam.pipeline.dto.report.NodePoolUsageReportRecord;
import com.epam.pipeline.entity.cluster.pool.NodePoolUsage;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.cluster.pool.NodePoolUsageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.epam.pipeline.manager.report.ReportUtils.buildTimeIntervals;
import static com.epam.pipeline.manager.report.ReportUtils.calculateSampleMax;
import static com.epam.pipeline.manager.report.ReportUtils.calculateSampleMedian;
import static com.epam.pipeline.manager.report.ReportUtils.dateInInterval;

@Service
@RequiredArgsConstructor
@Slf4j
public class NodePoolReportService {
    private static final int TO_PERCENTS = 100;

    private final NodePoolUsageService nodePoolUsageService;

    private Map<NodePoolReportType, AbstractNodePoolReportWriter> writers;

    @Autowired
    public void setWriters(final List<AbstractNodePoolReportWriter> writers) {
        this.writers = writers.stream()
                .collect(Collectors.toMap(AbstractNodePoolReportWriter::getType, Function.identity()));
    }

    public List<NodePoolUsageReport> getReport(final ReportFilter filter) {
        prepareFilter(filter);
        final Map<Long, List<NodePoolUsage>> usagesByPool = nodePoolUsageService
                .getByPeriod(filter.getFrom(), filter.getTo()).stream()
                .collect(Collectors.groupingBy(NodePoolUsage::getNodePoolId));

        if (filter.getInterval() == ChronoUnit.HOURS) {
            return buildHourlyUsageByPool(filter, usagesByPool);
        }

        if (filter.getInterval() == ChronoUnit.DAYS) {
            return buildDailyUsageByPool(filter, usagesByPool);
        }

        throw new UnsupportedOperationException(String.format("Time interval '%s' is not supported for now",
                filter.getInterval().name()));
    }

    public InputStream getReportFile(final ReportFilter filter, final Long targetPool, final NodePoolReportType type) {
        return getWriter(type)
                .writeToStream(getReport(filter), targetPool, filter.getInterval());
    }

    private AbstractNodePoolReportWriter getWriter(final NodePoolReportType type) {
        return Optional.ofNullable(writers.get(type)).orElseThrow(() ->
                new IllegalArgumentException(String.format("Report format '%s' is not supported", type.name())));
    }

    private List<NodePoolUsageReport> buildDailyUsageByPool(final ReportFilter filter,
                                                            final Map<Long, List<NodePoolUsage>> usagesByPool) {
        return usagesByPool.entrySet().stream()
                .map(entry -> NodePoolUsageReport.builder()
                        .poolId(entry.getKey())
                        .records(buildDailyUsage(filter, entry.getValue()))
                        .build())
                .collect(Collectors.toList());
    }

    private List<NodePoolUsageReport> buildHourlyUsageByPool(final ReportFilter filter,
                                                             final Map<Long, List<NodePoolUsage>> usagesByPool) {
        return usagesByPool.entrySet().stream()
                .map(entry -> NodePoolUsageReport.builder()
                        .poolId(entry.getKey())
                        .records(buildHourlyUsage(filter, entry.getValue()))
                        .build())
                .collect(Collectors.toList());
    }

    private List<NodePoolUsageReportRecord> buildHourlyUsage(final ReportFilter filter,
                                                             final List<NodePoolUsage> poolUsage) {
        final List<LocalDateTime> hourIntervals = buildTimeIntervals(filter.getFrom(), filter.getTo(),
                filter.getInterval());
        return hourIntervals.stream()
                .map(periodStart -> calculateHourUsage(periodStart, poolUsage))
                .collect(Collectors.toList());
    }

    private NodePoolUsageReportRecord calculateHourUsage(final LocalDateTime periodStart,
                                                         final List<NodePoolUsage> poolUsage) {
        final LocalDateTime periodEnd = periodStart.plusHours(1);
        final List<NodePoolUsage> hourUsages = poolUsage.stream()
                .filter(usage -> dateInInterval(usage.getLogDate(), periodStart, periodEnd))
                .collect(Collectors.toList());
        final Integer nodesCount = calculateSampleMax(NodePoolUsage::getTotalNodesCount, hourUsages);
        final Integer occupiedNodesCount = calculateSampleMax(NodePoolUsage::getOccupiedNodesCount, hourUsages);
        final Integer utilization = calculateSampleMax(this::calculateHourUtilization, hourUsages);
        return NodePoolUsageReportRecord.builder()
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .occupiedNodesCount(occupiedNodesCount)
                .nodesCount(nodesCount)
                .utilization(utilization)
                .build();
    }

    private Integer calculateHourUtilization(final NodePoolUsage usage) {
        final Integer nodesCount = usage.getTotalNodesCount();
        final Integer occupiedNodesCount = usage.getOccupiedNodesCount();
        if (Objects.isNull(occupiedNodesCount) || Objects.isNull(nodesCount) || nodesCount == 0) {
            return null;
        }
        return Math.toIntExact(Math.round(TO_PERCENTS * occupiedNodesCount / (double) nodesCount));
    }

    private List<NodePoolUsageReportRecord> buildDailyUsage(final ReportFilter filter,
                                                            final List<NodePoolUsage> poolUsage) {
        final List<LocalDateTime> dayIntervals = buildTimeIntervals(filter.getFrom(), filter.getTo(),
                filter.getInterval());
        return dayIntervals.stream()
                .map(dayInterval -> calculateDayUsage(dayInterval, poolUsage))
                .collect(Collectors.toList());
    }

    private NodePoolUsageReportRecord calculateDayUsage(final LocalDateTime daysStart,
                                                        final List<NodePoolUsage> poolUsages) {
        final LocalDateTime daysEnd = daysStart.plusDays(1);
        final ReportFilter dayFilter = ReportFilter.builder()
                .from(daysStart)
                .to(daysEnd)
                .interval(ChronoUnit.HOURS)
                .build();
        final List<NodePoolUsageReportRecord> hourlyUsage = buildHourlyUsage(dayFilter, poolUsages);
        return NodePoolUsageReportRecord.builder()
                .periodStart(daysStart)
                .periodEnd(daysEnd)
                .occupiedNodesCount(calculateSampleMedian(
                        NodePoolUsageReportRecord::getOccupiedNodesCount, hourlyUsage))
                .nodesCount(calculateSampleMedian(NodePoolUsageReportRecord::getNodesCount, hourlyUsage))
                .utilization(calculateSampleMedian(NodePoolUsageReportRecord::getUtilization, hourlyUsage))
                .build();
    }

    private void prepareFilter(final ReportFilter filter) {
        if (Objects.isNull(filter.getInterval())) {
            filter.setInterval(ChronoUnit.HOURS);
        }
        final LocalDateTime now = DateUtils.nowUTC();
        if (Objects.isNull(filter.getFrom())) {
            filter.setFrom(now.toLocalDate().atStartOfDay());
        }
        if (Objects.isNull(filter.getTo())) {
            filter.setTo(now);
        }
        Assert.state(!filter.getFrom().isAfter(filter.getTo()), "'from' date must be before 'to' date");
        filter.setTo(filter.getTo().isAfter(now) ? now : filter.getTo());
    }
}
