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

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.controller.vo.cluster.pool.CapacityReservationRequest;
import com.epam.pipeline.dao.cluster.capacityreservation.CapacityReservationDao;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservation;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservationMonitorSettings;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservationStatus;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservationType;
import com.epam.pipeline.entity.cluster.pool.NodePool;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.entity.cluster.InstanceOffer;
import com.epam.pipeline.manager.cluster.InstanceOfferManager;
import com.epam.pipeline.manager.cluster.pool.NodePoolManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.region.CloudRegionManager;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.test.creator.cluster.capacityreservation.CapacityReservationCreatorUtils;
import com.epam.pipeline.vo.CapacitySpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class CapacityReservationServiceTest {

    private static final Long REGION_ID = 1L;
    private static final Long NODE_POOL_ID = 2L;
    private static final String OFFERING_ID = "cbr-0123456789abcdef0";
    private static final String INSTANCE_TYPE = "p5.48xlarge";
    private static final int INSTANCE_COUNT = 8;
    private static final long CAPACITY_DURATION_SECONDS = 14 * 24 * 3600L;
    private static final double GPU_INSTANCE_MEMORY_GB = 61;
    private static final double CPU_INSTANCE_MEMORY_GB = 32;

    @Mock
    private CapacityReservationDao reservationDao;

    @Mock
    private CapacityReservationApprovalService approvalService;

    @Mock
    private CapacityReservationStatusService statusService;

    @Mock
    private CapacityReservationCloudFacade cloudFacade;

    @Mock
    private CloudRegionManager regionManager;

    @Mock
    private NodePoolManager poolManager;

    @Mock
    private PreferenceManager preferenceManager;

    @Mock
    private InstanceOfferManager instanceOfferManager;

    @Captor
    private ArgumentCaptor<CapacityReservation> reservationCaptor;

    private CapacityReservationService service;

    @BeforeEach
    public void setUp() {
        final AwsRegion region = new AwsRegion();
        region.setId(REGION_ID);
        when(regionManager.load(REGION_ID)).thenReturn(region);
        when(approvalService.evaluate(any(CapacityReservation.class)))
                .thenReturn(CapacityReservationStatus.REQUIRED_APPROVE);
        when(reservationDao.create(reservationCaptor.capture()))
                .thenAnswer(invocation -> invocation.getArguments()[0]);
        when(instanceOfferManager.findOffer(anyString(), anyLong())).thenReturn(Optional.empty());
        service = new CapacityReservationService(reservationDao, approvalService, statusService, cloudFacade,
                regionManager, mock(MessageHelper.class), preferenceManager, mock(AuthManager.class),
                instanceOfferManager);
        ReflectionTestUtils.setField(service, "poolManager", poolManager);
    }

    @Test
    public void shouldCopyTheChosenOfferingOntoACapacityBlockReservation() {
        final LocalDateTime start = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS).plusWeeks(1);
        final LocalDateTime end = start.plusSeconds(CAPACITY_DURATION_SECONDS);
        final CapacityReservationRequest request = CapacityReservationRequest.builder()
                .nodePoolId(NODE_POOL_ID)
                .regionId(REGION_ID)
                .reservationType(CapacityReservationType.CAPACITY_BLOCK)
                .candidate(CapacitySpecification.builder()
                        .instanceType(INSTANCE_TYPE)
                        .instanceCount(INSTANCE_COUNT)
                        .build())
                .requestedStartDate(start)
                .requestedEndDate(end)
                .commitmentDuration(CAPACITY_DURATION_SECONDS)
                .capacityBlockOfferingId(OFFERING_ID)
                .build();

        service.create(request);

        final CapacityReservation built = reservationCaptor.getValue();
        assertThat(built.getCapacityBlockOfferingId()).isEqualTo(OFFERING_ID);
        assertThat(built.getAvailabilityZone()).isNull();
        assertThat(built.getStartDate()).isEqualTo(start);
        assertThat(built.getEndDate()).isEqualTo(end);
        assertThat(built.getRequestedStartDate()).isEqualTo(start);
        assertThat(built.getRequestedEndDate()).isEqualTo(end);
    }

    @Test
    public void shouldLeaveTheGrantedWindowUnsetForAFutureDatedReservation() {
        final LocalDateTime start = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS).plusWeeks(1);
        final LocalDateTime end = start.plusDays(60);
        final CapacityReservationRequest request = CapacityReservationRequest.builder()
                .nodePoolId(NODE_POOL_ID)
                .regionId(REGION_ID)
                .reservationType(CapacityReservationType.FUTURE_DATED)
                .candidate(CapacitySpecification.builder()
                        .instanceType(INSTANCE_TYPE)
                        .instanceCount(INSTANCE_COUNT)
                        .build())
                .requestedStartDate(start)
                .requestedEndDate(end)
                .commitmentDuration(CAPACITY_DURATION_SECONDS)
                .build();

        service.create(request);

        final CapacityReservation built = reservationCaptor.getValue();
        assertThat(built.getCapacityBlockOfferingId()).isNull();
        assertThat(built.getAvailabilityZone()).isNull();
        assertThat(built.getStartDate()).isNull();
        assertThat(built.getEndDate()).isNull();
    }

    @Test
    public void shouldSortFallbackCandidatesBiggestFirst() {
        final CapacitySpecification gpuHeavy = fallback("p3.2xlarge", 1);
        final CapacitySpecification cpuHeavy = fallback("c5.4xlarge", 1);
        final CapacitySpecification smallest = fallback("t3.micro", 1);
        when(instanceOfferManager.findOffer("p3.2xlarge", REGION_ID))
                .thenReturn(Optional.of(InstanceOffer.builder().gpu(1).vCPU(8).memory(GPU_INSTANCE_MEMORY_GB).build()));
        when(instanceOfferManager.findOffer("c5.4xlarge", REGION_ID))
                .thenReturn(Optional.of(
                        InstanceOffer.builder().gpu(0).vCPU(16).memory(CPU_INSTANCE_MEMORY_GB).build()));
        when(instanceOfferManager.findOffer("t3.micro", REGION_ID))
                .thenReturn(Optional.of(InstanceOffer.builder().gpu(0).vCPU(2).memory(1).build()));
        final CapacityReservationRequest request = futureDatedRequest(Arrays.asList(smallest, cpuHeavy, gpuHeavy));

        service.create(request);

        assertThat(reservationCaptor.getValue().getFallbackCandidates())
                .containsExactly(gpuHeavy, cpuHeavy, smallest);
    }

    @Test
    public void shouldDropAFallbackCandidateMatchingTheMainPairButKeepADifferentCount() {
        final CapacitySpecification sameTypeAndCount = fallback(INSTANCE_TYPE, INSTANCE_COUNT);
        final CapacitySpecification sameTypeDifferentCount = fallback(INSTANCE_TYPE, INSTANCE_COUNT + 1);
        final CapacityReservationRequest request =
                futureDatedRequest(Arrays.asList(sameTypeAndCount, sameTypeDifferentCount));

        service.create(request);

        assertThat(reservationCaptor.getValue().getFallbackCandidates())
                .containsExactly(sameTypeDifferentCount);
    }

    @Test
    public void shouldMergeDuplicateFallbackCandidates() {
        final CapacitySpecification first = fallback("c5.4xlarge", 2);
        final CapacitySpecification duplicate = fallback("c5.4xlarge", 2);
        final CapacityReservationRequest request = futureDatedRequest(Arrays.asList(first, duplicate));

        service.create(request);

        assertThat(reservationCaptor.getValue().getFallbackCandidates()).containsExactly(first);
    }

    @Test
    public void shouldSortAnUnpricedFallbackCandidateLast() {
        final CapacitySpecification priced = fallback("c5.4xlarge", 1);
        final CapacitySpecification unpriced = fallback("unknown.type", 1);
        when(instanceOfferManager.findOffer("c5.4xlarge", REGION_ID))
                .thenReturn(Optional.of(
                        InstanceOffer.builder().gpu(0).vCPU(16).memory(CPU_INSTANCE_MEMORY_GB).build()));
        when(instanceOfferManager.findOffer("unknown.type", REGION_ID)).thenReturn(Optional.empty());
        final CapacityReservationRequest request = futureDatedRequest(Arrays.asList(unpriced, priced));

        service.create(request);

        assertThat(reservationCaptor.getValue().getFallbackCandidates()).containsExactly(priced, unpriced);
    }

    @Test
    public void shouldNeverReadFallbackCandidatesForACapacityBlockRequest() {
        final CapacityReservationRequest request = CapacityReservationRequest.builder()
                .nodePoolId(NODE_POOL_ID)
                .regionId(REGION_ID)
                .reservationType(CapacityReservationType.CAPACITY_BLOCK)
                .candidate(CapacitySpecification.builder()
                        .instanceType(INSTANCE_TYPE)
                        .instanceCount(INSTANCE_COUNT)
                        .build())
                .capacityBlockOfferingId(OFFERING_ID)
                .fallbackCandidates(Arrays.asList(fallback("c5.4xlarge", 1)))
                .build();

        service.create(request);

        assertThat(reservationCaptor.getValue().getFallbackCandidates()).isEmpty();
    }

    private static CapacitySpecification fallback(final String instanceType, final int count) {
        return CapacitySpecification.builder().instanceType(instanceType).instanceCount(count).build();
    }

    private static CapacityReservationRequest futureDatedRequest(final List<CapacitySpecification> fallbacks) {
        return CapacityReservationRequest.builder()
                .nodePoolId(NODE_POOL_ID)
                .regionId(REGION_ID)
                .reservationType(CapacityReservationType.FUTURE_DATED)
                .candidate(CapacitySpecification.builder()
                        .instanceType(INSTANCE_TYPE)
                        .instanceCount(INSTANCE_COUNT)
                        .build())
                .fallbackCandidates(fallbacks)
                .build();
    }

    @Test
    public void shouldNotifyOnceCreated() {
        final CapacityReservationRequest request = CapacityReservationRequest.builder()
                .nodePoolId(NODE_POOL_ID)
                .regionId(REGION_ID)
                .reservationType(CapacityReservationType.FUTURE_DATED)
                .candidate(CapacitySpecification.builder()
                        .instanceType(INSTANCE_TYPE)
                        .instanceCount(INSTANCE_COUNT)
                        .build())
                .build();

        final CapacityReservation created = service.create(request);

        verify(statusService).notifyCreated(created);
    }

    @Test
    public void shouldExtendOnlyThePoolsEndDateWhenRecordingAnExtendedEndDate() {
        final int finalizingLeadHours = 2;
        when(preferenceManager.getPreference(SystemPreferences.CLUSTER_CAPACITY_RESERVATION_MONITOR_SETTINGS))
                .thenReturn(new CapacityReservationMonitorSettings(finalizingLeadHours));
        final CapacityReservation reservation =
                CapacityReservationCreatorUtils.getCapacityBlockReservation(NODE_POOL_ID);
        final LocalDateTime extendedEndDate = reservation.getEndDate().plusDays(1);
        reservation.setEndDate(extendedEndDate);
        final NodePool pool = new NodePool();
        final LocalDateTime untouchedStartDate = reservation.getStartDate().minusDays(1);
        pool.setStartDate(untouchedStartDate);
        @SuppressWarnings("unchecked")
        final ArgumentCaptor<Consumer> poolChange = ArgumentCaptor.forClass(Consumer.class);
        when(poolManager.applyReservationState(eq(NODE_POOL_ID), poolChange.capture())).thenReturn(pool);

        service.recordExtendedEndDate(reservation);

        verify(reservationDao).update(reservation);
        poolChange.getValue().accept(pool);
        assertThat(pool.getEndDate()).isEqualTo(extendedEndDate.minusHours(finalizingLeadHours));
        assertThat(pool.getStartDate()).isEqualTo(untouchedStartDate);
    }

    @Test
    public void shouldUpdateThePoolsInstanceTypeWhenActivatingAFallbackCandidate() {
        final CapacityReservation reservation =
                CapacityReservationCreatorUtils.getScheduledReservation(NODE_POOL_ID);
        reservation.setCandidate(CapacitySpecification.builder()
                .instanceType("c5.4xlarge")
                .instanceCount(4)
                .build());
        final NodePool pool = new NodePool();
        pool.setInstanceType(INSTANCE_TYPE);
        pool.setCount(0);
        @SuppressWarnings("unchecked")
        final ArgumentCaptor<Consumer> poolChange = ArgumentCaptor.forClass(Consumer.class);
        when(poolManager.applyReservationState(eq(NODE_POOL_ID), poolChange.capture())).thenReturn(pool);

        service.activatePool(reservation);

        poolChange.getValue().accept(pool);
        assertThat(pool.getInstanceType()).isEqualTo("c5.4xlarge");
        assertThat(pool.getCount()).isEqualTo(4);
    }

    @Test
    public void shouldLeaveThePoolsInstanceTypeUnchangedWhenTheMainCandidateLands() {
        final CapacityReservation reservation =
                CapacityReservationCreatorUtils.getScheduledReservation(NODE_POOL_ID);
        final NodePool pool = new NodePool();
        pool.setInstanceType(reservation.getCandidate().getInstanceType());
        pool.setCount(0);
        @SuppressWarnings("unchecked")
        final ArgumentCaptor<Consumer> poolChange = ArgumentCaptor.forClass(Consumer.class);
        when(poolManager.applyReservationState(eq(NODE_POOL_ID), poolChange.capture())).thenReturn(pool);

        service.activatePool(reservation);

        poolChange.getValue().accept(pool);
        assertThat(pool.getInstanceType()).isEqualTo(reservation.getCandidate().getInstanceType());
        assertThat(pool.getCount()).isEqualTo(reservation.getCandidate().getInstanceCount());
    }

    @Test
    public void shouldPointTheScheduledPoolAtWhicheverCandidateTheProviderAccepted() {
        when(preferenceManager.getPreference(SystemPreferences.CLUSTER_CAPACITY_RESERVATION_MONITOR_SETTINGS))
                .thenReturn(new CapacityReservationMonitorSettings(0));
        final CapacityReservation reservation = CapacityReservationCreatorUtils.getScheduledReservation(NODE_POOL_ID);
        reservation.setCandidate(CapacitySpecification.builder()
                .instanceType("c5.4xlarge")
                .instanceCount(4)
                .build());
        final NodePool pool = new NodePool();
        pool.setInstanceType(INSTANCE_TYPE);
        pool.setCount(0);
        @SuppressWarnings("unchecked")
        final ArgumentCaptor<Consumer> poolChange = ArgumentCaptor.forClass(Consumer.class);
        when(poolManager.applyReservationState(eq(NODE_POOL_ID), poolChange.capture())).thenReturn(pool);

        service.schedulePool(reservation);

        poolChange.getValue().accept(pool);
        assertThat(pool.getInstanceType()).isEqualTo("c5.4xlarge");
        assertThat(pool.getCount()).isEqualTo(0);
    }

    @Test
    public void shouldRefuseToCancelAPurchasedCapacityBlock() {
        final Long id = 1L;
        final CapacityReservation reservation = CapacityReservationCreatorUtils.getCapacityBlockReservation(2L);
        reservation.setId(id);
        reservation.setCloudReservationId(CapacityReservationCreatorUtils.CLOUD_RESERVATION_ID);
        when(reservationDao.find(id)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> service.cancel(id)).isInstanceOf(IllegalArgumentException.class);

        verify(cloudFacade, never()).cancel(any());
        verify(statusService, never()).transition(any(), eq(CapacityReservationStatus.CANCELLED), any());
    }

    @Test
    public void shouldAllowCancellingAStillPendingCapacityBlockRequest() {
        final Long id = 1L;
        final CapacityReservation reservation = CapacityReservationCreatorUtils.getCapacityBlockReservation(2L);
        reservation.setId(id);
        reservation.setStatus(CapacityReservationStatus.APPROVED);
        when(reservationDao.find(id)).thenReturn(Optional.of(reservation));
        when(statusService.transition(reservation, CapacityReservationStatus.CANCELLED, null))
                .thenReturn(reservation);

        service.cancel(id);

        verify(cloudFacade).cancel(reservation);
        verify(statusService).transition(reservation, CapacityReservationStatus.CANCELLED, null);
    }
}
