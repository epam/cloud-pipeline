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
import com.epam.pipeline.dao.cluster.capacityreservation.CapacityBlockExtensionDao;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityBlockExtension;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityBlockExtensionStatus;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityBlockOffering;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservation;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservationStatus;
import com.epam.pipeline.entity.cluster.capacityreservation.CloudCapacityBlockExtension;
import com.epam.pipeline.entity.cluster.capacityreservation.CloudCapacityReservationState;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.test.creator.cluster.capacityreservation.CapacityBlockExtensionCreatorUtils;
import com.epam.pipeline.test.creator.cluster.capacityreservation.CapacityReservationCreatorUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class CapacityBlockExtensionServiceTest {

    private static final Long RESERVATION_ID = 1L;
    private static final Long EXTENSION_ID = 2L;
    private static final String USER = "user";
    private static final int DURATION_HOURS = CapacityBlockExtensionCreatorUtils.DURATION_HOURS;
    private static final int MAX_ONE_DAY_STEP_TOTAL_HOURS =
            CapacityBlockExtensionService.MAX_ONE_DAY_STEP_TOTAL_HOURS;
    private static final int MAX_TOTAL_EXTENSION_HOURS = CapacityBlockExtensionService.MAX_TOTAL_EXTENSION_HOURS;
    private static final long SECONDS_PER_HOUR = 3600L;
    private static final Long OTHER_RESERVATION_ID = 99L;
    private static final int HOURS_PER_DAY = 24;
    private static final int SEVEN_DAY_STEP_HOURS = 7 * HOURS_PER_DAY;

    @Mock
    private CapacityReservationService reservationService;

    @Mock
    private CapacityBlockExtensionDao extensionDao;

    @Mock
    private CapacityReservationCloudFacade cloudFacade;

    @Mock
    private CapacityReservationApprovalService approvalService;

    @Mock
    private AuthManager authManager;

    @Captor
    private ArgumentCaptor<CapacityBlockExtension> extensionCaptor;

    private CapacityBlockExtensionService service;

    @Before
    public void setUp() {
        when(authManager.getAuthorizedUser()).thenReturn(USER);
        when(extensionDao.create(extensionCaptor.capture()))
                .thenAnswer(invocation -> invocation.getArguments()[0]);
        when(extensionDao.update(any())).thenAnswer(invocation -> invocation.getArguments()[0]);
        service = new CapacityBlockExtensionService(reservationService, extensionDao, cloudFacade, approvalService,
                mock(MessageHelper.class), authManager);
    }

    @Test
    public void shouldAutoApproveAndPurchaseWhenThePolicyApproves() {
        final CapacityReservation reservation = activeCapacityBlockReservation();
        when(reservationService.loadForUpdate(RESERVATION_ID)).thenReturn(reservation);
        when(approvalService.evaluate(any())).thenReturn(CapacityReservationStatus.APPROVED);
        final LocalDateTime newEndDate = reservation.getEndDate().plusHours(DURATION_HOURS);
        when(cloudFacade.purchaseCapacityBlockExtension(reservation,
                CapacityBlockExtensionCreatorUtils.EXTENSION_OFFERING_ID)).thenReturn(
                CloudCapacityBlockExtension.builder()
                        .state(CloudCapacityReservationState.ACTIVE)
                        .startDate(reservation.getEndDate())
                        .endDate(newEndDate)
                        .build());

        final CapacityBlockExtension result = service.requestExtension(RESERVATION_ID,
                CapacityBlockExtensionCreatorUtils.EXTENSION_OFFERING_ID, DURATION_HOURS, null);

        assertThat(result.getStatus()).isEqualTo(CapacityBlockExtensionStatus.SUCCEEDED);
        assertThat(result.getEndDate()).isEqualTo(newEndDate);
        assertThat(result.getRequestedBy()).isEqualTo(USER);
        assertThat(result.getDurationHours()).isEqualTo(DURATION_HOURS);
        assertThat(reservation.getEndDate()).isEqualTo(newEndDate);
        verify(reservationService).recordExtendedEndDate(reservation);
    }

    @Test
    public void shouldRecordWhatAwsActuallyGrantedAsTheDurationRatherThanWhatWasRequested() {
        final CapacityReservation reservation = activeCapacityBlockReservation();
        when(reservationService.loadForUpdate(RESERVATION_ID)).thenReturn(reservation);
        when(approvalService.evaluate(any())).thenReturn(CapacityReservationStatus.APPROVED);
        final int grantedHours = DURATION_HOURS * 2;
        when(cloudFacade.purchaseCapacityBlockExtension(any(), any())).thenReturn(CloudCapacityBlockExtension
                .builder()
                .state(CloudCapacityReservationState.ACTIVE)
                .startDate(reservation.getEndDate())
                .endDate(reservation.getEndDate().plusHours(grantedHours))
                .build());

        final CapacityBlockExtension result = service.requestExtension(RESERVATION_ID,
                CapacityBlockExtensionCreatorUtils.EXTENSION_OFFERING_ID, DURATION_HOURS, null);

        assertThat(result.getDurationHours()).isEqualTo(grantedHours);
    }

    @Test
    public void shouldWaitForApprovalWhenThePolicyDoesNotAutoApprove() {
        final CapacityReservation reservation = activeCapacityBlockReservation();
        when(reservationService.loadForUpdate(RESERVATION_ID)).thenReturn(reservation);
        when(approvalService.evaluate(any())).thenReturn(CapacityReservationStatus.REQUIRED_APPROVE);

        final CapacityBlockExtension result = service.requestExtension(RESERVATION_ID,
                CapacityBlockExtensionCreatorUtils.EXTENSION_OFFERING_ID, DURATION_HOURS, null);

        assertThat(result.getStatus()).isEqualTo(CapacityBlockExtensionStatus.REQUIRED_APPROVE);
        verify(cloudFacade, never()).purchaseCapacityBlockExtension(any(), any());
        verify(extensionDao, never()).update(any());
    }

    @Test
    public void shouldPassTheExtensionDurationAsTheCommitmentDurationToTheApprovalPolicy() {
        final CapacityReservation reservation = activeCapacityBlockReservation();
        when(reservationService.loadForUpdate(RESERVATION_ID)).thenReturn(reservation);
        when(approvalService.evaluate(any())).thenReturn(CapacityReservationStatus.REQUIRED_APPROVE);

        service.requestExtension(RESERVATION_ID, CapacityBlockExtensionCreatorUtils.EXTENSION_OFFERING_ID,
                DURATION_HOURS, null);

        final ArgumentCaptor<CapacityReservation> captor = ArgumentCaptor.forClass(CapacityReservation.class);
        verify(approvalService).evaluate(captor.capture());
        assertThat(captor.getValue().getCommitmentDuration()).isEqualTo(DURATION_HOURS * SECONDS_PER_HOUR);
        assertThat(captor.getValue().getId()).isEqualTo(reservation.getId());
    }

    @Test
    public void shouldNotLetTheExtensionInheritTheApprovalOfTheReservationsOwnOriginChain() {
        final CapacityReservation reservation = activeCapacityBlockReservation();
        reservation.setOriginId(OTHER_RESERVATION_ID);
        when(reservationService.loadForUpdate(RESERVATION_ID)).thenReturn(reservation);
        when(approvalService.evaluate(any())).thenReturn(CapacityReservationStatus.REQUIRED_APPROVE);

        service.requestExtension(RESERVATION_ID, CapacityBlockExtensionCreatorUtils.EXTENSION_OFFERING_ID,
                DURATION_HOURS, null);

        final ArgumentCaptor<CapacityReservation> captor = ArgumentCaptor.forClass(CapacityReservation.class);
        verify(approvalService).evaluate(captor.capture());
        assertThat(captor.getValue().getOriginId()).isNull();
        assertThat(reservation.getOriginId()).isEqualTo(OTHER_RESERVATION_ID);
    }

    @Test
    public void shouldInheritApprovalForARetryAskingForNoMoreThanTheFailedOriginal() {
        final CapacityReservation reservation = activeCapacityBlockReservation();
        when(reservationService.loadForUpdate(RESERVATION_ID)).thenReturn(reservation);
        final CapacityBlockExtension origin = failedExtension(RESERVATION_ID, EXTENSION_ID, DURATION_HOURS * 2);
        when(extensionDao.find(EXTENSION_ID)).thenReturn(java.util.Optional.of(origin));
        when(cloudFacade.purchaseCapacityBlockExtension(eq(reservation), any())).thenReturn(
                CloudCapacityBlockExtension.builder()
                        .state(CloudCapacityReservationState.ACTIVE)
                        .startDate(reservation.getEndDate())
                        .endDate(reservation.getEndDate().plusHours(DURATION_HOURS))
                        .build());

        final CapacityBlockExtension result = service.requestExtension(RESERVATION_ID,
                CapacityBlockExtensionCreatorUtils.EXTENSION_OFFERING_ID, DURATION_HOURS, EXTENSION_ID);

        assertThat(result.getStatus()).isEqualTo(CapacityBlockExtensionStatus.SUCCEEDED);
        verify(approvalService, never()).evaluate(any());
    }

    @Test
    public void shouldInheritApprovalForARetryAskingForExactlyTheOriginalsDuration() {
        final CapacityReservation reservation = activeCapacityBlockReservation();
        when(reservationService.loadForUpdate(RESERVATION_ID)).thenReturn(reservation);
        final CapacityBlockExtension origin = failedExtension(RESERVATION_ID, EXTENSION_ID, DURATION_HOURS);
        when(extensionDao.find(EXTENSION_ID)).thenReturn(java.util.Optional.of(origin));
        when(cloudFacade.purchaseCapacityBlockExtension(eq(reservation), any())).thenReturn(
                CloudCapacityBlockExtension.builder()
                        .state(CloudCapacityReservationState.ACTIVE)
                        .startDate(reservation.getEndDate())
                        .endDate(reservation.getEndDate().plusHours(DURATION_HOURS))
                        .build());

        final CapacityBlockExtension result = service.requestExtension(RESERVATION_ID,
                CapacityBlockExtensionCreatorUtils.EXTENSION_OFFERING_ID, DURATION_HOURS, EXTENSION_ID);

        assertThat(result.getStatus()).isEqualTo(CapacityBlockExtensionStatus.SUCCEEDED);
        verify(approvalService, never()).evaluate(any());
    }

    @Test
    public void shouldNotInheritApprovalWhenTheRetryAsksForMoreThanTheOriginal() {
        final CapacityReservation reservation = activeCapacityBlockReservation();
        when(reservationService.loadForUpdate(RESERVATION_ID)).thenReturn(reservation);
        final CapacityBlockExtension origin = failedExtension(RESERVATION_ID, EXTENSION_ID, DURATION_HOURS);
        when(extensionDao.find(EXTENSION_ID)).thenReturn(java.util.Optional.of(origin));
        when(approvalService.evaluate(any())).thenReturn(CapacityReservationStatus.REQUIRED_APPROVE);

        final CapacityBlockExtension result = service.requestExtension(RESERVATION_ID,
                CapacityBlockExtensionCreatorUtils.EXTENSION_OFFERING_ID, DURATION_HOURS * 2, EXTENSION_ID);

        assertThat(result.getStatus()).isEqualTo(CapacityBlockExtensionStatus.REQUIRED_APPROVE);
        verify(approvalService).evaluate(any());
    }

    @Test
    public void shouldNotInheritApprovalFromAnOriginThatIsNotFailed() {
        final CapacityReservation reservation = activeCapacityBlockReservation();
        when(reservationService.loadForUpdate(RESERVATION_ID)).thenReturn(reservation);
        final CapacityBlockExtension origin = CapacityBlockExtensionCreatorUtils.getExtension(RESERVATION_ID)
                .toBuilder().id(EXTENSION_ID).durationHours(DURATION_HOURS * 2).build();
        when(extensionDao.find(EXTENSION_ID)).thenReturn(java.util.Optional.of(origin));
        when(approvalService.evaluate(any())).thenReturn(CapacityReservationStatus.REQUIRED_APPROVE);

        final CapacityBlockExtension result = service.requestExtension(RESERVATION_ID,
                CapacityBlockExtensionCreatorUtils.EXTENSION_OFFERING_ID, DURATION_HOURS, EXTENSION_ID);

        assertThat(result.getStatus()).isEqualTo(CapacityBlockExtensionStatus.REQUIRED_APPROVE);
        verify(approvalService).evaluate(any());
    }

    @Test
    public void shouldNotInheritApprovalFromAnOriginBelongingToAnotherReservation() {
        final CapacityReservation reservation = activeCapacityBlockReservation();
        when(reservationService.loadForUpdate(RESERVATION_ID)).thenReturn(reservation);
        final CapacityBlockExtension origin = failedExtension(OTHER_RESERVATION_ID, EXTENSION_ID,
                DURATION_HOURS * 2);
        when(extensionDao.find(EXTENSION_ID)).thenReturn(java.util.Optional.of(origin));
        when(approvalService.evaluate(any())).thenReturn(CapacityReservationStatus.REQUIRED_APPROVE);

        final CapacityBlockExtension result = service.requestExtension(RESERVATION_ID,
                CapacityBlockExtensionCreatorUtils.EXTENSION_OFFERING_ID, DURATION_HOURS, EXTENSION_ID);

        assertThat(result.getStatus()).isEqualTo(CapacityBlockExtensionStatus.REQUIRED_APPROVE);
        verify(approvalService).evaluate(any());
    }

    private static CapacityBlockExtension failedExtension(final Long reservationId, final Long id,
                                                            final int durationHours) {
        return CapacityBlockExtensionCreatorUtils.getExtension(reservationId).toBuilder()
                .id(id)
                .status(CapacityBlockExtensionStatus.FAILED)
                .durationHours(durationHours)
                .build();
    }

    @Test
    public void shouldRejectRequestingAnExtensionWithNoDuration() {
        assertThatThrownBy(() -> service.requestExtension(RESERVATION_ID,
                CapacityBlockExtensionCreatorUtils.EXTENSION_OFFERING_ID, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(reservationService, never()).loadForUpdate(any());
    }

    @Test
    public void shouldRejectRequestingAnExtensionWithNoOfferingId() {
        assertThatThrownBy(() -> service.requestExtension(RESERVATION_ID, null, DURATION_HOURS, null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(reservationService, never()).loadForUpdate(any());
    }

    @Test
    public void shouldRejectListingOfferingsWithNoDuration() {
        assertThatThrownBy(() -> service.listOfferings(RESERVATION_ID, null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(reservationService, never()).load(any());
    }

    @Test
    public void shouldRejectRequestingAnExtensionForAFutureDatedReservation() {
        final CapacityReservation reservation = CapacityReservationCreatorUtils.getScheduledReservation(1L);
        reservation.setId(RESERVATION_ID);
        reservation.setStatus(CapacityReservationStatus.ACTIVE);
        when(reservationService.loadForUpdate(RESERVATION_ID)).thenReturn(reservation);

        assertThatThrownBy(() -> service.requestExtension(RESERVATION_ID,
                CapacityBlockExtensionCreatorUtils.EXTENSION_OFFERING_ID, DURATION_HOURS, null))
                .isInstanceOf(IllegalStateException.class);
        verify(extensionDao, never()).create(any());
    }

    @Test
    public void shouldRejectRequestingAnExtensionForAReservationThatIsNotActiveOrFinalizing() {
        final CapacityReservation reservation = activeCapacityBlockReservation();
        reservation.setStatus(CapacityReservationStatus.APPROVED);
        when(reservationService.loadForUpdate(RESERVATION_ID)).thenReturn(reservation);

        assertThatThrownBy(() -> service.requestExtension(RESERVATION_ID,
                CapacityBlockExtensionCreatorUtils.EXTENSION_OFFERING_ID, DURATION_HOURS, null))
                .isInstanceOf(IllegalStateException.class);
        verify(extensionDao, never()).create(any());
    }

    @Test
    public void shouldAcceptExtensionOfferingsForAReservationThatIsFinalizing() {
        final CapacityReservation reservation = activeCapacityBlockReservation();
        reservation.setStatus(CapacityReservationStatus.FINALIZING);
        when(reservationService.load(RESERVATION_ID)).thenReturn(reservation);
        final List<CapacityBlockOffering> offerings = Collections.singletonList(CapacityBlockOffering.builder()
                .build());
        when(cloudFacade.listCapacityBlockExtensionOfferings(reservation, DURATION_HOURS)).thenReturn(offerings);

        assertThat(service.listOfferings(RESERVATION_ID, DURATION_HOURS)).isEqualTo(offerings);
    }

    @Test
    public void shouldRejectRequestingAnExtensionLongerThanAnySingleExtensionCanSpan() {
        final CapacityReservation reservation = activeCapacityBlockReservation();
        when(reservationService.loadForUpdate(RESERVATION_ID)).thenReturn(reservation);

        assertThatThrownBy(() -> service.requestExtension(RESERVATION_ID,
                CapacityBlockExtensionCreatorUtils.EXTENSION_OFFERING_ID,
                MAX_TOTAL_EXTENSION_HOURS + SEVEN_DAY_STEP_HOURS, null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(extensionDao, never()).create(any());
    }

    @Test
    public void shouldFailTheExtensionWhenThePurchaseThrows() {
        final CapacityReservation reservation = activeCapacityBlockReservation();
        when(reservationService.loadForUpdate(RESERVATION_ID)).thenReturn(reservation);
        when(approvalService.evaluate(any())).thenReturn(CapacityReservationStatus.APPROVED);
        when(cloudFacade.purchaseCapacityBlockExtension(any(), any()))
                .thenThrow(new RuntimeException("AWS refused the purchase"));

        final CapacityBlockExtension result = service.requestExtension(RESERVATION_ID,
                CapacityBlockExtensionCreatorUtils.EXTENSION_OFFERING_ID, DURATION_HOURS, null);

        assertThat(result.getStatus()).isEqualTo(CapacityBlockExtensionStatus.FAILED);
        assertThat(result.getStatusReason()).contains("AWS refused the purchase");
        verify(reservationService, never()).recordExtendedEndDate(any());
    }

    @Test
    public void shouldFailTheExtensionWhenThePurchaseReportsAFailureState() {
        final CapacityReservation reservation = activeCapacityBlockReservation();
        when(reservationService.loadForUpdate(RESERVATION_ID)).thenReturn(reservation);
        when(approvalService.evaluate(any())).thenReturn(CapacityReservationStatus.APPROVED);
        when(cloudFacade.purchaseCapacityBlockExtension(any(), any())).thenReturn(CloudCapacityBlockExtension
                .builder()
                .state(CloudCapacityReservationState.PAYMENT_FAILED)
                .stateReason("Could not be paid for")
                .build());

        final CapacityBlockExtension result = service.requestExtension(RESERVATION_ID,
                CapacityBlockExtensionCreatorUtils.EXTENSION_OFFERING_ID, DURATION_HOURS, null);

        assertThat(result.getStatus()).isEqualTo(CapacityBlockExtensionStatus.FAILED);
        assertThat(result.getStatusReason()).isEqualTo("Could not be paid for");
        verify(reservationService, never()).recordExtendedEndDate(any());
    }

    @Test
    public void shouldApproveAWaitingExtensionAndPurchaseIt() {
        final CapacityReservation reservation = activeCapacityBlockReservation();
        final CapacityBlockExtension extension = CapacityBlockExtensionCreatorUtils.getExtension(RESERVATION_ID);
        extension.setId(EXTENSION_ID);
        when(extensionDao.find(EXTENSION_ID)).thenReturn(java.util.Optional.of(extension));
        when(reservationService.loadForUpdate(RESERVATION_ID)).thenReturn(reservation);
        final LocalDateTime newEndDate = reservation.getEndDate().plusHours(DURATION_HOURS);
        when(cloudFacade.purchaseCapacityBlockExtension(reservation, extension.getExtensionOfferingId()))
                .thenReturn(CloudCapacityBlockExtension.builder()
                        .state(CloudCapacityReservationState.ACTIVE)
                        .startDate(reservation.getEndDate())
                        .endDate(newEndDate)
                        .build());

        final CapacityBlockExtension result = service.approve(EXTENSION_ID);

        assertThat(result.getStatus()).isEqualTo(CapacityBlockExtensionStatus.SUCCEEDED);
        verify(reservationService).recordExtendedEndDate(reservation);
    }

    @Test
    public void shouldRejectApprovingAnExtensionThatIsNotWaitingForApproval() {
        final CapacityReservation reservation = activeCapacityBlockReservation();
        final CapacityBlockExtension extension = CapacityBlockExtensionCreatorUtils.getSucceededExtension(
                RESERVATION_ID);
        extension.setId(EXTENSION_ID);
        when(extensionDao.find(EXTENSION_ID)).thenReturn(java.util.Optional.of(extension));
        when(reservationService.loadForUpdate(RESERVATION_ID)).thenReturn(reservation);

        assertThatThrownBy(() -> service.approve(EXTENSION_ID)).isInstanceOf(IllegalStateException.class);
        verify(extensionDao, never()).update(any());
        verify(cloudFacade, never()).purchaseCapacityBlockExtension(any(), any());
    }

    @Test
    public void shouldRejectApprovingAnExtensionThatDoesNotExist() {
        when(extensionDao.find(EXTENSION_ID)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.approve(EXTENSION_ID)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void shouldRereadTheExtensionAfterTakingTheReservationLockBeforeApproving() {
        final CapacityReservation reservation = activeCapacityBlockReservation();
        final CapacityBlockExtension waiting = CapacityBlockExtensionCreatorUtils.getExtension(RESERVATION_ID);
        waiting.setId(EXTENSION_ID);
        final CapacityBlockExtension alreadyPurchased =
                CapacityBlockExtensionCreatorUtils.getSucceededExtension(RESERVATION_ID);
        alreadyPurchased.setId(EXTENSION_ID);
        // simulates a concurrent approve() completing the purchase between this call's first read and its
        // taking the reservation lock - the second read, made under the lock, must be what the status check sees
        when(extensionDao.find(EXTENSION_ID)).thenReturn(java.util.Optional.of(waiting),
                java.util.Optional.of(alreadyPurchased));
        when(reservationService.loadForUpdate(RESERVATION_ID)).thenReturn(reservation);

        assertThatThrownBy(() -> service.approve(EXTENSION_ID)).isInstanceOf(IllegalStateException.class);
        verify(extensionDao, never()).update(any());
        verify(cloudFacade, never()).purchaseCapacityBlockExtension(any(), any());
    }

    @Test
    public void shouldAcceptDurationsWithinTheOneDayStepCeiling() {
        assertThat(CapacityBlockExtensionService.isValidExtensionDuration(MAX_ONE_DAY_STEP_TOTAL_HOURS))
                .isTrue();
    }

    @Test
    public void shouldRejectADurationPastTheOneDayStepCeilingThatIsNotOnTheSevenDayStep() {
        assertThat(CapacityBlockExtensionService.isValidExtensionDuration(
                MAX_ONE_DAY_STEP_TOTAL_HOURS + HOURS_PER_DAY)).isFalse();
    }

    @Test
    public void shouldAcceptASevenDayStepPastTheOneDayCeiling() {
        assertThat(CapacityBlockExtensionService.isValidExtensionDuration(
                MAX_ONE_DAY_STEP_TOTAL_HOURS + SEVEN_DAY_STEP_HOURS)).isTrue();
    }

    @Test
    public void shouldRejectADurationThatWouldExceedTheMaximumSingleExtension() {
        assertThat(CapacityBlockExtensionService.isValidExtensionDuration(
                MAX_TOTAL_EXTENSION_HOURS + SEVEN_DAY_STEP_HOURS)).isFalse();
    }

    @Test
    public void shouldRejectAZeroOrNegativeRequestedDuration() {
        assertThat(CapacityBlockExtensionService.isValidExtensionDuration(0)).isFalse();
        assertThat(CapacityBlockExtensionService.isValidExtensionDuration(-HOURS_PER_DAY)).isFalse();
    }

    private CapacityReservation activeCapacityBlockReservation() {
        final CapacityReservation reservation = CapacityReservationCreatorUtils.getCapacityBlockReservation(1L);
        reservation.setId(RESERVATION_ID);
        reservation.setStatus(CapacityReservationStatus.ACTIVE);
        return reservation;
    }
}
