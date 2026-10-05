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

/**
 * The part of a reservation's life nobody asks for: submission, polling, activation and winding down.
 *
 * <p>The provider is mocked here, which is the point - these are the platform's own decisions about what a
 * provider's answer means, and they are worth testing independently of any cloud.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class CapacityReservationMonitorCoreTest {

    private static final Long POOL_ID = 1L;
    private static final Long RESERVATION_ID = 10L;
    private static final String CLOUD_ID = "cr-0123456789abcdef0";
    private static final String TIMED_OUT = "timed out";
    private static final String ZONE = "us-east-1c";
    private static final String OTHER_ZONE = "us-east-1d";
    private static final String PROVIDER_UNREACHABLE = "provider unreachable";
    private static final int FINALIZING_LEAD_HOURS = 1;
    private static final int DURATION_HOURS = 24;
    private static final int WINDOW_DAYS = 3;
    private static final int WITHIN_LEAD_MINUTES = 30;
    /** 10 days, shorter than the 24h-per-day duration the fixture requests. */
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
        // Every candidate the fixtures ask for is still one the provider accepts, in the one zone there is, unless a
        // test says otherwise.
        when(cloudFacade.earliestAcceptedStart(any())).thenReturn(DateUtils.nowUTC());
        when(cloudFacade.candidateZones(any())).thenReturn(Collections.singletonList(ZONE));
        // Nothing a lost reply bought, unless a test says otherwise.
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

    /**
     * The first attempt asks for the earliest date the requester would accept.
     */
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

    /**
     * Approval takes time, and a start date that was far enough ahead when requested may no longer be. Submitting it
     * would only be refused, so it moves to the earliest date the provider accepts, with the margin on top.
     */
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
        // Not a slide: the date was never asked for, and the token must still find a lost reply of this attempt.
        assertThat(reservation.getAttempt()).isEqualTo(attemptBefore);
    }

    /**
     * The zone is chosen here, from the zones the platform can launch into - never left to the provider, which could
     * put the capacity somewhere no node of the pool could use it.
     */
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

    /**
     * A refusal is for one date in one zone. Another zone may well have the capacity on the same date, which is the
     * date the requester asked for first - so every zone is tried before the date moves.
     */
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
        // A different zone is a different request, so it gets its own token.
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
        // Every zone gets its turn again for the new date.
        assertThat(reservation.getAvailabilityZone()).isNull();
    }

    /**
     * A retried attempt must ask the provider what the lost call asked: a token reused with other dates is refused,
     * not recognised. So a date the attempt already chose is kept for as long as the provider still accepts it.
     */
    @Test
    public void shouldKeepTheDateARetriedAttemptAlreadyChose() {
        final CapacityReservation reservation = approved();
        final LocalDateTime chosen = reservation.getRequestedStartDate().plusDays(2);
        reservation.setStartDate(chosen);
        reservation.setEndDate(chosen.plusHours(DURATION_HOURS));
        givenStatus(CapacityReservationStatus.APPROVED, reservation);
        // Later than when the date was chosen, but still before it.
        when(cloudFacade.earliestAcceptedStart(reservation)).thenReturn(chosen.minusMinutes(1));
        givenSubmissionReturns(CloudCapacityReservation.builder()
                .cloudReservationId(CLOUD_ID)
                .state(CloudCapacityReservationState.PENDING)
                .build());

        monitor.processApproved();

        assertThat(reservation.getStartDate()).isEqualTo(chosen);
    }

    /**
     * When even the provider's earliest date leaves no room for the duration inside the window, there is nothing to
     * ask for - the request fails with the reason, rather than being bought for a date the requester did not allow.
     */
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

    /**
     * A lost reply may have bought the reservation on a date that could no longer be asked for. The provider agreed to
     * it, so it is adopted - failing the request here would leave it billing with nothing tracking it.
     */
    @Test
    public void shouldAdoptWhatALostSubmissionBoughtEvenWhenNoDateCouldBeAskedForNow() {
        final CapacityReservation reservation = approved();
        givenStatus(CapacityReservationStatus.APPROVED, reservation);
        when(cloudFacade.earliestAcceptedStart(reservation)).thenReturn(reservation.getRequestedEndDate());
        // The facade's lookup finds it, so no dates are chosen at all.
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

    /**
     * Our own call failing is retryable by the requester, which is what distinguishes it from the provider saying
     * no - so it must not land on the reservation as {@code FAILED}.
     */
    @Test
    public void shouldMarkPurchaseFailedWhenSubmissionThrows() {
        final CapacityReservation reservation = approved();
        givenStatus(CapacityReservationStatus.APPROVED, reservation);
        when(cloudFacade.create(any())).thenThrow(new IllegalStateException("boom"));

        monitor.processApproved();

        verify(statusService).transition(eq(reservation), eq(CapacityReservationStatus.PURCHASE_FAILED), any());
    }

    /**
     * A submission whose reply was lost may have bought the reservation. Ending it as {@code PURCHASE_FAILED} would
     * never look at it again, so it stays {@code APPROVED} - the next cycle resubmits the same attempt, whose token
     * finds what the lost call bought.
     */
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
        // The same attempt, so the retry carries the same token.
        assertThat(reservation.getAttempt()).isEqualTo(attemptBefore);
    }

    /**
     * A lookup that could not reach the provider has not found "nothing". Choosing dates and buying anyway would be
     * the blind retry the lookup exists to prevent.
     */
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

    /**
     * The provider's decision is left to the next poll, whatever the create call reported: it is normally still
     * assessing at that point.
     */
    @Test
    public void shouldLeaveTheProvidersDecisionToTheNextPoll() {
        final CapacityReservation reservation = approved();
        final int attemptBefore = reservation.getAttempt();
        givenStatus(CapacityReservationStatus.APPROVED, reservation);
        givenSubmissionReturns(CloudCapacityReservation.builder()
                .cloudReservationId(CLOUD_ID)
                .state(CloudCapacityReservationState.UNSUPPORTED)
                .build());

        monitor.processApproved();

        verify(statusService)
                .transition(reservation, CapacityReservationStatus.ASSESSING_BY_CLOUD_PROVIDER, null);
        verify(statusService, never())
                .transition(eq(reservation), eq(CapacityReservationStatus.APPROVED), any());
        assertThat(reservation.getAttempt()).isEqualTo(attemptBefore);
    }

    /**
     * The provider agreeing is its own step, and a real one: the capacity is spoken for from the start date. It has
     * to be distinguishable from merely having been asked, which is what the separate status is for.
     */
    @Test
    public void shouldMoveToScheduledWhenTheProviderAgrees() {
        final CapacityReservation reservation = assessing();
        givenStatus(CapacityReservationStatus.ASSESSING_BY_CLOUD_PROVIDER, reservation);
        when(cloudFacade.describe(reservation)).thenReturn(CloudCapacityReservation.builder()
                .state(CloudCapacityReservationState.SCHEDULED)
                .build());

        monitor.processAssessing();

        verify(statusService).transition(reservation, CapacityReservationStatus.SCHEDULED, null);
        // The pool is prepared now, and stays switched off until the capacity is live.
        verify(reservationService).schedulePool(reservation);
        verify(reservationService, never()).activatePool(any());
    }

    /**
     * Seen again on every later poll, so it must not try to re-enter the status it is already in - the state
     * machine rejects that.
     */
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

        // The provider's own dates win - what it granted may not be what was asked for.
        assertThat(reservation.getStartDate()).isEqualTo(start);
        verify(statusService).transition(reservation, CapacityReservationStatus.ACTIVE, null);
        verify(reservationService).activatePool(reservation);
        // Prepared when it was scheduled.
        verify(reservationService, never()).schedulePool(any());
    }

    /**
     * A provider may skip straight to live. The pool was then never prepared, and without the reservation's target
     * its nodes would run as plain on-demand ones while the capacity idles.
     */
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

    /**
     * Still being assessed is not news. Nothing should change, least of all the pool.
     */
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

    /**
     * "Not at this date" is not "no". The next candidate inside the requester's window is tried, and the attempt
     * counter moves so the next submission is a genuinely new request rather than a repeat of the last one.
     */
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
        // The old provider id must go, or the idempotency guard would adopt a reservation for the wrong date.
        assertThat(reservation.getCloudReservationId()).isNull();
        verify(statusService).transition(eq(reservation), eq(CapacityReservationStatus.APPROVED), any());
    }

    /**
     * AWS may agree to a shorter commitment than was asked for, and the requester is then bound to the shorter one.
     * Reporting the requested figure back would be untrue about what they owe.
     */
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

    /**
     * Later polls need not repeat it, and a poll that omits it must not erase what was already learned.
     */
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

    /**
     * A delayed reservation is still owed to us, so it has to be handed back before another date is requested -
     * otherwise the platform holds two reservations and has erased the id of the first.
     */
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
        // The release comes after the write, and is for the delayed reservation, not the next attempt.
        final InOrder order = inOrder(statusService, cloudFacade);
        order.verify(statusService).transition(eq(reservation), eq(CapacityReservationStatus.APPROVED), any());
        final ArgumentCaptor<CapacityReservation> released = ArgumentCaptor.forClass(CapacityReservation.class);
        order.verify(cloudFacade).cancel(released.capture());
        assertThat(released.getValue().getCloudReservationId()).isEqualTo(CLOUD_ID);
        assertThat(released.getValue().getAttempt()).isEqualTo(attemptBefore);
        // Its target is taken out of the pool, which the next attempt may never write into again.
        verify(reservationService).deactivatePool(released.getValue());
    }

    /**
     * Only our own writes roll back. Releasing first and then failing to write the new date would leave a reservation
     * the provider has cancelled, which the next poll reads as the request being cancelled - so a write that fails
     * must mean nothing was released.
     */
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

    /**
     * The pass works from a list read before any step ran. A reservation cancelled since must not be submitted - that
     * would buy what the user just gave up.
     */
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

    /**
     * Failing to hand it back must leave the id intact for the next cycle. Sliding anyway would abandon a
     * reservation the provider still intends to deliver, with nothing left pointing at it.
     */
    @Test
    public void shouldKeepTheReservationWhenReleasingADelayedOneFails() {
        final CapacityReservation reservation = assessing();
        givenStatus(CapacityReservationStatus.ASSESSING_BY_CLOUD_PROVIDER, reservation);
        when(cloudFacade.describe(reservation)).thenReturn(CloudCapacityReservation.builder()
                .state(CloudCapacityReservationState.DELAYED)
                .build());
        doThrow(new IllegalStateException(PROVIDER_UNREACHABLE)).when(cloudFacade).cancel(any());

        monitor.processAssessing();

        // The new date was written before the release, so it is the rollback that keeps the delayed reservation -
        // and its id - for the next cycle to release. What is actually persisted is pinned against the database in
        // CapacityReservationMonitorTransactionTest.
        verify(transactionManager).rollback(any());
        verify(transactionManager, never()).commit(any());
    }

    /**
     * An unsupported reservation was never delivered, so there is nothing to give back - and AWS refuses to cancel
     * one anyway. Asking would turn a clean slide into an error.
     */
    @Test
    public void shouldNotTryToReleaseAReservationThatWasNeverDelivered() {
        final CapacityReservation reservation = assessing();
        givenStatus(CapacityReservationStatus.ASSESSING_BY_CLOUD_PROVIDER, reservation);
        when(cloudFacade.describe(reservation)).thenReturn(CloudCapacityReservation.builder()
                .state(CloudCapacityReservationState.UNSUPPORTED)
                .build());

        monitor.processAssessing();

        verify(cloudFacade, never()).cancel(any());
        verify(statusService).transition(eq(reservation), eq(CapacityReservationStatus.APPROVED), any());
    }

    /**
     * Once no start date in the window can still fit the duration, the request has genuinely failed - and the
     * reason has to say so in terms the requester can act on.
     */
    @Test
    public void shouldFailWhenTheWindowIsExhausted() {
        final CapacityReservation reservation = scheduled();
        // One step short of the end: sliding again cannot fit the duration.
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
    public void shouldFailWhenProviderRejectsOutright() {
        final CapacityReservation reservation = scheduled();
        givenStatus(CapacityReservationStatus.SCHEDULED, reservation);
        when(cloudFacade.describe(reservation)).thenReturn(CloudCapacityReservation.builder()
                .state(CloudCapacityReservationState.FAILED)
                .stateReason("insufficient capacity")
                .build());

        monitor.processScheduled();

        verify(statusService).transition(reservation, CapacityReservationStatus.FAILED, "insufficient capacity");
        // It was scheduled, so its target is in the pool - and must not outlive it.
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

    /**
     * A future-dated reservation is created with no end date, because AWS forbids one inside the commitment
     * duration - so nothing at the provider ends it on our behalf. Without this cancel the capacity keeps billing
     * after the pool has stopped using it.
     */
    @Test
    public void shouldGiveTheCapacityBackToTheProviderOnFinish() {
        final CapacityReservation reservation = active();
        reservation.setStatus(CapacityReservationStatus.FINALIZING);
        reservation.setEndDate(DateUtils.nowUTC().minusMinutes(1));
        givenStatus(CapacityReservationStatus.FINALIZING, reservation);

        monitor.processFinalizing();

        verify(cloudFacade).cancel(reservation);
    }

    /**
     * Failing to hand the capacity back must leave the reservation where the next cycle will try again. Marking it
     * finished first would abandon something still being paid for.
     */
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

    /**
     * Capacity blocks are data-model-complete but unimplemented, so a request for one must say so rather than be
     * submitted as if it were an ordinary reservation.
     */
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

    /**
     * A step is several writes that only make sense together, and one reservation's failure must not undo another's
     * progress - so each gets a transaction of its own, and only the failing one is rolled back.
     */
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

    /**
     * Once the provider has answered, a failure is ours to write down rather than the provider's verdict. Recording
     * it as PURCHASE_FAILED would end a reservation the provider may well hold; rolling the step back leaves it
     * APPROVED, and the next cycle's token lookup adopts what this call bought.
     */
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

    /**
     * One reservation's failure must not strand the others - they are independent requests that merely share a
     * cycle.
     */
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

        verify(statusService).transition(eq(healthy), eq(CapacityReservationStatus.FAILED), isNull(String.class));
    }

    /**
     * What the provider creates once the lookup found nothing to adopt and the monitor chose the dates.
     */
    private void givenSubmissionReturns(final CloudCapacityReservation created) {
        when(cloudFacade.create(any())).thenReturn(created);
    }

    /**
     * The pass lists the reservations in a status, and each step reads its reservation again under the pool's lock.
     */
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
