/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.cluster.InstanceDisk;
import com.epam.pipeline.entity.cluster.pool.InstanceRequest;
import com.epam.pipeline.entity.cluster.pool.NodePool;
import com.epam.pipeline.entity.cluster.pool.RunningInstance;
import com.epam.pipeline.entity.cluster.pool.filter.PoolFilter;
import com.epam.pipeline.entity.cluster.pool.filter.instancefilter.PoolInstanceFilter;
import com.epam.pipeline.entity.cluster.pool.filter.instancefilter.PoolInstanceFilterType;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.pipeline.run.parameter.PipelineRunParameter;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.cloud.CloudFacade;
import com.epam.pipeline.manager.cluster.KubernetesConstants;
import com.epam.pipeline.manager.cluster.autoscale.filter.PoolFilterHandler;
import com.epam.pipeline.manager.metadata.MetadataManager;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.pipeline.RunStatusManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.utils.CommonUtils;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;

@Component
@Slf4j
public class ReassignHandler {
    private static final String CP_CREATE_NEW_NODE = "CP_CREATE_NEW_NODE";
    private static final String CP_CAP_DEDICATED_INSTANCE = "CP_CAP_DEDICATED_INSTANCE";
    private static final String CP_CAP_POOL_ID = "CP_CAP_POOL_ID";

    private final AutoscalerService autoscalerService;
    private final CloudFacade cloudFacade;
    private final PipelineRunManager pipelineRunManager;
    private final RunStatusManager runStatusManager;
    private final Map<PoolInstanceFilterType, PoolFilterHandler> filterHandlers;
    private final MetadataManager metadataManager;
    private final IAMProfileVerifier iamProfileVerifier;
    private final MessageHelper messageHelper;
    private final PreferenceManager preferenceManager;

    public ReassignHandler(final AutoscalerService autoscalerService,
                           final CloudFacade cloudFacade,
                           final PipelineRunManager pipelineRunManager,
                           final List<PoolFilterHandler> filterHandlers,
                           final MetadataManager metadataManager,
                           final IAMProfileVerifier iamProfileVerifier,
                           final RunStatusManager runStatusManager,
                           final MessageHelper messageHelper,
                           final PreferenceManager preferenceManager) {
        this.autoscalerService = autoscalerService;
        this.cloudFacade = cloudFacade;
        this.pipelineRunManager = pipelineRunManager;
        this.filterHandlers = CommonUtils.groupByKey(filterHandlers, PoolFilterHandler::type);
        this.metadataManager = metadataManager;
        this.iamProfileVerifier = iamProfileVerifier;
        this.runStatusManager = runStatusManager;
        this.messageHelper = messageHelper;
        this.preferenceManager = preferenceManager;
    }

    public boolean tryReassignNode(final KubernetesClient client,
                                   final Set<String> scheduledRuns,
                                   final Set<String> reassignedNodes,
                                   final String runId,
                                   final long longId,
                                   final InstanceRequest requiredInstance,
                                   final List<String> freeNodes) {
        final Optional<PipelineRun> pipelineRun = pipelineRunManager.findRun(longId);
        if (!pipelineRun.map(this::isReassignAllowed).orElse(true)) {
            log.debug("Reassign is not allowed for run '{}'", runId);
            return false;
        }

        final Map<String, RunningInstance> freeInstances = ListUtils.emptyIfNull(freeNodes)
                .stream()
                .collect(HashMap::new,
                    (map, id) -> map.put(id, autoscalerService.getPreviousRunInstance(id, client)),
                    HashMap::putAll);

        freeInstances.values()
            .removeIf(entry -> KubernetesConstants.WINDOWS.equals(entry.getInstance().getNodePlatform()));
        if (MapUtils.isEmpty(freeInstances)) {
            log.debug("Available nodes are not suitable for the reassign.");
            return false;
        }

        final Optional<Long> requiredPoolId = pipelineRun.flatMap(this::requiredPoolId);

        // Try to find match with pre-pulled image. This is not the last attempt on this tick, so a required
        // pool's own node that fails here does not fail the run yet. The plain attempt below may still take it.
        final boolean reassignedWithMatchingImage = attemptReassign(freeInstances,
                autoscalerService::requirementsMatchWithImages, requiredInstance, runId, longId,
                pipelineRun, requiredPoolId, scheduledRuns, reassignedNodes, false);
        if (reassignedWithMatchingImage) {
            return true;
        }
        // The last and most permissive attempt on this tick. Nothing looser is left to try, so a required
        // pool's own node that fails here is reported as a final answer.
        return attemptReassign(freeInstances,
                autoscalerService::requirementsMatch, requiredInstance, runId, longId,
                pipelineRun, requiredPoolId, scheduledRuns, reassignedNodes, true);

    }

