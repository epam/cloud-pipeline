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

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.controller.vo.FilterNodesVO;
import com.epam.pipeline.dao.cluster.ClusterDao;
import com.epam.pipeline.entity.cluster.FilterPodsRequest;
import com.epam.pipeline.entity.cluster.MasterNode;
import com.epam.pipeline.entity.cluster.NodeInstance;
import com.epam.pipeline.entity.cluster.NodeInstanceAddress;
import com.epam.pipeline.entity.cluster.PodInstance;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.manager.cloud.CloudFacade;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.region.CloudRegionManager;
import io.fabric8.kubernetes.api.model.DoneableNode;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.collections4.map.HashedMap;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
@Slf4j
public class NodesManager {

    private static final String MASTER_LABEL = "node-role.kubernetes.io/master";
    private static final int NODE_DOWN_ATTEMPTS = 10;

    @Autowired
    private MessageHelper messageHelper;

    @Autowired
    private PreferenceManager preferenceManager;

    @Autowired
    private PipelineRunManager pipelineRunManager;

    @Autowired
    private CloudFacade cloudFacade;

    @Autowired
    private CloudRegionManager regionManager;

    @Autowired
    private ClusterDao clusterDao;

    @Autowired
    private KubernetesManager kubernetesManager;

    @Value("${kube.protected.node.labels:}")
    private String protectedNodesString;

    private Map<String, String> protectedNodeLabels;

