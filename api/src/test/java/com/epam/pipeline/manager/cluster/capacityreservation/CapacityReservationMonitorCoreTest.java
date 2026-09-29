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
import com.epam.pipeline.manager.cluster.capacityreservation.cloud.CapacityReservationSubmissionUncertainException;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.test.creator.cluster.capacityreservation.CapacityReservationCreatorUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The part of a reservation's life nobody asks for: submission, polling, activation and winding down.
 *
 * <p>The provider is mocked here, which is the point - these are the platform's own decisions about what a
 * provider's answer means, and they are worth testing independently of any cloud.
 */
@RunWith(MockitoJUnitRunner.class)
public class CapacityReservationMonitorCoreTest {

    private static final Long POOL_ID = 1L;
    private static final Long RESERVATION_ID = 10L;
    private static final String CLOUD_ID = "cr-0123456789abcdef0";
    private static final String ZONE = "us-east-1c";
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

    @InjectMocks
    private CapacityReservationMonitorCore monitor;

    @Before
    public void setUp() {
        when(preferenceManager.getPreference(any()))
                .thenReturn(new CapacityReservationMonitorSettings(FINALIZING_LEAD_HOURS));
    }

    @Test
    public void shouldSubmitApprovedReservationAndAwaitTheProvidersDecision() {
        final CapacityReservation reservation = approved();
        givenStatus(CapacityReservationStatus.APPROVED, reservation);
        when(cloudFacade.submit(reservation)).thenReturn(CloudCapacityReservation.builder()
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
        when(cloudFacade.submit(any())).thenReturn(CloudCapacityReservation.builder().build());

        monitor.processApproved();

        assertThat(reservation.getStartDate()).isEqualTo(reservation.getRequestedStartDate());
        assertThat(reservation.getEndDate())
                .isEqualTo(reservation.getRequestedStartDate().plusHours(DURATION_HOURS));
    }

    /**
     * Our own call failing is retryable by the requester, which is what distinguishes it from the provider saying
     * no - so it must not land on the reservation as {@code FAILED}.
     */
    @Test
    public void shouldMarkPurchaseFailedWhenSubmissionThrows() {
        final CapacityReservation reservation = approved();
        givenStatus(CapacityReservationStatus.APPROVED, reservation);
        when(cloudFacade.submit(any())).thenThrow(new IllegalStateException("boom"));

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
        when(cloudFacade.submit(any())).thenThrow(
                new CapacityReservationSubmissionUncertainException("timed out", new IllegalStateException()));

        monitor.processApproved();

        verify(statusService).recordRetry(reservation, "timed out");
        verify(statusService, never()).transition(any(), any(), any());
        // The same attempt, so the retry carries the same token.
        assertThat(reservation.getAttempt()).isEqualTo(attemptBefore);
    }

    /**
     * The create call answers synchronously, and it can refuse the date there and then. That answer must be acted
     * on in the same cycle rather than leaving the reservation sitting in SCHEDULED until the next poll.
     */
    @Test
    public void shouldActOnARefusalReturnedBySubmissionItself() {
        final CapacityReservation reservation = approved();
        final int attemptBefore = reservation.getAttempt();
        givenStatus(CapacityReservationStatus.APPROVED, reservation);
        when(cloudFacade.submit(any())).thenReturn(CloudCapacityReservation.builder()
                .cloudReservationId(CLOUD_ID)
                .state(CloudCapacityReservationState.UNSUPPORTED)
                .build());

        monitor.processApproved();

        verify(statusService)
                .transition(reservation, CapacityReservationStatus.ASSESSING_BY_CLOUD_PROVIDER, null);
        // ...and straight back to APPROVED with the next candidate, without waiting for a poll.
        verify(statusService).transition(eq(reservation), eq(CapacityReservationStatus.APPROVED), any());
        assertThat(reservation.getAttempt()).isEqualTo(attemptBefore + 1);
    }

    /**
     * The normal case: the provider takes the request and is still assessing it, so nothing further happens this
     * cycle.
     */
    @Test
    public void shouldStayScheduledWhenSubmissionIsStillBeingAssessed() {
        final CapacityReservation reservation = approved();
        givenStatus(CapacityReservationStatus.APPROVED, reservation);
        when(cloudFacade.submit(any())).thenReturn(CloudCapacityReservation.builder()
                .cloudReservationId(CLOUD_ID)
                .state(CloudCapacityReservationState.PENDING)
                .build());

        monitor.processApproved();

        verify(statusService)
                .transition(reservation, CapacityReservationStatus.ASSESSING_BY_CLOUD_PROVIDER, null);
        verify(statusService, never())
                .transition(eq(reservation), eq(CapacityReservationStatus.APPROVED), any());
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
    public void shouldReleaseADelayedReservationBeforeAskingForAnotherDate() {
        final CapacityReservation reservation = assessing();
        final int attemptBefore = reservation.getAttempt();
        givenStatus(CapacityReservationStatus.ASSESSING_BY_CLOUD_PROVIDER, reservation);
        when(cloudFacade.describe(reservation)).thenReturn(CloudCapacityReservation.builder()
                .state(CloudCapacityReservationState.DELAYED)
                .build());

        monitor.processAssessing();

        verify(cloudFacade).cancel(reservation);
        assertThat(reservation.getAttempt()).isEqualTo(attemptBefore + 1);
        assertThat(reservation.getCloudReservationId()).isNull();
        verify(statusService).transition(eq(reservation), eq(CapacityReservationStatus.APPROVED), any());
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
        doThrow(new IllegalStateException("provider unreachable")).when(cloudFacade).cancel(reservation);

        monitor.processAssessing();

        assertThat(reservation.getCloudReservationId()).isEqualTo(CLOUD_ID);
        verify(statusService, never())
                .transition(eq(reservation), eq(CapacityReservationStatus.APPROVED), any());
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
        doThrow(new IllegalStateException("provider unreachable")).when(cloudFacade).cancel(reservation);

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

        verify(cloudFacade, never()).submit(any());
        verify(statusService).transition(eq(reservation), eq(CapacityReservationStatus.PURCHASE_FAILED), any());
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
        when(reservationService.loadByStatus(CapacityReservationStatus.SCHEDULED))
                .thenReturn(java.util.Arrays.asList(failing, healthy));
        when(cloudFacade.describe(failing)).thenThrow(new IllegalStateException("provider unreachable"));
        when(cloudFacade.describe(healthy)).thenReturn(CloudCapacityReservation.builder()
                .state(CloudCapacityReservationState.FAILED)
                .build());

        monitor.processScheduled();

        verify(statusService).transition(eq(healthy), eq(CapacityReservationStatus.FAILED), isNull(String.class));
    }

    private void givenStatus(final CapacityReservationStatus status, final CapacityReservation reservation) {
        when(reservationService.loadByStatus(status)).thenReturn(Collections.singletonList(reservation));
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
