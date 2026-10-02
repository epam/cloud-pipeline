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
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservationStatus;
import com.epam.pipeline.entity.cluster.capacityreservation.CloudCapacityReservation;
import com.epam.pipeline.entity.cluster.capacityreservation.CloudCapacityReservationState;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.cluster.capacityreservation.cloud.CapacityReservationCloudService;
import com.epam.pipeline.manager.cluster.capacityreservation.cloud.CapacityReservationSubmissionUncertainException;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.region.CloudRegionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.ApplyCancellationCharges;
import software.amazon.awssdk.services.ec2.model.CancelCapacityReservationRequest;
import software.amazon.awssdk.services.ec2.model.CapacityReservationCancellationQuote;
import software.amazon.awssdk.services.ec2.model.CapacityReservationCommitmentInfo;
import software.amazon.awssdk.services.ec2.model.CapacityReservationDeliveryPreference;
import software.amazon.awssdk.services.ec2.model.CapacityReservationState;
import software.amazon.awssdk.services.ec2.model.CreateCapacityReservationCancellationQuoteRequest;
import software.amazon.awssdk.services.ec2.model.CreateCapacityReservationRequest;
import software.amazon.awssdk.services.ec2.model.DescribeCapacityReservationsRequest;
import software.amazon.awssdk.services.ec2.model.DescribeInstanceTypeOfferingsRequest;
import software.amazon.awssdk.services.ec2.model.Ec2Exception;
import software.amazon.awssdk.services.ec2.model.Filter;
import software.amazon.awssdk.services.ec2.model.InstanceMatchCriteria;
import software.amazon.awssdk.services.ec2.model.InstanceTypeOffering;
import software.amazon.awssdk.services.ec2.model.LocationType;
import software.amazon.awssdk.services.ec2.model.ResourceType;
import software.amazon.awssdk.services.ec2.model.Tag;
import software.amazon.awssdk.services.ec2.model.TagSpecification;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * AWS On-Demand Capacity Reservations, future-dated.
 *
 * <h3>The request AWS requires</h3>
 *
 * <p>A future-dated reservation is not an ordinary one with a date attached - AWS imposes its own rules, and each
 * of the following is set because the API refuses or misbehaves without it:
 *
 * <ul>
 *   <li>{@code instanceMatchCriteria = TARGETED} - mandatory for future-dated requests, and the right thing
 *       regardless: with {@code OPEN}, unrelated instances in the account silently consume capacity a user was
 *       promised.</li>
 *   <li>{@code deliveryPreference = INCREMENTAL} - the only value AWS supports here.</li>
 *   <li>{@code commitmentDuration} in <em>seconds</em>, the minimum time we promise to keep the reservation active
 *       once delivered.</li>
 *   <li>No {@code endDate}. AWS forbids an end date inside the commitment duration, and ours would land exactly on
 *       its boundary. So the reservation is created open-ended and this platform ends it explicitly - see
 *       {@link #cancel}.</li>
 * </ul>
 *
 * <h3>Why the client token is also written as a tag</h3>
 *
 * <p>{@code clientToken} makes the create call idempotent, but AWS never gives it back: {@code
 * DescribeCapacityReservations} returns no token field. So reconciling "did my last attempt actually create
 * something?" cannot be done on the token alone. The token is therefore also stored as a tag, which describe
 * <em>can</em> filter on, and {@link #findByClientToken} searches that tag. Without this the idempotency guard
 * would be unable to see an existing reservation and would buy a second one.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AwsCapacityReservationService implements CapacityReservationCloudService {

    /**
     * Tag carrying the idempotency token, because describe cannot filter on the token itself.
     */
    static final String CLIENT_TOKEN_TAG = "CP-capacity-reservation-client-token";

    /**
     * Tag carrying this platform's own reservation id, so a reservation found in the console is traceable back to
     * the pool that asked for it.
     */
    static final String RESERVATION_ID_TAG = "CP-capacity-reservation-id";

    private static final String NAME_TAG = "Name";
    private static final int SECONDS_PER_HOUR = 3600;
    /**
     * AWS accepts a future-dated reservation only when it starts at least this many days after the request.
     */
    public static final int MINIMUM_LEAD_DAYS = 5;

    static final String CAPACITY_RESERVATION_SPECIFICATION = "CapacityReservationSpecification";

    private static final int HTTP_SERVER_ERROR = 500;
    /**
     * States in which AWS no longer holds - or bills for - the capacity, or never delivered it.
     */
    private static final Set<CapacityReservationState> ALREADY_RELEASED = Collections.unmodifiableSet(EnumSet.of(
            CapacityReservationState.CANCELLED, CapacityReservationState.CANCELLING,
            CapacityReservationState.EXPIRED, CapacityReservationState.FAILED,
            CapacityReservationState.PAYMENT_FAILED, CapacityReservationState.UNSUPPORTED));
    /**
     * What EC2 answers with for an id it does not know, or cannot even parse - an error, not an empty list.
     */
    private static final Set<String> UNKNOWN_RESERVATION_ERROR_CODES = new HashSet<>(Arrays.asList(
            "InvalidCapacityReservationId.NotFound", "InvalidCapacityReservationId.Malformed"));
    /**
     * How long after submission an unknown reservation may only be not visible yet. The EC2 API is eventually
     * consistent, and a describe right after a create may answer that the new id does not exist; AWS gives no duration,
     * only advice to retry "up to a few minutes" - see
     * <a href="https://docs.aws.amazon.com/ec2/latest/devguide/eventual-consistency.html">Eventual consistency in the
     * Amazon EC2 API</a>. Five minutes is our reading of that, not a figure AWS publishes.
     */
    static final Duration UNKNOWN_RESERVATION_GRACE_PERIOD = Duration.ofMinutes(5);
    private static final Set<String> INSUFFICIENT_CAPACITY_ERROR_CODES = new HashSet<>(Arrays.asList(
            "InsufficientCapacity", "InsufficientInstanceCapacity", "InsufficientReservedInstanceCapacity"));

    private final CloudRegionManager regionManager;
    private final AWSV2EC2ClientProvider clientProvider;
    private final PreferenceManager preferenceManager;

    @Override
    public CloudProvider getProvider() {
        return CloudProvider.AWS;
    }

    @Override
    public Duration getMinimumLead() {
        return Duration.ofDays(MINIMUM_LEAD_DAYS);
    }

    /**
     * The {@code RunInstances} argument that sends an instance into a targeted reservation - without it, a
     * reservation created with {@code TARGETED} match criteria is never consumed.
     */
    @Override
    public Map<String, Object> launchSpecification(final CapacityReservation reservation) {
        return Collections.singletonMap(CAPACITY_RESERVATION_SPECIFICATION,
                Collections.singletonMap("CapacityReservationTarget",
                        Collections.singletonMap("CapacityReservationId", reservation.getCloudReservationId())));
    }

    @Override
    public List<String> candidateZones(final CapacityReservation reservation) {
        final AwsRegion region = region(reservation);
        final Map<String, String> networks = allowedNetworks(region.getRegionCode());
        final List<String> offered = uncertainOnTransientFailure(reservation, () -> {
            try (Ec2Client client = clientProvider.client(region)) {
                return ListUtils.emptyIfNull(client.describeInstanceTypeOfferings(
                                DescribeInstanceTypeOfferingsRequest.builder()
                                        .locationType(LocationType.AVAILABILITY_ZONE)
                                        .filters(Filter.builder()
                                                .name("instance-type")
                                                .values(reservation.getInstanceType())
                                                .build())
                                        .build())
                                .instanceTypeOfferings()).stream()
                        .map(InstanceTypeOffering::location)
                        .collect(Collectors.toList());
            }
        });
        return offered.stream()
                .filter(zone -> networks.isEmpty() || networks.containsKey(zone))
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    @Override
    public CloudCapacityReservation createFutureDated(final CapacityReservation reservation) {
        final AwsRegion region = region(reservation);
        final CreateCapacityReservationRequest.Builder request = CreateCapacityReservationRequest.builder()
                .instanceType(reservation.getInstanceType())
                .instancePlatform(reservation.getInstancePlatform())
                .instanceCount(reservation.getInstanceCount())
                .instanceMatchCriteria(InstanceMatchCriteria.TARGETED)
                .deliveryPreference(CapacityReservationDeliveryPreference.INCREMENTAL)
                .startDate(toInstant(reservation.getStartDate()))
                .commitmentDuration((long) reservation.getDurationHours() * SECONDS_PER_HOUR)
                .clientToken(reservation.getClientToken())
                .tagSpecifications(tags(reservation));

        // Only pin a zone when one is already known - on a first attempt AWS picks the zone with capacity, which
        // is more likely to succeed than any zone we could guess. Whatever it picks comes back and is what the
        // node launch is then pinned to.
        Optional.ofNullable(reservation.getAvailabilityZone())
                .filter(zone -> !zone.isEmpty())
                .ifPresent(request::availabilityZone);

        log.debug("Submitting future-dated capacity reservation {} to AWS in region {} for {} from {}",
                reservation.getId(), region.getRegionCode(), reservation.getInstanceType(),
                reservation.getStartDate());

        return uncertainOnTransientFailure(reservation, () -> {
            try (Ec2Client client = clientProvider.client(region)) {
                return toCloudReservation(client.createCapacityReservation(request.build()).capacityReservation());
            }
        });
    }

    @Override
    public CloudCapacityReservation describe(final CapacityReservation reservation) {
        final AwsRegion region = region(reservation);
        try (Ec2Client client = clientProvider.client(region)) {
            final Optional<software.amazon.awssdk.services.ec2.model.CapacityReservation> current =
                    findById(client, reservation.getCloudReservationId());
            if (current.isPresent()) {
                return toCloudReservation(current.get());
            }
            // Thrown rather than answered while it may only not be visible yet: the step is rolled back, and the next
            // cycle asks again.
            final boolean shouldBeVisible =
                    CapacityReservationStatus.ASSESSING_BY_CLOUD_PROVIDER != reservation.getStatus()
                    || Optional.ofNullable(reservation.getUpdated())
                        .map(updated -> updated.isBefore(DateUtils.nowUTC().minus(UNKNOWN_RESERVATION_GRACE_PERIOD)))
                        .orElse(true);
            Assert.state(
                    shouldBeVisible,
                    String.format("AWS does not show capacity reservation %s in region %s yet; will ask again",
                    reservation.getCloudReservationId(), region.getRegionCode())
            );
            // Past that, unknown is not a transient failure - a genuine outage throws instead. Failing it is the honest
            // outcome: there is nothing to wait for, and nothing was left running that could still cost money.
            return CloudCapacityReservation.builder()
                    .cloudReservationId(reservation.getCloudReservationId())
                    .state(CloudCapacityReservationState.FAILED)
                    .stateReason(String.format("AWS does not know capacity reservation %s in region %s",
                            reservation.getCloudReservationId(), region.getRegionCode()))
                    .build();
        }
    }

    /**
     * Ends the reservation at AWS.
     *
     * <p>This is not only a user-initiated cancel: because the reservation is created without an end date (see the
     * class comment), it is also how a reservation that has run its course stops costing money.
     *
     */
    @Override
    public void cancel(final CapacityReservation reservation) {
        final AwsRegion region = region(reservation);
        try (Ec2Client client = clientProvider.client(region)) {
            final Optional<software.amazon.awssdk.services.ec2.model.CapacityReservation> current =
                    findById(client, reservation.getCloudReservationId());
            if (current.isPresent()) {
                if (ALREADY_RELEASED.contains(current.get().state())) {
                    // A retried step: the monitor rolls a whole step back when a later write in it fails, so a cancel
                    // AWS already carried out comes round again. There is nothing left to release, and asking AWS to
                    // cancel it anyway would fail the step every time.
                    log.debug("Capacity reservation {} ({}) is already {} at AWS; nothing to cancel",
                            reservation.getId(), reservation.getCloudReservationId(), current.get().stateAsString());
                    return;
                }
                if (requiresCancellationQuote(current.get())) {
                    cancelWithCommitmentWindDown(client, reservation);
                    return;
                }
            }
            // Not shown is not proof of gone - a reservation created moments ago may not be visible yet - so it is
            // cancelled all the same, and only the cancel's own answer settles that there is nothing to release.
            cancelPlainly(client, reservation);
        }
    }

    private void cancelPlainly(final Ec2Client client, final CapacityReservation reservation) {
        log.debug("Cancelling capacity reservation {} ({}) at AWS", reservation.getId(),
                reservation.getCloudReservationId());
        try {
            client.cancelCapacityReservation(CancelCapacityReservationRequest.builder()
                    .capacityReservationId(reservation.getCloudReservationId())
                    .build());
        } catch (Ec2Exception e) {
            if (!isUnknownReservation(e)) {
                throw e;
            }
            // Already gone, never existed, or an id AWS cannot even parse. Either way nothing is held or billed.
            log.warn("Capacity reservation {} ({}) is unknown to AWS; nothing to cancel",
                    reservation.getId(), reservation.getCloudReservationId());
        }
    }

    /**
     * Whether AWS will refuse a plain cancel and demand a quote.
     */
    private boolean requiresCancellationQuote(
            final software.amazon.awssdk.services.ec2.model.CapacityReservation awsReservation) {
        if (CapacityReservationState.SCHEDULED == awsReservation.state()) {
            return true;
        }
        if (CapacityReservationState.ACTIVE != awsReservation.state()) {
            return false;
        }
        return Optional.ofNullable(awsReservation.commitmentInfo())
                .map(CapacityReservationCommitmentInfo::commitmentEndDate)
                .filter(end -> end.isAfter(Instant.now()))
                .isPresent();
    }

    /**
     * Releases the capacity and accepts the wind-down charge, which is the only way out of a live commitment.
     */
    private void cancelWithCommitmentWindDown(final Ec2Client client, final CapacityReservation reservation) {
        final CapacityReservationCancellationQuote quote = client.createCapacityReservationCancellationQuote(
                        CreateCapacityReservationCancellationQuoteRequest.builder()
                                .capacityReservationId(reservation.getCloudReservationId())
                                // Same reasoning as the create call: a retried quote must not become a second one.
                                .clientToken(cancellationToken(reservation))
                                .build())
                .capacityReservationCancellationQuote();

        log.warn("Capacity reservation {} ({}) is being cancelled inside its commitment duration; accepting the "
                        + "commitment wind-down charge under quote {}", reservation.getId(),
                reservation.getCloudReservationId(), quote.capacityReservationCancellationQuoteId());

        client.cancelCapacityReservation(CancelCapacityReservationRequest.builder()
                .capacityReservationId(reservation.getCloudReservationId())
                .quoteId(quote.capacityReservationCancellationQuoteId())
                .applyCancellationCharges(ApplyCancellationCharges.COMMITMENT_WIND_DOWN)
                .build());
    }

    static String cancellationToken(final CapacityReservation reservation) {
        return String.format("cp-cr-cancel-%d-%d", reservation.getId(), reservation.getAttempt());
    }

    /**
     * Empty when AWS does not know the id - whether it answers with no reservation or, as EC2 does for an id it has
     * never had, has purged or cannot parse, with one of {@link #UNKNOWN_RESERVATION_ERROR_CODES}.
     */
    private Optional<software.amazon.awssdk.services.ec2.model.CapacityReservation> findById(
            final Ec2Client client, final String cloudReservationId) {
        try {
            return ListUtils.emptyIfNull(client.describeCapacityReservations(
                            DescribeCapacityReservationsRequest.builder()
                                    .capacityReservationIds(cloudReservationId)
                                    .build())
                            .capacityReservations()).stream()
                    .findFirst();
        } catch (Ec2Exception e) {
            if (isUnknownReservation(e)) {
                return Optional.empty();
            }
            throw e;
        }
    }

    @Override
    public Optional<CloudCapacityReservation> findByClientToken(final CapacityReservation reservation,
                                                               final String clientToken) {
        final AwsRegion region = region(reservation);
        return uncertainOnTransientFailure(reservation, () -> {
            try (Ec2Client client = clientProvider.client(region)) {
                return ListUtils.emptyIfNull(client.describeCapacityReservations(
                                DescribeCapacityReservationsRequest.builder()
                                        .filters(Filter.builder()
                                                .name("tag:" + CLIENT_TOKEN_TAG)
                                                .values(clientToken)
                                                .build())
                                        .build())
                                .capacityReservations()).stream()
                        .findFirst()
                        .map(AwsCapacityReservationService::toCloudReservation);
            }
        });
    }

    /**
     * Separates a failure that leaves the outcome open from a definitive refusal.
     *
     * <p>A client-side failure - a timeout, a dropped connection - may have happened after AWS acted, and throttling
     * or a 5xx is AWS saying "not now" rather than "no". Both are safe to retry with the same token and must be,
     * or a reservation AWS did create is abandoned. Any other service error is a 4xx refusal of the request itself,
     * which means nothing was created and a retry would be refused again - so it is left as it is.
     *
     * <p>EC2 reports running out of capacity as a 5xx, but it is a refusal like any 4xx: nothing was created, and
     * retrying the same request would only ask for the same missing capacity again.
     */
    private static <T> T uncertainOnTransientFailure(final CapacityReservation reservation,
                                                     final Supplier<T> call) {
        try {
            return call.get();
        } catch (SdkClientException e) {
            throw uncertain(reservation, e);
        } catch (SdkServiceException e) {
            if (e.isThrottlingException()
                    || e.statusCode() >= HTTP_SERVER_ERROR && !isInsufficientCapacity(e)) {
                throw uncertain(reservation, e);
            }
            throw e;
        }
    }

    private static boolean isInsufficientCapacity(final SdkServiceException e) {
        return e instanceof AwsServiceException
                && Optional.ofNullable(((AwsServiceException) e).awsErrorDetails())
                        .map(AwsErrorDetails::errorCode)
                        .filter(INSUFFICIENT_CAPACITY_ERROR_CODES::contains)
                        .isPresent();
    }

    private static boolean isUnknownReservation(final AwsServiceException e) {
        return Optional.ofNullable(e.awsErrorDetails())
                .map(AwsErrorDetails::errorCode)
                .filter(UNKNOWN_RESERVATION_ERROR_CODES::contains)
                .isPresent();
    }

    private static CapacityReservationSubmissionUncertainException uncertain(final CapacityReservation reservation,
                                                                             final RuntimeException cause) {
        return new CapacityReservationSubmissionUncertainException(String.format(
                "AWS did not give a definitive answer for capacity reservation %d, will retry: %s",
                reservation.getId(), cause.getMessage()), cause);
    }

    /**
     * Maps AWS's own reservation state onto the platform's vocabulary.
     *
     * <p>Package-visible so it can be tested without AWS.
     *
     * <p>{@link CloudCapacityReservationState#UNSUPPORTED} is the state that makes the monitor abandon this
     * reservation and buy another for a later date, so the boundary around it is the one that costs money to get
     * wrong - and it is wrong in one direction only. Mapping a state there that AWS still intends to deliver
     * creates a second reservation alongside the first and clears the id that could have cancelled it. Mapping a
     * genuinely dead state to {@code PENDING} instead only leaves a reservation visibly stuck, which a human can
     * sort out. So only a state AWS documents as undeliverable may slide:
     *
     * <ul>
     *   <li>{@code unsupported} - slides straight away. AWS is explicit: "Unsupported Capacity Reservations are not
     *       delivered". Nothing exists to pay for or cancel, so asking for another date is a clean new request.</li>
     *   <li>{@code delayed} - "Amazon EC2 encountered a delay in provisioning ... unable to deliver the requested
     *       capacity by the requested start date and time". Late, not refused - the reservation is still owed to
     *       us, so it gets its own state and the monitor releases it before asking for another date. AWS waives
     *       the commitment for a delay, so that release is free.</li>
     *   <li>{@code unavailable} - AWS lists this value among the valid states without documenting what it means.
     *       Polled rather than acted on: guessing "still coming" about a dead reservation only leaves it visibly
     *       stuck, whereas guessing "dead" about a live one risks paying for two.</li>
     *   <li>{@code cancelling} - capacity is already released, so it is treated as cancelled even though charges
     *       continue through the wind-down period.</li>
     * </ul>
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
            case "pending":
            case "payment-pending":
            case "unavailable":
                return CloudCapacityReservationState.PENDING;
            case "delayed":
                return CloudCapacityReservationState.DELAYED;
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

    /**
     * AWS carries no state-reason field on a reservation, so anything the monitor reports to a user has to be
     * built from the state itself.
     */
    private static CloudCapacityReservation toCloudReservation(
            final software.amazon.awssdk.services.ec2.model.CapacityReservation reservation) {
        final String awsState = reservation.stateAsString();
        final CloudCapacityReservationState state = toState(awsState);
        return CloudCapacityReservation.builder()
                .cloudReservationId(reservation.capacityReservationId())
                .availabilityZone(reservation.availabilityZone())
                .state(state)
                .startDate(toLocalDateTime(reservation.startDate()))
                .endDate(toLocalDateTime(reservation.endDate()))
                .stateReason(state == CloudCapacityReservationState.ACTIVE
                        || state == CloudCapacityReservationState.SCHEDULED
                        || state == CloudCapacityReservationState.PENDING
                        ? null
                        : String.format("AWS reported the reservation as '%s'", awsState))
                // What AWS committed us to, which it may have shortened from what was asked for.
                .grantedCommitmentSeconds(Optional.ofNullable(reservation.commitmentInfo())
                        .map(CapacityReservationCommitmentInfo::commitmentDuration)
                        .orElse(null))
                .build();
    }

    private TagSpecification tags(final CapacityReservation reservation) {
        return TagSpecification.builder()
                .resourceType(ResourceType.CAPACITY_RESERVATION)
                .tags(Tag.builder().key(NAME_TAG).value(reservation.getName()).build(),
                        Tag.builder().key(RESERVATION_ID_TAG)
                                .value(String.valueOf(reservation.getId())).build(),
                        Tag.builder().key(CLIENT_TOKEN_TAG).value(reservation.getClientToken()).build())
                .build();
    }

    private static Instant toInstant(final LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.toInstant(ZoneOffset.UTC);
    }

    private static LocalDateTime toLocalDateTime(final Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private AwsRegion region(final CapacityReservation reservation) {
        return (AwsRegion) regionManager.load(reservation.getRegionId());
    }

    /**
     * Zone to subnet, as the region's {@code networks} configure them; empty where they configure none.
     */
    private Map<String, String> allowedNetworks(final String regionCode) {
        return Optional.ofNullable(preferenceManager.getPreference(SystemPreferences.CLUSTER_NETWORKS_CONFIG))
                .map(configuration -> configuration.allowedNetworks(regionCode))
                .orElseGet(Collections::emptyMap);
    }
}
