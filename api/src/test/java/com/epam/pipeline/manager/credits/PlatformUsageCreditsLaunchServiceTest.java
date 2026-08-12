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
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.pipeline.run.parameter.PipelineRunParameter;
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
    private static final String ORIGINAL_OWNER = "initiatorUser";
    private static final long USER_ID = 1L;
    private static final long ORIGINAL_OWNER_ID = 2L;
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
    private static final int NO_GPU = 0;

    // credit balances
    private static final int BALANCE_2000 = 2000;
    private static final int BALANCE_200 = 200;
    private static final int BALANCE_100 = 100;
    private static final int BALANCE_50 = 50;
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
    // checkCreditsForRun
    // =========================================================================

    @Test
    public void checkRunAllowedWhenModeIsOff() {
        doReturn(PlatformUsageCreditsMode.OFF).when(preferenceManager)
                .getPreference(SystemPreferences.USAGE_CREDITS_MODE);
        service.checkCreditsForRun(config(INSTANCE_TYPE, REGION_ID, null, null));
    }

    @Test
    public void checkRunAllowedForAdmin() {
        final PipelineUser admin = regularUser();
        admin.setAdmin(true);
        mockCurrentUser(admin);
        mockCreditsOn();
        service.checkCreditsForRun(config(INSTANCE_TYPE, REGION_ID, null, null));
    }

    @Test
    public void checkRunSkippedWhenInstanceNotInCatalogue() {
        mockUser();
        mockWeights();
        mockNoActiveRuns();
        doReturn(Optional.empty()).when(instanceOfferManager).findOffer(eq(INSTANCE_TYPE), eq(REGION_ID));
        service.checkCreditsForRun(config(INSTANCE_TYPE, REGION_ID, null, null));
    }

    @Test
    public void checkRunPlainAllowedWhenSufficient() {
        mockUser();
        mockWeights();
        mockBalance(BALANCE_2000);
        mockNoActiveRuns();
        doReturn(Optional.of(offer(VCPU_4, NO_GPU)))
                .when(instanceOfferManager).findOffer(eq(INSTANCE_TYPE), eq(REGION_ID));
        service.checkCreditsForRun(config(INSTANCE_TYPE, REGION_ID, null, null));
    }

    @Test
    public void checkRunPlainBlockedWhenInsufficient() {
        // balance=10, required=20 CPUs → blocked
        mockUser();
        mockWeights();
        mockBalance(BALANCE_10);
        mockNoActiveRuns();
        doReturn(Optional.of(offer(VCPU_20, NO_GPU)))
                .when(instanceOfferManager).findOffer(eq(INSTANCE_TYPE), eq(REGION_ID));
        assertThrows(InsufficientUsageCreditsException.class, () ->
                service.checkCreditsForRun(config(INSTANCE_TYPE, REGION_ID, null, null)));
    }

    @Test
    public void checkRunClusterAllowedWhenSufficient() {
        // 3 replicas (1 master + 2 workers) * 4 CPUs = 12; balance=200 → allowed
        mockUser();
        mockWeights();
        mockBalance(BALANCE_200);
        mockNoActiveRuns();
        doReturn(Optional.of(offer(VCPU_4, NO_GPU)))
                .when(instanceOfferManager).findOffer(eq(INSTANCE_TYPE), eq(REGION_ID));
        service.checkCreditsForRun(config(INSTANCE_TYPE, REGION_ID, TWO_WORKERS, null));
    }

    @Test
    public void checkRunClusterBlockedWhenInsufficient() {
        // 3 replicas * 20 CPUs = 60; balance=50 → blocked
        mockUser();
        mockWeights();
        mockBalance(BALANCE_50);
        mockNoActiveRuns();
        doReturn(Optional.of(offer(VCPU_20, NO_GPU)))
                .when(instanceOfferManager).findOffer(eq(INSTANCE_TYPE), eq(REGION_ID));
        assertThrows(InsufficientUsageCreditsException.class, () ->
                service.checkCreditsForRun(config(INSTANCE_TYPE, REGION_ID, TWO_WORKERS, null)));
    }

    @Test
    public void checkRunZeroNodeCountTreatedAsPlainRun() {
        // nodeCount=0 → plain run, required=4, balance=200 → allowed
        mockUser();
        mockWeights();
        mockBalance(BALANCE_200);
        mockNoActiveRuns();
        doReturn(Optional.of(offer(VCPU_4, NO_GPU)))
                .when(instanceOfferManager).findOffer(eq(INSTANCE_TYPE), eq(REGION_ID));
        service.checkCreditsForRun(config(INSTANCE_TYPE, REGION_ID, ZERO_WORKERS, null));
    }

    @Test
    public void checkRunFallbackMoreExpensiveBlocks() {
        // primary=4 CPUs, fallback=60 CPUs; balance=10 → worst-case=60 → blocked
        mockUser();
        mockWeights();
        mockBalance(BALANCE_10);
        mockNoActiveRuns();
        doReturn(Optional.of(offer(VCPU_4, NO_GPU)))
                .when(instanceOfferManager).findOffer(eq(INSTANCE_TYPE), eq(REGION_ID));
        doReturn(Optional.of(offer(VCPU_60, NO_GPU)))
                .when(instanceOfferManager).findOffer(eq(FALLBACK_INSTANCE_TYPE), eq(REGION_ID));
        assertThrows(InsufficientUsageCreditsException.class, () ->
                service.checkCreditsForRun(config(INSTANCE_TYPE, REGION_ID, null,
                        Collections.singletonList(FALLBACK_INSTANCE_TYPE))));
    }

    @Test
    public void checkRunCapacityBlockUsesRequestedCpu() {
        // catalogue has 96 CPUs but run requests only 4 → cost=4, balance=10 → allowed
        mockUser();
        mockWeights();
        mockBalance(BALANCE_10);
        mockNoActiveRuns();
        service.checkCreditsForRun(configWithParams(INSTANCE_TYPE, REGION_ID, params("4", null)));
    }

    @Test
    public void checkRunUsesOriginalOwnerWhenRunAsParamPresent() {
        // run-as: security context is targetUser, but ORIGINAL_OWNER param says initiatorUser
        // credits must be checked against initiatorUser, not targetUser
        final PipelineUser initiator = new PipelineUser();
        initiator.setId(ORIGINAL_OWNER_ID);
        initiator.setUserName(ORIGINAL_OWNER);
        initiator.setAdmin(false);
        initiator.setRoles(Collections.emptyList());

        mockCreditsOn();
        doReturn("ORIGINAL_OWNER").when(preferenceManager)
                .getPreference(SystemPreferences.LAUNCH_ORIGINAL_OWNER_PARAMETER);
        doReturn(initiator).when(userManager).loadByNameOrId(ORIGINAL_OWNER);
        mockWeights();
        final PlatformUsageCreditsUserBalance balance = new PlatformUsageCreditsUserBalance();
        balance.setCurrentValue(BALANCE_2000);
        doReturn(Optional.of(balance)).when(crudService).findByUserId(ORIGINAL_OWNER_ID);
        doReturn(Collections.emptyList()).when(pipelineRunCRUDService)
                .loadRunsByStatusesAndOriginalOwner(any(), eq(ORIGINAL_OWNER));
        doReturn(Optional.of(offer(VCPU_4, NO_GPU)))
                .when(instanceOfferManager).findOffer(eq(INSTANCE_TYPE), eq(REGION_ID));

        final Map<String, PipeConfValueVO> parameters = new HashMap<>();
        parameters.put("ORIGINAL_OWNER", new PipeConfValueVO(ORIGINAL_OWNER));
        final PipelineConfiguration config = configWithParams(INSTANCE_TYPE, REGION_ID, parameters);
        config.setCloudRegionId(REGION_ID);
        service.checkCreditsForRun(config);
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

    private void mockNoActiveRuns() {
        doReturn(Collections.emptyList()).when(pipelineRunCRUDService)
                .loadRunsByStatusesAndOriginalOwner(any(), eq(OWNER));
    }

    private void mockActiveRuns(final PipelineRun... runs) {
        doReturn(Arrays.asList(runs)).when(pipelineRunCRUDService)
                .loadRunsByStatusesAndOriginalOwner(any(), eq(OWNER));
    }

    // =========================================================================
    // Helpers — builders
    // =========================================================================

    private static PipelineRun provisionedRun(final long id, final String instanceType, final Long regionId) {
        final PipelineRun run = new PipelineRun();
        run.setId(id);
        run.setOwner(OWNER);
        run.setOriginalOwner(OWNER);
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
        run.setOriginalOwner(OWNER);
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
        run.setOriginalOwner(OWNER);
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
