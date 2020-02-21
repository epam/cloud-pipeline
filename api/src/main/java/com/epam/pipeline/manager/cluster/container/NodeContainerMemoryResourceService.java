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

import com.epam.pipeline.entity.cluster.container.ContainerMemoryResourcePolicy;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.manager.cluster.InstanceOfferManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import org.springframework.stereotype.Service;

@Service
public class NodeContainerMemoryResourceService extends AbstractNodeContainerMemoryResourceService {

    public NodeContainerMemoryResourceService(final InstanceOfferManager instanceOfferManager,
                                              final PreferenceManager preferenceManager) {
        super(instanceOfferManager, preferenceManager);
    }

    @Override
    protected double getMemorySize(final PipelineRun run) {
        return getNodeRam(run);
    }

    @Override
    public ContainerMemoryResourcePolicy policy() {
        return ContainerMemoryResourcePolicy.NODE;
    }

}
