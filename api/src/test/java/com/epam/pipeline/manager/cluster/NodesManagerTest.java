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

import com.epam.pipeline.controller.vo.FilterNodesVO;
import com.epam.pipeline.entity.cluster.NodeInstance;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.util.KubernetesTestUtils;
import io.fabric8.kubernetes.api.model.DoneableNode;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.NodeAddress;
import io.fabric8.kubernetes.api.model.NodeList;
import io.fabric8.kubernetes.api.model.NodeStatus;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import org.apache.commons.collections4.map.HashedMap;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;

public class NodesManagerTest {

    private static final String TEST_ID = "1";
    private static final String TEST_ADDRESS = "111";
    private static final String ANOTHER_TEST_ADDRESS = "222";
    private static final String TEST_STRING = "TEST";
    private static final String TEST_UUID = "0cd07fdc-4364-11eb-b378-0242ac130002";

    private FilterNodesVO filterNodesVO;
    private Map<String, String> labels;

    @Mock
    private KubernetesManager mockKubernetesManager;

    @Mock
    private PipelineRunManager mockPipelineRunManager;

    @Mock
    private DefaultKubernetesClient mockKubernetesClient;

    @InjectMocks
    @Autowired
    private NodesManager nodesManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        labels = new HashedMap<>();
        labels.put(KubernetesConstants.RUN_ID_LABEL, TEST_ID);
        labels.put(TEST_STRING, TEST_STRING);

        filterNodesVO = new FilterNodesVO();
        filterNodesVO.setRunId(TEST_ID);

        final PipelineRun pipelineRun = new PipelineRun();
        pipelineRun.setId(1L);

        final ObjectMeta objectMeta = new ObjectMeta();
        objectMeta.setUid(TEST_UUID);
        objectMeta.setName(TEST_STRING);
        objectMeta.setCreationTimestamp(TEST_STRING);
        objectMeta.setClusterName(TEST_STRING);
        objectMeta.setLabels(labels);

        final NodeAddress nodeAddress = new NodeAddress();
        nodeAddress.setAddress(TEST_ADDRESS);

        final NodeStatus nodeStatus = new NodeStatus();
        nodeStatus.setAddresses(Collections.singletonList(nodeAddress));

        final Node node = new Node();
        node.setStatus(nodeStatus);
        node.setMetadata(objectMeta);

        final NonNamespaceOperation<Node, NodeList, DoneableNode, Resource<Node, DoneableNode>> mockNodes =
                new KubernetesTestUtils.MockNodes()
                        .mockWithLabels(labels)
                        .mockNodeList(Collections.singletonList(node))
                        .and()
                        .getMockedEntity();

        doReturn(mockKubernetesClient).when(mockKubernetesManager).getKubernetesClient(any(Config.class));
        doReturn(mockNodes).when(mockKubernetesClient).nodes();
        doReturn(Collections.singletonList(pipelineRun)).when(mockPipelineRunManager).loadPipelineRuns(any());
    }

    @Test
    public void shouldReturnListNodeWhenFiltersArePassed() {
        filterNodesVO.setAddress(TEST_ADDRESS);
        filterNodesVO.setLabels(Collections.singletonMap(TEST_STRING, TEST_STRING));

        final List<NodeInstance> filteredNodes = nodesManager.filterNodes(filterNodesVO);
        final NodeInstance returnedNode = filteredNodes.get(0);

        assertThat(filteredNodes).hasSize(1);
        assertThat(returnedNode.getRunId()).isEqualTo(TEST_ID);
        assertThat(returnedNode.getAddresses().get(0).getAddress()).isEqualTo(TEST_ADDRESS);
        assertThat(returnedNode.getLabels()).isEqualTo(labels);
    }

    @Test
    public void shouldReturnEmptyListNodeWhenAddressFilterIsNotPassed() {
        filterNodesVO.setAddress(ANOTHER_TEST_ADDRESS);
        filterNodesVO.setLabels(Collections.singletonMap(TEST_STRING, TEST_STRING));

        assertThat(nodesManager.filterNodes(filterNodesVO)).isEmpty();
    }
}
