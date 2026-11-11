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
 * Search criteria for the capacity block offerings AWS sells now. No reservation exists at this point. This is
 * why the request is separate from {@link com.epam.pipeline.controller.vo.cluster.pool.CapacityReservationRequest},
 * which asks for the capacity of one specific pool instead of searching a catalogue.
 *
 * <p>{@code resourceType} may be unset or set to {@link CapacityBlockResourceType#INSTANCE}. Unset means the same
 * thing. That is the only accepted value today. The field also allows
 * {@link CapacityBlockResourceType#ULTRASERVER}, so the request does not have to change once it is supported.
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
     * The date to start looking from. Required, unlike {@link #endDateRange}: AWS cannot answer a search that
     * has no earliest date.
     */
    private LocalDateTime startDateRange;
    private LocalDateTime endDateRange;

    private Integer capacityDurationHours;
}
