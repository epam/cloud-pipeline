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
import com.epam.pipeline.manager.notification.NotificationManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.SchedulerLock;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class NodePoolMonitoringServiceCore {
    private final NodePoolManager nodePoolManager;
    private final NotificationManager notificationManager;
    private final KubernetesPoolService kubernetesPoolService;

    @SchedulerLock(name = "NodePoolMonitoringService_monitor", lockAtMostForString = "PT10M")
    public void monitor() {
        final List<NodePool> activePools = nodePoolManager.getActivePools();
        if (CollectionUtils.isEmpty(activePools)) {
            log.debug("No active node pools found");
            return;
        }
        notificationManager.notifyFullNodePools(kubernetesPoolService.filterFullNodePools(activePools));
    }
}
