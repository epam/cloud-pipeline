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

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dto.credits.PlatformUsageCreditsMode;
import com.epam.pipeline.dto.credits.PlatformUsageCreditsUserBalance;
import com.epam.pipeline.entity.cluster.InstanceOffer;
import com.epam.pipeline.entity.configuration.PipeConfValueVO;
import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import com.epam.pipeline.entity.contextual.ContextualPreference;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.pipeline.run.parameter.PipelineRunParameter;
import com.epam.pipeline.entity.user.DefaultRoles;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.exception.credits.InsufficientUsageCreditsException;
import com.epam.pipeline.manager.cluster.InstanceOfferManager;
import com.epam.pipeline.manager.contextual.ContextualPreferenceManager;
import com.epam.pipeline.manager.pipeline.PipelineRunCRUDService;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.user.UserManager;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class PlatformUsageCreditsLaunchServiceTest {

    private static final String OWNER = "user1";
    private static final long USER_ID = 1L;
    private static final long RUN_ID_1 = 10L;
    private static final long RUN_ID_2 = 11L;
    private static final String INSTANCE_TYPE = "m5.xlarge";
    private static final String INSTANCE_TYPE_2 = "m5.large";
    private static final String FALLBACK_INSTANCE_TYPE = "m5.2xlarge";
    private static final Long REGION_ID = 1L;

    // credit weights: CPU=1, GPU=100
    private static final int CPU_WEIGHT = 1;
    private static final int GPU_WEIGHT = 100;

    // representative vCPU / GPU counts used across tests
    private static final int VCPU_4 = 4;
    private static final int VCPU_20 = 20;
    private static final int VCPU_60 = 60;
    private static final int VCPU_96 = 96;
    private static final int NO_GPU = 0;
    private static final int NO_VCPU = 0;
    private static final int ONE_GPU = 1;
    private static final int EIGHT_GPU = 8;

    // credit balances
    private static final int BALANCE_2000 = 2000;
    private static final int DEFAULT_BALANCE = 2000;
    private static final int BALANCE_200 = 200;
    private static final int BALANCE_150 = 150;
    private static final int BALANCE_100 = 100;
    private static final int BALANCE_90 = 90;
    private static final int BALANCE_50 = 50;
    private static final int BALANCE_30 = 30;
    private static final int BALANCE_10 = 10;
    private static final int BALANCE_0 = 0;

    // expected allocated / required values derived from weights above
    private static final int CREDITS_104 = 104;     // 4 CPU + 1 GPU

    // node counts used in cluster tests
    private static final int TWO_WORKERS = 2;
    private static final int ZERO_WORKERS = 0;

    private final PreferenceManager preferenceManager = mock(PreferenceManager.class);
    private final ContextualPreferenceManager contextualPreferenceManager =
            mock(ContextualPreferenceManager.class);
    private final PlatformUsageCreditsUserBalanceCRUDService crudService =
            mock(PlatformUsageCreditsUserBalanceCRUDService.class);
    private final UserManager userManager = mock(UserManager.class);
    private final PipelineRunCRUDService pipelineRunCRUDService = mock(PipelineRunCRUDService.class);
    private final InstanceOfferManager instanceOfferManager = mock(InstanceOfferManager.class);
    private final MessageHelper messageHelper = mock(MessageHelper.class);
    private final PlatformUsageCreditsLaunchService service = new PlatformUsageCreditsLaunchService(
            preferenceManager, contextualPreferenceManager, crudService, userManager,
            pipelineRunCRUDService, instanceOfferManager, messageHelper);

    // =========================================================================
    // checkCreditsForRunLaunch
    // =========================================================================

    // --- mode short-circuit ---

    @Test
    public void runLaunchAllowedWhenModeIsOff() {
        doReturn(PlatformUsageCreditsMode.OFF).when(preferenceManager)
                .getPreference(SystemPreferences.USAGE_CREDITS_MODE);
        service.checkCreditsForRunLaunch(Optional.of(offer(VCPU_4, NO_GPU)), Collections.emptyList(),
                null, Collections.emptyMap());
    }

    @Test
    public void runLaunchAllowedWhenModeIsBalanceOnly() {
        doReturn(PlatformUsageCreditsMode.BALANCE_ONLY).when(preferenceManager)
                .getPreference(SystemPreferences.USAGE_CREDITS_MODE);
        service.checkCreditsForRunLaunch(Optional.of(offer(VCPU_4, NO_GPU)), Collections.emptyList(),
                null, Collections.emptyMap());
    }

    @Test
    public void runLaunchSkippedWhenInstanceAbsent() {
        mockCurrentUser(regularUser());
        mockCreditsOn();
        mockWeights();
        mockNoActiveRuns();
        service.checkCreditsForRunLaunch(Optional.empty(), Collections.emptyList(), null, Collections.emptyMap());
    }

    // --- admin bypass ---

    @Test
    public void runLaunchAllowedForAdmin() {
        final PipelineUser admin = regularUser();
        admin.setAdmin(true);
        mockCurrentUser(admin);
        mockCreditsOn();
        service.checkCreditsForRunLaunch(Optional.of(offer(VCPU_4, NO_GPU)), Collections.emptyList(),
                null, Collections.emptyMap());
    }

    @Test
    public void runLaunchAllowedForRunAdmin() {
        final PipelineUser runAdmin = regularUser();
        runAdmin.setRoles(Collections.singletonList(DefaultRoles.ROLE_RUN_ADMIN.getRole()));
        mockCurrentUser(runAdmin);
        mockCreditsOn();
        service.checkCreditsForRunLaunch(Optional.of(offer(VCPU_4, NO_GPU)), Collections.emptyList(),
                null, Collections.emptyMap());
    }

    // --- plain run credits ---

    @Test
    public void runLaunchAllowedWhenSufficient() {
        mockUser();
        mockWeights();
        mockBalance(BALANCE_2000);
        mockNoActiveRuns();
        // balance=2000, required=4 → allowed
        service.checkCreditsForRunLaunch(Optional.of(offer(VCPU_4, NO_GPU)), Collections.emptyList(),
                null, Collections.emptyMap());
    }

    @Test
    public void runLaunchAllowedOnExactFit() {
        // balance=100, active run uses 60, required=40 → 100-60=40 ≥ 40
        final int activeRunCpus = 60;
        final int requiredCpus = 40;

        mockUser();
        mockWeights();
        mockBalance(BALANCE_100);
        mockActiveRuns(provisionedRun(RUN_ID_1, INSTANCE_TYPE, REGION_ID));
        doReturn(Optional.of(offer(activeRunCpus, NO_GPU)))
                .when(instanceOfferManager).findOffer(eq(INSTANCE_TYPE), eq(REGION_ID));
        service.checkCreditsForRunLaunch(Optional.of(offer(requiredCpus, NO_GPU)), Collections.emptyList(),
                null, Collections.emptyMap());
    }

    @Test
    public void runLaunchBlockedWhenInsufficient() {
        // balance=100, active run uses 60, required=50 → 100-60=40 < 50
        final int activeRunCpus = 60;
        final int requiredCpus = 50;

        mockUser();
        mockWeights();
        mockBalance(BALANCE_100);
        mockActiveRuns(provisionedRun(RUN_ID_1, INSTANCE_TYPE, REGION_ID));
        doReturn(Optional.of(offer(activeRunCpus, NO_GPU)))
                .when(instanceOfferManager).findOffer(eq(INSTANCE_TYPE), eq(REGION_ID));
        assertThrows(InsufficientUsageCreditsException.class, () ->
                service.checkCreditsForRunLaunch(Optional.of(offer(requiredCpus, NO_GPU)),
                        Collections.emptyList(), null, Collections.emptyMap()));
    }

    @Test
    public void runLaunchUsesDefaultBalanceWhenNoBalanceRow() {
        mockUser();
        mockWeights();
        mockDefaultBalance(DEFAULT_BALANCE);
        mockNoActiveRuns();
        // default=2000, required=4 → allowed
        service.checkCreditsForRunLaunch(Optional.of(offer(VCPU_4, NO_GPU)), Collections.emptyList(),
                null, Collections.emptyMap());
    }

    @Test
    public void runLaunchBlockedByGpuWeight() {
        // 1 GPU * 100 = 100 credits required; balance=90 → blocked
        mockUser();
        mockWeights();
        mockBalance(BALANCE_90);
        mockNoActiveRuns();
        assertThrows(InsufficientUsageCreditsException.class, () ->
                service.checkCreditsForRunLaunch(Optional.of(offer(NO_VCPU, ONE_GPU)),
                        Collections.emptyList(), null, Collections.emptyMap()));
    }

    // --- fallback instance types ---

    @Test
    public void runLaunchAllowedWhenFallbackAffordable() {
        // primary=4 CPUs (cost=4), fallback=4 CPUs (cost=4); balance=10 → allowed
        mockUser();
        mockWeights();
        mockBalance(BALANCE_10);
        mockNoActiveRuns();
        doReturn(Optional.of(offer(VCPU_4, NO_GPU)))
                .when(instanceOfferManager).findOffer(eq(FALLBACK_INSTANCE_TYPE), eq(REGION_ID));
        service.checkCreditsForRunLaunch(Optional.of(offer(VCPU_4, NO_GPU)),
                Collections.singletonList(FALLBACK_INSTANCE_TYPE), REGION_ID, Collections.emptyMap());
    }

    @Test
    public void runLaunchBlockedWhenFallbackMoreExpensive() {
        // primary=4 CPUs (cost=4), fallback=60 CPUs (cost=60); balance=10 → blocked by worst-case
        mockUser();
        mockWeights();
        mockBalance(BALANCE_10);
        mockNoActiveRuns();
        doReturn(Optional.of(offer(VCPU_60, NO_GPU)))
                .when(instanceOfferManager).findOffer(eq(FALLBACK_INSTANCE_TYPE), eq(REGION_ID));
        assertThrows(InsufficientUsageCreditsException.class, () ->
                service.checkCreditsForRunLaunch(Optional.of(offer(VCPU_4, NO_GPU)),
                        Collections.singletonList(FALLBACK_INSTANCE_TYPE), REGION_ID, Collections.emptyMap()));
    }

    @Test
    public void runLaunchBlockedWhenPrimaryMoreExpensiveThanFallback() {
        // primary=60 CPUs (cost=60 > balance=10) → blocked even though fallback is cheaper
        mockUser();
        mockWeights();
        mockBalance(BALANCE_10);
        mockNoActiveRuns();
        doReturn(Optional.of(offer(VCPU_4, NO_GPU)))
                .when(instanceOfferManager).findOffer(eq(FALLBACK_INSTANCE_TYPE), eq(REGION_ID));
        assertThrows(InsufficientUsageCreditsException.class, () ->
                service.checkCreditsForRunLaunch(Optional.of(offer(VCPU_60, NO_GPU)),
                        Collections.singletonList(FALLBACK_INSTANCE_TYPE), REGION_ID, Collections.emptyMap()));
    }

    @Test
    public void runLaunchSkipsUnresolvableFallbackType() {
        // fallback not in catalogue → absent → skipped; primary passes → allowed
        mockUser();
        mockWeights();
        mockBalance(BALANCE_10);
        mockNoActiveRuns();
        doReturn(Optional.empty()).when(instanceOfferManager).findOffer(eq(FALLBACK_INSTANCE_TYPE), eq(REGION_ID));
        service.checkCreditsForRunLaunch(Optional.of(offer(VCPU_4, NO_GPU)),
                Collections.singletonList(FALLBACK_INSTANCE_TYPE), REGION_ID, Collections.emptyMap());
    }

    // --- capacity block params at launch ---

    @Test
    public void runLaunchUsesRequestedCpuForCapacityBlock() {
        // catalogue offer has 96 CPUs but run requests only 4 → cost=4, balance=10 → allowed
        final String requestedCpus = "4";

        mockUser();
        mockWeights();
        mockBalance(BALANCE_10);
        mockNoActiveRuns();
        service.checkCreditsForRunLaunch(Optional.of(offer(VCPU_96, NO_GPU)),
                Collections.emptyList(), null, params(requestedCpus, null));
    }

    @Test
    public void runLaunchUsesRequestedGpuForCapacityBlock() {
        // catalogue offer has 8 GPUs (cost=800) but run requests 1 GPU (cost=100); balance=150 → allowed
        final String requestedGpus = "1";

        mockUser();
        mockWeights();
        mockBalance(BALANCE_150);
        mockNoActiveRuns();
        service.checkCreditsForRunLaunch(Optional.of(offer(NO_VCPU, EIGHT_GPU)),
                Collections.emptyList(), null, params(null, requestedGpus));
    }

    @Test
    public void runLaunchBlockedWhenCapacityBlockRequestExceedsBalance() {
        // requests cpu=2 (cost=2) + gpu=1 (cost=100) = 102; balance=100 → blocked
        final String requestedCpus = "2";
        final String requestedGpus = "1";

        mockUser();
        mockWeights();
        mockBalance(BALANCE_100);
        mockNoActiveRuns();
        assertThrows(InsufficientUsageCreditsException.class, () ->
                service.checkCreditsForRunLaunch(Optional.of(offer(VCPU_96, EIGHT_GPU)),
                        Collections.emptyList(), null, params(requestedCpus, requestedGpus)));
    }

    @Test
    public void runLaunchAllowedWhenCapacityBlockParamsBothZero() {
        // both params present but value=0 → required=0 → always allowed even with balance=0
        mockUser();
        mockWeights();
        mockBalance(BALANCE_0);
        mockNoActiveRuns();
        service.checkCreditsForRunLaunch(Optional.of(offer(VCPU_96, EIGHT_GPU)),
                Collections.emptyList(), null, params("0", "0"));
    }

    @Test
    public void runLaunchTreatsNonNumericCapacityBlockParamAsZero() {
        // non-numeric gpu → treated as 0; cpu=4 → required=4, balance=10 → allowed
        mockUser();
        mockWeights();
        mockBalance(BALANCE_10);
        mockNoActiveRuns();
        service.checkCreditsForRunLaunch(Optional.of(offer(VCPU_96, EIGHT_GPU)),
                Collections.emptyList(), null, params("4", "notanumber"));
    }

    @Test
    public void runLaunchUsesFullOfferWhenNoCapacityBlockParams() {
        // no CB params → full offer (96 CPUs = 96 credits), balance=10 → blocked
        mockUser();
        mockWeights();
        mockBalance(BALANCE_10);
        mockNoActiveRuns();
        assertThrows(InsufficientUsageCreditsException.class, () ->
                service.checkCreditsForRunLaunch(Optional.of(offer(VCPU_96, NO_GPU)),
                        Collections.emptyList(), null, Collections.emptyMap()));
    }

    // --- active-run offer resolution ---

    @Test
    public void activeCapacityBlockRunContributesSyntheticCpuCost() {
        // active CB run requests 4 CPUs → allocated=4; balance=10, required=8 → 10-4=6 < 8 → blocked
        final String activeRunRequestedCpus = "4";
        final int requiredCpus = 8;

        mockUser();
        mockWeights();
        mockBalance(BALANCE_10);
        mockActiveRuns(cbRun(RUN_ID_1, activeRunRequestedCpus, null));
        assertThrows(InsufficientUsageCreditsException.class, () ->
                service.checkCreditsForRunLaunch(Optional.of(offer(requiredCpus, NO_GPU)),
                        Collections.emptyList(), null, Collections.emptyMap()));
    }

    @Test
    public void activeCapacityBlockRunContributesSyntheticGpuCost() {
        // active CB run requests 1 GPU → allocated=100; balance=150, required=60 → 150-100=50 < 60 → blocked
        final String activeRunRequestedGpus = "1";
        final int requiredCpus = 60;

        mockUser();
        mockWeights();
        mockBalance(BALANCE_150);
        mockActiveRuns(cbRun(RUN_ID_1, null, activeRunRequestedGpus));
        assertThrows(InsufficientUsageCreditsException.class, () ->
                service.checkCreditsForRunLaunch(Optional.of(offer(requiredCpus, NO_GPU)),
                        Collections.emptyList(), null, Collections.emptyMap()));
    }

    @Test
    public void activeCapacityBlockRunWithZeroParamsContributesNothing() {
        // CB run with cpu=0, gpu=0 → allocated=0; balance=10, required=4 → allowed
        mockUser();
        mockWeights();
        mockBalance(BALANCE_10);
        mockActiveRuns(cbRun(RUN_ID_1, "0", "0"));
        service.checkCreditsForRunLaunch(Optional.of(offer(VCPU_4, NO_GPU)), Collections.emptyList(),
                null, Collections.emptyMap());
    }

    @Test
    public void activeRunWithNoInstanceIsIgnored() {
        final PipelineRun runWithNoInstance = new PipelineRun();
        runWithNoInstance.setId(RUN_ID_1);
        runWithNoInstance.setOwner(OWNER);

        mockUser();
        mockWeights();
        mockBalance(BALANCE_10);
        mockActiveRuns(runWithNoInstance);
        service.checkCreditsForRunLaunch(Optional.of(offer(VCPU_4, NO_GPU)), Collections.emptyList(),
                null, Collections.emptyMap());
    }

    @Test
    public void activeRunNotYetProvisionedUsesWorstCaseFallback() {
        // unprovisioned run has primary=4 CPUs but fallback=60 CPUs → worst-case=60 → allocated=60
        // balance=100, required=50 → 100-60=40 < 50 → blocked
        final int fallbackCpus = 60;
        final int requiredCpus = 50;

        mockUser();
        mockWeights();
        mockBalance(BALANCE_100);
        final PipelineRun unprovisionedRun = unprovisionedRun(RUN_ID_1, INSTANCE_TYPE, REGION_ID,
                Collections.singletonList(FALLBACK_INSTANCE_TYPE));
        mockActiveRuns(unprovisionedRun);
        doReturn(Optional.of(offer(VCPU_4, NO_GPU)))
                .when(instanceOfferManager).findOffer(eq(INSTANCE_TYPE), eq(REGION_ID));
        doReturn(Optional.of(offer(fallbackCpus, NO_GPU)))
                .when(instanceOfferManager).findOffer(eq(FALLBACK_INSTANCE_TYPE), eq(REGION_ID));
        assertThrows(InsufficientUsageCreditsException.class, () ->
                service.checkCreditsForRunLaunch(Optional.of(offer(requiredCpus, NO_GPU)),
                        Collections.emptyList(), null, Collections.emptyMap()));
    }

    @Test
    public void activeRunProvisionedUsesActualInstanceType() {
        // provisioned run: nodeId set → use actual offer (4 CPUs), ignore any fallbacks
        // balance=10, required=4 → 10-4=6 ≥ 4... but required=8 → blocked
        final int activeRunCpus = 4;
        final int requiredCpus = 8;

        mockUser();
        mockWeights();
        mockBalance(BALANCE_10);
        mockActiveRuns(provisionedRun(RUN_ID_1, INSTANCE_TYPE, REGION_ID));
        doReturn(Optional.of(offer(activeRunCpus, NO_GPU)))
                .when(instanceOfferManager).findOffer(eq(INSTANCE_TYPE), eq(REGION_ID));
        assertThrows(InsufficientUsageCreditsException.class, () ->
                service.checkCreditsForRunLaunch(Optional.of(offer(requiredCpus, NO_GPU)),
                        Collections.emptyList(), null, Collections.emptyMap()));
    }

    @Test
    public void mixedActiveRunsAreAllSummed() {
        // plain run: 20 CPUs=20; CB run: requests 4 CPUs=4 → allocated=24
        // balance=30, required=8 → 30-24=6 < 8 → blocked
        final int plainRunCpus = 20;
        final String cbRunRequestedCpus = "4";
        final int requiredCpus = 8;

        mockUser();
        mockWeights();
        mockBalance(BALANCE_30);
        mockActiveRuns(
                provisionedRun(RUN_ID_1, INSTANCE_TYPE, REGION_ID),
                cbRun(RUN_ID_2, cbRunRequestedCpus, null));
        doReturn(Optional.of(offer(plainRunCpus, NO_GPU)))
                .when(instanceOfferManager).findOffer(eq(INSTANCE_TYPE), eq(REGION_ID));
        assertThrows(InsufficientUsageCreditsException.class, () ->
                service.checkCreditsForRunLaunch(Optional.of(offer(requiredCpus, NO_GPU)),
                        Collections.emptyList(), null, Collections.emptyMap()));
    }

    // =========================================================================
    // checkCreditsForResumeRun
    // =========================================================================

    @Test
    public void resumeAllowedWhenModeIsOff() {
        doReturn(PlatformUsageCreditsMode.OFF).when(preferenceManager)
                .getPreference(SystemPreferences.USAGE_CREDITS_MODE);
        doReturn(regularUser()).when(userManager).loadByNameOrId(OWNER);
        service.checkCreditsForResumeRun(provisionedRun(RUN_ID_1, INSTANCE_TYPE, REGION_ID));
    }

    @Test
    public void resumeAllowedForAdmin() {
        final PipelineUser admin = regularUser();
        admin.setAdmin(true);
        mockCreditsOn();
        doReturn(admin).when(userManager).loadByNameOrId(OWNER);
        service.checkCreditsForResumeRun(provisionedRun(RUN_ID_1, INSTANCE_TYPE, REGION_ID));
    }

    @Test
    public void resumeAllowedWhenSufficient() {
        // balance=100, no active runs, required=4 CPUs → allowed
        mockUserByNameOrId();
        mockWeights();
        mockBalance(BALANCE_100);
        mockNoActiveRuns();
        doReturn(Optional.of(offer(VCPU_4, NO_GPU)))
                .when(instanceOfferManager).findOffer(eq(INSTANCE_TYPE), eq(REGION_ID));
        service.checkCreditsForResumeRun(provisionedRun(RUN_ID_1, INSTANCE_TYPE, REGION_ID));
    }

    @Test
    public void resumeBlockedWhenInsufficient() {
        // balance=10, no active runs, required=60 CPUs → blocked
        mockUserByNameOrId();
        mockWeights();
        mockBalance(BALANCE_10);
        mockNoActiveRuns();
        doReturn(Optional.of(offer(VCPU_60, NO_GPU)))
                .when(instanceOfferManager).findOffer(eq(INSTANCE_TYPE), eq(REGION_ID));
        assertThrows(InsufficientUsageCreditsException.class, () ->
                service.checkCreditsForResumeRun(provisionedRun(RUN_ID_1, INSTANCE_TYPE, REGION_ID)));
    }

    @Test
    public void resumeDoesNotCountPausedRunItself() {
        // PAUSED runs are excluded from allocation; the resumed run is not double-counted
        // balance=10, active (RUNNING) runs=0, required=4 → allowed
        mockUserByNameOrId();
        mockWeights();
        mockBalance(BALANCE_10);
        mockNoActiveRuns();
        doReturn(Optional.of(offer(VCPU_4, NO_GPU)))
                .when(instanceOfferManager).findOffer(eq(INSTANCE_TYPE), eq(REGION_ID));
        service.checkCreditsForResumeRun(provisionedRun(RUN_ID_1, INSTANCE_TYPE, REGION_ID));
    }

    @Test
    public void resumeUnprovisionedRunUsesWorstCaseFallback() {
        // unprovisioned paused run: primary=4 CPUs, fallback=60 CPUs → worst-case=60
        // balance=10, no active runs → 10-0=10 < 60 → blocked
        mockUserByNameOrId();
        mockWeights();
        mockBalance(BALANCE_10);
        mockNoActiveRuns();
        doReturn(Optional.of(offer(VCPU_4, NO_GPU)))
                .when(instanceOfferManager).findOffer(eq(INSTANCE_TYPE), eq(REGION_ID));
        doReturn(Optional.of(offer(VCPU_60, NO_GPU)))
                .when(instanceOfferManager).findOffer(eq(FALLBACK_INSTANCE_TYPE), eq(REGION_ID));
        final PipelineRun run = unprovisionedRun(RUN_ID_1, INSTANCE_TYPE, REGION_ID,
                Collections.singletonList(FALLBACK_INSTANCE_TYPE));
        assertThrows(InsufficientUsageCreditsException.class, () ->
                service.checkCreditsForResumeRun(run));
    }

    @Test
    public void resumeCapacityBlockRunUsesSyntheticOffer() {
        // CB paused run requests 4 CPUs → cost=4; balance=10 → allowed
        final String requestedCpus = "4";

        mockUserByNameOrId();
        mockWeights();
        mockBalance(BALANCE_10);
        mockNoActiveRuns();
        service.checkCreditsForResumeRun(cbRun(RUN_ID_1, requestedCpus, null));
    }

    // =========================================================================
    // checkCreditsForCluster
    // =========================================================================

    @Test
    public void clusterAllowedWhenModeIsOff() {
        doReturn(PlatformUsageCreditsMode.OFF).when(preferenceManager)
                .getPreference(SystemPreferences.USAGE_CREDITS_MODE);
        service.checkCreditsForCluster(config(INSTANCE_TYPE, REGION_ID, TWO_WORKERS, null));
    }

    @Test
    public void clusterAllowedForAdmin() {
        final PipelineUser admin = regularUser();
        admin.setAdmin(true);
        mockCurrentUser(admin);
        mockCreditsOn();
        service.checkCreditsForCluster(config(INSTANCE_TYPE, REGION_ID, TWO_WORKERS, null));
    }

    @Test
    public void clusterSkippedWhenNodeCountIsZero() {
        // nodeCount=0 → check skipped entirely regardless of balance
        mockUser();
        mockWeights();
        mockBalance(BALANCE_0);
        mockNoActiveRuns();
        service.checkCreditsForCluster(config(INSTANCE_TYPE, REGION_ID, ZERO_WORKERS, null));
    }

    @Test
    public void clusterAllowedWhenSufficient() {
        // 3 replicas (1 master + 2 workers) * 4 CPUs = 12 credits; balance=200 → allowed
        final int instanceCpus = 4;

        mockUser();
        mockWeights();
        mockBalance(BALANCE_200);
        mockNoActiveRuns();
        doReturn(Optional.of(offer(instanceCpus, NO_GPU)))
                .when(instanceOfferManager).findOffer(eq(INSTANCE_TYPE), eq(REGION_ID));
        service.checkCreditsForCluster(config(INSTANCE_TYPE, REGION_ID, TWO_WORKERS, null));
    }

    @Test
    public void clusterBlockedWhenInsufficient() {
        // 3 replicas * 20 CPUs = 60; balance=50 → blocked
        mockUser();
        mockWeights();
        mockBalance(BALANCE_50);
        mockNoActiveRuns();
        doReturn(Optional.of(offer(VCPU_20, NO_GPU)))
                .when(instanceOfferManager).findOffer(eq(INSTANCE_TYPE), eq(REGION_ID));
        assertThrows(InsufficientUsageCreditsException.class, () ->
                service.checkCreditsForCluster(config(INSTANCE_TYPE, REGION_ID, TWO_WORKERS, null)));
    }

    @Test
    public void clusterBlockedWhenFallbackMoreExpensive() {
        // primary=4 CPUs (cost=4), fallback=60 CPUs (cost=60); 3 replicas * 60 = 180; balance=50 → blocked
        mockUser();
        mockWeights();
        mockBalance(BALANCE_50);
        mockNoActiveRuns();
        doReturn(Optional.of(offer(VCPU_4, NO_GPU)))
                .when(instanceOfferManager).findOffer(eq(INSTANCE_TYPE), eq(REGION_ID));
        doReturn(Optional.of(offer(VCPU_60, NO_GPU)))
                .when(instanceOfferManager).findOffer(eq(FALLBACK_INSTANCE_TYPE), eq(REGION_ID));
        assertThrows(InsufficientUsageCreditsException.class, () ->
                service.checkCreditsForCluster(config(INSTANCE_TYPE, REGION_ID, TWO_WORKERS,
                        Collections.singletonList(FALLBACK_INSTANCE_TYPE))));
    }

    // =========================================================================
    // checkCreditsForConfiguration
    // =========================================================================

    @Test
    public void configurationAllowedWhenModeIsOff() {
        doReturn(PlatformUsageCreditsMode.OFF).when(preferenceManager)
                .getPreference(SystemPreferences.USAGE_CREDITS_MODE);
        final PipelineConfiguration master = config(INSTANCE_TYPE, REGION_ID, null, null);
        final PipelineConfiguration worker = config(INSTANCE_TYPE_2, REGION_ID, null, null);
        service.checkCreditsForConfiguration(master, 0, Collections.singletonList(worker));
    }

    @Test
    public void configurationAllowedForAdmin() {
        final PipelineUser admin = regularUser();
        admin.setAdmin(true);
        mockCurrentUser(admin);
        mockCreditsOn();
        final PipelineConfiguration master = config(INSTANCE_TYPE, REGION_ID, null, null);
        service.checkCreditsForConfiguration(master, 0, Collections.emptyList());
    }

    @Test
    public void configurationSkippedWhenNoOffersResolvable() {
        // both instance types absent from catalogue → groups empty → no-op even with balance=0
        mockUser();
        mockWeights();
        mockBalance(BALANCE_0);
        mockNoActiveRuns();
        doReturn(Optional.empty()).when(instanceOfferManager).findOffer(any(), any());
        final PipelineConfiguration master = config(INSTANCE_TYPE, REGION_ID, null, null);
        service.checkCreditsForConfiguration(master, 0, Collections.emptyList());
    }

    @Test
    public void configurationAllowedWhenSufficient() {
        // master (1 replica) + 1 worker (1 replica) = 2 nodes * 4 CPUs = 8; balance=100 → allowed
        final int instanceCpus = 4;

        mockUser();
        mockWeights();
        mockBalance(BALANCE_100);
        mockNoActiveRuns();
        doReturn(Optional.of(offer(instanceCpus, NO_GPU)))
                .when(instanceOfferManager).findOffer(any(), eq(REGION_ID));
        final PipelineConfiguration master = config(INSTANCE_TYPE, REGION_ID, null, null);
        final PipelineConfiguration worker = config(INSTANCE_TYPE_2, REGION_ID, null, null);
        service.checkCreditsForConfiguration(master, 0, Collections.singletonList(worker));
    }

    @Test
    public void configurationBlockedWhenInsufficient() {
        // master + 1 worker = 2 * 60 CPUs = 120; balance=100 → blocked
        mockUser();
        mockWeights();
        mockBalance(BALANCE_100);
        mockNoActiveRuns();
        doReturn(Optional.of(offer(VCPU_60, NO_GPU)))
                .when(instanceOfferManager).findOffer(any(), eq(REGION_ID));
        final PipelineConfiguration master = config(INSTANCE_TYPE, REGION_ID, null, null);
        final PipelineConfiguration worker = config(INSTANCE_TYPE_2, REGION_ID, null, null);
        assertThrows(InsufficientUsageCreditsException.class, () ->
                service.checkCreditsForConfiguration(master, 0, Collections.singletonList(worker)));
    }

    @Test
    public void configurationMasterCapacityBlockUsesSyntheticOffer() {
        // master requests 4 CPUs via CB params (catalogue has 96); worker=4 CPUs
        // total = 4 + 4 = 8; balance=10 → allowed
        final String masterRequestedCpus = "4";
        final int workerCpus = 4;

        mockUser();
        mockWeights();
        mockBalance(BALANCE_10);
        mockNoActiveRuns();
        doReturn(Optional.of(offer(workerCpus, NO_GPU)))
                .when(instanceOfferManager).findOffer(eq(INSTANCE_TYPE_2), eq(REGION_ID));
        final PipelineConfiguration master = configWithParams(INSTANCE_TYPE, REGION_ID,
                params(masterRequestedCpus, null));
        final PipelineConfiguration worker = config(INSTANCE_TYPE_2, REGION_ID, null, null);
        service.checkCreditsForConfiguration(master, 0, Collections.singletonList(worker));
    }

    @Test
    public void configurationWorkerCapacityBlockUsesSyntheticOffer() {
        // master=4 CPUs; worker requests 4 CPUs via CB params (catalogue has 96)
        // total = 4 + 4 = 8; balance=10 → allowed
        final int masterCpus = 4;
        final String workerRequestedCpus = "4";

        mockUser();
        mockWeights();
        mockBalance(BALANCE_10);
        mockNoActiveRuns();
        doReturn(Optional.of(offer(masterCpus, NO_GPU)))
                .when(instanceOfferManager).findOffer(eq(INSTANCE_TYPE), eq(REGION_ID));
        final PipelineConfiguration master = config(INSTANCE_TYPE, REGION_ID, null, null);
        final PipelineConfiguration worker = configWithParams(INSTANCE_TYPE_2, REGION_ID,
                params(workerRequestedCpus, null));
        service.checkCreditsForConfiguration(master, 0, Collections.singletonList(worker));
    }

    @Test
    public void configurationBlockedWhenWorkerFallbackMoreExpensive() {
        // master=4 CPUs; worker primary=4 CPUs, worker fallback=60 CPUs → worst-case worker cost=60
        // total = 4 + 60 = 64; balance=50 → blocked
        final int masterCpus = 4;
        final int workerPrimaryCpus = 4;
        final int workerFallbackCpus = 60;

        mockUser();
        mockWeights();
        mockBalance(BALANCE_50);
        mockNoActiveRuns();
        doReturn(Optional.of(offer(masterCpus, NO_GPU)))
                .when(instanceOfferManager).findOffer(eq(INSTANCE_TYPE), eq(REGION_ID));
        doReturn(Optional.of(offer(workerPrimaryCpus, NO_GPU)))
                .when(instanceOfferManager).findOffer(eq(INSTANCE_TYPE_2), eq(REGION_ID));
        doReturn(Optional.of(offer(workerFallbackCpus, NO_GPU)))
                .when(instanceOfferManager).findOffer(eq(FALLBACK_INSTANCE_TYPE), eq(REGION_ID));
        final PipelineConfiguration master = config(INSTANCE_TYPE, REGION_ID, null, null);
        final PipelineConfiguration worker = config(INSTANCE_TYPE_2, REGION_ID, null,
                Collections.singletonList(FALLBACK_INSTANCE_TYPE));
        assertThrows(InsufficientUsageCreditsException.class, () ->
                service.checkCreditsForConfiguration(master, 0, Collections.singletonList(worker)));
    }

    @Test
    public void configurationMasterNodeCountIncludedInTotal() {
        // masterNodeCount=1 → 2 master-side replicas; plus 1 worker = 3 total * 20 CPUs = 60; balance=50 → blocked
        final int masterNodeCount = 1;

        mockUser();
        mockWeights();
        mockBalance(BALANCE_50);
        mockNoActiveRuns();
        doReturn(Optional.of(offer(VCPU_20, NO_GPU)))
                .when(instanceOfferManager).findOffer(any(), eq(REGION_ID));
        final PipelineConfiguration master = config(INSTANCE_TYPE, REGION_ID, null, null);
        final PipelineConfiguration worker = config(INSTANCE_TYPE_2, REGION_ID, null, null);
        assertThrows(InsufficientUsageCreditsException.class, () ->
                service.checkCreditsForConfiguration(master, masterNodeCount,
                        Collections.singletonList(worker)));
    }

    // =========================================================================
    // getAllocatedCredits
    // =========================================================================

    @Test
    public void getAllocatedCreditsReturnsZeroWhenNoActiveRuns() {
        mockWeights();
        mockNoActiveRuns();
        assertThat(service.getAllocatedCredits(OWNER), is(0));
    }

    @Test
    public void getAllocatedCreditsSumsPlainActiveRuns() {
        // run1=4 CPUs, run2=2 CPUs → total=6
        final int run1Cpus = 4;
        final int run2Cpus = 2;
        final int expectedAllocated = 6;

        mockWeights();
        mockActiveRuns(
                provisionedRun(RUN_ID_1, INSTANCE_TYPE, REGION_ID),
                provisionedRun(RUN_ID_2, INSTANCE_TYPE_2, REGION_ID));
        doReturn(Optional.of(offer(run1Cpus, NO_GPU)))
                .when(instanceOfferManager).findOffer(eq(INSTANCE_TYPE), eq(REGION_ID));
        doReturn(Optional.of(offer(run2Cpus, NO_GPU)))
                .when(instanceOfferManager).findOffer(eq(INSTANCE_TYPE_2), eq(REGION_ID));
        assertThat(service.getAllocatedCredits(OWNER), is(expectedAllocated));
    }

    @Test
    public void getAllocatedCreditsSumsCapacityBlockActiveRuns() {
        // CB run: 4 CPU * 1 + 1 GPU * 100 = 104
        final String cpuRequest = "4";
        final String gpuRequest = "1";

        mockWeights();
        mockActiveRuns(cbRun(RUN_ID_1, cpuRequest, gpuRequest));
        assertThat(service.getAllocatedCredits(OWNER), is(CREDITS_104));
    }

    // =========================================================================
    // Helpers — mocking
    // =========================================================================

    private void mockCreditsOn() {
        doReturn(PlatformUsageCreditsMode.ON).when(preferenceManager)
                .getPreference(SystemPreferences.USAGE_CREDITS_MODE);
    }

    private void mockCurrentUser(final PipelineUser user) {
        doReturn(user).when(userManager).getCurrentUser();
    }

    private void mockUserByNameOrId() {
        mockCreditsOn();
        doReturn(regularUser()).when(userManager).loadByNameOrId(OWNER);
    }

    private void mockUser() {
        mockCreditsOn();
        mockCurrentUser(regularUser());
    }

    private void mockWeights() {
        doReturn(weights()).when(preferenceManager)
                .getPreference(SystemPreferences.USAGE_CREDITS_RESOURCE_WEIGHTS);
    }

    private void mockBalance(final int value) {
        final PlatformUsageCreditsUserBalance balance = new PlatformUsageCreditsUserBalance();
        balance.setCurrentValue(value);
        doReturn(Optional.of(balance)).when(crudService).findByUserId(USER_ID);
    }

    private void mockDefaultBalance(final int value) {
        doReturn(Optional.empty()).when(crudService).findByUserId(USER_ID);
        doReturn(new ContextualPreference(SystemPreferences.USAGE_CREDITS_DEFAULT.getKey(),
                String.valueOf(value)))
                .when(contextualPreferenceManager).search(any(), any(PipelineUser.class));
    }

    private void mockNoActiveRuns() {
        doReturn(Collections.emptyList()).when(pipelineRunCRUDService)
                .loadRunsByStatusesAndOwner(any(), eq(OWNER));
    }

    private void mockActiveRuns(final PipelineRun... runs) {
        doReturn(Arrays.asList(runs)).when(pipelineRunCRUDService)
                .loadRunsByStatusesAndOwner(any(), eq(OWNER));
    }

    // =========================================================================
    // Helpers — builders
    // =========================================================================

    private static PipelineRun provisionedRun(final long id, final String instanceType, final Long regionId) {
        final PipelineRun run = new PipelineRun();
        run.setId(id);
        run.setOwner(OWNER);
        final RunInstance instance = new RunInstance();
        instance.setNodeType(instanceType);
        instance.setCloudRegionId(regionId);
        instance.setNodeId("node-" + id);
        run.setInstance(instance);
        run.setPipelineRunParameters(Collections.emptyList());
        return run;
    }

    private static PipelineRun unprovisionedRun(final long id, final String instanceType, final Long regionId,
                                                final List<String> fallbackInstanceTypes) {
        final PipelineRun run = new PipelineRun();
        run.setId(id);
        run.setOwner(OWNER);
        final RunInstance instance = new RunInstance();
        instance.setNodeType(instanceType);
        instance.setCloudRegionId(regionId);
        instance.setFallbackInstanceTypes(fallbackInstanceTypes);
        // nodeId intentionally not set → unprovisioned
        run.setInstance(instance);
        run.setPipelineRunParameters(Collections.emptyList());
        return run;
    }

    private static PipelineRun cbRun(final long id, final String cpuRequest, final String gpuRequest) {
        final PipelineRun run = new PipelineRun();
        run.setId(id);
        run.setOwner(OWNER);
        final RunInstance instance = new RunInstance();
        instance.setNodeType(INSTANCE_TYPE);
        instance.setCloudRegionId(REGION_ID);
        instance.setNodeId("node-" + id);
        run.setInstance(instance);
        final List<PipelineRunParameter> params = new java.util.ArrayList<>();
        if (cpuRequest != null) {
            params.add(new PipelineRunParameter(
                    PlatformUsageCreditsLaunchService.CP_CAP_REQUESTS_CPU, cpuRequest));
        }
        if (gpuRequest != null) {
            params.add(new PipelineRunParameter(
                    PlatformUsageCreditsLaunchService.CP_CAP_REQUESTS_GPU, gpuRequest));
        }
        run.setPipelineRunParameters(params);
        return run;
    }

    private static Map<String, PipeConfValueVO> params(final String cpu, final String gpu) {
        final Map<String, PipeConfValueVO> map = new HashMap<>();
        if (cpu != null) {
            map.put(PlatformUsageCreditsLaunchService.CP_CAP_REQUESTS_CPU, new PipeConfValueVO(cpu));
        }
        if (gpu != null) {
            map.put(PlatformUsageCreditsLaunchService.CP_CAP_REQUESTS_GPU, new PipeConfValueVO(gpu));
        }
        return map;
    }

    private static InstanceOffer offer(final int vcpu, final int gpu) {
        final InstanceOffer o = new InstanceOffer();
        o.setVCPU(vcpu);
        o.setGpu(gpu);
        return o;
    }

    private static PipelineConfiguration config(final String instanceType, final Long regionId,
                                                final Integer nodeCount,
                                                final List<String> fallbackInstanceTypes) {
        final PipelineConfiguration config = new PipelineConfiguration();
        config.setInstanceType(instanceType);
        config.setCloudRegionId(regionId);
        config.setNodeCount(nodeCount);
        config.setFallbackInstanceTypes(fallbackInstanceTypes);
        return config;
    }

    private static PipelineConfiguration configWithParams(final String instanceType, final Long regionId,
                                                          final Map<String, PipeConfValueVO> parameters) {
        final PipelineConfiguration config = new PipelineConfiguration();
        config.setInstanceType(instanceType);
        config.setCloudRegionId(regionId);
        config.setParameters(parameters);
        return config;
    }

    private static PipelineUser regularUser() {
        final PipelineUser user = new PipelineUser();
        user.setId(USER_ID);
        user.setUserName(OWNER);
        user.setAdmin(false);
        user.setRoles(Collections.emptyList());
        return user;
    }

    private static Map<String, Integer> weights() {
        final Map<String, Integer> w = new HashMap<>();
        w.put("CPU", CPU_WEIGHT);
        w.put("GPU", GPU_WEIGHT);
        return w;
    }
}
