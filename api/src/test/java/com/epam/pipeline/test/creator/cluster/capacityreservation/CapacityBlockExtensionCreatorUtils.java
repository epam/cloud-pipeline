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

package com.epam.pipeline.test.creator.cluster.capacityreservation;

import com.epam.pipeline.entity.cluster.capacityreservation.CapacityBlockExtension;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityBlockExtensionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public final class CapacityBlockExtensionCreatorUtils {

    public static final String EXTENSION_OFFERING_ID = "cbe-0123456789abcdef0";
    public static final int DURATION_HOURS = 24;
    public static final BigDecimal UPFRONT_FEE_AMOUNT = BigDecimal.valueOf(123.45);
    public static final String UPFRONT_FEE_CURRENCY = "USD";
    public static final String REQUESTED_BY = "user";
    public static final String STATUS_REASON = "Denied by policy";

    private CapacityBlockExtensionCreatorUtils() {
    }

    public static CapacityBlockExtension getExtension(final Long reservationId) {
        final LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
        return CapacityBlockExtension.builder()
                .reservationId(reservationId)
                .extensionOfferingId(EXTENSION_OFFERING_ID)
                .durationHours(DURATION_HOURS)
                .upfrontFeeAmount(UPFRONT_FEE_AMOUNT)
                .upfrontFeeCurrency(UPFRONT_FEE_CURRENCY)
                .status(CapacityBlockExtensionStatus.REQUIRED_APPROVE)
                .requestedBy(REQUESTED_BY)
                .created(now)
                .updated(now)
                .build();
    }

    public static CapacityBlockExtension getSucceededExtension(final Long reservationId) {
        final CapacityBlockExtension extension = getExtension(reservationId);
        extension.setStatus(CapacityBlockExtensionStatus.SUCCEEDED);
        extension.setStartDate(extension.getCreated());
        extension.setEndDate(extension.getCreated().plusHours(DURATION_HOURS));
        return extension;
    }
}
