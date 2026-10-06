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
import com.epam.pipeline.manager.scheduling.AbstractSchedulingManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.SchedulerLock;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class CapacityReservationMonitor extends AbstractSchedulingManager {

    private final CapacityReservationMonitorCore core;

    @PostConstruct
    public void init() {
        scheduleSecured(core::monitor, SystemPreferences.CLUSTER_CAPACITY_RESERVATION_MONITOR_CRON,
                "CapacityReservationMonitor");
    }

    @Component
    @Slf4j
    static class CapacityReservationMonitorCore {

        protected static final int SLIDE_WINDOW_STEP_DAYS = 1;

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
            this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        }

        @SchedulerLock(name = "CapacityReservationMonitor_monitor", lockAtMostForString = "PT5M")
        public void monitor() {
            log.debug("Running CapacityReservation monitor cycle");
            processApproved();
            processAssessing();
            processScheduled();
            processActive();
            processFinalizing();
            log.debug("Finished CapacityReservation monitor cycle");
        }

        void processApproved() {
            forEach(CapacityReservationStatus.APPROVED, this::submit);
        }

        void processAssessing() {
            forEach(CapacityReservationStatus.ASSESSING_BY_CLOUD_PROVIDER, this::poll);
        }

        void processScheduled() {
            forEach(CapacityReservationStatus.SCHEDULED, this::poll);
        }

        void processActive() {
            forEach(CapacityReservationStatus.ACTIVE, this::finalizeIfEnding);
        }

        void processFinalizing() {
            forEach(CapacityReservationStatus.FINALIZING, this::finishIfEnded);
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
                    slideOrFail(reservation, cloud.getStateReason());
                    break;
                case DELAYED:
                    cancelAndSlide(reservation, cloud.getStateReason());
                    break;
                case FAILED:
                    statusService.transition(reservation, CapacityReservationStatus.FAILED, cloud.getStateReason());
                    reservationService.deactivatePool(reservation);
                    break;
                case CANCELLED:
                    statusService.transition(reservation, CapacityReservationStatus.CANCELLED,
                            cloud.getStateReason());
                    reservationService.deactivatePool(reservation);
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
        void submit(final CapacityReservation reservation) {
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
            statusService.transition(reservation, CapacityReservationStatus.ASSESSING_BY_CLOUD_PROVIDER, null);
        }

        void poll(final CapacityReservation reservation) {
            applyState(reservation, cloudFacade.describe(reservation));
        }

        /**
         * Chooses the dates this attempt asks for, or finds that none are left.
         */
        private void adjustReservation(final CapacityReservation reservation)
                throws CapacityReservationNoCandidateException {
            if (reservation.getStartDate() == null) {
                reservation.setStartDate(reservation.getRequestedStartDate());
                reservation.setEndDate(reservation.getRequestedStartDate()
                        .plusHours(reservation.getDurationHours()));
            }
            if (reservation.getStartDate().isBefore(cloudFacade.earliestAcceptedStart(reservation))) {
                final LocalDateTime start = cloudFacade.earliestAcceptedStart(reservation).plus(SUBMISSION_MARGIN);
                final LocalDateTime end = start.plusHours(reservation.getDurationHours());
                if (end.isAfter(reservation.getRequestedEndDate())) {
                    throw new CapacityReservationNoCandidateException(String.format("No start date between %s and %s "
                            + "leaves room for a reservation of %d hours: the earliest start the provider accepts now "
                            + "is %s", reservation.getRequestedStartDate(), reservation.getRequestedEndDate(),
                            reservation.getDurationHours(), cloudFacade.earliestAcceptedStart(reservation)));
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

        private void cancelAndSlide(final CapacityReservation reservation, final String reason) {
            final CapacityReservation delayed = reservation.copy();
            slideOrFail(reservation, reason);
            cloudFacade.cancel(delayed);
            reservationService.deactivatePool(delayed);
        }

        /**
         * Moves a future-dated request to the next start date inside the window the requester allowed, or gives up
         * when the window cannot fit the duration any more.
         */
        private void slideOrFail(final CapacityReservation reservation, final String reason) {
            final Optional<String> nextZone = fetchNextCandidateZone(reservation);
            if (nextZone.isPresent()) {
                log.debug("Capacity reservation {} is unavailable in {} at {}; trying {} for the same date",
                        reservation.getId(), reservation.getAvailabilityZone(), reservation.getStartDate(),
                        nextZone.get());
                reservation.setAvailabilityZone(nextZone.get());
                resetForNextAttempt(reservation, reason);
                return;
            }
            final LocalDateTime nextStart = reservation.getStartDate().plusDays(SLIDE_WINDOW_STEP_DAYS);
            final LocalDateTime nextEnd = nextStart.plusHours(reservation.getDurationHours());

            if (nextEnd.isAfter(reservation.getRequestedEndDate())) {
                statusService.transition(reservation, CapacityReservationStatus.FAILED, String.format(
                        "No available start date between %s and %s for a reservation of %d hours%s",
                        reservation.getRequestedStartDate(), reservation.getRequestedEndDate(),
                        reservation.getDurationHours(), reason == null ? "" : ". Last provider response: " + reason));
                return;
            }

            log.debug("Capacity reservation {} is unavailable at {}; trying {}",
                    reservation.getId(), reservation.getStartDate(), nextStart);
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

        void finalizeIfEnding(final CapacityReservation reservation) {
            if (reservation.getEndDate() == null) {
                return;
            }
            final LocalDateTime warnFrom = reservation.getEndDate().minusHours(preferenceManager.getPreference(
                    SystemPreferences.CLUSTER_CAPACITY_RESERVATION_MONITOR_SETTINGS).getFinalizingLeadHours());
            if (!DateUtils.nowUTC().isBefore(warnFrom)) {
                statusService.transition(reservation, CapacityReservationStatus.FINALIZING, null);
            }
        }

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
         */
        @SuppressWarnings("PMD.AvoidCatchingGenericException")
        private void forEach(final CapacityReservationStatus status, final Consumer<CapacityReservation> action) {
            for (final CapacityReservation reservation :
                    ListUtils.emptyIfNull(reservationService.loadByStatus(status))) {
                try {
                    transaction.execute(ignored -> {
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
    }

    private static final class CapacityReservationNoCandidateException extends Exception {

        CapacityReservationNoCandidateException(final String reason) {
            super(reason);
        }
    }
}
