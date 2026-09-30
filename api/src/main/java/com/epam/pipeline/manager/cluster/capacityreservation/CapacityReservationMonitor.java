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
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.scheduling.AbstractSchedulingManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.SchedulerLock;
import org.apache.commons.collections4.ListUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Schedules {@link CapacityReservationMonitorCore}, which drives a capacity reservation through the part of its life
 * that nobody asks for.
 *
 * <p>Split in two for the reason every scheduled manager here is: {@code @SchedulerLock} applies only when the
 * locked method is called through the proxy, so the schedule lives outside the bean it drives.
 */
@Service
@RequiredArgsConstructor
public class CapacityReservationMonitor extends AbstractSchedulingManager {

    private final CapacityReservationMonitorCore core;

    @PostConstruct
    public void init() {
        scheduleSecured(core::monitor, SystemPreferences.CLUSTER_CAPACITY_RESERVATION_MONITOR_CRON,
                "CapacityReservationMonitor");
    }

    /**
     * Drives a capacity reservation through the part of its life that nobody asks for: submitting an approved
     * request, watching what the provider makes of it, activating the pool once capacity exists, and winding the pool
     * down when it ends.
     *
     * <p>Each pass handles one status, and each reservation's step in it commits or rolls back on its own. A
     * reservation can still move through more than one pass in a cycle - a submission the provider agrees to at
     * once is scheduled straight away - but each move is an independently visible, independently retryable event
     * rather than a long chain that can half-fail.
     *
     * <p>Note what this does <em>not</em> do: it never approves anything. Approval is a human's or a policy's
     * decision, and a monitor silently promoting {@code REQUIRED_APPROVE} would remove the only gate on spending.
     */
    @Component
    @Slf4j
    static class CapacityReservationMonitorCore {

        /**
         * How far a future-dated start date moves forward when the provider reports the current candidate is not
         * available. A day is the granularity the search is worth doing at: each candidate costs a provider
         * assessment cycle, so a finer step buys little and a coarser one skips dates that would have been granted.
         */
        protected static final int SLIDE_WINDOW_STEP_DAYS = 1;

        /**
         * Added to the provider's minimum lead when a start date has to be chosen for it. The lead is measured by the
         * provider's clock when the request arrives, so a date exactly on it would be refused - and a date chosen with
         * room to spare is still accepted when a lost submission is retried a cycle later, so the retry can ask for
         * the same date.
         */
        static final Duration SUBMISSION_MARGIN = Duration.ofHours(1);

        private static final int SECONDS_PER_HOUR = 3600;

        private final CapacityReservationService reservationService;
        private final CapacityReservationStatusService statusService;
        private final CapacityReservationCloudFacade cloudFacade;
        private final PreferenceManager preferenceManager;
        private final TransactionTemplate transaction;

        @Autowired
        CapacityReservationMonitorCore(final CapacityReservationService reservationService,
                                       final CapacityReservationStatusService statusService,
                                       final CapacityReservationCloudFacade cloudFacade,
                                       final PreferenceManager preferenceManager,
                                       final PlatformTransactionManager transactionManager) {
            this.reservationService = reservationService;
            this.statusService = statusService;
            this.cloudFacade = cloudFacade;
            this.preferenceManager = preferenceManager;
            this.transaction = new TransactionTemplate(transactionManager);
            // A reservation's step commits or rolls back on its own, whatever the caller is doing.
            this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        }

        @SchedulerLock(name = "CapacityReservationMonitor_monitor", lockAtMostForString = "PT5M")
        public void monitor() {
            processApproved();
            processAssessing();
            processScheduled();
            processActive();
            processFinalizing();
        }

        /**
         * Submits approved requests to their provider.
         */
        void processApproved() {
            forEach(CapacityReservationStatus.APPROVED, this::submit);
        }

        /**
         * Asks the provider whether it has decided yet.
         */
        void processAssessing() {
            forEach(CapacityReservationStatus.ASSESSING_BY_CLOUD_PROVIDER, this::poll);
        }

        /**
         * Watches an agreed reservation for its start date - and for the provider withdrawing it.
         */
        void processScheduled() {
            forEach(CapacityReservationStatus.SCHEDULED, this::poll);
        }

        /**
         * Warns while there is still time to act.
         */
        void processActive() {
            forEach(CapacityReservationStatus.ACTIVE, this::finalizeIfEnding);
        }

        /**
         * Winds the pool down once the capacity is gone.
         */
        void processFinalizing() {
            forEach(CapacityReservationStatus.FINALIZING, this::finishIfEnded);
        }

