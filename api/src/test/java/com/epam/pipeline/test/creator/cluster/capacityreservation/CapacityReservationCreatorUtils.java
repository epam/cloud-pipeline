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

import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservation;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservationStatus;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservationType;
import com.epam.pipeline.entity.region.CloudProvider;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public final class CapacityReservationCreatorUtils {

    public static final String RESERVATION_NAME = "g5 reservation";
    public static final long REGION_ID = 1L;
    /** G family: the P families cannot be reserved with a future-dated reservation. */
    public static final String INSTANCE_TYPE = "g5.48xlarge";
    public static final int INSTANCE_COUNT = 2;
    /**
     * 14 days, the shortest commitment AWS accepts for a future-dated reservation. Fixtures use a realistic value
     * so they exercise requests the provider would actually take.
     */
    public static final int DURATION_HOURS = 14 * 24;
    public static final String INSTANCE_PLATFORM = "Linux/UNIX";
    public static final String OWNER = "user";
    public static final String AVAILABILITY_ZONE = "us-east-1c";
    public static final String CLOUD_RESERVATION_ID = "cr-0123456789abcdef0";
    public static final String CLIENT_TOKEN = "cp-cr-1-0";
    public static final String STATUS_REASON = "Capacity unavailable for the requested date";
    /** 10 days: less than DURATION_HOURS, so the granted and requested values cannot be confused. */
    public static final long GRANTED_COMMITMENT_SECONDS = 864000L;
    /**
     * AWS assesses a future-dated request only 5 to 120 days ahead, so a fixture asking for tomorrow would be
     * rejected before it reached the provider.
     */
    private static final int REQUEST_LEAD_DAYS = 7;
    private static final int REQUEST_WINDOW_DAYS = 60;

    private CapacityReservationCreatorUtils() {
        //no op
    }

    /**
     * A reservation as it looks the moment its pool is created: no cloud identity yet, awaiting approval.
     * Truncated to milliseconds so a round trip through {@code TIMESTAMP} compares equal.
     */
    public static CapacityReservation getReservation(final Long nodePoolId) {
        final LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
        return CapacityReservation.builder()
                .nodePoolId(nodePoolId)
                .name(RESERVATION_NAME)
                .regionId(REGION_ID)
                .cloudProvider(CloudProvider.AWS)
                .reservationType(CapacityReservationType.FUTURE_DATED)
                .status(CapacityReservationStatus.REQUIRED_APPROVE)
                .owner(OWNER)
                .created(now)
                .updated(now)
                .instanceType(INSTANCE_TYPE)
                .instanceCount(INSTANCE_COUNT)
                .requestedStartDate(now.plusDays(REQUEST_LEAD_DAYS))
                .requestedEndDate(now.plusDays(REQUEST_WINDOW_DAYS))
                .durationHours(DURATION_HOURS)
                .instancePlatform(INSTANCE_PLATFORM)
                .build();
    }

    /**
     * A reservation that has been submitted and accepted by the cloud provider, so every nullable column
     * carries a value - which is what a round-trip test needs.
     */
    public static CapacityReservation getScheduledReservation(final Long nodePoolId) {
        return getScheduledReservation(nodePoolId, null);
    }

    /**
     * As above, additionally linked to the reservation it retries, so {@code origin_id} is covered by the
     * round trip rather than only by the delete test that asserts it is null.
     */
    public static CapacityReservation getScheduledReservation(final Long nodePoolId, final Long originId) {
        final CapacityReservation reservation = getReservation(nodePoolId);
        reservation.setOriginId(originId);
        reservation.setStatus(CapacityReservationStatus.SCHEDULED);
        reservation.setCloudReservationId(CLOUD_RESERVATION_ID);
        reservation.setAvailabilityZone(AVAILABILITY_ZONE);
        reservation.setClientToken(CLIENT_TOKEN);
        reservation.setStatusReason(STATUS_REASON);
        reservation.setStartDate(reservation.getRequestedStartDate());
        reservation.setEndDate(reservation.getRequestedStartDate().plusHours(DURATION_HOURS));
        reservation.setAttempt(1);
        // Deliberately shorter than DURATION_HOURS: a provider may grant less than was asked for, and the column
        // exists precisely to keep the two apart.
        reservation.setGrantedCommitmentSeconds(GRANTED_COMMITMENT_SECONDS);
        return reservation;
    }
}
