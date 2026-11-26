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
import com.epam.pipeline.vo.CapacitySpecification;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

/**
 * A request to reserve cloud capacity for a node pool, and what the provider has made of it.
 *
 * <p>One reservation belongs to exactly one node pool, and its fields fall into three groups:
 * <ul>
 *   <li><b>what was asked for</b> - {@code candidate} (the instance type and count) and {@code instancePlatform},
 *       the window ({@code requestedStartDate}, {@code requestedEndDate}) and the {@code commitmentDuration}, in
 *       seconds, the capacity is to be held for. A {@link CapacityReservationType#CAPACITY_BLOCK} also has
 *       {@code capacityBlockOfferingId}, the offering it buys. A {@link CapacityReservationType#FUTURE_DATED}
 *       may also have {@code fallbackCandidates}, other instance types to try once {@code candidate} has no
 *       window left;</li>
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

    private String capacityBlockOfferingId;

    private String clientToken;

    private CloudProvider cloudProvider;
    private CapacityReservationType reservationType;
    private CapacityReservationStatus status;

    private String statusReason;

    private String owner;
    private LocalDateTime created;
    private LocalDateTime updated;

    /**
     * What this attempt is currently asking for - the instance type and count.
     */
    private CapacitySpecification candidate;

    /**
     * Other instance types to try, in the order to try them, once {@link #candidate} has no window left
     * anywhere in {@code requestedStartDate}..{@code requestedEndDate}. Merged and sorted once, biggest
     * instance first, when the reservation is created - never rewritten after that.
     * {@link CapacityReservationType#FUTURE_DATED} only.
     *
     * <p>There is no separate field for "how far into this list the request has moved." The current
     * candidate is {@link #candidate} itself; the next one is found by locating it inside this list and
     * taking the entry after it.
     *
     * <p>Defaults to empty, never {@code null} - the persisted column round-trips a {@code CAPACITY_BLOCK}
     * reservation's never-set list as an empty JSON array, not a SQL {@code NULL}, so a freshly built
     * reservation has to start out the same way to compare equal to one just reloaded.
     */
    @Builder.Default
    private List<CapacitySpecification> fallbackCandidates = Collections.emptyList();

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

    public boolean isCapacityBlock() {
        return CapacityReservationType.CAPACITY_BLOCK == reservationType;
    }

    /**
     * AWS only answers extension calls for a block that is still delivering capacity - everything
     * {@code AwsCapacityReservationService#validateExtensionLeadTime} narrows further on top of this, per the
     * lead-time window the provider itself allows.
     */
    public boolean isWithinExtensionLeadWindow() {
        return EnumSet.of(CapacityReservationStatus.ACTIVE, CapacityReservationStatus.FINALIZING).contains(status);
    }

    /**
     * A pure predicate, no cloud call - what the pre-expiry notification asks to say whether an extension is still
     * possible at all for this block. Mirrors {@link com.epam.pipeline.entity.cluster.pool.NodePool#isActive}'s
     * place on its own entity - neither needs a service to answer a question only its own fields decide.
     */
    public boolean isExtendable() {
        return isCapacityBlock() && isWithinExtensionLeadWindow();
    }
}