    /**
     * A run that names a pool with {@code CP_CAP_POOL_ID} is bound to that pool only. Just the free nodes of
     * that pool may take it - never a plain node and never a node of another pool. Any other run is free to
     * take any matching pool or plain node. {@link AutoscaleManager} reads this as well, so such a run never
     * falls through to a generic on-demand or spot node when this method cannot place it.
     */
    public Optional<Long> requiredPoolId(final PipelineRun run) {
        return ListUtils.emptyIfNull(run.getPipelineRunParameters()).stream()
                .filter(parameter -> Objects.equals(parameter.getName(), CP_CAP_POOL_ID))
                .map(PipelineRunParameter::getValue)
                .filter(StringUtils::isNotBlank)
                .findAny()
                .flatMap(this::parsePoolId);
    }

    private Optional<Long> parsePoolId(final String value) {
        try {
            return Optional.of(Long.valueOf(value));
        } catch (NumberFormatException e) {
            log.warn("CP_CAP_POOL_ID parameter '{}' is not a valid pool id - treating the run as unbound.", value);
            return Optional.empty();
        }
    }

    private boolean attemptReassign(final Map<String, RunningInstance> freeInstances,
                                    final BiFunction<RunningInstance, InstanceRequest, Boolean> matcher,
                                    final InstanceRequest requiredInstance,
                                    final String runId,
                                    final Long longId,
                                    final Optional<PipelineRun> pipelineRun,
                                    final Optional<Long> requiredPoolId,
                                    final Set<String> scheduledRuns,
                                    final Set<String> reassignedNodes,
                                    final boolean failFast) {
        return freeInstances.entrySet()
                .stream()
                .anyMatch(entry -> {
                    final String previousId = entry.getKey();
                    final RunningInstance previousInstance = entry.getValue();
                    final Optional<NodePool> ownPool = failFast
                            ? requiredPoolOf(previousInstance, requiredPoolId) : Optional.empty();
                    if (!matcher.apply(previousInstance, requiredInstance)) {
                        ownPool.ifPresent(pool -> failIncompatible(
                                MessageConstants.ERROR_NODE_POOL_RESERVED_NODE_MISMATCH, pool.getName()));
                        return false;
                    }
                    ownPool.ifPresent(this::assertReservationNotExpired);
                    final boolean passedPoolFilter = Optional.ofNullable(previousInstance.getPool())
                            .map(pool -> matchesRequiredPool(pool.getId(), requiredPoolId)
                                    && isReusable(pool) && matchesPoolFilter(pool, pipelineRun, ownPool.isPresent()))
                            .orElse(!requiredPoolId.isPresent());
                    if (!passedPoolFilter) {
                        return false;
                    }
                    return reassignInstance(runId, longId, scheduledRuns, reassignedNodes,
                            previousId, previousInstance, pipelineRun);
                });
    }

    /**
     * Returns the required pool if this free node belongs to it. Only such a node may fail the run: it is the one
     * node the run must fit. A node of another pool that does not fit says nothing about this run, because the
     * run was never going to use it.
     */
    private Optional<NodePool> requiredPoolOf(final RunningInstance instance, final Optional<Long> requiredPoolId) {
        return Optional.ofNullable(instance.getPool())
                .filter(pool -> requiredPoolId.map(required -> required.equals(pool.getId())).orElse(false));
    }

