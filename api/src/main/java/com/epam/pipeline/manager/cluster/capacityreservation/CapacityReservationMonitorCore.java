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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.SchedulerLock;
import org.apache.commons.collections4.ListUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Drives a capacity reservation through the part of its life that nobody asks for: submitting an approved
 * request, watching what the provider makes of it, activating the pool once capacity exists, and winding the pool
 * down when it ends.
 *
 * <p>Each pass handles one status, so a reservation advances at most one step per cycle. That is deliberate -
 * it keeps every transition an independently visible, independently retryable event rather than a long chain
 * that can half-fail.
 *
 * <p>Note what this does <em>not</em> do: it never approves anything. Approval is a human's or a policy's
 * decision, and a monitor silently promoting {@code REQUIRED_APPROVE} would remove the only gate on spending.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CapacityReservationMonitorCore {

    /**
     * How far a future-dated start date moves forward when the provider reports the current candidate is not
     * available. A day is the granularity the search is worth doing at: each candidate costs a provider
     * assessment cycle, so a finer step buys little and a coarser one skips dates that would have been granted.
     */
    protected static final int SLIDE_WINDOW_STEP_DAYS = 1;

    private static final int SECONDS_PER_HOUR = 3600;

    private final CapacityReservationService reservationService;
    private final CapacityReservationStatusService statusService;
    private final CapacityReservationCloudFacade cloudFacade;
    private final PreferenceManager preferenceManager;

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
     * Catches broadly on purpose: a failure to submit must never escape and leave the reservation unexplained.
     *
     * <p>Two kinds of failure, handled differently. When the outcome is uncertain the provider may already hold a
     * reservation for this attempt, so it stays {@code APPROVED} and is submitted again next cycle with the same
     * token - which adopts what the lost call bought instead of abandoning it to bill unseen. A refusal, or a
     * failure on our side, lands on {@code PURCHASE_FAILED}.
     */
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    @Transactional
    void submit(final CapacityReservation reservation) {
        if (CapacityReservationType.FUTURE_DATED != reservation.getReservationType()) {
            statusService.transition(reservation, CapacityReservationStatus.PURCHASE_FAILED,
                    reservation.getReservationType() + " reservations are not supported yet");
            return;
        }
        try {
            prepareCandidate(reservation);
            final CloudCapacityReservation submitted = cloudFacade.submit(reservation);
            reservation.setCloudReservationId(submitted.getCloudReservationId());
            reservation.setAvailabilityZone(submitted.getAvailabilityZone());
            statusService.transition(reservation, CapacityReservationStatus.ASSESSING_BY_CLOUD_PROVIDER, null);
            // The create call answers synchronously - normally with the provider still assessing the date, but it
            // may already have agreed, or refused outright. Acting on that answer now rather than waiting for the
            // next cycle means a refusal starts the search for another date immediately.
            applyState(reservation, submitted);
        } catch (CapacityReservationSubmissionUncertainException e) {
            log.warn("Submission of capacity reservation {} did not complete and will be retried: {}",
                    reservation.getId(), e.getMessage());
            statusService.recordRetry(reservation, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to submit capacity reservation {}", reservation.getId(), e);
            statusService.transition(reservation, CapacityReservationStatus.PURCHASE_FAILED, e.getMessage());
        }
    }

    /**
     * Chooses the dates this attempt asks for.
     *
     * <p>The first attempt asks for the earliest date the requester would accept. Later attempts have already
     * been moved on by {@link #slideOrFail}, so this only fills in a first candidate.
     */
    private void prepareCandidate(final CapacityReservation reservation) {
        if (reservation.getStartDate() == null) {
            reservation.setStartDate(reservation.getRequestedStartDate());
            reservation.setEndDate(reservation.getRequestedStartDate()
                    .plusHours(reservation.getDurationHours()));
        }
    }

    @Transactional
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
     * Gives a reservation back to the provider and then asks for another date.
     */
    private void releaseAndSlide(final CapacityReservation reservation, final String reason) {
        cloudFacade.cancel(reservation);
        slideOrFail(reservation, reason);
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
                    String.format("No available start date between %s and %s for a reservation of %d hours%s",
                            reservation.getRequestedStartDate(), reservation.getRequestedEndDate(),
                            reservation.getDurationHours(),
                            reason == null ? "" : ". Last provider response: " + reason));
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

    @Transactional
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
    @Transactional
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
     * One reservation's failure must not stop the others being processed - they are independent requests that
     * happen to share a cycle.
     */
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private void forEach(final CapacityReservationStatus status,
                         final java.util.function.Consumer<CapacityReservation> action) {
        for (final CapacityReservation reservation : ListUtils.emptyIfNull(
                reservationService.loadByStatus(status))) {
            try {
                action.accept(reservation);
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
