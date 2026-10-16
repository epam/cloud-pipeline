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
 * A request to reserve cloud capacity for a node pool, and what the provider has made of it.
 *
 * <p>One reservation belongs to exactly one node pool, and its fields fall into three groups:
 * <ul>
 *   <li><b>what was asked for</b> - {@code instanceType}, {@code instanceCount} and {@code instancePlatform}, the
 *       window ({@code requestedStartDate}, {@code requestedEndDate}) and the {@code commitmentDuration}, in
 *       seconds, the capacity is to be held for;</li>
 *   <li><b>what the platform decided</b> - {@code status} with the {@code statusReason} behind it, the
 *       {@code attempt} the request is on, and the {@code originId} of the reservation this one replaces;</li>
 *   <li><b>what the provider answered</b> - {@code cloudReservationId}, the {@code availabilityZone} and the
 *       {@code startDate}/{@code endDate} it actually holds, the {@code grantedCommitmentDuration}, and the
 *       {@code clientToken} the current attempt was submitted under.</li>
 * </ul>
 *
 * <p>{@link #copy()} takes the reservation exactly as it stands, which is how a step that moves a request on keeps
 * hold of the attempt it is abandoning - long enough to release the capacity that attempt had bought.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class CapacityReservation {

    public static final String DEFAULT_INSTANCE_PLATFORM = "Linux/UNIX";

    private Long id;

    private Long nodePoolId;

    private Long originId;

    private String name;
    private Long regionId;

    private String cloudReservationId;

    private String availabilityZone;

    private String clientToken;

    private CloudProvider cloudProvider;
    private CapacityReservationType reservationType;
    private CapacityReservationStatus status;

    private String statusReason;

    private String owner;
    private LocalDateTime created;
    private LocalDateTime updated;

    private String instanceType;
    private int instanceCount;

    private LocalDateTime requestedStartDate;
    private LocalDateTime requestedEndDate;

    private LocalDateTime startDate;
    private LocalDateTime endDate;

    private Long commitmentDuration;

    @Builder.Default
    private String instancePlatform = DEFAULT_INSTANCE_PLATFORM;

    private int attempt;

    private Long grantedCommitmentDuration;

    public CapacityReservation copy() {
        return toBuilder().build();
    }
}
