/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.vmmonitor.service.pipeline;

import com.epam.pipeline.entity.reporter.NodeReporterHostStats;
import com.epam.pipeline.entity.reporter.NodeReporterProcessStats;
import com.epam.pipeline.entity.reporter.NodeReporterStatsLimit;
import com.epam.pipeline.entity.reporter.NodeReporterStatsType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class NodeStatsClientTest {

    /**
     * The answer of the node reporter of a host whose fs.file-max is LONG_MAX, as systemd 240 or later sets it.
     */
    private static final String HOST_STATS = "/node-reporter/host-stats-file-max-long-max.json";
    private static final String NODE = "ip-10-0-1-15.ec2.internal";
    private static final LocalDateTime TIMESTAMP = LocalDateTime.of(2026, 11, 13, 10, 15, 30, 125_000_000);
    private static final String ALL_PROCESSES = "all processes";
    private static final String DOCKERD = "dockerd";
    private static final String CONTAINERD = "containerd";
    private static final int DOCKERD_PID = 1201;
    private static final int CONTAINERD_PID = 1034;
    private static final long FILE_MAX = Long.MAX_VALUE;
    private static final long ALL_OPEN_FILES = 3520L;
    private static final long DOCKERD_LIMIT = 1073741816L;
    private static final long DOCKERD_OPEN_FILES = 61L;
    private static final long CONTAINERD_SOFT_LIMIT = 512L;
    private static final long CONTAINERD_HARD_LIMIT = 1024L;
    private static final long CONTAINERD_OPEN_FILES = 1000L;

    private NodeReporterServer server;

    @BeforeEach
    public void setUp() throws IOException {
        server = NodeReporterServer.start(HOST_STATS);
    }

    @AfterEach
    public void tearDown() {
        server.close();
    }

    @Test
    public void loadShouldReadTheStatsOfAHostWithFileMaxOfLongMax() {
        final NodeReporterHostStats stats = new NodeStatsClient("http", server.getPort()).load(server.getHost());

        assertEquals(NODE, stats.getName());
        assertEquals(TIMESTAMP, stats.getTimestamp());
        final Map<String, NodeReporterProcessStats> processes = stats.getProcesses().stream()
                .collect(Collectors.toMap(NodeReporterProcessStats::getName, Function.identity()));
        assertEquals(3, processes.size());
        assertProcess(processes.get(ALL_PROCESSES), 0, FILE_MAX, FILE_MAX, ALL_OPEN_FILES);
        assertProcess(processes.get(DOCKERD), DOCKERD_PID, DOCKERD_LIMIT, DOCKERD_LIMIT, DOCKERD_OPEN_FILES);
        assertProcess(processes.get(CONTAINERD), CONTAINERD_PID, CONTAINERD_SOFT_LIMIT, CONTAINERD_HARD_LIMIT,
                CONTAINERD_OPEN_FILES);
    }

    private static void assertProcess(final NodeReporterProcessStats process, final int pid, final long softLimit,
                                      final long hardLimit, final long openFiles) {
        assertEquals(pid, process.getPid());
        final NodeReporterStatsLimit limit = process.getLimits().get(NodeReporterStatsType.NOFILE);
        assertEquals(softLimit, limit.getSoftLimit());
        assertEquals(hardLimit, limit.getHardLimit());
        assertEquals(openFiles, process.getStats().get(NodeReporterStatsType.NOFILE).getValue());
    }
}
