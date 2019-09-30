/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

import java.util.List;

import com.epam.pipeline.controller.vo.FilterNodesVO;
import com.epam.pipeline.entity.cluster.*;
import com.epam.pipeline.manager.cluster.performancemonitoring.CAdvisorMonitoringManager;
import com.epam.pipeline.entity.cluster.monitoring.MonitoringStats;
import com.epam.pipeline.manager.security.acl.AclMask;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PostFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class ClusterApiService {

    @Autowired
    private NodesManager nodesManager;

    @Autowired
    private CAdvisorMonitoringManager cAdvisorMonitorManager;

    @Autowired
    private InstanceOfferManager instanceOfferManager;

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
    public List<MonitoringStats> getStatsForNode(String nodeName) {
        return cAdvisorMonitorManager.getStatsForNode(nodeName);
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
