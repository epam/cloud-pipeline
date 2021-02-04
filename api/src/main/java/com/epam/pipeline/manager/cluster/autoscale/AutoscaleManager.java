/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.manager.cluster.autoscale;

import com.epam.pipeline.entity.cluster.pool.InstanceRequest;
import com.epam.pipeline.entity.cluster.pool.NodePool;
import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.pipeline.run.parameter.PipelineRunParameter;
import com.epam.pipeline.exception.CmdExecutionException;
import com.epam.pipeline.exception.git.GitClientException;
import com.epam.pipeline.manager.cloud.CloudFacade;
import com.epam.pipeline.manager.cluster.KubernetesConstants;
import com.epam.pipeline.manager.cluster.KubernetesManager;
import com.epam.pipeline.manager.cluster.NodesManager;
import com.epam.pipeline.manager.cluster.cleaner.RunCleaner;
import com.epam.pipeline.manager.cluster.pool.NodePoolManager;
import com.epam.pipeline.manager.parallel.ParallelExecutorService;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.scheduling.AbstractSchedulingManager;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.SchedulerLock;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
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
@Slf4j
@ConditionalOnProperty(value = "cluster.disable.autoscaling", matchIfMissing = true, havingValue = "false")
@SuppressWarnings("PMD.AvoidCatchingGenericException")
public class AutoscaleManager extends AbstractSchedulingManager {
    private final AutoscaleManagerCore core;

    @Autowired
    public AutoscaleManager(final AutoscaleManagerCore core) {
        this.core = core;
    }

    @PostConstruct
    public void init() {
        if (preferenceManager.getPreference(SystemPreferences.CLUSTER_ENABLE_AUTOSCALING)) {
            scheduleFixedDelaySecured(core::runAutoscaling, SystemPreferences.CLUSTER_AUTOSCALE_RATE,
                    "Autoscaling job");
        }
    }

    @Component
    static class AutoscaleManagerCore {

        private final PipelineRunManager pipelineRunManager;
        private final ParallelExecutorService executorService;
        private final AutoscalerService autoscalerService;
        private final NodesManager nodesManager;
        private final KubernetesManager kubernetesManager;
        private final CloudFacade cloudFacade;
        private final PreferenceManager preferenceManager;
        private final String kubeNamespace;
        private final NodePoolManager nodePoolManager;
        private final ReassignHandler reassignHandler;
        private final ScaleDownHandler scaleDownHandler;
        private final List<RunCleaner> runCleaners;
        private final Set<Long> nodeUpTaskInProgress = ConcurrentHashMap.newKeySet();
        private final Map<Long, Integer> nodeUpAttempts = new ConcurrentHashMap<>();
        private final Map<Long, Integer> spotNodeUpAttempts = new ConcurrentHashMap<>();
        private final Map<Long, Integer> poolNodeUpTaskInProgress = new ConcurrentHashMap<>();

        @Autowired
        AutoscaleManagerCore(final PipelineRunManager pipelineRunManager,
                             final ParallelExecutorService executorService,
                             final AutoscalerService autoscalerService,
                             final NodesManager nodesManager,
                             final KubernetesManager kubernetesManager,
                             final PreferenceManager preferenceManager,
                             final @Value("${kube.namespace}") String kubeNamespace,
                             final CloudFacade cloudFacade,
                             final NodePoolManager nodePoolManager,
                             final ReassignHandler reassignHandler,
                             final ScaleDownHandler scaleDownHandler,
                             final List<RunCleaner> runCleaners) {
            this.pipelineRunManager = pipelineRunManager;
            this.executorService = executorService;
            this.autoscalerService = autoscalerService;
            this.nodesManager = nodesManager;
            this.kubernetesManager = kubernetesManager;
            this.cloudFacade = cloudFacade;
            this.kubeNamespace = kubeNamespace;
            this.preferenceManager = preferenceManager;
            this.nodePoolManager = nodePoolManager;
            this.reassignHandler = reassignHandler;
            this.scaleDownHandler = scaleDownHandler;
            this.runCleaners = runCleaners;
        }

