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
import com.epam.pipeline.entity.cluster.pool.InstanceRequest;
import com.epam.pipeline.entity.configuration.PipeConfValueVO;
import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.pipeline.run.parameter.PipelineRunParameter;
import com.epam.pipeline.entity.user.DefaultRoles;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.exception.credits.InsufficientUsageCreditsException;
import com.epam.pipeline.manager.cluster.InstanceOfferManager;
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class PlatformUsageCreditsLaunchServiceTest {

    private static final String OWNER = "user1";
    private static final long USER_ID = 1L;
    private static final long RUN_ID_1 = 10L;
    private static final long RUN_ID_2 = 11L;
    private static final int BALANCE = 2000;
    private static final int DEFAULT_BALANCE = 2000;
    private static final int CPU_WEIGHT = 1;
    private static final int GPU_WEIGHT = 100;
    private static final String INSTANCE_TYPE = "m5.xlarge";
    private static final String INSTANCE_TYPE_2 = "m5.large";
    private static final Long REGION_ID = 1L;
    private static final int VCPU = 4;
    private static final int NO_GPU = 0;
    private static final int NO_VCPU = 0;
    private static final int ONE_GPU = 1;

    private final PreferenceManager preferenceManager = mock(PreferenceManager.class);
    private final PlatformUsageCreditsUserBalanceCRUDService crudService =
            mock(PlatformUsageCreditsUserBalanceCRUDService.class);
    private final UserManager userManager = mock(UserManager.class);
    private final PipelineRunCRUDService pipelineRunCRUDService = mock(PipelineRunCRUDService.class);
    private final InstanceOfferManager instanceOfferManager = mock(InstanceOfferManager.class);
    private final MessageHelper messageHelper = mock(MessageHelper.class);
    private final PlatformUsageCreditsLaunchService service = new PlatformUsageCreditsLaunchService(
            preferenceManager, crudService, userManager, pipelineRunCRUDService,
            instanceOfferManager, messageHelper);

    // -------------------------------------------------------------------------
    // Mode short-circuit
    // -------------------------------------------------------------------------

    @Test
    public void runLaunchAllowedWhenModeIsOff() {
        doReturn(PlatformUsageCreditsMode.OFF).when(preferenceManager)
                .getPreference(SystemPreferences.USAGE_CREDITS_MODE);
        mockNoActiveRuns();
        // no exception = allowed
        service.checkCreditsForRunLaunch(OWNER, Optional.of(offer(VCPU, NO_GPU)), Collections.emptyMap());
    }

    @Test
    public void runLaunchAllowedWhenModeIsBalanceOnly() {
        doReturn(PlatformUsageCreditsMode.BALANCE_ONLY).when(preferenceManager)
                .getPreference(SystemPreferences.USAGE_CREDITS_MODE);
        mockNoActiveRuns();
        service.checkCreditsForRunLaunch(OWNER, Optional.of(offer(VCPU, NO_GPU)), Collections.emptyMap());
    }

    @Test
    public void runLaunchSkippedWhenInstanceAbsent() {
        doReturn(PlatformUsageCreditsMode.ON).when(preferenceManager)
                .getPreference(SystemPreferences.USAGE_CREDITS_MODE);
        // empty Optional → no check at all, no exception
        service.checkCreditsForRunLaunch(OWNER, Optional.empty(), Collections.emptyMap());
    }

    // -------------------------------------------------------------------------
    // checkCreditsForRunLaunch — admin / run-admin bypass
    // -------------------------------------------------------------------------

    @Test
    public void runLaunchAllowedForAdmin() {
        final PipelineUser admin = regularUser();
        admin.setAdmin(true);
        mockCreditsEnforced();
        mockNoActiveRuns();
        doReturn(admin).when(userManager).loadByNameOrId(OWNER);
        service.checkCreditsForRunLaunch(OWNER, Optional.of(offer(VCPU, NO_GPU)), Collections.emptyMap());
    }

    @Test
    public void runLaunchAllowedForRunAdmin() {
        final PipelineUser runAdmin = regularUser();
        runAdmin.setRoles(Collections.singletonList(DefaultRoles.ROLE_RUN_ADMIN.getRole()));
        mockCreditsEnforced();
        mockNoActiveRuns();
        doReturn(runAdmin).when(userManager).loadByNameOrId(OWNER);
        service.checkCreditsForRunLaunch(OWNER, Optional.of(offer(VCPU, NO_GPU)), Collections.emptyMap());
    }

    // -------------------------------------------------------------------------
    // checkCreditsForRunLaunch — plain run
    // -------------------------------------------------------------------------

    @Test
    public void runLaunchAllowedWhenSufficient() {
        // balance=2000, no active runs, required=4
        mockUser();
        mockWeights();
        mockBalance(BALANCE);
        mockNoActiveRuns();
        service.checkCreditsForRunLaunch(OWNER, Optional.of(offer(VCPU, NO_GPU)), Collections.emptyMap());
    }

    @Test
    public void runLaunchAllowedOnExactFit() {
        // balance=100, active run uses 60, required=40 → 100-60=40 ≥ 40
        final int currentBalance = 100;
        final int activeRunUsesCpus = 60;
        final int requiredCpus = 40;

        mockUser();
        mockWeights();
        mockBalance(currentBalance);
        mockActiveRuns(plainRun(RUN_ID_1, INSTANCE_TYPE, REGION_ID));
        doReturn(Optional.of(offer(activeRunUsesCpus, NO_GPU)))
                .when(instanceOfferManager).findOffer(eq(INSTANCE_TYPE), eq(REGION_ID));
        service.checkCreditsForRunLaunch(OWNER, Optional.of(offer(requiredCpus, NO_GPU)), Collections.emptyMap());
    }

    @Test
    public void runLaunchBlockedWhenInsufficient() {
        // balance=100, active run uses 60, required=50 → 100-60=40 < 50
        final int currentBalance = 100;
        final int activeRunUsesCpus = 60;
        final int requiredCpus = 50;

        mockUser();
        mockWeights();
        mockBalance(currentBalance);
        mockActiveRuns(plainRun(RUN_ID_1, INSTANCE_TYPE, REGION_ID));
        doReturn(Optional.of(offer(activeRunUsesCpus, NO_GPU)))
                .when(instanceOfferManager).findOffer(eq(INSTANCE_TYPE), eq(REGION_ID));
        assertThrows(InsufficientUsageCreditsException.class, () ->
                service.checkCreditsForRunLaunch(OWNER, Optional.of(offer(requiredCpus, NO_GPU)),
                        Collections.emptyMap()));
    }

    @Test
    public void runLaunchUsesDefaultWhenNoBalanceRow() {
        mockUser();
        mockWeights();
        doReturn(Optional.empty()).when(crudService).findByUserId(USER_ID);
        doReturn(DEFAULT_BALANCE).when(preferenceManager).getPreference(SystemPreferences.USAGE_CREDITS_DEFAULT);
        mockNoActiveRuns();
        // default=2000, required=4 → allowed
        service.checkCreditsForRunLaunch(OWNER, Optional.of(offer(VCPU, NO_GPU)), Collections.emptyMap());
    }

    @Test
    public void runLaunchBlockedByGpuWeight() {
        // 1 GPU * 100 = 100 credits required; balance=90 → blocked
        final int currentBalance = 90;

        mockUser();
        mockWeights();
        mockBalance(currentBalance);
        mockNoActiveRuns();
        assertThrows(InsufficientUsageCreditsException.class, () ->
                service.checkCreditsForRunLaunch(OWNER, Optional.of(offer(NO_VCPU, ONE_GPU)),
                        Collections.emptyMap()));
    }

    // -------------------------------------------------------------------------
    // checkCreditsForRunLaunch — capacity block params
    // -------------------------------------------------------------------------

    @Test
    public void runLaunchUsesRequestedCpuForCapacityBlock() {
        // catalogue offer has 96 CPUs, but run requests only 4 → required=4, balance=10 → allowed
        final int currentBalance = 10;
        final int requiredCpus = 96;

        mockUser();
        mockWeights();
        mockBalance(currentBalance);
        mockNoActiveRuns();
        service.checkCreditsForRunLaunch(OWNER, Optional.of(offer(requiredCpus, NO_GPU)), params("4", null));
    }

    @Test
    public void runLaunchUsesRequestedGpuForCapacityBlock() {
        // catalogue offer has 8 GPUs = 800 credits, run requests 1 GPU = 100; balance=150 → allowed
        final int currentBalance = 150;
        final int offerRequestedGpus = 8;

        mockUser();
        mockWeights();
        mockBalance(currentBalance);
        mockNoActiveRuns();
        service.checkCreditsForRunLaunch(OWNER, Optional.of(offer(NO_VCPU, offerRequestedGpus)),
                params(null, "1"));
    }

    @Test
    public void runLaunchBlockedWhenCbRequestExceedsBalance() {
        // requests cpu=2 (cost=2) + gpu=1 (cost=100) = 102; balance=100 → blocked
        final int currentBalance = 100;
        final int offerRequestedCpus = 96;
        final int offerRequestedGpus = 8;
        final String cpuRequest = "2";
        final String gpuRequest = "1";

        mockUser();
        mockWeights();
        mockBalance(currentBalance);
        mockNoActiveRuns();
        assertThrows(InsufficientUsageCreditsException.class, () -> service.checkCreditsForRunLaunch(OWNER,
                Optional.of(offer(offerRequestedCpus, offerRequestedGpus)), params(cpuRequest, gpuRequest)));
    }

    @Test
    public void runLaunchAllowedWhenCbParamsBothZero() {
        // both params present but value=0 → required=0 → always allowed
        final int currentBalance = 0;
        final int offerRequestedCpus = 96;
        final int offerRequestedGpus = 8;

        mockUser();
        mockWeights();
        mockBalance(currentBalance);
        mockNoActiveRuns();
        service.checkCreditsForRunLaunch(OWNER, Optional.of(offer(offerRequestedCpus, offerRequestedGpus)),
                params("0", "0"));
    }

    @Test
    public void runLaunchTreatsNonNumericCbParamAsZero() {
        // non-numeric gpu → treated as 0; cpu=4 → required=4, balance=10 → allowed
        final int currentBalance = 10;
        final int offerRequestedCpus = 96;
        final int offerRequestedGpus = 8;

        mockUser();
        mockWeights();
        mockBalance(currentBalance);
        mockNoActiveRuns();
        service.checkCreditsForRunLaunch(OWNER, Optional.of(offer(offerRequestedCpus, offerRequestedGpus)),
                params("4", "notanumber"));
    }

    @Test
    public void runLaunchUsesFullOfferWhenNoParams() {
        // no CB params → full offer (96 CPUs = 96 credits), balance=10 → blocked
        final int currentBalance = 10;
        final int offerRequestedCpus = 96;

        mockUser();
        mockWeights();
        mockBalance(currentBalance);
        mockNoActiveRuns();
        assertThrows(InsufficientUsageCreditsException.class, () ->
                service.checkCreditsForRunLaunch(OWNER, Optional.of(offer(offerRequestedCpus, NO_GPU)),
                        Collections.emptyMap()));
    }

    // -------------------------------------------------------------------------
    // Active-run offer resolution (capacity block vs plain)
    // -------------------------------------------------------------------------

    @Test
    public void activeCapacityBlockRunContributesSyntheticCpuOffer() {
        // active CB run requests 4 CPUs → allocated=4; balance=10, required=8 → blocked (10-4 < 8)
        final int currentBalance = 10;
        final int requiredCpus = 8;
        final String activeRunCpuRequest = "4";

        mockUser();
        mockWeights();
        mockBalance(currentBalance);
        mockActiveRuns(cbRun(RUN_ID_1, activeRunCpuRequest, null));
        assertThrows(InsufficientUsageCreditsException.class, () ->
                service.checkCreditsForRunLaunch(OWNER, Optional.of(offer(requiredCpus, NO_GPU)),
                        Collections.emptyMap()));
    }

    @Test
    public void activeCapacityBlockRunContributesSyntheticGpuOffer() {
        // active CB run requests 1 GPU → allocated=100; balance=150, required=60 → blocked (150-100=50 < 60)
        final int currentBalance = 150;
        final int requiredCpus = 60;
        final String activeRunGpuRequest = "1";

        mockUser();
        mockWeights();
        mockBalance(currentBalance);
        mockActiveRuns(cbRun(RUN_ID_1, null, activeRunGpuRequest));
        assertThrows(InsufficientUsageCreditsException.class, () ->
                service.checkCreditsForRunLaunch(OWNER, Optional.of(offer(requiredCpus, NO_GPU)),
                        Collections.emptyMap()));
    }

    @Test
    public void activeCapacityBlockRunWithZeroParamsIsSkipped() {
        // CB run with cpu=0, gpu=0 → contributes nothing; allocated=0, required=4, balance=10 → allowed
        final int currentBalance = 10;
        final String zeroCpuRequest = "0";
        final String zeroGpuRequest = "0";

        mockUser();
        mockWeights();
        mockBalance(currentBalance);
        mockActiveRuns(cbRun(RUN_ID_1, zeroCpuRequest, zeroGpuRequest));
        service.checkCreditsForRunLaunch(OWNER, Optional.of(offer(VCPU, NO_GPU)), Collections.emptyMap());
    }

    @Test
    public void activeRunWithNoInstanceIsSkipped() {
        final int currentBalance = 10;

        mockUser();
        mockWeights();
        mockBalance(currentBalance);
        final PipelineRun run = new PipelineRun();
        run.setId(RUN_ID_1);
        run.setOwner(OWNER);
        mockActiveRuns(run);
        service.checkCreditsForRunLaunch(OWNER, Optional.of(offer(VCPU, NO_GPU)), Collections.emptyMap());
    }

    @Test
    public void mixedActiveRunsUseCorrectOfferPerRun() {
        // plain run: 20 CPU = 20; CB run: requests 4 CPU = 4 → allocated=24
        // balance=30, required=8 → 30-24=6 < 8 → blocked
        final int currentBalance = 30;
        final int requiredCpus = 8;
        final int plainRunAllocatedCpus = 20;
        final String cbRunCpuRequest = "4";

        mockUser();
        mockWeights();
        mockBalance(currentBalance);
        mockActiveRuns(
                plainRun(RUN_ID_1, INSTANCE_TYPE, REGION_ID),
                cbRun(RUN_ID_2, cbRunCpuRequest, null));
        doReturn(Optional.of(offer(plainRunAllocatedCpus, NO_GPU)))
                .when(instanceOfferManager).findOffer(eq(INSTANCE_TYPE), eq(REGION_ID));
        assertThrows(InsufficientUsageCreditsException.class, () ->
                service.checkCreditsForRunLaunch(OWNER, Optional.of(offer(requiredCpus, NO_GPU)),
                        Collections.emptyMap()));
    }

    // -------------------------------------------------------------------------
    // hasCreditsToLaunchRun (autoscale path — returns boolean, uses excludeRunId)
    // -------------------------------------------------------------------------

    @Test
    public void hasCreditsReturnsTrueWhenSufficient() {
        final int currentBalance = 100;

        mockUser();
        mockWeights();
        mockBalance(currentBalance);
        mockNoActiveRuns();
        doReturn(Optional.of(offer(VCPU, NO_GPU)))
                .when(instanceOfferManager).findOffer(eq(INSTANCE_TYPE), eq(REGION_ID));
        assertTrue(service.hasCreditsToLaunchRun(OWNER, instanceRequest(INSTANCE_TYPE, REGION_ID), null));
    }

    @Test
    public void hasCreditsReturnsFalseWhenInsufficient() {
        final int currentBalance = 10;
        final int requestedCpus = 96;

        mockUser();
        mockWeights();
        mockBalance(currentBalance);
        mockNoActiveRuns();
        doReturn(Optional.of(offer(requestedCpus, NO_GPU)))
                .when(instanceOfferManager).findOffer(eq(INSTANCE_TYPE), eq(REGION_ID));
        assertFalse(service.hasCreditsToLaunchRun(OWNER, instanceRequest(INSTANCE_TYPE, REGION_ID), null));
    }

    @Test
    public void hasCreditsReturnsTrueWhenOfferNotFound() {
        doReturn(Optional.empty()).when(instanceOfferManager).findOffer(any(), any());
        // unknown instance type → allowed
        assertTrue(service.hasCreditsToLaunchRun(OWNER, instanceRequest(INSTANCE_TYPE, REGION_ID), null));
    }

    @Test
    public void hasCreditsExcludesSpecifiedRunFromAllocation() {
        // run1 costs 60, run2 costs 20; exclude run1 → allocated=20, required=50, balance=100 → allowed
        final int currentBalance = 100;
        final int masterCpus = 60;
        final int run2Cpus = 20;
        final int newWorkerRequestedCpus = 50;
        final String newWorkerInstanceType = "target";

        mockUser();
        mockWeights();
        mockBalance(currentBalance);
        mockActiveRuns(
                plainRun(RUN_ID_1, INSTANCE_TYPE, REGION_ID),
                plainRun(RUN_ID_2, INSTANCE_TYPE_2, REGION_ID));
        doReturn(Optional.of(offer(masterCpus, NO_GPU)))
                .when(instanceOfferManager).findOffer(eq(INSTANCE_TYPE), eq(REGION_ID));
        doReturn(Optional.of(offer(run2Cpus, NO_GPU)))
                .when(instanceOfferManager).findOffer(eq(INSTANCE_TYPE_2), eq(REGION_ID));
        doReturn(Optional.of(offer(newWorkerRequestedCpus, NO_GPU)))
                .when(instanceOfferManager).findOffer(eq(newWorkerInstanceType), eq(REGION_ID));
        assertTrue(service.hasCreditsToLaunchRun(OWNER, instanceRequest(newWorkerInstanceType, REGION_ID), RUN_ID_1));
    }

    // -------------------------------------------------------------------------
    // checkCreditsForRestartRun
    // -------------------------------------------------------------------------

    @Test
    public void restartRunAllowedWhenSufficient() {
        // balance=100, no active runs, required=4 CPUs → allowed
        final int currentBalance = 100;

        mockUser();
        mockWeights();
        mockBalance(currentBalance);
        mockNoActiveRuns();
        doReturn(Optional.of(offer(VCPU, NO_GPU)))
                .when(instanceOfferManager).findOffer(eq(INSTANCE_TYPE), eq(REGION_ID));
        service.checkCreditsForRestartRun(plainRun(RUN_ID_1, INSTANCE_TYPE, REGION_ID));
    }

    @Test
    public void restartRunExcludesRestartedRunFromAllocation() {
        // balance=100, the run being restarted costs 60 CPUs and is still RUNNING in the DB
        // without exclusion: allocated=60 + required=60 → needs 120 > 100 → wrongly blocked
        // with exclusion:    allocated=0  + required=60 → needs 60  ≤ 100 → allowed
        final int currentBalance = 100;
        final int runCpus = 60;

        mockUser();
        mockWeights();
        mockBalance(currentBalance);
        mockActiveRuns(plainRun(RUN_ID_1, INSTANCE_TYPE, REGION_ID));
        doReturn(Optional.of(offer(runCpus, NO_GPU)))
                .when(instanceOfferManager).findOffer(eq(INSTANCE_TYPE), eq(REGION_ID));
        service.checkCreditsForRestartRun(plainRun(RUN_ID_1, INSTANCE_TYPE, REGION_ID));
    }

    @Test
    public void restartRunBlockedWhenInsufficient() {
        // balance=10, required=96 CPUs → 10 < 96 → blocked
        final int currentBalance = 10;
        final int requiredCpus = 96;

        mockUser();
        mockWeights();
        mockBalance(currentBalance);
        mockNoActiveRuns();
        doReturn(Optional.of(offer(requiredCpus, NO_GPU)))
                .when(instanceOfferManager).findOffer(eq(INSTANCE_TYPE), eq(REGION_ID));
        assertThrows(InsufficientUsageCreditsException.class, () ->
                service.checkCreditsForRestartRun(plainRun(RUN_ID_1, INSTANCE_TYPE, REGION_ID)));
    }

    @Test
    public void restartRunAllowedWhenOfferNotFound() {
        // unknown instance type → offer absent → no-op
        doReturn(Optional.empty()).when(instanceOfferManager).findOffer(any(), any());
        service.checkCreditsForRestartRun(plainRun(RUN_ID_1, INSTANCE_TYPE, REGION_ID));
    }

    @Test
    public void restartRunUsesRequestedCpuForCapacityBlock() {
        // CB run requests 4 CPUs; balance=10 → allowed (full catalogue offer of 96 would be blocked)
        final int currentBalance = 10;
        final String cpuRequest = "4";

        mockUser();
        mockWeights();
        mockBalance(currentBalance);
        mockNoActiveRuns();
        service.checkCreditsForRestartRun(cbRun(RUN_ID_1, cpuRequest, null));
    }

    @Test
    public void restartRunBlockedForCapacityBlockWhenInsufficient() {
        // CB run requests 2 CPUs + 1 GPU = 2 + 100 = 102; balance=100 → blocked
        final int currentBalance = 100;
        final String cpuRequest = "2";
        final String gpuRequest = "1";

        mockUser();
        mockWeights();
        mockBalance(currentBalance);
        mockNoActiveRuns();
        assertThrows(InsufficientUsageCreditsException.class, () ->
                service.checkCreditsForRestartRun(cbRun(RUN_ID_1, cpuRequest, gpuRequest)));
    }

    @Test
    public void restartRunAllowedWhenModeIsOff() {
        // mode OFF short-circuits inside checkGroups; offer is still resolved before that
        final int anyLargeOffer = 96;

        doReturn(PlatformUsageCreditsMode.OFF).when(preferenceManager)
                .getPreference(SystemPreferences.USAGE_CREDITS_MODE);
        doReturn(Optional.of(offer(anyLargeOffer, NO_GPU)))
                .when(instanceOfferManager).findOffer(eq(INSTANCE_TYPE), eq(REGION_ID));
        service.checkCreditsForRestartRun(plainRun(RUN_ID_1, INSTANCE_TYPE, REGION_ID));
    }

    // -------------------------------------------------------------------------
    // checkHomogeneousClusterCredits
    // -------------------------------------------------------------------------

    @Test
    public void homogeneousClusterAllowedWhenSufficient() {
        // balance=200, no active runs, required=3 replicas * 4 CPUs = 12
        final int currentBalance = 200;
        final int requestedCpus = 4;

        mockUser();
        mockWeights();
        mockBalance(currentBalance);
        mockNoActiveRuns();
        doReturn(Optional.of(offer(requestedCpus, NO_GPU)))
                .when(instanceOfferManager).findOffer(eq(INSTANCE_TYPE), eq(REGION_ID));
        service.checkHomogeneousClusterCredits(OWNER, config(INSTANCE_TYPE, REGION_ID, 2));
    }

    @Test
    public void homogeneousClusterBlockedWhenInsufficient() {
        // balance=50, required=3 * 20 = 60 > 50
        final int currentBalance = 50;
        final int requestedCpus = 20;

        mockUser();
        mockWeights();
        mockBalance(currentBalance);
        mockNoActiveRuns();
        doReturn(Optional.of(offer(requestedCpus, NO_GPU)))
                .when(instanceOfferManager).findOffer(eq(INSTANCE_TYPE), eq(REGION_ID));
        assertThrows(InsufficientUsageCreditsException.class, () ->
                service.checkHomogeneousClusterCredits(OWNER, config(INSTANCE_TYPE, REGION_ID, 2)));
    }

    @Test
    public void homogeneousClusterSkippedWhenNodeCountZero() {
        final int currentBalance = 0;

        mockUser();
        mockWeights();
        mockBalance(currentBalance);
        mockNoActiveRuns();
        // nodeCount=0 → check skipped → no exception
        service.checkHomogeneousClusterCredits(OWNER, config(INSTANCE_TYPE, REGION_ID, 0));
    }

    // -------------------------------------------------------------------------
    // getAllocatedCredits
    // -------------------------------------------------------------------------

    @Test
    public void getAllocatedCreditsReturnsZeroWhenNoActiveRuns() {
        final int expectedBalance = 0;

        mockWeights();
        mockNoActiveRuns();
        assertThat(service.getAllocatedCredits(OWNER), is(expectedBalance));
    }

    @Test
    public void getAllocatedCreditsSumsPlainActiveRuns() {
        final int run1Cpus = 4;
        final int run2Cpus = 2;
        final int expectedBalance = 6;

        mockWeights();
        mockActiveRuns(
                plainRun(RUN_ID_1, INSTANCE_TYPE, REGION_ID),
                plainRun(RUN_ID_2, INSTANCE_TYPE_2, REGION_ID));
        doReturn(Optional.of(offer(run1Cpus, NO_GPU)))
                .when(instanceOfferManager).findOffer(eq(INSTANCE_TYPE), eq(REGION_ID));
        doReturn(Optional.of(offer(run2Cpus, NO_GPU)))
                .when(instanceOfferManager).findOffer(eq(INSTANCE_TYPE_2), eq(REGION_ID));
        // 4 + 2 = 6
        assertThat(service.getAllocatedCredits(OWNER), is(expectedBalance));
    }

    @Test
    public void getAllocatedCreditsSumsCbActiveRuns() {
        final int expectedAllocated = 104;
        final String cpuRequest = "4";
        final String gpuRequest = "1";

        mockWeights();
        mockActiveRuns(cbRun(RUN_ID_1, cpuRequest, gpuRequest));
        // 4 CPU * 1 + 1 GPU * 100 = 104
        assertThat(service.getAllocatedCredits(OWNER), is(expectedAllocated));
    }

    // -------------------------------------------------------------------------
    // Helpers — mocking
    // -------------------------------------------------------------------------

    private void mockCreditsEnforced() {
        doReturn(PlatformUsageCreditsMode.ON).when(preferenceManager)
                .getPreference(SystemPreferences.USAGE_CREDITS_MODE);
    }

    private void mockUser() {
        mockCreditsEnforced();
        doReturn(regularUser()).when(userManager).loadByNameOrId(OWNER);
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

    private void mockNoActiveRuns() {
        doReturn(Collections.emptyList()).when(pipelineRunCRUDService)
                .loadRunsByStatusesAndOwner(any(), eq(OWNER));
    }

    private void mockActiveRuns(final PipelineRun... runs) {
        doReturn(Arrays.asList(runs)).when(pipelineRunCRUDService)
                .loadRunsByStatusesAndOwner(any(), eq(OWNER));
    }

    // -------------------------------------------------------------------------
    // Helpers — builders
    // -------------------------------------------------------------------------

    private static PipelineRun plainRun(final long id, final String instanceType, final Long regionId) {
        final PipelineRun run = new PipelineRun();
        run.setId(id);
        run.setOwner(OWNER);
        final RunInstance instance = new RunInstance();
        instance.setNodeType(instanceType);
        instance.setCloudRegionId(regionId);
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
        run.setInstance(instance);
        final List<PipelineRunParameter> paramList = new java.util.ArrayList<>();
        if (cpuRequest != null) {
            paramList.add(new PipelineRunParameter(
                    PlatformUsageCreditsLaunchService.CP_CAP_REQUESTS_CPU, cpuRequest));
        }
        if (gpuRequest != null) {
            paramList.add(new PipelineRunParameter(
                    PlatformUsageCreditsLaunchService.CP_CAP_REQUESTS_GPU, gpuRequest));
        }
        run.setPipelineRunParameters(paramList);
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

    private static InstanceRequest instanceRequest(final String instanceType, final Long regionId) {
        final RunInstance instance = new RunInstance();
        instance.setNodeType(instanceType);
        instance.setCloudRegionId(regionId);
        final InstanceRequest req = new InstanceRequest();
        req.setInstance(instance);
        return req;
    }

    private static PipelineConfiguration config(final String instanceType, final Long regionId,
                                                final int nodeCount) {
        final PipelineConfiguration config = new PipelineConfiguration();
        config.setInstanceType(instanceType);
        config.setCloudRegionId(regionId);
        config.setNodeCount(nodeCount == 0 ? null : nodeCount);
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
