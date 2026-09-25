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
public class CapacityReservationCreationTest extends AbstractManagerTest {

    private static final String POOL_NAME = "p5 reserved pool";
    private static final String INSTANCE_TYPE = "p5.48xlarge";
    private static final long REGION_ID = 1L;
    private static final int INSTANCE_DISK = 100;
    private static final int INSTANCE_COUNT = 2;
    private static final int DURATION_HOURS = 24;
    private static final int WINDOW_DAYS = 10;
    private static final String OWNER = "requester";
    private static final int CALLER_CPU_RESERVED = 4;

    @Autowired
    private NodePoolManager poolManager;

    @Autowired
    private CapacityReservationDao reservationDao;

    @Autowired
    private NodePoolDao poolDao;

    @Autowired
    private PreferenceManager preferenceManager;

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
    @WithMockUser(username = OWNER, roles = "ADMIN")
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
     * A sharable pool's runs select their nodes by the reservation id, so without a reservation there is nothing
     * to select and the pool would be created unschedulable.
     */
    @Test
    @WithMockUser(username = OWNER)
    public void shouldRejectSharablePoolWithoutReservation() {
        final NodePoolVO vo = basePoolVO();
        vo.setPoolType(NodePoolType.SHARABLE_NODE);

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

    /** Matches the request: its instance type is p5.48xlarge. */
    private static String autoApproveOnInstanceType() {
        return policy("AUTO_APPROVE", logical("instance.type", "=", "p5.*"));
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
        final LocalDateTime start = DateUtils.nowUTC().plusDays(1);
        request.setRequestedStartDate(start);
        request.setRequestedEndDate(start.plusDays(WINDOW_DAYS));
        request.setDurationHours(DURATION_HOURS);
        vo.setCapacityReservationRequest(request);
        return vo;
    }
}
