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

/**
 * Routes a reservation to its provider, and holds what the rule never to buy the same reservation twice rests on,
 * whichever provider that is: a client token per attempt, and the lookup by it. The caller - the monitor's submit
 * step - looks before it creates.
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
     * What the provider already holds for this attempt: a reservation bought by a submission whose reply was lost.
     * Looked for by the attempt's client token, so a retry adopts it instead of buying a second one.
     *
     * @return empty when there is nothing to adopt, and a reservation may be created
     */
    public Optional<CloudCapacityReservation> findSubmitted(final CapacityReservation reservation) {
        if (StringUtils.isNotBlank(reservation.getCloudReservationId())) {
            return Optional.empty();
        }
        final Optional<CloudCapacityReservation> existing =
                serviceFor(reservation).findByClientToken(reservation, clientToken(reservation));
        existing.ifPresent(found -> log.warn("Capacity reservation {} already exists at its provider as {}; adopting "
                + "it instead of creating another", reservation.getId(), found.getCloudReservationId()));
        return existing;
    }

    /**
     * Creates the reservation for this attempt, under the attempt's client token - so a repeat of this call is
     * recognised by the provider rather than bought twice. Only once {@link #findSubmitted} has found nothing.
     */
    public CloudCapacityReservation create(final CapacityReservation reservation) {
        reservation.setClientToken(clientToken(reservation));
        return serviceFor(reservation).createFutureDated(reservation);
    }

    /**
     * The earliest start date the provider accepts for this reservation right now: its minimum lead from now.
     */
    public LocalDateTime earliestAcceptedStart(final CapacityReservation reservation) {
        return DateUtils.nowUTC().plus(serviceFor(reservation).getMinimumLead());
    }

    public Map<String, Object> launchSpecification(final CapacityReservation reservation) {
        return serviceFor(reservation).launchSpecification(reservation);
    }

    public List<String> candidateZones(final CapacityReservation reservation) {
        return serviceFor(reservation).candidateZones(reservation);
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

    /**
     * Whether a reservation can be made in a region of this provider at all.
     */
    public boolean isSupported(final CloudProvider provider) {
        return services.containsKey(provider);
    }

    private CapacityReservationCloudService serviceFor(final CapacityReservation reservation) {
        return Optional.ofNullable(services.get(reservation.getCloudProvider()))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Capacity reservations are not supported for " + reservation.getCloudProvider()));
    }
}
