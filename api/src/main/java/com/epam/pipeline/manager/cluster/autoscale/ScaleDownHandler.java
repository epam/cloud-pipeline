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

import com.epam.pipeline.config.Constants;
import com.epam.pipeline.controller.vo.TagsVO;
import com.epam.pipeline.entity.cluster.pool.InstanceRequest;
import com.epam.pipeline.entity.cluster.pool.NodePool;
import com.epam.pipeline.entity.cluster.pool.RunningInstance;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.RunLog;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.cloud.CloudFacade;
import com.epam.pipeline.manager.cluster.KubernetesConstants;
import com.epam.pipeline.manager.cluster.KubernetesManager;
import com.epam.pipeline.manager.pipeline.PipelineRunCRUDService;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.pipeline.RunLogManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.NodeList;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class ScaleDownHandler {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern(Constants.FMT_ISO_LOCAL_DATE);
    private static final Duration FALLBACK_CLUSTER_NODE_UNAVAILABLE_GRACE_PERIOD = Duration.ofMinutes(30);
    private static final String NODE_UNAVAILABLE_TAG = "NODE_UNAVAILABLE";
    private static final String NODE_AVAILABILITY_MONITOR = "NodeAvailabilityMonitor";

    private final AutoscalerService autoscalerService;
    private final CloudFacade cloudFacade;
    private final PipelineRunManager pipelineRunManager;
    private final RunLogManager runLogManager;
    private final KubernetesManager kubernetesManager;
    private final PipelineRunCRUDService runCRUDService;
    private final PreferenceManager preferenceManager;

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    public void checkFreeNodes(final Set<String> scheduledRuns,
                               final KubernetesClient client,
                               final Set<String> pods) {
        final List<InstanceRequest> requiredInstances = getRequiredInstances(scheduledRuns, client);
        final Duration grace = getUnavailabilityGracePeriod();
        for (Node node : kubernetesManager.getAvailableNodes(client).getItems()) {
            try {
                scaleDownNodeIfFree(scheduledRuns, client, pods, requiredInstances, node, grace);
            } catch (Exception e) {
                log.error("Node {} processing has failed.", getNodeName(node), e);
            }
        }
    }

    private Duration getUnavailabilityGracePeriod() {
        return Optional.of(SystemPreferences.CLUSTER_NODE_UNAVAILABLE_GRACE_PERIOD_MINUTES)
                .map(preferenceManager::getPreference)
                .map(Duration::ofMinutes)
                .orElse(FALLBACK_CLUSTER_NODE_UNAVAILABLE_GRACE_PERIOD);
    }

    private void scaleDownNodeIfFree(final Set<String> scheduledRuns,
                                     final KubernetesClient client,
                                     final Set<String> pods,
                                     final List<InstanceRequest> requiredInstances,
                                     final Node node,
                                     final Duration grace) {
        final String name = getNodeName(node);
        final String label = getNodeLabel(node);

        if (isPaused(node)) {
            log.debug("Skipping paused node {} #{}...", name, label);
            return;
        }

        if (isUnavailable(node)) {
            log.debug("Processing unavailable node {} #{}...", name, label);
            processUnavailableNode(client, node, grace);
            return;
        }

        if (isRecovered(node)) {
            log.debug("Processing recovered node {} #{}...", name, label);
            processRecoveredNode(node);
        }

        if (isAssigned(node, scheduledRuns, pods)) {
            log.debug("Skipping assigned node {} #{}...", name, label);
            return;
        }

        log.debug("Processing unassigned node {} #{}...", name, label);
        scaleDownNode(client, node, requiredInstances);
    }

    private String getNodeName(final Node node) {
        return node.getMetadata().getName();
    }

    private String getNodeLabel(final Node node) {
        return node.getMetadata().getLabels().get(KubernetesConstants.RUN_ID_LABEL);
    }

    private boolean isPaused(final Node node) {
        return node.getMetadata().getLabels().get(KubernetesConstants.PAUSED_NODE_LABEL) != null;
    }

    private boolean isUnavailable(final Node node) {
        return kubernetesManager.isNodeUnavailable(node);
    }

    private void processUnavailableNode(final KubernetesClient client, final Node node, final Duration grace) {
        final LocalDateTime now = DateUtils.nowUTC();
        final LocalDateTime timestamp = kubernetesManager.getLastConditionDateTime(node).orElse(now);
        if (now.minus(grace).isBefore(timestamp)) {
            if (isUnavailableNodeLabeled(client, node)) {
                return;
            }
            log.debug("Marking unavailable node {} #{}", getNodeName(node), getNodeLabel(node));
            labelUnavailableNode(node, timestamp);
            findRun(node).ifPresent(run -> {
                labelUnavailableNodeRun(run, timestamp);
                logUnavailableNodeRun(run, timestamp);
            });
            return;
        }
        scaleDownUnavailableNode(client, node);
    }

    private boolean isUnavailableNodeLabeled(final KubernetesClient client, final Node node) {
        return kubernetesManager.getNodeLabels(client, getNodeName(node))
                .containsKey(KubernetesConstants.UNAVAILABLE_NODE_LABEL);
    }

    private void labelUnavailableNode(final Node node, final LocalDateTime timestamp) {
        kubernetesManager.addNodeLabel(getNodeName(node), KubernetesConstants.UNAVAILABLE_NODE_LABEL,
                KubernetesConstants.KUBE_LABEL_DATE_FORMATTER.format(timestamp));
    }

    private void labelUnavailableNodeRun(final PipelineRun run, final LocalDateTime timestamp) {
        final Map<String, String> tags = new HashMap<>();
        tags.put(NODE_UNAVAILABLE_TAG, KubernetesConstants.TRUE_LABEL_VALUE);
        getTimestampTag(NODE_UNAVAILABLE_TAG).ifPresent(tag -> tags.put(tag, DATE_TIME_FORMATTER.format(timestamp)));
        pipelineRunManager.updateTags(run.getId(), new TagsVO(tags), false);
    }

    private void logUnavailableNodeRun(final PipelineRun run, final LocalDateTime timestamp) {
        log(run, String.format("Node %s has been unavailable since %s", run.getInstance().getNodeId(),
                DATE_TIME_FORMATTER.format(timestamp)));
    }

    private void log(final PipelineRun run, final String text) {
        runLogManager.saveLog(RunLog.builder()
                .date(DateUtils.now())
                .runId(run.getId())
                .instance(run.getPodId())
                .status(TaskStatus.RUNNING)
                .taskName(NODE_AVAILABILITY_MONITOR)
                .logText(text)
                .build());
    }

    private void scaleDownUnavailableNode(final KubernetesClient client, final Node node) {
        if (isPooled(node)) {
            scaleDownUnavailablePoolNode(node);
        } else {
            scaleDownUnavailableRunNode(client, node);
        }
    }

    private void scaleDownUnavailablePoolNode(final Node node) {
        final String name = getNodeName(node);
        final String label = getNodeLabel(node);
        log.debug("Scaling down unavailable pool node {} #{}.", name, label);
        cloudFacade.scaleDownPoolNode(label);
    }

    private void scaleDownUnavailableRunNode(final KubernetesClient client, final Node node) {
        final String name = getNodeName(node);
        final String label = getNodeLabel(node);
        final Long runId = Long.parseLong(label);
        if (autoscalerService.getPreviousRunInstance(label, client) != null) {
            log.debug("Failing run of unavailable node {} #{}.", name, label);
            pipelineRunManager.updatePipelineStatusIfNotFinal(runId, TaskStatus.FAILURE);
            updatePodStatus(node, runId);
        }
        log.debug("Scaling down unavailable node {} #{}.", name, label);
        cloudFacade.scaleDownNode(runId);
    }

    private boolean isPooled(final Node node) {
        return getNodeLabel(node).startsWith(AutoscaleContants.NODE_POOL_PREFIX);
    }

    private boolean isRecovered(final Node node) {
        return getLabels(node).containsKey(KubernetesConstants.UNAVAILABLE_NODE_LABEL);
    }

    private Map<String, String> getLabels(final Node node) {
        return Optional.ofNullable(node)
                .map(Node::getMetadata)
                .map(ObjectMeta::getLabels)
                .orElseGet(Collections::emptyMap);
    }

    private void processRecoveredNode(final Node node) {
        final LocalDateTime now = DateUtils.nowUTC();
        final LocalDateTime timestamp = kubernetesManager.getLastConditionDateTime(node).orElse(now);
        log.debug("Marking recovered node {} #{}", getNodeName(node), getNodeLabel(node));
        labelRecoveredNode(node);
        findRun(node).ifPresent(run -> {
            labelRecoveredNodeRun(run);
            logRecoveredNodeRun(run, timestamp);
        });
    }

    private void labelRecoveredNode(final Node node) {
        kubernetesManager.removeNodeLabel(getNodeName(node), KubernetesConstants.UNAVAILABLE_NODE_LABEL);
    }

    private void labelRecoveredNodeRun(final PipelineRun run) {
        final Map<String, String> tags = MapUtils.emptyIfNull(run.getTags());
        tags.remove(NODE_UNAVAILABLE_TAG);
        getTimestampTag(NODE_UNAVAILABLE_TAG).ifPresent(tags::remove);
        pipelineRunManager.updateTags(run.getId(), new TagsVO(tags), true);
    }

    private void logRecoveredNodeRun(final PipelineRun run, final LocalDateTime timestamp) {
        log(run, String.format("Node %s has been available since %s", run.getInstance().getNodeId(),
                DATE_TIME_FORMATTER.format(timestamp)));
    }

    private Optional<PipelineRun> findRun(final Node node) {
        return Optional.ofNullable(getNodeLabel(node))
                .map(NumberUtils::toLong)
                .filter(runId -> runId > 0)
                .flatMap(pipelineRunManager::findRun);
    }

    private boolean isAssigned(final Node node, final Set<String> runs, final Set<String> pods) {
        final String label = getNodeLabel(node);
        return runs.contains(label) || pods.contains(label);
    }

    private void scaleDownNode(final KubernetesClient client, final Node node,
                               final List<InstanceRequest> requiredInstances) {
        if (isPooled(node)) {
            scaleDownPoolNodeIfNotRequired(client, node, requiredInstances);
        } else {
            scaleDownRunNodeIfNotRequired(client, node, requiredInstances);
        }
    }

    private void scaleDownPoolNodeIfNotRequired(final KubernetesClient client, final Node node,
                                                final List<InstanceRequest> requiredInstances) {
        final String nodeLabel = getNodeLabel(node);
        final NodePool nodePool = autoscalerService
                .findPool(nodeLabel, client)
                .orElse(null);

        if (nodePool == null) {
            log.debug("Scaling down pool node {} for a deleted pool.", nodeLabel);
            cloudFacade.scaleDownPoolNode(nodeLabel);
            return;
        }
        final Optional<InstanceRequest> matchingPipeline = requiredInstances.stream()
                .filter(instance -> autoscalerService
                        .requirementsMatch(nodePool.toRunningInstance(), instance))
                .findFirst();
        if (matchingPipeline.isPresent()) {
            requiredInstances.remove(matchingPipeline.get());
            log.debug("Leaving node {} free since it possibly matches a pending run.", nodeLabel);
        } else if (matchesActivePool(nodePool, client)) {
            log.debug("Leaving {} node in cluster as it matches an active pool.", nodePool);
        } else {
            log.debug("Scaling down pool node {}.", nodeLabel);
            cloudFacade.scaleDownPoolNode(nodeLabel);
        }
    }

    private void scaleDownRunNodeIfNotRequired(final KubernetesClient client, final Node node,
                                               final List<InstanceRequest> requiredInstances) {
        final String nodeLabel = getNodeLabel(node);
        final Long currentRunId = Long.parseLong(nodeLabel);
        final RunningInstance previousConfiguration = autoscalerService.getPreviousRunInstance(nodeLabel, client);

        if (previousConfiguration == null) {
            log.debug("Scaling down {} node for deleted pipeline.", nodeLabel);
            cloudFacade.scaleDownNode(currentRunId);
            return;
        }
        if (KubernetesConstants.WINDOWS.equalsIgnoreCase(previousConfiguration.getInstance().getNodePlatform())) {
            log.debug("Scaling down node {} for finished Windows-based pipeline.", nodeLabel);
            cloudFacade.scaleDownNode(currentRunId);
            return;
        }

        final Optional<InstanceRequest> matchingPipeline = requiredInstances.stream()
                .filter(instance -> autoscalerService
                        .requirementsMatch(previousConfiguration, instance))
                .findFirst();
        if (matchingPipeline.isPresent()) {
            requiredInstances.remove(matchingPipeline.get());
            log.debug("Leaving node {} free since it possibly matches a pending run.", nodeLabel);
        } else if (matchesActivePool(nodeLabel, client)) {
            log.debug("Leaving {} node in cluster as it matches active schedule.", nodeLabel);
        } else {
            if (cloudFacade.isNodeExpired(currentRunId)) {
                log.debug("Scaling down expired node {}.", nodeLabel);
                cloudFacade.scaleDownNode(currentRunId);
            } else {
                log.debug("Leaving node {} free.", nodeLabel);
            }
        }
    }

    private boolean matchesActivePool(final String nodeLabel,
                                      final KubernetesClient client) {
        return autoscalerService.findPool(nodeLabel, client)
                .map(node -> matchesActivePool(node, client))
                .orElse(false);
    }

    private boolean matchesActivePool(final NodePool nodePool,
                                      final KubernetesClient client) {
        if (!nodePool.isActive(DateUtils.nowUTC())) {
            return false;
        }
        final NodeList nodes = client.nodes()
                .withLabel(KubernetesConstants.NODE_POOL_ID_LABEL, String.valueOf(nodePool.getId()))
                .list();
        return nodes.getItems().size() <= nodePool.getCount();
    }

    private void updatePodStatus(final Node node, final Long id) {
        try {
            log.debug("Trying to update pod {} status ", id);
            final PipelineRun run = runCRUDService.loadRunById(id);
            final String currentStatus = run.getPodStatus() == null ? "" : run.getPodStatus();
            final String status = kubernetesManager.updateStatusWithNodeConditions(
                    new StringBuilder(currentStatus), node);
            if (!status.isEmpty() && !currentStatus.equals(status)) {
                pipelineRunManager.updatePodStatus(id, status);
            }
        } catch (IllegalArgumentException e) {
            log.debug(e.getMessage(), e);
        }
    }

    private List<InstanceRequest> getRequiredInstances(
            final Set<String> scheduledRuns,
            final KubernetesClient client) {
        final PodList podList = kubernetesManager.getPodList(client);
        return ListUtils.emptyIfNull(podList.getItems())
                .stream()
                .filter(kubernetesManager::isPodUnscheduled)
                .map(pod -> pod.getMetadata().getLabels().get(KubernetesConstants.RUN_ID_LABEL))
                .filter(runId -> !scheduledRuns.contains(runId))
                .map(runId -> {
                    final PipelineRun pipelineRun = runCRUDService.loadRunById(Long.parseLong(runId));
                    final InstanceRequest instanceRequest = new InstanceRequest();
                    instanceRequest.setRequestedImage(pipelineRun.getActualDockerImage());
                    instanceRequest.setInstance(autoscalerService.fillInstance(pipelineRun.getInstance()));
                    return instanceRequest;
                })
                .collect(Collectors.toList());
    }

    private Optional<String> getTimestampTag(final String name) {
        return Optional.of(SystemPreferences.SYSTEM_RUN_TAG_DATE_SUFFIX)
                .map(preferenceManager::getPreference)
                .filter(StringUtils::isNotEmpty)
                .map(suffix -> name + suffix);
    }
}
