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
 * The state a cloud provider reports for a reservation it holds.
 *
 * <p>Deliberately the platform's own vocabulary rather than any provider's: each provider service translates its
 * own states into these, so the monitor never has to know which cloud it is talking to. The AWS mapping is
 * documented on each value.
 */
public enum CloudCapacityReservationState {

    /**
     * The provider has the request but has not decided yet, so there is nothing to do but ask again later.
     * AWS: {@code assessing}, {@code delayed}, {@code payment-pending}.
     */
    PENDING,

    /**
     * The provider has accepted the request for a future date but the capacity is not usable yet.
     * AWS: {@code scheduled}.
     */
    SCHEDULED,

    /**
     * The capacity exists and can be consumed now. AWS: {@code active}.
     */
    ACTIVE,

    /**
     * The provider cannot satisfy this request as asked. For a future-dated request this is not final - another
     * start date may work - so the monitor treats it as "try the next candidate" rather than as failure.
     * AWS: {@code unsupported}.
     */
    UNSUPPORTED,

    /**
     * The provider rejected or terminated the reservation outright. AWS: {@code failed},
     * {@code payment-failed}.
     */
    FAILED,

    /**
     * The reservation is gone. AWS: {@code cancelled}, {@code cancelling}.
     */
    CANCELLED,

    /**
     * The reservation period ended normally. AWS: {@code expired}.
     */
    EXPIRED
}
