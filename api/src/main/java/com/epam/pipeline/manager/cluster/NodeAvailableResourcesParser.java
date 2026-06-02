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

package com.epam.pipeline.manager.cluster;

import com.epam.pipeline.entity.cluster.ContainerInstance;
import com.epam.pipeline.entity.cluster.NodeInstance;
import com.epam.pipeline.entity.cluster.NodeResources;
import com.epam.pipeline.entity.cluster.PodInstance;
import com.epam.pipeline.utils.KubernetesMemoryParser;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public final class NodeAvailableResourcesParser {
    private static final String CPU_RESOURCE = "cpu";
    private static final String GPU_RESOURCE = "nvidia.com/gpu";
    private static final String MEMORY_RESOURCE = "memory";
    private static final String MILLICORES_MARKER = "m";

    private NodeAvailableResourcesParser() {
    }

    public static NodeResources parse(final NodeInstance node, final List<PodInstance> pods,
                                      final boolean showDetails) {
        final NodeResources resources = NodeResources.builder()
                .nodeName(node.getName())
                .total(toQuantities(node.getAllocatable(), false))
                .build();
        if (CollectionUtils.isEmpty(pods)) {
            resources.setUsed(NodeResources.Quantities.empty()); // empty node
            return resources;
        }
        resources.setUsed(collectAllocatedResources(pods));
        if (showDetails) {
            resources.setDetails(collectDetailsForRuns(pods));
        }
        return resources;
    }

    private static NodeResources.Quantities collectAllocatedResources(final List<PodInstance> pods) {
        return collectContainersResources(ListUtils.emptyIfNull(pods).stream()
                .flatMap(pod -> ListUtils.emptyIfNull(pod.getContainers()).stream()));
    }

    private static List<NodeResources.RunDetails> collectDetailsForRuns(final List<PodInstance> pods) {
        return ListUtils.emptyIfNull(pods).stream()
                .map(NodeAvailableResourcesParser::podToNodeDetails)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private static Long parseMemory(final String memoryValue) {
        if (StringUtils.isBlank(memoryValue)) {
            return null;
        }
        if (NumberUtils.isDigits(memoryValue)) {
            return Long.parseLong(memoryValue);
        }
        final Long memory = KubernetesMemoryParser.parseMemoryToBytes(memoryValue);
        if (Objects.isNull(memory)) {
            log.error("Failed to parse k8s memory value {}", memoryValue);
            return null;
        }
        return memory;
    }

    private static Long parseCpu(final String cpuValue, final boolean roundUp) {
        if (StringUtils.isBlank(cpuValue)) {
            return null;
        }
        if (NumberUtils.isDigits(cpuValue)) {
            return Long.parseLong(cpuValue);
        }
        if (cpuValue.endsWith(MILLICORES_MARKER)) {
            return millicoresToCpuUnits(cpuValue, roundUp);
        }
        log.error("Failed to parse k8s CPU value {}", cpuValue);
        return null;
    }

    private static long millicoresToCpuUnits(final String cpuValue, final boolean roundUp) {
        final long millicores = Long.parseLong(
                StringUtils.substring(cpuValue, 0, cpuValue.length() - 1));
        final double cpuUnits = (double) (millicores / 1000);
        return (long) (roundUp ? Math.ceil(cpuUnits) : Math.floor(cpuUnits));
    }

    private static Long pargeGpu(final String gpuValue) {
        if (StringUtils.isBlank(gpuValue)) {
            return null;
        }
        if (NumberUtils.isDigits(gpuValue)) {
            return Long.parseLong(gpuValue);
        }
        log.error("Failed to parse k8s GPU value {}", gpuValue);
        return null;
    }

    private static NodeResources.Quantities toQuantities(final Map<String, String> quantities, final boolean roundUp) {
        return NodeResources.Quantities.builder()
                .cpu(parseCpu(quantities.get(CPU_RESOURCE), roundUp))
                .memory(parseMemory(quantities.get(MEMORY_RESOURCE)))
                .gpu(pargeGpu(quantities.get(GPU_RESOURCE)))
                .build();
    }

    private static Long nullSafeSum(final Long term1, final Long term2) {
        return Long.sum(zeroIfNull(term1), zeroIfNull(term2));
    }

    private static Long zeroIfNull(final Long value) {
        return Optional.ofNullable(value).orElse(0L);
    }

    private static NodeResources.RunDetails podToNodeDetails(final PodInstance pod) {
        if (!MapUtils.emptyIfNull(pod.getLabels()).containsKey(KubernetesConstants.RUN_ID_LABEL)) {
            // skip if pod has not 'runid' label
            return null;
        }
        final String runIdValue = pod.getLabels().get(KubernetesConstants.RUN_ID_LABEL);
        if (StringUtils.isBlank(runIdValue) || !NumberUtils.isDigits(runIdValue)) {
            // skip if run is not assigned to pod
            return null;
        }
        return NodeResources.RunDetails.builder()
                .allocated(collectContainersResources(ListUtils.emptyIfNull(pod.getContainers()).stream()))
                .runId(Long.parseLong(runIdValue))
                // if owner label was not provided will try to fetch from DB later
                .owner(pod.getLabels().getOrDefault(KubernetesConstants.OWNER_LABEL, null))
                .build();
    }

    private static NodeResources.Quantities collectContainersResources(
            final Stream<ContainerInstance> containersStream) {
        return containersStream
                .map(ContainerInstance::getRequests)
                .filter(MapUtils::isNotEmpty)
                .map(quantities -> toQuantities(quantities, true))
                .reduce(NodeResources.Quantities.builder().build(), (q1, q2) ->
                        NodeResources.Quantities.builder()
                                .cpu(nullSafeSum(q1.getCpu(), q2.getCpu()))
                                .gpu(nullSafeSum(q1.getGpu(), q2.getGpu()))
                                .memory(nullSafeSum(q1.getMemory(), q2.getMemory()))
                                .build());
    }
}
