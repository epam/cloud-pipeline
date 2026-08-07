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
import com.epam.pipeline.entity.configuration.PipeConfValueVO;
import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import com.epam.pipeline.entity.configuration.RunConfigurationUtils;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.user.DefaultRoles;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.exception.credits.InsufficientUsageCreditsException;
import com.epam.pipeline.manager.cluster.InstanceOfferManager;
import com.epam.pipeline.manager.pipeline.PipelineRunCRUDService;
import com.epam.pipeline.manager.contextual.ContextualPreferenceManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.user.UserManager;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Objects;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    private final ContextualPreferenceManager contextualPreferenceManager;
    private final PlatformUsageCreditsUserBalanceCRUDService userBalanceService;
    private final UserManager userManager;
    private final PipelineRunCRUDService pipelineRunCRUDService;
    private final InstanceOfferManager instanceOfferManager;
    private final MessageHelper messageHelper;

    /**
     * Enforces credits for a single-node run launch, also checking every fallback primaryInstance type.
     *
     * <p>All types — primary and each fallback — must individually pass the credits check.
     * The active-offer snapshot is loaded once and reused for every type check.
     * Capacity block runs are detected via {@code CP_CAP_REQUESTS_CPU} /
     * {@code CP_CAP_REQUESTS_GPU} in {@code parameters}; a synthetic offer is used for
     * them so only the requested slice is charged (fallback types are ignored for capacity
     * block runs).
     *
     * @param primaryInstance       resolved catalogue offer for the primary instance type;
     *                              an empty {@code Optional} skips the primary check
     * @param fallbackInstanceTypes fallback primaryInstance type names to check in addition to primary
     * @param regionId              cloud region used to resolve fallback offers
     * @param parameters            the run's full parameter map; used for capacity-block detection
     * @throws InsufficientUsageCreditsException if the owner cannot afford any of the types
     */
    public void checkCreditsForRunLaunch(final Optional<InstanceOffer> primaryInstance,
                                         final List<String> fallbackInstanceTypes,
                                         final Long regionId,
                                         final Map<String, PipeConfValueVO> parameters) {
        final PipelineUser user = userManager.getCurrentUser();

        if (checksNotRequired(user)) {
            return;
        }

        final Map<String, Integer> weights = weights();
        findAppropriateOffer(regionId, resolvePrimaryOffer(primaryInstance, parameters), fallbackInstanceTypes, weights)
                .ifPresent(offer -> throwIfInsufficient(
                        checkGroups(user, singleRunGroup(offer),
                                loadActiveOffers(user.getUserName(), weights), weights)));
    }

    /**
     * Enforces credits for resuming a previously paused run.
     *
     * <p>Checks the primary instance type and every fallback type stored on the run;
     * all types must individually pass.
     * PAUSED runs are excluded from the active-offer sum (see {@link #loadActiveOffers}),
     * so there is no need to exclude the run's own ID here.
     *
     * <p>Short-circuits to a no-op when mode is not {@link PlatformUsageCreditsMode#ON}
     * or the user is an admin.
     *
     * @param run the run being resumed
     * @throws InsufficientUsageCreditsException if the owner cannot afford the resume
     */
    public void checkCreditsForResumeRun(final PipelineRun run) {
        final String owner = run.getOwner();
        final PipelineUser user = userManager.loadByNameOrId(owner);

        if (checksNotRequired(user)) {
            return;
        }

        final Map<String, Integer> weights = weights();
        resolveOfferForRun(run, weights)
                .ifPresent(offer -> throwIfInsufficient(
                        checkGroups(user, singleRunGroup(offer), loadActiveOffers(owner, weights), weights)));
    }

    /**
     * Enforces credits for a homogeneous cluster (master + {@code nodeCount} workers,
     * all sharing the same instance type).
     *
     * <p>The check is skipped when {@code configuration.nodeCount} is null or ≤ 0.
     *
     * @param configuration pipeline configuration carrying the instance type, region,
     *                      and worker node count
     * @throws InsufficientUsageCreditsException if the owner cannot afford this cluster
     */
    public void checkCreditsForCluster(final PipelineConfiguration configuration) {
        if (Objects.isNull(configuration.getNodeCount()) || configuration.getNodeCount() <= 0) {
            return;
        }

        final PipelineUser user = userManager.getCurrentUser();

        if (checksNotRequired(user)) {
            return;
        }

        final Map<String, Integer> weights = weights();
        findOfferByConfiguration(configuration, weights)
                .ifPresent(offer -> throwIfInsufficient(
                        checkGroups(user, homogeneousGroup(offer, configuration.getNodeCount()),
                                loadActiveOffers(user.getUserName(), weights), weights)));
    }

    /**
     * Enforces credits for a configuration described by a master configuration
     * and a list of child worker entries (each potentially using a different instance type).
     *
     * <p>The owner is resolved from the current security context.
     * Groups whose offer cannot be resolved are silently skipped; if no groups resolve
     * the check is treated as a no-op.
     *
     * <p>Capacity block runs are detected via {@code CP_CAP_REQUESTS_CPU} /
     * {@code CP_CAP_REQUESTS_GPU} in each node's parameter map; a synthetic offer is used so
     * only the requested slice is charged.
     *
     * <p>Fallback instance types are read from each node's {@link PipelineConfiguration};
     * the most expensive offer across primary and all fallbacks is used for the credit check,
     * so the owner must be able to afford the worst-case type.
     *
     * @param mainConfiguration   master node configuration
     * @param masterNodeCount     number of master-side worker replicas (excluding the master itself)
     * @param childConfigurations resolved configurations for each child entry
     * @throws InsufficientUsageCreditsException if the owner cannot afford this cluster
     */
    public void checkCreditsForConfiguration(final PipelineConfiguration mainConfiguration,
                                             final int masterNodeCount,
                                             final List<PipelineConfiguration> childConfigurations) {
        final PipelineUser user = userManager.getCurrentUser();

        if (checksNotRequired(user)) {
            return;
        }

        final Map<String, Integer> weights = weights();
        final List<ReplicaGroup> groups = heterogeneousGroups(mainConfiguration, masterNodeCount,
                childConfigurations, weights);

        if (groups.isEmpty()) {
            return;
        }

        throwIfInsufficient(checkGroups(user, groups, loadActiveOffers(user.getUserName(), weights), weights));
    }

    /**
     * Returns the total credits currently allocated by the given owner's RUNNING runs.
     *
     * <p>PAUSED runs are intentionally excluded: {@link #checkCreditsForResumeRun} relies
     * on this so that the run being resumed is not double-counted against its own balance.
     *
     * @param owner username whose active runs should be summed
     * @return total allocated credits; 0 when there are no active runs
     */
    public int getAllocatedCredits(final String owner) {
        final Map<String, Integer> weights = weights();
        return computeUsedCredits(loadActiveOffers(owner, weights), weights);
    }

    private CheckResult checkGroups(final PipelineUser user,
                                    final List<ReplicaGroup> groups,
                                    final List<InstanceOffer> activeOffers,
                                    final Map<String, Integer> weights) {
        final int required = groups.stream()
                .mapToInt(g -> g.getReplicas() * creditsForOffer(g.getOffer(), weights))
                .sum();
        final int balance = userBalance(user);
        final int allocated = computeUsedCredits(activeOffers, weights);
        log.debug("Credits check for user {}: balance={}, allocated={}, required={} (groups={})",
                user.getUserName(), balance, allocated, required, groups.size());
        return new CheckResult((balance - allocated) >= required, required, balance, allocated);
    }

    private List<InstanceOffer> loadActiveOffers(final String owner, final Map<String, Integer> weights) {
        return pipelineRunCRUDService.loadRunsByStatusesAndOwner(
                        Collections.singletonList(TaskStatus.RUNNING), owner).stream()
                .map(run -> resolveOfferForRun(run, weights))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    private Optional<InstanceOffer> resolveOfferForRun(final PipelineRun run, final Map<String, Integer> weights) {
        final RunInstance instance = run.getInstance();
        final Optional<InstanceOffer> primaryOffer = resolvePrimaryOffer(instance,
                MapUtils.emptyIfNull(run.convertParamsToMap()));

        if (Objects.isNull(instance) || StringUtils.isBlank(instance.getNodeType()) || !primaryOffer.isPresent()) {
            return Optional.empty();
        }

        // node already provisioned — use the actual instance type
        if (StringUtils.isNotBlank(instance.getNodeId())) {
            return primaryOffer;
        }

        // node not yet provisioned — use the most expensive offer across primary + fallbacks
        // so that in-flight runs conservatively hold credits for the worst-case type
        return findAppropriateOffer(
                instance.getCloudRegionId(), primaryOffer, instance.getFallbackInstanceTypes(), weights);
    }

    // for configuration
    private Optional<InstanceOffer> resolvePrimaryOffer(final PipelineConfiguration configuration) {
        if (Objects.isNull(configuration) || StringUtils.isBlank(configuration.getInstanceType())) {
            return Optional.empty();
        }

        final Map<String, PipeConfValueVO> params = MapUtils.emptyIfNull(configuration.getParameters());
        if (!params.containsKey(CP_CAP_REQUESTS_CPU) && !params.containsKey(CP_CAP_REQUESTS_GPU)) {
            // not a capacity block
            return instanceOfferManager.findOffer(configuration.getInstanceType(), configuration.getCloudRegionId());
        }

        // create artificial offer for capacity block
        return syntheticOffer(params);
    }

    // for instance
    private Optional<InstanceOffer> resolvePrimaryOffer(final RunInstance instance,
                                                        final Map<String, PipeConfValueVO> params) {
        if (Objects.isNull(instance) || StringUtils.isBlank(instance.getNodeType())) {
            return Optional.empty();
        }

        if (!params.containsKey(CP_CAP_REQUESTS_CPU) && !params.containsKey(CP_CAP_REQUESTS_GPU)) {
            // not a capacity block
            return instanceOfferManager.findOffer(instance.getNodeType(), instance.getCloudRegionId());
        }

        // create artificial offer for capacity block
        return syntheticOffer(params);
    }

    // for offer directly
    private Optional<InstanceOffer> resolvePrimaryOffer(final Optional<InstanceOffer> catalogOffer,
                                                        final Map<String, PipeConfValueVO> params) {
        if (!catalogOffer.isPresent()) {
            return catalogOffer;
        }

        if (!params.containsKey(CP_CAP_REQUESTS_CPU) && !params.containsKey(CP_CAP_REQUESTS_GPU)) {
            // not a capacity block
            return catalogOffer;
        }

        // create artificial offer for capacity block
        return syntheticOffer(params);
    }

    private List<ReplicaGroup> heterogeneousGroups(final PipelineConfiguration mainConfiguration,
                                                   final int masterNodeCount,
                                                   final List<PipelineConfiguration> childConfigurations,
                                                   final Map<String, Integer> weights) {
        final List<ReplicaGroup> groups = new ArrayList<>();
        findOfferByConfiguration(mainConfiguration, weights)
                .ifPresent(o -> groups.add(new ReplicaGroup(o, masterNodeCount + 1)));
        for (final PipelineConfiguration childConfig : childConfigurations) {
            findOfferByConfiguration(childConfig, weights)
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
                .orElseGet(() -> Integer.parseInt(contextualPreferenceManager.search(
                        Collections.singletonList(SystemPreferences.USAGE_CREDITS_DEFAULT.getKey()),
                        user).getValue()));
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

    private Optional<InstanceOffer> syntheticOffer(final Map<String, PipeConfValueVO> params) {
        final int vcpu = parseIntFromConfMap(params, CP_CAP_REQUESTS_CPU);
        final int gpu = parseIntFromConfMap(params, CP_CAP_REQUESTS_GPU);
        final InstanceOffer offer = new InstanceOffer();
        offer.setVCPU(vcpu);
        offer.setGpu(gpu);
        return Optional.of(offer);
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

    private Optional<InstanceOffer> findAppropriateOffer(final Long regionId,
                                                         final Optional<InstanceOffer> primaryOffer,
                                                         final List<String> fallbackInstanceTypes,
                                                         final Map<String, Integer> weights) {
        return Stream.concat(
                Stream.of(primaryOffer),
                        ListUtils.emptyIfNull(fallbackInstanceTypes).stream()
                                .map(fallbackType -> instanceOfferManager.findOffer(fallbackType, regionId))
                ).collect(Collectors.toList()).stream()
                .filter(Optional::isPresent)
                .map(Optional::get)
                .max(Comparator.comparingInt(offer -> creditsForOffer(offer, weights)));
    }

    private Optional<InstanceOffer> findOfferByConfiguration(final PipelineConfiguration configuration,
                                                             final Map<String, Integer> weights) {
        return findAppropriateOffer(configuration.getCloudRegionId(),
                resolvePrimaryOffer(configuration),
                configuration.getFallbackInstanceTypes(), weights);
    }

    private boolean checksNotRequired(final PipelineUser user) {
        if (!PlatformUsageCreditsMode.ON.equals(getMode())) {
            log.debug("Platform usage credits checks disabled.");
            return true;
        }
        if (user.isAdmin() || user.hasRole(DefaultRoles.ROLE_RUN_ADMIN.getRole())) {
            log.debug("Platform usage credits checks disabled for admins.");
            return true;
        }
        return false;
    }
}
