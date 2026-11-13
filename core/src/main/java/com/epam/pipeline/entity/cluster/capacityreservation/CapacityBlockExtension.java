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

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One request to extend a {@link CapacityReservationType#CAPACITY_BLOCK} past its current end date.
 *
 * <p>A block can be extended more than once, and each attempt has its own price, approval and purchase outcome, so
 * this is a history - one row per request - rather than fields overwritten on {@link CapacityReservation} itself.
 * {@link CapacityReservation#getEndDate()} keeps meaning "the block's current end", and is advanced only once a
 * request here reaches {@link CapacityBlockExtensionStatus#SUCCEEDED}.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class CapacityBlockExtension {

    private Long id;

    private Long reservationId;

    /**
     * Names the request this one retries, the same idea as {@link CapacityReservation#getOriginId()} for the
     * reservation itself: a retry naming a {@link CapacityBlockExtensionStatus#FAILED} origin, and asking for no
     * more than it did, inherits that origin's approval rather than waiting on a fresh one.
     */
    private Long originId;

    private String extensionOfferingId;
    private int durationHours;

    private BigDecimal upfrontFeeAmount;
    private String upfrontFeeCurrency;

    private CapacityBlockExtensionStatus status;
    private String statusReason;

    private String requestedBy;
    private LocalDateTime created;
    private LocalDateTime updated;

    private LocalDateTime startDate;
    private LocalDateTime endDate;
}
