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

package com.epam.pipeline.entity.cluster.pool;

import com.epam.pipeline.entity.pipeline.run.container.RunContainerSpec;
import lombok.Data;

import java.util.Map;

/**
 * How the nodes of a node pool launch runs: where a run's container is placed, and what it may ask of the node.
 *
 * <p>Stored as JSON on the pool itself, and built by {@code NodePoolLaunchConfigBuilder} for a shareable pool that
 * is created without one. The {@code *RequestsEnabled} flags say which of CPU, GPU and memory are requested for a
 * container at all; the {@code *RequestsReserved} values say how much of the node is kept for the system instead of
 * being offered to a run - {@code ramRequestsReserved} either as a quantity that names its own unit
 * ({@code 1GiB}), or as a bare amount read in {@code ramRequestsUnit}.
 *
 * @see NodePool
 */
@Data
public class NodePoolLaunchConfig {

    private RunContainerSpec kubeAssignPolicy;

    private boolean cpuRequestsEnabled;
    private boolean gpuRequestsEnabled;
    private boolean ramRequestsEnabled;

    private Integer cpuRequestsReserved;
    private Integer gpuRequestsReserved;

    private String ramRequestsReserved;
    private String ramRequestsUnit;

    private Map<String, String> additionalParameters;
}
