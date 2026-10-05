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

import com.epam.pipeline.entity.region.CloudProvider;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

public class CapacityReservationTest {

    private static final String CLOUD_ID = "cr-0123456789abcdef0";
    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 1, 12, 0);

    @Test
    public void shouldCopyEveryField() {
        final CapacityReservation reservation = CapacityReservation.builder()
                .id(1L)
                .nodePoolId(2L)
                .regionId(3L)
                .cloudProvider(CloudProvider.AWS)
                .cloudReservationId(CLOUD_ID)
                .status(CapacityReservationStatus.ASSESSING_BY_CLOUD_PROVIDER)
                .instancePlatform("Red Hat Enterprise Linux")
                .startDate(START)
                .attempt(4)
                .build();

        final CapacityReservation copy = reservation.copy();

        assertNotSame(reservation, copy);
        assertEquals(reservation, copy);
    }

    /**
     * The copy is what a provider releases after the reservation itself has moved on - so it must keep what it was.
     */
    @Test
    public void shouldKeepTheCopyAsItWasWhenTheReservationMovesOn() {
        final CapacityReservation reservation = CapacityReservation.builder()
                .cloudReservationId(CLOUD_ID)
                .attempt(0)
                .build();

        final CapacityReservation copy = reservation.copy();
        reservation.setCloudReservationId(null);
        reservation.setAttempt(1);

        assertEquals(CLOUD_ID, copy.getCloudReservationId());
        assertEquals(0, copy.getAttempt());
    }

    /**
     * A client can send no platform, leaving the defaulted field null. The copy must not fill it in.
     */
    @Test
    public void shouldCopyANullPlatformAsNull() {
        final CapacityReservation reservation = new CapacityReservation();
        reservation.setInstancePlatform(null);

        assertNull(reservation.copy().getInstancePlatform());
    }
}
