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

package com.epam.pipeline.manager.cluster.node;

import com.epam.pipeline.entity.cluster.InstanceType;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.manager.cluster.KubernetesConstants;
import com.epam.pipeline.manager.cluster.container.ContainerResources;
import com.epam.pipeline.manager.cluster.InstanceTypeCRUDService;
import com.epam.pipeline.manager.preference.AbstractSystemPreference;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import io.fabric8.kubernetes.api.model.Quantity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DefaultNodeResourcesService implements NodeResourcesService {

    private final PreferenceManager preferenceManager;
    private final InstanceTypeCRUDService instanceTypeCRUDService;

    @Override
    public NodeResources build(final RunInstance instance) {
        return Optional.ofNullable(instance)
                .flatMap(this::findInstanceType)
                .map(this::build)
                .orElseGet(NodeResources::empty);
    }

    private NodeResources build(final InstanceType type) {
        final int totalMemGiB = getTotalMemGiB(type);
        final int totalMemMiB = totalMemGiB * KubernetesConstants.MIB_IN_GIB;

        final int kubeMemMiB = resolve(totalMemMiB, SystemPreferences.CLUSTER_NODE_KUBE_MEM_RATIO,
                SystemPreferences.CLUSTER_NODE_KUBE_MEM_MIN_MIB, SystemPreferences.CLUSTER_NODE_KUBE_MEM_MAX_MIB);
        final int systemMemMiB = resolve(totalMemMiB, SystemPreferences.CLUSTER_NODE_SYSTEM_MEM_RATIO,
                SystemPreferences.CLUSTER_NODE_SYSTEM_MEM_MIN_MIB, SystemPreferences.CLUSTER_NODE_SYSTEM_MEM_MAX_MIB);
        final int extraMemMiB = resolve(totalMemMiB, SystemPreferences.CLUSTER_NODE_EXTRA_MEM_RATIO,
                SystemPreferences.CLUSTER_NODE_EXTRA_MEM_MIN_MIB, SystemPreferences.CLUSTER_NODE_EXTRA_MEM_MAX_MIB);

        final int containerLimitMemMiB = Math.max(0, totalMemMiB - kubeMemMiB - systemMemMiB - extraMemMiB);
        final int containerRequestMemGiB = getContainerRequestMemGiB();

        return NodeResources.builder()
                .kubeMem(kubeMemMiB + KubernetesConstants.MIB_UNIT)
                .systemMem(systemMemMiB + KubernetesConstants.MIB_UNIT)
                .extraMem(extraMemMiB + KubernetesConstants.MIB_UNIT)
                .containerResources(ContainerResources.builder()
                        .requests(toResourceMap(KubernetesConstants.MEM_RESOURCE_NAME,
                                containerRequestMemGiB, KubernetesConstants.GIB_UNIT))
                        .limits(toResourceMap(KubernetesConstants.MEM_RESOURCE_NAME,
                                containerLimitMemMiB, KubernetesConstants.MIB_UNIT))
                        .build())
                .build();
    }

    private Optional<InstanceType> findInstanceType(final RunInstance instance) {
        return instanceTypeCRUDService.find(instance.getCloudProvider(), instance.getCloudRegionId(),
                instance.getNodeType());
    }

    private int getTotalMemGiB(final InstanceType type) {
        return Optional.of(type).map(InstanceType::getMemory).map(Math::ceil).map(Double::intValue).orElse(1);
    }

    private int resolve(final int total, final AbstractSystemPreference<Double> ratio,
                        final AbstractSystemPreference<Integer> min, final AbstractSystemPreference<Integer> max) {
        return resolve(total, preferenceManager.getPreference(ratio),
                preferenceManager.getPreference(min), preferenceManager.getPreference(max));
    }

    private int resolve(final int total, final double ratio, final int min, final int max) {
        return (int) Math.min(Math.max(min, Math.ceil(total * ratio)), max);
    }

    private int getContainerRequestMemGiB() {
        return preferenceManager.getPreference(SystemPreferences.LAUNCH_CONTAINER_MEMORY_RESOURCE_REQUEST);
    }

    private Map<String, Quantity> toResourceMap(final String key, final int value, final String unit) {
        return value <= 0 ? null : Collections.singletonMap(key, new Quantity(value + unit));
    }
}
