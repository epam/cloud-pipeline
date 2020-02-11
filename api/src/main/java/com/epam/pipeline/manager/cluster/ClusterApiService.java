/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.cluster;

import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import com.epam.pipeline.controller.vo.FilterNodesVO;
import com.epam.pipeline.entity.cluster.AllowedInstanceAndPriceTypes;
import com.epam.pipeline.entity.cluster.FilterPodsRequest;
import com.epam.pipeline.entity.cluster.InstanceType;
import com.epam.pipeline.entity.cluster.MasterNode;
import com.epam.pipeline.entity.cluster.NodeInstance;
import com.epam.pipeline.entity.cluster.monitoring.MonitoringStats;
import com.epam.pipeline.manager.cluster.performancemonitoring.UsageMonitoringManager;
import com.epam.pipeline.manager.security.acl.AclMask;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PostFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ClusterApiService {

    private final NodesManager nodesManager;
    private final UsageMonitoringManager usageMonitoringManager;
    private final InstanceOfferManager instanceOfferManager;

    @PostFilter("hasRole('ADMIN') OR @grantPermissionManager.nodePermission(filterObject, 'READ')")
    public List<NodeInstance> getNodes() {
        return nodesManager.getNodes();
    }

    @PostFilter("hasRole('ADMIN') OR @grantPermissionManager.nodePermission(filterObject, 'READ')")
    public List<NodeInstance> filterNodes(FilterNodesVO filterNodesVO) {
        return nodesManager.filterNodes(filterNodesVO);
    }

    @PreAuthorize("hasRole('ADMIN') OR @grantPermissionManager.nodePermission(#name, 'READ')")
    @AclMask
    public NodeInstance getNode(String name) {
        return nodesManager.getNode(name);
    }

    @PreAuthorize("hasRole('ADMIN') OR @grantPermissionManager.nodePermission(#name, 'READ')")
    @AclMask
    public NodeInstance getNode(String name, FilterPodsRequest request) {
        return nodesManager.getNode(name, request);
    }

    @PreAuthorize("hasRole('ADMIN') OR @grantPermissionManager.nodeStopPermission(#name, 'EXECUTE')")
    @AclMask
    public NodeInstance terminateNode(String name) {
        return nodesManager.terminateNode(name);
    }

    @PreAuthorize("hasRole('ADMIN') OR @grantPermissionManager.nodePermission(#nodeName, 'READ')")
    public List<MonitoringStats> getStatsForNode(String nodeName, final LocalDateTime from, final LocalDateTime to) {
        return usageMonitoringManager.getStatsForNode(nodeName, from, to);
    }

    @PreAuthorize("hasRole('ADMIN') OR @grantPermissionManager.nodePermission(#name, 'READ')")
    public InputStream getUsageStatisticsFile(final String name, final LocalDateTime from, final LocalDateTime to,
                                              final Duration interval) {
        return usageMonitoringManager.getStatsForNodeAsInputStream(name, from, to, interval);
    }

    public List<InstanceType> getAllowedInstanceTypes(final Long regionId, final Boolean spot) {
        return instanceOfferManager.getAllowedInstanceTypes(regionId, spot);
    }

    public List<InstanceType> getAllowedToolInstanceTypes(final Long regionId, final Boolean spot) {
        return instanceOfferManager.getAllowedToolInstanceTypes(regionId, spot);
    }

    public AllowedInstanceAndPriceTypes getAllowedInstanceAndPriceTypes(final Long toolId, final Long regionId,
                                                                        final Boolean spot) {
        return instanceOfferManager.getAllowedInstanceAndPriceTypes(toolId, regionId, spot);
    }

    public List<MasterNode> getMasterNodes() {
        return nodesManager.getMasterNodes();
    }
}