        /**
         * Catches the provider call broadly on purpose: a failed submission must never escape and leave the reservation
         * unexplained.
         *
         * <p>Two kinds of failure, handled differently. When the outcome is uncertain the provider may already hold a
         * reservation for this attempt, so it stays {@code APPROVED} and is submitted again next cycle with the same
         * token - which adopts what the lost call bought instead of abandoning it to bill unseen. A refusal, or a
         * failure on our side, lands on {@code PURCHASE_FAILED}.
         */
        @SuppressWarnings("PMD.AvoidCatchingGenericException")
        void submit(final CapacityReservation reservation) {
            if (CapacityReservationType.FUTURE_DATED != reservation.getReservationType()) {
                statusService.transition(reservation, CapacityReservationStatus.PURCHASE_FAILED,
                        reservation.getReservationType() + " reservations are not supported yet");
                return;
            }
            final Optional<CloudCapacityReservation> result;
            try {
                result = cloudFacade.submit(reservation, () -> prepareCandidate(reservation));
            } catch (CapacityReservationSubmissionUncertainException e) {
                log.warn("Submission of capacity reservation {} did not complete and will be retried: {}",
                        reservation.getId(), e.getMessage());
                statusService.recordRetry(reservation, e.getMessage());
                return;
            } catch (Exception e) {
                log.error("Failed to submit capacity reservation {}", reservation.getId(), e);
                statusService.transition(reservation, CapacityReservationStatus.PURCHASE_FAILED, e.getMessage());
                return;
            }
            if (!result.isPresent()) {
                // Nothing to adopt and no date left to ask for. A verdict on the window, like a provider refusing every
                // date in it - so FAILED, not PURCHASE_FAILED.
                statusService.transition(reservation, CapacityReservationStatus.FAILED,
                        noAcceptedStartDateLeft(reservation));
                return;
            }
            final CloudCapacityReservation submitted = result.get();
            // Outside the catch on purpose: a failure from here on is ours to write down, not the provider's answer.
            // It rolls the whole step back, and the next cycle resubmits the same attempt, whose token adopts what this
            // one bought.
            reservation.setCloudReservationId(submitted.getCloudReservationId());
            reservation.setAvailabilityZone(submitted.getAvailabilityZone());
            statusService.transition(reservation, CapacityReservationStatus.ASSESSING_BY_CLOUD_PROVIDER, null);
            // The create call answers synchronously - normally with the provider still assessing the date, but it
            // may already have agreed, or refused outright. Acting on that answer now rather than waiting for the
            // next cycle means a refusal starts the search for another date immediately.
            applyState(reservation, submitted);
        }

        /**
         * Chooses the dates this attempt asks for, or finds that none are left.
         *
         * <p>The first attempt asks for the earliest date the requester would accept. Later attempts have already
         * been moved on by {@link #slideOrFail}, so this only fills in a first candidate.
         *
         * <p>Either way the candidate has to be one the provider still accepts. Time passes between the request and
         * its submission - waiting for approval, sliding past refused dates - and a start date that was far enough
         * ahead when requested may no longer be. Submitting it anyway would only be refused, so it moves to the
         * earliest date the provider accepts, plus {@link #SUBMISSION_MARGIN}.
         *
         * <p>The attempt - and so the token - stays the same: the date was never asked for. And a retry of the attempt
         * keeps whatever date it already chose, which {@code recordRetry} persists, for as long as the provider still
         * accepts it; the margin is what makes that last across retries. So a retry asks the provider exactly what the
         * lost call asked, and the provider recognises the repeat instead of refusing a token reused with other dates.
         *
         * <p>Only called once the lookup has found nothing to adopt - see
         * {@link CapacityReservationCloudFacade#submit}.
         *
         * @return false when no date the provider accepts leaves room for the duration inside the requested window
         */
        private boolean prepareCandidate(final CapacityReservation reservation) {
            if (reservation.getStartDate() == null) {
                reservation.setStartDate(reservation.getRequestedStartDate());
                reservation.setEndDate(reservation.getRequestedStartDate()
                        .plusHours(reservation.getDurationHours()));
            }
            if (!reservation.getStartDate().isBefore(cloudFacade.earliestAcceptedStart(reservation))) {
                return true;
            }
            final LocalDateTime start = cloudFacade.earliestAcceptedStart(reservation).plus(SUBMISSION_MARGIN);
            final LocalDateTime end = start.plusHours(reservation.getDurationHours());
            if (end.isAfter(reservation.getRequestedEndDate())) {
                return false;
            }
            log.debug("Capacity reservation {} can no longer start at {} with this provider; asking for {} instead",
                    reservation.getId(), reservation.getStartDate(), start);
            reservation.setStartDate(start);
            reservation.setEndDate(end);
            return true;
        }

        private String noAcceptedStartDateLeft(final CapacityReservation reservation) {
            return String.format("No start date between %s and %s leaves room for a reservation of %d hours: the "
                            + "earliest start the provider accepts now is %s", reservation.getRequestedStartDate(),
                    reservation.getRequestedEndDate(), reservation.getDurationHours(),
                    cloudFacade.earliestAcceptedStart(reservation));
        }