    @PostConstruct
    public void init() {
        protectedNodeLabels = new HashMap<>();
        protectedNodeLabels.put(MASTER_LABEL, null);
        if (StringUtils.isBlank(protectedNodesString)) {
            return;
        }
        String[] labels = protectedNodesString.trim().split(",");
        if (labels.length == 0) {
            return;
        }
        Arrays.stream(labels).forEach(label -> {
            if (StringUtils.isBlank(label)) {
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
            if (StringUtils.isNotBlank(filterNodesVO.getRunId())) {
                labelsMap.put(KubernetesConstants.RUN_ID_LABEL, filterNodesVO.getRunId());
            }
            Predicate<NodeInstance> addressFilter = node -> true;
            if (StringUtils.isNotBlank(filterNodesVO.getAddress())) {
                Predicate<NodeInstanceAddress> addressEqualsPredicate =
                    address -> StringUtils.isNotBlank(address.getAddress()) &&
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
        return findNode(name, request).orElseThrow(() -> new IllegalArgumentException(
                messageHelper.getMessage(MessageConstants.ERROR_NODE_NOT_FOUND, name)));
    }

    public Optional<NodeInstance> findNode(final String name) {
        return findNode(name, null);
    }

    public Optional<NodeInstance> findNode(final String name, final FilterPodsRequest request) {
        try (KubernetesClient client = new DefaultKubernetesClient()) {
            final Resource<Node, DoneableNode> nodeSearchResult = client.nodes().withName(name);
            final Node node = nodeSearchResult.get();
            if (node != null) {
                final List<String> statuses = request != null ? request.getPodStatuses() : null;
                final NodeInstance nodeInstance = new NodeInstance(node);
                this.attachRunsInfo(Collections.singletonList(nodeInstance));
                nodeInstance.setPods(PodInstance.convertToInstances(client.pods().list(),
                        FilterPodsRequest.getPodsByNodeNameAndStatusPredicate(name, statuses))
                );
                return Optional.of(nodeInstance);
            }
        }
        final List<String> podStatuses = Optional.ofNullable(request)
                .map(FilterPodsRequest::getPodStatuses)
                .orElseGet(Collections::emptyList);
        log.warn("Node with name {} and the following pod statuses {} wasn't found in cluster.", name, podStatuses);
        return Optional.empty();
    }

    public NodeInstance terminateNode(final String name) {
        final NodeInstance nodeInstance = getNode(name);
        terminateNode(nodeInstance);
        return nodeInstance;
    }

    public List<MasterNode> getMasterNodes() {
        final String defMasterPort =
                String.valueOf(preferenceManager.getPreference(SystemPreferences.CLUSTER_KUBE_MASTER_PORT));
        try (KubernetesClient client = new DefaultKubernetesClient()) {
            return client.nodes().withLabel(MASTER_LABEL).list().getItems()
                    .stream()
                    .filter(this::nodeIsReady)
                    .map(node -> MasterNode.fromNode(node, defMasterPort))
                    .collect(Collectors.toList());
        }
    }

    private boolean nodeIsReady(final Node node) {
        return CollectionUtils.emptyIfNull(node.getStatus().getConditions())
                .stream().anyMatch(
                    nc -> nc.getType().equalsIgnoreCase(KubernetesConstants.READY) &&
                            nc.getStatus().equalsIgnoreCase(KubernetesConstants.TRUE));
    }

    /**
     * Terminates run cloud instance.
     *
     * If there is a corresponding Kubernetes node it terminates it as well.
     * Otherwise just the cloud instance is terminated.
     */
    public void terminateRun(final PipelineRun run) {
        final Optional<RunInstance> instance = Optional.ofNullable(run.getInstance());
        final Optional<NodeInstance> node = instance.map(RunInstance::getNodeName).flatMap(this::findNode);
        if (node.isPresent()) {
            log.debug("Kubernetes node {} for run {} was found and will be terminated.", node.get().getId(),
                    run.getId());
            terminateNode(node.get());
        } else {
            log.debug("Kubernetes node for run {} wasn't found and its termination will be skipped.", run.getId());
            final AbstractCloudRegion region = instance.map(RunInstance::getCloudRegionId)
                    .map(regionManager::load)
                    .orElseGet(regionManager::loadDefaultRegion);
            final Optional<String> nodeId = instance.map(RunInstance::getNodeId)
                    .filter(id -> cloudFacade.instanceExists(region.getId(), id))
                    .map(Optional::of)
                    .orElseGet(() -> instanceIdFromRunId(run.getId()));
            if (nodeId.isPresent()) {
                log.debug("Cloud instance {} for run {} was found in region {} and will be terminated.", nodeId.get(),
                        run.getId(), region.getRegionCode());
                terminateInstance(region, nodeId.get());
            } else {
                log.debug("Cloud instance for run {} wasn't found in region {} and its termination will be skipped.",
                        run.getId(), region.getRegionCode());
            }
        }
    }

    private Optional<String> instanceIdFromRunId(final Long runId) {
        return Optional.ofNullable(cloudFacade.describeAliveInstance(runId, new RunInstance()))
                .map(RunInstance::getNodeId);
    }

    private void terminateInstance(final AbstractCloudRegion region, final String nodeId) {
        cloudFacade.terminateInstance(region.getId(), nodeId);
    }

    private void terminateNode(final NodeInstance nodeInstance) {
        Assert.isTrue(!isNodeProtected(nodeInstance),
                messageHelper.getMessage(MessageConstants.ERROR_NODE_IS_PROTECTED, nodeInstance.getName()));

        if (nodeInstance.getPipelineRun() != null) {
            PipelineRun run = nodeInstance.getPipelineRun();
            pipelineRunManager.updatePipelineStatusIfNotFinal(run.getId(), TaskStatus.STOPPED);
        }

        final AbstractCloudRegion cloudRegion = Optional.ofNullable(nodeInstance.getPipelineRun())
                .map(run -> regionManager.load(run.getInstance().getCloudRegionId()))
                .orElseGet(() -> loadRegionFromLabels(nodeInstance));
        final Optional<NodeInstanceAddress> internalIP = nodeInstance.getAddresses()
                .stream()
                .filter(a -> a.getType() != null && a.getType().equalsIgnoreCase("internalip"))
                .findAny();
        internalIP.ifPresent(nodeInstanceAddress -> terminateNode(nodeInstance, nodeInstanceAddress, cloudRegion));
    }

    private AbstractCloudRegion loadRegionFromLabels(final NodeInstance nodeInstance) {
        final CloudProvider provider = nodeInstance.getProvider();
        final String region = nodeInstance.getRegion();
        if (provider == null || org.apache.commons.lang3.StringUtils.isBlank(region)) {
            //missing node labels, let's try default region
            log.error("Node {} is missing cloud provider labels. Provider: {}, region: {}.", nodeInstance.getName(),
                    provider, region);
            return regionManager.loadDefaultRegion();
        }
        return regionManager.load(provider, region);
    }

    private void terminateNode(final NodeInstance nodeInstance,
                               final NodeInstanceAddress nodeInstanceAddress,
                               final AbstractCloudRegion region) {
        cloudFacade.terminateNode(region, nodeInstanceAddress.getAddress(), nodeInstance.getName());
        kubernetesManager.waitNodeDown(nodeInstance.getName(), NODE_DOWN_ATTEMPTS);
    }

    private boolean isNodeProtected(NodeInstance nodeInstance) {
        Map<String, String> labels = nodeInstance.getLabels();
        if (MapUtils.isEmpty(labels)) {
            return false;
        }
        return protectedNodeLabels.entrySet().stream().anyMatch(entry -> {
            // for empty values we just check presence of label
            if (StringUtils.isBlank(entry.getValue()) && labels.containsKey(entry.getKey())) {
                return true;
            }
            String labelValue = labels.get(entry.getKey());
            if (StringUtils.isNotBlank(labelValue) && labelValue.equals(entry.getValue())) {
                return true;
            }
            return false;
        });
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
