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

package com.epam.pipeline.entity.cluster.capacityreservation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * What a cloud provider tells us about a reservation: what it is called there, where it lives, and how it is
 * doing. Everything the monitor needs and nothing about how it was asked for.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CloudCapacityReservation {

    /**
     * The provider's own identifier for the reservation.
     */
    private String cloudReservationId;

    /**
     * The zone the capacity was granted in. A reservation lives in exactly one zone, and a node launched into a
     * different one silently pays on-demand while the reservation sits idle - which is why this is carried all
     * the way to the launch call rather than being treated as informational.
     */
    private String availabilityZone;

    private CloudCapacityReservationState state;

    /**
     * When the capacity actually starts and ends, as the provider resolved it - not necessarily what was asked
     * for.
     */
    private LocalDateTime startDate;
    private LocalDateTime endDate;

    /**
     * The provider's own explanation, where it gives one. Surfaced to the requester verbatim rather than
     * summarised, because a capacity refusal is usually only actionable in the provider's own terms.
     */
    private String stateReason;

    /**
     * The commitment the provider granted, in seconds, which can be shorter than the one requested. Null until it
     * has decided, and null for a provider with no commitment concept.
     */
    private Long grantedCommitmentSeconds;
}
