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

package com.epam.pipeline.entity.cluster.monitoring;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class MonitoringStats {

    String startTime;
    String endTime;

    long millsInPeriod;

    ContainerSpec containerSpec;

    CPUUsage cpuUsage;
    MemoryUsage memoryUsage;
    DisksUsage disksUsage;
    NetworkUsage networkUsage;

    @Setter
    @Getter
    public static class CPUUsage {
        double load;
    }


    @Setter
    @Getter
    public static class MemoryUsage {
        long capacity;
        long usage;
    }

    @Setter
    @Getter
    public static class DisksUsage {

        Map<String, DiskStats> statsByDevices = new LinkedHashMap<>();

        @Setter
        @Getter
        public static class DiskStats {
            long capacity;
            long usableSpace;
        }
    }

    @Setter
    @Getter
    public static class NetworkUsage {

        Map<String, NetworkStats> statsByInterface = new LinkedHashMap<>();

        @Setter
        @Getter
        public static class NetworkStats {
            long txBytes;
            long rxBytes;
        }
    }

    @Setter
    @Getter
    public static class ContainerSpec {
        long maxMemory;
        int numberOfCores;
    }
}
