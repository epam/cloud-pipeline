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
 * What a cloud provider has made of a request to purchase a capacity block extension offering, as answered by
 * {@code PurchaseCapacityBlockExtension}.
 *
 * <p>{@link #getState()} reuses {@link CloudCapacityReservationState} - only {@link CloudCapacityReservationState
 * #PENDING}, {@link CloudCapacityReservationState#ACTIVE} and {@link CloudCapacityReservationState#PAYMENT_FAILED}
 * are ever produced here, the same way not every value of that enum is produced by every existing cloud call.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CloudCapacityBlockExtension {

    private String offeringId;
    private CloudCapacityReservationState state;
    private String stateReason;

    private LocalDateTime startDate;
    private LocalDateTime endDate;
}
