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
import lombok.Value;
import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@SuppressWarnings("checkstyle:MagicNumber")
public class MonitoringStatsWriterTest {

    private static final int NUM_CORES = 2;
    private static final long MAX_MEM = 1024;
    private static final MonitoringStats.ContainerSpec CONTAINER_SPEC = getContainerSpec(NUM_CORES, MAX_MEM);
    private static final List<String> END_TIMES = Arrays.asList("2020-01-23T10:18:00", "2020-01-23T10:19:00");
    private static final List<Double> CPU_LOADS = Arrays.asList(0.01, 0.02);
    private static final List<Long> MEM_USAGES = Arrays.asList(256L, 512L);
    private static final List<DiskInfo> DISK_INFOS =
        Arrays.asList(new DiskInfo("disk1", 1000, 255),
                      new DiskInfo("disk2", 500, 417));
    private static final List<String> NETWORK_INTERFACES = Arrays.asList("int1", "int2");
    private static final List<Long> NETWORK_INTERFACES_RX = Arrays.asList(101L, 201L);
    private static final List<Long> NETWORK_INTERFACES_TX = Arrays.asList(102L, 202L);
    private static final double HUNDRED_PERCENTS = 100.0;

    @Test
    public void testMonitoringStatsToCsvConversion() {
        final List<MonitoringStats> stats = createStatsList();
        final MonitoringStatsWriter monitoringStatsWriter = new MonitoringStatsWriter();
        final String csvInfo = monitoringStatsWriter.convertStatsToCsvString(stats);
        final String[] linesOfTables = csvInfo.split("\\n");
        Assert.assertEquals(3, linesOfTables.length);
        final List<String[]> table = Arrays.stream(linesOfTables)
            .map(line -> line.replaceAll("\"", ""))
            .map(line -> line.split(",", -1))
            .peek(cells ->
                      Assert.assertEquals(5 + 2 * DISK_INFOS.size() + 2 * NETWORK_INTERFACES.size(),
                                          cells.length)
            )
            .collect(Collectors.toList());

        final String[] firstStatEntry = table.get(1);
        final long totalSpace1 = DISK_INFOS.get(0).totalSpace;
        final long usedSpace1 = DISK_INFOS.get(0).usedSpace;
        Assert.assertEquals(totalSpace1, Long.parseLong(firstStatEntry[5]));
        Assert.assertEquals(0,
                            Double.compare(HUNDRED_PERCENTS * usedSpace1 / totalSpace1,
                                           Double.parseDouble(firstStatEntry[6])));
        Assert.assertEquals(StringUtils.EMPTY, firstStatEntry[7]);
        Assert.assertEquals(StringUtils.EMPTY, firstStatEntry[8]);
        final long inBytes1 = NETWORK_INTERFACES_RX.get(0);
        final long outBytes1 = NETWORK_INTERFACES_TX.get(0);
        Assert.assertEquals(inBytes1, Long.parseLong(firstStatEntry[9]));
        Assert.assertEquals(outBytes1, Long.parseLong(firstStatEntry[10]));
        Assert.assertEquals(StringUtils.EMPTY, firstStatEntry[11]);
        Assert.assertEquals(StringUtils.EMPTY, firstStatEntry[12]);

        final String[] secondStatEntry = table.get(2);
        Assert.assertEquals(StringUtils.EMPTY, secondStatEntry[5]);
        Assert.assertEquals(StringUtils.EMPTY, secondStatEntry[6]);
        final long totalSpace2 = DISK_INFOS.get(1).totalSpace;
        final long usedSpace2 = DISK_INFOS.get(1).usedSpace;
        Assert.assertEquals(totalSpace2, Long.parseLong(secondStatEntry[7]));
        Assert.assertEquals(0,
                            Double.compare(HUNDRED_PERCENTS * usedSpace2 / totalSpace2,
                                           Double.parseDouble(secondStatEntry[8])));
        Assert.assertEquals(StringUtils.EMPTY, secondStatEntry[9]);
        Assert.assertEquals(StringUtils.EMPTY, secondStatEntry[10]);
        final long inBytes2 = NETWORK_INTERFACES_RX.get(1);
        final long outBytes2 = NETWORK_INTERFACES_TX.get(1);
        Assert.assertEquals(inBytes2, Long.parseLong(secondStatEntry[11]));
        Assert.assertEquals(outBytes2, Long.parseLong(secondStatEntry[12]));
    }

