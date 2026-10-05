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
 * A submission whose outcome is unknown, or whose failure the provider expects to go away: a timeout, a dropped
 * connection, throttling, a provider-side error.
 *
 * <p>The provider may have created the reservation and we never saw the reply, so giving up here would abandon
 * something that bills for its whole commitment with nothing tracking it. The caller must instead retry the same
 * attempt - same idempotency token - which either adopts what the first call bought or buys it for the first time.
 * A definitive refusal is never reported this way: it means nothing was created, and retrying would only be
 * refused again.
 */
public class CapacityReservationSubmissionUncertainException extends RuntimeException {

    public CapacityReservationSubmissionUncertainException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
