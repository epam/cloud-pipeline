/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.report.pool;

import com.epam.pipeline.dto.report.NodePoolUsageReport;
import com.epam.pipeline.dto.report.NodePoolUsageReportRecord;
import com.epam.pipeline.dto.report.ReportFilter;
import com.epam.pipeline.entity.cluster.pool.NodePoolUsage;
import com.epam.pipeline.manager.cluster.pool.NodePoolUsageService;
import org.apache.commons.collections4.ListUtils;
import org.junit.Test;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

import static com.epam.pipeline.util.ReportTestUtils.DAYS_IN_MONTH;
import static com.epam.pipeline.util.ReportTestUtils.HOURS_IN_DAY;
import static com.epam.pipeline.util.ReportTestUtils.buildTimeIntervals;
import static com.epam.pipeline.util.ReportTestUtils.dayEnd;
import static com.epam.pipeline.util.ReportTestUtils.dayStart;
import static com.epam.pipeline.util.ReportTestUtils.monthEnd;
import static com.epam.pipeline.util.ReportTestUtils.monthStart;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class NodePoolReportServiceTest {
    private static final Long POOL_ID1 = 1L;
    private static final Long POOL_ID2 = 2L;
    private static final Integer NODES_COUNT = 2;
    private static final Integer OCCUPIED_NODE_COUNT = 1;
    private static final Integer UTILIZATION = 50;

    private final NodePoolUsageService nodePoolUsageService = mock(NodePoolUsageService.class);
    private final NodePoolReportService nodePoolReportService = new NodePoolReportService(nodePoolUsageService);

    @Test
    public void shouldCalculateDayUsage() {
        final LocalDateTime from = dayStart();
        final LocalDateTime to = dayEnd(from);

        final ReportFilter filter = ReportFilter.builder()
                .from(from)
                .to(to)
                .interval(ChronoUnit.HOURS)
                .build();
        doReturn(generateNodePoolUsage(from, to)).when(nodePoolUsageService).getByPeriod(from, to);

        final NodePoolUsageReportRecord expectedFirstRecord = NodePoolUsageReportRecord.builder()
                .periodStart(from)
                .periodEnd(from.plusHours(1))
                .nodesCount(NODES_COUNT)
                .occupiedNodesCount(OCCUPIED_NODE_COUNT)
                .utilization(UTILIZATION)
                .build();
        final NodePoolUsageReportRecord expectedLastRecord = NodePoolUsageReportRecord.builder()
                .periodStart(from.plusHours(HOURS_IN_DAY - 1))
                .periodEnd(from.plusHours(HOURS_IN_DAY))
                .nodesCount(NODES_COUNT)
                .occupiedNodesCount(OCCUPIED_NODE_COUNT)
                .utilization(UTILIZATION)
                .build();

        final List<NodePoolUsageReport> result = nodePoolReportService.getReport(filter);
        assertThat(result).hasSize(2);
        assertUsageRecords(result.get(0), expectedFirstRecord, expectedLastRecord, HOURS_IN_DAY);
        assertUsageRecords(result.get(1), expectedFirstRecord, expectedLastRecord, HOURS_IN_DAY);
    }

    @Test
    public void shouldReturnEmptyDayUsageIfNodesCountUnknown() {
        final LocalDateTime from = dayStart();
        final LocalDateTime to = dayEnd(from);

        final ReportFilter filter = ReportFilter.builder()
                .from(from)
                .to(to)
                .interval(ChronoUnit.HOURS)
                .build();
        doReturn(generateNodePoolUsage(from, to).stream()
                .peek(usage -> usage.setTotalNodesCount(null))
                .peek(usage -> usage.setOccupiedNodesCount(null))
                .collect(Collectors.toList()))
                .when(nodePoolUsageService).getByPeriod(from, to);

        final NodePoolUsageReportRecord expectedFirstRecord = NodePoolUsageReportRecord.builder()
                .periodStart(from)
                .periodEnd(from.plusHours(1))
                .build();
        final NodePoolUsageReportRecord expectedLastRecord = NodePoolUsageReportRecord.builder()
                .periodStart(from.plusHours(HOURS_IN_DAY - 1))
                .periodEnd(from.plusHours(HOURS_IN_DAY))
                .build();

        final List<NodePoolUsageReport> result = nodePoolReportService.getReport(filter);
        assertThat(result).hasSize(2);
        assertUsageRecords(result.get(0), expectedFirstRecord, expectedLastRecord, HOURS_IN_DAY);
        assertUsageRecords(result.get(1), expectedFirstRecord, expectedLastRecord, HOURS_IN_DAY);
    }

    @Test
    public void shouldCalculateMonthUsage() {
        final LocalDateTime from = monthStart();
        final LocalDateTime to = monthEnd(from);

        final ReportFilter filter = ReportFilter.builder()
                .from(from)
                .to(to)
                .interval(ChronoUnit.DAYS)
                .build();
        doReturn(generateNodePoolUsage(from, to)).when(nodePoolUsageService).getByPeriod(from, to);

        final NodePoolUsageReportRecord expectedFirstRecord = NodePoolUsageReportRecord.builder()
                .periodStart(from)
                .periodEnd(from.plusDays(1))
                .nodesCount(NODES_COUNT)
                .occupiedNodesCount(OCCUPIED_NODE_COUNT)
                .utilization(UTILIZATION)
                .build();
        final NodePoolUsageReportRecord expectedLastRecord = NodePoolUsageReportRecord.builder()
                .periodStart(from.plusDays(DAYS_IN_MONTH - 1))
                .periodEnd(from.plusDays(DAYS_IN_MONTH))
                .nodesCount(NODES_COUNT)
                .occupiedNodesCount(OCCUPIED_NODE_COUNT)
                .utilization(UTILIZATION)
                .build();

        final List<NodePoolUsageReport> result = nodePoolReportService.getReport(filter);
        assertThat(result).hasSize(2);
        assertUsageRecords(result.get(0), expectedFirstRecord, expectedLastRecord, DAYS_IN_MONTH);
        assertUsageRecords(result.get(1), expectedFirstRecord, expectedLastRecord, DAYS_IN_MONTH);
    }

    @Test
    public void shouldReturnEmptyMonthUsageIfNodesCountUnknown() {
        final LocalDateTime from = monthStart();
        final LocalDateTime to = monthEnd(from);

        final ReportFilter filter = ReportFilter.builder()
                .from(from)
                .to(to)
                .interval(ChronoUnit.DAYS)
                .build();
        doReturn(generateNodePoolUsage(from, to).stream()
                .peek(usage -> usage.setTotalNodesCount(null))
                .peek(usage -> usage.setOccupiedNodesCount(null))
                .collect(Collectors.toList()))
                .when(nodePoolUsageService).getByPeriod(from, to);

        final NodePoolUsageReportRecord expectedFirstRecord = NodePoolUsageReportRecord.builder()
                .periodStart(from)
                .periodEnd(from.plusDays(1))
                .build();
        final NodePoolUsageReportRecord expectedLastRecord = NodePoolUsageReportRecord.builder()
                .periodStart(from.plusDays(DAYS_IN_MONTH - 1))
                .periodEnd(from.plusDays(DAYS_IN_MONTH))
                .build();

        final List<NodePoolUsageReport> result = nodePoolReportService.getReport(filter);
        assertThat(result).hasSize(2);
        assertUsageRecords(result.get(0), expectedFirstRecord, expectedLastRecord, DAYS_IN_MONTH);
        assertUsageRecords(result.get(1), expectedFirstRecord, expectedLastRecord, DAYS_IN_MONTH);
    }

    private List<NodePoolUsage> generateNodePoolUsage(final LocalDateTime from, final LocalDateTime to) {
        return ListUtils.union(generateNodePoolUsage(from, to, POOL_ID1),
                generateNodePoolUsage(from, to, POOL_ID2));
    }

    private List<NodePoolUsage> generateNodePoolUsage(final LocalDateTime from, final LocalDateTime to,
                                                      final Long poolId) {
        return buildTimeIntervals(from, to).stream()
                .map(intervalStart -> nodePoolUsage(intervalStart, poolId))
                .collect(Collectors.toList());
    }

    private NodePoolUsage nodePoolUsage(final LocalDateTime logDate, final Long poolId) {
        return NodePoolUsage.builder()
                .nodePoolId(poolId)
                .logDate(logDate)
                .totalNodesCount(NODES_COUNT)
                .occupiedNodesCount(OCCUPIED_NODE_COUNT)
                .build();
    }

    private void assertUsageRecords(final NodePoolUsageReport resultReport,
                                    final NodePoolUsageReportRecord expectedFirstRecord,
                                    final NodePoolUsageReportRecord expectedLastRecord,
                                    final Integer recordsCount) {
        assertThat(resultReport.getRecords()).hasSize(recordsCount);
        assertThat(resultReport.getRecords().get(0)).isEqualTo(expectedFirstRecord);
        assertThat(resultReport.getRecords().get(recordsCount - 1)).isEqualTo(expectedLastRecord);
    }
}
