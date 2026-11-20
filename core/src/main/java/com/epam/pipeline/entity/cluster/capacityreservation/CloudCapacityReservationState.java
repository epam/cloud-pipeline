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
 * The state of a reservation at its cloud provider, as the platform reads it.
 *
 * <p>Each provider's own vocabulary is mapped onto these, so one monitor serves them all:
 * <ul>
 *   <li>{@link #PENDING} - the provider has not decided yet.</li>
 *   <li>{@link #DELAYED} - the provider cannot deliver the capacity when it was asked for.</li>
 *   <li>{@link #SCHEDULED} - the provider will deliver it in the window it reports.</li>
 *   <li>{@link #ACTIVE} - the capacity is held and can be launched into.</li>
 *   <li>{@link #UNSUPPORTED} - the provider will not reserve this configuration at all.</li>
 *   <li>{@link #FAILED} - the provider gave the request up.</li>
 *   <li>{@link #PAYMENT_FAILED} - the provider could not be paid for it.</li>
 *   <li>{@link #CANCELLED} - the reservation was given up, by the platform or by the provider.</li>
 *   <li>{@link #EXPIRED} - its window has passed.</li>
 * </ul>
 *
 * <p>A state the platform does not recognise is read as {@link #PENDING}, so a provider's new vocabulary delays a
 * decision rather than failing a request.
 */
public enum CloudCapacityReservationState {

    PENDING,

    DELAYED,

    SCHEDULED,

    ACTIVE,

    UNSUPPORTED,

    FAILED,

    PAYMENT_FAILED,

    CANCELLED,

    EXPIRED
}
