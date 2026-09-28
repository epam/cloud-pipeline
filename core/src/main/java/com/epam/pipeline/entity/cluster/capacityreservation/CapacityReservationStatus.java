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

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * The lifecycle of a {@link CapacityReservation}.
 *
 * <p>{@code ACTIVE} is only ever reached from {@code SCHEDULED}: every reservation type is submitted to
 * the cloud provider and then assessed by it, so there is no direct {@code APPROVED -> ACTIVE} edge.
 */
public enum CapacityReservationStatus {

    /**
     * Created, awaiting approval by an administrator.
     */
    REQUIRED_APPROVE(false),

    /**
     * Approved, not yet submitted to the cloud provider.
     */
    APPROVED(false),

    /**
     * Cloud-Pipeline's own call to the cloud provider threw. Distinct from {@link #FAILED}: this means our
     * attempt errored rather than the provider saying no.
     *
     * <p>Terminal: this reservation makes no further attempt. Retrying means a new pool with a new
     * reservation linked back to this one, not a transition out of this state.
     */
    PURCHASE_FAILED(true),

    /**
     * Submitted, and the cloud provider is deciding whether it can honour the requested date.
     */
    ASSESSING_BY_CLOUD_PROVIDER(false),

    /**
     * The cloud provider has agreed to hold the capacity, but the reservation has not started yet.
     *
     * <p>This is a real commitment - the capacity is spoken for from the start date - it simply is not consumable
     * until then.
     */
    SCHEDULED(false),

    /**
     * The reservation is live and the linked node pool is schedulable.
     */
    ACTIVE(false),

    /**
     * Approaching its end date. Advisory: the pool stays schedulable until the reservation actually ends.
     */
    FINALIZING(false),

    /**
     * The reservation period ended and the linked node pool has been deactivated.
     */
    FINISHED(true),

    /**
     * The cloud provider itself rejected or terminated the reservation, or no viable start date was found
     * within the requested window. {@code statusReason} carries the detail.
     */
    FAILED(true),

    /**
     * Cancelled explicitly by the owner or an administrator.
     */
    CANCELLED(true);

    private static final Set<CapacityReservationStatus> TERMINAL = Collections.unmodifiableSet(
            EnumSet.of(PURCHASE_FAILED, FINISHED, FAILED, CANCELLED));

    private final boolean terminal;

    CapacityReservationStatus(final boolean terminal) {
        this.terminal = terminal;
    }

    /**
     * @return {@code true} when this reservation will not transition again, so it holds no cloud resource
     *         and its pool may be deleted. Retrying a terminal reservation creates a new one; it does not
     *         revive this one.
     */
    public boolean isTerminal() {
        return terminal;
    }

    public static Set<CapacityReservationStatus> terminalStatuses() {
        return TERMINAL;
    }
}
