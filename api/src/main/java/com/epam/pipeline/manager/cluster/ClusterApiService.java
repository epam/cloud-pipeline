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
import com.epam.pipeline.entity.cluster.NodeDisk;
import com.epam.pipeline.entity.cluster.NodeInstance;
import com.epam.pipeline.entity.cluster.monitoring.MonitoringStats;
import com.epam.pipeline.manager.cluster.InstanceOfferManager;
import com.epam.pipeline.manager.cluster.MonitoringReportType;
import com.epam.pipeline.manager.cluster.NodeDiskManager;
import com.epam.pipeline.manager.cluster.NodesManager;
import com.epam.pipeline.manager.cluster.performancemonitoring.UsageMonitoringManager;
import com.epam.pipeline.manager.security.acl.AclMask;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PostFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import static com.epam.pipeline.security.acl.AclExpressions.NODE_READ;
import static com.epam.pipeline.security.acl.AclExpressions.NODE_READ_FILTER;
import static com.epam.pipeline.security.acl.AclExpressions.NODE_STOP;

@Service
@RequiredArgsConstructor
public class ClusterApiService {

    private final NodesManager nodesManager;
    private final NodeDiskManager nodeDiskManager;
    private final UsageMonitoringManager usageMonitoringManager;
    private final InstanceOfferManager instanceOfferManager;

    @PostFilter(NODE_READ_FILTER)
    public List<NodeInstance> getNodes() {
        return nodesManager.getNodes();
    }

    @PostFilter(NODE_READ_FILTER)
    public List<NodeInstance> filterNodes(final FilterNodesVO filterNodesVO) {
        return nodesManager.filterNodes(filterNodesVO);
    }

    @PreAuthorize(NODE_READ)
    @AclMask
    public NodeInstance getNode(final String name) {
        return nodesManager.getNode(name);
    }

    @PreAuthorize(NODE_READ)
    @AclMask
    public NodeInstance getNode(final String name, final FilterPodsRequest request) {
        return nodesManager.getNode(name, request);
    }

    @PreAuthorize(NODE_STOP)
    @AclMask
    public NodeInstance terminateNode(final String name) {
        return nodesManager.terminateNode(name);
    }

    @PreAuthorize(NODE_READ)
    public List<MonitoringStats> getStatsForNode(final String name, final LocalDateTime from, final LocalDateTime to) {
        return usageMonitoringManager.getStatsForNode(name, from, to);
    }

    @PreAuthorize(NODE_READ)
    public InputStream getUsageStatisticsFile(final String name, final LocalDateTime from, final LocalDateTime to,
                                              final Duration interval, final MonitoringReportType type) {
        return usageMonitoringManager.getStatsForNodeAsInputStream(name, from, to, interval, type);
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

    @PreAuthorize(NODE_READ)
    public List<NodeDisk> loadNodeDisks(final String name) {
        return nodeDiskManager.loadByNodeId(name);
    }
}
