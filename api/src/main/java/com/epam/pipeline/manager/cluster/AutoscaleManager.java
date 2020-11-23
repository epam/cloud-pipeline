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

import com.epam.pipeline.entity.cluster.DiskRegistrationRequest;
import com.epam.pipeline.entity.cluster.InstanceDisk;
import com.epam.pipeline.entity.cluster.schedule.PersistentNode;
import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.pipeline.run.parameter.PipelineRunParameter;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.exception.CmdExecutionException;
import com.epam.pipeline.exception.git.GitClientException;
import com.epam.pipeline.manager.cloud.CloudFacade;
import com.epam.pipeline.manager.cluster.schedule.PersistentNodeManager;
import com.epam.pipeline.manager.parallel.ParallelExecutorService;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.scheduling.AbstractSchedulingManager;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.NodeCondition;
import io.fabric8.kubernetes.api.model.NodeList;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodCondition;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import net.javacrumbs.shedlock.core.SchedulerLock;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

@Service
@ConditionalOnProperty(value = "cluster.disable.autoscaling", matchIfMissing = true, havingValue = "false")
@SuppressWarnings("PMD.AvoidCatchingGenericException")
public class AutoscaleManager extends AbstractSchedulingManager {
    public static final int NODEUP_SPOT_FAILED_EXIT_CODE = 5;
    public static final int NODEUP_LIMIT_EXCEEDED_EXIT_CODE = 6;

    private static final Logger LOGGER = LoggerFactory.getLogger(AutoscaleManager.class);

    private final AutoscaleManagerCore core;

    @Autowired
    public AutoscaleManager(final AutoscaleManagerCore core) {
        this.core = core;
    }

    @PostConstruct
    public void init() {
        if (preferenceManager.getPreference(SystemPreferences.CLUSTER_ENABLE_AUTOSCALING)) {
            scheduleFixedDelaySecured(core::runAutoscaling, SystemPreferences.CLUSTER_AUTOSCALE_RATE, "Autoscaling job");
        }
    }

    @Component
    static class AutoscaleManagerCore {

        private final PipelineRunManager pipelineRunManager;
        private final ParallelExecutorService executorService;
        private final AutoscalerService autoscalerService;
        private final NodesManager nodesManager;
        private final NodeDiskManager nodeDiskManager;
        private final KubernetesManager kubernetesManager;
        private final CloudFacade cloudFacade;
        private final PreferenceManager preferenceManager;
        private final String kubeNamespace;
        private static final String PERSISTENT_NODE_PREFIX = "f-";
        private final PersistentNodeManager persistentNodeManager;
        private final Set<Long> nodeUpTaskInProgress = ConcurrentHashMap.newKeySet();
        private final Map<Long, Integer> nodeUpAttempts = new ConcurrentHashMap<>();
        private final Map<Long, Integer> spotNodeUpAttempts = new ConcurrentHashMap<>();
        private final Set<Long> persitentNodeUpTaskInProgress = ConcurrentHashMap.newKeySet();

        @Autowired
        AutoscaleManagerCore(final PipelineRunManager pipelineRunManager,
                             final ParallelExecutorService executorService,
                             final AutoscalerService autoscalerService,
                             final NodesManager nodesManager,
                             final NodeDiskManager nodeDiskManager,
                             final KubernetesManager kubernetesManager,
                             final PreferenceManager preferenceManager,
                             final @Value("${kube.namespace}") String kubeNamespace,
                             final CloudFacade cloudFacade,
                             final PersistentNodeManager persistentNodeManager) {
            this.pipelineRunManager = pipelineRunManager;
            this.executorService = executorService;
            this.autoscalerService = autoscalerService;
            this.nodesManager = nodesManager;
            this.nodeDiskManager = nodeDiskManager;
            this.kubernetesManager = kubernetesManager;
            this.cloudFacade = cloudFacade;
            this.kubeNamespace = kubeNamespace;
            this.preferenceManager = preferenceManager;
            this.persistentNodeManager = persistentNodeManager;
        }

