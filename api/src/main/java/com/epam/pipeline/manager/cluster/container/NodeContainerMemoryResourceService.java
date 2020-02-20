/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
 * limitations under the License.
 */

package com.epam.pipeline.manager.cluster.container;

import com.epam.pipeline.entity.cluster.InstanceType;
import com.epam.pipeline.entity.cluster.container.ContainerMemoryResourcePolicy;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.manager.cluster.InstanceOfferManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import io.fabric8.kubernetes.api.model.Quantity;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.ListUtils;
import org.springframework.stereotype.Service;

import java.util.Collections;


@Service
@RequiredArgsConstructor
public class NodeContainerMemoryResourceService implements ContainerMemoryResourceService {

    private static final String MEMORY_RESOURCE = "memory";

    private final InstanceOfferManager instanceOfferManager;
    private final PreferenceManager preferenceManager;

    @Override
    public ContainerResources buildResourcesForRun(final PipelineRun run) {
        final RunInstance instance = run.getInstance();
        final String nodeType = instance.getNodeType();
        final Float nodeRam = ListUtils.emptyIfNull(
                instanceOfferManager.getAllInstanceTypes(instance.getCloudRegionId(), instance.getSpot()))
                .stream()
                .filter(type -> nodeType.equals(type.getName()))
                .findFirst()
                .map(InstanceType::getMemory)
                .orElse(0.0f);
        final int memoryRequest = preferenceManager.getPreference(SystemPreferences.LAUNCH_CONTAINER_MEMORY_RESOURCE_REQUEST);
        final int memoryLimit = Math.max(0, Math.round(nodeRam - 1.0f));
        return ContainerResources
                .builder()
                .requests(Collections.singletonMap(MEMORY_RESOURCE, new Quantity(String.valueOf(memoryRequest))))
                .limits(Collections.singletonMap(MEMORY_RESOURCE, new Quantity(String.valueOf(memoryLimit))))
                .build();
    }

    @Override
    public ContainerMemoryResourcePolicy policy() {
        return ContainerMemoryResourcePolicy.NODE;
    }
}
