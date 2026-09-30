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
import com.epam.pipeline.entity.cluster.InstanceOffer;
import com.epam.pipeline.entity.cluster.pool.NodePoolLaunchConfig;
import com.epam.pipeline.entity.cluster.pool.NodePool;
import com.epam.pipeline.entity.cluster.pool.NodePoolType;
import com.epam.pipeline.entity.preference.Preference;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.AbstractManagerTest;
import com.epam.pipeline.manager.cluster.InstanceOfferManager;
import com.epam.pipeline.manager.cluster.pool.NodePoolLaunchConfigBuilder;
import com.epam.pipeline.manager.cluster.pool.NodePoolManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.region.CloudRegionManager;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Mockito.when;

/**
 * The path this whole slice exists to make testable: a pool created with a reservation request lands both
 * rows in the database, correctly linked, with the reservation's status decided by the approval policy.
 *
 * <p>Nothing here reaches a cloud provider - there is no provider integration yet - so a reservation created
 * this way stops at {@code APPROVED} or {@code REQUIRED_APPROVE}.
 */
@Transactional
// Cancelling releases the capacity at the provider first; which provider call that is does not matter here.
@MockBean(CapacityReservationCloudFacade.class)
public class CapacityReservationCreationTest extends AbstractManagerTest {

    private static final String POOL_NAME = "g5 reserved pool";
    /**
     * A G-family type, because AWS reserves future-dated capacity for the C, M, R, I, T and G families only - a
     * P-family request would be refused before it reached the provider.
     */
    private static final String INSTANCE_TYPE = "g5.48xlarge";
    private static final long REGION_ID = 1L;
    private static final int INSTANCE_DISK = 100;
    private static final int INSTANCE_COUNT = 2;
    /**
     * Within AWS's limits for a future-dated request: at least 5 days of lead time, and a commitment of at least
     * 14 days that still fits inside the window.
     */
    private static final int DURATION_HOURS = 14 * 24;
    private static final int LEAD_DAYS = 7;
    private static final int WINDOW_DAYS = 30;
    /** Well inside AWS's minimum commitment, so a request asking for it must be refused. */
    private static final int ONE_DAY_HOURS = 24;
    private static final String OWNER = "requester";
    private static final String ADMIN = "ADMIN";
    private static final int CALLER_CPU_RESERVED = 4;
    private static final int OTHER_INSTANCE_COUNT = 5;
    /** What a g5.48xlarge has. */
    private static final int INSTANCE_VCPUS = 192;
    private static final int SMALL_INSTANCE_VCPUS = 8;
    private static final int MIN_VCPUS = 32;
    private static final int AUTOSCALED_MIN_SIZE = 1;
    private static final double SCALE_UP_THRESHOLD = 90.0;
    private static final double SCALE_DOWN_THRESHOLD = 10.0;
    private static final String RENAMED = "renamed pool";
    private static final String ZONE = "us-east-1c";

    @Autowired
    private NodePoolManager poolManager;

    @Autowired
    private CapacityReservationDao reservationDao;

    @Autowired
    private NodePoolDao poolDao;

    @Autowired
    private PreferenceManager preferenceManager;

    @Autowired
    private CapacityReservationService reservationService;

    @MockBean
    private CloudRegionManager regionManager;

    /**
     * Pool validation asks whether the price type and instance type are offered in the region. That is a
     * question about the cloud, not about this flow, so it is stubbed rather than exercised here.
     */
    @MockBean
    private InstanceOfferManager instanceOfferManager;

    @Before
    public void setUp() {
        final AwsRegion region = new AwsRegion();
        region.setId(REGION_ID);
        when(regionManager.load(any())).thenReturn((AbstractCloudRegion) region);
        when(instanceOfferManager.isPriceTypeAllowed(any())).thenReturn(true);
        when(instanceOfferManager.isToolInstanceAllowed(any(), any(), anyBoolean())).thenReturn(true);
        givenVcpusPerInstance(INSTANCE_VCPUS);
    }

