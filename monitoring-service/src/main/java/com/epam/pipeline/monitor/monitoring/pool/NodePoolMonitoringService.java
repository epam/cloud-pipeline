/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
 *
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

package com.epam.pipeline.monitor.monitoring.pool;

import com.epam.pipeline.entity.cluster.pool.NodePool;
import com.epam.pipeline.monitor.monitoring.MonitoringService;
import com.epam.pipeline.monitor.rest.CloudPipelineAPIClient;
import com.epam.pipeline.vo.cluster.pool.NodePoolUsageRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Slf4j
public class NodePoolMonitoringService implements MonitoringService {
    private final CloudPipelineAPIClient client;
    private final String monitorEnabledPreferenceName;

    public NodePoolMonitoringService(final CloudPipelineAPIClient client,
                                     @Value("${preference.name.usage.node.pool.enable}")
                                         final String monitorEnabledPreferenceName) {
        this.client = client;
        this.monitorEnabledPreferenceName = monitorEnabledPreferenceName;
    }

    @Override
    public void monitor() {
        if (!client.getBooleanPreference(monitorEnabledPreferenceName)) {
            log.debug("Node pool usage monitoring is not enabled");
            return;
        }
        final List<NodePool> pools = client.loadAllNodePools();
        client.saveNodePoolUsage(pools.stream()
                .map(this::buildUsageRecord)
                .collect(Collectors.toList()));
        log.debug("Finished node pool usage monitoring");
    }

    private NodePoolUsageRecord buildUsageRecord(final NodePool pool) {
        final long activePoolRuns = client.loadRunsByPool(pool.getId()).stream()
                .filter(run -> Objects.nonNull(run.getInstance()) && Objects.nonNull(run.getInstance().getPoolId()))
                .count();
        return NodePoolUsageRecord.builder()
                .nodePoolId(pool.getId())
                .totalNodes(pool.getCount())
                .nodesInUse(Math.toIntExact(activePoolRuns))
                .build();
    }
}
