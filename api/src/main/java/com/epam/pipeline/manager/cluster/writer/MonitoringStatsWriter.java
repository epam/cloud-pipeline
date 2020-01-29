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

package com.epam.pipeline.manager.cluster.writer;

import com.epam.pipeline.entity.cluster.monitoring.MonitoringStats;
import com.opencsv.CSVWriter;
import lombok.NoArgsConstructor;
import lombok.Value;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@NoArgsConstructor
public class MonitoringStatsWriter {

    private static final List<String> COMMON_STATS_HEADER =
        Arrays.asList("Timestamp", "CPU_cores", "CPU_usage[%]", "MEM_capacity[bytes]", "MEM_usage[%]");
    private static final String DISK_TOTAL_HEADER_TEMPLATE = "%s_total[bytes]";
    private static final String DISK_USAGE_HEADER_TEMPLATE = "%s_usage[%%]";
    private static final String NETWORK_USAGE_IN_HEADER_TEMPLATE = "%s_in[bytes]";
    private static final String NETWORK_USAGE_OUT_HEADER_TEMPLATE = "%s_out[bytes]";
    private static final double HUNDRED_PERCENTS = 100.0;

    public String convertStatsToCsvString(final List<MonitoringStats> stats) {
        final StringWriter stringWriter = new StringWriter();
        final CSVWriter csvWriter = new CSVWriter(stringWriter);
        final List<String[]> allLines = extractTable(stats);
        csvWriter.writeAll(allLines);
        return stringWriter.toString();
    }

    private List<String[]> extractTable(final List<MonitoringStats> stats) {
        final List<String[]> allLines = new ArrayList<>();
        final MonitoringStatsHeader header = extractHeader(stats);
        if (!header.getColumnNames().isEmpty()) {
            allLines.add(header.getColumnNames().toArray(new String[0]));
            final List<String[]> entities = stats.stream()
                .map(stat -> createNewLine(header, stat))
                .collect(Collectors.toList());
            allLines.addAll(entities);
            return allLines;
        } else {
            return Collections.emptyList();
        }
    }

    private MonitoringStatsHeader extractHeader(final List<MonitoringStats> stats) {
        if (stats.size() == 0) {
            return MonitoringStatsHeader.EMPTY_HEADER;
        }
        final List<String> headerColumns = new ArrayList<>(COMMON_STATS_HEADER);
        final List<String> disks = stats.stream()
            .map(MonitoringStats::getDisksUsage)
            .map(MonitoringStats.DisksUsage::getStatsByDevices)
            .map(Map::keySet)
            .flatMap(Set::stream)
            .distinct()
            .sorted()
            .peek(disk -> {
                headerColumns.add(String.format(DISK_TOTAL_HEADER_TEMPLATE, disk));
                headerColumns.add(String.format(DISK_USAGE_HEADER_TEMPLATE, disk));
            })
            .collect(Collectors.toList());
        final List<String> networkInterfaces = stats.stream()
            .map(MonitoringStats::getNetworkUsage)
            .map(MonitoringStats.NetworkUsage::getStatsByInterface)
            .map(Map::keySet)
            .flatMap(Set::stream)
            .distinct()
            .sorted()
            .peek(netInterface -> {
                headerColumns.add(String.format(NETWORK_USAGE_IN_HEADER_TEMPLATE, netInterface));
                headerColumns.add(String.format(NETWORK_USAGE_OUT_HEADER_TEMPLATE, netInterface));
            })
            .collect(Collectors.toList());
        return new MonitoringStatsHeader(disks, networkInterfaces, headerColumns);
    }

    private String[] createNewLine(final MonitoringStatsHeader header, final MonitoringStats stat) {
        final String[] newLine = new String[header.getColumnNames().size()];
        fillGeneralColumns(stat, newLine);
        fillDisksColumns(stat, header, newLine);
        fillNetworkingColumns(stat, header, newLine);
        return newLine;
    }

    private void fillGeneralColumns(final MonitoringStats stat, final String[] newLine) {
        newLine[0] = stat.getEndTime();
        newLine[1] = Integer.toString(stat.getContainerSpec().getNumberOfCores());
        newLine[2] = Double.toString(HUNDRED_PERCENTS * stat.getCpuUsage().getLoad());
        final long memCapacity = stat.getMemoryUsage().getCapacity();
        newLine[3] = Long.toString(memCapacity);
        newLine[4] = Double.toString(HUNDRED_PERCENTS * stat.getMemoryUsage().getUsage() / memCapacity);
    }

    private void fillDisksColumns(final MonitoringStats stat, final MonitoringStatsHeader header,
                                  final String[] newLine) {
        final List<String> diskNames = header.getDiskNames();
        stat.getDisksUsage().getStatsByDevices().forEach((diskName, diskStatValue) -> {
            final long diskCapacity = diskStatValue.getCapacity();
            final double diskUsage = HUNDRED_PERCENTS * diskStatValue.getUsableSpace() / diskCapacity;
            final int columnIndex = COMMON_STATS_HEADER.size() + 2 * diskNames.indexOf(diskName);
            newLine[columnIndex] = Long.toString(diskCapacity);
            newLine[columnIndex + 1] = Double.toString(diskUsage);
        });
    }

    private void fillNetworkingColumns(final MonitoringStats stat, final MonitoringStatsHeader header,
                                       final String[] newLine) {
        stat.getNetworkUsage().getStatsByInterface().forEach((interfaceName, networkStats) -> {
            final int disksColumnShift = 2 * header.getDiskNames().size();
            final List<String> interfaceNames = header.getInterfaceNames();
            final int columnIndex =
                COMMON_STATS_HEADER.size() + disksColumnShift + 2 * interfaceNames.indexOf(interfaceName);
            newLine[columnIndex] = Long.toString(networkStats.getRxBytes());
            newLine[columnIndex + 1] = Long.toString(networkStats.getTxBytes());
        });
    }

    @Value
    private static class MonitoringStatsHeader {

        private static final MonitoringStatsHeader EMPTY_HEADER = new MonitoringStatsHeader(Collections.emptyList(),
                                                                                            Collections.emptyList(),
                                                                                            Collections.emptyList());
        private List<String> diskNames;
        private List<String> interfaceNames;
        private List<String> columnNames;
    }
}
