/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
import com.epam.pipeline.entity.cluster.pool.RunningInstance;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.cloud.CloudFacade;
import com.epam.pipeline.manager.cluster.KubernetesConstants;
import com.epam.pipeline.manager.cluster.KubernetesManager;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.NodeList;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class ScaleDownHandler {

    private final AutoscalerService autoscalerService;
    private final CloudFacade cloudFacade;
    private final PipelineRunManager pipelineRunManager;
    private final KubernetesManager kubernetesManager;

    public void checkFreeNodes(final Set<String> scheduledRuns,
                               final KubernetesClient client,
                               final Set<String> pods) {
        final List<InstanceRequest> requiredInstances = getRequiredInstances(scheduledRuns, client);
        kubernetesManager.getAvailableNodes(client)
                .getItems()
                .forEach(node -> scaleDownNodeIfFree(scheduledRuns, client, pods, requiredInstances, node));
    }

    private void scaleDownNodeIfFree(final Set<String> scheduledRuns,
                                     final KubernetesClient client,
                                     final Set<String> pods,
                                     final List<InstanceRequest> requiredInstances,
                                     final Node node) {
        final String nodeLabel = node.getMetadata().getLabels().get(KubernetesConstants.RUN_ID_LABEL);
        if (node.getMetadata().getLabels().get(KubernetesConstants.PAUSED_NODE_LABEL) != null) {
            log.debug("Node {} is paused.", nodeLabel);
            return;
        }
        //TODO: refactor this
        final boolean poolNode = nodeLabel.startsWith(AutoscaleContants.NODE_POOL_PREFIX);
        if (kubernetesManager.isNodeUnavailable(node)) {
            if (poolNode) {
                log.debug("Scaling down unavailable {} pool node.", nodeLabel);
                cloudFacade.scaleDownPoolNode(nodeLabel);
            } else {
                final Long currentRunId = Long.parseLong(nodeLabel);
                if (autoscalerService.getPreviousRunInstance(nodeLabel, client) != null) {
                    log.debug("Trying to set failure status for run {}.", nodeLabel);
                    pipelineRunManager.updatePipelineStatusIfNotFinal(currentRunId, TaskStatus.FAILURE);
                    updatePodStatus(node, currentRunId);
                }
                log.debug("Scaling down unavailable {} node.", nodeLabel);
                cloudFacade.scaleDownNode(currentRunId);
            }
            return;
        }
        if (scheduledRuns.contains(nodeLabel) || pods.contains(nodeLabel)) {
            log.debug("Node is already assigned to run {}.", nodeLabel);
            return;
        }
        if (poolNode) {
            scaleDownPoolNodeIfNotRequired(nodeLabel, client, node, requiredInstances);
        } else {
            scaleDownRunNodeIfNotRequired(nodeLabel, client, requiredInstances);
        }
    }

    private void scaleDownPoolNodeIfNotRequired(final String nodeLabel,
                                                final KubernetesClient client,
                                                final Node node,
                                                final List<InstanceRequest> requiredInstances) {
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

    private void scaleDownRunNodeIfNotRequired(final String nodeLabel,
                                               final KubernetesClient client,
                                               final List<InstanceRequest> requiredInstances) {
        final Long currentRunId = Long.parseLong(nodeLabel);
        final RunningInstance previousConfiguration = autoscalerService.getPreviousRunInstance(nodeLabel, client);

        if (previousConfiguration == null) {
            log.debug("Scaling down {} node for deleted pipeline.", nodeLabel);
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
            final PipelineRun run = pipelineRunManager.loadPipelineRun(id);
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
                    final PipelineRun pipelineRun = pipelineRunManager.loadPipelineRun(Long.parseLong(runId));
                    final InstanceRequest instanceRequest = new InstanceRequest();
                    instanceRequest.setRequestedImage(pipelineRun.getActualDockerImage());
                    instanceRequest.setInstance(autoscalerService.fillInstance(pipelineRun.getInstance()));
                    return instanceRequest;
                })
                .collect(Collectors.toList());
    }
}
