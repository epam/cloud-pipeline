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

package com.epam.pipeline.manager.cloud.aws.capacityreservation;

import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservation;
import com.epam.pipeline.entity.cluster.capacityreservation.CloudCapacityReservation;
import com.epam.pipeline.entity.cluster.capacityreservation.CloudCapacityReservationState;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.manager.cluster.capacityreservation.cloud.CapacityReservationCloudService;
import com.epam.pipeline.manager.region.CloudRegionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * AWS On-Demand Capacity Reservations.
 *
 * <h3>Not yet talking to AWS</h3>
 *
 * <p>Every method here is deliberately a stub: the shape, the mapping and the lifecycle around it are real and
 * exercised, but nothing reaches EC2 and no money is spent. A submitted reservation is reported as
 * {@link CloudCapacityReservationState#PENDING} for as long as it is asked about, which is what AWS genuinely
 * does while it assesses a future-dated request - so the monitor polls, the reservation sits in
 * {@code SCHEDULED}, and no pool is ever activated. That is the intended behaviour of this build, not an
 * oversight.
 *
 * <p>What is left to do when this goes live is confined to the four method bodies below; each carries the AWS
 * call and the fields it needs. The SDK v2 dependency is already declared
 * ({@code software.amazon.awssdk:ec2}) because v1 has neither {@code StartDate}/{@code CommitmentDuration} nor
 * the {@code assessing}/{@code unsupported} states.
 *
 * <h3>Two things the real implementation must not lose</h3>
 *
 * <p>{@code instanceMatchCriteria} must be {@code TARGETED}, never {@code OPEN}: with {@code OPEN}, unrelated
 * instances in the same account silently consume capacity a user was promised. And the resolved availability
 * zone must be returned, because a reservation lives in exactly one zone and a node launched elsewhere pays
 * on-demand while the reservation idles.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AwsCapacityReservationService implements CapacityReservationCloudService {

    /**
     * Prefix of the synthetic identifiers this stub hands out, so a reservation that never touched AWS is
     * obvious in the database and cannot be mistaken for a real {@code cr-...} id.
     */
    static final String STUB_RESERVATION_ID_PREFIX = "stub-cr-";

    private final CloudRegionManager regionManager;

    @Override
    public CloudProvider getProvider() {
        return CloudProvider.AWS;
    }

    @Override
    public CloudCapacityReservation createFutureDated(final CapacityReservation reservation) {
        final AwsRegion region = region(reservation);
        // TODO: CreateCapacityReservationRequest - instanceType, instancePlatform, instanceCount,
        //  startDate = reservation.getStartDate(), commitmentDuration = durationHours in seconds,
        //  deliveryPreference = INCREMENTAL, instanceMatchCriteria = TARGETED,
        //  clientToken = reservation.getClientToken(), tag CP-capacity-reservation-id = reservation.getId().
        //  Return the provider's id and the zone it resolved.
        log.warn("Capacity reservation {} was not submitted to AWS: the reservation cloud service is a stub. "
                        + "Reporting it as pending in region {}.", reservation.getId(), region.getId());
        return CloudCapacityReservation.builder()
                .cloudReservationId(STUB_RESERVATION_ID_PREFIX + reservation.getId() + "-"
                        + reservation.getAttempt())
                .availabilityZone(stubZone(region))
                .state(CloudCapacityReservationState.PENDING)
                .startDate(reservation.getStartDate())
                .endDate(reservation.getEndDate())
                .build();
    }

    @Override
    public CloudCapacityReservation describe(final CapacityReservation reservation) {
        // TODO: DescribeCapacityReservationsRequest by reservation.getCloudReservationId(), then
        //  toState(response.state()).
        return CloudCapacityReservation.builder()
                .cloudReservationId(reservation.getCloudReservationId())
                .availabilityZone(reservation.getAvailabilityZone())
                .state(CloudCapacityReservationState.PENDING)
                .startDate(reservation.getStartDate())
                .endDate(reservation.getEndDate())
                .build();
    }

    @Override
    public void cancel(final CapacityReservation reservation) {
        // TODO: CancelCapacityReservationRequest by reservation.getCloudReservationId().
        log.warn("Capacity reservation {} was not cancelled at AWS: the reservation cloud service is a stub.",
                reservation.getId());
    }

    @Override
    public Optional<CloudCapacityReservation> findByClientToken(final CapacityReservation reservation,
                                                               final String clientToken) {
        // TODO: DescribeCapacityReservationsRequest filtered on the CP-capacity-reservation-id tag, then match
        //  the client token. Returning empty here is safe for a stub - nothing was ever created - but a real
        //  implementation returning empty when a reservation does exist would cause a second purchase.
        return Optional.empty();
    }

    /**
     * Maps AWS's own reservation state onto the platform's vocabulary.
     *
     * <p>Kept here, and package-visible, so it can be tested without AWS: it is the part of this class that will
     * still be worth trusting once the calls above are real. Note {@code unsupported} is deliberately not a
     * failure - for a future-dated request it means "not at this date", and the monitor answers by trying the
     * next one.
     */
    static CloudCapacityReservationState toState(final String awsState) {
        if (awsState == null) {
            return CloudCapacityReservationState.PENDING;
        }
        switch (awsState.toLowerCase()) {
            case "active":
                return CloudCapacityReservationState.ACTIVE;
            case "scheduled":
                return CloudCapacityReservationState.SCHEDULED;
            case "assessing":
            case "delayed":
            case "payment-pending":
                return CloudCapacityReservationState.PENDING;
            case "unsupported":
                return CloudCapacityReservationState.UNSUPPORTED;
            case "failed":
            case "payment-failed":
                return CloudCapacityReservationState.FAILED;
            case "cancelled":
            case "cancelling":
                return CloudCapacityReservationState.CANCELLED;
            case "expired":
                return CloudCapacityReservationState.EXPIRED;
            default:
                log.warn("Unrecognised AWS capacity reservation state '{}'; treating it as pending so the "
                        + "reservation is polled again rather than failed on a state we do not know", awsState);
                return CloudCapacityReservationState.PENDING;
        }
    }

    private AwsRegion region(final CapacityReservation reservation) {
        return (AwsRegion) regionManager.load(reservation.getRegionId());
    }

    /**
     * A plausible zone for the stub: the region's own name with a zone suffix, so the value looks like what AWS
     * would return and the availability-zone plumbing is genuinely exercised.
     */
    private String stubZone(final AwsRegion region) {
        return region.getRegionCode() + "a";
    }
}
