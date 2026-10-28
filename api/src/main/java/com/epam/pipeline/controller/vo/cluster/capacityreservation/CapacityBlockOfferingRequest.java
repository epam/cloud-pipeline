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

package com.epam.pipeline.controller.vo.cluster.capacityreservation;

import com.epam.pipeline.entity.cluster.capacityreservation.CapacityBlockResourceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * What to search AWS's current capacity block offerings for - no reservation exists yet, so this stands apart from
 * {@link com.epam.pipeline.controller.vo.cluster.pool.CapacityReservationRequest}, which asks for a specific pool's
 * capacity rather than searching a catalogue.
 *
 * <p>{@code resourceType} is left unset (read as {@link CapacityBlockResourceType#INSTANCE}) or set to it
 * explicitly - that is the only value accepted today. {@link CapacityBlockResourceType#ULTRASERVER} exists on the
 * field only so this shape need not change again once it is supported.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class CapacityBlockOfferingRequest {

    private Long regionId;

    private CapacityBlockResourceType resourceType;

    private String instanceType;
    private Integer instanceCount;

    /**
     * The date to start looking from - required, unlike {@link #endDateRange}, since a search with no earliest
     * date to search from is not a search AWS can answer.
     */
    private LocalDateTime startDateRange;
    private LocalDateTime endDateRange;

    private Integer capacityDurationHours;
}
