/*
 * Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
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
package com.epam.pipeline.entity.cluster.monitoring.platform.network;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Value
@Builder
@Jacksonized
public class NetworkEventFilter {

    public static final String REPORTER = "reporter";
    public static final String HOST_NAME = "host_name";
    public static final String HOST_IP = "host_ip";
    public static final String RUN_ID = "run_id";
    public static final String RESOURCE_HOST = "resource_host";
    public static final String METHOD = "method";

    List<String> reporter;
    List<String> hostname;
    List<String> hostIp;
    List<String> runId;
    List<String> resourceHost;
    List<String> method;

    @JsonIgnore
    public Map<String, List<String>> toMap() {
        return new HashMap<String, List<String>>(){
            {
                if (Objects.nonNull(reporter)) {
                    put(REPORTER, reporter);
                }
                if (Objects.nonNull(hostname)) {
                    put(HOST_NAME, hostname);
                }
                if (Objects.nonNull(hostIp)) {
                    put(HOST_IP, hostIp);
                }
                if (Objects.nonNull(runId)) {
                    put(RUN_ID, runId);
                }
                if (Objects.nonNull(resourceHost)) {
                    put(RESOURCE_HOST, resourceHost);
                }
                if (Objects.nonNull(method)) {
                    put(METHOD, method);
                }
            }
        };
    }

    public static NetworkEventFilter fromMap(final Map<String, List<String>> map) {
        NetworkEventFilterBuilder builder = NetworkEventFilter.builder();
        builder.reporter = map.getOrDefault(REPORTER, null);
        builder.hostname = map.getOrDefault(HOST_NAME, null);
        builder.hostIp = map.getOrDefault(HOST_IP, null);
        builder.runId = map.getOrDefault(RUN_ID, null);
        builder.resourceHost = map.getOrDefault(RESOURCE_HOST, null);
        builder.method = map.getOrDefault(METHOD, null);
        return builder.build();
    }
}