    /**
     * A capacity reservation pool whose window has passed is a dead end, just like a node that does not match.
     * No later tick changes that.
     */
    private void assertReservationNotExpired(final NodePool pool) {
        if (pool.isCapacityReservation() && pool.getEndDate() != null
                && DateUtils.nowUTC().isAfter(pool.getEndDate())) {
            failIncompatible(MessageConstants.ERROR_NODE_POOL_RESERVATION_EXPIRED, pool.getName());
        }
    }

    private void failIncompatible(final String messageKey, final Object... args) {
        throw new PoolReservationIncompatibleException(messageHelper.getMessage(messageKey, args));
    }

    private boolean matchesRequiredPool(final Long poolId, final Optional<Long> requiredPoolId) {
        // the required id is the receiver: a pool without an id is not the pool a run named
        return requiredPoolId.map(required -> required.equals(poolId)).orElse(true);
    }

    /**
     * Only a capacity reservation pool's own window decides whether its free node may take a new run: past it the
     * reservation is ending or gone, and the job would outlive the reserved capacity. An ordinary pool's free node is
     * reused regardless of its schedule - it is already running and paid for until it is scaled down.
     */
    private boolean isReusable(final NodePool pool) {
        return !pool.isCapacityReservation() || pool.isActive(DateUtils.nowUTC());
    }

    /**
     * Whether a pool's free node may take this run. A filter about whose runs the pool serves does not hold against
     * the pool's own owner, who reuses its nodes whatever it says; a filter about the run itself - its image, its
     * pipeline, its parameters - holds for the owner as for anyone else.
     *
     * <p>{@code failFast} is true only for a node of the required pool. For such a node a rejection is reported
     * as a final answer, not just returned as {@code false}. The run's owner, image, pipeline and parameters
     * stay the same on every later tick, so the answer cannot change.
     */
    private boolean matchesPoolFilter(final NodePool pool, final Optional<PipelineRun> pipelineRun,
                                      final boolean failFast) {
        final PoolFilter filter = pool.getFilter();
        if (filter == null || filter.isEmpty()) {
            return true;
        }
        return pipelineRun
                .map(run -> matchRun(pool, filter, run, failFast))
                .orElse(false);
    }

    private boolean ownsPool(final NodePool pool, final PipelineRun run) {
        return StringUtils.isNotBlank(pool.getOwner())
                && StringUtils.equalsIgnoreCase(pool.getOwner(), run.getOwner());
    }

    /**
     * Combines the filters with AND or OR. Only this method knows the overall result, so only this method may
     * report a rejection as a final answer. Under OR one failing filter means nothing, because another filter
     * may still pass. {@link #matchRunToFilter} never decides this on its own.
     */
    private boolean matchRun(final NodePool pool, final PoolFilter filter, final PipelineRun run,
                             final boolean failFast) {
        final List<PoolInstanceFilter> filters = filter.filters();
        switch (filter.operator()) {
            case AND: {
                final Optional<PoolInstanceFilter> firstFailing = filters.stream()
                        .filter(f -> !matchRunToFilter(pool, f, run))
                        .findFirst();
                if (firstFailing.isPresent() && failFast) {
                    failIncompatible(MessageConstants.ERROR_NODE_POOL_FILTER_MISMATCH, pool.getName(),
                            firstFailing.get().getType().name());
                }
                return !firstFailing.isPresent();
            }
            case OR: {
                final boolean anyMatches = filters.stream()
                        .anyMatch(f -> matchRunToFilter(pool, f, run));
                if (!anyMatches && failFast) {
                    failIncompatible(MessageConstants.ERROR_NODE_POOL_FILTER_MISMATCH, pool.getName(),
                            filters.get(0).getType().name());
                }
                return anyMatches;
            }
            default:
                throw new IllegalArgumentException("Unsupported filter operator: " + filter.operator());
        }
    }

