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
import com.epam.pipeline.entity.cluster.capacityreservation.CloudCapacityReservation;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.manager.cluster.capacityreservation.cloud.CapacityReservationCloudService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
     * <p>Submission spends money and runs on a timer, so the dangerous case is not failure but uncertainty: the
     * provider accepted the request and we never saw the response. Retrying blindly then buys a second
     * reservation that nothing in the platform knows about, and nothing will ever cancel. So the token is
     * computed and persisted first, and a reservation that has a token but no provider id is looked up before
     * anything is bought.
     */
    public CloudCapacityReservation submit(final CapacityReservation reservation) {
        final CapacityReservationCloudService service = serviceFor(reservation);
        final String clientToken = clientToken(reservation);

        if (StringUtils.isBlank(reservation.getCloudReservationId())
                && StringUtils.isNotBlank(reservation.getClientToken())) {
            final Optional<CloudCapacityReservation> existing =
                    service.findByClientToken(reservation, reservation.getClientToken());
            if (existing.isPresent()) {
                log.warn("Capacity reservation {} already exists at its provider as {}; adopting it instead of "
                                + "creating another", reservation.getId(),
                        existing.get().getCloudReservationId());
                return existing.get();
            }
        }

        reservation.setClientToken(clientToken);
        return service.createFutureDated(reservation);
    }

    public CloudCapacityReservation describe(final CapacityReservation reservation) {
        return serviceFor(reservation).describe(reservation);
    }

    public void cancel(final CapacityReservation reservation) {
        if (StringUtils.isBlank(reservation.getCloudReservationId())) {
            // Nothing was ever created, so there is nothing to give back.
            return;
        }
        serviceFor(reservation).cancel(reservation);
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
