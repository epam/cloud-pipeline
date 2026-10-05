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

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dao.cluster.capacityreservation.CapacityReservationDao;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservation;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservationStatus;
import com.epam.pipeline.entity.notification.NotificationType;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.notification.NotificationManager;
import com.epam.pipeline.manager.pipeline.PipelineRunCRUDService;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The only component that writes a capacity reservation's status.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CapacityReservationStatusService {

    /**
     * The state machine. A reservation may only move along an edge declared here; anything else is a bug in a
     * caller and is rejected rather than silently written.
     *
     * <p>{@code ACTIVE} is never reachable straight from {@code APPROVED}: every reservation is submitted, then
     * assessed by the provider, and only then can capacity exist. The path is
     * {@code APPROVED → ASSESSING_BY_CLOUD_PROVIDER → SCHEDULED → ACTIVE}, with a refusal at either of the middle
     * two steps returning to {@code APPROVED} to try another date.
     */
    private static final Map<CapacityReservationStatus, Set<CapacityReservationStatus>> ALLOWED =
            buildAllowedTransitions();

    private final CapacityReservationDao reservationDao;
    private final NotificationManager notificationManager;
    /*
     * The CRUD service rather than PipelineRunManager, deliberately.
     *
     * PipelineRunManager.loadRunsByPoolId calls NodePoolManager.load(poolId) as an existence check before
     * delegating here, which would make this a cycle: NodePoolManager -> CapacityReservationService ->
     * this -> PipelineRunManager -> NodePoolManager. The check is redundant on this path anyway - we got here
     * from a reservation whose foreign key guarantees the pool exists.
     */
    private final PipelineRunCRUDService runCRUDService;
    private final MessageHelper messageHelper;

    /**
     * Moves a reservation to {@code newStatus}, persisting and notifying as one step.
     *
     * @param statusReason the provider's own explanation, where there is one; null otherwise
     */
    @Transactional
    public CapacityReservation transition(final CapacityReservation reservation,
                                          final CapacityReservationStatus newStatus,
                                          final String statusReason) {
        final CapacityReservationStatus current = reservation.getStatus();
        if (!ALLOWED.getOrDefault(current, Collections.emptySet()).contains(newStatus)) {
            throw new IllegalStateException(messageHelper.getMessage(
                    MessageConstants.ERROR_CAPACITY_RESERVATION_ILLEGAL_TRANSITION,
                    reservation.getId(), current, newStatus));
        }

        reservation.setStatus(newStatus);
        reservation.setStatusReason(statusReason);
        reservation.setUpdated(DateUtils.nowUTC());
        final CapacityReservation updated = reservationDao.update(reservation);
        log.debug("Capacity reservation {} moved from {} to {}", updated.getId(), current, newStatus);

        notify(updated, notificationTypeFor(newStatus));
        return updated;
    }

    /**
     * Records why a reservation is still where it was, without moving it.
     *
     * <p>For a step that will be retried rather than failed: the status does not change, so nobody is notified,
     * but the reason is persisted so a reservation sitting in {@code APPROVED} says what it is waiting for. So are the
     * dates and zone the step asked for, so the retry asks the provider for the same ones - a token reused with other
     * parameters would be refused rather than recognised as a repeat.
     *
     * <p>Only those are written, and only if the status is still the one the caller saw. The caller's copy was
     * loaded before a provider call that may have taken minutes to time out, and a user may have cancelled the
     * reservation meanwhile; writing the whole row back would revive it, and the next cycle would buy it.
     *
     * @return whether the reservation was still where the caller saw it
     */
    @Transactional
    public boolean recordRetry(final CapacityReservation reservation, final String statusReason) {
        reservation.setStatusReason(statusReason);
        reservation.setUpdated(DateUtils.nowUTC());
        final boolean written = reservationDao.updateRetry(reservation);
        if (!written) {
            log.debug("Capacity reservation {} left {} while its retry was being recorded; leaving it as it is",
                    reservation.getId(), reservation.getStatus());
        }
        return written;
    }

    /**
     * Announces a newly created reservation: administrators need to know when something is waiting on them,
     * and the requester needs to know when it is not.
     */
    public void notifyCreated(final CapacityReservation reservation) {
        notify(reservation, reservation.getStatus() == CapacityReservationStatus.REQUIRED_APPROVE
                ? NotificationType.CAPACITY_RESERVATION_REQUIRES_APPROVAL
                : NotificationType.CAPACITY_RESERVATION_STATUS_CHANGED);
    }

    private void notify(final CapacityReservation reservation, final NotificationType type) {
        notificationManager.notifyCapacityReservation(reservation, type, extraRecipients(reservation, type));
    }

    /**
     * Only the finalizing notification goes beyond the owner and administrators, and it goes to whoever has a
     * job running on the pool right now - they are the ones affected by the reservation ending, and the owner
     * cannot pass the warning on for them.
     */
    private List<String> extraRecipients(final CapacityReservation reservation, final NotificationType type) {
        if (type != NotificationType.CAPACITY_RESERVATION_FINALIZING) {
            return Collections.emptyList();
        }
        return ListUtils.emptyIfNull(runCRUDService.loadRunsByPoolId(reservation.getNodePoolId())).stream()
                .map(PipelineRun::getOwner)
                .distinct()
                .collect(Collectors.toList());
    }

    private static NotificationType notificationTypeFor(final CapacityReservationStatus status) {
        return status == CapacityReservationStatus.FINALIZING
                ? NotificationType.CAPACITY_RESERVATION_FINALIZING
                : NotificationType.CAPACITY_RESERVATION_STATUS_CHANGED;
    }

    private static Map<CapacityReservationStatus, Set<CapacityReservationStatus>> buildAllowedTransitions() {
        final Map<CapacityReservationStatus, Set<CapacityReservationStatus>> allowed =
                new EnumMap<>(CapacityReservationStatus.class);
        allowed.put(CapacityReservationStatus.REQUIRED_APPROVE, EnumSet.of(
                CapacityReservationStatus.APPROVED,
                CapacityReservationStatus.CANCELLED));
        allowed.put(CapacityReservationStatus.APPROVED, EnumSet.of(
                CapacityReservationStatus.ASSESSING_BY_CLOUD_PROVIDER,
                CapacityReservationStatus.PURCHASE_FAILED,
                CapacityReservationStatus.FAILED,
                CapacityReservationStatus.CANCELLED));
        allowed.put(CapacityReservationStatus.ASSESSING_BY_CLOUD_PROVIDER, EnumSet.of(
                CapacityReservationStatus.SCHEDULED,
                // A provider may also skip straight to live if the start date has effectively arrived.
                CapacityReservationStatus.ACTIVE,
                // A candidate the provider will not honour goes back to be resubmitted for another date.
                CapacityReservationStatus.APPROVED,
                CapacityReservationStatus.FAILED,
                CapacityReservationStatus.CANCELLED));
        allowed.put(CapacityReservationStatus.SCHEDULED, EnumSet.of(
                CapacityReservationStatus.ACTIVE,
                // Agreement is not irrevocable - a provider can still withdraw a scheduled reservation, and the
                // answer to that is the same as before: try another date within the requested window.
                CapacityReservationStatus.APPROVED,
                CapacityReservationStatus.FAILED,
                CapacityReservationStatus.CANCELLED));
        allowed.put(CapacityReservationStatus.ACTIVE, EnumSet.of(
                CapacityReservationStatus.FINALIZING,
                CapacityReservationStatus.FINISHED,
                CapacityReservationStatus.CANCELLED));
        allowed.put(CapacityReservationStatus.FINALIZING, EnumSet.of(
                CapacityReservationStatus.FINISHED,
                CapacityReservationStatus.CANCELLED));
        // Terminal statuses deliberately have no outgoing edges: retrying creates a new reservation.
        allowed.put(CapacityReservationStatus.FINISHED, Collections.emptySet());
        allowed.put(CapacityReservationStatus.FAILED, Collections.emptySet());
        allowed.put(CapacityReservationStatus.PURCHASE_FAILED, Collections.emptySet());
        allowed.put(CapacityReservationStatus.CANCELLED, Collections.emptySet());
        return Collections.unmodifiableMap(allowed);
    }
}
