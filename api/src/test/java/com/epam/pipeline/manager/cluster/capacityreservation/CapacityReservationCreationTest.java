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

package com.epam.pipeline.manager.cluster.capacityreservation;

import com.epam.pipeline.controller.vo.cluster.pool.CapacityReservationRequest;
import com.epam.pipeline.controller.vo.cluster.pool.NodePoolVO;
import com.epam.pipeline.dao.cluster.capacityreservation.CapacityReservationDao;
import com.epam.pipeline.dao.cluster.pool.NodePoolDao;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservation;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservationStatus;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservationType;
import com.epam.pipeline.entity.cluster.AMIConfiguration;
import com.epam.pipeline.entity.cluster.InstanceOffer;
import com.epam.pipeline.entity.cluster.pool.NodePoolLaunchConfig;
import com.epam.pipeline.entity.cluster.pool.NodePool;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.cluster.pool.NodePoolType;
import com.epam.pipeline.entity.preference.Preference;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.entity.region.GCPRegion;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.AbstractManagerTest;
import com.epam.pipeline.manager.cluster.InstanceOfferManager;
import com.epam.pipeline.manager.cluster.KubernetesConstants;
import com.epam.pipeline.manager.cluster.pool.NodePoolManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.region.CloudRegionManager;
import com.epam.pipeline.mapper.cluster.pool.NodePoolMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

@Transactional
public class CapacityReservationCreationTest extends AbstractManagerTest {

    private static final String POOL_NAME = "g5 reserved pool";
    private static final String INSTANCE_TYPE = "g5.48xlarge";
    private static final long REGION_ID = 1L;
    private static final int INSTANCE_DISK = 100;
    private static final int INSTANCE_COUNT = 2;
    private static final long COMMITMENT_DURATION = 14 * 24 * 3600L;
    private static final int LEAD_DAYS = 7;
    private static final int WINDOW_DAYS = 30;
    private static final long ONE_DAY_SECONDS = 24 * 3600L;
    private static final String OWNER = "requester";
    private static final String ADMIN = "ADMIN";
    private static final int CALLER_CPU_RESERVED = 4;
    private static final int OTHER_INSTANCE_COUNT = 5;
    private static final String REGION_CODE = "us-east-1";
    private static final String SUBNET = "subnet-in-zone";
    private static final String RESERVATION_TARGET = "CapacityReservationSpecification";
    private static final String CLOUD_RESERVATION_ID = "cr-0123456789abcdef0";
    private static final String OWN_SPEC_KEY = "IamInstanceProfile";
    private static final String PROVIDER_ANSWER = "the provider had no capacity at that date";
    private static final String OWN_INIT_SCRIPT = "/opt/api/scripts/init_custom.sh";
    private static final int INSTANCE_VCPUS = 192;
    private static final int SMALL_INSTANCE_VCPUS = 8;
    private static final int MIN_VCPUS = 32;
    private static final int AUTOSCALED_MIN_SIZE = 1;
    private static final double SCALE_UP_THRESHOLD = 90.0;
    private static final double SCALE_DOWN_THRESHOLD = 10.0;
    private static final String RENAMED = "renamed pool";
    private static final String ZONE = "us-east-1c";
    private static final long FOREIGN_POOL_ID = 999_999L;
    private static final long FOREIGN_REGION_ID = 999_999L;
    private static final String FOREIGN_OWNER = "intruder";
    private static final String FOREIGN_INSTANCE_TYPE = "p5.48xlarge";

    @Autowired
    private NodePoolManager poolManager;

    @Autowired
    private CapacityReservationDao reservationDao;

    @Autowired
    private NodePoolDao poolDao;

    @Autowired
    private NodePoolMapper poolMapper;

    @Autowired
    private PreferenceManager preferenceManager;

    @Autowired
    private CapacityReservationService reservationService;

    @MockBean
    private CapacityReservationCloudFacade cloudFacade;

    @MockBean
    private CloudRegionManager regionManager;

    @MockBean
    private InstanceOfferManager instanceOfferManager;

    @BeforeEach
    public void setUp() {
        final AwsRegion region = new AwsRegion();
        region.setId(REGION_ID);
        region.setRegionCode(REGION_CODE);
        when(regionManager.load(any())).thenReturn((AbstractCloudRegion) region);
        when(cloudFacade.launchSpecification(any())).thenAnswer(invocation -> Collections.singletonMap(
                RESERVATION_TARGET, ((CapacityReservation) invocation.getArguments()[0]).getCloudReservationId()));
        when(cloudFacade.isSupported(CloudProvider.AWS)).thenReturn(true);
        when(instanceOfferManager.isPriceTypeAllowed(any())).thenReturn(true);
        when(instanceOfferManager.isToolInstanceAllowed(any(), any(), anyBoolean())).thenReturn(true);
        givenVcpusPerInstance(INSTANCE_VCPUS);
    }

