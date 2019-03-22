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

import com.amazonaws.util.StringUtils;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.cluster.NodeInstance;
import com.epam.pipeline.entity.cluster.NodeInstanceAddress;
import com.epam.pipeline.entity.cluster.PodInstance;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.controller.vo.FilterNodesVO;
import com.epam.pipeline.entity.cluster.FilterPodsRequest;
import com.epam.pipeline.dao.cluster.ClusterDao;
import com.epam.pipeline.manager.CmdExecutor;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import io.fabric8.kubernetes.api.model.DoneableNode;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.Resource;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.collections4.map.HashedMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import com.epam.pipeline.common.MessageConstants;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

@Service
public class NodesManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(NodesManager.class);
    private static final String EXECUTABLE = "python";
    private static final String INTERNAL_IP_PARAMETER = "--internal_ip";
    private static final String NODE_NAME_PARAMETER = "--node_name";
    private static final String MASTER_LABEL = "node-role.kubernetes.io/master";

    @Autowired
    private MessageHelper messageHelper;

    @Autowired
    private PipelineRunManager pipelineRunManager;

    @Autowired
    private ClusterDao clusterDao;

    private CmdExecutor cmdExecutor = new CmdExecutor();

    @Value("${cluster.node.terminate.script}")
    private String nodeTerminateScript;

    @Value("${kube.protected.node.labels:}")
    private String protectedNodesString;
    private Map<String, String> protectedNodeLabels;

    @PostConstruct
    public void init() {
        protectedNodeLabels = new HashMap<>();
        protectedNodeLabels.put(MASTER_LABEL, null);
        if (StringUtils.isNullOrEmpty(protectedNodesString)) {
            return;
        }
        String[] labels = protectedNodesString.trim().split(",");
        if (labels.length == 0) {
            return;
        }
        Arrays.stream(labels).forEach(label -> {
            if (StringUtils.isNullOrEmpty(label)) {
                return;
            }
            String[] labelAndValue = label.split("=", 2);
            if (labelAndValue.length == 1) {
                protectedNodeLabels.put(labelAndValue[0], null);
            } else {
                protectedNodeLabels.put(labelAndValue[0], labelAndValue[1]);
            }
        });

    }

    public List<NodeInstance> getNodes() {
        List<NodeInstance> result;
        Config config = new Config();
        try (KubernetesClient client = new DefaultKubernetesClient(config)) {
            result = client.nodes().list().getItems().stream().map(NodeInstance::new).collect(Collectors.toList());
            this.attachRunsInfo(result);
        }
        return result;
    }

    public List<NodeInstance> filterNodes(FilterNodesVO filterNodesVO) {
        List<NodeInstance> result;
        Config config = new Config();
        try (KubernetesClient client = new DefaultKubernetesClient(config)) {
            Map<String, String> labelsMap = new HashedMap<>();
            if (!StringUtils.isNullOrEmpty(filterNodesVO.getRunId())) {
                labelsMap.put(NodeInstance.RUN_ID_LABEL, filterNodesVO.getRunId());
            }
            Predicate<NodeInstance> addressFilter = node -> true;
            if (!StringUtils.isNullOrEmpty(filterNodesVO.getAddress())) {
                Predicate<NodeInstanceAddress> addressEqualsPredicate =
                    address -> !StringUtils.isNullOrEmpty(address.getAddress()) &&
                        address.getAddress().equalsIgnoreCase(filterNodesVO.getAddress());
                addressFilter = node ->
                        node.getAddresses() != null && node.getAddresses()
                                .stream()
                                .filter(addressEqualsPredicate).count() > 0;
            }
            result = client.nodes()
                    .withLabels(labelsMap)
                    .list()
                    .getItems()
                    .stream()
                    .map(NodeInstance::new)
                    .filter(addressFilter)
                    .collect(Collectors.toList());
            this.attachRunsInfo(result);
        }
        return result;
    }

    public NodeInstance getNode(String name) {
        return this.getNode(name, null);
    }

    public NodeInstance getNode(String name, FilterPodsRequest request) {
        NodeInstance nodeInstance = null;
        try (KubernetesClient client = new DefaultKubernetesClient()) {
            Resource<Node, DoneableNode> nodeSearchResult = client.nodes().withName(name);
            Node node = nodeSearchResult.get();
            if (node != null) {
                final List<String> statuses = request != null ? request.getPodStatuses() : null;
                nodeInstance = new NodeInstance(node);
                this.attachRunsInfo(Collections.singletonList(nodeInstance));
                nodeInstance.setPods(PodInstance.convertToInstances(client.pods().list(),
                        FilterPodsRequest.getPodsByNodeNameAndStatusPredicate(name, statuses))
                );
            }
        }
        Assert.notNull(nodeInstance, messageHelper.getMessage(MessageConstants.ERROR_NODE_NOT_FOUND, name));
        return nodeInstance;
    }

    public NodeInstance terminateNode(String name) {
        NodeInstance nodeInstance = getNode(name);
        Assert.isTrue(!isNodeProtected(nodeInstance),
                messageHelper.getMessage(MessageConstants.ERROR_NODE_IS_PROTECTED, name));
        if (nodeInstance.getPipelineRun() != null) {
            PipelineRun run = nodeInstance.getPipelineRun();
            pipelineRunManager.updatePipelineStatusIfNotFinal(run.getId(), TaskStatus.STOPPED, null);
        }
        Optional<NodeInstanceAddress> internalIP = nodeInstance.getAddresses()
                .stream()
                .filter(a -> a.getType() != null && a.getType().equalsIgnoreCase("internalip"))
                .findAny();
        if (internalIP.isPresent()) {
            String command = buildCommand(internalIP.get().getAddress(), nodeInstance.getName(), nodeTerminateScript);
            LOGGER.debug("Terminating node. Command: {}.", command);
            cmdExecutor.executeCommand(command);
        }
        return nodeInstance;
    }

    private boolean isNodeProtected(NodeInstance nodeInstance) {
        Map<String, String> labels = nodeInstance.getLabels();
        if (MapUtils.isEmpty(labels)) {
            return false;
        }
        return protectedNodeLabels.entrySet().stream().anyMatch(entry -> {
            // for empty values we just check presence of label
            if (StringUtils.isNullOrEmpty(entry.getValue()) && labels.containsKey(entry.getKey())) {
                return true;
            }
            String labelValue = labels.get(entry.getKey());
            if (StringUtils.hasValue(labelValue) && labelValue.equals(entry.getValue())) {
                return true;
            }
            return false;
        });
    }

    private String buildCommand(String internalIp, String nodeName, String script) {
        List<String> commands = new ArrayList<>();
        commands.add(EXECUTABLE);
        commands.add(script);
        commands.add(INTERNAL_IP_PARAMETER);
        commands.add(internalIp);
        commands.add(NODE_NAME_PARAMETER);
        commands.add(nodeName);
        return commands.stream().collect(Collectors.joining(" "));
    }

    private void attachRunsInfo(List<NodeInstance> nodeInstances) {
        List<Long> ids = new ArrayList<>();
        nodeInstances.forEach(i -> {
            try {
                ids.add(Long.parseLong(i.getRunId()));
            } catch (NumberFormatException exception) {
                i.setRunId(null);
                i.setPipelineRun(null);
            }
        });
        if (ids.isEmpty()) {
            return;
        }
        List<PipelineRun> runs = pipelineRunManager.loadPipelineRuns(ids);
        nodeInstances.forEach(i -> {
            Predicate<? super PipelineRun> filter = r -> r.getId().toString().equals(i.getRunId());
            Optional<PipelineRun> oPipelineRun = runs.stream().filter(filter).findFirst();
            oPipelineRun.ifPresent(i::setPipelineRun);
        });
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public Long getNextFreeNodeId() {
        return clusterDao.createNextFreeNodeId();
    }
}
