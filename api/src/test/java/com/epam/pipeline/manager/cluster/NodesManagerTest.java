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
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;
import java.util.HashMap;
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
    private static final String ANOTHER_TEST_STRING = "TEST2";
    private static final String CLOUD_PROVIDER = "AWS";
    private static final String TEST_UUID = "0cd07fdc-4364-11eb-b378-0242ac130002";

    private final Node node = new Node();
    private final FilterNodesVO filterNodesVO = new FilterNodesVO();
    private final PipelineRun pipelineRun = new PipelineRun();
    private final Map<String, String> labels = new HashMap<>();

    @Mock
    private KubernetesManager kubernetesManager;

    @Mock
    private PipelineRunManager pipelineRunManager;

    @Mock
    private DefaultKubernetesClient kubernetesClient;

    @InjectMocks
    @Autowired
    private NodesManager nodesManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        final NonNamespaceOperation<Node, NodeList, DoneableNode, Resource<Node, DoneableNode>> mockNodes =
                new KubernetesTestUtils.MockNodes()
                        .mockWithLabels(Collections.singletonMap(KubernetesConstants.RUN_ID_LABEL, TEST_ID))
                        .mockNodeList(Collections.singletonList(node))
                        .and()
                        .getMockedEntity();

        labels.put(KubernetesConstants.RUN_ID_LABEL, TEST_ID);
        labels.put(KubernetesConstants.CLOUD_PROVIDER_LABEL, CLOUD_PROVIDER);

        final ObjectMeta objectMeta = new ObjectMeta();
        objectMeta.setLabels(labels);
        objectMeta.setUid(TEST_UUID);
        objectMeta.setName(TEST_STRING);
        objectMeta.setCreationTimestamp(TEST_STRING);
        objectMeta.setClusterName(TEST_STRING);

        final NodeAddress nodeAddress = new NodeAddress();
        nodeAddress.setAddress(TEST_ADDRESS);

        final NodeStatus nodeStatus = new NodeStatus();
        nodeStatus.setAddresses(Collections.singletonList(nodeAddress));

        node.setStatus(nodeStatus);
        node.setMetadata(objectMeta);

        filterNodesVO.setRunId(TEST_ID);
        pipelineRun.setId(1L);

        doReturn(kubernetesClient).when(kubernetesManager).getKubernetesClient(any(Config.class));
        doReturn(mockNodes).when(kubernetesClient).nodes();
        doReturn(Collections.singletonList(pipelineRun)).when(pipelineRunManager).loadPipelineRuns(any());
    }


    @Test
    public void shouldReturnListNodeWhenFiltersArePassed() {
        labels.put(TEST_STRING, TEST_STRING);
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
        labels.put(TEST_STRING, TEST_STRING);
        filterNodesVO.setAddress(ANOTHER_TEST_ADDRESS);
        filterNodesVO.setLabels(Collections.singletonMap(TEST_STRING, TEST_STRING));

        assertThat(nodesManager.filterNodes(filterNodesVO)).isEmpty();
    }

    @Test
    public void shouldReturnEmptyListNodeWhenLabelsFilterIsNotPassed() {
        labels.put(ANOTHER_TEST_STRING, ANOTHER_TEST_STRING);
        filterNodesVO.setAddress(TEST_ADDRESS);
        filterNodesVO.setLabels(Collections.singletonMap(TEST_STRING, TEST_STRING));

        assertThat(nodesManager.filterNodes(filterNodesVO)).isEmpty();
    }
}
