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

import com.epam.pipeline.entity.cluster.container.ContainerMemoryResourcePolicy;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.manager.cluster.node.NodeResources;
import com.epam.pipeline.manager.cluster.node.NodeResourcesService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultContainerMemoryResourceService implements ContainerMemoryResourceService {

    private final NodeResourcesService nodeResourcesService;

    @Override
    public ContainerMemoryResourcePolicy policy() {
        return ContainerMemoryResourcePolicy.DEFAULT;
    }

    @Override
    public ContainerResources buildResourcesForRun(final PipelineRun run) {
        return Optional.ofNullable(run)
                .map(PipelineRun::getInstance)
                .map(nodeResourcesService::build)
                .map(NodeResources::getContainerResources)
                .orElseGet(ContainerResources::empty);
    }
}