        @SchedulerLock(name = "AutoscaleManager_runAutoscaling", lockAtMostForString = "PT10M")
        public void runAutoscaling() {
            log.debug("Starting autoscaling job.");
            Config config = new Config();
            Set<String> scheduledRuns = new HashSet<>();
            try (KubernetesClient client = kubernetesManager.getKubernetesClient(config)) {
                Set<String> nodes = kubernetesManager.getAvailableNodesIds(client);
                checkPendingPods(scheduledRuns, client, nodes);
                Set<String> pods = kubernetesManager.getAllPodIds(client);
                scaleDownHandler.checkFreeNodes(scheduledRuns, client, pods);
                checkPoolNodes(client);
                int clusterSize = kubernetesManager.getAvailableNodes(client).getItems().size();
                int nodeUpTasksSize = nodeUpTaskInProgress.size() + getPoolNodeUpTasksCount();

                log.debug(
                        "Finished autoscaling job. Minimum cluster size {}. Maximum cluster size {}. "
                                + "Current cluster size {}. Nodeup tasks in progress: {}. Total size: {}.",
                        preferenceManager.getPreference(SystemPreferences.CLUSTER_MIN_SIZE),
                        preferenceManager.getPreference(SystemPreferences.CLUSTER_MAX_SIZE),
                        clusterSize,
                        nodeUpTasksSize,
                        clusterSize + nodeUpTasksSize);
                log.debug("Current retry queue size: {}.", nodeUpAttempts.size());
                log.debug("Current pool instance queue: {}.", poolNodeUpTaskInProgress);
            } catch (KubernetesClientException e) {
                log.error(e.getMessage(), e);
            }
        }

        private void checkPendingPods(Set<String> scheduledRuns, KubernetesClient client, Set<String> nodes) {
            List<CompletableFuture<Void>> tasks = new ArrayList<>();
            PodList podList = kubernetesManager.getPodList(client);
            List<Pod> orderedPipelines = getOrderedPipelines(podList.getItems(), client);
            Set<String> allPods = kubernetesManager.convertKubeItemsToRunIdSet(podList.getItems());
            Set<String> reassignedNodes = new HashSet<>();
            orderedPipelines.forEach(pod -> {
                if (kubernetesManager.isPodUnscheduled(pod)) {
                    processPod(pod, client, scheduledRuns, tasks, allPods, nodes, reassignedNodes);
                }
            });
            if (!tasks.isEmpty()) {
                log.debug("Created {} nodeup tasks.", tasks.size());
            }
            log.debug("In progress {} nodeup tasks.", nodeUpTaskInProgress.size());
        }

        private void checkPoolNodes(final KubernetesClient client) {
            final List<NodePool> activePools = nodePoolManager.getActivePools();
            if (CollectionUtils.isEmpty(activePools)) {
                return;
            }
            final List<Node> currentNodes = ListUtils.emptyIfNull(kubernetesManager.getAvailableNodes(client)
                    .getItems());
            activePools.forEach(pool -> {
                final Integer activeTasks = poolNodeUpTaskInProgress.getOrDefault(pool.getId(), 0);
                log.debug("{} instance(s) are already created for pool {}.", activeTasks, pool);
                if (activeTasks >= pool.getCount()) {
                    return;
                }
                final long matchingNodeCount = currentNodes.stream().filter(currentNode -> {
                    final String nodeIdLabel = MapUtils.emptyIfNull(currentNode.getMetadata().getLabels())
                            .get(KubernetesConstants.NODE_POOL_ID_LABEL);
                    return StringUtils.isNotBlank(nodeIdLabel) && NumberUtils.isDigits(nodeIdLabel) &&
                            pool.getId().equals(Long.parseLong(nodeIdLabel));
                }).count();
                log.debug("Found {} existing instances matching {}.", matchingNodeCount, pool);
                final long totalCount = activeTasks + matchingNodeCount;
                if (totalCount < pool.getCount()) {
                    final long nodesToCreate = pool.getCount() - totalCount;
                    log.debug("Creating {} pool instance(s) for {}.", nodesToCreate, pool);
                    LongStream.range(0, nodesToCreate).forEach(i -> createPoolNode(pool, client));
                }
            });
        }

        private void createPoolNode(final NodePool node, final KubernetesClient client) {
            final int currentClusterSize = getCurrentClusterSize(client);
            final Integer maxClusterSize = preferenceManager.getPreference(SystemPreferences.CLUSTER_MAX_SIZE);
            if (currentClusterSize >= maxClusterSize) {
                log.debug("Reached maximum cluster size {} - current size {}.", maxClusterSize, currentClusterSize);
                return;
            }
            if (!hasFreeNodeUpThreads()) {
                return;
            }
            poolNodeUpTaskInProgress.merge(node.getId(), 1, (oldVal, newVal) -> oldVal + 1);
            CompletableFuture.runAsync(
                () -> {
                    Instant start = Instant.now();
                    String nodeId = AutoscaleContants.NODE_POOL_PREFIX + nodesManager.getNextFreeNodeId();
                    cloudFacade.scaleUpPoolNode(nodeId, node);
                    Instant end = Instant.now();
                    poolNodeUpTaskInProgress.merge(node.getId(), 0, (oldVal, newVal) -> oldVal - 1);
                    log.debug("Time to create {} : {} s.", node, Duration.between(start, end).getSeconds());
                },
                executorService.getExecutorService())
                .exceptionally(e -> {
                    log.error(e.getMessage(), e);
                    poolNodeUpTaskInProgress.merge(node.getId(), 0, (oldVal, newVal) -> oldVal - 1);
                    return null;
                });
        }

