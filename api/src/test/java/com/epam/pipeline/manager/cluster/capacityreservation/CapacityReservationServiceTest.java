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
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservationStatus;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservationType;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.manager.cluster.pool.NodePoolManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.region.CloudRegionManager;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.test.creator.cluster.capacityreservation.CapacityReservationCreatorUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class CapacityReservationServiceTest {

    private static final Long REGION_ID = 1L;
    private static final Long NODE_POOL_ID = 2L;
    private static final String OFFERING_ID = "cbr-0123456789abcdef0";
    private static final String AVAILABILITY_ZONE = "us-east-1c";
    private static final String INSTANCE_TYPE = "p5.48xlarge";
    private static final int INSTANCE_COUNT = 8;
    private static final long CAPACITY_DURATION_SECONDS = 14 * 24 * 3600L;

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

    @Captor
    private ArgumentCaptor<CapacityReservation> reservationCaptor;

    private CapacityReservationService service;

    @Before
    public void setUp() {
        final AwsRegion region = new AwsRegion();
        region.setId(REGION_ID);
        when(regionManager.load(REGION_ID)).thenReturn(region);
        when(approvalService.evaluate(any(CapacityReservation.class)))
                .thenReturn(CapacityReservationStatus.REQUIRED_APPROVE);
        when(reservationDao.create(reservationCaptor.capture()))
                .thenAnswer(invocation -> invocation.getArguments()[0]);
        service = new CapacityReservationService(reservationDao, approvalService, statusService, cloudFacade,
                regionManager, mock(MessageHelper.class), mock(PreferenceManager.class), mock(AuthManager.class));
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
                .instanceType(INSTANCE_TYPE)
                .instanceCount(INSTANCE_COUNT)
                .requestedStartDate(start)
                .requestedEndDate(end)
                .commitmentDuration(CAPACITY_DURATION_SECONDS)
                .capacityBlockOfferingId(OFFERING_ID)
                .availabilityZone(AVAILABILITY_ZONE)
                .build();

        service.create(request);

        final CapacityReservation built = reservationCaptor.getValue();
        assertThat(built.getCapacityBlockOfferingId()).isEqualTo(OFFERING_ID);
        assertThat(built.getAvailabilityZone()).isEqualTo(AVAILABILITY_ZONE);
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
                .instanceType(INSTANCE_TYPE)
                .instanceCount(INSTANCE_COUNT)
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
    public void shouldNotifyOnceCreated() {
        final CapacityReservationRequest request = CapacityReservationRequest.builder()
                .nodePoolId(NODE_POOL_ID)
                .regionId(REGION_ID)
                .reservationType(CapacityReservationType.FUTURE_DATED)
                .instanceType(INSTANCE_TYPE)
                .instanceCount(INSTANCE_COUNT)
                .build();

        final CapacityReservation created = service.create(request);

        verify(statusService).notifyCreated(created);
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
