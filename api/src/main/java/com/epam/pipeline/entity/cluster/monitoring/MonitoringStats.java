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

package com.epam.pipeline.entity.cluster.monitoring;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;

@Setter
@Getter
@EqualsAndHashCode
public class MonitoringStats {

    private String startTime;
    private String endTime;

    private long millsInPeriod;

    private ContainerSpec containerSpec;

    private CPUUsage cpuUsage;
    private MemoryUsage memoryUsage;
    private DisksUsage disksUsage;
    private NetworkUsage networkUsage;

    @Setter
    @Getter
    public static class CPUUsage {
        private double load;
        private double max;
    }


    @Setter
    @Getter
    public static class MemoryUsage {
        private long capacity;
        private long usage;
        private long max;
    }

    @Setter
    @Getter
    public static class DisksUsage {

        private Map<String, DiskStats> statsByDevices = new LinkedHashMap<>();

        @Setter
        @Getter
        public static class DiskStats {
            private long capacity;
            private long usableSpace;
        }
    }

    @Setter
    @Getter
    public static class NetworkUsage {

        private Map<String, NetworkStats> statsByInterface = new LinkedHashMap<>();

        @Setter
        @Getter
        public static class NetworkStats {
            private long txBytes;
            private long rxBytes;
        }
    }

    @Setter
    @Getter
    public static class ContainerSpec {
        private long maxMemory;
        private int numberOfCores;
    }
}
