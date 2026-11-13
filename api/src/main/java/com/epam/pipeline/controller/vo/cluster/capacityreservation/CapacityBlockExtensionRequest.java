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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A request to extend a purchased capacity block by one of the offerings
 * {@code GET /cluster/capacity-reservation/{id}/extension-offerings} answered.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class CapacityBlockExtensionRequest {

    /**
     * Named exactly as the offerings search returned it, which already fixes the window.
     */
    private String extensionOfferingId;

    /**
     * Carried again rather than read back off the offering, since it is what the approval policies are evaluated
     * against before the offering is purchased.
     */
    private Integer durationHours;

    /**
     * Left unset for a fresh request; names a {@code FAILED} extension this one retries - asking for no more than
     * that one did inherits its approval rather than waiting on a fresh one, the same idea as
     * {@code CapacityReservationRequest#getOriginId()} for the reservation itself.
     */
    private Long originId;
}