        void poll(final CapacityReservation reservation) {
            applyState(reservation, cloudFacade.describe(reservation));
        }

        /**
         * Acts on what the provider says about a reservation. Shared by the submit and poll paths, because the create
         * call reports a state too and it deserves the same treatment as a later poll.
         */
        private void applyState(final CapacityReservation reservation, final CloudCapacityReservation cloud) {
            final CloudCapacityReservationState state = cloud.getState();
            log.debug("Capacity reservation {} is {} at its provider", reservation.getId(), state);

            recordGrantedCommitment(reservation, cloud);

            switch (state) {
                case ACTIVE:
                    activate(reservation, cloud);
                    break;
                case SCHEDULED:
                    // The provider has agreed. Guarded because this is also seen on every later poll, and repeating a
                    // transition it is already in would be rejected by the state machine.
                    if (CapacityReservationStatus.SCHEDULED != reservation.getStatus()) {
                        Optional.ofNullable(cloud.getStartDate()).ifPresent(reservation::setStartDate);
                        Optional.ofNullable(cloud.getEndDate()).ifPresent(reservation::setEndDate);
                        statusService.transition(reservation, CapacityReservationStatus.SCHEDULED, null);
                    }
                    break;
                case UNSUPPORTED:
                    // Not a refusal of the request, only of this date, and nothing was ever delivered - so the next
                    // candidate can be asked for directly.
                    slideOrFail(reservation, cloud.getStateReason());
                    break;
                case DELAYED:
                    // The provider still owes us this reservation, so it has to be handed back before another is
                    // asked for - otherwise we would hold two.
                    releaseAndSlide(reservation, cloud.getStateReason());
                    break;
                case FAILED:
                    statusService.transition(reservation, CapacityReservationStatus.FAILED, cloud.getStateReason());
                    break;
                case CANCELLED:
                    statusService.transition(reservation, CapacityReservationStatus.CANCELLED,
                            cloud.getStateReason());
                    break;
                case EXPIRED:
                    statusService.transition(reservation, CapacityReservationStatus.FINISHED, cloud.getStateReason());
                    break;
                default:
                    // PENDING: the provider is still deciding, so ask again next cycle.
                    break;
            }
        }

        /**
         * The capacity exists, so make the pool usable and record the dates the provider actually gave us.
         */
        private void activate(final CapacityReservation reservation, final CloudCapacityReservation cloud) {
            Optional.ofNullable(cloud.getStartDate()).ifPresent(reservation::setStartDate);
            Optional.ofNullable(cloud.getEndDate()).ifPresent(reservation::setEndDate);
            Optional.ofNullable(cloud.getAvailabilityZone()).ifPresent(reservation::setAvailabilityZone);
            statusService.transition(reservation, CapacityReservationStatus.ACTIVE, null);
            reservationService.activatePool(reservation);
        }

        /**
         * Records what the provider committed us to, whenever it says.
         */
        private void recordGrantedCommitment(final CapacityReservation reservation,
                                             final CloudCapacityReservation cloud) {
            Optional.ofNullable(cloud.getGrantedCommitmentSeconds())
                    .filter(granted -> !granted.equals(reservation.getGrantedCommitmentSeconds()))
                    .ifPresent(granted -> {
                        if (reservation.getDurationHours() != null
                                && granted < (long) reservation.getDurationHours() * SECONDS_PER_HOUR) {
                            log.warn("Capacity reservation {} was granted a commitment of {}s, shorter than the {}h "
                                            + "requested", reservation.getId(), granted,
                                    reservation.getDurationHours());
                        }
                        reservation.setGrantedCommitmentSeconds(granted);
                    });
        }

        /**
         * Gives a reservation back to the provider and asks for another date.
         *
         * <p>The new date is written first and the release comes last, because only our own writes roll back. If the
         * release went first and a write after it failed, the step would roll back to a reservation the provider has
         * already cancelled - and the next poll would read that as the request being cancelled, ending the search.
         * This way a failed release rolls back the new date with it, and the next cycle finds the delayed
         * reservation still there to release.
         */
        private void releaseAndSlide(final CapacityReservation reservation, final String reason) {
            final CapacityReservation delayed = releaseHandle(reservation);
            slideOrFail(reservation, reason);
            cloudFacade.cancel(delayed);
        }

        /**
         * What the provider needs to release a reservation, captured before a slide moves the reservation on to its
         * next attempt and clears the provider id.
         */
        private static CapacityReservation releaseHandle(final CapacityReservation reservation) {
            return CapacityReservation.builder()
                    .id(reservation.getId())
                    .regionId(reservation.getRegionId())
                    .cloudProvider(reservation.getCloudProvider())
                    .cloudReservationId(reservation.getCloudReservationId())
                    .attempt(reservation.getAttempt())
                    .build();
        }

