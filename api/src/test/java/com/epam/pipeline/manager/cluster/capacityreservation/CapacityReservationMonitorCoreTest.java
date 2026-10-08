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

import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservation;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservationMonitorSettings;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservationStatus;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservationType;
import com.epam.pipeline.entity.cluster.capacityreservation.CloudCapacityReservation;
import com.epam.pipeline.entity.cluster.capacityreservation.CloudCapacityReservationState;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.cluster.capacityreservation.CapacityReservationMonitor.CapacityReservationMonitorCore;
import com.epam.pipeline.manager.cluster.capacityreservation.cloud.CapacityReservationSubmissionUncertainException;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.test.creator.cluster.capacityreservation.CapacityReservationCreatorUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class CapacityReservationMonitorCoreTest {

    private static final Long POOL_ID = 1L;
    private static final Long RESERVATION_ID = 10L;
    private static final String CLOUD_ID = "cr-0123456789abcdef0";
    private static final String PAYMENT_REASON = "the payment method was declined";
    private static final String REFUSAL_REASON = "the requested capacity is not available";
    private static final String TIMED_OUT = "timed out";
    private static final String ZONE = "us-east-1c";
    private static final String OTHER_ZONE = "us-east-1d";
    private static final String PROVIDER_UNREACHABLE = "provider unreachable";
    private static final int FINALIZING_LEAD_HOURS = 1;
    private static final int DURATION_HOURS = 24;
    private static final int WINDOW_DAYS = 3;
    private static final int WITHIN_LEAD_MINUTES = 30;
    private static final long GRANTED_COMMITMENT_SECONDS = 864000L;

    @Mock
    private CapacityReservationService reservationService;

    @Mock
    private CapacityReservationStatusService statusService;

    @Mock
    private CapacityReservationCloudFacade cloudFacade;

    @Mock
    private PreferenceManager preferenceManager;

    @Mock
    private PlatformTransactionManager transactionManager;

    @InjectMocks
    private CapacityReservationMonitorCore monitor;

    @BeforeEach
    public void setUp() {
        when(preferenceManager.getPreference(any()))
                .thenReturn(new CapacityReservationMonitorSettings(FINALIZING_LEAD_HOURS));
        when(cloudFacade.earliestAcceptedStart(any())).thenReturn(DateUtils.nowUTC());
        when(cloudFacade.candidateZones(any())).thenReturn(Collections.singletonList(ZONE));
        when(cloudFacade.findSubmitted(any())).thenReturn(Optional.empty());
    }

    @Test
    public void shouldSubmitApprovedReservationAndAwaitTheProvidersDecision() {
        final CapacityReservation reservation = approved();
        givenStatus(CapacityReservationStatus.APPROVED, reservation);
        givenSubmissionReturns(CloudCapacityReservation.builder()
                .cloudReservationId(CLOUD_ID)
                .availabilityZone(ZONE)
                .state(CloudCapacityReservationState.PENDING)
                .build());

        monitor.processApproved();

        assertThat(reservation.getCloudReservationId()).isEqualTo(CLOUD_ID);
        assertThat(reservation.getAvailabilityZone()).isEqualTo(ZONE);
        verify(statusService)
                .transition(reservation, CapacityReservationStatus.ASSESSING_BY_CLOUD_PROVIDER, null);
    }

    @Test
    public void shouldSubmitTheEarliestRequestedDateFirst() {
        final CapacityReservation reservation = approved();
        givenStatus(CapacityReservationStatus.APPROVED, reservation);
        givenSubmissionReturns(CloudCapacityReservation.builder().build());

        monitor.processApproved();

        assertThat(reservation.getStartDate()).isEqualTo(reservation.getRequestedStartDate());
        assertThat(reservation.getEndDate())
                .isEqualTo(reservation.getRequestedStartDate().plusHours(DURATION_HOURS));
    }

    @Test
    public void shouldMoveACandidateTheProviderNoLongerAcceptsToTheEarliestItDoes() {
        final CapacityReservation reservation = approved();
        final int attemptBefore = reservation.getAttempt();
        final LocalDateTime accepted = reservation.getRequestedStartDate().plusDays(1);
        final LocalDateTime expectedStart = accepted.plus(CapacityReservationMonitorCore.SUBMISSION_MARGIN);
        givenStatus(CapacityReservationStatus.APPROVED, reservation);
        when(cloudFacade.earliestAcceptedStart(reservation)).thenReturn(accepted);
        givenSubmissionReturns(CloudCapacityReservation.builder()
                .cloudReservationId(CLOUD_ID)
                .state(CloudCapacityReservationState.PENDING)
                .build());

        monitor.processApproved();

        assertThat(reservation.getStartDate()).isEqualTo(expectedStart);
        assertThat(reservation.getEndDate()).isEqualTo(expectedStart.plusHours(DURATION_HOURS));
        assertThat(reservation.getAttempt()).isEqualTo(attemptBefore);
    }

    @Test
    public void shouldAskForTheFirstCandidateZone() {
        final CapacityReservation reservation = approved();
        givenStatus(CapacityReservationStatus.APPROVED, reservation);
        when(cloudFacade.candidateZones(reservation)).thenReturn(Arrays.asList(ZONE, OTHER_ZONE));
        final AtomicReference<String> askedFor = new AtomicReference<>();
        when(cloudFacade.create(any())).thenAnswer(invocation -> {
            askedFor.set(reservation.getAvailabilityZone());
            return CloudCapacityReservation.builder()
                    .cloudReservationId(CLOUD_ID)
                    .state(CloudCapacityReservationState.PENDING)
                    .build();
        });

        monitor.processApproved();

        assertThat(askedFor.get()).isEqualTo(ZONE);
        assertThat(reservation.getAvailabilityZone()).isEqualTo(ZONE);
    }

    @Test
    public void shouldFailWithoutCreatingWhenNoZoneCanTakeTheReservation() {
        final CapacityReservation reservation = approved();
        givenStatus(CapacityReservationStatus.APPROVED, reservation);
        when(cloudFacade.candidateZones(reservation)).thenReturn(Collections.emptyList());

        monitor.processApproved();

        assertThat(reservation.getCloudReservationId()).isNull();
        final ArgumentCaptor<String> reason = ArgumentCaptor.forClass(String.class);
        verify(statusService).transition(eq(reservation), eq(CapacityReservationStatus.FAILED), reason.capture());
        assertThat(reason.getValue()).contains("No availability zone");
        verify(cloudFacade, never()).create(any());
    }

    @Test
    public void shouldTryTheNextZoneBeforeSlidingTheDate() {
        final CapacityReservation reservation = assessing();
        reservation.setAvailabilityZone(ZONE);
        final LocalDateTime date = reservation.getStartDate();
        final int attemptBefore = reservation.getAttempt();
        givenStatus(CapacityReservationStatus.ASSESSING_BY_CLOUD_PROVIDER, reservation);
        when(cloudFacade.candidateZones(reservation)).thenReturn(Arrays.asList(ZONE, OTHER_ZONE));
        when(cloudFacade.describe(reservation)).thenReturn(CloudCapacityReservation.builder()
                .state(CloudCapacityReservationState.UNSUPPORTED)
                .build());

        monitor.processAssessing();

        assertThat(reservation.getAvailabilityZone()).isEqualTo(OTHER_ZONE);
        assertThat(reservation.getStartDate()).isEqualTo(date);
        assertThat(reservation.getAttempt()).isEqualTo(attemptBefore + 1);
        assertThat(reservation.getCloudReservationId()).isNull();
        verify(statusService).transition(eq(reservation), eq(CapacityReservationStatus.APPROVED), any());
    }

    @Test
    public void shouldSlideTheDateOnceEveryZoneWasTried() {
        final CapacityReservation reservation = assessing();
        reservation.setAvailabilityZone(OTHER_ZONE);
        final LocalDateTime date = reservation.getStartDate();
        givenStatus(CapacityReservationStatus.ASSESSING_BY_CLOUD_PROVIDER, reservation);
        when(cloudFacade.candidateZones(reservation)).thenReturn(Arrays.asList(ZONE, OTHER_ZONE));
        when(cloudFacade.describe(reservation)).thenReturn(CloudCapacityReservation.builder()
                .state(CloudCapacityReservationState.UNSUPPORTED)
                .build());

        monitor.processAssessing();

        assertThat(reservation.getStartDate())
                .isEqualTo(date.plusDays(CapacityReservationMonitorCore.SLIDE_WINDOW_STEP_DAYS));
        assertThat(reservation.getAvailabilityZone()).isNull();
    }

    @Test
    public void shouldKeepTheDateARetriedAttemptAlreadyChose() {
        final CapacityReservation reservation = approved();
        final LocalDateTime chosen = reservation.getRequestedStartDate().plusDays(2);
        reservation.setStartDate(chosen);
        reservation.setEndDate(chosen.plusHours(DURATION_HOURS));
        givenStatus(CapacityReservationStatus.APPROVED, reservation);
        when(cloudFacade.earliestAcceptedStart(reservation)).thenReturn(chosen.minusMinutes(1));
        givenSubmissionReturns(CloudCapacityReservation.builder()
                .cloudReservationId(CLOUD_ID)
                .state(CloudCapacityReservationState.PENDING)
                .build());

        monitor.processApproved();

        assertThat(reservation.getStartDate()).isEqualTo(chosen);
    }

    @Test
    public void shouldFailWithoutCreatingWhenNoAcceptedDateFitsTheWindow() {
        final CapacityReservation reservation = approved();
        givenStatus(CapacityReservationStatus.APPROVED, reservation);
        when(cloudFacade.earliestAcceptedStart(reservation)).thenReturn(reservation.getRequestedEndDate());

        monitor.processApproved();

        assertThat(reservation.getCloudReservationId()).isNull();
        final ArgumentCaptor<String> reason = ArgumentCaptor.forClass(String.class);
        verify(statusService).transition(eq(reservation), eq(CapacityReservationStatus.FAILED), reason.capture());
        assertThat(reason.getValue()).contains("earliest start the provider accepts");
        verify(cloudFacade, never()).create(any());
    }

    @Test
    public void shouldAdoptWhatALostSubmissionBoughtEvenWhenNoDateCouldBeAskedForNow() {
        final CapacityReservation reservation = approved();
        givenStatus(CapacityReservationStatus.APPROVED, reservation);
        when(cloudFacade.earliestAcceptedStart(reservation)).thenReturn(reservation.getRequestedEndDate());
        when(cloudFacade.findSubmitted(reservation)).thenReturn(Optional.of(CloudCapacityReservation.builder()
                .cloudReservationId(CLOUD_ID)
                .state(CloudCapacityReservationState.PENDING)
                .build()));

        monitor.processApproved();

        assertThat(reservation.getCloudReservationId()).isEqualTo(CLOUD_ID);
        verify(cloudFacade, never()).candidateZones(any());
        verify(cloudFacade, never()).create(any());
        verify(statusService)
                .transition(reservation, CapacityReservationStatus.ASSESSING_BY_CLOUD_PROVIDER, null);
        verify(statusService, never()).transition(eq(reservation), eq(CapacityReservationStatus.FAILED), any());
    }

    @Test
    public void shouldMarkPurchaseFailedWhenSubmissionThrows() {
        final CapacityReservation reservation = approved();
        givenStatus(CapacityReservationStatus.APPROVED, reservation);
        when(cloudFacade.create(any())).thenThrow(new IllegalStateException("boom"));

        monitor.processApproved();

        verify(statusService).transition(eq(reservation), eq(CapacityReservationStatus.PURCHASE_FAILED), any());
    }

    @Test
    public void shouldKeepTheReservationApprovedWhenTheSubmissionOutcomeIsUncertain() {
        final CapacityReservation reservation = approved();
        final int attemptBefore = reservation.getAttempt();
        givenStatus(CapacityReservationStatus.APPROVED, reservation);
        when(cloudFacade.create(any())).thenThrow(
                new CapacityReservationSubmissionUncertainException(TIMED_OUT, new IllegalStateException()));

        monitor.processApproved();

        verify(statusService).recordRetry(reservation, TIMED_OUT);
        verify(statusService, never()).transition(any(), any(), any());
        assertThat(reservation.getAttempt()).isEqualTo(attemptBefore);
    }

    @Test
    public void shouldNeitherChooseDatesNorBuyWhenTheLookupOutcomeIsUncertain() {
        final CapacityReservation reservation = approved();
        givenStatus(CapacityReservationStatus.APPROVED, reservation);
        when(cloudFacade.findSubmitted(any())).thenThrow(
                new CapacityReservationSubmissionUncertainException(TIMED_OUT, new IllegalStateException()));

        monitor.processApproved();

        verify(statusService).recordRetry(reservation, TIMED_OUT);
        verify(statusService, never()).transition(any(), any(), any());
        verify(cloudFacade, never()).candidateZones(any());
        verify(cloudFacade, never()).create(any());
    }

    @Test
    public void shouldSlideWhenTheSubmissionIsAnsweredAsFailed() {
        assertSubmissionIsSlid(CloudCapacityReservationState.FAILED);
    }

    @Test
    public void shouldSlideWhenTheSubmissionIsAnsweredAsCancelled() {
        assertSubmissionIsSlid(CloudCapacityReservationState.CANCELLED);
    }

    @Test
    public void shouldSlideWhenTheSubmissionIsAnsweredAsUnsupported() {
        assertSubmissionIsSlid(CloudCapacityReservationState.UNSUPPORTED);
    }

    @Test
    public void shouldSlideWhenTheSubmissionIsAnsweredAsExpired() {
        assertSubmissionIsSlid(CloudCapacityReservationState.EXPIRED);
    }

    private void assertSubmissionIsSlid(final CloudCapacityReservationState refusal) {
        final CapacityReservation reservation = approved();
        final int attemptBefore = reservation.getAttempt();
        givenStatus(CapacityReservationStatus.APPROVED, reservation);
        givenSubmissionReturns(CloudCapacityReservation.builder()
                .cloudReservationId(CLOUD_ID)
                .state(refusal)
                .stateReason(REFUSAL_REASON)
                .build());

        monitor.processApproved();

        assertThat(reservation.getStartDate()).isEqualTo(reservation.getRequestedStartDate()
                .plusDays(CapacityReservationMonitorCore.SLIDE_WINDOW_STEP_DAYS));
        assertThat(reservation.getAttempt()).isEqualTo(attemptBefore + 1);
        assertThat(reservation.getCloudReservationId()).isNull();
        verify(statusService).transition(reservation, CapacityReservationStatus.APPROVED, REFUSAL_REASON);
        verify(statusService, never())
                .transition(eq(reservation), eq(CapacityReservationStatus.ASSESSING_BY_CLOUD_PROVIDER), any());
        verify(statusService, never()).transition(eq(reservation), eq(CapacityReservationStatus.FAILED), any());
        verify(statusService, never()).transition(eq(reservation), eq(CapacityReservationStatus.CANCELLED), any());
    }

    @Test
    public void shouldFailTheRequestWhenTheSubmissionIsRefusedAndTheWindowIsSpent() {
        final CapacityReservation reservation = approved();
        reservation.setRequestedEndDate(reservation.getRequestedStartDate().plusHours(DURATION_HOURS));
        givenStatus(CapacityReservationStatus.APPROVED, reservation);
        givenSubmissionReturns(CloudCapacityReservation.builder()
                .cloudReservationId(CLOUD_ID)
                .state(CloudCapacityReservationState.FAILED)
                .stateReason(REFUSAL_REASON)
                .build());

        monitor.processApproved();

        final ArgumentCaptor<String> reason = ArgumentCaptor.forClass(String.class);
        verify(statusService).transition(eq(reservation), eq(CapacityReservationStatus.FAILED), reason.capture());
        assertThat(reason.getValue()).contains("No available start date").contains(REFUSAL_REASON);
        verify(reservationService).deactivatePool(reservation);
    }

    @Test
    public void shouldReleaseAReservationDelayedRightAfterItsSubmission() {
        final CapacityReservation reservation = approved();
        givenStatus(CapacityReservationStatus.APPROVED, reservation);
        givenSubmissionReturns(CloudCapacityReservation.builder()
                .cloudReservationId(CLOUD_ID)
                .state(CloudCapacityReservationState.DELAYED)
                .build());

        monitor.processApproved();

        verify(statusService).transition(eq(reservation), eq(CapacityReservationStatus.APPROVED), any());
        final ArgumentCaptor<CapacityReservation> released = ArgumentCaptor.forClass(CapacityReservation.class);
        verify(cloudFacade).cancel(released.capture());
        assertThat(released.getValue().getCloudReservationId()).isEqualTo(CLOUD_ID);
        verify(reservationService).deactivatePool(reservation);
    }

    @Test
    public void shouldTryTheNextZoneBeforeTheNextDateWhenTheSubmissionIsRefused() {
        final CapacityReservation reservation = approved();
        reservation.setAvailabilityZone(ZONE);
        givenStatus(CapacityReservationStatus.APPROVED, reservation);
        when(cloudFacade.candidateZones(reservation)).thenReturn(Arrays.asList(ZONE, OTHER_ZONE));
        givenSubmissionReturns(CloudCapacityReservation.builder()
                .cloudReservationId(CLOUD_ID)
                .state(CloudCapacityReservationState.FAILED)
                .stateReason(REFUSAL_REASON)
                .build());

        monitor.processApproved();

        assertThat(reservation.getAvailabilityZone()).isEqualTo(OTHER_ZONE);
        assertThat(reservation.getStartDate()).isEqualTo(reservation.getRequestedStartDate());
        verify(statusService).transition(reservation, CapacityReservationStatus.APPROVED, REFUSAL_REASON);
    }

    @Test
    public void shouldFailTheRequestWhenTheProviderCannotBePaid() {
        final CapacityReservation reservation = approved();
        givenStatus(CapacityReservationStatus.APPROVED, reservation);
        givenSubmissionReturns(CloudCapacityReservation.builder()
                .cloudReservationId(CLOUD_ID)
                .state(CloudCapacityReservationState.PAYMENT_FAILED)
                .stateReason(PAYMENT_REASON)
                .build());

        monitor.processApproved();

        verify(statusService).transition(reservation, CapacityReservationStatus.FAILED, PAYMENT_REASON);
        verify(statusService, never()).transition(eq(reservation), eq(CapacityReservationStatus.APPROVED), any());
        verify(reservationService).deactivatePool(reservation);
    }

    @Test
    public void shouldNotSlideAScheduledReservationThatCannotBePaidFor() {
        final CapacityReservation reservation = scheduled();
        givenStatus(CapacityReservationStatus.SCHEDULED, reservation);
        when(cloudFacade.describe(reservation)).thenReturn(CloudCapacityReservation.builder()
                .state(CloudCapacityReservationState.PAYMENT_FAILED)
                .stateReason(PAYMENT_REASON)
                .build());

        monitor.processScheduled();

        verify(statusService).transition(reservation, CapacityReservationStatus.FAILED, PAYMENT_REASON);
        verify(statusService, never()).transition(eq(reservation), eq(CapacityReservationStatus.APPROVED), any());
        verify(reservationService).deactivatePool(reservation);
    }

    @Test
    public void shouldFinishAReservationTheProviderReportsAsExpired() {
        final CapacityReservation reservation = scheduled();
        givenStatus(CapacityReservationStatus.SCHEDULED, reservation);
        when(cloudFacade.describe(reservation)).thenReturn(CloudCapacityReservation.builder()
                .state(CloudCapacityReservationState.EXPIRED)
                .build());

        monitor.processScheduled();

        verify(statusService).transition(eq(reservation), eq(CapacityReservationStatus.FINISHED), any());
        verify(reservationService).deactivatePool(reservation);
    }

    @Test
    public void shouldMoveToScheduledWhenTheProviderAgrees() {
        final CapacityReservation reservation = assessing();
        givenStatus(CapacityReservationStatus.ASSESSING_BY_CLOUD_PROVIDER, reservation);
        when(cloudFacade.describe(reservation)).thenReturn(CloudCapacityReservation.builder()
                .state(CloudCapacityReservationState.SCHEDULED)
                .build());

        monitor.processAssessing();

        verify(statusService).transition(reservation, CapacityReservationStatus.SCHEDULED, null);
        verify(reservationService).schedulePool(reservation);
        verify(reservationService, never()).activatePool(any());
    }

    @Test
    public void shouldNotRepeatTheScheduledTransitionOnLaterPolls() {
        final CapacityReservation reservation = scheduled();
        givenStatus(CapacityReservationStatus.SCHEDULED, reservation);
        when(cloudFacade.describe(reservation)).thenReturn(CloudCapacityReservation.builder()
                .state(CloudCapacityReservationState.SCHEDULED)
                .build());

        monitor.processScheduled();

        verify(statusService, never()).transition(any(), any(), any());
        verify(reservationService, never()).schedulePool(any());
    }

    @Test
    public void shouldActivatePoolWhenProviderReportsActive() {
        final CapacityReservation reservation = scheduled();
        givenStatus(CapacityReservationStatus.SCHEDULED, reservation);
        final LocalDateTime start = DateUtils.nowUTC().plusDays(1);
        when(cloudFacade.describe(reservation)).thenReturn(CloudCapacityReservation.builder()
                .state(CloudCapacityReservationState.ACTIVE)
                .startDate(start)
                .endDate(start.plusHours(DURATION_HOURS))
                .availabilityZone(ZONE)
                .build());

        monitor.processScheduled();

        assertThat(reservation.getStartDate()).isEqualTo(start);
        verify(statusService).transition(reservation, CapacityReservationStatus.ACTIVE, null);
        verify(reservationService).activatePool(reservation);
        verify(reservationService, never()).schedulePool(any());
    }

    @Test
    public void shouldPrepareThePoolWhenTheReservationGoesLiveWithoutBeingScheduled() {
        final CapacityReservation reservation = assessing();
        givenStatus(CapacityReservationStatus.ASSESSING_BY_CLOUD_PROVIDER, reservation);
        when(cloudFacade.describe(reservation)).thenReturn(CloudCapacityReservation.builder()
                .state(CloudCapacityReservationState.ACTIVE)
                .availabilityZone(ZONE)
                .build());

        monitor.processAssessing();

        final InOrder order = inOrder(reservationService);
        order.verify(reservationService).schedulePool(reservation);
        order.verify(reservationService).activatePool(reservation);
    }

    @Test
    public void shouldDoNothingWhileProviderIsStillAssessing() {
        final CapacityReservation reservation = scheduled();
        givenStatus(CapacityReservationStatus.SCHEDULED, reservation);
        when(cloudFacade.describe(reservation)).thenReturn(CloudCapacityReservation.builder()
                .state(CloudCapacityReservationState.PENDING)
                .build());

        monitor.processScheduled();

        verify(statusService, never()).transition(any(), any(), any());
        verify(reservationService, never()).activatePool(any());
    }

    @Test
    public void shouldSlideToTheNextDateWhenProviderCannotSatisfyThisOne() {
        final CapacityReservation reservation = assessing();
        final LocalDateTime firstCandidate = reservation.getStartDate();
        final int attemptBefore = reservation.getAttempt();
        givenStatus(CapacityReservationStatus.ASSESSING_BY_CLOUD_PROVIDER, reservation);
        when(cloudFacade.describe(reservation)).thenReturn(CloudCapacityReservation.builder()
                .state(CloudCapacityReservationState.UNSUPPORTED)
                .build());

        monitor.processAssessing();

        assertThat(reservation.getStartDate())
                .isEqualTo(firstCandidate.plusDays(CapacityReservationMonitorCore.SLIDE_WINDOW_STEP_DAYS));
        assertThat(reservation.getAttempt()).isEqualTo(attemptBefore + 1);
        assertThat(reservation.getCloudReservationId()).isNull();
        verify(statusService).transition(eq(reservation), eq(CapacityReservationStatus.APPROVED), any());
    }

    @Test
    public void shouldRecordTheCommitmentTheProviderActuallyGranted() {
        final CapacityReservation reservation = assessing();
        givenStatus(CapacityReservationStatus.ASSESSING_BY_CLOUD_PROVIDER, reservation);
        when(cloudFacade.describe(reservation)).thenReturn(CloudCapacityReservation.builder()
                .state(CloudCapacityReservationState.SCHEDULED)
                .grantedCommitmentSeconds(GRANTED_COMMITMENT_SECONDS)
                .build());

        monitor.processAssessing();

        assertThat(reservation.getGrantedCommitmentSeconds()).isEqualTo(GRANTED_COMMITMENT_SECONDS);
    }

    @Test
    public void shouldKeepAGrantedCommitmentThatALaterPollOmits() {
        final CapacityReservation reservation = scheduled();
        reservation.setGrantedCommitmentSeconds(GRANTED_COMMITMENT_SECONDS);
        givenStatus(CapacityReservationStatus.SCHEDULED, reservation);
        when(cloudFacade.describe(reservation)).thenReturn(CloudCapacityReservation.builder()
                .state(CloudCapacityReservationState.SCHEDULED)
                .build());

        monitor.processScheduled();

        assertThat(reservation.getGrantedCommitmentSeconds()).isEqualTo(GRANTED_COMMITMENT_SECONDS);
    }

    @Test
    public void shouldReleaseADelayedReservationOnceTheNextDateIsWritten() {
        final CapacityReservation reservation = assessing();
        final int attemptBefore = reservation.getAttempt();
        givenStatus(CapacityReservationStatus.ASSESSING_BY_CLOUD_PROVIDER, reservation);
        when(cloudFacade.describe(reservation)).thenReturn(CloudCapacityReservation.builder()
                .state(CloudCapacityReservationState.DELAYED)
                .build());

        monitor.processAssessing();

        assertThat(reservation.getAttempt()).isEqualTo(attemptBefore + 1);
        assertThat(reservation.getCloudReservationId()).isNull();
        final InOrder order = inOrder(statusService, cloudFacade);
        order.verify(statusService).transition(eq(reservation), eq(CapacityReservationStatus.APPROVED), any());
        final ArgumentCaptor<CapacityReservation> released = ArgumentCaptor.forClass(CapacityReservation.class);
        order.verify(cloudFacade).cancel(released.capture());
        assertThat(released.getValue().getCloudReservationId()).isEqualTo(CLOUD_ID);
        assertThat(released.getValue().getAttempt()).isEqualTo(attemptBefore);
        verify(reservationService).deactivatePool(reservation);
    }

    @Test
    public void shouldNotReleaseADelayedReservationWhenTheNextDateCannotBeWritten() {
        final CapacityReservation reservation = assessing();
        givenStatus(CapacityReservationStatus.ASSESSING_BY_CLOUD_PROVIDER, reservation);
        when(cloudFacade.describe(reservation)).thenReturn(CloudCapacityReservation.builder()
                .state(CloudCapacityReservationState.DELAYED)
                .build());
        when(statusService.transition(eq(reservation), eq(CapacityReservationStatus.APPROVED), any()))
                .thenThrow(new IllegalStateException("database unavailable"));

        monitor.processAssessing();

        verify(cloudFacade, never()).cancel(any());
        verify(transactionManager).rollback(any());
    }

    @Test
    public void shouldSkipAReservationThatMovedOnBeforeItsTurn() {
        final CapacityReservation listed = approved();
        givenStatus(CapacityReservationStatus.APPROVED, listed);
        final CapacityReservation current = approved();
        current.setStatus(CapacityReservationStatus.CANCELLED);
        when(reservationService.loadForUpdate(listed.getId())).thenReturn(current);

        monitor.processApproved();

        verify(cloudFacade, never()).findSubmitted(any());
        verify(cloudFacade, never()).create(any());
        verify(statusService, never()).transition(any(), any(), any());
    }

    @Test
    public void shouldKeepTheReservationWhenReleasingADelayedOneFails() {
        final CapacityReservation reservation = assessing();
        givenStatus(CapacityReservationStatus.ASSESSING_BY_CLOUD_PROVIDER, reservation);
        when(cloudFacade.describe(reservation)).thenReturn(CloudCapacityReservation.builder()
                .state(CloudCapacityReservationState.DELAYED)
                .build());
        doThrow(new IllegalStateException(PROVIDER_UNREACHABLE)).when(cloudFacade).cancel(any());

        monitor.processAssessing();

        verify(transactionManager).rollback(any());
        verify(transactionManager, never()).commit(any());
    }

    @Test
    public void shouldReleaseTheReservationItAbandonsWhenTheProviderRefusesIt() {
        final CapacityReservation reservation = assessing();
        givenStatus(CapacityReservationStatus.ASSESSING_BY_CLOUD_PROVIDER, reservation);
        when(cloudFacade.describe(reservation)).thenReturn(CloudCapacityReservation.builder()
                .state(CloudCapacityReservationState.UNSUPPORTED)
                .build());

        monitor.processAssessing();

        final ArgumentCaptor<CapacityReservation> abandoned = ArgumentCaptor.forClass(CapacityReservation.class);
        verify(cloudFacade).cancel(abandoned.capture());
        assertThat(abandoned.getValue().getCloudReservationId()).isEqualTo(CLOUD_ID);
        verify(statusService).transition(eq(reservation), eq(CapacityReservationStatus.APPROVED), any());
    }

    @Test
    public void shouldNotAskTheProviderToReleaseAReservationItNeverGaveUs() {
        final CapacityReservation reservation = approved();
        givenStatus(CapacityReservationStatus.APPROVED, reservation);
        when(cloudFacade.create(any())).thenReturn(CloudCapacityReservation.builder()
                .state(CloudCapacityReservationState.FAILED)
                .build());

        monitor.processApproved();

        verify(cloudFacade, never()).cancel(any());
        verify(statusService).transition(eq(reservation), eq(CapacityReservationStatus.APPROVED), any());
    }

    @Test
    public void shouldFailWhenTheWindowIsExhausted() {
        final CapacityReservation reservation = scheduled();
        reservation.setStartDate(reservation.getRequestedEndDate().minusHours(DURATION_HOURS));
        givenStatus(CapacityReservationStatus.SCHEDULED, reservation);
        when(cloudFacade.describe(reservation)).thenReturn(CloudCapacityReservation.builder()
                .state(CloudCapacityReservationState.UNSUPPORTED)
                .build());

        monitor.processScheduled();

        final ArgumentCaptor<String> reason = ArgumentCaptor.forClass(String.class);
        verify(statusService).transition(eq(reservation), eq(CapacityReservationStatus.FAILED),
                reason.capture());
        assertThat(reason.getValue()).contains("No available start date");
        verify(reservationService, never()).activatePool(any());
    }

    @Test
    public void shouldSlideAndReleaseThePoolWhenProviderRejectsAScheduledReservation() {
        final CapacityReservation reservation = scheduled();
        final LocalDateTime date = reservation.getStartDate();
        givenStatus(CapacityReservationStatus.SCHEDULED, reservation);
        when(cloudFacade.describe(reservation)).thenReturn(CloudCapacityReservation.builder()
                .state(CloudCapacityReservationState.FAILED)
                .stateReason("insufficient capacity")
                .build());

        monitor.processScheduled();

        assertThat(reservation.getStartDate())
                .isEqualTo(date.plusDays(CapacityReservationMonitorCore.SLIDE_WINDOW_STEP_DAYS));
        verify(statusService).transition(reservation, CapacityReservationStatus.APPROVED, "insufficient capacity");
        verify(statusService, never()).transition(eq(reservation), eq(CapacityReservationStatus.FAILED), any());
        verify(reservationService).deactivatePool(reservation);
    }

    @Test
    public void shouldSlideWhenProviderCancelsAScheduledReservationOnItsOwn() {
        final CapacityReservation reservation = scheduled();
        givenStatus(CapacityReservationStatus.SCHEDULED, reservation);
        when(cloudFacade.describe(reservation)).thenReturn(CloudCapacityReservation.builder()
                .state(CloudCapacityReservationState.CANCELLED)
                .build());

        monitor.processScheduled();

        verify(statusService).transition(eq(reservation), eq(CapacityReservationStatus.APPROVED), any());
        verify(statusService, never()).transition(eq(reservation), eq(CapacityReservationStatus.CANCELLED), any());
        verify(reservationService).deactivatePool(reservation);
    }

    @Test
    public void shouldWarnAsTheEndApproaches() {
        final CapacityReservation reservation = active();
        reservation.setEndDate(DateUtils.nowUTC().plusMinutes(WITHIN_LEAD_MINUTES));
        givenStatus(CapacityReservationStatus.ACTIVE, reservation);

        monitor.processActive();

        verify(statusService).transition(reservation, CapacityReservationStatus.FINALIZING, null);
    }

    @Test
    public void shouldNotWarnWhileTheEndIsFarOff() {
        final CapacityReservation reservation = active();
        reservation.setEndDate(DateUtils.nowUTC().plusDays(2));
        givenStatus(CapacityReservationStatus.ACTIVE, reservation);

        monitor.processActive();

        verify(statusService, never()).transition(any(), any(), any());
    }

    @Test
    public void shouldFinishAndDeactivateThePoolOnceTheEndHasPassed() {
        final CapacityReservation reservation = active();
        reservation.setStatus(CapacityReservationStatus.FINALIZING);
        reservation.setEndDate(DateUtils.nowUTC().minusMinutes(1));
        givenStatus(CapacityReservationStatus.FINALIZING, reservation);

        monitor.processFinalizing();

        verify(statusService).transition(reservation, CapacityReservationStatus.FINISHED, null);
        verify(reservationService).deactivatePool(reservation);
    }

    @Test
    public void shouldGiveTheCapacityBackToTheProviderOnFinish() {
        final CapacityReservation reservation = active();
        reservation.setStatus(CapacityReservationStatus.FINALIZING);
        reservation.setEndDate(DateUtils.nowUTC().minusMinutes(1));
        givenStatus(CapacityReservationStatus.FINALIZING, reservation);

        monitor.processFinalizing();

        verify(cloudFacade).cancel(reservation);
    }

    @Test
    public void shouldNotMarkFinishedWhenTheProviderCancelFails() {
        final CapacityReservation reservation = active();
        reservation.setStatus(CapacityReservationStatus.FINALIZING);
        reservation.setEndDate(DateUtils.nowUTC().minusMinutes(1));
        givenStatus(CapacityReservationStatus.FINALIZING, reservation);
        doThrow(new IllegalStateException(PROVIDER_UNREACHABLE)).when(cloudFacade).cancel(reservation);

        monitor.processFinalizing();

        verify(statusService, never())
                .transition(eq(reservation), eq(CapacityReservationStatus.FINISHED), any());
        verify(reservationService, never()).deactivatePool(any());
    }

    @Test
    public void shouldNotFinishBeforeTheEnd() {
        final CapacityReservation reservation = active();
        reservation.setStatus(CapacityReservationStatus.FINALIZING);
        reservation.setEndDate(DateUtils.nowUTC().plusHours(2));
        givenStatus(CapacityReservationStatus.FINALIZING, reservation);

        monitor.processFinalizing();

        verify(reservationService, never()).deactivatePool(any());
    }

    @Test
    public void shouldRejectUnsupportedReservationTypeWithoutCallingTheProvider() {
        final CapacityReservation reservation = approved();
        reservation.setReservationType(CapacityReservationType.CAPACITY_BLOCK);
        givenStatus(CapacityReservationStatus.APPROVED, reservation);

        monitor.processApproved();

        verify(cloudFacade, never()).findSubmitted(any());
        verify(cloudFacade, never()).create(any());
        verify(statusService).transition(eq(reservation), eq(CapacityReservationStatus.PURCHASE_FAILED), any());
    }

    @Test
    public void shouldRunEachReservationInATransactionOfItsOwn() {
        final CapacityReservation failing = scheduled();
        final CapacityReservation healthy = scheduled();
        healthy.setId(RESERVATION_ID + 1);
        givenStatus(CapacityReservationStatus.SCHEDULED, failing, healthy);
        when(cloudFacade.describe(failing)).thenThrow(new IllegalStateException(PROVIDER_UNREACHABLE));
        when(cloudFacade.describe(healthy)).thenReturn(CloudCapacityReservation.builder()
                .state(CloudCapacityReservationState.FAILED)
                .build());

        monitor.processScheduled();

        verify(transactionManager, times(2)).getTransaction(any());
        verify(transactionManager).rollback(any());
        verify(transactionManager).commit(any());
    }

    @Test
    public void shouldRollBackRatherThanFailWhenOurOwnWriteFailsAfterSubmission() {
        final CapacityReservation reservation = approved();
        givenStatus(CapacityReservationStatus.APPROVED, reservation);
        givenSubmissionReturns(CloudCapacityReservation.builder()
                .cloudReservationId(CLOUD_ID)
                .state(CloudCapacityReservationState.PENDING)
                .build());
        when(statusService.transition(reservation, CapacityReservationStatus.ASSESSING_BY_CLOUD_PROVIDER, null))
                .thenThrow(new IllegalStateException("database unavailable"));

        monitor.processApproved();

        verify(statusService, never())
                .transition(eq(reservation), eq(CapacityReservationStatus.PURCHASE_FAILED), any());
        verify(transactionManager).rollback(any());
    }

    @Test
    public void shouldKeepProcessingAfterOneReservationFails() {
        final CapacityReservation failing = scheduled();
        final CapacityReservation healthy = scheduled();
        healthy.setId(RESERVATION_ID + 1);
        givenStatus(CapacityReservationStatus.SCHEDULED, failing, healthy);
        when(cloudFacade.describe(failing)).thenThrow(new IllegalStateException(PROVIDER_UNREACHABLE));
        when(cloudFacade.describe(healthy)).thenReturn(CloudCapacityReservation.builder()
                .state(CloudCapacityReservationState.FAILED)
                .build());

        monitor.processScheduled();

        verify(statusService).transition(eq(healthy), eq(CapacityReservationStatus.APPROVED), isNull(String.class));
    }

    private void givenSubmissionReturns(final CloudCapacityReservation created) {
        when(cloudFacade.create(any())).thenReturn(created);
    }

    private void givenStatus(final CapacityReservationStatus status, final CapacityReservation... reservations) {
        when(reservationService.loadByStatus(status)).thenReturn(Arrays.asList(reservations));
        for (final CapacityReservation reservation : reservations) {
            when(reservationService.loadForUpdate(reservation.getId())).thenReturn(reservation);
        }
    }

    private CapacityReservation approved() {
        final CapacityReservation reservation = CapacityReservationCreatorUtils.getReservation(POOL_ID);
        reservation.setId(RESERVATION_ID);
        reservation.setStatus(CapacityReservationStatus.APPROVED);
        reservation.setDurationHours(DURATION_HOURS);
        reservation.setRequestedStartDate(DateUtils.nowUTC().plusDays(1));
        reservation.setRequestedEndDate(DateUtils.nowUTC().plusDays(1 + WINDOW_DAYS));
        reservation.setStartDate(null);
        reservation.setEndDate(null);
        return reservation;
    }

    private CapacityReservation assessing() {
        final CapacityReservation reservation = approved();
        reservation.setStatus(CapacityReservationStatus.ASSESSING_BY_CLOUD_PROVIDER);
        reservation.setCloudReservationId(CLOUD_ID);
        reservation.setStartDate(reservation.getRequestedStartDate());
        reservation.setEndDate(reservation.getRequestedStartDate().plusHours(DURATION_HOURS));
        return reservation;
    }

    private CapacityReservation scheduled() {
        final CapacityReservation reservation = approved();
        reservation.setStatus(CapacityReservationStatus.SCHEDULED);
        reservation.setCloudReservationId(CLOUD_ID);
        reservation.setStartDate(reservation.getRequestedStartDate());
        reservation.setEndDate(reservation.getRequestedStartDate().plusHours(DURATION_HOURS));
        return reservation;
    }

    private CapacityReservation active() {
        final CapacityReservation reservation = scheduled();
        reservation.setStatus(CapacityReservationStatus.ACTIVE);
        return reservation;
    }
}
