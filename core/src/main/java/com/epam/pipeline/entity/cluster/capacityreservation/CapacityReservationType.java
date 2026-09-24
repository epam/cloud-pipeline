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

/**
 * A kind of capacity reservation product. Cloud-neutral names; the current implementation targets AWS.
 */
public enum CapacityReservationType {

    /**
     * Capacity reserved for a future start date, with a minimum commitment duration. The cloud provider
     * assesses asynchronously - days, for AWS - whether a requested start date is actually viable, so
     * {@code requestedStartDate}, {@code requestedEndDate} and {@code durationHours} describe a window
     * to search rather than a single fixed slot.
     */
    FUTURE_DATED,

    /**
     * A GPU-optimized, fixed-duration, paid-upfront reservation product. The user selects one concrete
     * offering before requesting the pool, so the dates come from that offering and are never adjusted
     * afterwards. Data-model-complete but not implemented yet.
     */
    CAPACITY_BLOCK
}