        private static String noStartDateLeft(final CapacityReservation reservation, final String reason) {
            return String.format("No available start date between %s and %s for a reservation of %d hours%s",
                    reservation.getRequestedStartDate(), reservation.getRequestedEndDate(),
                    reservation.getDurationHours(), reason == null ? "" : ". Last provider response: " + reason);
        }

        /**
         * Moves a future-dated request to the next start date inside the window the requester allowed, or gives up
         * when the window cannot fit the duration any more.
         *
         * <p>The attempt counter is what makes this safe to repeat: it feeds the idempotency token, so each candidate
         * is a genuinely new request to the provider rather than a retry of the last one.
         */
        private void slideOrFail(final CapacityReservation reservation, final String reason) {
            final LocalDateTime nextStart = reservation.getStartDate().plusDays(SLIDE_WINDOW_STEP_DAYS);
            final LocalDateTime nextEnd = nextStart.plusHours(reservation.getDurationHours());

            if (nextEnd.isAfter(reservation.getRequestedEndDate())) {
                statusService.transition(reservation, CapacityReservationStatus.FAILED,
                        noStartDateLeft(reservation, reason));
                return;
            }

            log.debug("Capacity reservation {} is unavailable at {}; trying {}",
                    reservation.getId(), reservation.getStartDate(), nextStart);
            reservation.setStartDate(nextStart);
            reservation.setEndDate(nextEnd);
            reservation.setAttempt(reservation.getAttempt() + 1);
            reservation.setCloudReservationId(null);
            statusService.transition(reservation, CapacityReservationStatus.APPROVED, reason);
        }

        void finalizeIfEnding(final CapacityReservation reservation) {
            if (reservation.getEndDate() == null) {
                return;
            }
            final LocalDateTime warnFrom = reservation.getEndDate()
                    .minusHours(settings().getFinalizingLeadHours());
            if (!DateUtils.nowUTC().isBefore(warnFrom)) {
                statusService.transition(reservation, CapacityReservationStatus.FINALIZING, null);
            }
        }

        /**
         * Ends the reservation once its period is over - at the provider as well as in the database.
         *
         * <p>The cancel goes first, and an unchanged status is the deliberate consequence of it failing - a reservation
         * left in {@code FINALIZING} is retried on the next cycle, whereas marking it {@code FINISHED} first would
         * abandon a paid reservation that nothing would ever look at again.
         */
        void finishIfEnded(final CapacityReservation reservation) {
            if (reservation.getEndDate() == null) {
                return;
            }
            if (!DateUtils.nowUTC().isBefore(reservation.getEndDate())) {
                cloudFacade.cancel(reservation);
                statusService.transition(reservation, CapacityReservationStatus.FINISHED, null);
                reservationService.deactivatePool(reservation);
            }
        }

        /**
         * Runs a step for every reservation in a status, each in a transaction of its own.
         *
         * <p>Per reservation, because a step is several writes that only make sense together - a status and the pool
         * it switches on or off, with the notification about it - and one reservation's failure must not stop, or roll
         * back, the others: they are independent requests that happen to share a cycle. A step that fails is rolled
         * back whole and retried on the next cycle, which every step is written to survive: a provider call it made
         * before the failure is either idempotent, or - a release - made last, after everything that could fail.
         *
         * <p>Each step holds its pool's lock throughout, taken before the reservation is read, which is the order every
         * write here takes them in; a user cancelling or editing the pool waits for the step rather than deadlocking
         * with it.
         */
        @SuppressWarnings("PMD.AvoidCatchingGenericException")
        private void forEach(final CapacityReservationStatus status,
                             final Consumer<CapacityReservation> action) {
            for (final CapacityReservation reservation : ListUtils.emptyIfNull(
                    reservationService.loadByStatus(status))) {
                try {
                    transaction.execute(ignored -> {
                        // Read again under the pool's lock: the list is a snapshot, and a step working from it could
                        // write a status back over a cancellation made since - and then buy what was cancelled.
                        final CapacityReservation current = reservationService.loadForUpdate(reservation.getId());
                        if (current.getStatus() == status) {
                            action.accept(current);
                        } else {
                            log.debug("Capacity reservation {} moved from {} to {} before its turn; skipping",
                                    current.getId(), status, current.getStatus());
                        }
                        return null;
                    });
                } catch (Exception e) {
                    log.error("Failed to process capacity reservation {} in status {}",
                            reservation.getId(), status, e);
                }
            }
        }

        private CapacityReservationMonitorSettings settings() {
            return Optional.ofNullable(preferenceManager.getPreference(
                            SystemPreferences.CLUSTER_CAPACITY_RESERVATION_MONITOR_SETTINGS))
                    .orElseGet(() -> new CapacityReservationMonitorSettings(1));
        }
    }
}
