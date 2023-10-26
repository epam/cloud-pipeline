/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.epam.pipeline.manager.cluster.container;

import com.epam.pipeline.entity.cluster.InstanceType;
import com.epam.pipeline.entity.cluster.container.ContainerMemoryResourcePolicy;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.manager.cluster.InstanceOfferManager;
import com.epam.pipeline.manager.cluster.KubernetesConstants;
import com.epam.pipeline.manager.preference.AbstractSystemPreference;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import io.fabric8.kubernetes.api.model.Quantity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AutoContainerMemoryResourceService implements ContainerMemoryResourceService {

    private final InstanceOfferManager instanceOfferManager;
    private final PreferenceManager preferenceManager;

    @Override
    public ContainerMemoryResourcePolicy policy() {
        return ContainerMemoryResourcePolicy.AUTO;
    }

    @Override
    public ContainerResources buildResourcesForRun(final PipelineRun run) {
        return ContainerResources.builder()
                .requests(Collections.singletonMap(KubernetesConstants.MEM_RESOURCE_NAME,
                        new Quantity(getMemRequestMiB() + KubernetesConstants.MIB_UNIT)))
                .limits(Collections.singletonMap(KubernetesConstants.MEM_RESOURCE_NAME,
                        new Quantity(getMemLimitMiB(run) + KubernetesConstants.MIB_UNIT)))
                .build();
    }

    private int getMemRequestMiB() {
        final int requestMemGiB = get(SystemPreferences.LAUNCH_CONTAINER_MEMORY_RESOURCE_REQUEST);
        return requestMemGiB * KubernetesConstants.MIB_IN_GIB;
    }

    private int getMemLimitMiB(final PipelineRun run) {
        final int totalMemMiB = getNodeMemMiB(run);
        final int kubeletMemMiB = normalize(totalMemMiB, SystemPreferences.CLUSTER_NODE_KUBELET_MEM_RATIO,
                SystemPreferences.CLUSTER_NODE_KUBELET_MEM_MIN, SystemPreferences.CLUSTER_NODE_KUBELET_MEM_MAX);
        final int systemMemMiB = normalize(totalMemMiB, SystemPreferences.CLUSTER_NODE_SYSTEM_MEM_RATIO,
                SystemPreferences.CLUSTER_NODE_SYSTEM_MEM_MIN, SystemPreferences.CLUSTER_NODE_SYSTEM_MEM_MAX);
        final int extraMemMiB = normalize(totalMemMiB, SystemPreferences.CLUSTER_NODE_EXTRA_MEM_RATIO,
                SystemPreferences.CLUSTER_NODE_EXTRA_MEM_MIN, SystemPreferences.CLUSTER_NODE_EXTRA_MEM_MAX);
        return Math.max(0, totalMemMiB - kubeletMemMiB - systemMemMiB - extraMemMiB);
    }

    private int getNodeMemMiB(final PipelineRun run) {
        final float nodeMemGiB = getNodeMemGiB(run);
        return Math.round(nodeMemGiB * KubernetesConstants.MIB_IN_GIB);
    }

    private float getNodeMemGiB(final PipelineRun run) {
        return getInstanceType(run).map(InstanceType::getMemory).orElse(0.0f);
    }

    private Optional<InstanceType> getInstanceType(final PipelineRun run) {
        final RunInstance instance = run.getInstance();
        final String nodeType = instance.getNodeType();
        return ListUtils.emptyIfNull(instanceOfferManager.getAllInstanceTypes(instance.getCloudRegionId(),
                        instance.getSpot()))
                .stream()
                .filter(type -> nodeType.equals(type.getName()))
                .findFirst();
    }

    private int normalize(final int total, final AbstractSystemPreference<Integer> ratio,
                          final AbstractSystemPreference<Integer> min, final AbstractSystemPreference<Integer> max) {
        return normalize(total, get(ratio), get(min), get(max));
    }

    private int normalize(final int total, final int ratio, final int min, final int max) {
        return (int) Math.min(Math.max(min, Math.round(total * (ratio / 100.0))), max);
    }

    private <T> T get(final AbstractSystemPreference<T> preference) {
        return Optional.of(preference)
                .map(preferenceManager::getPreference)
                .orElseGet(preference::getDefaultValue);
    }
}
