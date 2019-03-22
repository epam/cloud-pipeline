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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;


/*
* Class for parsing statistics json received from CAdviser
* */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RawMonitoringStats {

    private RawSpec spec;

    private List<RawStats> stats;

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RawSpec {

        private SpecCpu cpu;

        private SpecMemory memory;

        @JsonProperty(value = "has_cpu")
        private boolean hasCpu;

        @JsonProperty(value = "has_memory")
        private boolean hasMemory;

        @JsonProperty(value = "has_filesystem")
        private boolean hasFilesystem;

        @JsonProperty(value = "has_network")
        private boolean hasNetwork;

    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SpecMemory {

        private long limit;

    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SpecCpu {

        private String mask;

    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RawStats {

        private String timestamp;
        private StatsCpu cpu;
        private StatsMemory memory;
        private List<Filesystem> filesystem;
        private Network network;

    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StatsCpu {

        private CpuUsage usage;

    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CpuUsage {

        private long total;

    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StatsMemory {

        private long usage;

    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Filesystem {

        private String device;
        private String type;
        private long capacity;
        private long usage;

    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Network {

        private List<NetworkInterface> interfaces;

    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NetworkInterface {

        private String name;

        @JsonProperty(value = "rx_bytes")
        private long rxBytes;

        @JsonProperty(value = "tx_bytes")
        private long txBytes;

    }

}
