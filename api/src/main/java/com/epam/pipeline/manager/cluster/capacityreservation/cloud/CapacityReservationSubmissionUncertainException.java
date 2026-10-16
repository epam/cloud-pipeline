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

package com.epam.pipeline.manager.cluster.capacityreservation.cloud;

/**
 * Raised when a call to a cloud provider leaves it unknown whether capacity was reserved.
 *
 * <p>Throttling, a server-side error or a reply that never arrived all mean the reservation may or may not exist.
 * The request is therefore left in the status it was in and retried with the same client token on a later cycle:
 * failing it would abandon capacity the platform may already be paying for, and retrying under a new token would
 * buy that capacity twice.
 */
public class CapacityReservationSubmissionUncertainException extends RuntimeException {

    public CapacityReservationSubmissionUncertainException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
