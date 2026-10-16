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
 * The capacity reservation part of a {@link NodePoolVO}: what the caller asks a cloud provider to hold for the pool
 * being created.
 *
 * <p>Only the fields a caller may choose are read from the request body - the type of reservation, the window it is
 * wanted for, the commitment to hold it, the instance platform and the origin below. The rest are
 * {@link JsonIgnore}d and filled in from the pool itself, so a reservation can never describe capacity different
 * from the pool it backs.
 *
 * <p>{@code originId} names an earlier reservation this one replaces. A request that asks for the same capacity as
 * an origin which failed inherits that origin's approval, so a failed reservation can be asked for again without a
 * second human decision.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class CapacityReservationRequest {

    private CapacityReservationType reservationType;

    private LocalDateTime requestedStartDate;
    private LocalDateTime requestedEndDate;
    private Long commitmentDuration;

    private String instancePlatform;

    private Long originId;

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