        private void processPod(Pod pod, KubernetesClient client, Set<String> scheduledRuns,
                                List<CompletableFuture<Void>> tasks, Set<String> allPods, Set<String> nodes,
                                Set<String> reassignedNodes) {
            log.debug("Found an unscheduled pod: {}.", pod.getMetadata().getName());
            Map<String, String> labels = pod.getMetadata().getLabels();
            String runId = labels.get(KubernetesConstants.RUN_ID_LABEL);
            long longId = Long.parseLong(runId);
            if (nodeUpTaskInProgress.contains(longId)) {
                log.debug("Nodeup task for ID {} is already in progress.", runId);
                return;
            }
            // Check whether node with required RunID is available
            if (nodes.contains(runId)) {
                log.debug("Node with required ID {} already exists.", runId);
                return;
            }
            //check max nodeup retry count
            int retryCount = nodeUpAttempts.getOrDefault(longId, 0); // TODO: should we lock here?
            int nodeUpRetryCount = preferenceManager.getPreference(SystemPreferences.CLUSTER_NODEUP_RETRY_COUNT);

            if (retryCount >= nodeUpRetryCount) {
                log.debug("Exceeded max nodeup attempts ({}) for run ID {}. Setting run status 'FAILURE'.",
                        retryCount, runId);
                pipelineRunManager.updatePipelineStatusIfNotFinal(longId, TaskStatus.FAILURE);
                removeNodeUpTask(longId);
                return;
            }

            try {
                InstanceRequest requiredInstance = getNewRunInstance(runId);
                // check whether instance already exists
                RunInstance instance = cloudFacade.describeInstance(longId, requiredInstance.getInstance());
                if (instance != null && instance.getNodeId() != null) {
                    log.debug("Found {} instance for run ID {}.", instance.getNodeId(), runId);
                    createNodeForRun(tasks, runId, requiredInstance);
                    return;
                }
                List<String> freeNodes =
                        nodes.stream().filter(nodeId -> !allPods.contains(nodeId)
                                && !reassignedNodes.contains(nodeId) &&
                                kubernetesManager.isNodeAvailable(client, nodeId))
                                .collect(Collectors.toList());
                log.debug("Found {} free nodes.", freeNodes.size());
                if (reassignHandler.tryReassignNode(client, scheduledRuns, reassignedNodes, runId,
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
                    log.debug("Current cluster size {} has reached limit {}. Checking free nodes.",
                            currentClusterSize, maxClusterSize);

                    List<String> nonMatchingFreeNodes =
                            freeNodes.stream().filter(id -> !reassignedNodes.contains(id))
                                    .collect(Collectors.toList());

                    if (!CollectionUtils.isEmpty(nonMatchingFreeNodes)) {
                        String nodeId = nonMatchingFreeNodes.get(0);
                        //to remove node from free
                        reassignedNodes.add(nodeId);
                        log.debug("Scaling down unused node {}.", nodeId);
                        cloudFacade.scaleDownNode(Long.valueOf(nodeId));
                    } else {
                        log.debug("Exceeded maximum cluster size {}.", currentClusterSize);
                        log.debug("Leaving pending run {}.", runId);
                        return;
                    }
                }
                if (!hasFreeNodeUpThreads()) {
                    return;
                }
                scheduledRuns.add(runId);
                createNodeForRun(tasks, runId, requiredInstance);
            } catch (Exception e) {
                log.error("Failed to create node for run {}.", runId);
                log.error("An error during pod processing: {}" + e.getMessage(), e);
            }
        }

        private boolean hasClusterCapacity(final KubernetesClient client) {
            final int currentClusterSize = getCurrentClusterSize(client);
            final Integer maxClusterSize = preferenceManager.getPreference(SystemPreferences.CLUSTER_MAX_SIZE);
            if (currentClusterSize > maxClusterSize) {
                log.debug("Exceeded maximum cluster size {} - current size {}.",
                        maxClusterSize, currentClusterSize);
                return false;
            }
            return true;
        }

        private boolean hasFreeNodeUpThreads() {
            final int maxNodeUpThreads = preferenceManager.getPreference(SystemPreferences.CLUSTER_NODEUP_MAX_THREADS);
            final int nodeUpTasks = nodeUpTaskInProgress.size() + getPoolNodeUpTasksCount();
            if (nodeUpTasks >= maxNodeUpThreads) {
                log.debug("Exceeded maximum node up tasks queue size {}.", nodeUpTasks);
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
                        log.debug("Pipeline run {} is already in final status", runId);
                        continue;
                    }
                    if (run.getStatus() == TaskStatus.PAUSED) {
                        log.debug("Pipeline run {} is paused", runId);
                        continue;
                    }
                    List<PipelineRunParameter> runParameters = run.getPipelineRunParameters();
                    if (!preferenceManager.getPreference(SystemPreferences.CLUSTER_RANDOM_SCHEDULING)) {
                        getParentId(parentIds, pod, runParameters);
                    }
                    checkedPods.add(pod);
                    priorityScore.put(pod, getParameterValue(runParameters, "priority-score", 0L));
                } catch (IllegalArgumentException e) {
                    log.error("Failed to load pipeline run {}.", runId);
                    log.error(e.getMessage(), e);
                    cleanDeletedRun(client, pod, runId);
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

        private void cleanDeletedRun(final KubernetesClient client, final Pod pod, final Long runId) {
            // If we failed to load a matching pipeline run for a pod, we delete it here, since
            // PodMonitor wont't process it either
            log.debug("Trying to clear resources for run {}.", runId);
            try {
                runCleaners.forEach(cleaner -> cleaner.cleanResources(runId));
            } catch (Exception e) {
                log.error("Error during resources clean up: {}", e.getMessage());
            }
            deletePod(pod, client);
            removeNodeUpTask(runId);
        }

        private void deletePod(Pod pod, KubernetesClient client) {
            try {
                client.pods().inNamespace(kubeNamespace).withName(pod.getMetadata().getName()).delete();
            } catch (KubernetesClientException e) {
                log.error(e.getMessage(), e);
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
                                      InstanceRequest requiredInstance) {
            long longId = Long.parseLong(runId);
            addNodeUpTask(longId);
            tasks.add(CompletableFuture.runAsync(() -> {
                Instant start = Instant.now();
                //save required instance
                pipelineRunManager.updateRunInstance(longId, requiredInstance.getInstance());
                RunInstance instance = cloudFacade.scaleUpNode(longId, requiredInstance.getInstance());
                //save instance ID and IP
                pipelineRunManager.updateRunInstance(longId, instance);
                autoscalerService.registerDisks(longId, instance);
                Instant end = Instant.now();
                removeNodeUpTask(longId);
                log.debug("Time to create a node for run {} : {} s.", runId,
                        Duration.between(start, end).getSeconds());
            }, executorService.getExecutorService()).exceptionally(e -> {
                log.error(e.getMessage(), e);

                if (e.getCause() instanceof CmdExecutionException &&
                        Objects.equals(AutoscaleContants.NODEUP_SPOT_FAILED_EXIT_CODE,
                                ((CmdExecutionException) e.getCause()).getExitCode())) {
                    spotNodeUpAttempts.merge(longId, 1, (oldVal, newVal) -> oldVal + 1);
                }
                if (e.getCause() instanceof CmdExecutionException && Objects.equals(
                        AutoscaleContants.NODEUP_LIMIT_EXCEEDED_EXIT_CODE,
                        ((CmdExecutionException) e.getCause()).getExitCode())) {
                    // do not fail and do not change attempts count if instance quota exceeded
                    nodeUpAttempts.merge(longId, 1, (oldVal, newVal) -> oldVal - 1);
                }

                removeNodeUpTask(longId, false);
                return null;
            }));
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
            return nodeUpTaskInProgress.size() + getPoolNodeUpTasksCount() +
                    kubernetesManager.getAvailableNodes(client).getItems().size();
        }

        public InstanceRequest getNewRunInstance(String runId) throws GitClientException {
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
            final InstanceRequest instanceRequest = new InstanceRequest();
            instanceRequest.setInstance(instance);
            instanceRequest.setRequestedImage(run.getActualDockerImage());
            return instanceRequest;
        }

        private int getPoolNodeUpTasksCount() {
            return MapUtils.emptyIfNull(poolNodeUpTaskInProgress)
                    .values()
                    .stream()
                    .mapToInt(i -> i)
                    .sum();
        }
    }
}