    private boolean matchRunToFilter(final NodePool pool, final PoolInstanceFilter filter,
                                     final PipelineRun run) {
        final PoolInstanceFilterType type = filter.getType();
        if ((PoolInstanceFilterType.RUN_OWNER == type || PoolInstanceFilterType.RUN_OWNER_GROUP == type)
                && ownsPool(pool, run)) {
            log.debug("Run {} belongs to the owner of the pool, so pool filter {} does not apply to it.",
                    run.getId(), filter);
            return true;
        }
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
                                     final RunningInstance previousInstance,
                                     final Optional<PipelineRun> pipelineRun) {
        log.debug("Reassigning node ID {} to run {}.", previousNodeId, newNodeId);
        final Map<String, String> tags = findInstanceTags(runId, pipelineRun);
        final boolean successfullyReassigned = runReassign(previousNodeId, runId, tags);
        if (!successfullyReassigned) {
            return false;
        }
        scheduledRuns.add(newNodeId);
        final RunInstance instance = previousInstance.getInstance();
        final RunInstance reassignedInstance = StringUtils.isBlank(instance.getNodeId()) ?
                cloudFacade.describeInstance(runId, instance) : instance;
        reassignedInstance.setPoolId(instance.getPoolId());
        pipelineRunManager.updateRunInstanceAndPrices(runId, reassignedInstance);
        pipelineRunManager.updateRunInstanceStartDate(runId, DateUtils.nowUTC());
        runStatusManager.updatePriceForCurrentActiveRunStatus(runId);
        final List<InstanceDisk> disks = cloudFacade.loadDisks(reassignedInstance.getCloudRegionId(),
                runId);
        if (CollectionUtils.isNotEmpty(disks)) {
            autoscalerService.adjustRunPrices(runId, disks);
        }
        reassignedNodes.add(previousNodeId);
        return true;
    }

    private boolean runReassign(final String previousNodeId, final Long runId,
                                final Map<String, String> tags) {
        if (previousNodeId.startsWith(AutoscaleContants.NODE_POOL_PREFIX) ||
                previousNodeId.startsWith(AutoscaleContants.NODE_LOCAL_PREFIX)) {
            return cloudFacade.reassignPoolNode(previousNodeId, runId, tags);
        }
        return cloudFacade.reassignNode(Long.valueOf(previousNodeId), runId, tags);
    }

    /**
     * Checks whether a run may get a free node of an earlier run.
     *
     * @param run the run that waits for a node
     * @return {@code false} if {@code cluster.disable.reassign} is {@code true}, if it is a Windows run,
     * if it has {@code CP_CREATE_NEW_NODE} or {@code CP_CAP_DEDICATED_INSTANCE} set to {@code true},
     * or if it matches an image config with an {@code IamInstanceProfile} in {@code cluster.networks.config}
     */
    public boolean isReassignAllowed(final PipelineRun run) {
        return !isReassignDisabled()
                && !KubernetesConstants.WINDOWS.equals(run.getPlatform())
                && !hasTrueParameter(run, CP_CREATE_NEW_NODE)
                && !hasTrueParameter(run, CP_CAP_DEDICATED_INSTANCE)
                && !iamProfileVerifier.isImageRestricted(run);
    }

    private boolean isReassignDisabled() {
        return BooleanUtils.isTrue(preferenceManager.getPreference(SystemPreferences.CLUSTER_DISABLE_REASSIGN));
    }

    private boolean hasTrueParameter(final PipelineRun run, final String name) {
        return ListUtils.emptyIfNull(run.getPipelineRunParameters()).stream()
                .anyMatch(parameter -> Objects.equals(parameter.getName(), name)
                        && isValueTrue(parameter.getValue()));
    }

    private boolean isValueTrue(final String value) {
        if (StringUtils.isBlank(value)) {
            return false;
        }
        return BooleanUtils.toBoolean(value);
    }

    private Map<String, String> findInstanceTags(final Long runId, final Optional<PipelineRun> optionalRun) {
        return optionalRun.map(Optional::of)
                .orElseGet(() -> pipelineRunManager.findRun(runId))
                .map(metadataManager::prepareCloudResourceTags)
                .orElseGet(Collections::emptyMap);
    }
}
