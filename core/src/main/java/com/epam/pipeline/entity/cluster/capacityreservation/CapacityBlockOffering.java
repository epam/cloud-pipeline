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
 * One capacity block AWS currently has for sale, as answered by {@code DescribeCapacityBlockOfferings}.
 *
 * <p>Nothing here is a commitment yet - the window, zone and price are AWS's answer to a search, fixed only once
 * {@link #getOfferingId()} is purchased. A purchase must reference this id exactly as returned; the platform does
 * not negotiate a different window, zone or count for it.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CapacityBlockOffering {

    private String offeringId;

    private CapacityBlockResourceType resourceType;

    private String instanceType;
    private String availabilityZone;
    private Integer instanceCount;

    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private Integer capacityDurationHours;

    private BigDecimal upfrontFeeAmount;
    private String upfrontFeeCurrency;

    private String tenancy;
}
