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
 * The life cycle of a capacity reservation, from the request to the capacity going back to the provider.
 *
 * <ul>
 *   <li>{@link #REQUIRED_APPROVE} - waiting for an administrator to approve the request.</li>
 *   <li>{@link #APPROVED} - approved, and not yet submitted to the cloud provider.</li>
 *   <li>{@link #ASSESSING_BY_CLOUD_PROVIDER} - submitted; the provider has not said yet whether it can
 *       deliver.</li>
 *   <li>{@link #SCHEDULED} - the provider agreed to deliver the capacity in the window it reports.</li>
 *   <li>{@link #ACTIVE} - the capacity is held, and the pool runs at the size reserved for it.</li>
 *   <li>{@link #FINALIZING} - the window is about to close, so the pool takes no new runs.</li>
 *   <li>{@link #FINISHED} - the window has passed and the capacity is back with the provider.</li>
 *   <li>{@link #PURCHASE_FAILED} - the request never reached the provider: its reservation type is not
 *       supported yet, or the submission itself failed outright.</li>
 *   <li>{@link #FAILED} - the request reached the provider and got nowhere: no date its window allows was
 *       accepted, no zone could take it, or the provider could not be paid.</li>
 *   <li>{@link #CANCELLED} - given up, by a user or by the node pool being deleted.</li>
 * </ul>
 *
 * <p>The four statuses {@link #isTerminal()} reports are the end of a reservation: nothing moves out of them, and
 * the pool one of them leaves behind is a pool of no nodes. Which of the other transitions are allowed is
 * {@code CapacityReservationStatusService}'s matrix, not this enum's business.
 */
public enum CapacityReservationStatus {

    REQUIRED_APPROVE(false),

    APPROVED(false),

    PURCHASE_FAILED(true),

    ASSESSING_BY_CLOUD_PROVIDER(false),

    SCHEDULED(false),

    ACTIVE(false),

    FINALIZING(false),

    FINISHED(true),

    FAILED(true),

    CANCELLED(true);

    private final boolean terminal;

    CapacityReservationStatus(final boolean terminal) {
        this.terminal = terminal;
    }

    public boolean isTerminal() {
        return terminal;
    }
}
