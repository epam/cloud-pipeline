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

import com.epam.pipeline.entity.cluster.CloudRegionsConfiguration;
import com.epam.pipeline.entity.cluster.NetworkConfiguration;
import com.epam.pipeline.entity.cluster.container.ContainerMemoryResourcePolicy;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.manager.cluster.InstanceOfferManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class NodeSwapContainerMemoryResourceService extends AbstractNodeContainerMemoryResourceService {

    private static final String SWAP_RATIO = "swap_ratio";

    public NodeSwapContainerMemoryResourceService(final InstanceOfferManager instanceOfferManager,
                                                  final PreferenceManager preferenceManager) {
        super(instanceOfferManager, preferenceManager);
    }

    @Override
    protected double getMemorySize(final PipelineRun run) {
        final double nodeRam = getNodeRam(run);
        return nodeRam + getSwapSize(run, nodeRam);
    }

    private double getSwapSize(final PipelineRun run, final double nodeRam) {
        final CloudRegionsConfiguration preference = getPreferenceManager()
                .getPreference(SystemPreferences.CLUSTER_NETWORKS_CONFIG);
        if (preference == null) {
            return 0.0;
        }
        final double swapRatio = ListUtils.emptyIfNull(preference.getRegions()).stream()
                .filter(regionConfig -> regionConfig.getRegionId().equals(run.getInstance().getCloudRegionId()))
                .findFirst()
                .map(NetworkConfiguration::getSwap)
                .orElse(Collections.emptyList())
                .stream()
                .filter(swap -> SWAP_RATIO.equals(swap.getName()) && NumberUtils.isNumber(swap.getPath()))
                .findFirst()
                .map(swap -> NumberUtils.createDouble(swap.getPath())
                ).orElse(0.0);
        return swapRatio * nodeRam;
    }

    @Override
    public ContainerMemoryResourcePolicy policy() {
        return ContainerMemoryResourcePolicy.NODE_SWAP;
    }
}
