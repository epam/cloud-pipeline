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

import com.epam.pipeline.dto.credits.PlatformUsageCreditsMode;
import com.epam.pipeline.dto.credits.PlatformUsageCreditsUserBalance;
import com.epam.pipeline.entity.cluster.InstanceOffer;
import com.epam.pipeline.entity.credits.ClusterReplicaGroup;
import com.epam.pipeline.entity.credits.PlatformUsageCreditsCheckResult;
import com.epam.pipeline.entity.user.DefaultRoles;
import com.epam.pipeline.entity.user.PipelineUser;
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

import static java.util.Arrays.asList;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class PlatformUsageCreditsLaunchServiceTest {

    private static final String OWNER = "user1";
    private static final long USER_ID = 1L;
    private static final int BALANCE = 2000;
    private static final int DEFAULT_BALANCE = 2000;
    private static final int CPU_WEIGHT = 1;
    private static final int GPU_WEIGHT = 100;

    private final PreferenceManager preferenceManager = mock(PreferenceManager.class);
    private final PlatformUsageCreditsUserBalanceCRUDService crudService =
            mock(PlatformUsageCreditsUserBalanceCRUDService.class);
    private final UserManager userManager = mock(UserManager.class);
    private final PlatformUsageCreditsLaunchService service = new PlatformUsageCreditsLaunchService(
            preferenceManager, crudService, userManager);

    // --- mode short-circuit ---

    @Test
    public void checkCreditsReturnsTrueWhenModeIsOff() {
        doReturn(PlatformUsageCreditsMode.OFF).when(preferenceManager)
                .getPreference(SystemPreferences.USAGE_CREDITS_MODE);

        assertThat(service.checkCreditsForRunLaunch(OWNER, offer(4, 0), Collections.emptyList()).isOk(), is(true));
    }

    @Test
    public void checkCreditsReturnsTrueWhenModeIsBalanceOnly() {
        doReturn(PlatformUsageCreditsMode.BALANCE_ONLY).when(preferenceManager)
                .getPreference(SystemPreferences.USAGE_CREDITS_MODE);

        assertThat(service.checkCreditsForRunLaunch(OWNER, offer(4, 0), Collections.emptyList()).isOk(), is(true));
    }

    // --- checkCreditsForRunLaunch ---

    @Test
    public void checkCreditsReturnsTrueForAdmin() {
        final PipelineUser admin = regularUser();
        admin.setAdmin(true);
        mockCreditsEnforced();
        doReturn(admin).when(userManager).loadByNameOrId(OWNER);

        assertThat(service.checkCreditsForRunLaunch(OWNER, offer(4, 0), Collections.emptyList()).isOk(), is(true));
    }

    @Test
    public void checkCreditsReturnsTrueForRunAdmin() {
        final PipelineUser runAdmin = regularUser();
        runAdmin.setRoles(Collections.singletonList(DefaultRoles.ROLE_RUN_ADMIN.getRole()));
        mockCreditsEnforced();
        doReturn(runAdmin).when(userManager).loadByNameOrId(OWNER);

        assertThat(service.checkCreditsForRunLaunch(OWNER, offer(4, 0), Collections.emptyList()).isOk(), is(true));
    }

    @Test
    public void checkCreditsReturnsTrueWhenSufficient() {
        // balance=2000, no active runs, required=4 CPUs * 1 = 4
        mockUser();
        mockWeights();
        mockBalance(BALANCE);

        assertThat(service.checkCreditsForRunLaunch(OWNER, offer(4, 0), Collections.emptyList()).isOk(), is(true));
    }

    @Test
    public void checkCreditsReturnsTrueOnExactFit() {
        // balance=100, active runs use 60 CPU credits, required=40
        mockUser();
        mockWeights();
        mockBalance(100);

        assertThat(service.checkCreditsForRunLaunch(OWNER, offer(40, 0),
                Collections.singletonList(offer(60, 0))).isOk(), is(true));
    }

    @Test
    public void checkCreditsReturnsFalseWhenInsufficient() {
        // balance=100, active runs use 60, required=50 → available=40 < 50
        mockUser();
        mockWeights();
        mockBalance(100);

        assertThat(service.checkCreditsForRunLaunch(OWNER, offer(50, 0),
                Collections.singletonList(offer(60, 0))).isOk(), is(false));
    }

    @Test
    public void checkCreditsUsesDefaultWhenNoBalanceRow() {
        mockUser();
        mockWeights();
        doReturn(Optional.empty()).when(crudService).findByUserId(USER_ID);
        doReturn(DEFAULT_BALANCE).when(preferenceManager).getPreference(SystemPreferences.USAGE_CREDITS_DEFAULT);

        // default=2000, required=4 → allowed
        assertThat(service.checkCreditsForRunLaunch(OWNER, offer(4, 0), Collections.emptyList()).isOk(), is(true));
    }

    @Test
    public void checkCreditsAppliesGpuWeightCorrectly() {
        // 1 GPU * 100 = 100 credits required; balance=90 → blocked
        mockUser();
        mockWeights();
        mockBalance(90);

        assertThat(service.checkCreditsForRunLaunch(OWNER, offer(0, 1), Collections.emptyList()).isOk(), is(false));
    }

    @Test
    public void checkCreditsResultContainsCorrectValues() {
        // balance=100, allocated=60 (active), required=50 → available=40, blocked
        mockUser();
        mockWeights();
        mockBalance(100);

        final PlatformUsageCreditsCheckResult result = service.checkCreditsForRunLaunch(OWNER, offer(50, 0),
                Collections.singletonList(offer(60, 0)));

        assertThat(result.isOk(), is(false));
        assertThat(result.getRequired(), is(50));
        assertThat(result.getBalance(), is(100));
        assertThat(result.getAllocated(), is(60));
    }

    // --- checkCreditsForClusterLaunch ---

    @Test
    public void clusterCheckReturnsTrueWhenSufficient() {
        // balance=200, no active runs, required=3 replicas * 4 CPUs = 12
        mockUser();
        mockWeights();
        mockBalance(200);

        assertThat(service.checkCreditsForClusterLaunch(OWNER, offer(4, 0), 3, Collections.emptyList()).isOk(),
                is(true));
    }

    @Test
    public void clusterCheckReturnsFalseWhenInsufficient() {
        // balance=50, no active runs, required=3 * 20 = 60 > 50
        mockUser();
        mockWeights();
        mockBalance(50);

        assertThat(service.checkCreditsForClusterLaunch(OWNER, offer(20, 0), 3, Collections.emptyList()).isOk(),
                is(false));
    }

    @Test
    public void clusterCheckResultContainsCorrectValues() {
        // balance=100, no active, required=3 * 10 = 30
        mockUser();
        mockWeights();
        mockBalance(100);

        final PlatformUsageCreditsCheckResult result = service.checkCreditsForClusterLaunch(OWNER, offer(10, 0), 3,
                Collections.emptyList());

        assertThat(result.isOk(), is(true));
        assertThat(result.getRequired(), is(30));
        assertThat(result.getBalance(), is(100));
        assertThat(result.getAllocated(), is(0));
    }

    // --- checkCreditsForClusterLaunch (group-based / heterogeneous) ---

    @Test
    public void heterogeneousClusterSumsGroupCosts() {
        // group1: 1 master * (4 CPU = 4 credits) = 4
        // group2: 2 workers * (0 CPU + 1 GPU = 100 credits each) = 200
        // total required = 204; balance=300 → allowed
        mockUser();
        mockWeights();
        mockBalance(300);

        final List<ClusterReplicaGroup> groups = asList(
                new ClusterReplicaGroup(offer(4, 0), 1),
                new ClusterReplicaGroup(offer(0, 1), 2));

        assertThat(service.checkCreditsForClusterLaunch(OWNER, groups, Collections.emptyList()).isOk(), is(true));
    }

    @Test
    public void heterogeneousClusterBlockedWhenExpensiveChildExceedsBudget() {
        // group1: 1 master * (2 CPU = 2 credits) = 2
        // group2: 3 workers * (0 CPU + 1 GPU = 100 each) = 300
        // total = 302; balance=200 → blocked
        mockUser();
        mockWeights();
        mockBalance(200);

        final List<ClusterReplicaGroup> groups = asList(
                new ClusterReplicaGroup(offer(2, 0), 1),
                new ClusterReplicaGroup(offer(0, 1), 3));

        assertThat(service.checkCreditsForClusterLaunch(OWNER, groups, Collections.emptyList()).isOk(), is(false));
    }

    @Test
    public void heterogeneousClusterResultContainsCorrectValues() {
        // group1: 1 * 10 CPU = 10; group2: 2 * 5 CPU = 10; total = 20; balance=50, allocated=0
        mockUser();
        mockWeights();
        mockBalance(50);

        final List<ClusterReplicaGroup> groups = asList(
                new ClusterReplicaGroup(offer(10, 0), 1),
                new ClusterReplicaGroup(offer(5, 0), 2));

        final PlatformUsageCreditsCheckResult result = service.checkCreditsForClusterLaunch(OWNER, groups, Collections.emptyList());

        assertThat(result.isOk(), is(true));
        assertThat(result.getRequired(), is(20));
        assertThat(result.getBalance(), is(50));
        assertThat(result.getAllocated(), is(0));
    }

    @Test
    public void heterogeneousClusterMissingGroupOfferSkipped() {
        // Only one group resolves an offer → cost = 1 * 4 = 4; balance=10 → allowed
        mockUser();
        mockWeights();
        mockBalance(10);

        final List<ClusterReplicaGroup> groups = Collections.singletonList(
                new ClusterReplicaGroup(offer(4, 0), 1));

        assertThat(service.checkCreditsForClusterLaunch(OWNER, groups, Collections.emptyList()).isOk(), is(true));
    }

    // --- getAllocatedCredits ---

    @Test
    public void getAllocatedCreditsReturnsZeroForEmpty() {
        mockWeights();
        assertThat(service.getAllocatedCredits(Collections.emptyList()), is(0));
    }

    @Test
    public void getAllocatedCreditsSumsMultipleOffers() {
        mockWeights();
        // 2 CPU + 1 GPU = 2*1 + 1*100 = 102
        // 4 CPU = 4*1 = 4
        // total = 106
        final List<InstanceOffer> offers = Arrays.asList(offer(2, 1), offer(4, 0));
        assertThat(service.getAllocatedCredits(offers), is(106));
    }

    // --- helpers ---

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

    private static InstanceOffer offer(final int vcpu, final int gpu) {
        final InstanceOffer o = new InstanceOffer();
        o.setVCPU(vcpu);
        o.setGpu(gpu);
        return o;
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
