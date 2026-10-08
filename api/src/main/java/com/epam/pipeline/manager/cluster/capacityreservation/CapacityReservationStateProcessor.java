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
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * One step of the capacity reservation monitor, for one reservation, in a transaction of its own. Every step takes
 * the reservation's pool lock first, and does nothing when the reservation left the status it was listed in before
 * its turn came. A step that throws takes its whole transaction down, leaving the reservation as it was for the next
 * cycle - which is why the provider is asked to release a reservation only after the database agrees.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CapacityReservationStateProcessor {

    static final int SLIDE_WINDOW_STEP_DAYS = 1;

    static final Duration SUBMISSION_MARGIN = Duration.ofHours(1);

    private final CapacityReservationService reservationService;
    private final CapacityReservationStatusService statusService;
    private final CapacityReservationCloudFacade cloudFacade;
    private final PreferenceManager preferenceManager;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void submit(final Long reservationId, final CapacityReservationStatus expected) {
        locked(reservationId, expected).ifPresent(this::submit);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void poll(final Long reservationId, final CapacityReservationStatus expected) {
        locked(reservationId, expected)
                .ifPresent(reservation -> applyState(reservation, cloudFacade.describe(reservation)));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finalizeIfEnding(final Long reservationId, final CapacityReservationStatus expected) {
        locked(reservationId, expected).ifPresent(this::finalizeIfEnding);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finishIfEnded(final Long reservationId, final CapacityReservationStatus expected) {
        locked(reservationId, expected).ifPresent(this::finishIfEnded);
    }

    /**
     * The reservation under its pool's lock, unless it is no longer in the status the monitor listed it in.
     */
    private Optional<CapacityReservation> locked(final Long reservationId,
                                                 final CapacityReservationStatus expected) {
        final CapacityReservation current = reservationService.loadForUpdate(reservationId);
        if (current.getStatus() == expected) {
            return Optional.of(current);
        }
        log.debug("Capacity reservation {} moved from {} to {} before its turn; skipping",
                current.getId(), expected, current.getStatus());
        return Optional.empty();
    }

    /**
     * Settles a just submitted request on the state the provider answered with. A refused reservation is not the
     * end of the request: it moves on to the next date or zone its window allows, as a refusal seen by a later
     * poll does. Every other answer parks the request at {@code ASSESSING_BY_CLOUD_PROVIDER} for the poll.
     */
    private void processSubmitted(final CapacityReservation reservation,
                                  final CloudCapacityReservation submitted) {
        final CloudCapacityReservationState state = submitted.getState();
        switch (state) {
            case UNSUPPORTED:
            case FAILED:
            case CANCELLED:
            case EXPIRED:
            case DELAYED:
                log.debug("Capacity reservation {} came back {} from its submission; asking for another date or "
                        + "zone", reservation.getId(), state);
                slideOrFail(reservation, submitted.getStateReason());
                break;
            case PAYMENT_FAILED:
                fail(reservation, submitted.getStateReason());
                break;
            case PENDING:
            case SCHEDULED:
            case ACTIVE:
            default:
                statusService.transition(reservation, CapacityReservationStatus.ASSESSING_BY_CLOUD_PROVIDER, null);
                break;
        }
    }

    /**
     * Acts on what the provider says about a reservation.
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
                if (CapacityReservationStatus.SCHEDULED != reservation.getStatus()) {
                    Optional.ofNullable(cloud.getStartDate()).ifPresent(reservation::setStartDate);
                    Optional.ofNullable(cloud.getEndDate()).ifPresent(reservation::setEndDate);
                    statusService.transition(reservation, CapacityReservationStatus.SCHEDULED, null);
                    reservationService.schedulePool(reservation);
                }
                break;
            case UNSUPPORTED:
            case FAILED:
            case CANCELLED:
            case DELAYED:
                slideOrFail(reservation, cloud.getStateReason());
                break;
            case PAYMENT_FAILED:
                fail(reservation, cloud.getStateReason());
                break;
            case EXPIRED:
                statusService.transition(reservation, CapacityReservationStatus.FINISHED, cloud.getStateReason());
                reservationService.deactivatePool(reservation);
                break;
            default:
                break;
        }
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private void submit(final CapacityReservation reservation) {
        if (CapacityReservationType.FUTURE_DATED != reservation.getReservationType()) {
            statusService.transition(reservation, CapacityReservationStatus.PURCHASE_FAILED,
                    reservation.getReservationType() + " reservations are not supported yet");
            return;
        }
        final CloudCapacityReservation submitted;
        try {
            final Optional<CloudCapacityReservation> existing = cloudFacade.findSubmitted(reservation);
            if (existing.isPresent()) {
                submitted = existing.get();
            } else {
                adjustReservation(reservation);
                submitted = cloudFacade.create(reservation);
            }
        } catch (CapacityReservationNoCandidateException e) {
            statusService.transition(reservation, CapacityReservationStatus.FAILED, e.getMessage());
            return;
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

        reservation.setCloudReservationId(submitted.getCloudReservationId());
        Optional.ofNullable(submitted.getAvailabilityZone()).ifPresent(reservation::setAvailabilityZone);
        // The start the provider reports is the one it will deliver, and the only one a reservation adopted by
        // its client token - rather than created in this cycle - has. The end follows from the commitment, since
        // a future-dated reservation is submitted with a duration and no end date, and so is reported without
        // one: taking the start on its own would leave a window shorter than the commitment behind
        Optional.ofNullable(submitted.getStartDate()).ifPresent(start -> {
            reservation.setStartDate(start);
            Optional.ofNullable(reservation.getCommitmentDuration())
                    .ifPresent(seconds -> reservation.setEndDate(start.plusSeconds(seconds)));
        });
        Optional.ofNullable(submitted.getEndDate()).ifPresent(reservation::setEndDate);
        processSubmitted(reservation, submitted);
    }

    /**
     * Chooses the dates this attempt asks for, or finds that none are left.
     */
    private void adjustReservation(final CapacityReservation reservation)
            throws CapacityReservationNoCandidateException {
        if (reservation.getStartDate() == null) {
            reservation.setStartDate(reservation.getRequestedStartDate());
            reservation.setEndDate(reservation.getRequestedStartDate()
                    .plusSeconds(reservation.getCommitmentDuration()));
        }
        if (reservation.getStartDate().isBefore(cloudFacade.earliestAcceptedStart(reservation))) {
            final LocalDateTime start = cloudFacade.earliestAcceptedStart(reservation).plus(SUBMISSION_MARGIN);
            final LocalDateTime end = start.plusSeconds(reservation.getCommitmentDuration());
            if (end.isAfter(reservation.getRequestedEndDate())) {
                throw new CapacityReservationNoCandidateException(String.format("No start date between %s and %s "
                        + "leaves room for a commitment of %ds: the earliest start the provider accepts now "
                        + "is %s", reservation.getRequestedStartDate(), reservation.getRequestedEndDate(),
                        reservation.getCommitmentDuration(), cloudFacade.earliestAcceptedStart(reservation)));
            }
            log.debug("Capacity reservation {} can no longer start at {} with this provider; asking for {} instead",
                    reservation.getId(), reservation.getStartDate(), start);
            reservation.setStartDate(start);
            reservation.setEndDate(end);
        }
        if (StringUtils.isBlank(reservation.getAvailabilityZone())) {
            final List<String> zones = cloudFacade.candidateZones(reservation);
            if (zones.isEmpty()) {
                throw new CapacityReservationNoCandidateException(String.format("No availability zone can take a "
                        + "reservation for %s: none of the region's zones that offer it is one its networks "
                        + "configure", reservation.getInstanceType()));
            }
            reservation.setAvailabilityZone(zones.get(0));
        }
    }

    private void activate(final CapacityReservation reservation, final CloudCapacityReservation cloud) {
        final boolean scheduled = CapacityReservationStatus.SCHEDULED == reservation.getStatus();
        Optional.ofNullable(cloud.getStartDate()).ifPresent(reservation::setStartDate);
        Optional.ofNullable(cloud.getEndDate()).ifPresent(reservation::setEndDate);
        Optional.ofNullable(cloud.getAvailabilityZone()).ifPresent(reservation::setAvailabilityZone);
        statusService.transition(reservation, CapacityReservationStatus.ACTIVE, null);
        if (!scheduled) {
            reservationService.schedulePool(reservation);
        }
        reservationService.activatePool(reservation);
    }

    private void recordGrantedCommitment(final CapacityReservation reservation,
                                         final CloudCapacityReservation cloud) {
        Optional.ofNullable(cloud.getGrantedCommitmentDuration())
                .filter(granted -> !granted.equals(reservation.getGrantedCommitmentDuration()))
                .ifPresent(granted -> {
                    if (reservation.getCommitmentDuration() != null
                            && granted < reservation.getCommitmentDuration()) {
                        log.warn("Capacity reservation {} was granted a commitment of {}s, shorter than the {}s "
                                        + "requested", reservation.getId(), granted,
                                reservation.getCommitmentDuration());
                    }
                    reservation.setGrantedCommitmentDuration(granted);
                });
    }

    /**
     * Moves a future-dated request to the next start date or zone inside the window the requester allowed, or
     * gives up when the window cannot fit the duration any more. The pool stops pointing at the reservation the
     * refused attempt had, and that reservation is released at the provider: the next attempt submits under a
     * client token of its own, so nothing would ever reach it again, and a reservation the provider turns out to
     * still hold would be paid for and forgotten.
     */
    private void slideOrFail(final CapacityReservation reservation, final String reason) {
        final CapacityReservation abandoned = reservation.copy();
        nextCandidateOrFail(reservation, reason);
        reservationService.deactivatePool(reservation);
        if (StringUtils.isNotBlank(abandoned.getCloudReservationId())) {
            cloudFacade.cancel(abandoned);
        }
    }

    /**
     * Ends the request for good: no other date or zone would fare any better.
     */
    private void fail(final CapacityReservation reservation, final String reason) {
        statusService.transition(reservation, CapacityReservationStatus.FAILED, reason);
        reservationService.deactivatePool(reservation);
    }

    private void nextCandidateOrFail(final CapacityReservation reservation, final String reason) {
        final Optional<String> nextZone = fetchNextCandidateZone(reservation);
        if (nextZone.isPresent()) {
            log.debug("Capacity reservation {} is unavailable in {} at {}; trying {} for the same date",
                    reservation.getId(), reservation.getAvailabilityZone(), reservation.getStartDate(),
                    nextZone.get());
            reservation.setAvailabilityZone(nextZone.get());
            resetForNextAttempt(reservation, reason);
            return;
        }
        final LocalDateTime currentStart = Optional.ofNullable(reservation.getStartDate())
                .orElse(reservation.getRequestedStartDate());
        final LocalDateTime nextStart = currentStart.plusDays(SLIDE_WINDOW_STEP_DAYS);
        final LocalDateTime nextEnd = nextStart.plusSeconds(reservation.getCommitmentDuration());

        if (nextEnd.isAfter(reservation.getRequestedEndDate())) {
            statusService.transition(reservation, CapacityReservationStatus.FAILED, String.format(
                    "No available start date between %s and %s for a commitment of %ds%s",
                    reservation.getRequestedStartDate(), reservation.getRequestedEndDate(),
                    reservation.getCommitmentDuration(),
                    reason == null ? "" : ". Last provider response: " + reason));
            return;
        }

        log.debug("Capacity reservation {} is unavailable at {}; trying {}",
                reservation.getId(), currentStart, nextStart);
        reservation.setStartDate(nextStart);
        reservation.setEndDate(nextEnd);
        reservation.setAvailabilityZone(null);
        resetForNextAttempt(reservation, reason);
    }

    private void resetForNextAttempt(final CapacityReservation reservation, final String reason) {
        reservation.setAttempt(reservation.getAttempt() + 1);
        reservation.setCloudReservationId(null);
        statusService.transition(reservation, CapacityReservationStatus.APPROVED, reason);
    }

    private Optional<String> fetchNextCandidateZone(final CapacityReservation reservation) {
        if (StringUtils.isBlank(reservation.getAvailabilityZone())) {
            return Optional.empty();
        }
        final List<String> zones = cloudFacade.candidateZones(reservation);
        final int current = zones.indexOf(reservation.getAvailabilityZone());
        return current >= 0 && current + 1 < zones.size()
                ? Optional.of(zones.get(current + 1))
                : Optional.empty();
    }

    private void finalizeIfEnding(final CapacityReservation reservation) {
        if (reservation.getEndDate() == null) {
            return;
        }
        final LocalDateTime warnFrom = reservation.getEndDate().minusHours(preferenceManager.getPreference(
                SystemPreferences.CLUSTER_CAPACITY_RESERVATION_MONITOR_SETTINGS).getFinalizingLeadHours());
        if (!DateUtils.nowUTC().isBefore(warnFrom)) {
            statusService.transition(reservation, CapacityReservationStatus.FINALIZING, null);
        }
    }

    private void finishIfEnded(final CapacityReservation reservation) {
        if (reservation.getEndDate() == null) {
            return;
        }
        if (!DateUtils.nowUTC().isBefore(reservation.getEndDate())) {
            cloudFacade.cancel(reservation);
            statusService.transition(reservation, CapacityReservationStatus.FINISHED, null);
            reservationService.deactivatePool(reservation);
        }
    }

    private static final class CapacityReservationNoCandidateException extends Exception {

        CapacityReservationNoCandidateException(final String reason) {
            super(reason);
        }
    }
}
