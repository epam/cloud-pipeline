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

@Service
@Slf4j
@RequiredArgsConstructor
public class AwsCapacityReservationService implements CapacityReservationCloudService {

    static final String CLIENT_TOKEN_TAG = "CP-capacity-reservation-client-token";

    static final String RESERVATION_ID_TAG = "CP-capacity-reservation-id";

    private static final String NAME_TAG = "Name";
    private static final int SECONDS_PER_HOUR = 3600;
    public static final int MINIMUM_LEAD_DAYS = 5;

    static final String CAPACITY_RESERVATION_SPECIFICATION = "CapacityReservationSpecification";

    private static final int HTTP_SERVER_ERROR = 500;
    private static final Set<CapacityReservationState> ALREADY_RELEASED = Collections.unmodifiableSet(EnumSet.of(
            CapacityReservationState.CANCELLED, CapacityReservationState.CANCELLING,
            CapacityReservationState.EXPIRED, CapacityReservationState.FAILED,
            CapacityReservationState.PAYMENT_FAILED, CapacityReservationState.UNSUPPORTED));
    private static final Set<String> UNKNOWN_RESERVATION_ERROR_CODES = new HashSet<>(Arrays.asList(
            "InvalidCapacityReservationId.NotFound", "InvalidCapacityReservationId.Malformed"));
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
     */
    @Override
    public void cancel(final CapacityReservation reservation) {
        final AwsRegion region = region(reservation);
        try (Ec2Client client = clientProvider.client(region)) {
            final Optional<software.amazon.awssdk.services.ec2.model.CapacityReservation> current =
                    findById(client, reservation.getCloudReservationId());
            if (current.isPresent()) {
                if (ALREADY_RELEASED.contains(current.get().state())) {
                    log.debug("Capacity reservation {} ({}) is already {} at AWS; nothing to cancel",
                            reservation.getId(), reservation.getCloudReservationId(), current.get().stateAsString());
                    return;
                }
                if (requiresCancellationQuote(current.get())) {
                    cancelWithCommitmentWindDown(client, reservation);
                    return;
                }
            }
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
            log.warn("Capacity reservation {} ({}) is unknown to AWS; nothing to cancel",
                    reservation.getId(), reservation.getCloudReservationId());
        }
    }

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

    private void cancelWithCommitmentWindDown(final Ec2Client client, final CapacityReservation reservation) {
        final CapacityReservationCancellationQuote quote = client.createCapacityReservationCancellationQuote(
                        CreateCapacityReservationCancellationQuoteRequest.builder()
                                .capacityReservationId(reservation.getCloudReservationId())
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
                return CloudCapacityReservationState.FAILED;
            case "payment-failed":
                return CloudCapacityReservationState.PAYMENT_FAILED;
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

    private Map<String, String> allowedNetworks(final String regionCode) {
        return Optional.ofNullable(preferenceManager.getPreference(SystemPreferences.CLUSTER_NETWORKS_CONFIG))
                .map(configuration -> configuration.allowedNetworks(regionCode))
                .orElseGet(Collections::emptyMap);
    }
}
