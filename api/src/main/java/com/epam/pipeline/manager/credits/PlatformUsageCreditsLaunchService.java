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

import com.epam.pipeline.dto.credits.PlatformUsageCreditsUserBalance;
import com.epam.pipeline.entity.cluster.InstanceOffer;
import com.epam.pipeline.entity.user.DefaultRoles;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.user.UserManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Pure-computation service for platform usage credits enforcement at launch time.
 *
 * <p>This service is intentionally kept as a <em>leaf</em> in the dependency graph — it does not
 * depend on {@code PipelineRunManager} or {@code InstanceOfferManager}. Callers are responsible
 * for resolving {@link InstanceOffer} objects and loading the list of active-run offers before
 * invoking these methods; the service only performs the credit arithmetic and the admin/feature-flag
 * short-circuits.
 *
 * <p>The credit cost of a single node is:
 * <pre>
 *   cost = vCPU × weight(CPU) + GPU × weight(GPU)
 * </pre>
 * where weights come from the {@code usage.credits.resource.weights} system preference
 * (default: CPU=1, GPU=100).
 *
 * <p>All checks return {@link CreditsCheckResult#allowed()} when:
 * <ul>
 *   <li>the {@code monitoring.platform.usage.credits.enable} feature flag is {@code false}, or</li>
 *   <li>the requesting user has the {@code ADMIN} or {@code ROLE_RUN_ADMIN} role.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformUsageCreditsLaunchService {

    private static final String CPU_WEIGHT_KEY = "CPU";
    private static final String GPU_WEIGHT_KEY = "GPU";
    private static final int CPU_DEFAULT_WEIGHT = 1;
    private static final int GPU_DEFAULT_WEIGHT = 100;

    private final PreferenceManager preferenceManager;
    private final PlatformUsageCreditsUserBalanceCRUDService userBalanceService;
    private final UserManager userManager;

    /**
     * Checks whether {@code owner} has enough credits to launch a single run on {@code currentRunOffer}.
     *
     * @param owner            username of the run owner
     * @param currentRunOffer  instance offer describing the node to be launched
     * @param activeRunsOffers offers of the owner's currently active (RUNNING / PAUSED) runs,
     *                         used to compute already-allocated credits
     * @return a {@link CreditsCheckResult} with {@code ok=true} if the launch is allowed
     */
    public CreditsCheckResult checkCreditsForRunLaunch(final String owner,
                                                       final InstanceOffer currentRunOffer,
                                                       final List<InstanceOffer> activeRunsOffers) {
        return checkCreditsForGroups(owner,
                Collections.singletonList(new ClusterReplicaGroup(currentRunOffer, 1)),
                activeRunsOffers);
    }

    /**
     * Checks whether {@code owner} has enough credits to launch a <em>homogeneous</em> cluster:
     * {@code replicas} nodes all using the same {@code offer}.
     *
     * <p>This is the convenience overload for {@code runCmd} / {@code runPipeline} clusters where
     * every worker shares the master's instance type. Required credits = {@code replicas × cost(offer)}.
     *
     * @param owner            username of the run owner
     * @param offer            instance offer shared by all nodes in the cluster
     * @param replicas         total node count (master + workers)
     * @param activeRunsOffers offers of the owner's currently active runs
     * @return a {@link CreditsCheckResult} with {@code ok=true} if the launch is allowed
     */
    public CreditsCheckResult checkCreditsForClusterLaunch(final String owner,
                                                           final InstanceOffer offer,
                                                           final int replicas,
                                                           final List<InstanceOffer> activeRunsOffers) {
        return checkCreditsForGroups(owner,
                Collections.singletonList(new ClusterReplicaGroup(offer, replicas)),
                activeRunsOffers);
    }

    /**
     * Checks whether {@code owner} has enough credits to launch a <em>heterogeneous</em> cluster
     * described by {@code groups}, where each group may use a different instance type.
     *
     * <p>Used by {@code CloudPlatformRunner} for multi-entry {@code RunConfiguration} launches.
     * Required credits = Σ (group.replicas × cost(group.offer)).
     * Groups whose offer could not be resolved should be omitted by the caller; an empty
     * {@code groups} list is treated as no-op (returns {@link CreditsCheckResult#allowed()}).
     *
     * @param owner            username of the run owner
     * @param groups           one entry per distinct instance type in the cluster, each carrying
     *                         its resolved {@link InstanceOffer} and the number of replicas
     * @param activeRunsOffers offers of the owner's currently active runs
     * @return a {@link CreditsCheckResult} with {@code ok=true} if the launch is allowed
     */
    public CreditsCheckResult checkCreditsForClusterLaunch(final String owner,
                                                           final List<ClusterReplicaGroup> groups,
                                                           final List<InstanceOffer> activeRunsOffers) {
        return checkCreditsForGroups(owner, groups, activeRunsOffers);
    }

    /**
     * Returns the total credits currently allocated by the given active-run offers.
     *
     * <p>Used by the balance read path to populate the {@code allocated} field on a user's
     * balance response. The caller is responsible for supplying only the relevant owner's offers.
     *
     * @param activeRunsOffers offers of the runs to sum
     * @return sum of {@code cost(offer)} for each offer in the list
     */
    public int getAllocatedCredits(final List<InstanceOffer> activeRunsOffers) {
        return computeUsedCredits(activeRunsOffers, weights());
    }

    private CreditsCheckResult checkCreditsForGroups(final String owner,
                                                     final List<ClusterReplicaGroup> groups,
                                                     final List<InstanceOffer> activeRunsOffers) {
        if (!preferenceManager.getPreference(SystemPreferences.MONITORING_PLATFORM_USAGE_CREDITS_ENABLE)) {
            return CreditsCheckResult.allowed();
        }
        final PipelineUser user = userManager.loadByNameOrId(owner);
        if (user.isAdmin() || user.hasRole(DefaultRoles.ROLE_RUN_ADMIN.getRole())) {
            return CreditsCheckResult.allowed();
        }
        final Map<String, Integer> weights = weights();
        final int required = groups.stream()
                .mapToInt(g -> g.getReplicas() * computeCreditsForOffer(g.getOffer(), weights))
                .sum();
        final int balance = getUserBalance(user);
        final int allocated = computeUsedCredits(activeRunsOffers, weights);
        log.debug("Credits check for user {}: balance={}, allocated={}, required={} (groups={})",
                user.getUserName(), balance, allocated, required, groups.size());
        return new CreditsCheckResult((balance - allocated) >= required, required, balance, allocated);
    }

    private int computeUsedCredits(final List<InstanceOffer> offers, final Map<String, Integer> weights) {
        return offers.stream()
                .mapToInt(offer -> computeCreditsForOffer(offer, weights))
                .sum();
    }

    private int computeCreditsForOffer(final InstanceOffer offer, final Map<String, Integer> weights) {
        return offer.getVCPU() * weights.getOrDefault(CPU_WEIGHT_KEY, CPU_DEFAULT_WEIGHT)
                + offer.getGpu() * weights.getOrDefault(GPU_WEIGHT_KEY, GPU_DEFAULT_WEIGHT);
    }

    private int getUserBalance(final PipelineUser user) {
        return userBalanceService.findByUserId(user.getId())
                .map(PlatformUsageCreditsUserBalance::getCurrentValue)
                .orElseGet(() -> preferenceManager.getPreference(SystemPreferences.USAGE_CREDITS_DEFAULT));
    }

    private Map<String, Integer> weights() {
        return MapUtils.emptyIfNull(
                preferenceManager.getPreference(SystemPreferences.USAGE_CREDITS_RESOURCE_WEIGHTS));
    }
}
