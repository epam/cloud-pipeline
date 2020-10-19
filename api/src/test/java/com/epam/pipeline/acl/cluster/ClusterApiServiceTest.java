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

package com.epam.pipeline.acl.cluster;

import com.epam.pipeline.controller.vo.FilterNodesVO;
import com.epam.pipeline.entity.cluster.AllowedInstanceAndPriceTypes;
import com.epam.pipeline.entity.cluster.FilterPodsRequest;
import com.epam.pipeline.entity.cluster.InstanceType;
import com.epam.pipeline.entity.cluster.MasterNode;
import com.epam.pipeline.entity.cluster.NodeDisk;
import com.epam.pipeline.entity.cluster.NodeInstance;
import com.epam.pipeline.entity.cluster.monitoring.MonitoringStats;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.manager.cluster.InstanceOfferManager;
import com.epam.pipeline.manager.cluster.NodeDiskManager;
import com.epam.pipeline.manager.cluster.NodesManager;
import com.epam.pipeline.manager.cluster.performancemonitoring.UsageMonitoringManager;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.security.acl.AclPermission;
import com.epam.pipeline.test.acl.AbstractAclTest;
import com.epam.pipeline.test.creator.cluster.ClusterCreatorUtils;
import com.epam.pipeline.test.creator.cluster.NodeCreatorUtils;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;
import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;

public class ClusterApiServiceTest extends AbstractAclTest {

    private final FilterPodsRequest filterPodsRequest = NodeCreatorUtils.getDefaultFilterPodsRequest();
    private final NodeInstance nodeInstance = NodeCreatorUtils.getNodeInstance(1L, SIMPLE_USER);
    private final NodeInstance nodeInstanceWithoutPermission = NodeCreatorUtils.getNodeInstance(2L, TEST_STRING);
    private final PipelineRun pipelineRun = ClusterCreatorUtils.getPipelineRun(1L, SIMPLE_USER);
    private final FilterNodesVO filterNodesVO = NodeCreatorUtils.getDefaultFilterNodesVO();
    private final NodeDisk nodeDisk = NodeCreatorUtils.getDefaultNodeDisk();
    private final MonitoringStats monitoringStats = ClusterCreatorUtils.getMonitoringStats();
    private final InstanceType instanceType = NodeCreatorUtils.getDefaultInstanceType();
    private final InputStream inputStream = new ByteArrayInputStream(TEST_STRING.getBytes());

    private final List<NodeDisk> nodeDisks = Collections.singletonList(nodeDisk);
    private final List<InstanceType> instanceTypes = Collections.singletonList(instanceType);
    private final List<MonitoringStats> statsList = Collections.singletonList(monitoringStats);

    @Autowired
    private ClusterApiService clusterApiService;

    @Autowired
    private NodesManager mockNodesManager;

    @Autowired
    private PipelineRunManager mockPipelineRunManager;

    @Autowired
    private AuthManager mockAuthManager;

    @Autowired
    private UsageMonitoringManager mockUsageMonitoringManager;

    @Autowired
    private NodeDiskManager mockNodeDiskManager;

