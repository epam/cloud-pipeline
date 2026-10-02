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

package com.epam.pipeline.controller.vo.cluster.pool;

import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservationType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * A request to reserve capacity for the pool being created, attached to {@link NodePoolVO}.
 *
 * <p>The client sets only the reservation's own terms. What the reservation takes from its pool is filled in
 * from the pool once it is persisted, and is not part of the request body.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class CapacityReservationRequest {

    private CapacityReservationType reservationType;

    /**
     * The window within which the reservation may start, and how long it must run for.
     *
     * <p>A window rather than a single date because the cloud provider decides which dates are actually
     * available: the request says "any start between these two that fits {@code durationHours}".
     */
    private LocalDateTime requestedStartDate;
    private LocalDateTime requestedEndDate;
    private Integer durationHours;

    /**
     * The platform string the cloud provider requires. Defaulted when absent.
     */
    private String instancePlatform;

    /**
     * The reservation this request retries, when it is a retry of one the provider rejected. Lets the retry
     * inherit the original's approval instead of queueing for a human again.
     */
    private Long originId;

    // Taken from the pool - see the class description.
    @JsonIgnore
    private Long nodePoolId;
    @JsonIgnore
    private String name;
    @JsonIgnore
    private Long regionId;
    @JsonIgnore
    private String owner;
    @JsonIgnore
    private String instanceType;
    @JsonIgnore
    private Integer instanceCount;
}
