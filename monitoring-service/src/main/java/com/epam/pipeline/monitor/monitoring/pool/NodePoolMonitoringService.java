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

import com.epam.pipeline.entity.cluster.ContainerInstance;
import com.epam.pipeline.entity.cluster.MachineType;
import com.epam.pipeline.entity.cluster.NodeInstance;
import com.epam.pipeline.entity.cluster.PodInstance;
import com.epam.pipeline.entity.cluster.pool.NodePool;
import com.epam.pipeline.monitor.monitoring.MonitoringService;
import com.epam.pipeline.monitor.rest.CloudPipelineAPIClient;
import com.epam.pipeline.utils.KubernetesRequestParser;
import com.epam.pipeline.vo.FilterNodesVO;
import com.epam.pipeline.vo.cluster.pool.NodePoolUsage;
import com.epam.pipeline.vo.cluster.pool.Requests;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class NodePoolMonitoringService implements MonitoringService {

    private static final String NODE_POOL_ID_LABEL = "pool_id";
    private static final String PENDING_PHASE = "Pending";

    private final CloudPipelineAPIClient client;
    private final String monitorEnabledPreferenceName;
    private final String requestsNamePreferenceName;

    public NodePoolMonitoringService(
            final CloudPipelineAPIClient client,
            @Value("${preference.name.usage.node.pool.enable}") final String monitorEnabledPreferenceName,
            @Value("${preference.name.usage.node.pool.request.names}") final String requestsNamePreferenceName) {
        this.client = client;
        this.monitorEnabledPreferenceName = monitorEnabledPreferenceName;
        this.requestsNamePreferenceName = requestsNamePreferenceName;
    }

    @Override
    public void monitor() {
        if (!client.getBooleanPreference(monitorEnabledPreferenceName)) {
            log.debug("Node pool usage monitoring is not enabled");
            return;
        }
        final List<NodePool> pools = client.loadAllNodePools();
        client.saveNodePoolUsage(pools.stream()
                .map(this::buildUsage)
                .collect(Collectors.toList()));
        log.debug("Finished node pool usage monitoring");
    }

    private NodePoolUsage buildUsage(final NodePool pool) {
        final long activePoolRuns = client.loadRunsByPool(pool.getId()).stream()
                .filter(run -> Objects.nonNull(run.getInstance()) && Objects.nonNull(run.getInstance().getPoolId()))
                .count();
        final NodePoolUsage.NodePoolUsageBuilder builder = NodePoolUsage.builder()
                .nodePoolId(pool.getId())
                .totalNodesCount(pool.getCount())
                .occupiedNodesCount(Math.toIntExact(activePoolRuns));
        addRunMetrics(pool, builder);
        return builder.build();
    }

    private void addRunMetrics(final NodePool pool,
                               final NodePoolUsage.NodePoolUsageBuilder builder) {
        final Map<String, String> monitoredLabels = MapUtils.emptyIfNull(pool.getKubeLabels())
                .entrySet()
                .stream()
                .filter(entry -> Objects.nonNull(entry.getKey()) &&
                        Objects.nonNull(entry.getValue())
                        && entry.getValue().isMonitored())
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getValue()));
        if (MapUtils.isEmpty(monitoredLabels)) {
            return;
        }
        final List<PodInstance> pods = ListUtils.emptyIfNull(client.filterPods(monitoredLabels));
        final List<PodInstance> pendingPods = pods.stream()
                .filter(pod -> PENDING_PHASE.equals(pod.getPhase()) ||
                        ListUtils.emptyIfNull(pod.getContainers())
                                .stream()
                                .anyMatch(ContainerInstance::isPending)).collect(Collectors.toList());
        builder.pendingRunsCount((long) pendingPods.size());

        //Load all nodes by labels
        final FilterNodesVO filter = new FilterNodesVO();
        final Map<String, String> poolLabels = new HashMap<>();
        poolLabels.put(NODE_POOL_ID_LABEL, pool.getId().toString());
        filter.setLabels(poolLabels);
        final List<NodeInstance> poolNodes = ListUtils.emptyIfNull(client.filterNodes(filter, MachineType.KUBE));
        final Set<String> poolNodeNames = ListUtils.emptyIfNull(poolNodes)
                .stream().map(NodeInstance::getName)
                .collect(Collectors.toSet());
        final List<PodInstance> activePods = pods.stream()
                .filter(pod -> poolNodeNames.contains(pod.getNodeName()) &&
                        ListUtils.emptyIfNull(pod.getContainers())
                                .stream()
                                .allMatch(ContainerInstance::isRunning)).collect(Collectors.toList());
        builder.activeRunsCount((long) activePods.size());
        addRequests(builder, pendingPods, activePods, poolNodes);
    }

    private void addRequests(final NodePoolUsage.NodePoolUsageBuilder builder,
                             final List<PodInstance> pendingPods,
                             final List<PodInstance> activePods,
                             final List<NodeInstance> nodes) {
        final List<String> monitoredRequests = client.getObjectPreference(requestsNamePreferenceName);
        if (CollectionUtils.isEmpty(monitoredRequests)) {
            return;
        }
        final Map<String, Requests> result = new HashMap<>();
        monitoredRequests.forEach(requestName -> {
            final Requests requests = new Requests();
            requests.setTotal(nodes.stream()
                    .mapToLong(node ->
                            parseRequest(node.getAllocatable().getOrDefault(requestName, StringUtils.EMPTY)))
                    .sum());
            requests.setPending(pendingPods.stream()
                    .mapToLong(pod -> pod.getContainers().stream()
                            .mapToLong(container ->
                                    parseRequest(container.getRequests().getOrDefault(requestName, StringUtils.EMPTY)))
                            .sum())
                    .sum());
            requests.setActive(activePods.stream()
                    .mapToLong(pod -> pod.getContainers().stream()
                            .mapToLong(container ->
                                    parseRequest(container.getRequests().getOrDefault(requestName, StringUtils.EMPTY)))
                            .sum())
                    .sum());
            if (Objects.nonNull(requests.getActive())
                    || Objects.nonNull(requests.getPending())
                    || Objects.nonNull(requests.getTotal())) {
                result.put(requestName, requests);
            }
        });
        if(!MapUtils.isEmpty(result)) {
            builder.requestsStats(result);
        }
    }

    private Long parseRequest(final String value) {
        if (StringUtils.isBlank(value)) {
            return 0L;
        }
        if (NumberUtils.isDigits(value)) {
            return Long.parseLong(value);
        }
        final Long requestValue = KubernetesRequestParser.parseRequest(value);
        if (Objects.isNull(requestValue)) {
            log.error("Failed to parse k8s request value {}", value);
            return 0L;
        }
        return requestValue;
    }
}
