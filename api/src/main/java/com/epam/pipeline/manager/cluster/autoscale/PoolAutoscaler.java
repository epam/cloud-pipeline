/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.cluster.autoscale;

import com.epam.pipeline.controller.vo.cluster.pool.NodePoolVO;
import com.epam.pipeline.entity.cluster.pool.NodePool;
import com.epam.pipeline.manager.cluster.KubernetesManager;
import com.epam.pipeline.manager.cluster.pool.NodePoolManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.mapper.cluster.pool.NodePoolMapper;
import com.epam.pipeline.utils.DoubleUtils;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

import static com.epam.pipeline.manager.cluster.pool.NodePoolUtils.determineActiveNodesCount;

@Component
@RequiredArgsConstructor
@Slf4j
public class PoolAutoscaler {

    public static final int PERCENT_MULTIPLIER = 100;
    private final NodePoolManager poolManager;
    private final NodePoolMapper poolMapper;
    private final KubernetesManager kubernetesManager;
    private final PreferenceManager preferenceManager;

    public void adjustPoolSizes() {
        if (preferenceManager.findPreference(SystemPreferences.SYSTEM_MAINTENANCE_MODE).orElse(false)) {
            log.debug("Platform is in a maintenance mode, operation is temporary unavailable");
            return;
        }
        try (KubernetesClient kubernetesClient = kubernetesManager.getKubernetesClient()) {
            final List<Node> availableNodes = kubernetesManager.getNodes(kubernetesClient);
            final Set<String> activePodIds = kubernetesManager.getAllPodIds(kubernetesClient);
            poolManager.getActivePools()
                    .forEach(pool ->
                            adjustPoolSize(pool, availableNodes, activePodIds));
        }
    }

    private void adjustPoolSize(final NodePool pool,
                                final List<Node> availableNodes,
                                final Set<String> activePodIds) {
        if (!pool.isAutoscaled()) {
            return;
        }
        final long activePoolNodes = determineActiveNodesCount(availableNodes, activePodIds, pool.getId());
        final double occupiedPercent = pool.getCount() == 0 ? PERCENT_MULTIPLIER :
                (double)activePoolNodes / pool.getCount() * PERCENT_MULTIPLIER;
        log.debug("{} occupied node(s) match pool[{}] with total size {}, {}% is occupied",
                activePoolNodes, pool.getId(), pool.getCount(), occupiedPercent);

        if (pool.getCount() < pool.getMaxSize() &&
                DoubleUtils.compare(occupiedPercent, pool.getScaleUpThreshold()) > 0) {
            final int increasedSize = Math.min(pool.getMaxSize(), pool.getCount() + pool.getScaleStep());
            log.debug("Increasing pool[{}] size from {} to {}", pool.getId(), pool.getCount(), increasedSize);
            updatePoolSize(pool, increasedSize);
        } else if (pool.getCount() > pool.getMinSize() &&
                DoubleUtils.compare(occupiedPercent, pool.getScaleDownThreshold()) < 0) {
            final int decreasedSize = Math.max(pool.getMinSize(), pool.getCount() - pool.getScaleStep());
            log.debug("Decreasing pool[{}] size from {} to {}", pool.getId(), pool.getCount(), decreasedSize);
            updatePoolSize(pool, decreasedSize);
        }
    }

    private void updatePoolSize(final NodePool pool, final int newSize) {
        final NodePoolVO nodePoolVO = poolMapper.toVO(pool);
        nodePoolVO.setCount(newSize);
        poolManager.createOrUpdate(nodePoolVO);
    }
}
