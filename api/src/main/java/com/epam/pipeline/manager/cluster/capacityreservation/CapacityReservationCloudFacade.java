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

import com.epam.pipeline.controller.vo.cluster.capacityreservation.CapacityBlockOfferingRequest;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityBlockInstanceTypeInfo;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityBlockOffering;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservation;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservationStatus;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservationType;
import com.epam.pipeline.entity.cluster.capacityreservation.CloudCapacityReservation;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
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
 * Routes each reservation's provider calls to the {@link CapacityReservationCloudService} of its cloud provider,
 * and owns the idempotency the providers cannot own for us.
 *
 * <p>Every attempt to buy capacity carries a client token derived from the reservation and its attempt number
 * ({@code cp-capacity-reservation-<id>-<attempt>}). That token is what makes a lost reply harmless:
 * <ul>
 *   <li>{@link #findSubmitted} asks the provider what this attempt already holds before anything is created, so a
 *       repeated submission adopts that reservation instead of paying for a second one;</li>
 *   <li>{@link #cancel} releases what an unconfirmed submission bought, even though the platform never learned its
 *       id.</li>
 * </ul>
 *
 * <p>A capacity block purchase carries that same token, but only as a tag on what it buys - AWS's purchase call
 * takes no idempotency token of its own, so {@link #findSubmitted} answering present is the only thing standing
 * between a lost reply and paying for the block twice. Nothing may call {@code create} for one a second time on
 * the strength of a failure alone; only {@link #findSubmitted} answering empty, after however long the caller has
 * decided is long enough to trust that answer, says it is safe to.
 *
 * <p>{@link #isSupported(CloudProvider)} answers whether a provider can reserve capacity at all, which is what
 * keeps a reservation-backed pool from being created where it could never be delivered.
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
     * Finds the reservation the provider already holds under this attempt's client token, if any.
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

    public CloudCapacityReservation create(final CapacityReservation reservation) {
        reservation.setClientToken(clientToken(reservation));
        final CapacityReservationCloudService service = serviceFor(reservation);
        return CapacityReservationType.CAPACITY_BLOCK == reservation.getReservationType()
                ? service.purchaseCapacityBlock(reservation)
                : service.createFutureDated(reservation);
    }

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

    static String clientToken(final CapacityReservation reservation) {
        return String.format("cp-capacity-reservation-%d-%d", reservation.getId(), reservation.getAttempt());
    }

    public boolean isSupported(final CloudProvider provider) {
        return services.containsKey(provider);
    }

    /**
     * Searches a region's current capacity block offerings. There is no {@link CapacityReservation} yet to read a
     * provider from, so the region's own is used instead.
     */
    public List<CapacityBlockOffering> listCapacityBlockOfferings(final AbstractCloudRegion region,
                                                                   final CapacityBlockOfferingRequest request) {
        return serviceFor(region.getProvider()).listCapacityBlockOfferings(region, request);
    }

    public List<CapacityBlockInstanceTypeInfo> listCapacityBlockInstanceTypes(final AbstractCloudRegion region) {
        return serviceFor(region.getProvider()).listCapacityBlockInstanceTypes(region);
    }

    private CapacityReservationCloudService serviceFor(final CapacityReservation reservation) {
        return serviceFor(reservation.getCloudProvider());
    }

    private CapacityReservationCloudService serviceFor(final CloudProvider provider) {
        return Optional.ofNullable(services.get(provider))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Capacity reservations are not supported for " + provider));
    }
}
