/*
 * Copyright 2021 EPAM Systems, Inc. (https://www.epam.com/)
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
import com.epam.pipeline.manager.cluster.KubernetesManager;
import com.epam.pipeline.manager.notification.NotificationManager;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.SchedulerLock;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.epam.pipeline.manager.cluster.pool.NodePoolUtils.determineActiveNodesCount;

@Service
@Slf4j
@RequiredArgsConstructor
public class NodePoolMonitoringServiceCore {
    private final NodePoolManager nodePoolManager;
    private final KubernetesManager kubernetesManager;
    private final NotificationManager notificationManager;

    @SchedulerLock(name = "NodePoolMonitoringService_monitor", lockAtMostForString = "PT10M")
    public void monitor() {
        final List<NodePool> activePools = nodePoolManager.getActivePools();
        if (CollectionUtils.isEmpty(activePools)) {
            log.debug("No active node pools found");
            return;
        }
        notificationManager.notifyFullNodePools(filterFullNodePools(activePools));
    }

    private List<NodePool> filterFullNodePools(final List<NodePool> activePools) {
        try (KubernetesClient kubernetesClient = kubernetesManager.getKubernetesClient()) {
            final List<Node> availableNodes = kubernetesManager.getNodes(kubernetesClient);
            final Set<String> activePodIds = kubernetesManager.getAllPodIds(kubernetesClient);
            return activePools.stream()
                    .filter(pool -> isFull(pool, availableNodes, activePodIds))
                    .collect(Collectors.toList());
        }
    }

    private boolean isFull(final NodePool pool, final List<Node> availableNodes, final Set<String> activePodIds) {
        final long activeNodesCount = determineActiveNodesCount(availableNodes, activePodIds, pool.getId());
        return activeNodesCount >= pool.getMaxSize();
    }
}
