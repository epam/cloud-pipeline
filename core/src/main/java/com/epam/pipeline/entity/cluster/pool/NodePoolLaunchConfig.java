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
 * How runs may divide one node of a {@link NodePoolType#SHARABLE_NODE} pool between them.
 *
 * <p>Replaces the per-instance-type entries of the {@code launch.reservation.parameters} system
 * preference. The class and its {@code launch_config} column exist so that enabling sharable nodes later
 * needs no further migration; nothing populates it yet.
 *
 * <p>The {@code *Reserved} values are headroom <em>withheld</em> from the node, not an allowance: the
 * maximum a run may ask for is the instance's capacity minus the reserved amount. They are boxed, and
 * the defaults when absent are deliberately asymmetric - the preference this replaces defaults CPU and
 * RAM to 1 and GPU to 0, so a primitive {@code int} defaulting to 0 would quietly stop withholding the
 * vCPU and GiB the platform's own agents need.
 */
@Data
public class NodePoolLaunchConfig {

    private RunContainerSpec kubeAssignPolicy;

    private boolean cpuRequestsEnabled;
    private boolean gpuRequestsEnabled;
    private boolean ramRequestsEnabled;

    private Integer cpuRequestsReserved;
    private Integer gpuRequestsReserved;

    /**
     * Withheld RAM as a Kubernetes quantity, e.g. {@code 1GiB}.
     */
    private String ramRequestsReserved;
    private String ramRequestsUnit;

    /**
     * Extra run parameters applied when any of the request dimensions above is enabled. Corresponds to the
     * {@code parameters} key of the preference being replaced.
     */
    private Map<String, String> additionalParameters;
}
