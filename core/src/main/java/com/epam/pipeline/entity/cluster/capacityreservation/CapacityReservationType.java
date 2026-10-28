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
 * The kind of capacity a reservation request asks a cloud provider for.
 *
 * <ul>
 *   <li>{@link #FUTURE_DATED} - capacity held for a window that starts in the future, committed to for a duration.
 *       The only type the platform submits today.</li>
 *   <li>{@link #CAPACITY_BLOCK} - a provider's pre-sold block of accelerated capacity, bought whole, named by the
 *       offering id it buys rather than a window to search. Accepted by validation and persisted; the platform does
 *       not yet submit it to the provider for purchase.</li>
 * </ul>
 */
public enum CapacityReservationType {

    FUTURE_DATED,

    CAPACITY_BLOCK
}
