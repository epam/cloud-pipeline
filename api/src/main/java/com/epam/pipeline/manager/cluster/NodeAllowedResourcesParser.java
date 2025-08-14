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
import com.epam.pipeline.entity.cluster.NodeResourceInfo;
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

@Slf4j
public final class NodeAllowedResourcesParser {
    private static final String CPU_RESOURCE = "cpu";
    private static final String GPU_RESOURCE = "nvidia.com/gpu";
    private static final String MEMORY_RESOURCE = "memory";
    private static final String MILLICORES_MARKER = "m";

    private NodeAllowedResourcesParser() {
    }

    public static NodeResourceInfo parse(final NodeInstance node, final List<PodInstance> pods) {
        final NodeResourceInfo resources = NodeResourceInfo.builder()
                .nodeName(node.getName())
                .total(toResource(node.getAllocatable(), false))
                .build();
        if (CollectionUtils.isEmpty(pods)) {
            // empty node
            resources.setUsed(NodeResourceInfo.Resource.empty());
            return resources;
        }
        resources.setUsed(collectAllocatedResources(pods));
        return resources;
    }

    private static NodeResourceInfo.Resource collectAllocatedResources(final List<PodInstance> pods) {
        return ListUtils.emptyIfNull(pods).stream()
                .flatMap(pod -> ListUtils.emptyIfNull(pod.getContainers()).stream())
                .map(ContainerInstance::getRequests)
                .filter(MapUtils::isNotEmpty)
                .map(quantities -> toResource(quantities, true))
                .reduce(NodeResourceInfo.Resource.builder().build(), (r1, r2) ->
                        NodeResourceInfo.Resource.builder()
                                .cpu(nullSafeSum(r1.getCpu(), r2.getCpu()))
                                .gpu(nullSafeSum(r1.getGpu(), r2.getGpu()))
                                .memory(nullSafeSum(r1.getMemory(), r2.getMemory()))
                                .build());
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

    private static NodeResourceInfo.Resource toResource(final Map<String, String> resource, final boolean roundUp) {
        return NodeResourceInfo.Resource.builder()
                .cpu(parseCpu(resource.get(CPU_RESOURCE), roundUp))
                .memory(parseMemory(resource.get(MEMORY_RESOURCE)))
                .gpu(pargeGpu(resource.get(GPU_RESOURCE)))
                .build();
    }

    private static Long nullSafeSum(final Long term1, final Long term2) {
        return Long.sum(zeroIfNull(term1), zeroIfNull(term2));
    }

    private static Long zeroIfNull(final Long value) {
        return Optional.ofNullable(value).orElse(0L);
    }
}