    @Test
    @WithMockUser(username = OWNER)
    public void shouldRefuseAReservationInARegionOfAnUnsupportedProvider() {
        final GCPRegion region = new GCPRegion();
        region.setId(REGION_ID);
        when(regionManager.load(any())).thenReturn((AbstractCloudRegion) region);

        assertThatThrownBy(() -> poolManager.create(reservationPoolVO()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not supported in GCP");
        assertThat(poolDao.loadAll().stream().noneMatch(pool -> POOL_NAME.equals(pool.getName()))).isTrue();
    }

    @Test
    @WithMockUser(username = OWNER)
    public void shouldRefuseAReservationBelowTheProvidersVcpuMinimum() {
        givenVcpusPerInstance(SMALL_INSTANCE_VCPUS);

        assertThatThrownBy(() -> poolManager.create(reservationPoolVO()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32");
    }

    @Test
    @WithMockUser(username = OWNER)
    public void shouldAcceptAReservationOfExactlyTheVcpuMinimum() {
        givenVcpusPerInstance(MIN_VCPUS / INSTANCE_COUNT);

        assertThat(poolManager.create(reservationPoolVO()).getId()).isNotNull();
    }

    @Test
    @WithMockUser(username = OWNER)
    public void shouldRefuseAnAutoscaledReservationPool() {
        assertThatThrownBy(() -> poolManager.create(autoscaled(reservationPoolVO())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be autoscaled");
    }

    @Test
    @WithMockUser(username = OWNER)
    public void shouldLeaveAnInstanceTypeWithoutAnOfferToTheProvider() {
        when(instanceOfferManager.findOffer(any(), any())).thenReturn(Optional.empty());

        assertThat(poolManager.create(reservationPoolVO()).getId()).isNotNull();
    }

    private void givenVcpusPerInstance(final int vcpus) {
        when(instanceOfferManager.findOffer(any(), any()))
                .thenReturn(Optional.of(InstanceOffer.builder().vCPU(vcpus).build()));
    }

    @Test
    @WithMockUser(username = OWNER)
    public void shouldPersistPoolAndReservationTogether() {
        final NodePool created = poolManager.create(reservationPoolVO());

        assertThat(created.getId()).isNotNull();
        assertThat(created.isCapacityReservation()).isTrue();
        assertThat(created.getOwner()).isEqualTo(OWNER);

        final Optional<CapacityReservation> reservation = reservationDao.findByNodePoolId(created.getId());
        assertThat(reservation.isPresent()).isTrue();
        assertThat(reservation.get().getNodePoolId()).isEqualTo(created.getId());
        assertThat(reservation.get().getName()).isEqualTo(POOL_NAME);
        assertThat(reservation.get().getRegionId()).isEqualTo(REGION_ID);
        assertThat(reservation.get().getInstanceType()).isEqualTo(INSTANCE_TYPE);
        assertThat(reservation.get().getInstanceCount()).isEqualTo(INSTANCE_COUNT);
        assertThat(reservation.get().getCommitmentDuration()).isEqualTo(COMMITMENT_DURATION);
        assertThat(reservation.get().getOwner()).isEqualTo(OWNER);
        assertThat(reservation.get().getInstancePlatform())
                .isEqualTo(CapacityReservation.DEFAULT_INSTANCE_PLATFORM);
    }

    @Test
    @WithMockUser(username = OWNER)
    public void shouldTakeThePoolsSideOfTheReservationFromThePoolNotTheRequest() {
        final NodePoolVO vo = reservationPoolVO();
        final CapacityReservationRequest request = vo.getCapacityReservationRequest();
        request.setNodePoolId(FOREIGN_POOL_ID);
        request.setName(RENAMED);
        request.setRegionId(FOREIGN_REGION_ID);
        request.setOwner(FOREIGN_OWNER);
        request.setInstanceType(FOREIGN_INSTANCE_TYPE);
        request.setInstanceCount(INSTANCE_COUNT + 1);

        final NodePool created = poolManager.create(vo);

        final CapacityReservation reservation = reservationDao.findByNodePoolId(created.getId())
                .orElseThrow(AssertionError::new);
        assertThat(reservation.getNodePoolId()).isEqualTo(created.getId());
        assertThat(reservation.getName()).isEqualTo(POOL_NAME);
        assertThat(reservation.getRegionId()).isEqualTo(REGION_ID);
        assertThat(reservation.getOwner()).isEqualTo(OWNER);
        assertThat(reservation.getInstanceType()).isEqualTo(INSTANCE_TYPE);
        assertThat(reservation.getInstanceCount()).isEqualTo(INSTANCE_COUNT);
    }

    @Test
    @WithMockUser(username = OWNER)
    public void shouldPersistReservationBackedPoolWithZeroCount() {
        final NodePool created = poolManager.create(reservationPoolVO());

        final NodePool loaded = poolDao.find(created.getId()).orElseThrow(AssertionError::new);
        assertThat(loaded.getCount()).isZero();
        assertThat(loaded.isActive(DateUtils.nowUTC())).isFalse();
        assertThat(reservationDao.findByNodePoolId(created.getId()).orElseThrow(AssertionError::new)
                .getInstanceCount()).isEqualTo(INSTANCE_COUNT);
    }

    @Test
    @WithMockUser(username = OWNER)
    public void shouldRequireApprovalWhenNoPolicyMatches() {
        final NodePool created = poolManager.create(reservationPoolVO());

        assertThat(reservationDao.findByNodePoolId(created.getId()).orElseThrow(AssertionError::new)
                .getStatus()).isEqualTo(CapacityReservationStatus.REQUIRED_APPROVE);
    }

    @Test
    @WithMockUser(username = OWNER, roles = ADMIN)
    public void shouldAutoApproveForAdmin() {
        final NodePool created = poolManager.create(reservationPoolVO());

        assertThat(reservationDao.findByNodePoolId(created.getId()).orElseThrow(AssertionError::new)
                .getStatus()).isEqualTo(CapacityReservationStatus.APPROVED);
    }

    @Test
    @WithMockUser(username = OWNER)
    public void shouldNotCreateReservationForOrdinaryPool() {
        final NodePoolVO vo = basePoolVO();

        final NodePool created = poolManager.create(vo);

        assertThat(created.isCapacityReservation()).isFalse();
        assertThat(created.getCount()).isEqualTo(INSTANCE_COUNT);
        assertThat(reservationDao.findByNodePoolId(created.getId()).isPresent()).isFalse();
    }

    @Test
    @WithMockUser(username = OWNER)
    public void shouldGenerateLaunchConfigForSharablePool() {
        final NodePoolVO vo = reservationPoolVO();
        vo.setPoolType(NodePoolType.SHARABLE_NODE);

        final NodePool created = poolManager.create(vo);

        final NodePoolLaunchConfig config = poolDao.find(created.getId())
                .orElseThrow(AssertionError::new)
                .getLaunchConfig();

        assertThat(config).isNotNull();
        assertThat(config.isCpuRequestsEnabled()).isTrue();
        assertThat(config.isRamRequestsEnabled()).isTrue();
        assertThat(config.getKubeAssignPolicy().isSkipContainerRequests()).isFalse();
        assertThat(config.getKubeAssignPolicy().getSecurityContext().getPrivileged()).isTrue();
        assertThat(config.getKubeAssignPolicy().getSelector().getLabel())
                .isEqualTo(KubernetesConstants.NODE_POOL_ID_LABEL);
        assertThat(config.getKubeAssignPolicy().getSelector().getValue())
                .isEqualTo(String.valueOf(created.getId()));
    }

    @Test
    @WithMockUser(username = OWNER)
    public void shouldKeepCallerLaunchConfigAndStillFillTheSelector() {
        final NodePoolLaunchConfig requested = new NodePoolLaunchConfig();
        requested.setCpuRequestsEnabled(true);
        requested.setCpuRequestsReserved(CALLER_CPU_RESERVED);
        final NodePoolVO vo = reservationPoolVO();
        vo.setPoolType(NodePoolType.SHARABLE_NODE);
        vo.setLaunchConfig(requested);

        final NodePool created = poolManager.create(vo);

        final NodePoolLaunchConfig stored = poolDao.find(created.getId())
                .orElseThrow(AssertionError::new)
                .getLaunchConfig();

        assertThat(stored.getCpuRequestsReserved()).isEqualTo(CALLER_CPU_RESERVED);
        assertThat(stored.isRamRequestsEnabled()).isFalse();
        assertThat(stored.getKubeAssignPolicy().getSelector().getValue())
                .isEqualTo(String.valueOf(created.getId()));
    }

    @Test
    @WithMockUser(username = OWNER)
    public void shouldGenerateLaunchConfigForASharablePoolWithoutAReservation() {
        final NodePoolVO vo = basePoolVO();
        vo.setPoolType(NodePoolType.SHARABLE_NODE);

        final NodePool created = poolManager.create(vo);

        final NodePoolLaunchConfig config = poolDao.find(created.getId())
                .orElseThrow(AssertionError::new)
                .getLaunchConfig();
        assertThat(config).isNotNull();
        assertThat(config.isCpuRequestsEnabled()).isTrue();
        assertThat(config.getKubeAssignPolicy().getSelector().getValue()).isEqualTo(String.valueOf(created.getId()));
    }

    @Test
    @WithMockUser(username = OWNER)
    public void shouldReplaceTheLaunchConfigOnlyWhenAnEditSendsOne() {
        final NodePoolVO vo = reservationPoolVO();
        vo.setPoolType(NodePoolType.SHARABLE_NODE);
        final NodePool created = poolManager.create(vo);
        final NodePoolLaunchConfig generated = poolDao.find(created.getId())
                .orElseThrow(AssertionError::new)
                .getLaunchConfig();

        final NodePoolVO unchanged = editOf(created.getId());
        unchanged.setLaunchConfig(null);
        poolManager.update(created.getId(), unchanged);
        assertThat(poolDao.find(created.getId()).orElseThrow(AssertionError::new).getLaunchConfig())
                .isEqualTo(generated);

        final NodePoolLaunchConfig requested = new NodePoolLaunchConfig();
        requested.setCpuRequestsReserved(CALLER_CPU_RESERVED);
        final NodePoolVO edit = editOf(created.getId());
        edit.setLaunchConfig(requested);
        poolManager.update(created.getId(), edit);
        final NodePoolLaunchConfig replaced = poolDao.find(created.getId())
                .orElseThrow(AssertionError::new)
                .getLaunchConfig();
        assertThat(replaced.getCpuRequestsReserved()).isEqualTo(CALLER_CPU_RESERVED);
        assertThat(replaced.getKubeAssignPolicy().getSelector().getValue())
                .isEqualTo(generated.getKubeAssignPolicy().getSelector().getValue());
    }

    @Test
    @WithMockUser(username = OWNER)
    public void shouldNotGenerateLaunchConfigForStandardPool() {
        final NodePool created = poolManager.create(reservationPoolVO());

        assertThat(poolDao.find(created.getId()).orElseThrow(AssertionError::new).getLaunchConfig()).isNull();
    }

    @Test
    @WithMockUser(username = OWNER)
    public void shouldRejectAStartDateTooSoonForTheProvider() {
        final NodePoolVO vo = reservationPoolVO();
        final LocalDateTime start = DateUtils.nowUTC().plusDays(2);
        vo.getCapacityReservationRequest().setRequestedStartDate(start);
        vo.getCapacityReservationRequest().setRequestedEndDate(start.plusDays(WINDOW_DAYS));

        assertThatThrownBy(() -> poolManager.create(vo)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @WithMockUser(username = OWNER)
    public void shouldRejectAStartDateTooFarAheadForTheProvider() {
        final NodePoolVO vo = reservationPoolVO();
        final LocalDateTime start = DateUtils.nowUTC().plusDays(200);
        vo.getCapacityReservationRequest().setRequestedStartDate(start);
        vo.getCapacityReservationRequest().setRequestedEndDate(start.plusDays(WINDOW_DAYS));

        assertThatThrownBy(() -> poolManager.create(vo)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @WithMockUser(username = OWNER)
    public void shouldRejectADurationShorterThanTheProvidersMinimumCommitment() {
        final NodePoolVO vo = reservationPoolVO();
        vo.getCapacityReservationRequest().setCommitmentDuration(ONE_DAY_SECONDS);

        assertThatThrownBy(() -> poolManager.create(vo)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @WithMockUser(username = OWNER)
    public void shouldRejectAnInstanceFamilyTheProviderWillNotReserve() {
        final NodePoolVO vo = reservationPoolVO();
        vo.setInstanceType("p5.48xlarge");

        assertThatThrownBy(() -> poolManager.create(vo)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @WithMockUser(username = OWNER)
    public void shouldNotMistakeALongerFamilyPrefixForASupportedOne() {
        final NodePoolVO vo = reservationPoolVO();
        vo.setInstanceType("trn1.32xlarge");

        assertThatThrownBy(() -> poolManager.create(vo)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @WithMockUser(username = OWNER)
    public void shouldAllowAFamilyAnAdministratorHasEnabled() {
        setInstanceFamilies("c,m,r,i,t,g,p");
        final NodePoolVO vo = reservationPoolVO();
        vo.setInstanceType("p5.48xlarge");

        final NodePool created = poolManager.create(vo);

        assertThat(reservationDao.findByNodePoolId(created.getId()).isPresent()).isTrue();
    }

    @Test
    @WithMockUser(username = OWNER)
    public void shouldRejectAnInstancePlatformTheProviderDoesNotKnow() {
        final NodePoolVO vo = reservationPoolVO();
        vo.getCapacityReservationRequest().setInstancePlatform("Plan 9");

        assertThatThrownBy(() -> poolManager.create(vo)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @WithMockUser(username = OWNER)
    public void shouldAutoApproveWhenPolicyMatches() {
        setPolicies("[" + autoApproveOnInstanceType() + "]");

        final NodePool created = poolManager.create(reservationPoolVO());

        assertThat(statusOf(created)).isEqualTo(CapacityReservationStatus.APPROVED);
    }

    @Test
    @WithMockUser(username = OWNER)
    public void shouldRequireApprovalWhenDenyMatchesAlongsideAutoApprove() {
        setPolicies("[" + autoApproveOnInstanceType() + "," + denyOnDuration() + "]");

        final NodePool created = poolManager.create(reservationPoolVO());

        assertThat(statusOf(created)).isEqualTo(CapacityReservationStatus.REQUIRED_APPROVE);
    }

    @Test
    @WithMockUser(username = OWNER)
    public void shouldAutoApproveWhenTheDeniedCommitmentIsLongerThanTheRequestedOne() {
        setPolicies("[" + autoApproveOnInstanceType() + "," + denyOnLongerDuration() + "]");

        final NodePool created = poolManager.create(reservationPoolVO());

        assertThat(statusOf(created)).isEqualTo(CapacityReservationStatus.APPROVED);
    }

    @Test
    @WithMockUser(username = OWNER)
    public void shouldRequireApprovalWhenDenyIsListedBeforeAutoApprove() {
        setPolicies("[" + denyOnDuration() + "," + autoApproveOnInstanceType() + "]");

        final NodePool created = poolManager.create(reservationPoolVO());

        assertThat(statusOf(created)).isEqualTo(CapacityReservationStatus.REQUIRED_APPROVE);
    }

    @Test
    @WithMockUser(username = OWNER)
    public void shouldAutoApproveWhenDenyDoesNotMatch() {
        setPolicies("[" + autoApproveOnInstanceType() + "," + denyOnNonMatchingInstanceType() + "]");

        final NodePool created = poolManager.create(reservationPoolVO());

        assertThat(statusOf(created)).isEqualTo(CapacityReservationStatus.APPROVED);
    }

    @Test
    @WithMockUser(username = OWNER, roles = ADMIN)
    public void shouldPersistTheReservationWindowWhenThePoolIsScheduled() {
        final CapacityReservation reservation = activeReservationOf(poolManager.create(reservationPoolVO()));

        reservationService.schedulePool(reservation);

        final NodePool pool = poolDao.find(reservation.getNodePoolId()).orElseThrow(AssertionError::new);
        final LocalDateTime cutOff = reservation.getEndDate().minusHours(preferenceManager.getPreference(
                SystemPreferences.CLUSTER_CAPACITY_RESERVATION_MONITOR_SETTINGS).getFinalizingLeadHours());
        assertThat(pool.getCount()).isZero();
        assertThat(pool.getStartDate()).isEqualTo(reservation.getStartDate());
        assertThat(pool.getEndDate()).isEqualTo(cutOff);
        assertThat(cutOff.isBefore(reservation.getEndDate())).isTrue();
        reservationService.activatePool(reservation);
        final NodePool activated = poolDao.find(reservation.getNodePoolId()).orElseThrow(AssertionError::new);
        assertThat(activated.isActive(cutOff.minusMinutes(1))).isTrue();
        assertThat(activated.isActive(cutOff.plusMinutes(1))).isFalse();
    }

    @Test
    @WithMockUser(username = OWNER, roles = ADMIN)
    public void shouldOnlySwitchThePoolOnWhenItsReservationIsActivated() {
        setNetworks();
        final NodePool created = createWithOwnLaunchSettings();
        final CapacityReservation reservation = activeReservationOf(created);
        reservationService.schedulePool(reservation);
        final NodePool scheduled = poolDao.find(created.getId()).orElseThrow(AssertionError::new);

        reservationService.activatePool(reservation);

        final NodePool activated = poolDao.find(created.getId()).orElseThrow(AssertionError::new);
        assertThat(activated.getCount()).isEqualTo(INSTANCE_COUNT);
        assertThat(activated.getAmiConfiguration()).isEqualTo(scheduled.getAmiConfiguration());
        assertThat(activated.getStartDate()).isEqualTo(scheduled.getStartDate());
        assertThat(activated.getEndDate()).isEqualTo(scheduled.getEndDate());
    }

    @Test
    @WithMockUser(username = OWNER, roles = ADMIN)
    public void shouldKeepAPoolInertThroughAnEditBeforeItsReservationIsActive() {
        final NodePool created = poolManager.create(reservationPoolVO());
        final NodePoolVO edit = editOf(created.getId());
        edit.setName(RENAMED);

        poolManager.update(created.getId(), edit);

        final NodePool pool = poolDao.find(created.getId()).orElseThrow(AssertionError::new);
        assertThat(pool.getName()).isEqualTo(RENAMED);
        assertThat(pool.getCount()).isZero();
    }

    @Test
    @WithMockUser(username = OWNER)
    public void shouldRefuseResizingAReservationPoolStillAwaitingApproval() {
        final NodePool created = poolManager.create(reservationPoolVO());
        final NodePoolVO edit = editOf(created.getId());
        edit.setCount(OTHER_INSTANCE_COUNT);

        assertThatThrownBy(() -> poolManager.update(created.getId(), edit))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot change the count");
        assertThat(reservationDao.findByNodePoolId(created.getId()).orElseThrow(AssertionError::new)
                .getInstanceCount()).isEqualTo(INSTANCE_COUNT);
        assertThat(poolDao.find(created.getId()).orElseThrow(AssertionError::new).getCount()).isZero();
    }

    @Test
    @WithMockUser(username = OWNER, roles = ADMIN)
    public void shouldRefuseResizingAnActiveReservationPool() {
        final CapacityReservation reservation = activeReservationOf(poolManager.create(reservationPoolVO()));
        scheduleAndActivate(reservation);
        final NodePoolVO edit = editOf(reservation.getNodePoolId());
        edit.setCount(OTHER_INSTANCE_COUNT);

        assertThatThrownBy(() -> poolManager.update(reservation.getNodePoolId(), edit))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot change the count");
        assertThat(poolDao.find(reservation.getNodePoolId()).orElseThrow(AssertionError::new).getCount())
                .isEqualTo(INSTANCE_COUNT);
    }

    @Test
    @WithMockUser(username = OWNER, roles = ADMIN)
    public void shouldRefuseAutoscalingAReservationPool() {
        final NodePool created = poolManager.create(reservationPoolVO());
        final NodePoolVO edit = autoscaled(editOf(created.getId()));

        assertThatThrownBy(() -> poolManager.update(created.getId(), edit))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be autoscaled");
        assertThat(poolDao.find(created.getId()).orElseThrow(AssertionError::new).isAutoscaled()).isFalse();
    }

    @Test
    @WithMockUser(username = OWNER, roles = ADMIN)
    public void shouldKeepAPoolInertThroughAnEditAfterItsReservationWasCancelled() {
        final CapacityReservation reservation = activeReservationOf(poolManager.create(reservationPoolVO()));
        scheduleAndActivate(reservation);
        reservationService.cancel(reservation.getId());

        poolManager.update(reservation.getNodePoolId(), editOf(reservation.getNodePoolId()));

        assertThat(poolDao.find(reservation.getNodePoolId()).orElseThrow(AssertionError::new).getCount())
                .isZero();
    }

    @Test
    @WithMockUser(username = OWNER, roles = ADMIN)
    public void shouldDeactivateThePoolWhenAnActiveReservationIsCancelled() {
        final CapacityReservation reservation = activeReservationOf(poolManager.create(reservationPoolVO()));
        scheduleAndActivate(reservation);

        reservationService.cancel(reservation.getId());

        assertThat(poolDao.find(reservation.getNodePoolId()).orElseThrow(AssertionError::new).getCount())
                .isZero();
        assertThat(reservationDao.find(reservation.getId()).orElseThrow(AssertionError::new).getStatus())
                .isEqualTo(CapacityReservationStatus.CANCELLED);
    }

    private NodePoolVO editOf(final Long poolId) {
        return poolMapper.toVO(poolDao.find(poolId).orElseThrow(AssertionError::new));
    }

    private static NodePoolVO autoscaled(final NodePoolVO vo) {
        vo.setAutoscaled(true);
        vo.setMinSize(AUTOSCALED_MIN_SIZE);
        vo.setMaxSize(INSTANCE_COUNT);
        vo.setScaleUpThreshold(SCALE_UP_THRESHOLD);
        vo.setScaleDownThreshold(SCALE_DOWN_THRESHOLD);
        vo.setScaleStep(1);
        return vo;
    }

    @Test
    @WithMockUser(username = OWNER, roles = ADMIN)
    public void shouldLetARunAskingForNoImageUseAScheduledReservationPool() {
        setNetworks();
        final CapacityReservation reservation = activeReservationOf(poolManager.create(reservationPoolVO()));

        scheduleAndActivate(reservation);

        final NodePool pool = poolDao.find(reservation.getNodePoolId()).orElseThrow(AssertionError::new);
        assertThat(pool.getAmiConfiguration().getAmi()).isNull();
        final RunInstance run = new RunInstance();
        run.setNodeType(INSTANCE_TYPE);
        run.setNodeDisk(INSTANCE_DISK);
        run.setEffectiveNodeDisk(INSTANCE_DISK);
        run.setSpot(false);
        run.setCloudRegionId(REGION_ID);
        assertThat(pool.toRunInstance().requirementsMatch(run, 0)).isTrue();
    }

    @Test
    @WithMockUser(username = OWNER, roles = ADMIN)
    public void shouldWriteTheReservationIntoThePoolsLaunchConfigurationWhenScheduled() {
        setNetworks();
        final NodePool created = createWithOwnLaunchSettings();
        final CapacityReservation reservation = activeReservationOf(created);

        reservationService.schedulePool(reservation);

        assertThat(poolDao.find(created.getId()).orElseThrow(AssertionError::new).getCount()).isZero();
        final AMIConfiguration configuration = launchConfigurationOf(created);
        assertThat(configuration.getAdditionalSpec()).containsEntry(RESERVATION_TARGET, CLOUD_RESERVATION_ID);
        assertThat(configuration.getAvailabilityZone()).isEqualTo(ZONE);
        assertThat(configuration.getSubnet()).isEqualTo(SUBNET);
        assertThat(configuration.getAdditionalSpec()).containsKey(OWN_SPEC_KEY);
        assertThat(configuration.getInitScript()).isEqualTo(OWN_INIT_SCRIPT);
    }

    @Test
    @WithMockUser(username = OWNER, roles = ADMIN)
    public void shouldRemoveTheReservationFromThePoolsLaunchConfigurationWhenItEnds() {
        setNetworks();
        final NodePool created = createWithOwnLaunchSettings();
        final CapacityReservation reservation = activeReservationOf(created);
        scheduleAndActivate(reservation);

        reservationService.cancel(reservation.getId());

        final AMIConfiguration configuration = launchConfigurationOf(created);
        assertThat(configuration.getAdditionalSpec()).doesNotContainKey(RESERVATION_TARGET);
        assertThat(configuration.getAvailabilityZone()).isNull();
        assertThat(configuration.getSubnet()).isNull();
        assertThat(configuration.getAdditionalSpec()).containsKey(OWN_SPEC_KEY);
        assertThat(configuration.getInitScript()).isEqualTo(OWN_INIT_SCRIPT);
    }

    @Test
    @WithMockUser(username = OWNER, roles = ADMIN)
    public void shouldRejectApprovingARequestThatIsNotWaitingForApproval() {
        setPolicies("[" + autoApproveOnInstanceType() + "]");
        final NodePool created = poolManager.create(reservationPoolVO());
        final CapacityReservation reservation = reservationDao.findByNodePoolId(created.getId())
                .orElseThrow(AssertionError::new);
        reservation.setStatusReason(PROVIDER_ANSWER);
        reservationDao.update(reservation);

        assertThatThrownBy(() -> reservationService.approve(reservation.getId()))
                .isInstanceOf(IllegalStateException.class);

        final CapacityReservation current = reservationService.load(reservation.getId());
        assertThat(current.getStatus()).isEqualTo(CapacityReservationStatus.APPROVED);
        assertThat(current.getStatusReason()).isEqualTo(PROVIDER_ANSWER);
    }

    @Test
    @WithMockUser(username = OWNER, roles = ADMIN)
    public void shouldTakeThePoolsWindowBackWhenTheReservationIsCancelled() {
        setNetworks();
        final NodePool created = createWithOwnLaunchSettings();
        final CapacityReservation reservation = activeReservationOf(created);
        scheduleAndActivate(reservation);
        assertThat(poolOf(created).getStartDate()).isNotNull();

        reservationService.cancel(reservation.getId());

        final NodePool pool = poolOf(created);
        assertThat(pool.getStartDate()).isNull();
        assertThat(pool.getEndDate()).isNull();
        assertThat(pool.getCount()).isZero();
    }

    @Test
    @WithMockUser(username = OWNER, roles = ADMIN)
    public void shouldLeaveTheLaunchConfigurationAloneThroughAnEdit() {
        setNetworks();
        final NodePool created = createWithOwnLaunchSettings();
        scheduleAndActivate(activeReservationOf(created));
        final AMIConfiguration before = launchConfigurationOf(created);
        final NodePoolVO edit = editOf(created.getId());
        edit.setName(RENAMED);

        poolManager.update(created.getId(), edit);

        final AMIConfiguration configuration = launchConfigurationOf(created);
        assertThat(configuration).isEqualTo(before);
        assertThat(configuration.getAdditionalSpec()).containsEntry(RESERVATION_TARGET, CLOUD_RESERVATION_ID);
        assertThat(configuration.getInitScript()).isEqualTo(OWN_INIT_SCRIPT);
    }

    @Test
    @WithMockUser(username = OWNER, roles = ADMIN)
    public void shouldRefuseReplacingTheLaunchConfigurationOfAReservationPool() {
        setNetworks();
        final NodePool created = createWithOwnLaunchSettings();
        scheduleAndActivate(activeReservationOf(created));
        final AMIConfiguration before = launchConfigurationOf(created);
        final NodePoolVO edit = editOf(created.getId());
        final AMIConfiguration replacement = new AMIConfiguration();
        replacement.setInitScript("/opt/api/scripts/init_edited.sh");
        replacement.setAvailabilityZone("us-east-1a");
        edit.setAmiConfiguration(replacement);

        assertThatThrownBy(() -> poolManager.update(created.getId(), edit))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot change the launch configuration");
        assertThat(launchConfigurationOf(created)).isEqualTo(before);
    }

    @Test
    @WithMockUser(username = OWNER, roles = ADMIN)
    @SuppressWarnings("deprecation")
    public void shouldRefuseChangingTheImageOfAReservationPool() {
        final NodePool created = poolManager.create(reservationPoolVO());
        final NodePoolVO edit = editOf(created.getId());
        edit.setInstanceImage("ami-0123456789abcdef0");

        assertThatThrownBy(() -> poolManager.update(created.getId(), edit))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot change the instance image");
    }

    @Test
    @WithMockUser(username = OWNER, roles = ADMIN)
    public void shouldRefuseSwitchingAReservationPoolToSpot() {
        final NodePool created = poolManager.create(reservationPoolVO());
        final NodePoolVO edit = editOf(created.getId());
        edit.setPriceType(com.epam.pipeline.entity.cluster.PriceType.SPOT);

        assertThatThrownBy(() -> poolManager.update(created.getId(), edit))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot change the price type");
    }

    private NodePool createWithOwnLaunchSettings() {
        final AMIConfiguration configuration = new AMIConfiguration();
        configuration.setInitScript(OWN_INIT_SCRIPT);
        configuration.setAdditionalSpec(new HashMap<>(Collections.singletonMap(OWN_SPEC_KEY, "profile")));
        final NodePoolVO vo = reservationPoolVO();
        vo.setAmiConfiguration(configuration);
        return poolManager.create(vo);
    }

    private AMIConfiguration launchConfigurationOf(final NodePool pool) {
        return poolOf(pool).getAmiConfiguration();
    }

    private NodePool poolOf(final NodePool pool) {
        return poolDao.find(pool.getId()).orElseThrow(AssertionError::new);
    }

    private void setNetworks() {
        final Preference preference = SystemPreferences.CLUSTER_NETWORKS_CONFIG.toPreference();
        preference.setValue("{\"regions\": [{\"name\": \"" + REGION_CODE + "\", \"networks\": {\"" + ZONE
                + "\": \"" + SUBNET + "\"}}]}");
        preferenceManager.update(Collections.singletonList(preference));
    }

    private void scheduleAndActivate(final CapacityReservation reservation) {
        reservationService.schedulePool(reservation);
        reservationService.activatePool(reservation);
    }

    private CapacityReservation activeReservationOf(final NodePool pool) {
        final CapacityReservation reservation = reservationDao.findByNodePoolId(pool.getId())
                .orElseThrow(AssertionError::new);
        reservation.setStartDate(reservation.getRequestedStartDate());
        reservation.setEndDate(reservation.getRequestedStartDate().plusSeconds(COMMITMENT_DURATION));
        reservation.setAvailabilityZone(ZONE);
        reservation.setCloudReservationId(CLOUD_RESERVATION_ID);
        return withStatus(reservation, CapacityReservationStatus.ACTIVE);
    }

    private CapacityReservation withStatus(final CapacityReservation reservation,
                                           final CapacityReservationStatus status) {
        reservation.setStatus(status);
        return reservationDao.update(reservation);
    }

    private static String autoApproveOnInstanceType() {
        return policy("AUTO_APPROVE", logical("instance.type", "=", "g5.*"));
    }

    private static String denyOnDuration() {
        return policy("DENY", logical("commitment.duration", ">", String.valueOf(COMMITMENT_DURATION - 1)));
    }

    private static String denyOnLongerDuration() {
        return policy("DENY", logical("commitment.duration", ">", String.valueOf(COMMITMENT_DURATION)));
    }

    private static String denyOnNonMatchingInstanceType() {
        return policy("DENY", logical("instance.type", "=", "m5.*"));
    }

    private static String policy(final String action, final String statement) {
        return "{\"action\": \"" + action + "\", \"statement\": " + statement + "}";
    }

    private static String logical(final String field, final String operand, final String value) {
        return "{\"type\": \"LOGICAL\", \"field\": \"" + field + "\", \"operand\": \"" + operand
                + "\", \"value\": \"" + value + "\"}";
    }

    private void setPolicies(final String json) {
        final Preference preference = SystemPreferences.CLUSTER_CAPACITY_RESERVATION_POLICIES.toPreference();
        preference.setValue(json);
        preferenceManager.update(Collections.singletonList(preference));
    }

    private void setInstanceFamilies(final String families) {
        final Preference preference =
                SystemPreferences.CLUSTER_CAPACITY_RESERVATION_INSTANCE_FAMILIES.toPreference();
        preference.setValue(families);
        preferenceManager.update(Collections.singletonList(preference));
    }

    private CapacityReservationStatus statusOf(final NodePool pool) {
        return reservationDao.findByNodePoolId(pool.getId())
                .orElseThrow(AssertionError::new)
                .getStatus();
    }

    private NodePoolVO basePoolVO() {
        final NodePoolVO vo = new NodePoolVO();
        vo.setName(POOL_NAME);
        vo.setRegionId(REGION_ID);
        vo.setInstanceType(INSTANCE_TYPE);
        vo.setInstanceDisk(INSTANCE_DISK);
        vo.setPriceType(com.epam.pipeline.entity.cluster.PriceType.ON_DEMAND);
        vo.setCount(INSTANCE_COUNT);
        return vo;
    }

    private NodePoolVO reservationPoolVO() {
        final NodePoolVO vo = basePoolVO();
        final CapacityReservationRequest request = new CapacityReservationRequest();
        request.setReservationType(CapacityReservationType.FUTURE_DATED);
        final LocalDateTime start = DateUtils.nowUTC().plusDays(LEAD_DAYS);
        request.setRequestedStartDate(start);
        request.setRequestedEndDate(start.plusDays(WINDOW_DAYS));
        request.setCommitmentDuration(COMMITMENT_DURATION);
        vo.setCapacityReservationRequest(request);
        return vo;
    }
}