    private List<MonitoringStats> createStatsList() {
        return IntStream.range(0, 2).mapToObj(i -> createMonitoringStats(END_TIMES.get(i),
                                                                         CPU_LOADS.get(i),
                                                                         MAX_MEM,
                                                                         MEM_USAGES.get(i),
                                                                         getStatsByDisk(DISK_INFOS.get(i)),
                                                                         NETWORK_INTERFACES.get(i),
                                                                         NETWORK_INTERFACES_RX.get(i),
                                                                         NETWORK_INTERFACES_TX.get(i))
        ).collect(Collectors.toList());
    }

    private Map<String, MonitoringStats.DisksUsage.DiskStats> getStatsByDisk(final DiskInfo... infos) {
        return Arrays.stream(infos)
            .collect(Collectors.toMap(DiskInfo::getName, i -> getDiskStats(i.getTotalSpace(), i.getUsedSpace())));
    }

    private MonitoringStats createMonitoringStats(final String endTime, final double cpuLoad, final long maxMem,
                                                  final long memUsage,
                                                  final Map<String, MonitoringStats.DisksUsage.DiskStats> statsByDevice,
                                                  final String interfaceName, final long bytesRx, final long bytesTx) {
        final MonitoringStats monitoringStat1 = new MonitoringStats();
        monitoringStat1.setEndTime(endTime);
        monitoringStat1.setContainerSpec(CONTAINER_SPEC);
        monitoringStat1.setCpuUsage(getCpuUsage(cpuLoad));
        monitoringStat1.setMemoryUsage(createMemoryUsage(maxMem, memUsage));
        setDiskUsageStats(monitoringStat1, statsByDevice);
        setUsageForOneInterface(monitoringStat1, interfaceName, bytesRx, bytesTx);
        return monitoringStat1;
    }

    private static void setDiskUsageStats(final MonitoringStats stats,
                                          final Map<String, MonitoringStats.DisksUsage.DiskStats> statsByDevice) {
        final MonitoringStats.DisksUsage disksUsage = new MonitoringStats.DisksUsage();
        disksUsage.setStatsByDevices(statsByDevice);
        stats.setDisksUsage(disksUsage);
    }

    private static MonitoringStats.CPUUsage getCpuUsage(final double load) {
        final MonitoringStats.CPUUsage cpuUsage = new MonitoringStats.CPUUsage();
        cpuUsage.setLoad(load);
        return cpuUsage;
    }

    private static MonitoringStats.ContainerSpec getContainerSpec(final int numCores, final long maxMem) {
        final MonitoringStats.ContainerSpec containerSpec = new MonitoringStats.ContainerSpec();
        containerSpec.setNumberOfCores(numCores);
        containerSpec.setMaxMemory(maxMem);
        return containerSpec;
    }

    private static MonitoringStats.DisksUsage.DiskStats getDiskStats(final long capacity, final long usable) {
        final MonitoringStats.DisksUsage.DiskStats value3 = new MonitoringStats.DisksUsage.DiskStats();
        value3.setCapacity(capacity);
        value3.setUsableSpace(usable);
        return value3;
    }

    private static void setUsageForOneInterface(final MonitoringStats stats,
                                                final String interfaceName,
                                                final long bytesRx,
                                                final long bytesTx) {
        final MonitoringStats.NetworkUsage networkUsage = new MonitoringStats.NetworkUsage();
        final MonitoringStats.NetworkUsage.NetworkStats value = new MonitoringStats.NetworkUsage.NetworkStats();
        value.setRxBytes(bytesRx);
        value.setTxBytes(bytesTx);
        networkUsage.setStatsByInterface(Collections.singletonMap(interfaceName, value));
        stats.setNetworkUsage(networkUsage);
    }

    private static MonitoringStats.MemoryUsage createMemoryUsage(final long capacity, final long usage) {
        final MonitoringStats.MemoryUsage memoryUsage = new MonitoringStats.MemoryUsage();
        memoryUsage.setCapacity(capacity);
        memoryUsage.setUsage(usage);
        return memoryUsage;
    }

    @Value
    private static class DiskInfo {

        private String name;
        private long totalSpace;
        private long usedSpace;
    }
}
