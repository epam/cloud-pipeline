/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.manager.cluster.autoscale;

/**
 * A run bound to a pool with {@code CP_CAP_POOL_ID} names one exact pool. If no node of that pool can take the
 * run, no later tick can take it either. This exception is thrown instead of the usual {@code false}, so the
 * caller can fail the run right away and not spend its retry budget on a known answer.
 */
public class PoolReservationIncompatibleException extends RuntimeException {

    public PoolReservationIncompatibleException(final String message) {
        super(message);
    }
}
