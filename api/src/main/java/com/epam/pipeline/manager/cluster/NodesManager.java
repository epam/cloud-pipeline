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
import com.epam.pipeline.entity.cluster.ContainerInstance;
import com.epam.pipeline.entity.cluster.DiskRegistrationRequest;
import com.epam.pipeline.entity.cluster.FilterPodsRequest;
import com.epam.pipeline.entity.cluster.MachineType;
import com.epam.pipeline.entity.cluster.MasterNode;
import com.epam.pipeline.entity.cluster.NodeInstance;
import com.epam.pipeline.entity.cluster.NodeInstanceAddress;
import com.epam.pipeline.entity.cluster.NodeResources;
import com.epam.pipeline.entity.cluster.PodInstance;
import com.epam.pipeline.entity.pipeline.DiskAttachRequest;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.pipeline.run.RunInfo;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.exception.cluster.NodeNotFoundException;
import com.epam.pipeline.manager.cloud.CloudFacade;
import com.epam.pipeline.manager.pipeline.PipelineRunCRUDService;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.region.CloudRegionManager;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.utils.CommonUtils;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.collections4.map.HashedMap;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
@Slf4j
@SuppressWarnings("PMD.AvoidCatchingGenericException")
public class NodesManager implements InitializingBean {

    private static final String MASTER_LABEL = "node-role.kubernetes.io/master";
    private static final int NODE_DOWN_ATTEMPTS = 10;
    private static final String ACCESS_DENIED_MSG = "Access is denied.";

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

    @Autowired
    private NodeDiskManager nodeDiskManager;

    @Autowired
    private PipelineRunCRUDService runCRUDService;

    @Autowired
    private AuthManager authManager;

    @Value("${kube.protected.node.labels:}")
    private String protectedNodesString;

    private Map<String, String> protectedNodeLabels;