    /**
     * AWS reserves future-dated capacity in blocks of at least 32 vCPUs in total. A smaller request would be
     * accepted, approved, and then refused by the provider days later.
     */
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

    /**
     * An autoscaled pool is reserved at its largest, so that is what counts.
     */
    @Test
    @WithMockUser(username = OWNER)
    public void shouldJudgeAnAutoscaledPoolAtItsMaxSize() {
        givenVcpusPerInstance(SMALL_INSTANCE_VCPUS);
        final NodePoolVO vo = reservationPoolVO();
        vo.setAutoscaled(true);
        vo.setMinSize(1);
        vo.setMaxSize(MIN_VCPUS / SMALL_INSTANCE_VCPUS);
        vo.setScaleUpThreshold(SCALE_UP_THRESHOLD);
        vo.setScaleDownThreshold(SCALE_DOWN_THRESHOLD);
        vo.setScaleStep(1);

        assertThat(poolManager.create(vo).getId()).isNotNull();
    }

    /**
     * With no offer to count the vCPUs by, the provider has the last word.
     */
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
        assertThat(reservation.get().getInstanceType()).isEqualTo(INSTANCE_TYPE);
        assertThat(reservation.get().getInstanceCount()).isEqualTo(INSTANCE_COUNT);
        assertThat(reservation.get().getDurationHours()).isEqualTo(DURATION_HOURS);
        assertThat(reservation.get().getOwner()).isEqualTo(OWNER);
        assertThat(reservation.get().getInstancePlatform())
                .isEqualTo(CapacityReservation.DEFAULT_INSTANCE_PLATFORM);
    }

    /**
     * A reservation-backed pool is persisted inert. Its requested size lives on the reservation, so the size
     * is not lost - but the pool cannot schedule anything until the reservation is actually granted.
     */
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

    /**
     * With no policy configured the safe default applies: the request waits for a human rather than
     * auto-approving something that spends money.
     */
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

    /**
     * An ordinary pool must be entirely unaffected: no reservation row, and its count honoured as given.
     */
    @Test
    @WithMockUser(username = OWNER)
    public void shouldNotCreateReservationForOrdinaryPool() {
        final NodePoolVO vo = basePoolVO();

        final NodePool created = poolManager.create(vo);

        assertThat(created.isCapacityReservation()).isFalse();
        assertThat(created.getCount()).isEqualTo(INSTANCE_COUNT);
        assertThat(reservationDao.findByNodePoolId(created.getId()).isPresent()).isFalse();
    }

    /**
     * A sharable pool gets a launch config derived from its instance type, and the pod selector is keyed on the
     * reservation's id - which is why the config cannot be part of the pool's own insert.
     */
    @Test
    @WithMockUser(username = OWNER)
    public void shouldGenerateLaunchConfigForSharablePool() {
        final NodePoolVO vo = reservationPoolVO();
        vo.setPoolType(NodePoolType.SHARABLE_NODE);

        final NodePool created = poolManager.create(vo);

        final Long reservationId = reservationDao.findByNodePoolId(created.getId())
                .orElseThrow(AssertionError::new)
                .getId();
        final NodePoolLaunchConfig config = poolDao.find(created.getId())
                .orElseThrow(AssertionError::new)
                .getLaunchConfig();

        assertThat(config).isNotNull();
        assertThat(config.isCpuRequestsEnabled()).isTrue();
        assertThat(config.isRamRequestsEnabled()).isTrue();
        assertThat(config.getKubeAssignPolicy().isSkipContainerRequests()).isFalse();
        assertThat(config.getKubeAssignPolicy().getSecurityContext().getPrivileged()).isTrue();
        assertThat(config.getKubeAssignPolicy().getSelector().getLabel())
                .isEqualTo(NodePoolLaunchConfigBuilder.CAPACITY_RESERVATION_LABEL);
        assertThat(config.getKubeAssignPolicy().getSelector().getValue())
                .isEqualTo(String.valueOf(reservationId));
    }

    /**
     * A caller-supplied config is taken as given, but a selector they left unset still gets the reservation id -
     * they have no way to know it when they submit the request.
     */
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

        final Long reservationId = reservationDao.findByNodePoolId(created.getId())
                .orElseThrow(AssertionError::new)
                .getId();
        final NodePoolLaunchConfig stored = poolDao.find(created.getId())
                .orElseThrow(AssertionError::new)
                .getLaunchConfig();

        assertThat(stored.getCpuRequestsReserved()).isEqualTo(CALLER_CPU_RESERVED);
        assertThat(stored.isRamRequestsEnabled()).isFalse();
        assertThat(stored.getKubeAssignPolicy().getSelector().getValue())
                .isEqualTo(String.valueOf(reservationId));
    }

    @Test
    @WithMockUser(username = OWNER)
    public void shouldNotGenerateLaunchConfigForStandardPool() {
        final NodePool created = poolManager.create(reservationPoolVO());

        assertThat(poolDao.find(created.getId()).orElseThrow(AssertionError::new).getLaunchConfig()).isNull();
    }

    /**
     * AWS will not assess a future-dated request for a date less than 5 days out, so accepting one here would
     * approve a request that is certain to be refused - after the approval, and after the wait.
     */
    @Test
    @WithMockUser(username = OWNER)
    public void shouldRejectAStartDateTooSoonForTheProvider() {
        final NodePoolVO vo = reservationPoolVO();
        final LocalDateTime start = DateUtils.nowUTC().plusDays(2);
        vo.getCapacityReservationRequest().setRequestedStartDate(start);
        vo.getCapacityReservationRequest().setRequestedEndDate(start.plusDays(WINDOW_DAYS));

        assertThatThrownBy(() -> poolManager.create(vo)).isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * The other end of the same window: AWS looks no further ahead than 120 days.
     */
    @Test
    @WithMockUser(username = OWNER)
    public void shouldRejectAStartDateTooFarAheadForTheProvider() {
        final NodePoolVO vo = reservationPoolVO();
        final LocalDateTime start = DateUtils.nowUTC().plusDays(200);
        vo.getCapacityReservationRequest().setRequestedStartDate(start);
        vo.getCapacityReservationRequest().setRequestedEndDate(start.plusDays(WINDOW_DAYS));

        assertThatThrownBy(() -> poolManager.create(vo)).isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * The duration is the commitment, and AWS holds the requester to at least 14 days of it. Rejecting a shorter
     * one is deliberately not the same as quietly rounding it up: that would bill two weeks for a request nobody
     * made.
     */
    @Test
    @WithMockUser(username = OWNER)
    public void shouldRejectADurationShorterThanTheProvidersMinimumCommitment() {
        final NodePoolVO vo = reservationPoolVO();
        vo.getCapacityReservationRequest().setDurationHours(ONE_DAY_HOURS);

        assertThatThrownBy(() -> poolManager.create(vo)).isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * AWS reserves future-dated capacity for the C, M, R, I, T and G families only - which notably excludes the P
     * families, the obvious choice for GPU work. Refusing here is the difference between an immediate, explicable
     * error and a provider rejection days later.
     */
    @Test
    @WithMockUser(username = OWNER)
    public void shouldRejectAnInstanceFamilyTheProviderWillNotReserve() {
        final NodePoolVO vo = reservationPoolVO();
        vo.setInstanceType("p5.48xlarge");

        assertThatThrownBy(() -> poolManager.create(vo)).isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * The family is the letters before the first digit, not the first letter: {@code trn1} is Trainium and is not
     * the {@code t} family, so matching on the initial alone would let it through to be refused by AWS.
     */
    @Test
    @WithMockUser(username = OWNER)
    public void shouldNotMistakeALongerFamilyPrefixForASupportedOne() {
        final NodePoolVO vo = reservationPoolVO();
        vo.setInstanceType("trn1.32xlarge");

        assertThatThrownBy(() -> poolManager.create(vo)).isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * The supported families are the provider's list and it changes, so an administrator can widen it without
     * waiting for a release.
     */
    @Test
    @WithMockUser(username = OWNER)
    public void shouldAllowAFamilyAnAdministratorHasEnabled() {
        setInstanceFamilies("c,m,r,i,t,g,p");
        final NodePoolVO vo = reservationPoolVO();
        vo.setInstanceType("p5.48xlarge");

        final NodePool created = poolManager.create(vo);

        assertThat(reservationDao.findByNodePoolId(created.getId()).isPresent()).isTrue();
    }

    /**
     * An operating system AWS does not reserve capacity for cannot be satisfied at any date.
     */
    @Test
    @WithMockUser(username = OWNER)
    public void shouldRejectAnInstancePlatformTheProviderDoesNotKnow() {
        final NodePoolVO vo = reservationPoolVO();
        vo.getCapacityReservationRequest().setInstancePlatform("Plan 9");

        assertThatThrownBy(() -> poolManager.create(vo)).isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * A matching AUTO_APPROVE policy is how an administrator pre-authorizes a <em>shape</em> of request - this
     * instance family, under this duration - rather than approving each one by hand.
     */
    @Test
    @WithMockUser(username = OWNER)
    public void shouldAutoApproveWhenPolicyMatches() {
        setPolicies("[" + autoApproveOnInstanceType() + "]");

        final NodePool created = poolManager.create(reservationPoolVO());

        assertThat(statusOf(created)).isEqualTo(CapacityReservationStatus.APPROVED);
    }

    /**
     * Deny wins. A request can legitimately match an auto-approve rule and a deny rule at once - broad
     * "this family is fine" alongside narrow "but not above this size" - and in that case it must still reach a
     * human. Evaluating auto-approve first would silently spend money on exactly the requests an administrator
     * wrote a rule to stop.
     */
    @Test
    @WithMockUser(username = OWNER)
    public void shouldRequireApprovalWhenDenyMatchesAlongsideAutoApprove() {
        setPolicies("[" + autoApproveOnInstanceType() + "," + denyOnDuration() + "]");

        final NodePool created = poolManager.create(reservationPoolVO());

        assertThat(statusOf(created)).isEqualTo(CapacityReservationStatus.REQUIRED_APPROVE);
    }

    /**
     * Order of the rules in the preference must not matter - the deny is decisive whether it is written first or
     * last, otherwise the outcome would depend on how an administrator happened to arrange the list.
     */
    @Test
    @WithMockUser(username = OWNER)
    public void shouldRequireApprovalWhenDenyIsListedBeforeAutoApprove() {
        setPolicies("[" + denyOnDuration() + "," + autoApproveOnInstanceType() + "]");

        final NodePool created = poolManager.create(reservationPoolVO());

        assertThat(statusOf(created)).isEqualTo(CapacityReservationStatus.REQUIRED_APPROVE);
    }

    /**
     * And a deny that does not describe this request leaves the auto-approve standing.
     */
    @Test
    @WithMockUser(username = OWNER)
    public void shouldAutoApproveWhenDenyDoesNotMatch() {
        setPolicies("[" + autoApproveOnInstanceType() + "," + denyOnNonMatchingInstanceType() + "]");

        final NodePool created = poolManager.create(reservationPoolVO());

        assertThat(statusOf(created)).isEqualTo(CapacityReservationStatus.APPROVED);
    }

    /**
     * The ordinary pool update leaves the dates out, so activating through it used to drop the window - and the
     * pool then stayed schedulable after the reservation ended.
     */
    @Test
    @WithMockUser(username = OWNER, roles = ADMIN)
    public void shouldPersistTheReservationWindowWhenThePoolIsActivated() {
        final CapacityReservation reservation = activeReservationOf(poolManager.create(reservationPoolVO()));

        reservationService.activatePool(reservation);

        final NodePool pool = poolDao.find(reservation.getNodePoolId()).orElseThrow(AssertionError::new);
        assertThat(pool.getCount()).isEqualTo(INSTANCE_COUNT);
        assertThat(pool.getStartDate()).isEqualTo(reservation.getStartDate());
        assertThat(pool.getEndDate()).isEqualTo(reservation.getEndDate());
        assertThat(pool.isActive(reservation.getEndDate().plusMinutes(1))).isFalse();
    }

    /**
     * Until its reservation is active the pool is kept inert by a count of 0. An edit that took the count from the
     * request would switch it on early, launching plain on-demand nodes with no capacity behind them.
     */
    @Test
    @WithMockUser(username = OWNER, roles = ADMIN)
    public void shouldKeepAPoolInertThroughAnEditBeforeItsReservationIsActive() {
        final NodePool created = poolManager.create(reservationPoolVO());
        final NodePoolVO edit = editOf();
        edit.setName(RENAMED);

        poolManager.update(created.getId(), edit);

        final NodePool pool = poolDao.find(created.getId()).orElseThrow(AssertionError::new);
        assertThat(pool.getName()).isEqualTo(RENAMED);
        assertThat(pool.getCount()).isZero();
    }

    /**
     * An editor shows the pool's count - 0 until activation - and sends it back unchanged. That is not a request to
     * change the size, so it must not be refused as one.
     */
    @Test
    @WithMockUser(username = OWNER, roles = ADMIN)
    public void shouldAcceptAnEditThatSendsBackTheCountThePoolShows() {
        final NodePool created = poolManager.create(reservationPoolVO());
        final NodePoolVO edit = editOf();
        edit.setCount(0);
        edit.setName(RENAMED);

        poolManager.update(created.getId(), edit);

        assertThat(poolDao.find(created.getId()).orElseThrow(AssertionError::new).getName()).isEqualTo(RENAMED);
        assertThat(reservationDao.findByNodePoolId(created.getId()).orElseThrow(AssertionError::new)
                .getInstanceCount()).isEqualTo(INSTANCE_COUNT);
    }

    @Test
    @WithMockUser(username = OWNER, roles = ADMIN)
    public void shouldRefuseResizingAnApprovedReservation() {
        final NodePool created = poolManager.create(reservationPoolVO());
        final NodePoolVO edit = editOf();
        edit.setCount(OTHER_INSTANCE_COUNT);

        assertThatThrownBy(() -> poolManager.update(created.getId(), edit))
                .isInstanceOf(IllegalStateException.class);
    }

    /**
     * Before approval the size is still only a request, so it follows the pool - an approver must see the size
     * that will actually be bought.
     */
    @Test
    @WithMockUser(username = OWNER)
    public void shouldResizeAReservationStillAwaitingApproval() {
        final NodePool created = poolManager.create(reservationPoolVO());
        final NodePoolVO edit = editOf();
        edit.setCount(OTHER_INSTANCE_COUNT);

        poolManager.update(created.getId(), edit);

        assertThat(reservationDao.findByNodePoolId(created.getId()).orElseThrow(AssertionError::new)
                .getInstanceCount()).isEqualTo(OTHER_INSTANCE_COUNT);
        assertThat(poolDao.find(created.getId()).orElseThrow(AssertionError::new).getCount()).isZero();
    }

    /**
     * An edit rebuilds the labels from the request, which knows nothing of the one activation added.
     */
    @Test
    @WithMockUser(username = OWNER, roles = ADMIN)
    public void shouldKeepTheReservationLabelOfAnActivePoolThroughAnEdit() {
        final CapacityReservation reservation = activeReservationOf(poolManager.create(reservationPoolVO()));
        reservationService.activatePool(reservation);
        final NodePoolVO edit = editOf();
        edit.setName(RENAMED);

        poolManager.update(reservation.getNodePoolId(), edit);

        assertThat(poolDao.find(reservation.getNodePoolId()).orElseThrow(AssertionError::new).getKubeLabels())
                .containsKey(NodePoolLaunchConfigBuilder.CAPACITY_RESERVATION_LABEL);
    }

    @Test
    @WithMockUser(username = OWNER, roles = ADMIN)
    public void shouldKeepAnAutoscaledPoolInertThroughAnEditBeforeItsReservationIsActive() {
        final NodePool created = poolManager.create(autoscaled(reservationPoolVO()));
        final NodePoolVO edit = autoscaled(editOf());
        edit.setCount(INSTANCE_COUNT);

        poolManager.update(created.getId(), edit);

        assertThat(poolDao.find(created.getId()).orElseThrow(AssertionError::new).getCount()).isZero();
    }

    /**
     * An editor opened before activation still shows 0. Saving it afterwards must not switch the pool off: the
     * autoscaler only looks at pools that are on, so nothing would ever switch it back while the capacity idles.
     */
    @Test
    @WithMockUser(username = OWNER, roles = ADMIN)
    public void shouldNotLetAnEditSwitchOffAnActiveAutoscaledPool() {
        final CapacityReservation reservation = activeReservationOf(
                poolManager.create(autoscaled(reservationPoolVO())));
        reservationService.activatePool(reservation);
        final NodePoolVO staleEdit = autoscaled(editOf());
        staleEdit.setCount(0);

        poolManager.update(reservation.getNodePoolId(), staleEdit);

        assertThat(poolDao.find(reservation.getNodePoolId()).orElseThrow(AssertionError::new).getCount())
                .isEqualTo(INSTANCE_COUNT);
    }

    @Test
    @WithMockUser(username = OWNER, roles = ADMIN)
    public void shouldNotLetAnEditGrowAnActiveAutoscaledPoolPastItsReservation() {
        final CapacityReservation reservation = activeReservationOf(
                poolManager.create(autoscaled(reservationPoolVO())));
        reservationService.activatePool(reservation);
        final NodePoolVO edit = autoscaled(editOf());
        edit.setCount(INSTANCE_COUNT + 1);

        poolManager.update(reservation.getNodePoolId(), edit);

        assertThat(poolDao.find(reservation.getNodePoolId()).orElseThrow(AssertionError::new).getCount())
                .isEqualTo(INSTANCE_COUNT);
    }

    /**
     * Once the reservation has ended its pool is inert for good - a retry is a new pool - so an edit must not
     * switch it back on with no capacity behind it.
     */
    @Test
    @WithMockUser(username = OWNER, roles = ADMIN)
    public void shouldKeepAPoolInertThroughAnEditAfterItsReservationWasCancelled() {
        final CapacityReservation reservation = activeReservationOf(poolManager.create(reservationPoolVO()));
        reservationService.activatePool(reservation);
        reservationService.cancel(reservation.getId());

        poolManager.update(reservation.getNodePoolId(), editOf());

        assertThat(poolDao.find(reservation.getNodePoolId()).orElseThrow(AssertionError::new).getCount())
                .isZero();
    }

    /**
     * An autoscaled pool's reservation covers it at its largest, so resizing it before approval follows maxSize.
     */
    @Test
    @WithMockUser(username = OWNER)
    public void shouldResizeAnAutoscaledPoolsReservationToItsMaxSize() {
        final NodePool created = poolManager.create(autoscaled(reservationPoolVO()));
        final NodePoolVO edit = autoscaled(editOf());
        edit.setMaxSize(OTHER_INSTANCE_COUNT);

        poolManager.update(created.getId(), edit);

        assertThat(reservationDao.findByNodePoolId(created.getId()).orElseThrow(AssertionError::new)
                .getInstanceCount()).isEqualTo(OTHER_INSTANCE_COUNT);
    }

    /**
     * Once the capacity exists the count is the pool's to manage again: the autoscaler resizes an autoscaled pool
     * through an ordinary update, inside the size the reservation holds.
     */
    @Test
    @WithMockUser(username = OWNER, roles = ADMIN)
    public void shouldLetTheAutoscalerResizeAnActiveReservationPool() {
        final NodePoolVO vo = autoscaled(reservationPoolVO());
        final CapacityReservation reservation = activeReservationOf(poolManager.create(vo));
        reservationService.activatePool(reservation);
        final NodePoolVO scaledDown = autoscaled(editOf());
        scaledDown.setCount(AUTOSCALED_MIN_SIZE);

        poolManager.update(reservation.getNodePoolId(), scaledDown);

        assertThat(poolDao.find(reservation.getNodePoolId()).orElseThrow(AssertionError::new).getCount())
                .isEqualTo(AUTOSCALED_MIN_SIZE);
    }

    /**
     * A cancelled reservation is never looked at again, so anything still switched on stays on - running
     * full-price on-demand nodes with no reservation behind them.
     */
    @Test
    @WithMockUser(username = OWNER, roles = ADMIN)
    public void shouldDeactivateThePoolWhenAnActiveReservationIsCancelled() {
        final CapacityReservation reservation = activeReservationOf(poolManager.create(reservationPoolVO()));
        reservationService.activatePool(reservation);

        reservationService.cancel(reservation.getId());

        assertThat(poolDao.find(reservation.getNodePoolId()).orElseThrow(AssertionError::new).getCount())
                .isZero();
        assertThat(reservationDao.find(reservation.getId()).orElseThrow(AssertionError::new).getStatus())
                .isEqualTo(CapacityReservationStatus.CANCELLED);
    }

    /**
     * FINALIZING only warns that the end is near; the capacity is held and paid for until the end itself, so new
     * nodes must keep consuming it.
     */
    @Test
    @WithMockUser(username = OWNER, roles = ADMIN)
    public void shouldOfferTheReservationToNewNodesWhileItIsActiveOrFinalizing() {
        final CapacityReservation reservation = activeReservationOf(poolManager.create(reservationPoolVO()));
        final Long poolId = reservation.getNodePoolId();

        assertThat(reservationService.findConsumableByNodePoolId(poolId).isPresent()).isTrue();
        withStatus(reservation, CapacityReservationStatus.FINALIZING);
        assertThat(reservationService.findConsumableByNodePoolId(poolId).isPresent()).isTrue();
        withStatus(reservation, CapacityReservationStatus.SCHEDULED);
        assertThat(reservationService.findConsumableByNodePoolId(poolId).isPresent()).isFalse();
        withStatus(reservation, CapacityReservationStatus.FINISHED);
        assertThat(reservationService.findConsumableByNodePoolId(poolId).isPresent()).isFalse();
    }

    /**
     * What an editor sends for an existing pool: its settings, but not the reservation request, which only applies
     * when the pool is created.
     */
    private NodePoolVO editOf() {
        return basePoolVO();
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

    /**
     * Puts the reservation where the monitor leaves it once the provider has granted the capacity.
     */
    private CapacityReservation activeReservationOf(final NodePool pool) {
        final CapacityReservation reservation = reservationDao.findByNodePoolId(pool.getId())
                .orElseThrow(AssertionError::new);
        reservation.setStartDate(reservation.getRequestedStartDate());
        reservation.setEndDate(reservation.getRequestedStartDate().plusHours(DURATION_HOURS));
        reservation.setAvailabilityZone(ZONE);
        return withStatus(reservation, CapacityReservationStatus.ACTIVE);
    }

    private CapacityReservation withStatus(final CapacityReservation reservation,
                                           final CapacityReservationStatus status) {
        reservation.setStatus(status);
        return reservationDao.update(reservation);
    }

    /** Matches the request: its instance type is g5.48xlarge. */
    private static String autoApproveOnInstanceType() {
        return policy("AUTO_APPROVE", logical("instance.type", "=", "g5.*"));
    }

    /** Also matches it: the requested duration is 24 hours. */
    private static String denyOnDuration() {
        return policy("DENY", logical("duration.hours", ">", "12"));
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
        request.setDurationHours(DURATION_HOURS);
        vo.setCapacityReservationRequest(request);
        return vo;
    }
}
