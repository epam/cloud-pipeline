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
import com.epam.pipeline.entity.cluster.capacityreservation.CloudCapacityReservation;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.cluster.capacityreservation.cloud.CapacityReservationCloudService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/**
 * Routes a reservation to its provider, and owns the one rule that must hold whichever provider that is: never
 * buy the same reservation twice.
 */
@Service
@Slf4j
public class CapacityReservationCloudFacade {

    private final Map<CloudProvider, CapacityReservationCloudService> services =
            new EnumMap<>(CloudProvider.class);

    @Autowired
    public CapacityReservationCloudFacade(final List<CapacityReservationCloudService> services) {
        ListUtils.emptyIfNull(services)
                .forEach(service -> this.services.put(service.getProvider(), service));
    }

    /**
     * Submits a reservation, adopting one that already exists instead of creating a second.
     *
     * <p>The dates are chosen only once the lookup has found nothing to adopt. A reservation this attempt already
     * bought is adopted as it is - even on a date that could no longer be asked for, since the provider has agreed
     * to it - and must never be abandoned because a new request would not fit.
     *
     * @param candidate chooses the dates to ask for; false when no date is left to ask for
     * @return what the provider holds for this attempt, or empty when there was nothing to adopt and no date left to
     *         ask for - in which case nothing was created
     */
    public Optional<CloudCapacityReservation> submit(final CapacityReservation reservation,
                                                     final BooleanSupplier candidate) {
        final CapacityReservationCloudService service = serviceFor(reservation);
        final String clientToken = clientToken(reservation);

        if (StringUtils.isBlank(reservation.getCloudReservationId())) {
            final Optional<CloudCapacityReservation> existing =
                    service.findByClientToken(reservation, clientToken);
            if (existing.isPresent()) {
                log.warn("Capacity reservation {} already exists at its provider as {}; adopting it instead of "
                                + "creating another", reservation.getId(),
                        existing.get().getCloudReservationId());
                return existing;
            }
        }
        if (!candidate.getAsBoolean()) {
            return Optional.empty();
        }

        reservation.setClientToken(clientToken);
        return Optional.of(service.createFutureDated(reservation));
    }

    /**
     * The earliest start date the provider accepts for this reservation right now: its minimum lead from now.
     */
    public LocalDateTime earliestAcceptedStart(final CapacityReservation reservation) {
        return DateUtils.nowUTC().plus(serviceFor(reservation).getMinimumLead());
    }

    public CloudCapacityReservation describe(final CapacityReservation reservation) {
        return serviceFor(reservation).describe(reservation);
    }

    /**
     * Gives a reservation back to its provider, including one bought by a submission whose reply was lost.
     */
    public void cancel(final CapacityReservation reservation) {
        final CapacityReservationCloudService service = serviceFor(reservation);
        if (StringUtils.isBlank(reservation.getCloudReservationId())) {
            // Only an approved reservation can have been bought without us learning its id - its submission is
            // retried until the reply arrives. Anywhere else, nothing was ever created.
            if (CapacityReservationStatus.APPROVED != reservation.getStatus()) {
                return;
            }
            final Optional<CloudCapacityReservation> lost =
                    service.findByClientToken(reservation, clientToken(reservation));
            if (!lost.isPresent()) {
                return;
            }
            log.warn("Capacity reservation {} is being cancelled while its submission was unconfirmed; releasing "
                    + "{}, which that submission bought", reservation.getId(), lost.get().getCloudReservationId());
            reservation.setCloudReservationId(lost.get().getCloudReservationId());
        }
        service.cancel(reservation);
    }

    /**
     * Deterministic, so the same attempt always produces the same token and the provider can recognise a repeat.
     * The attempt is part of it because a slid start date is a genuinely different request, not a retry.
     */
    static String clientToken(final CapacityReservation reservation) {
        return String.format("cp-cr-%d-%d", reservation.getId(), reservation.getAttempt());
    }

    private CapacityReservationCloudService serviceFor(final CapacityReservation reservation) {
        return Optional.ofNullable(services.get(reservation.getCloudProvider()))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Capacity reservations are not supported for " + reservation.getCloudProvider()));
    }
}
