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

package com.epam.pipeline.manager.cluster.capacityreservation.cloud;

import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservation;
import com.epam.pipeline.entity.cluster.capacityreservation.CloudCapacityReservation;
import com.epam.pipeline.entity.region.CloudProvider;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Everything the reservation lifecycle needs from a cloud provider. One implementation per provider, selected by
 * {@link #getProvider()}.
 *
 * <p>Implementations are the only code in the platform that calls a provider's reservation API. The monitor
 * drives them and never sees a provider's own types or state names - see
 * {@link com.epam.pipeline.entity.cluster.capacityreservation.CloudCapacityReservationState}.
 */
public interface CapacityReservationCloudService {

    CloudProvider getProvider();

    /**
     * How far ahead of the request the provider requires a future-dated reservation to start. A start date any
     * sooner is refused outright.
     */
    Duration getMinimumLead();

    /**
     * The zones a reservation for this instance type may be asked for in, in the order to try them: the region's
     * zones that offer the type, restricted to the ones the region's networks configure, when it configures any - a
     * reservation anywhere else could not be consumed. Empty when there is none.
     */
    List<String> candidateZones(CapacityReservation reservation);

    /**
     * What a node's launch request carries to consume this reservation, in the shape of the provider's own launch
     * arguments. It is merged into a pool's {@code additional_spec} while the reservation is live, and its keys are
     * removed when it ends, so they must not depend on anything but the reservation.
     */
    Map<String, Object> launchSpecification(CapacityReservation reservation);

    /**
     * Asks the provider to hold capacity from a future date.
     *
     * <p>Spends money, so it must be idempotent with respect to {@code reservation.clientToken}: called twice
     * with the same token it must yield the one reservation, not two. A caller that cannot get a response - a
     * timeout, a restart mid-call - relies on that, and on {@link #findByClientToken} to find out what happened.
     *
     * @throws CapacityReservationSubmissionUncertainException when the provider may have acted on the request, or
     *         will accept it on a retry - anything other than a definitive refusal
     */
    CloudCapacityReservation createFutureDated(CapacityReservation reservation);

    /**
     * The provider's current view of a reservation we already hold.
     */
    CloudCapacityReservation describe(CapacityReservation reservation);

    /**
     * Gives up a reservation. May incur a cancellation charge during a commitment period.
     */
    void cancel(CapacityReservation reservation);

    /**
     * Finds a reservation created with the given idempotency token, if one exists.
     *
     * <p>This is how a submission that may or may not have landed is reconciled: rather than retrying the
     * purchase and risking a second one, the caller asks whether the first already succeeded.
     *
     * @throws CapacityReservationSubmissionUncertainException when the provider could not be asked. The question
     *         is still open, so it must not be read as "nothing exists".
     */
    Optional<CloudCapacityReservation> findByClientToken(CapacityReservation reservation, String clientToken);
}
