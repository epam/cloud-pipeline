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
 * What a cloud provider says about one reservation, in the platform's own terms.
 *
 * <p>Every provider call that asks a question answers with this, so nothing above the provider layer has to read a
 * provider's own model: the id and zone it assigned, the window it holds, the
 * {@link CloudCapacityReservationState} it is in together with the provider's reason for that state, and the
 * commitment it actually granted, which may be shorter than the one that was asked for.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CloudCapacityReservation {

    private String cloudReservationId;

    private String availabilityZone;

    private CloudCapacityReservationState state;

    private LocalDateTime startDate;
    private LocalDateTime endDate;

    private String stateReason;

    private Long grantedCommitmentDuration;
}