        @SchedulerLock(name = "AutoscaleManager_runAutoscaling", lockAtMostForString = "PT10M")
        public void runAutoscaling() {
            LOGGER.debug("Starting autoscaling job.");
            Config config = new Config();
            Set<String> scheduledRuns = new HashSet<>();
            try (KubernetesClient client = kubernetesManager.getKubernetesClient(config)) {
                Set<String> nodes = getAvailableNodesIds(client);
                checkPendingPods(scheduledRuns, client, nodes);
                Set<String> pods = getAllPodIds(client);
                checkFreeNodes(scheduledRuns, client, pods);
                checkPersistentNodes(client);
                int clusterSize = getAvailableNodes(client).getItems().size();
                int nodeUpTasksSize = nodeUpTaskInProgress.size() + persitentNodeUpTaskInProgress.size();

                LOGGER.debug(
                        "Finished autoscaling job. Minimum cluster size {}. Maximum cluster size {}. "
                                + "Current cluster size {}. Nodeup tasks in progress: {}. Total size: {}.",
                        preferenceManager.getPreference(SystemPreferences.CLUSTER_MIN_SIZE),
                        preferenceManager.getPreference(SystemPreferences.CLUSTER_MAX_SIZE),
                        clusterSize,
                        nodeUpTasksSize,
                        clusterSize + nodeUpTasksSize);
                LOGGER.debug("Current retry queue size: {}.", nodeUpAttempts.size());
            } catch (KubernetesClientException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }

        private void checkPendingPods(Set<String> scheduledRuns, KubernetesClient client, Set<String> nodes) {
            List<CompletableFuture<Void>> tasks = new ArrayList<>();
            PodList podList = getPodList(client);
            List<Pod> orderedPipelines = getOrderedPipelines(podList.getItems(), client);
            Set<String> allPods = convertKubeItemsToRunIdSet(podList.getItems());
            Set<String> reassignedNodes = new HashSet<>();
            orderedPipelines.forEach(pod -> {
                if (isPodUnscheduled(pod)) {
                    processPod(pod, client, scheduledRuns, tasks, allPods, nodes, reassignedNodes);
                }
            });
            if (!tasks.isEmpty()) {
                LOGGER.debug("Created {} nodeup tasks.", tasks.size());
            }
            LOGGER.debug("In progress {} nodeup tasks.", nodeUpTaskInProgress.size());
        }

        private void checkFreeNodes(Set<String> scheduledRuns, KubernetesClient client,
                                    Set<String> pods) {
            NodeList nodeList = getAvailableNodes(client);
            List<RunInstance> requiredInstances = getRequiredInstances(scheduledRuns, client);
            nodeList.getItems().forEach(node ->
                    scaleDownNodeIfFree(scheduledRuns, client, pods, requiredInstances, node));
        }

        private void scaleDownNodeIfFree(final Set<String> scheduledRuns,
                                         final KubernetesClient client,
                                         final Set<String> pods,
                                         final List<RunInstance> requiredInstances,
                                         final Node node) {
            final String nodeLabel = node.getMetadata().getLabels().get(KubernetesConstants.RUN_ID_LABEL);
            if (node.getMetadata().getLabels().get(KubernetesConstants.PAUSED_NODE_LABEL) != null) {
                LOGGER.debug("Node {} is paused.", nodeLabel);
                return;
            }
            if (scheduledRuns.contains(nodeLabel) || pods.contains(nodeLabel)) {
                LOGGER.debug("Node is already assigned to run {}.", nodeLabel);
                return;
            }
            if (nodeLabel.startsWith(PERSISTENT_NODE_PREFIX)) {
                scaleDownPersistentNodeIfNotRequired(nodeLabel, client, node, requiredInstances);
            } else {
                scaleDownRunNodeIfNotRequired(nodeLabel, client, node, requiredInstances);
            }
        }

        private void scaleDownPersistentNodeIfNotRequired(final String nodeLabel,
                                                          final KubernetesClient client,
                                                          final Node node,
                                                          final List<RunInstance> requiredInstances) {
            final PersistentNode persistentNode = findPersistentNodeId(nodeLabel, client)
                    .flatMap(persistentNodeManager::find).orElse(null);
            if (persistentNode == null) {
                LOGGER.debug("Scaling down persistent {} node for deleted schedule.", nodeLabel);
                cloudFacade.scaleDownPersistentNode(nodeLabel);
                return;
            }
            if (isNodeAvailable(node)) {
                LOGGER.debug("Scaling down unavailable {} node.", nodeLabel);
                cloudFacade.scaleDownPersistentNode(nodeLabel);
                return;
            }
            final Optional<RunInstance> matchingPipeline = requiredInstances.stream()
                    .filter(instance -> autoscalerService
                            .requirementsMatch(persistentNode.toRunInstance(), instance))
                    .findFirst();
            if (matchingPipeline.isPresent()) {
                requiredInstances.remove(matchingPipeline.get());
                LOGGER.debug("Leaving node {} free since it possibly matches a pending run.", nodeLabel);
            } else if (matchesActiveSchedule(persistentNode, client)) {
                LOGGER.debug("Leaving {} node in cluster as it matches active schedule.", persistentNode);
            } else {
                LOGGER.debug("Scaling down persistent node {}.", nodeLabel);
                cloudFacade.scaleDownPersistentNode(nodeLabel);
            }
        }

        private void scaleDownRunNodeIfNotRequired(final String nodeLabel,
                                                   final KubernetesClient client,
                                                   final Node node,
                                                   final List<RunInstance> requiredInstances) {
            final Long currentRunId = Long.parseLong(nodeLabel);
            final RunInstance previousConfiguration = getPreviousRunInstance(nodeLabel, client);

            if (!isNodeAvailable(node)) {
                if (previousConfiguration != null) {
                    LOGGER.debug("Trying to set failure status for run {}.", nodeLabel);
                    pipelineRunManager.updatePipelineStatusIfNotFinal(currentRunId, TaskStatus.FAILURE);
                    updatePodStatus(node, currentRunId);
                }
                LOGGER.debug("Scaling down unavailable {} node.", nodeLabel);
                cloudFacade.scaleDownNode(currentRunId);
                return;
            }
            if (previousConfiguration == null) {
                LOGGER.debug("Scaling down {} node for deleted pipeline.", nodeLabel);
                cloudFacade.scaleDownNode(currentRunId);
                return;
            }
            final Optional<RunInstance> matchingPipeline = requiredInstances.stream()
                    .filter(instance -> autoscalerService
                            .requirementsMatch(previousConfiguration, instance))
                    .findFirst();
            if (matchingPipeline.isPresent()) {
                requiredInstances.remove(matchingPipeline.get());
                LOGGER.debug("Leaving node {} free since it possibly matches a pending run.", nodeLabel);
            } else if (matchesActiveSchedule(nodeLabel, client)) {
                LOGGER.debug("Leaving {} node in cluster as it matches active schedule.", nodeLabel);
            } else {
                if (cloudFacade.isNodeExpired(currentRunId)) {
                    LOGGER.debug("Scaling down expired node {}.", nodeLabel);
                    cloudFacade.scaleDownNode(currentRunId);
                } else {
                    LOGGER.debug("Leaving node {} free.", nodeLabel);
                }
            }
        }

        private boolean matchesActiveSchedule(final String nodeLabel,
                                              final KubernetesClient client) {
            return findPersistentNodeId(nodeLabel, client)
                    .flatMap(persistentNodeManager::find)
                    .map(node -> matchesActiveSchedule(node, client))
                    .orElse(false);
        }

        private boolean matchesActiveSchedule(final PersistentNode persistentNode,
                                              final KubernetesClient client) {
            if (!persistentNode.isActive(DateUtils.nowUTC())) {
                return false;
            }
            final NodeList nodes = client.nodes()
                    .withLabel(KubernetesConstants.PERSISTENT_NODE_ID_LABEL, String.valueOf(persistentNode.getId()))
                    .list();
            return nodes.getItems().size() <= persistentNode.getCount();
        }


        private void checkPersistentNodes(final KubernetesClient client) {
            final List<PersistentNode> scheduledNodes = persistentNodeManager.getActiveScheduleNodes();
            if (CollectionUtils.isEmpty(scheduledNodes)) {
                return;
            }
            final List<Node> currentNodes = ListUtils.emptyIfNull(getAvailableNodes(client).getItems());
            scheduledNodes.forEach(node -> {
                if (persitentNodeUpTaskInProgress.contains(node.getId())) {
                    LOGGER.debug("Instance for {} is already created.", node);
                    return;
                }
                final long matchingNodeCount = currentNodes.stream().filter(currentNode -> {
                    final String nodeIdLabel = MapUtils.emptyIfNull(currentNode.getMetadata().getLabels())
                            .get(KubernetesConstants.PERSISTENT_NODE_ID_LABEL);
                    return StringUtils.isNotBlank(nodeIdLabel) && NumberUtils.isDigits(nodeIdLabel) &&
                            node.getId().equals(Long.parseLong(nodeIdLabel));
                }).count();
                LOGGER.debug("Found {} existing instances matching {}.", matchingNodeCount, node);
                if (matchingNodeCount < node.getCount()) {
                    final long nodesToCreate = node.getCount() - matchingNodeCount;
                    LOGGER.debug("Creating {} persistent instance(s) for {}.", nodesToCreate, node);
                    LongStream.range(0, nodesToCreate).forEach(i -> createPersistentNode(node, client));
                }
            });
        }

        private void createPersistentNode(final PersistentNode node, final KubernetesClient client) {
            final int currentClusterSize = getCurrentClusterSize(client);
            final Integer maxClusterSize = preferenceManager.getPreference(SystemPreferences.CLUSTER_MAX_SIZE);
            if (currentClusterSize >= maxClusterSize) {
                LOGGER.debug("Reached maximum cluster size {} - current size {}.", maxClusterSize, currentClusterSize);
                return;
            }
            if (!hasFreeNodeUpThreads()) {
                return;
            }
            persitentNodeUpTaskInProgress.add(node.getId());
            CompletableFuture.runAsync(
                () -> {
                    Instant start = Instant.now();
                    String nodeId = PERSISTENT_NODE_PREFIX + nodesManager.getNextFreeNodeId();
                    cloudFacade.scaleUpPersistentNode(nodeId, node);
                    Instant end = Instant.now();
                    persitentNodeUpTaskInProgress.remove(node.getId());
                    LOGGER.debug("Time to create {} : {} s.", node, Duration.between(start, end).getSeconds());
                },
                executorService.getExecutorService())
                .exceptionally(e -> {
                    LOGGER.error(e.getMessage(), e);
                    persitentNodeUpTaskInProgress.remove(node.getId());
                    return null;
                });
        }

        private List<RunInstance> getRequiredInstances(Set<String> scheduledRuns, KubernetesClient client) {
            PodList podList = getPodList(client);
            if (CollectionUtils.isEmpty(podList.getItems())) {
                return Collections.emptyList();
            }
            return podList.getItems().stream()
                    .filter(this::isPodUnscheduled)
                    .map(pod -> pod.getMetadata().getLabels().get(KubernetesConstants.RUN_ID_LABEL))
                    .filter(runId -> !scheduledRuns.contains(runId))
                    .map(runId -> {
                        RunInstance instance =
                                pipelineRunManager.loadPipelineRun(Long.parseLong(runId)).getInstance();
                        return autoscalerService.fillInstance(instance);
                    })
                    .collect(Collectors.toList());
        }

        private void processPod(Pod pod, KubernetesClient client, Set<String> scheduledRuns,
                                List<CompletableFuture<Void>> tasks, Set<String> allPods, Set<String> nodes,
                                Set<String> reassignedNodes) {
            LOGGER.debug("Found an unscheduled pod: {}.", pod.getMetadata().getName());
            Map<String, String> labels = pod.getMetadata().getLabels();
            String runId = labels.get(KubernetesConstants.RUN_ID_LABEL);
            long longId = Long.parseLong(runId);
            if (nodeUpTaskInProgress.contains(longId)) {
                LOGGER.debug("Nodeup task for ID {} is already in progress.", runId);
                return;
            }
            // Check whether node with required RunID is available
            if (nodes.contains(runId)) {
                LOGGER.debug("Node with required ID {} already exists.", runId);
                return;
            }
            //check max nodeup retry count
            int retryCount = nodeUpAttempts.getOrDefault(longId, 0); // TODO: should we lock here?
            int nodeUpRetryCount = preferenceManager.getPreference(SystemPreferences.CLUSTER_NODEUP_RETRY_COUNT);

            if (retryCount >= nodeUpRetryCount) {
                LOGGER.debug("Exceeded max nodeup attempts ({}) for run ID {}. Setting run status 'FAILURE'.",
                        retryCount, runId);
                pipelineRunManager.updatePipelineStatusIfNotFinal(longId, TaskStatus.FAILURE);
                removeNodeUpTask(longId);
                return;
            }

            try {
                RunInstance requiredInstance = getNewRunInstance(runId);
                // check whether instance already exists
                RunInstance instance = cloudFacade.describeInstance(longId, requiredInstance);
                if (instance != null && instance.getNodeId() != null) {
                    LOGGER.debug("Found {} instance for run ID {}.", instance.getNodeId(), runId);
                    createNodeForRun(tasks, runId, requiredInstance);
                    return;
                }
                List<String> freeNodes =
                        nodes.stream().filter(nodeId -> !allPods.contains(nodeId)
                                && !reassignedNodes.contains(nodeId) && isNodeAvailable(client, nodeId))
                                .collect(Collectors.toList());
                if (tryReassignNode(client, scheduledRuns, reassignedNodes, runId,
                        longId, requiredInstance, freeNodes)) {
                    return;
                }
                if (!hasClusterCapacity(client)) {
                    return;
                }
                int currentClusterSize = getCurrentClusterSize(client);
                Integer maxClusterSize = preferenceManager.getPreference(SystemPreferences.CLUSTER_MAX_SIZE);
                if (currentClusterSize == maxClusterSize &&
                        preferenceManager.getPreference(SystemPreferences.CLUSTER_KILL_NOT_MATCHING_NODES)) {
                    LOGGER.debug("Current cluster size {} has reached limit {}. Checking free nodes.",
                            currentClusterSize, maxClusterSize);

                    List<String> nonMatchingFreeNodes =
                            freeNodes.stream().filter(id -> !reassignedNodes.contains(id))
                                    .collect(Collectors.toList());

                    if (!CollectionUtils.isEmpty(nonMatchingFreeNodes)) {
                        String nodeId = nonMatchingFreeNodes.get(0);
                        //to remove node from free
                        reassignedNodes.add(nodeId);
                        LOGGER.debug("Scaling down unused node {}.", nodeId);
                        cloudFacade.scaleDownNode(Long.valueOf(nodeId));
                    } else {
                        LOGGER.debug("Exceeded maximum cluster size {}.", currentClusterSize);
                        LOGGER.debug("Leaving pending run {}.", runId);
                        return;
                    }
                }
                if (!hasFreeNodeUpThreads()) {
                    return;
                }
                scheduledRuns.add(runId);
                createNodeForRun(tasks, runId, requiredInstance);
            } catch (Exception e) {
                LOGGER.error("Failed to create node for run {}.", runId);
                LOGGER.error("An error during pod processing: {}" + e.getMessage(), e);
            }
        }

        private boolean tryReassignNode(final KubernetesClient client,
                                        final Set<String> scheduledRuns,
                                        final Set<String> reassignedNodes,
                                        final String runId,
                                        final long longId,
                                        final RunInstance requiredInstance,
                                        final List<String> freeNodes) {
            LOGGER.debug("Found {} free nodes.", freeNodes.size());
            //Try to reassign one of idle nodes
            for (String previousId : freeNodes) {
                LOGGER.debug("Found free node ID {}.", previousId);
                RunInstance previousInstance = getPreviousRunInstance(previousId, client);
                if (autoscalerService.requirementsMatch(requiredInstance, previousInstance)) {
                    LOGGER.debug("Reassigning node ID {} to run {}.", previousId, runId);
                    if (previousId.startsWith(PERSISTENT_NODE_PREFIX)) {
                        LOGGER.debug("Reassigning persistent node");
                        //TODO: reassign persistent node
                        return true;
                    } else {
                        boolean successfullyReassigned = cloudFacade.reassignNode(Long.valueOf(previousId), longId);
                        if (successfullyReassigned) {
                            scheduledRuns.add(runId);
                            pipelineRunManager.updateRunInstance(longId, previousInstance);
                            List<InstanceDisk> disks = cloudFacade.loadDisks(previousInstance.getCloudRegionId(),
                                    longId);
                            adjustRunPrices(longId, disks);
                            reassignedNodes.add(previousId);
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        private boolean hasClusterCapacity(final KubernetesClient client) {
            final int currentClusterSize = getCurrentClusterSize(client);
            final Integer maxClusterSize = preferenceManager.getPreference(SystemPreferences.CLUSTER_MAX_SIZE);
            if (currentClusterSize > maxClusterSize) {
                LOGGER.debug("Exceeded maximum cluster size {} - current size {}.",
                        maxClusterSize, currentClusterSize);
                return false;
            }
            return true;
        }

        private boolean hasFreeNodeUpThreads() {
            final int maxNodeUpThreads = preferenceManager.getPreference(SystemPreferences.CLUSTER_NODEUP_MAX_THREADS);
            final int nodeUpTasks = nodeUpTaskInProgress.size() + persitentNodeUpTaskInProgress.size();
            if (nodeUpTasks >= maxNodeUpThreads) {
                LOGGER.debug("Exceeded maximum node up tasks queue size {}.", nodeUpTasks);
                return false;
            }
            return true;
        }

        private List<Pod> getOrderedPipelines(List<Pod> items, KubernetesClient client) {
            Map<Pod, Long> parentIds = new HashMap<>();
            Map<Pod, Long> priorityScore = new HashMap<>();
            List<Pod> checkedPods = new ArrayList<>();

            for (Pod pod : items) {
                Long runId = Long.parseLong(pod.getMetadata().getLabels().get(KubernetesConstants.RUN_ID_LABEL));
                try {
                    PipelineRun run = pipelineRunManager.loadPipelineRun(runId);
                    if (run.getStatus().isFinal()) {
                        LOGGER.debug("Pipeline run {} is already in final status", runId);
                        continue;
                    }
                    if (run.getStatus() == TaskStatus.PAUSED) {
                        LOGGER.debug("Pipeline run {} is paused", runId);
                        continue;
                    }
                    List<PipelineRunParameter> runParameters = run.getPipelineRunParameters();
                    if (!preferenceManager.getPreference(SystemPreferences.CLUSTER_RANDOM_SCHEDULING)) {
                        getParentId(parentIds, pod, runParameters);
                    }
                    checkedPods.add(pod);
                    priorityScore.put(pod, getParameterValue(runParameters, "priority-score", 0L));
                } catch (IllegalArgumentException e) {
                    LOGGER.error("Failed to load pipeline run {}.", runId);
                    LOGGER.error(e.getMessage(), e);
                    // If we failed to load a matching pipeline run for a pod, we delete it here, since
                    // PodMonitor wont't process it either
                    deletePod(pod, client);
                    removeNodeUpTask(runId);
                }
            }
            if (!CollectionUtils.isEmpty(checkedPods)) {
                checkedPods.sort((p1, p2) -> {
                    if (!preferenceManager.getPreference(SystemPreferences.CLUSTER_RANDOM_SCHEDULING)) {
                        Long parentId1 = parentIds.get(p1);
                        Long parentId2 = parentIds.get(p2);
                        if (!parentId1.equals(parentId2)) {
                            return Long.compare(parentId1, parentId2);
                        }
                    }
                    return Long.compare(priorityScore.get(p2), priorityScore.get(p1));
                });
                return checkedPods;
            } else {
                return Collections.emptyList();
            }
        }

        private void deletePod(Pod pod, KubernetesClient client) {
            try {
                client.pods().inNamespace(kubeNamespace).withName(pod.getMetadata().getName()).delete();
            } catch (KubernetesClientException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }

        private void getParentId(Map<Pod, Long> parentIds, Pod pod,
                                 List<PipelineRunParameter> runParameters) {
            boolean highNonBatchPriority = preferenceManager.getPreference(
                    SystemPreferences.CLUSTER_HIGH_NON_BATCH_PRIORITY);

            Long parentIdDefault = highNonBatchPriority ? 0L : Long.MAX_VALUE;
            Long parentID = getParameterValue(runParameters, "parent-id", parentIdDefault);
            if (!highNonBatchPriority && parentID == 0L) {
                parentIds.put(pod, Long.MAX_VALUE);
            } else {
                parentIds.put(pod, parentID);
            }
        }

        private Long getParameterValue(List<PipelineRunParameter> runParameters, String paramName, Long defaultValue) {
            Optional<PipelineRunParameter> value = runParameters.stream()
                    .filter(parameter -> parameter.getName().equals(paramName)).findAny();
            if (value.isPresent() && NumberUtils.isDigits(value.get().getValue())) {
                return Long.parseLong(value.get().getValue());
            } else {
                return defaultValue;
            }
        }

        private void createNodeForRun(List<CompletableFuture<Void>> tasks, String runId,
                                      RunInstance requiredInstance) {
            long longId = Long.parseLong(runId);
            addNodeUpTask(longId);
            tasks.add(CompletableFuture.runAsync(() -> {
                Instant start = Instant.now();
                //save required instance
                pipelineRunManager.updateRunInstance(longId, requiredInstance);
                RunInstance instance = cloudFacade.scaleUpNode(longId, requiredInstance);
                //save instance ID and IP
                pipelineRunManager.updateRunInstance(longId, instance);
                List<InstanceDisk> disks = cloudFacade.loadDisks(instance.getCloudRegionId(), longId);
                registerNodeDisks(longId, disks);
                adjustRunPrices(longId, disks);
                Instant end = Instant.now();
                removeNodeUpTask(longId);
                LOGGER.debug("Time to create a node for run {} : {} s.", runId,
                        Duration.between(start, end).getSeconds());
            }, executorService.getExecutorService()).exceptionally(e -> {
                LOGGER.error(e.getMessage(), e);

                if (e.getCause() instanceof CmdExecutionException &&
                        Objects.equals(NODEUP_SPOT_FAILED_EXIT_CODE,
                                ((CmdExecutionException) e.getCause()).getExitCode())) {
                    spotNodeUpAttempts.merge(longId, 1, (oldVal, newVal) -> oldVal + 1);
                }
                if (e.getCause() instanceof CmdExecutionException && Objects.equals(
                        NODEUP_LIMIT_EXCEEDED_EXIT_CODE, ((CmdExecutionException) e.getCause()).getExitCode())) {
                    // do not fail and do not change attempts count if instance quota exceeded
                    nodeUpAttempts.merge(longId, 1, (oldVal, newVal) -> oldVal - 1);
                }

                removeNodeUpTask(longId, false);
                return null;
            }));
        }

        private void registerNodeDisks(long runId, List<InstanceDisk> disks) {
            PipelineRun run = pipelineRunManager.loadPipelineRun(runId);
            String nodeId = run.getInstance().getNodeId();
            LocalDateTime creationDate = DateUtils.convertDateToLocalDateTime(run.getStartDate());
            List<DiskRegistrationRequest> requests = DiskRegistrationRequest.from(disks);
            nodeDiskManager.register(nodeId, creationDate, requests);
        }

        private void adjustRunPrices(long longId, List<InstanceDisk> disks) {
            pipelineRunManager.adjustRunPricePerHourToDisks(longId, disks);
        }

        private void addNodeUpTask(long longId) {
            nodeUpTaskInProgress.add(longId);
            nodeUpAttempts.merge(longId, 1, (oldVal, newVal) -> oldVal + 1);
        }

        private void removeNodeUpTask(long longId) {
            removeNodeUpTask(longId, true);
        }

        private void removeNodeUpTask(long longId, boolean clearAttempts) {
            nodeUpTaskInProgress.remove(longId);
            if (clearAttempts) {
                nodeUpAttempts.remove(longId);
                spotNodeUpAttempts.remove(longId);
            }
        }

        private int getCurrentClusterSize(KubernetesClient client) {
            return nodeUpTaskInProgress.size() + persitentNodeUpTaskInProgress.size() +
                    getAvailableNodes(client).getItems().size();
        }

        private boolean isNodeAvailable(final KubernetesClient client, final String nodeId) {
            return client.nodes()
                    .withLabel(KubernetesConstants.RUN_ID_LABEL, nodeId)
                    .withoutLabel(KubernetesConstants.PAUSED_NODE_LABEL)
                    .list().getItems()
                    .stream()
                    .findFirst()
                    .filter(this::isNodeAvailable)
                    .isPresent();
        }

        private boolean isNodeAvailable(final Node node) {
            if (node == null) {
                return false;
            }
            List<NodeCondition> conditions = node.getStatus().getConditions();
            if (CollectionUtils.isEmpty(conditions)) {
                return true;
            }
            String lastReason = conditions.get(0).getReason();
            for (String reason : KubernetesConstants.NODE_OUT_OF_ORDER_REASONS) {
                if (lastReason.contains(reason)) {
                    LOGGER.debug("Node is out of order: {}", conditions);
                    return false;
                }
            }
            return true;
        }

        public RunInstance getNewRunInstance(String runId) throws GitClientException {
            Long longRunId = Long.parseLong(runId);
            PipelineRun run = pipelineRunManager.loadPipelineRun(longRunId);

            RunInstance instance;
            if (run.getInstance() == null || run.getInstance().isEmpty()) {
                PipelineConfiguration configuration = pipelineRunManager.loadRunConfiguration(longRunId);
                instance = autoscalerService.configurationToInstance(configuration);
            } else {
                instance = autoscalerService.fillInstance(run.getInstance());
            }

            if (instance.getSpot() != null && instance.getSpot() &&
                    spotNodeUpAttempts.getOrDefault(longRunId, 0) >= preferenceManager.getPreference(
                            SystemPreferences.CLUSTER_SPOT_MAX_ATTEMPTS)) {
                instance.setSpot(false);
                pipelineRunManager.updateRunInstance(longRunId, instance);
            }

            return instance;
        }

        private RunInstance getPreviousRunInstance(final String nodeLabel, final KubernetesClient client) {
            return findPersistentNodeId(nodeLabel, client)
                    .flatMap(persistentNodeManager::find)
                    .map(PersistentNode::toRunInstance)
                    .orElseGet(() -> {
                        try {
                            return pipelineRunManager.loadPipelineRun(Long.parseLong(nodeLabel)).getInstance();
                        } catch (IllegalArgumentException e) {
                            LOGGER.trace(e.getMessage(), e);
                            return null;
                        }
                    });
        }

        private Optional<Long> findPersistentNodeId(final String nodeLabel, final KubernetesClient client) {
            final NodeList nodes = client.nodes()
                    .withLabel(KubernetesConstants.RUN_ID_LABEL, nodeLabel)
                    .withoutLabel(KubernetesConstants.PAUSED_NODE_LABEL)
                    .list();
            return ListUtils.emptyIfNull(nodes.getItems())
                    .stream()
                    .filter(node ->
                            MapUtils.emptyIfNull(node.getMetadata().getLabels())
                                    .containsKey(KubernetesConstants.PERSISTENT_NODE_ID_LABEL))
                    .map(node -> MapUtils.emptyIfNull(node.getMetadata().getLabels())
                            .get(KubernetesConstants.PERSISTENT_NODE_ID_LABEL))
                    .filter(NumberUtils::isDigits)
                    .map(Long::parseLong)
                    .findFirst();
        }

        private boolean isPodUnscheduled(Pod pod) {
            String phase = pod.getStatus().getPhase();
            if (KubernetesConstants.POD_SUCCEEDED_PHASE.equals(phase)
                    || KubernetesConstants.POD_FAILED_PHASE.equals(phase)) {
                return false;
            }
            List<PodCondition> conditions = pod.getStatus().getConditions();
            return !CollectionUtils.isEmpty(conditions) && KubernetesConstants.POD_UNSCHEDULABLE
                    .equals(conditions.get(0).getReason());
        }

        private NodeList getAvailableNodes(KubernetesClient client) {
            return client.nodes().withLabel(KubernetesConstants.RUN_ID_LABEL)
                    .withoutLabel(KubernetesConstants.PAUSED_NODE_LABEL)
                    .list();
        }

        private Set<String> getAvailableNodesIds(KubernetesClient client) {
            NodeList nodeList = getAvailableNodes(client);
            return convertKubeItemsToRunIdSet(nodeList.getItems());
        }

        private Set<String> getAllPodIds(KubernetesClient client) {
            PodList podList = getPodList(client);
            return convertKubeItemsToRunIdSet(podList.getItems());
        }

        private PodList getPodList(KubernetesClient client) {
            return client.pods()
                    .inNamespace(kubeNamespace)
                    .withLabel("type", "pipeline")
                    .withLabel(KubernetesConstants.RUN_ID_LABEL).list();
        }

        private Set<String> convertKubeItemsToRunIdSet(List<? extends HasMetadata> items) {
            if (CollectionUtils.isEmpty(items)) {
                return Collections.emptySet();
            }
            return items.stream()
                    .map(item -> item.getMetadata().getLabels().get(KubernetesConstants.RUN_ID_LABEL))
                    .collect(Collectors.toSet());
        }

        private void updatePodStatus(Node node, Long id) {
            try {
                LOGGER.debug("Trying to update pod {} status ", id);
                PipelineRun run = pipelineRunManager.loadPipelineRun(id);
                String currentStatus = run.getPodStatus() == null ? "" : run.getPodStatus();
                String status = kubernetesManager.updateStatusWithNodeConditions(
                        new StringBuilder(currentStatus), node);
                if (!status.isEmpty() && !currentStatus.equals(status)) {
                    pipelineRunManager.updatePodStatus(id, status);
                }
            } catch (IllegalArgumentException e) {
                LOGGER.debug(e.getMessage(), e);
            }

        }
    }
}
