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
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.manager.cluster.InstanceOfferManager;
import com.epam.pipeline.manager.cluster.KubernetesConstants;
import com.epam.pipeline.manager.preference.AbstractSystemPreference;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import io.fabric8.kubernetes.api.model.Quantity;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.ListUtils;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ConfigurableNodeResourcesService implements NodeResourcesService {

    private final PreferenceManager preferenceManager;
    private final InstanceOfferManager instanceOfferManager;

    @Override
    public NodeResources build(final RunInstance instance) {
        return build(getInstanceType(instance.getCloudRegionId(), instance.getSpot(), instance.getNodeType()));
    }

    private NodeResources build(final InstanceType type) {
        final int totalMemMiB = getNodeMemMiB(type);
        final int kubeMemMiB = resolve(totalMemMiB, SystemPreferences.CLUSTER_NODE_KUBE_MEM_RATIO,
                SystemPreferences.CLUSTER_NODE_KUBE_MEM_MIN, SystemPreferences.CLUSTER_NODE_KUBE_MEM_MAX);
        final int systemMemMiB = resolve(totalMemMiB, SystemPreferences.CLUSTER_NODE_SYSTEM_MEM_RATIO,
                SystemPreferences.CLUSTER_NODE_SYSTEM_MEM_MIN, SystemPreferences.CLUSTER_NODE_SYSTEM_MEM_MAX);
        final int extraMemMiB = resolve(totalMemMiB, SystemPreferences.CLUSTER_NODE_EXTRA_MEM_RATIO,
                SystemPreferences.CLUSTER_NODE_EXTRA_MEM_MIN, SystemPreferences.CLUSTER_NODE_EXTRA_MEM_MAX);
        final int containerLimitMemMiB = Math.max(0, totalMemMiB - (kubeMemMiB + systemMemMiB + extraMemMiB));
        final int containerRequestMemMiB = getContainerRequestMemMiB();
        return NodeResources.builder()
                .kubeMem(kubeMemMiB)
                .systemMem(systemMemMiB)
                .extraMem(extraMemMiB)
                .containerResources(ContainerResources.builder()
                        .requests(toResourcesMap(containerRequestMemMiB))
                        .limits(toResourcesMap(containerLimitMemMiB))
                        .build())
                .build();
    }

    public InstanceType getInstanceType(final Long regionId, final boolean spot, final String instanceType) {
        return findInstanceType(regionId, spot, instanceType)
                .orElseThrow(() -> new RuntimeException("Instance type has not been found"));
    }

    public Optional<InstanceType> findInstanceType(final Long regionId, final boolean spot, final String instanceType) {
        return ListUtils.emptyIfNull(instanceOfferManager.getAllInstanceTypes(regionId, spot))
                .stream()
                .filter(type -> instanceType.equals(type.getName()))
                .findFirst();
    }

    private int getNodeMemMiB(final InstanceType type) {
        final float nodeMemGiB = getNodeMemGiB(type);
        return Math.round(nodeMemGiB * KubernetesConstants.MIB_IN_GIB);
    }

    private float getNodeMemGiB(final InstanceType type) {
        return Optional.of(type).map(InstanceType::getMemory).orElse(0.0f);
    }

    private int resolve(final int total, final AbstractSystemPreference<Double> ratio,
                        final AbstractSystemPreference<Integer> min, final AbstractSystemPreference<Integer> max) {
        return resolve(total, preferenceManager.getPreference(ratio),
                preferenceManager.getPreference(min), preferenceManager.getPreference(max));
    }

    private int resolve(final int total, final double ratio, final int min, final int max) {
        return (int) Math.min(Math.max(min, Math.round(total * ratio)), max);
    }

    private int getContainerRequestMemMiB() {
        final int requestMemGiB = getContainerRequestMemGiB();
        return requestMemGiB * KubernetesConstants.MIB_IN_GIB;
    }

    private int getContainerRequestMemGiB() {
        return preferenceManager.getPreference(SystemPreferences.LAUNCH_CONTAINER_MEMORY_RESOURCE_REQUEST);
    }

    private Map<String, Quantity> toResourcesMap(final int memMiB) {
        if (memMiB <= 0) {
            return Collections.emptyMap();
        }
        return Collections.singletonMap(KubernetesConstants.MEM_RESOURCE_NAME,
                new Quantity(memMiB + KubernetesConstants.MIB_UNIT));
    }
}
