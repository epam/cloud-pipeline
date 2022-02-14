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

import com.epam.pipeline.config.Constants;
import com.epam.pipeline.dto.report.NodePoolUsageReport;
import com.epam.pipeline.dto.report.NodePoolUsageReportRecord;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.ListUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class NodePoolReportTableHelper {
    private static final String DATA_FORMAT = "yyyy-MM-dd";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern(DATA_FORMAT);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern(Constants.TIME_FORMAT);
    private static final int TIMESTAMP_COLUMN_INDEX = 0;
    private static final int TOTAL_NONES_COLUMN_INDEX = 1;
    private static final int OCCUPIED_NODES_COLUMN_INDEX = 2;

    private final NodePoolReportHeaderHelper headerHelper;

    public List<String[]> buildPoolDataTable(final List<NodePoolUsageReport> report,
                                             final ChronoUnit interval,
                                             final Long targetPoolId) {
        final List<String[]> lines = new ArrayList<>();

        final String[] headerLine = new NodePoolReportHeader(headerHelper).withPoolData(targetPoolId).toHeader();
        lines.add(headerLine);
        final List<NodePoolUsageReportRecord> targetRecords = findTargetRecords(report, targetPoolId);

        lines.addAll(targetRecords.stream()
                .map(targetRecord -> buildPoolDataRow(headerLine, targetRecord, interval))
                .collect(Collectors.toList()));
        return lines;
    }

    public List<String[]> buildUtilizationTable(final List<NodePoolUsageReport> report, final ChronoUnit interval) {
        final List<String[]> lines = new ArrayList<>();
        final Map<Integer, List<NodePoolUsageReportRecord>> reportByHeaderIndex = new HashMap<>();

        final String[] header = new NodePoolReportHeader(headerHelper)
                .withUtilizationData(report, reportByHeaderIndex)
                .toHeader();
        lines.add(header);

        final List<LocalDateTime> timestamps = ListUtils.emptyIfNull(
                Optional.ofNullable(report.get(0))
                        .orElseGet(NodePoolUsageReport::new)
                        .getRecords()).stream()
                .map(NodePoolUsageReportRecord::getPeriodStart)
                .collect(Collectors.toList());

        for (int recordIndex = 0; recordIndex < timestamps.size(); recordIndex++) {
            final String[] row = new String[header.length];
            row[TIMESTAMP_COLUMN_INDEX] = buildTimestampColumnValue(timestamps.get(recordIndex), interval);
            buildUtilizationValues(reportByHeaderIndex, row, recordIndex);
            lines.add(row);
        }

        return lines;
    }

    public List<String[]> buildDataTable(final List<NodePoolUsageReport> report, final ChronoUnit interval,
                                         final Long targetPoolId) {
        final List<String[]> lines = new ArrayList<>();
        final Map<Integer, List<NodePoolUsageReportRecord>> reportByHeaderIndex = new HashMap<>();

        final String[] header = new NodePoolReportHeader(headerHelper)
                .withPoolData(targetPoolId)
                .withUtilizationData(report, reportByHeaderIndex)
                .toHeader();
        lines.add(header);

        final List<NodePoolUsageReportRecord> targetRecords = findTargetRecords(report, targetPoolId);

        for (int recordIndex = 0; recordIndex < targetRecords.size(); recordIndex++) {
            final String[] row = buildPoolDataRow(header, targetRecords.get(recordIndex), interval);
            buildUtilizationValues(reportByHeaderIndex, row, recordIndex);
            lines.add(row);
        }

        return lines;
    }

    private List<NodePoolUsageReportRecord> findTargetRecords(final List<NodePoolUsageReport> report,
                                                              final Long targetPoolId) {
        return report.stream()
                .filter(poolReport -> Objects.equals(targetPoolId, poolReport.getPoolId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Cannot find requested pool in report data"))
                .getRecords();
    }

    private void buildUtilizationValues(final Map<Integer, List<NodePoolUsageReportRecord>> reportByHeaderIndex,
                                        final String[] row, final int recordIndex) {
        reportByHeaderIndex.forEach((headerIndex, records) ->
                row[headerIndex] = stringValue(records.get(recordIndex).getUtilization()));
    }

    private String buildTimestampColumnValue(final LocalDateTime timestamp, final ChronoUnit interval) {
        return ChronoUnit.HOURS.equals(interval)
                ? TIME_FORMATTER.format(timestamp)
                : DATE_FORMATTER.format(timestamp);
    }

    private String[] buildPoolDataRow(final String[] header, final NodePoolUsageReportRecord targetRecord,
                                      final ChronoUnit interval) {
        final String[] row = new String[header.length];
        row[TIMESTAMP_COLUMN_INDEX] = buildTimestampColumnValue(targetRecord.getPeriodStart(), interval);
        row[TOTAL_NONES_COLUMN_INDEX] = stringValue(targetRecord.getNodesCount());
        row[OCCUPIED_NODES_COLUMN_INDEX] = stringValue(targetRecord.getOccupiedNodesCount());
        return row;
    }

    private String stringValue(final Number value) {
        return Objects.isNull(value) ? "" : String.valueOf(value);
    }
}