    @Autowired
    private InstanceOfferManager mockInstanceOfferManager;

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldReturnListWithNodeInstancesForAdmin() {
        doReturn(initSingleNodeInstanceList()).when(mockNodesManager).getNodes();

        assertThat(clusterApiService.getNodes()).hasSize(1).contains(nodeInstance);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldReturnListWithNodeInstancesWhenPermissionIsGranted() {
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();
        initAclEntity(nodeInstance, AclPermission.READ);
        doReturn(initSingleNodeInstanceList()).when(mockNodesManager).getNodes();

        assertThat(clusterApiService.getNodes()).hasSize(1).contains(nodeInstance);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldReturnListWithNodeInstanceWhichPermissionIsGranted() {
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();
        initAclEntity(nodeInstance, AclPermission.READ);
        initAclEntity(nodeInstanceWithoutPermission, AclPermission.NO_READ);
        doReturn(initTwoNodeInstancesList()).when(mockNodesManager).getNodes();

        assertThat(clusterApiService.getNodes()).hasSize(1).contains(nodeInstance);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldReturnEmptyNodeInstanceListWhenPermissionIsNotGranted() {
        initAclEntity(nodeInstanceWithoutPermission);
        doReturn(initSingleNodeInstanceList()).when(mockNodesManager).getNodes();

        assertThat(clusterApiService.getNodes()).isEmpty();
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldReturnFilteredListWithNodeInstancesForAdmin() {
        doReturn(initSingleNodeInstanceList()).when(mockNodesManager).filterNodes(filterNodesVO);

        assertThat(clusterApiService.filterNodes(filterNodesVO)).hasSize(1).contains(nodeInstance);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldReturnFilteredNodeInstanceListWhenPermissionIsGranted() {
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();
        initAclEntity(nodeInstance, AclPermission.READ);
        doReturn(initSingleNodeInstanceList()).when(mockNodesManager).filterNodes(filterNodesVO);

        assertThat(clusterApiService.filterNodes(filterNodesVO)).hasSize(1).contains(nodeInstance);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldReturnFilteredListWithNodeInstanceWhichPermissionIsGranted() {
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();
        initAclEntity(nodeInstance, AclPermission.READ);
        initAclEntity(nodeInstanceWithoutPermission, AclPermission.NO_READ);
        doReturn(initTwoNodeInstancesList()).when(mockNodesManager).filterNodes(filterNodesVO);

        assertThat(clusterApiService.filterNodes(filterNodesVO)).hasSize(1).contains(nodeInstance);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldReturnEmptyFilteredNodeInstanceListWhenPermissionIsNotGranted() {
        initAclEntity(nodeInstanceWithoutPermission);
        doReturn(initSingleNodeInstanceList()).when(mockNodesManager).filterNodes(filterNodesVO);

        assertThat(clusterApiService.filterNodes(filterNodesVO)).isEmpty();
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldReturnNodeInstanceForAdmin() {
        doReturn(nodeInstance).when(mockNodesManager).getNode(nodeInstance.getName());

        assertThat(clusterApiService.getNode(nodeInstance.getName())).isEqualTo(nodeInstance);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldReturnNodeInstanceWhenPermissionIsGranted() {
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();
        initAclEntity(nodeInstance, AclPermission.READ);
        doReturn(nodeInstance).when(mockNodesManager).getNode(nodeInstance.getName());
        doReturn(pipelineRun).when(mockPipelineRunManager).loadPipelineRun(eq(pipelineRun.getId()));

        assertThat(clusterApiService.getNode(nodeInstance.getName())).isEqualTo(nodeInstance);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyAccessToNode() {
        initAclEntity(nodeInstance);
        doReturn(nodeInstance).when(mockNodesManager).getNode(nodeInstance.getName());
        doReturn(pipelineRun).when(mockPipelineRunManager).loadPipelineRun(eq(pipelineRun.getId()));

        assertThrows(AccessDeniedException.class, () -> clusterApiService.getNode(nodeInstance.getName()));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldReturnNodeThroughRequestForAdmin() {
        doReturn(nodeInstance).when(mockNodesManager).getNode(nodeInstance.getName(), filterPodsRequest);

        assertThat(clusterApiService.getNode(nodeInstance.getName(), filterPodsRequest)).isEqualTo(nodeInstance);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldReturnNodeThroughRequestWhenPermissionIsGranted() {
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();
        initAclEntity(nodeInstance, AclPermission.READ);
        doReturn(nodeInstance).when(mockNodesManager).getNode(nodeInstance.getName(), filterPodsRequest);
        doReturn(nodeInstance).when(mockNodesManager).getNode(nodeInstance.getName());
        doReturn(pipelineRun).when(mockPipelineRunManager).loadPipelineRun(eq(pipelineRun.getId()));

        assertThat(clusterApiService.getNode(nodeInstance.getName(), filterPodsRequest)).isEqualTo(nodeInstance);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyAccessToNodeThroughRequest() {
        initAclEntity(nodeInstance);
        doReturn(nodeInstance).when(mockNodesManager).getNode(nodeInstance.getName(), filterPodsRequest);
        doReturn(nodeInstance).when(mockNodesManager).getNode(nodeInstance.getName());
        doReturn(pipelineRun).when(mockPipelineRunManager).loadPipelineRun(eq(pipelineRun.getId()));

        assertThrows(AccessDeniedException.class,
                () -> clusterApiService.getNode(nodeInstance.getName(), filterPodsRequest));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldTerminateNodeForAdmin() {
        doReturn(nodeInstance).when(mockNodesManager).terminateNode(nodeInstance.getName());

        assertThat(clusterApiService.terminateNode(nodeInstance.getName())).isEqualTo(nodeInstance);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldTerminateNodeWhenPermissionIsGranted() {
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();
        initAclEntity(nodeInstance, AclPermission.READ);
        doReturn(nodeInstance).when(mockNodesManager).terminateNode(nodeInstance.getName());
        doReturn(nodeInstance).when(mockNodesManager).getNode(nodeInstance.getName());
        doReturn(pipelineRun).when(mockPipelineRunManager).loadPipelineRun(eq(pipelineRun.getId()));

        assertThat(clusterApiService.terminateNode(nodeInstance.getName())).isEqualTo(nodeInstance);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyAccessToNodeTerminationWhenPermissionIsNotGranted() {
        initAclEntity(nodeInstance);
        doReturn(nodeInstance).when(mockNodesManager).terminateNode(nodeInstance.getName());
        doReturn(nodeInstance).when(mockNodesManager).getNode(nodeInstance.getName());
        doReturn(pipelineRun).when(mockPipelineRunManager).loadPipelineRun(eq(pipelineRun.getId()));

        assertThrows(AccessDeniedException.class,
                () -> clusterApiService.terminateNode(nodeInstance.getName()));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldReturnStatsForNodeForAdmin() {
        final List<MonitoringStats> statsList = Collections.singletonList(monitoringStats);
        doReturn(statsList).when(mockUsageMonitoringManager)
                .getStatsForNode(nodeInstance.getName(), LocalDateTime.MIN, LocalDateTime.MAX);

        final List<MonitoringStats> returnedStatsList =
                clusterApiService.getStatsForNode(nodeInstance.getName(), LocalDateTime.MIN, LocalDateTime.MAX);

        assertThat(returnedStatsList).hasSize(1).contains(monitoringStats);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldReturnStatsWhenPermissionIsGranted() {
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();
        initAclEntity(nodeInstance, AclPermission.READ);
        doReturn(statsList).when(mockUsageMonitoringManager)
                .getStatsForNode(nodeInstance.getName(), LocalDateTime.MIN, LocalDateTime.MAX);
        doReturn(nodeInstance).when(mockNodesManager).getNode(nodeInstance.getName(), filterPodsRequest);
        doReturn(nodeInstance).when(mockNodesManager).getNode(nodeInstance.getName());
        doReturn(pipelineRun).when(mockPipelineRunManager).loadPipelineRun(eq(pipelineRun.getId()));

        final List<MonitoringStats> returnedStatsList =
                clusterApiService.getStatsForNode(nodeInstance.getName(), LocalDateTime.MIN, LocalDateTime.MAX);

        assertThat(returnedStatsList).hasSize(1).contains(monitoringStats);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyAccessToStatsWhenPermissionIsNotGranted() {
        initAclEntity(nodeInstance);
        doReturn(statsList).when(mockUsageMonitoringManager)
                .getStatsForNode(nodeInstance.getName(), LocalDateTime.MIN, LocalDateTime.MAX);
        doReturn(nodeInstance).when(mockNodesManager).getNode(nodeInstance.getName(), filterPodsRequest);
        doReturn(nodeInstance).when(mockNodesManager).getNode(nodeInstance.getName());
        doReturn(pipelineRun).when(mockPipelineRunManager).loadPipelineRun(eq(pipelineRun.getId()));

        assertThrows(AccessDeniedException.class,
                () -> clusterApiService.getStatsForNode(nodeInstance.getName(), LocalDateTime.MIN, LocalDateTime.MAX));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldReturnUsageStatisticsFileForAdmin() {
        doReturn(inputStream).when(mockUsageMonitoringManager).getStatsForNodeAsInputStream(
                nodeInstance.getName(), LocalDateTime.MIN, LocalDateTime.MAX, Duration.ZERO);

        final InputStream returnedInputStream = clusterApiService.getUsageStatisticsFile(
                nodeInstance.getName(), LocalDateTime.MIN, LocalDateTime.MAX, Duration.ZERO);

        assertThat(returnedInputStream).isEqualTo(inputStream);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldReturnUsageStatisticsFileWhenPermissionIsGranted() {
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();
        initAclEntity(nodeInstance, AclPermission.READ);
        doReturn(inputStream).when(mockUsageMonitoringManager)
                .getStatsForNodeAsInputStream(nodeInstance.getName(),
                        LocalDateTime.MIN, LocalDateTime.MAX, Duration.ZERO);
        doReturn(nodeInstance).when(mockNodesManager).getNode(nodeInstance.getName(), filterPodsRequest);
        doReturn(nodeInstance).when(mockNodesManager).getNode(nodeInstance.getName());
        doReturn(pipelineRun).when(mockPipelineRunManager).loadPipelineRun(eq(pipelineRun.getId()));

        final InputStream returnedInputStream = clusterApiService.getUsageStatisticsFile(
                nodeInstance.getName(), LocalDateTime.MIN, LocalDateTime.MAX, Duration.ZERO);

        assertThat(returnedInputStream).isEqualTo(inputStream);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyAccessToUsageStatisticsFileWhenPermissionIsNotGranted() {
        initAclEntity(nodeInstance);
        doReturn(inputStream).when(mockUsageMonitoringManager).getStatsForNodeAsInputStream(
                nodeInstance.getName(), LocalDateTime.MIN, LocalDateTime.MAX, Duration.ZERO);
        doReturn(nodeInstance).when(mockNodesManager).getNode(nodeInstance.getName(), filterPodsRequest);
        doReturn(nodeInstance).when(mockNodesManager).getNode(nodeInstance.getName());
        doReturn(pipelineRun).when(mockPipelineRunManager).loadPipelineRun(eq(pipelineRun.getId()));

        assertThrows(AccessDeniedException.class,
                () -> clusterApiService.getUsageStatisticsFile(
                        nodeInstance.getName(), LocalDateTime.MIN, LocalDateTime.MAX, Duration.ZERO));

    }

    @Test
    public void shouldReturnInstanceTypes() {
        doReturn(instanceTypes).when(mockInstanceOfferManager).getAllowedInstanceTypes(1L, true);

        assertThat(clusterApiService.getAllowedInstanceTypes(1L, true)).isEqualTo(instanceTypes);
    }

    @Test
    public void shouldReturnToolInstanceTypes() {
        doReturn(instanceTypes).when(mockInstanceOfferManager).getAllowedToolInstanceTypes(1L, true);

        assertThat(clusterApiService.getAllowedToolInstanceTypes(1L, true)).isEqualTo(instanceTypes);
    }

    @Test
    public void shouldReturnAllowedInstanceAndPriceTypes() {
        final AllowedInstanceAndPriceTypes allowedInstanceAndPriceTypes =
                NodeCreatorUtils.getDefaultAllowedInstanceAndPriceTypes();
        doReturn(allowedInstanceAndPriceTypes).when(mockInstanceOfferManager)
                .getAllowedInstanceAndPriceTypes(1L, 2L, true);

        assertThat(clusterApiService.getAllowedInstanceAndPriceTypes(1L, 2L, true))
                .isEqualTo(allowedInstanceAndPriceTypes);
    }

    @Test
    public void shouldReturnMasterNodes() {
        final MasterNode masterNode = NodeCreatorUtils.getMasterNodeWithEmptyNode();
        final List<MasterNode> masterNodes = Collections.singletonList(masterNode);
        doReturn(masterNodes).when(mockNodesManager).getMasterNodes();

        assertThat(clusterApiService.getMasterNodes()).isEqualTo(masterNodes);
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldReturnNodeDisksForAdmin() {
        doReturn(nodeDisks).when(mockNodeDiskManager).loadByNodeId(nodeDisk.getNodeId());

        assertThat(clusterApiService.loadNodeDisks(nodeDisk.getNodeId())).hasSize(1).contains(nodeDisk);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldReturnNodeDisksWhenPermissionIsGranted() {
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();
        initAclEntity(nodeInstance, AclPermission.READ);
        doReturn(nodeDisks).when(mockNodeDiskManager).loadByNodeId(nodeDisk.getNodeId());
        doReturn(nodeInstance).when(mockNodesManager).getNode(nodeDisk.getNodeId(), filterPodsRequest);
        doReturn(nodeInstance).when(mockNodesManager).getNode(nodeDisk.getNodeId());
        doReturn(pipelineRun).when(mockPipelineRunManager).loadPipelineRun(eq(pipelineRun.getId()));

        assertThat(clusterApiService.loadNodeDisks(nodeDisk.getNodeId())).hasSize(1).contains(nodeDisk);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyAccessToNodeDisksWhenPermissionIsNotGranted() {
        initAclEntity(nodeInstance);
        doReturn(nodeDisks).when(mockNodeDiskManager).loadByNodeId(nodeDisk.getNodeId());
        doReturn(nodeInstance).when(mockNodesManager).getNode(nodeDisk.getNodeId(), filterPodsRequest);
        doReturn(nodeInstance).when(mockNodesManager).getNode(nodeDisk.getNodeId());
        doReturn(pipelineRun).when(mockPipelineRunManager).loadPipelineRun(eq(pipelineRun.getId()));

        assertThrows(AccessDeniedException.class, () -> clusterApiService.loadNodeDisks(nodeDisk.getNodeId()));
    }

    private List<NodeInstance> initSingleNodeInstanceList() {
        final List<NodeInstance> singleNodeInstance = new ArrayList<>();
        singleNodeInstance.add(nodeInstance);
        return singleNodeInstance;
    }

    private List<NodeInstance> initTwoNodeInstancesList() {
        final List<NodeInstance> twoNodeInstances = new ArrayList<>();
        twoNodeInstances.add(nodeInstance);
        twoNodeInstances.add(nodeInstanceWithoutPermission);
        return twoNodeInstances;
    }
}
