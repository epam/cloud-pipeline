/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.credits;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dto.credits.PlatformUsageCreditsMode;
import com.epam.pipeline.dto.credits.PlatformUsageCreditsUserBalance;
import com.epam.pipeline.entity.cluster.InstanceOffer;
import com.epam.pipeline.entity.cluster.pool.InstanceRequest;
import com.epam.pipeline.entity.configuration.PipeConfValueVO;
import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import com.epam.pipeline.entity.configuration.RunConfigurationEntry;
import com.epam.pipeline.entity.configuration.RunConfigurationUtils;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.pipeline.run.PipelineStart;
import com.epam.pipeline.entity.pipeline.run.parameter.PipelineRunParameter;
import com.epam.pipeline.entity.user.DefaultRoles;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.exception.credits.InsufficientUsageCreditsException;
import com.epam.pipeline.manager.cluster.InstanceOfferManager;
import com.epam.pipeline.manager.pipeline.PipelineRunCRUDService;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.user.UserManager;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Credits enforcement service for all run-launch paths.
 *
 * <p>This service is the single authority for platform usage credits at launch time.
 * It owns active-run offer loading (via {@link PipelineRunCRUDService} and
 * {@link InstanceOfferManager}) so callers only need to describe the <em>new</em>
 * work being launched — not the owner's current state.
 *
 * <p><strong>Capacity block runs</strong> are detected by the presence of
 * {@link #CP_CAP_REQUESTS_CPU} or {@link #CP_CAP_REQUESTS_GPU} in the run parameters.
 * For these runs a synthetic offer is built from the requested values instead of the
 * full catalogue offer, so credits are charged only for the requested slice of the node.
 *
 * <p>The credit cost of a single node is:
 * <pre>
 *   cost = vCPU × weight(CPU) + GPU × weight(GPU)
 * </pre>
 * Weights come from the {@code usage.credits.resource.weights} system preference
 * (defaults: CPU = 1, GPU = 100).
 *
 * <p>All checks short-circuit to "allowed" when:
 * <ul>
 *   <li>the {@code platform.usage.credits.mode} preference is not {@link PlatformUsageCreditsMode#ON}; or</li>
 *   <li>the requesting user holds the {@code ADMIN} or {@code ROLE_RUN_ADMIN} role.</li>
 * </ul>
 *
 * <p>Methods whose names start with {@code check*} throw
 * {@link InsufficientUsageCreditsException} directly on failure.
 * {@link #hasCreditsToLaunchRun} returns a boolean and is intended for the autoscale path
 * where the caller controls the rejection response.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformUsageCreditsLaunchService {

    /** Run parameter carrying the requested CPU count for a capacity block run. */
    public static final String CP_CAP_REQUESTS_CPU = "CP_CAP_REQUESTS_CPU";

    /** Run parameter carrying the requested GPU count for a capacity block run. */
    public static final String CP_CAP_REQUESTS_GPU = "CP_CAP_REQUESTS_GPU";

    /**
     * Immutable result of a credits check.
     *
     * <p>{@code ok = true} means the launch is permitted.  The numeric fields are
     * populated even when {@code ok = true} so callers can surface them in logs or UI.
     */
    @Value
    public static class CheckResult {
        boolean ok;
        int required;
        int balance;
        int allocated;

        /** Convenience factory for the "always allowed" sentinel (mode OFF / admin). */
        public static CheckResult allowed() {
            return new CheckResult(true, 0, 0, 0);
        }
    }

    /**
     * One homogeneous node group inside a (potentially heterogeneous) cluster launch:
     * a specific instance offer and how many replicas of it are requested.
     */
    @Value
    public static class ReplicaGroup {
        InstanceOffer offer;
        int replicas;
    }

    private static final String CPU_WEIGHT_KEY = "CPU";
    private static final String GPU_WEIGHT_KEY = "GPU";
    private static final int CPU_DEFAULT_WEIGHT = 1;
    private static final int GPU_DEFAULT_WEIGHT = 100;

    private final PreferenceManager preferenceManager;
    private final PlatformUsageCreditsUserBalanceCRUDService userBalanceService;
    private final UserManager userManager;
    private final PipelineRunCRUDService pipelineRunCRUDService;
    private final InstanceOfferManager instanceOfferManager;
    private final MessageHelper messageHelper;

    /**
     * Enforces credits for a single-node run launch.
     *
     * <p>Capacity block runs are detected via {@code CP_CAP_REQUESTS_CPU} /
     * {@code CP_CAP_REQUESTS_GPU} in {@code parameters}; a synthetic offer is used for
     * them so only the requested slice is charged.
     *
     * @param owner      username of the run owner
     * @param instance   resolved catalogue offer for the selected instance type;
     *                   an empty {@code Optional} is a no-op (check is skipped)
     * @param parameters the run's full parameter map; used for capacity-block detection
     * @throws InsufficientUsageCreditsException if the owner cannot afford this launch
     */
    public void checkCreditsForRunLaunch(final String owner,
                                         final Optional<InstanceOffer> instance,
                                         final Map<String, PipeConfValueVO> parameters) {
        instance.ifPresent(offer -> throwIfInsufficient(
                checkGroups(owner, singleRunGroup(resolveEffectiveOffer(offer, parameters)),
                        loadActiveOffers(owner))));
    }

    /**
     * Enforces credits for a run restart (spot-retry or region-shift).
     *
     * <p>Resolves the effective instance offer from the run's stored instance and
     * parameters — capacity-block runs contribute only their requested slice, plain
     * runs use the catalogue offer. Delegates to the standard credits check.
     *
     * <p>Uses {@code run.getOwner()} as the owner because restart callers run in
     * scheduled threads with no security principal set.
     *
     * <p>Short-circuits to a no-op when the offer cannot be resolved or mode is not
     * {@link PlatformUsageCreditsMode#ON}.
     *
     * @param run the run being restarted
     * @throws InsufficientUsageCreditsException if the owner cannot afford the restart
     */
    public void checkCreditsForRestartRun(final PipelineRun run) {
        offerForActiveRun(run).ifPresent(offer ->
                throwIfInsufficient(checkGroups(run.getOwner(),
                        singleRunGroup(offer),
                        loadActiveOffers(run.getOwner(), run.getId()))));
    }

    /**
     * Returns {@code true} when {@code owner} has enough credits to provision the node
     * described by {@code requiredInstance}, optionally excluding one run from the
     * allocation count.
     *
     * <p>Returns {@code true} (allowed) when the offer cannot be resolved in the
     * catalogue — an unknown instance type should not block autoscaling.
     *
     * <p>Primary caller: {@code AutoscaleManager}, which passes the run's own ID as
     * {@code excludeRunId} to avoid double-counting it.
     *
     * @param owner            username of the run owner
     * @param requiredInstance instance type and cloud region of the node to be created
     * @param excludeRunId     run ID to exclude from the allocation sum, or {@code null}
     * @return {@code true} if the node may be provisioned
     */
    public boolean hasCreditsToLaunchRun(final String owner,
                                         final InstanceRequest requiredInstance,
                                         final Long excludeRunId) {
        final String instanceType = requiredInstance.getInstance().getNodeType();
        final Long regionId = requiredInstance.getInstance().getCloudRegionId();
        return instanceOfferManager.findOffer(instanceType, regionId)
                .map(offer -> checkGroups(owner, singleRunGroup(offer),
                        loadActiveOffers(owner, excludeRunId)))
                .orElseGet(CheckResult::allowed)
                .isOk();
    }

    /**
     * Enforces credits for a homogeneous cluster (master + {@code nodeCount} workers,
     * all sharing the same instance type).
     *
     * <p>The check is skipped when {@code configuration.nodeCount} is null or ≤ 0.
     *
     * @param owner         username of the run owner
     * @param configuration pipeline configuration carrying the instance type, region,
     *                      and worker node count
     * @throws InsufficientUsageCreditsException if the owner cannot afford this cluster
     */
    public void checkHomogeneousClusterCredits(final String owner,
                                               final PipelineConfiguration configuration) {
        if (configuration.getNodeCount() == null || configuration.getNodeCount() <= 0) {
            return;
        }
        instanceOfferManager.findOffer(configuration.getInstanceType(), configuration.getCloudRegionId())
                .ifPresent(offer -> throwIfInsufficient(
                        checkGroups(owner,
                                homogeneousGroup(offer, configuration.getNodeCount()),
                                loadActiveOffers(owner))));
    }

    /**
     * Enforces credits for a heterogeneous cluster described by a master configuration
     * and a list of child worker entries (each potentially using a different instance type).
     *
     * <p>The owner is resolved from the current security context.
     * Groups whose offer cannot be resolved are silently skipped; if no groups resolve
     * the check is treated as a no-op.
     *
     * @param mainConfiguration   master node configuration
     * @param masterNodeCount     number of master-side worker replicas (excluding the master itself)
     * @param childConfigurations resolved configurations for each child entry
     * @param childEntries        child entries parallel to {@code childConfigurations}
     * @throws InsufficientUsageCreditsException if the owner cannot afford this cluster
     */
    public void checkHeterogeneousClusterCredits(final PipelineConfiguration mainConfiguration,
                                                 final int masterNodeCount,
                                                 final List<PipelineConfiguration> childConfigurations,
                                                 final List<RunConfigurationEntry> childEntries) {
        final String owner = userManager.getCurrentUser().getUserName();
        final List<ReplicaGroup> groups = heterogeneousGroups(mainConfiguration, masterNodeCount,
                childConfigurations, childEntries);
        throwIfInsufficient(groups.isEmpty()
                ? CheckResult.allowed()
                : checkGroups(owner, groups, loadActiveOffers(owner)));
    }

    /**
     * Returns the total credits currently allocated by the given owner's active
     * (RUNNING or PAUSED) runs.
     *
     * <p>Capacity block runs contribute only their requested slice; plain runs
     * contribute the full cost of their instance offer.
     *
     * @param owner username whose active runs should be summed
     * @return total allocated credits; 0 when there are no active runs
     */
    public int getAllocatedCredits(final String owner) {
        return computeUsedCredits(loadActiveOffers(owner), weights());
    }

    private CheckResult checkGroups(final String owner,
                                    final List<ReplicaGroup> groups,
                                    final List<InstanceOffer> activeOffers) {
        if (!PlatformUsageCreditsMode.ON.equals(getMode())) {
            return CheckResult.allowed();
        }
        final PipelineUser user = userManager.loadByNameOrId(owner);
        if (user.isAdmin() || user.hasRole(DefaultRoles.ROLE_RUN_ADMIN.getRole())) {
            return CheckResult.allowed();
        }
        final Map<String, Integer> weights = weights();
        final int required = groups.stream()
                .mapToInt(g -> g.getReplicas() * creditsForOffer(g.getOffer(), weights))
                .sum();
        final int balance = userBalance(user);
        final int allocated = computeUsedCredits(activeOffers, weights);
        log.debug("Credits check for user {}: balance={}, allocated={}, required={} (groups={})",
                user.getUserName(), balance, allocated, required, groups.size());
        return new CheckResult((balance - allocated) >= required, required, balance, allocated);
    }

    private List<InstanceOffer> loadActiveOffers(final String owner) {
        return loadActiveOffers(owner, null);
    }

    private List<InstanceOffer> loadActiveOffers(final String owner, final Long excludeRunId) {
        final List<TaskStatus> activeStatuses = Arrays.asList(TaskStatus.RUNNING, TaskStatus.PAUSED);
        return pipelineRunCRUDService.loadRunsByStatusesAndOwner(activeStatuses, owner).stream()
                .filter(r -> excludeRunId == null || !excludeRunId.equals(r.getId()))
                .map(this::offerForActiveRun)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    private Optional<InstanceOffer> offerForActiveRun(final PipelineRun run) {
        final Map<String, String> params = toStringParamMap(run.getPipelineRunParameters());
        if (params.containsKey(CP_CAP_REQUESTS_CPU) || params.containsKey(CP_CAP_REQUESTS_GPU)) {
            final int cpu = parseIntFromStringMap(params, CP_CAP_REQUESTS_CPU);
            final int gpu = parseIntFromStringMap(params, CP_CAP_REQUESTS_GPU);
            return (cpu == 0 && gpu == 0) ? Optional.empty() : Optional.of(syntheticOffer(cpu, gpu));
        }
        if (run.getInstance() == null || run.getInstance().getNodeType() == null) {
            return Optional.empty();
        }
        return instanceOfferManager.findOffer(
                run.getInstance().getNodeType(), run.getInstance().getCloudRegionId());
    }

    private InstanceOffer resolveEffectiveOffer(final InstanceOffer catalogOffer,
                                                final Map<String, PipeConfValueVO> params) {
        if (!params.containsKey(CP_CAP_REQUESTS_CPU) && !params.containsKey(CP_CAP_REQUESTS_GPU)) {
            return catalogOffer;
        }
        return syntheticOffer(
                parseIntFromConfMap(params, CP_CAP_REQUESTS_CPU),
                parseIntFromConfMap(params, CP_CAP_REQUESTS_GPU));
    }

    private List<ReplicaGroup> heterogeneousGroups(final PipelineConfiguration mainConfiguration,
                                                   final int masterNodeCount,
                                                   final List<PipelineConfiguration> childConfigurations,
                                                   final List<RunConfigurationEntry> childEntries) {
        final List<ReplicaGroup> groups = new ArrayList<>();
        instanceOfferManager.findOffer(mainConfiguration.getInstanceType(),
                        mainConfiguration.getCloudRegionId())
                .ifPresent(o -> groups.add(new ReplicaGroup(o, masterNodeCount + 1)));
        for (int i = 0; i < childConfigurations.size(); i++) {
            final PipelineConfiguration childConfig = childConfigurations.get(i);
            final PipelineStart childStart = childEntries.get(i).toPipelineStart();
            instanceOfferManager.findOffer(childStart.getInstanceType(), childConfig.getCloudRegionId())
                    .ifPresent(o -> groups.add(new ReplicaGroup(o,
                            RunConfigurationUtils.getNodeCount(childConfig.getNodeCount(), 1))));
        }
        return groups;
    }

    private int computeUsedCredits(final List<InstanceOffer> offers, final Map<String, Integer> weights) {
        return offers.stream().mapToInt(o -> creditsForOffer(o, weights)).sum();
    }

    private int creditsForOffer(final InstanceOffer offer, final Map<String, Integer> weights) {
        return offer.getVCPU() * weights.getOrDefault(CPU_WEIGHT_KEY, CPU_DEFAULT_WEIGHT)
                + offer.getGpu() * weights.getOrDefault(GPU_WEIGHT_KEY, GPU_DEFAULT_WEIGHT);
    }

    private int userBalance(final PipelineUser user) {
        return userBalanceService.findByUserId(user.getId())
                .map(PlatformUsageCreditsUserBalance::getCurrentValue)
                .orElseGet(() -> preferenceManager.getPreference(SystemPreferences.USAGE_CREDITS_DEFAULT));
    }

    private Map<String, Integer> weights() {
        return MapUtils.emptyIfNull(
                preferenceManager.getPreference(SystemPreferences.USAGE_CREDITS_RESOURCE_WEIGHTS));
    }

    private PlatformUsageCreditsMode getMode() {
        return preferenceManager.getPreference(SystemPreferences.USAGE_CREDITS_MODE);
    }

    private static List<ReplicaGroup> singleRunGroup(final InstanceOffer offer) {
        return Collections.singletonList(new ReplicaGroup(offer, 1));
    }

    private static List<ReplicaGroup> homogeneousGroup(final InstanceOffer offer, final int nodeCount) {
        return Collections.singletonList(new ReplicaGroup(offer, nodeCount + 1));
    }

    private static InstanceOffer syntheticOffer(final int vcpu, final int gpu) {
        final InstanceOffer o = new InstanceOffer();
        o.setVCPU(vcpu);
        o.setGpu(gpu);
        return o;
    }

    private static Map<String, String> toStringParamMap(final List<PipelineRunParameter> parameters) {
        return ListUtils.emptyIfNull(parameters).stream()
                .filter(p -> p.getName() != null && p.getValue() != null)
                .collect(Collectors.toMap(
                        PipelineRunParameter::getName, PipelineRunParameter::getValue, (a, b) -> a));
    }

    private static int parseIntFromStringMap(final Map<String, String> params, final String key) {
        return Optional.ofNullable(params.get(key))
                .filter(NumberUtils::isNumber)
                .map(Integer::parseInt)
                .orElse(0);
    }

    private static int parseIntFromConfMap(final Map<String, PipeConfValueVO> params, final String key) {
        return Optional.ofNullable(params.get(key))
                .map(PipeConfValueVO::getValue)
                .filter(NumberUtils::isNumber)
                .map(Integer::parseInt)
                .orElse(0);
    }

    private void throwIfInsufficient(final CheckResult result) {
        if (!result.isOk()) {
            throw new InsufficientUsageCreditsException(
                    messageHelper.getMessage(MessageConstants.ERROR_PLATFORM_USAGE_CREDITS_INSUFFICIENT,
                            result.getRequired(), result.getBalance() - result.getAllocated(),
                            result.getBalance(), result.getAllocated()));
        }
    }
}
