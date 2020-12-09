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

import com.epam.pipeline.entity.cluster.InstanceDisk;
import com.epam.pipeline.entity.cluster.pool.InstanceRequest;
import com.epam.pipeline.entity.cluster.pool.NodePool;
import com.epam.pipeline.entity.cluster.pool.RunningInstance;
import com.epam.pipeline.entity.cluster.pool.filter.PoolFilter;
import com.epam.pipeline.entity.cluster.pool.filter.instancefilter.PoolInstanceFilter;
import com.epam.pipeline.entity.cluster.pool.filter.instancefilter.PoolInstanceFilterType;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.manager.cloud.CloudFacade;
import com.epam.pipeline.manager.cluster.autoscale.filter.PoolFilterHandler;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.utils.CommonUtils;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;

@Component
@Slf4j
public class ReassignHandler {

    private final AutoscalerService autoscalerService;
    private final CloudFacade cloudFacade;
    private final PipelineRunManager pipelineRunManager;
    private final Map<PoolInstanceFilterType, PoolFilterHandler> filterHandlers;


    public ReassignHandler(final AutoscalerService autoscalerService,
                           final CloudFacade cloudFacade,
                           final PipelineRunManager pipelineRunManager,
                           final List<PoolFilterHandler> filterHandlers) {
        this.autoscalerService = autoscalerService;
        this.cloudFacade = cloudFacade;
        this.pipelineRunManager = pipelineRunManager;
        this.filterHandlers = CommonUtils.groupByKey(filterHandlers, PoolFilterHandler::type);
    }

    public boolean tryReassignNode(final KubernetesClient client,
                                   final Set<String> scheduledRuns,
                                   final Set<String> reassignedNodes,
                                   final String runId,
                                   final long longId,
                                   final InstanceRequest requiredInstance,
                                   final List<String> freeNodes) {
        final Map<String, RunningInstance> freeInstances = ListUtils.emptyIfNull(freeNodes)
                .stream()
                .collect(HashMap::new,
                    (map, id) -> map.put(id, autoscalerService.getPreviousRunInstance(id, client)),
                    HashMap::putAll);
        // Try to find match with pre-pulled image
        final boolean reassignedWithMatchingImage = attemptReassign(freeInstances,
                autoscalerService::requirementsMatchWithImages, requiredInstance, runId,
                longId, scheduledRuns, reassignedNodes);
        if (reassignedWithMatchingImage) {
            return true;
        }
        return attemptReassign(freeInstances,
                autoscalerService::requirementsMatch, requiredInstance, runId,
                longId, scheduledRuns, reassignedNodes);

    }

    private boolean attemptReassign(final Map<String, RunningInstance> freeInstances,
                                    final BiFunction<RunningInstance, InstanceRequest, Boolean> matcher,
                                    final InstanceRequest requiredInstance,
                                    final String runId,
                                    final Long longId,
                                    final Set<String> scheduledRuns,
                                    final Set<String> reassignedNodes) {
        return freeInstances.entrySet()
                .stream()
                .anyMatch(entry -> {
                    final String previousId = entry.getKey();
                    final RunningInstance previousInstance = entry.getValue();
                    if (!matcher.apply(previousInstance, requiredInstance)) {
                        return false;
                    }
                    final boolean passedPoolFilter = Optional.ofNullable(previousInstance.getPool())
                            .map(pool -> matchesPoolFilter(pool, longId))
                            .orElse(true);
                    if (!passedPoolFilter) {
                        return false;
                    }
                    return reassignInstance(runId, longId, scheduledRuns, reassignedNodes,
                            previousId, previousInstance);
                });
    }

    private boolean matchesPoolFilter(final NodePool pool, final Long runId) {
        final PoolFilter filter = pool.getFilter();
        if (filter == null || filter.isEmpty()) {
            return true;
        }
        return pipelineRunManager.findRun(runId)
                .map(run -> matchRun(filter, run))
                .orElse(false);
    }

    private boolean matchRun(final PoolFilter filter, final PipelineRun run) {
        final List<PoolInstanceFilter> filters = filter.getFilters();
        switch (filter.getOperator()) {
            case AND:
                return filters.stream().allMatch(f -> matchRunToFilter(f, run));
            case OR:
                return filters.stream().anyMatch(f -> matchRunToFilter(f, run));
            default:
                throw new IllegalArgumentException("Unsupported filter operator: " + filter.getOperator());
        }
    }

    private boolean matchRunToFilter(final PoolInstanceFilter filter, final PipelineRun run) {
        log.debug("Matching run {} to filter pool filter {}.", run.getId(), filter);
        return Optional.ofNullable(filterHandlers.get(filter.getType()))
                .map(handler -> handler.matches(filter, run))
                .orElseGet(() -> {
                    log.debug("Failed to find a handler for pool filter {}.", filter);
                    return false;
                });
    }

    private boolean reassignInstance(final String newNodeId,
                                     final Long runId,
                                     final Set<String> scheduledRuns,
                                     final Set<String> reassignedNodes,
                                     final String previousNodeId,
                                     final RunningInstance previousInstance) {
        log.debug("Reassigning node ID {} to run {}.", previousNodeId, newNodeId);
        final boolean successfullyReassigned = previousNodeId.startsWith(AutoscaleContants.NODE_POOL_PREFIX) ?
                cloudFacade.reassignPoolNode(previousNodeId, runId) :
                cloudFacade.reassignNode(Long.valueOf(previousNodeId), runId);
        if (!successfullyReassigned) {
            return false;
        }
        scheduledRuns.add(newNodeId);
        final RunInstance instance = previousInstance.getInstance();
        final RunInstance reassignedInstance = StringUtils.isBlank(instance.getNodeId()) ?
                cloudFacade.describeInstance(runId, instance) : instance;
        pipelineRunManager.updateRunInstance(runId, reassignedInstance);
        final List<InstanceDisk> disks = cloudFacade.loadDisks(reassignedInstance.getCloudRegionId(),
                runId);
        autoscalerService.adjustRunPrices(runId, disks);
        reassignedNodes.add(previousNodeId);
        return true;
    }
}
