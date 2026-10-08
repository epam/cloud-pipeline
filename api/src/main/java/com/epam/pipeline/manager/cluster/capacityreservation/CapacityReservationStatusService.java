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

@Service
@Slf4j
@RequiredArgsConstructor
public class CapacityReservationStatusService {

    private static final Map<CapacityReservationStatus, Set<CapacityReservationStatus>> ALLOWED =
            buildAllowedTransitions();

    private final CapacityReservationDao reservationDao;
    private final NotificationManager notificationManager;
    private final PipelineRunCRUDService runCRUDService;
    private final MessageHelper messageHelper;

    /**
     * Moves a reservation to {@code newStatus}, persisting and notifying as one step.
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

    public void notifyCreated(final CapacityReservation reservation) {
        notify(reservation, reservation.getStatus() == CapacityReservationStatus.REQUIRED_APPROVE
                ? NotificationType.CAPACITY_RESERVATION_REQUIRES_APPROVAL
                : NotificationType.CAPACITY_RESERVATION_STATUS_CHANGED);
    }

    private void notify(final CapacityReservation reservation, final NotificationType type) {
        notificationManager.notifyCapacityReservation(reservation, type, extraRecipients(reservation, type));
    }

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
                // A submission the provider refuses outright re-approves the request for the next date or zone
                CapacityReservationStatus.APPROVED,
                CapacityReservationStatus.PURCHASE_FAILED,
                CapacityReservationStatus.FAILED,
                CapacityReservationStatus.CANCELLED));
        // FINISHED is reachable from both: a reservation whose end date passes while the platform is not watching -
        // its service down, or its own end date moved by the provider - is reported as expired on the next poll
        allowed.put(CapacityReservationStatus.ASSESSING_BY_CLOUD_PROVIDER, EnumSet.of(
                CapacityReservationStatus.SCHEDULED,
                CapacityReservationStatus.ACTIVE,
                CapacityReservationStatus.APPROVED,
                CapacityReservationStatus.FINISHED,
                CapacityReservationStatus.FAILED,
                CapacityReservationStatus.CANCELLED));
        allowed.put(CapacityReservationStatus.SCHEDULED, EnumSet.of(
                CapacityReservationStatus.ACTIVE,
                CapacityReservationStatus.APPROVED,
                CapacityReservationStatus.FINISHED,
                CapacityReservationStatus.FAILED,
                CapacityReservationStatus.CANCELLED));
        allowed.put(CapacityReservationStatus.ACTIVE, EnumSet.of(
                CapacityReservationStatus.FINALIZING,
                CapacityReservationStatus.FINISHED,
                CapacityReservationStatus.CANCELLED));
        allowed.put(CapacityReservationStatus.FINALIZING, EnumSet.of(
                CapacityReservationStatus.FINISHED,
                CapacityReservationStatus.CANCELLED));
        allowed.put(CapacityReservationStatus.FINISHED, Collections.emptySet());
        allowed.put(CapacityReservationStatus.FAILED, Collections.emptySet());
        allowed.put(CapacityReservationStatus.PURCHASE_FAILED, Collections.emptySet());
        allowed.put(CapacityReservationStatus.CANCELLED, Collections.emptySet());
        return Collections.unmodifiableMap(allowed);
    }
}
