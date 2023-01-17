/*
 * Copyright 2022 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.cluster.pool;

import com.epam.pipeline.entity.cluster.pool.NodePool;
import com.epam.pipeline.entity.cluster.pool.NodePoolInfo;
import com.epam.pipeline.entity.cluster.pool.NodePoolWithUsage;
import com.epam.pipeline.manager.cluster.KubernetesManager;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.epam.pipeline.manager.cluster.pool.NodePoolUtils.determineActiveNodesCount;

@Service
@RequiredArgsConstructor
public class KubernetesPoolService {
    private final KubernetesManager kubernetesManager;

    public List<NodePool> filterFullNodePools(final List<NodePool> activePools) {
        try (KubernetesClient kubernetesClient = kubernetesManager.getKubernetesClient()) {
            final List<Node> availableNodes = kubernetesManager.getNodes(kubernetesClient);
            final Set<String> activePodIds = kubernetesManager.getAllPodIds(kubernetesClient);
            return activePools.stream()
                    .filter(pool -> isFull(pool, availableNodes, activePodIds))
                    .collect(Collectors.toList());
        }
    }

    public List<NodePoolInfo> attachUsage(final List<NodePool> pools) {
        try (KubernetesClient kubernetesClient = kubernetesManager.getKubernetesClient()) {
            final List<Node> availableNodes = kubernetesManager.getNodes(kubernetesClient);
            final Set<String> activePodIds = kubernetesManager.getAllPodIds(kubernetesClient);
            return pools.stream()
                    .map(pool -> new NodePoolWithUsage(pool,
                            determineActiveNodesCount(availableNodes, activePodIds, pool.getId())))
                    .collect(Collectors.toList());
        }
    }

    private boolean isFull(final NodePool pool, final List<Node> availableNodes, final Set<String> activePodIds) {
        return determineActiveNodesCount(availableNodes, activePodIds, pool.getId())
                >= Optional.ofNullable(pool.getMaxSize()).orElse(pool.getCount());
    }
}
