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

import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.manager.cluster.InstanceOfferManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import io.fabric8.kubernetes.api.model.Quantity;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;

import java.util.Collections;

@Slf4j
@Getter
@RequiredArgsConstructor
public abstract class AbstractNodeContainerMemoryResourceService implements ContainerMemoryResourceService {

    private static final String MEMORY_RESOURCE = "memory";
    private static final double MEMORY_LIMIT_GAP = 1.0;

    private final InstanceOfferManager instanceOfferManager;
    private final PreferenceManager preferenceManager;

    @Override
    public ContainerResources buildResourcesForRun(final PipelineRun run) {
        log.debug("Building memory requirements for run {}", run.getId());
        final int memoryRequest = preferenceManager.getPreference(
                SystemPreferences.LAUNCH_CONTAINER_MEMORY_RESOURCE_REQUEST);
        final double nodeRam = getMemorySize(run);
        log.debug("Node RAM is {}", nodeRam);
        final long memoryLimit = Math.max(0, Math.round(nodeRam - MEMORY_LIMIT_GAP));
        log.debug("Calculated memory limit is {}", memoryLimit);
        final ContainerResources.ContainerResourcesBuilder requests = ContainerResources
                .builder()
                .requests(Collections.singletonMap(MEMORY_RESOURCE, new Quantity(String.valueOf(memoryRequest))));
        if (memoryLimit > 0) {
            requests.limits(Collections.singletonMap(MEMORY_RESOURCE, new Quantity(String.valueOf(memoryLimit))));
        }
        return requests.build();
    }

    abstract double getMemorySize(PipelineRun run);

    protected double getNodeRam(PipelineRun run) {
        final RunInstance instance = run.getInstance();
        final String nodeType = instance.getNodeType();
        return ListUtils.emptyIfNull(
                instanceOfferManager.getAllInstanceTypes(instance.getCloudRegionId(), instance.getSpot()))
                .stream()
                .filter(type -> nodeType.equals(type.getName()))
                .findFirst()
                .map(instanceType -> (double)instanceType.getMemory())
                .orElse(0.0);
    }
}