    @Override
    public void afterPropertiesSet() throws Exception {
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

    public List<NodeInstance> filterNodes(final FilterNodesVO filterNodesVO, final MachineType machineType) {
        switch (machineType) {
            case KUBE:
                return filterKubeNodes(filterNodesVO);
            case CLOUD:
                if (!authManager.isAdmin()) {
                    throw new AccessDeniedException(ACCESS_DENIED_MSG);
                }
                return filterCloudNodes(filterNodesVO);
            case ALL:
                if (!authManager.isAdmin()) {
                    log.debug("Cloud nodes is not available for non-admin users. Only kube nodes will be filtered.");
                    return filterKubeNodes(filterNodesVO);
                }
                final List<NodeInstance> kubeNodes = filterKubeNodes(filterNodesVO);
                final List<NodeInstance> cloudNodes = filterCloudNodes(filterNodesVO);
                return mergeNodesByMachineType(kubeNodes, cloudNodes);
            default:
                throw new UnsupportedOperationException(String.format("Unsupported type '%s'", machineType));
        }
    }

    public List<NodeResources> loadNodeAvailableResources(final Map<String, String> labels) {
        final List<NodeInstance> nodes = findKubeNodesByLabels(labels);
        if (CollectionUtils.isEmpty(nodes)) {
            log.debug("No nodes matching labels {} found", labels);
            return Collections.emptyList();
        }
        log.debug("Found {} nodes matching labels {}", nodes.size(), labels);
        final List<PodInstance> pods = findActivePodsByLabelsAndNodes(labels, nodes);
        log.debug("Found {} active pods matching labels {}", pods.size(), labels);
        final Map<String, List<PodInstance>> podsByNodes = pods.stream()
                .collect(Collectors.groupingBy(PodInstance::getNodeName));
        return ListUtils.emptyIfNull(nodes).stream()
                .map(node -> NodeAvailableResourcesParser.parse(node, podsByNodes.get(node.getName())))
                .collect(Collectors.toList());
    }

    public NodeInstance getNode(String name) {
        return this.getNode(name, null);
    }

    /**
     * Loads node by instance ID according to specified type:
     *  - KUBE - loads node from kubernetes cluster. regionId parameter will be ignored in this case.
     *  - CLOUD - loads node directly from cloud provider. If no regionId provided all regions
     *            with {@link AbstractCloudRegion#isClusterInclude()} flag shall be scanned
     *            for instance with specified ID.
     *  Other types not supported yet.
     * @param name - instance ID
     * @param machineType - type
     * @param regionId - region ID
     * @return node description or error
     */
    public NodeInstance getKubeOrCloudNode(final String name, final MachineType machineType, final Long regionId) {
        switch (machineType) {
            case KUBE:
                return getNode(name, null);
            case CLOUD:
                final Optional<NodeInstance> nodeInstance = Objects.nonNull(regionId)
                        ? findCloudNodeInRegion(regionManager.load(regionId), name)
                        : ListUtils.emptyIfNull(regionManager.loadAll()).stream()
                        .filter(AbstractCloudRegion::isClusterInclude)
                        .collect(Collectors.toList()).stream()
                        .map(region -> findCloudNodeInRegion(region, name))
                        .filter(Optional::isPresent)
                        .findFirst()
                        .flatMap(Function.identity());
                return nodeInstance.orElseThrow(() -> new NodeNotFoundException(
                        messageHelper.getMessage(MessageConstants.ERROR_NODE_NOT_FOUND, name)));
            default:
                throw new UnsupportedOperationException("Exact machine type KUBE or CLOUD shall be specified!");
        }
    }

    public NodeInstance getNode(String name, FilterPodsRequest request) {
        return findNode(name, request).orElseThrow(() -> new NodeNotFoundException(
                messageHelper.getMessage(MessageConstants.ERROR_NODE_NOT_FOUND, name)));
    }

    public Optional<NodeInstance> findNode(final String name) {
        return findNode(name, null);
    }

    public Optional<NodeInstance> findNode(final String name, final FilterPodsRequest request) {
        try (KubernetesClient client = kubernetesManager.getKubernetesClient()) {
            final Node node = client.nodes().withName(name).get();
            if (node != null) {
                final List<String> statuses = request != null ? request.getPodStatuses() : null;
                final NodeInstance nodeInstance = new NodeInstance(node);
                attachRunsInfo(Collections.singletonList(nodeInstance));
                nodeInstance.setPods(PodInstance.convertToInstances(client.pods()
                                .inAnyNamespace()
                                .withField("spec.nodeName", nodeInstance.getName()).list(),
                        FilterPodsRequest.getPodsByNodeNameAndStatusPredicate(name, statuses))
                );
                return Optional.of(nodeInstance);
            }
        } catch (KubernetesClientException e) {
            log.warn("Node wasn't found in cluster.", e);
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

    /**
     * Terminates node by instance ID. Supports multiple regimes:
     *  - KUBE - node shall be removed from kubernetes cluster and cloud instance shall be stopped.
     *           regionId parameter will be ignored in this case.
     *  - CLOUD - cloud instance shall be stopped. If no regionId provided all regions
     *            with {@link AbstractCloudRegion#isClusterInclude()} flag shall be scanned
     *            for instance with specified ID.
     *  Other types not supported yet.
     * @param name - instance ID
     * @param machineType - type
     * @param regionId - region ID
     * @return terminated node description or error
     */
    public NodeInstance terminateKubeOrCloudNode(final String name, final MachineType machineType,
                                                 final Long regionId) {
        switch (machineType) {
            case KUBE:
                return terminateNode(name);
            case CLOUD:
                final Optional<NodeInstance> nodeInstance = Objects.nonNull(regionId)
                        ? findAndTerminateCloudNodeInRegion(regionManager.load(regionId), name)
                        : findAndTerminateCloudNode(name);
                return nodeInstance.orElseThrow(() -> new NodeNotFoundException(
                        messageHelper.getMessage(MessageConstants.ERROR_NODE_NOT_FOUND, name)));
            default:
                throw new UnsupportedOperationException("Exact machine type KUBE or CLOUD shall be specified!");
        }
    }

    public NodeInstance terminateNode(final String name, final boolean updateRunStatus) {
        final NodeInstance nodeInstance = getNode(name);
        terminateNode(nodeInstance, updateRunStatus);
        return nodeInstance;
    }

    public List<MasterNode> getMasterNodes() {
        final String defMasterPort =
                String.valueOf(preferenceManager.getPreference(SystemPreferences.CLUSTER_KUBE_MASTER_PORT));
        try (KubernetesClient client = kubernetesManager.getKubernetesClient()) {
            return client.nodes().withLabel(MASTER_LABEL).list().getItems()
                    .stream()
                    .filter(this::nodeIsReady)
                    .map(node -> MasterNode.fromNode(node, defMasterPort))
                    .collect(Collectors.toList());
        }
    }

    @SuppressWarnings("unchecked")
    public RunInfo loadRunIdForNode(final String nodeName) {
        final List<PipelineRun> runs = runCRUDService.loadRunsForNodeName(nodeName);
        return CommonUtils.first(
                //first active
            () -> runs.stream()
                        .filter(run -> !run.getStatus().isFinal() && Objects.isNull(run.getEndDate()))
                        .findFirst(),
                //latest finished
            () -> runs.stream()
                        .max(Comparator.comparing(PipelineRun::getEndDate)),
                //try to fetch run from node
            () -> findNode(nodeName)
                        .filter(node -> Objects.nonNull(node.getPipelineRun()))
                        .map(NodeInstance::getPipelineRun))
            .map(run -> new RunInfo(run.getId()))
            .orElse(null);
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

    /**
     * Loads all available nodes. Supports multiple regimes:
     *  - KUBE - loads nodes from kubernetes only
     *  - CLOUD - loads nodes from cloud provider only
     *  - ALL - loads nodes from both kubernetes and cloud providers
     * @param machineType - the type of regime described above
     * @return load nodes
     */
    public List<NodeInstance> getNodes(final MachineType machineType) {
        switch (machineType) {
            case KUBE:
                return getKubeNodes();
            case CLOUD:
                if (!authManager.isAdmin()) {
                    throw new AccessDeniedException(ACCESS_DENIED_MSG);
                }
                return getCloudNodes();
            case ALL:
                if (!authManager.isAdmin()) {
                    log.debug("Cloud nodes is not available for non-admin users. Only kube nodes will be loaded.");
                    return getKubeNodes();
                }
                final List<NodeInstance> kubeNodes = getKubeNodes();
                final List<NodeInstance> cloudNodes = getCloudNodes();
                return mergeNodesByMachineType(kubeNodes, cloudNodes);
            default:
                throw new UnsupportedOperationException(String.format("Unsupported type '%s'", machineType));
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
        terminateNode(nodeInstance, true);
    }

    private void terminateNode(final NodeInstance nodeInstance, final boolean updateRunStatus) {
        Assert.isTrue(!isNodeProtected(nodeInstance),
                messageHelper.getMessage(MessageConstants.ERROR_NODE_IS_PROTECTED, nodeInstance.getName()));

        if (updateRunStatus && nodeInstance.getPipelineRun() != null) {
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

    /**
     * Creates and attaches new disk to the run cloud instance.
     */
    public void attachDisk(final PipelineRun run, final DiskAttachRequest request, final Map<String, String> tags) {
        final Optional<RunInstance> instance = Optional.ofNullable(run.getInstance());
        final String nodeId = instance.map(RunInstance::getNodeId)
                .orElseThrow(() -> new IllegalArgumentException(messageHelper.getMessage(
                        MessageConstants.ERROR_RUN_DISK_ATTACHING_MISSING_NODE_ID)));
        final AbstractCloudRegion region = instance.map(RunInstance::getCloudRegionId)
                .map(regionManager::load)
                .orElseGet(regionManager::loadDefaultRegion);
        cloudFacade.attachDisk(region.getId(), run.getId(), request, tags);
        nodeDiskManager.register(nodeId, DiskRegistrationRequest.from(request));
        pipelineRunManager.adjustRunPricePerHourToDisks(run.getId(),
                cloudFacade.loadDisks(region.getId(), run.getId()));
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

    private void attachRunsInfo(final List<NodeInstance> nodeInstances) {
        if (CollectionUtils.isEmpty(nodeInstances)) {
            return;
        }
        final List<Long> runIds = nodeInstances
                .stream()
                .map(NodeInstance::getRunId)
                .filter(runId -> StringUtils.isNotBlank(runId) &&
                        NumberUtils.isDigits(runId))
                .map(Long::parseLong)
                .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(runIds)) {
            return;
        }
        final Map<String, PipelineRun> runs = ListUtils.emptyIfNull(pipelineRunManager.loadPipelineRuns(runIds))
                .stream()
                .collect(Collectors.toMap(run -> run.getId().toString(), Function.identity(), (r1, r2) -> r1));
        nodeInstances.forEach(node -> node.setPipelineRun(runs.get(node.getRunId())));
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public Long getNextFreeNodeId() {
        return clusterDao.createNextFreeNodeId();
    }

    private List<NodeInstance> getKubeNodes() {
        try (KubernetesClient client = kubernetesManager.getKubernetesClient()) {
            final List<NodeInstance> result = ListUtils.emptyIfNull(client.nodes().list().getItems())
                    .stream()
                    .map(NodeInstance::new)
                    .collect(Collectors.toList());
            attachRunsInfo(result);
            return result;
        }
    }

    private List<NodeInstance> getCloudNodes() {
        return getCloudNodes(null);
    }

    private List<NodeInstance> getCloudNodes(final FilterNodesVO filterNodesVO) {
        return ListUtils.emptyIfNull(regionManager.loadAll()).stream()
                .filter(AbstractCloudRegion::isClusterInclude)
                .flatMap(region -> getCloudNodesInRegion(region, filterNodesVO).stream())
                .collect(Collectors.toList());
    }

    private List<NodeInstance> getCloudNodesInRegion(final AbstractCloudRegion region,
                                                     final FilterNodesVO filterNodesVO) {
        try {
            return cloudFacade.getCloudNodes(region.getId(), filterNodesVO);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    private List<NodeInstance> mergeNodesByMachineType(final List<NodeInstance> kubeNodes,
                                                       final List<NodeInstance> cloudNodes) {
        if (CollectionUtils.isEmpty(cloudNodes)) {
            return kubeNodes;
        }
        final Map<String, NodeInstance> kubeNodesByInstanceId = kubeNodes.stream()
                .collect(Collectors.toMap(NodeInstance::getName, Function.identity()));
        cloudNodes.forEach(cloudNode -> kubeNodesByInstanceId.putIfAbsent(cloudNode.getName(), cloudNode));
        return new ArrayList<>(kubeNodesByInstanceId.values());
    }

    private Optional<NodeInstance> findAndTerminateCloudNode(final String instanceId) {
        return ListUtils.emptyIfNull(regionManager.loadAll()).stream()
                .filter(AbstractCloudRegion::isClusterInclude)
                .collect(Collectors.toList()).stream()
                .map(region -> findAndTerminateCloudNodeInRegion(region, instanceId))
                .filter(Optional::isPresent)
                .findFirst()
                .flatMap(Function.identity());
    }

    private Optional<NodeInstance> findAndTerminateCloudNodeInRegion(final AbstractCloudRegion region,
                                                                     final String instanceId) {
        final Optional<NodeInstance> cloudNodeInRegion = findCloudNodeInRegion(region, instanceId);
        if (cloudNodeInRegion.isPresent()) {
            final NodeInstance nodeInstance = cloudNodeInRegion.get();
            Assert.isTrue(!isNodeProtected(nodeInstance),
                    messageHelper.getMessage(MessageConstants.ERROR_NODE_IS_PROTECTED, nodeInstance.getName()));
            cloudFacade.terminateInstance(region.getId(), instanceId);
            return cloudNodeInRegion;
        }
        return Optional.empty();
    }

    private Optional<NodeInstance> findCloudNodeInRegion(final AbstractCloudRegion region, final String instanceId) {
        try {
            return cloudFacade.findCloudNode(region.getId(), instanceId);
        } catch (Exception e) {
            log.error(e.getMessage());
            return Optional.empty();
        }
    }

    private List<NodeInstance> filterCloudNodes(final FilterNodesVO filterNodesVO) {
        if (MapUtils.isNotEmpty(filterNodesVO.getLabels()) || StringUtils.isNotBlank(filterNodesVO.getRunId())) {
            // filters by kube-labels or runid are not applicable for cloud nodes
            return new ArrayList<>();
        }
        return StringUtils.isBlank(filterNodesVO.getAddress()) ? getCloudNodes() : getCloudNodes(filterNodesVO);
    }

    private List<NodeInstance> filterKubeNodes(final FilterNodesVO filterNodesVO) {
        List<NodeInstance> result;
        Config config = new Config();
        try (KubernetesClient client = kubernetesManager.getKubernetesClient(config)) {
            Map<String, String> labelsMap = new HashedMap<>();
            if (StringUtils.isNotBlank(filterNodesVO.getRunId())) {
                labelsMap.put(KubernetesConstants.RUN_ID_LABEL, filterNodesVO.getRunId());
            }
            if (MapUtils.isNotEmpty(filterNodesVO.getLabels())) {
                labelsMap.putAll(filterNodesVO.getLabels());
            }
            Predicate<NodeInstance> addressFilter = node -> true;
            if (StringUtils.isNotBlank(filterNodesVO.getAddress())) {
                Predicate<NodeInstanceAddress> addressEqualsPredicate = address ->
                        StringUtils.isNotBlank(address.getAddress()) &&
                                address.getAddress().equalsIgnoreCase(filterNodesVO.getAddress());
                addressFilter = node ->
                        node.getAddresses() != null && node.getAddresses()
                                .stream().anyMatch(addressEqualsPredicate);
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

    private boolean isActivePod(final PodInstance pod) {
        return ListUtils.emptyIfNull(pod.getContainers()).stream()
                .allMatch(ContainerInstance::isRunning);
    }

    private boolean isPodOnNodeIn(final PodInstance pod, final Set<String> nodes) {
        return StringUtils.isNotBlank(pod.getNodeName()) && nodes.contains(pod.getNodeName());
    }

    private List<NodeInstance> findKubeNodesByLabels(final Map<String, String> labels) {
        final FilterNodesVO filterNodesVO = new FilterNodesVO();
        filterNodesVO.setLabels(labels);
        return filterKubeNodes(filterNodesVO);
    }

    private List<PodInstance> findActivePodsByLabelsAndNodes(final Map<String, String> labels,
                                                             final List<NodeInstance> nodes) {
        final Set<String> nodeNames = ListUtils.emptyIfNull(nodes).stream()
                .map(NodeInstance::getName)
                .collect(Collectors.toSet());
        return ListUtils.emptyIfNull(kubernetesManager.getPodsByLabels(labels)).stream()
                .map(PodInstance::new)
                .filter(pod -> isPodOnNodeIn(pod, nodeNames))
                .filter(this::isActivePod)
                .collect(Collectors.toList());
    }
}
