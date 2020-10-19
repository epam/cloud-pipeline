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

package com.epam.pipeline.controller.cluster;

import com.epam.pipeline.controller.vo.FilterNodesVO;
import com.epam.pipeline.entity.cluster.AllowedInstanceAndPriceTypes;
import com.epam.pipeline.entity.cluster.FilterPodsRequest;
import com.epam.pipeline.entity.cluster.InstanceType;
import com.epam.pipeline.entity.cluster.MasterNode;
import com.epam.pipeline.entity.cluster.NodeDisk;
import com.epam.pipeline.entity.cluster.NodeInstance;
import com.epam.pipeline.entity.cluster.monitoring.MonitoringStats;
import com.epam.pipeline.manager.cluster.ClusterApiService;
import com.epam.pipeline.test.creator.cluster.NodeCreatorUtils;
import com.epam.pipeline.test.web.AbstractControllerTest;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@WebMvcTest(controllers = ClusterController.class)
public class ClusterControllerTest extends AbstractControllerTest {

    private static final long ID = 1L;
    private static final String CLUSTER_URL = SERVLET_PATH + "/cluster";
    private static final String MASTER_NODES_URL = CLUSTER_URL + "/master";
    private static final String NODE_URL = CLUSTER_URL + "/node";
    private static final String INSTANCE_URL = CLUSTER_URL + "/instance";
    private static final String LOAD_NODES_URL = NODE_URL + "/loadAll";
    private static final String FILTER_NODES_URL = NODE_URL +"/filter";
    private static final String LOAD_NODE_URL = NODE_URL + "/%s/load";
    private static final String NODE_NAME_URL = NODE_URL + "/%s";
    private static final String LOAD_INSTANCE_TYPES_URL = INSTANCE_URL + "/loadAll";
    private static final String LOAD_ALLOWED_INSTANCE_TYPES_URL = INSTANCE_URL + "/allowed";
    private static final String NODE_USAGE_URL = NODE_NAME_URL + "/usage";
    private static final String NODE_DISKS_URL = NODE_NAME_URL + "/disks";
    private static final String NODE_STATISTICS_URL = NODE_USAGE_URL + "/report";
    private static final String PORT = "7367";
    private static final String NAME = "testName";
    private static final String TEST_DATA = "test_data";
    private static final String FROM_STRING = "2019-04-01T09:08:07";
    private static final String TO_STRING = "2020-05-02T12:11:10";
    private static final String OCTET_STREAM_CONTENT_TYPE = "application/octet-stream";
    private final NodeInstance nodeInstance = NodeCreatorUtils.getDefaultNodeInstance();
    private final List<NodeInstance> nodeInstances = Collections.singletonList(nodeInstance);
    private final List<InstanceType>
            instanceTypes = Collections.singletonList(NodeCreatorUtils.getDefaultInstanceType());
    private final LocalDateTime from = LocalDateTime.parse(FROM_STRING, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    private final LocalDateTime to = LocalDateTime.parse(TO_STRING, DateTimeFormatter.ISO_LOCAL_DATE_TIME);

    @Autowired
    private ClusterApiService mockClusterApiService;

    @Test
    public void shouldFailLoadMasterNodesForUnauthorizedUser() throws Exception {
        performUnauthorizedRequest(get(MASTER_NODES_URL));
    }

    @Test
    @WithMockUser
    public void shouldLoadMasterNodes() throws Exception {
        final List<MasterNode> masterNodes = Collections.singletonList(MasterNode.fromNode(
                NodeCreatorUtils.getDefaultNode(), PORT));
        Mockito.doReturn(masterNodes).when(mockClusterApiService).getMasterNodes();

        final MvcResult mvcResult = performRequest(get(MASTER_NODES_URL));

        Mockito.verify(mockClusterApiService).getMasterNodes();
        assertResponse(mvcResult, masterNodes, NodeCreatorUtils.MASTER_NODE_LIST_TYPE);
    }

    @Test
    public void shouldFailLoadNodesForUnauthorizedUser() throws Exception {
        performUnauthorizedRequest(get(LOAD_NODES_URL));
    }

    @Test
    @WithMockUser
    public void shouldLoadNodes() throws Exception {
        Mockito.doReturn(nodeInstances).when(mockClusterApiService).getNodes();

        final MvcResult mvcResult = performRequest(get(LOAD_NODES_URL));

        Mockito.verify(mockClusterApiService).getNodes();
        assertResponse(mvcResult, nodeInstances, NodeCreatorUtils.NODE_INSTANCE_LIST_TYPE);
    }

    @Test
    public void shouldFailFilterNodesForUnauthorizedUser() throws Exception {
        performUnauthorizedRequest(post(FILTER_NODES_URL));
    }

    @Test
    @WithMockUser
    public void shouldFilterNodes() throws Exception {
        final FilterNodesVO filterNodesVO = NodeCreatorUtils.getDefaultFilterNodesVO();
        final String content = getObjectMapper().writeValueAsString(filterNodesVO);
        Mockito.doReturn(nodeInstances).when(mockClusterApiService).filterNodes(Mockito.refEq(filterNodesVO));

        final MvcResult mvcResult = performRequest(post(FILTER_NODES_URL).content(content));

        Mockito.verify(mockClusterApiService).filterNodes(Mockito.refEq(filterNodesVO));
        assertResponse(mvcResult, nodeInstances, NodeCreatorUtils.NODE_INSTANCE_LIST_TYPE);
    }

    @Test
    public void shouldFailLoadNodeForUnauthorizedUser() throws Exception {
        performUnauthorizedRequest(get(String.format(LOAD_NODE_URL, NAME)));
    }

    @Test
    @WithMockUser
    public void shouldLoadNode() throws Exception {
        Mockito.doReturn(nodeInstance).when(mockClusterApiService).getNode(NAME);

        final MvcResult mvcResult = performRequest(get(String.format(LOAD_NODE_URL, NAME)));

        Mockito.verify(mockClusterApiService).getNode(NAME);
        assertResponse(mvcResult, nodeInstance, NodeCreatorUtils.NODE_INSTANCE_TYPE);
    }

    @Test
    public void shouldFailLoadNodeFilteredForUnauthorizedUser() throws Exception {
        performUnauthorizedRequest(post(LOAD_NODES_URL, NAME));
    }

    @Test
    @WithMockUser
    public void shouldLoadNodeFiltered() throws Exception {
        final FilterPodsRequest filterPodsRequest = NodeCreatorUtils.getDefaultFilterPodsRequest();
        final String content = getObjectMapper().writeValueAsString(filterPodsRequest);
        Mockito.doReturn(nodeInstance).when(mockClusterApiService)
                .getNode(Mockito.eq(NAME), Mockito.refEq(filterPodsRequest));

        final MvcResult mvcResult = performRequest(post(String.format(LOAD_NODE_URL, NAME)).content(content));

        Mockito.verify(mockClusterApiService)
                .getNode(Mockito.eq(NAME), Mockito.refEq(filterPodsRequest));
        assertResponse(mvcResult, nodeInstance, NodeCreatorUtils.NODE_INSTANCE_TYPE);
    }

    @Test
    public void shouldFailTerminateNodeForUnauthorizedUser() throws Exception {
        performUnauthorizedRequest(delete(NODE_NAME_URL, NAME));
    }

    @Test
    @WithMockUser
    public void shouldTerminateNode() throws Exception {
        Mockito.doReturn(nodeInstance).when(mockClusterApiService).terminateNode(NAME);

        final MvcResult mvcResult = performRequest(delete(String.format(NODE_NAME_URL, NAME)));

        Mockito.verify(mockClusterApiService).terminateNode(NAME);
        assertResponse(mvcResult, nodeInstance, NodeCreatorUtils.NODE_INSTANCE_TYPE);
    }

    @Test
    public void shouldFailLoadAllInstanceTypesForUnauthorizedUser() throws Exception {
        performUnauthorizedRequest(get(LOAD_INSTANCE_TYPES_URL));
    }

    @Test
    @WithMockUser
    public void shouldLoadAllowedInstanceTypes() throws Exception {
        final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("regionId", "1");
        params.add("toolInstances", "false");
        params.add("spot", "true");
        Mockito.doReturn(instanceTypes).when(mockClusterApiService).getAllowedInstanceTypes(ID, true);

        final MvcResult mvcResult = performRequest(get(LOAD_INSTANCE_TYPES_URL).params(params));

        Mockito.verify(mockClusterApiService).getAllowedInstanceTypes(ID, true);
        assertResponse(mvcResult, instanceTypes, NodeCreatorUtils.INSTANCE_TYPE_LIST_TYPE);
    }

    @Test
    @WithMockUser
    public void shouldLoadAllowedToolInstanceTypes() throws Exception {
        final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("regionId", "1");
        params.add("toolInstances", "true");
        params.add("spot", "false");
        Mockito.doReturn(instanceTypes).when(mockClusterApiService).getAllowedToolInstanceTypes(ID, false);

        final MvcResult mvcResult = performRequest(get(LOAD_INSTANCE_TYPES_URL).params(params));

        Mockito.verify(mockClusterApiService).getAllowedToolInstanceTypes(ID, false);
        assertResponse(mvcResult, instanceTypes, NodeCreatorUtils.INSTANCE_TYPE_LIST_TYPE);
    }

    @Test
    public void shouldFailLoadAllowedInstanceAndPriceTypesForUnauthorizedUser() throws Exception {
        performUnauthorizedRequest(get(LOAD_ALLOWED_INSTANCE_TYPES_URL));
    }

    @Test
    @WithMockUser
    public void shouldLoadAllowedInstanceAndPriceTypes() throws Exception {
        final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("toolId", "1");
        params.add("regionId", "1");
        params.add("spot", "false");
        final AllowedInstanceAndPriceTypes allowedInstanceAndPriceTypes =
                NodeCreatorUtils.getDefaultAllowedInstanceAndPriceTypes();
        Mockito.doReturn(allowedInstanceAndPriceTypes).when(mockClusterApiService)
                .getAllowedInstanceAndPriceTypes(ID, ID, false);

        final MvcResult mvcResult = performRequest(get(LOAD_ALLOWED_INSTANCE_TYPES_URL).params(params));

        Mockito.verify(mockClusterApiService).getAllowedInstanceAndPriceTypes(ID, ID, false);
        assertResponse(mvcResult, allowedInstanceAndPriceTypes, NodeCreatorUtils.ALLOWED_INSTANCE_TYPE);
    }

    @Test
    public void shouldFailGetNodeUsageStatisticsForUnauthorizedUser() throws Exception {
        performUnauthorizedRequest(get(String.format(NODE_USAGE_URL, NAME)));
    }

    @Test
    @WithMockUser
    public void shouldGetNodeUsageStatistics() throws Exception {
        final List<MonitoringStats> monitoringStats = Collections.singletonList(new MonitoringStats());
        final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("from", FROM_STRING.replace('T', ' '));
        params.add("to", TO_STRING.replace('T', ' '));
        Mockito.doReturn(monitoringStats).when(mockClusterApiService)
                .getStatsForNode(NAME, from, to);

        final MvcResult mvcResult = performRequest(get(String.format(NODE_USAGE_URL, NAME)).params(params));

        Mockito.verify(mockClusterApiService).getStatsForNode(NAME, from, to);
        assertResponse(mvcResult, monitoringStats, NodeCreatorUtils.MONITORING_STATS_TYPE);
    }

    @Test
    public void shouldFailDownloadNodeUsageStatisticsReportForUnauthorizedUser() throws Exception {
        performUnauthorizedRequest(get(String.format(NODE_STATISTICS_URL, NAME)));
    }

    @Test
    @WithMockUser
    public void shouldDownloadNodeUsageStatisticsReport() throws Exception {
        final InputStream inputStream = new ByteArrayInputStream(TEST_DATA.getBytes());
        final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("from", FROM_STRING.replace('T', ' '));
        params.add("to", TO_STRING.replace('T', ' '));
        params.add("interval", "PT1H");
        Mockito.doReturn(inputStream).when(mockClusterApiService)
                .getUsageStatisticsFile(NAME, from, to, Duration.ofHours(1));

        final MvcResult mvcResult = performRequest(
                get(String.format(NODE_STATISTICS_URL, NAME)).params(params), OCTET_STREAM_CONTENT_TYPE
        );

        Mockito.verify(mockClusterApiService).getUsageStatisticsFile(NAME, from, to, Duration.ofHours(1));
        final String actualResponseData = mvcResult.getResponse().getContentAsString();
        Assert.assertEquals(TEST_DATA, actualResponseData);
    }

    @Test
    public void shouldFailLoadNodeDisksForUnauthorizedUser() throws Exception {
        performUnauthorizedRequest(get(String.format(NODE_DISKS_URL, NAME)));
    }

    @Test
    @WithMockUser
    public void shouldLoadNodeDisks() throws Exception {
        final List<NodeDisk> nodeDisks = Collections.singletonList(NodeCreatorUtils.getDefaultNodeDisk());
        Mockito.doReturn(nodeDisks).when(mockClusterApiService).loadNodeDisks(NAME);

        final MvcResult mvcResult = performRequest(get(String.format(NODE_DISKS_URL, NAME)));

        Mockito.verify(mockClusterApiService).loadNodeDisks(NAME);
        assertResponse(mvcResult, nodeDisks, NodeCreatorUtils.NODE_DISK_TYPE);
    }
}
