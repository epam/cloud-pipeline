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

import com.epam.pipeline.entity.region.CloudProvider;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * A request to the cloud provider to guarantee compute capacity, and its approval/procurement lifecycle.
 *
 * <p>Always linked to exactly one node pool - the one whose creation triggered it - from the moment it is
 * created, and never exists standalone. {@code nodePoolId} is the only link between the two;
 * {@code node_pool} deliberately carries no reservation id, and no copy of this entity's {@code status}.
 *
 * <p>All {@link LocalDateTime} fields here are **UTC**. Conversion to and from the cloud provider's own
 * instant types happens in the provider-specific service, not in callers.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class CapacityReservation {

    public static final String DEFAULT_INSTANCE_PLATFORM = "Linux/UNIX";

    private Long id;

    /**
     * The pool this reservation backs. Always set.
     */
    private Long nodePoolId;

    /**
     * The reservation this one retries, when it was created as a retry of a {@code FAILED} request.
     * Nulled rather than cascaded if that original is ever deleted.
     */
    private Long originId;

    private String name;
    private Long regionId;

    /**
     * The provider's own identifier, set once the provider has accepted the request.
     */
    private String cloudReservationId;

    /**
     * The zone the capacity was granted in. A targeted reservation lives in exactly one zone, so a node
     * launched into a different one silently falls back to on-demand pricing while the reservation idles -
     * which is why this is carried all the way to the launch call.
     */
    private String availabilityZone;

    /**
     * Idempotency key for the provider's create call, derived from the reservation id and attempt. Ensures
     * a retried submission cannot buy a second reservation.
     */
    private String clientToken;

    private CloudProvider cloudProvider;
    private CapacityReservationType reservationType;
    private CapacityReservationStatus status;

    /**
     * The provider's own explanation for a {@code FAILED} or {@code PURCHASE_FAILED} status; null otherwise.
     */
    private String statusReason;

    private String owner;
    private LocalDateTime created;
    private LocalDateTime updated;

    private String instanceType;
    private int instanceCount;

    /**
     * The window the user asked for, as submitted. Immutable after creation, and the bound the start date
     * search may not leave.
     */
    private LocalDateTime requestedStartDate;
    private LocalDateTime requestedEndDate;

    /**
     * The dates actually in effect. Null until the first submission, then updated on every attempt;
     * {@code endDate} is {@code startDate} plus {@code durationHours}.
     */
    private LocalDateTime startDate;
    private LocalDateTime endDate;

    private Integer durationHours;

    /**
     * The platform string the cloud provider requires.
     *
     * <p>Defaulted on the builder path and by the no-args constructor Jackson uses, but a client can still send
     * null. The column is {@code NOT NULL} and the insert always names it, so the authoritative default is
     * applied where the value is bound, in {@code CapacityReservationDao}; do not rely on this field being
     * populated.
     */
    @Builder.Default
    private String instancePlatform = DEFAULT_INSTANCE_PLATFORM;

    /**
     * Incremented for every deliberately new candidate submitted, and feeds {@code clientToken}.
     */
    private int attempt;

    /**
     * The commitment the cloud provider actually granted, in seconds, once it has assessed the request - as opposed
     * to {@link #durationHours}, which is what was asked for.
     *
     * <p>They can differ: AWS may decide it can support a shorter commitment than requested and schedule the
     * reservation on that basis, which leaves the requester committed for less time than they asked for. Null until
     * the provider has decided, and null for a provider that has no such concept.
     */
    private Long grantedCommitmentSeconds;

    /**
     * This reservation as it is now. Every field is immutable, so the copy stays as it is whatever later happens to
     * this one.
     */
    public CapacityReservation copy() {
        return toBuilder().build();
    }
}
