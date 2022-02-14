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
import org.junit.Test;

import java.time.LocalDateTime;
import java.time.Month;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class NodePoolReportTableHelperTest {
    private static final String POOL_NAME1 = "pool1";
    private static final String POOL_NAME2 = "pool2";
    private static final String TIMESTAMP_HEADER = "Timestamp";
    private static final Long ID1 = 1L;
    private static final Long ID2 = 2L;
    private static final LocalDateTime TIMESTAMP1 = LocalDateTime.of(2022, Month.FEBRUARY, 7, 2, 30);
    private static final LocalDateTime TIMESTAMP2 = LocalDateTime.of(2022, Month.FEBRUARY, 8, 3, 30);
    private static final Integer NODES_COUNT = 2;
    private static final Integer OCCUPIED_NODES = 4;
    private static final Integer UTILIZATION = 150;
    private static final String HOUR_TIMESTAMP1 = "02:30:00";
    private static final String HOUR_TIMESTAMP2 = "03:30:00";
    private static final String DAY_TIMESTAMP1 = "2022-02-07";
    private static final String DAY_TIMESTAMP2 = "2022-02-08";

    private final NodePoolReportHeaderHelper headerHelper = NodePoolReportHeaderHelper.builder()
            .timestampColumnName(TIMESTAMP_HEADER)
            .totalNodesColumnFormat("%s_SIZE[nodes]")
            .occupiedNodesColumnFormat("%s_UTILIZATION[nodes]")
            .utilizationColumnsFormat("%s_UTILIZATION[%%]")
            .pools(poolNames())
            .build();
    private final NodePoolReportTableHelper tableHelper = new NodePoolReportTableHelper(headerHelper);

    @Test
    public void shouldBuildHourlyPoolDataReportTable() {
        final List<NodePoolUsageReport> report = new ArrayList<>();
        report.add(report(ID1));
        report.add(report(ID2));

        final List<String[]> table = tableHelper.buildPoolDataTable(report, ChronoUnit.HOURS, ID1);
        assertThat(table).hasSize(3);
        assertThat(table.get(0)).contains(poolDataHeader());
        assertPoolDataRow(table.get(1), HOUR_TIMESTAMP1);
        assertPoolDataRow(table.get(2), HOUR_TIMESTAMP2);
    }

    @Test
    public void shouldBuildDailyPoolDataReportTable() {
        final List<NodePoolUsageReport> report = new ArrayList<>();
        report.add(report(ID1));
        report.add(report(ID2));

        final List<String[]> table = tableHelper.buildPoolDataTable(report, ChronoUnit.DAYS, ID1);
        assertThat(table).hasSize(3);
        assertThat(table.get(0)).contains(poolDataHeader());
        assertPoolDataRow(table.get(1), DAY_TIMESTAMP1);
        assertPoolDataRow(table.get(2), DAY_TIMESTAMP2);
    }

    @Test
    public void shouldBuildHourlyUtilizationDataReportTable() {
        final List<NodePoolUsageReport> report = new ArrayList<>();
        report.add(report(ID1));
        report.add(report(ID2));

        final List<String[]> table = tableHelper.buildUtilizationTable(report, ChronoUnit.HOURS);
        assertThat(table).hasSize(3);
        assertThat(table.get(0)).contains(utilizationHeader());
        assertUtilizationDataRow(table.get(1), HOUR_TIMESTAMP1);
        assertUtilizationDataRow(table.get(2), HOUR_TIMESTAMP2);
    }

    @Test
    public void shouldBuildFullDataTable() {
        final List<NodePoolUsageReport> report = new ArrayList<>();
        report.add(report(ID1));
        report.add(report(ID2));

        final List<String[]> table = tableHelper.buildDataTable(report, ChronoUnit.HOURS, ID1);
        assertThat(table).hasSize(3);
        assertThat(table.get(0)).contains(fullDataHeader());
        assertFullDataRow(table.get(1), HOUR_TIMESTAMP1);
        assertFullDataRow(table.get(2), HOUR_TIMESTAMP2);
    }

    private void assertPoolDataRow(final String[] row, final String timestamp) {
        assertThat(row.length).isEqualTo(poolDataHeader().length);
        assertThat(row[0]).isEqualTo(timestamp);
        assertThat(row[1]).isEqualTo(NODES_COUNT.toString());
        assertThat(row[2]).isEqualTo(OCCUPIED_NODES.toString());
    }

    private void assertUtilizationDataRow(final String[] row, final String timestamp) {
        assertThat(row.length).isEqualTo(utilizationHeader().length);
        assertThat(row[0]).isEqualTo(timestamp);
        assertThat(row[1]).isEqualTo(UTILIZATION.toString());
        assertThat(row[2]).isEqualTo(UTILIZATION.toString());
    }

    private void assertFullDataRow(final String[] row, final String timestamp) {
        assertThat(row.length).isEqualTo(fullDataHeader().length);
        assertThat(row[0]).isEqualTo(timestamp);
        assertThat(row[1]).isEqualTo(NODES_COUNT.toString());
        assertThat(row[2]).isEqualTo(OCCUPIED_NODES.toString());
        assertThat(row[3]).isEqualTo(UTILIZATION.toString());
        assertThat(row[4]).isEqualTo(UTILIZATION.toString());
    }

    private NodePoolUsageReport report(final Long poolId) {
        return NodePoolUsageReport.builder()
                .poolId(poolId)
                .records(Arrays.asList(record(TIMESTAMP1), record(TIMESTAMP2)))
                .build();
    }

    private NodePoolUsageReportRecord record(final LocalDateTime timestamp) {
        return NodePoolUsageReportRecord.builder()
                .periodStart(timestamp)
                .nodesCount(NODES_COUNT)
                .occupiedNodesCount(OCCUPIED_NODES)
                .utilization(UTILIZATION)
                .build();
    }

    private static Map<Long, String> poolNames() {
        final Map<Long, String> poolNames = new HashMap<>();
        poolNames.put(ID1, POOL_NAME1);
        poolNames.put(ID2, POOL_NAME2);
        return poolNames;
    }

    private static String[] poolDataHeader() {
        return new String[] {TIMESTAMP_HEADER, "pool1_SIZE[nodes]", "pool1_UTILIZATION[nodes]"
        };
    }

    private static String[] utilizationHeader() {
        return new String[] {TIMESTAMP_HEADER, "pool1_UTILIZATION[%]", "pool2_UTILIZATION[%]"};
    }

    private static String[] fullDataHeader() {
        return new String[] {TIMESTAMP_HEADER, "pool1_SIZE[nodes]", "pool1_UTILIZATION[nodes]",
            "pool1_UTILIZATION[%]", "pool2_UTILIZATION[%]"};
    }
}
