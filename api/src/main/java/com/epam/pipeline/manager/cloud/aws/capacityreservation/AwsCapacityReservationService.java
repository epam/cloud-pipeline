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

import com.epam.pipeline.controller.vo.cluster.capacityreservation.CapacityBlockOfferingRequest;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityBlockInstanceTypeInfo;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityBlockOffering;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityBlockResourceType;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservation;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservationStatus;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservationType;
import com.epam.pipeline.entity.cluster.capacityreservation.CloudCapacityReservation;
import com.epam.pipeline.entity.cluster.capacityreservation.CloudCapacityReservationState;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
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
import org.apache.commons.lang3.StringUtils;
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
import software.amazon.awssdk.services.ec2.model.DescribeCapacityBlockOfferingsRequest;
import software.amazon.awssdk.services.ec2.model.DescribeCapacityReservationsRequest;
import software.amazon.awssdk.services.ec2.model.DescribeInstanceTypeOfferingsRequest;
import software.amazon.awssdk.services.ec2.model.DescribeInstanceTypesRequest;
import software.amazon.awssdk.services.ec2.model.Ec2Exception;
import software.amazon.awssdk.services.ec2.model.Filter;
import software.amazon.awssdk.services.ec2.model.GpuDeviceInfo;
import software.amazon.awssdk.services.ec2.model.InstanceMatchCriteria;
import software.amazon.awssdk.services.ec2.model.InstanceTypeInfo;
import software.amazon.awssdk.services.ec2.model.InstanceTypeOffering;
import software.amazon.awssdk.services.ec2.model.LocationType;
import software.amazon.awssdk.services.ec2.model.MemoryInfo;
import software.amazon.awssdk.services.ec2.model.PurchaseCapacityBlockRequest;
import software.amazon.awssdk.services.ec2.model.ResourceType;
import software.amazon.awssdk.services.ec2.model.Tag;
import software.amazon.awssdk.services.ec2.model.TagSpecification;
import software.amazon.awssdk.services.ec2.model.VCpuInfo;

import java.math.BigDecimal;
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
 * AWS implementation of {@link CapacityReservationCloudService}, over the EC2 future-dated capacity reservation
 * API.
 *
 * <p>What it asks of AWS, and what it makes of the answers:
 * <ul>
 *   <li><b>Submits</b> a request as a future-dated reservation - {@code InstanceMatchCriteria=targeted},
 *       {@code DeliveryPreference=incremental}, committed for the duration the request names - under a client token
 *       that makes the call idempotent, and tags it with that token and its own id.</li>
 *   <li><b>Offers zones</b>: those of the region's configured networks that actually offer the instance type - or
 *       every zone that offers it, where the region configures no networks - so a request is only ever made where
 *       AWS could deliver it.</li>
 *   <li><b>Describes</b> a reservation, mapping the EC2 state onto a {@link CloudCapacityReservationState} the
 *       monitor understands and reading back the commitment AWS granted. A reservation created moments ago may not
 *       be visible yet, so while the request is still being assessed a lookup that cannot find it is retried for a
 *       short grace period before it is believed.</li>
 *   <li><b>Cancels</b>: plainly once nothing is committed any more, and through a cancellation quote while the
 *       reservation is scheduled or still inside its commitment. A reservation AWS no longer knows, or an id it
 *       cannot parse, is nothing to cancel.</li>
 * </ul>
 *
 * <p>The three calls that can lead to capacity being paid for - the zone lookup, the submission itself and the
 * lookup by client token - raise {@link CapacityReservationSubmissionUncertainException} instead of reporting a
 * refusal whenever an error leaves the outcome genuinely unknown: throttling, a server-side failure, a reply that
 * never arrived. The caller then retries the same client token rather than paying for a second reservation. A
 * definitive refusal is passed on as it is, including insufficient capacity, which AWS reports with a server-side
 * status but which refuses this configuration rather than failing the call.
 *
 * <p>AWS will not assess a future-dated request made less than {@link #MINIMUM_LEAD_DAYS} days ahead, which is what
 * {@link #getMinimumLead()} reports to the monitor.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AwsCapacityReservationService implements CapacityReservationCloudService {

    static final String CLIENT_TOKEN_TAG = "cp-capacity-reservation-client-token";

    static final String RESERVATION_ID_TAG = "cp-capacity-reservation-id";

    private static final String NAME_TAG = "Name";
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

    private static final String SUPPORTED_USAGE_CLASS_FILTER = "supported-usage-class";
    private static final String CAPACITY_BLOCK_USAGE_CLASS = "capacity-block";
    static final int HOURS_PER_DAY = 24;
    static final int ONE_DAY_STEP_MAX_DAYS = 14;
    private static final int SEVEN_DAY_STEP_DAYS = 7;
    static final int SEVEN_DAY_STEP_MAX_DAYS = 182;
    static final int MAX_CAPACITY_BLOCK_INSTANCE_COUNT = 64;
    static final int MAX_CAPACITY_BLOCK_START_DATE_LEAD_WEEKS = 8;

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
                .commitmentDuration(reservation.getCommitmentDuration())
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

    /**
     * Buys the exact offering named on the reservation - no window, no zone to pick, since the offering already
     * fixed both. Unlike {@link #createFutureDated}, AWS gives this call no idempotency token of its own: the tag
     * {@link #tags(CapacityReservation)} attaches is the only way a lost reply can later be told apart from a
     * purchase that never happened, and {@link #findByClientToken} is how a caller must check that before ever
     * calling this again for the same reservation.
     */
    @Override
    public CloudCapacityReservation purchaseCapacityBlock(final CapacityReservation reservation) {
        final AwsRegion region = region(reservation);
        final PurchaseCapacityBlockRequest request = PurchaseCapacityBlockRequest.builder()
                .capacityBlockOfferingId(reservation.getCapacityBlockOfferingId())
                .instancePlatform(reservation.getInstancePlatform())
                .tagSpecifications(tags(reservation))
                .build();

        log.debug("Purchasing capacity block {} for node pool, offering {}", reservation.getId(),
                reservation.getCapacityBlockOfferingId());

        return uncertainOnTransientFailure(reservation, () -> {
            try (Ec2Client client = clientProvider.client(region)) {
                return toCloudReservation(client.purchaseCapacityBlock(request).capacityReservation());
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
     * Ends the reservation at AWS - except a capacity block, which this refuses outright rather than attempting:
     * AWS gives no cancel call for one at all, so there is nothing this could do but fail, and failing here rather
     * than silently doing nothing is what makes a caller's mistake visible instead of masking it. The human-facing
     * cancel path already refuses a purchased capacity block before ever reaching here; a caller that still reaches
     * this method for one - such as automatic cleanup at the end of its fixed window - must check the type itself
     * and not call this at all, since AWS already reclaims the block on its own.
     */
    @Override
    public void cancel(final CapacityReservation reservation) {
        Assert.state(CapacityReservationType.CAPACITY_BLOCK != reservation.getReservationType(),
                String.format("Capacity block %s (%s) cannot be cancelled: the cloud provider gives no way to "
                        + "give a purchased capacity block back.",
                        reservation.getId(), reservation.getCloudReservationId()));
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
        return String.format("cp-capacity-reservation-cancel-%d-%d", reservation.getId(), reservation.getAttempt());
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
     * Searches AWS's current capacity block offerings. Nothing here is paid for or committed to - a browse failure
     * is reported as it comes, not retried as uncertain the way {@link #createFutureDated} is.
     */
    @Override
    public List<CapacityBlockOffering> listCapacityBlockOfferings(final AbstractCloudRegion region,
                                                                  final CapacityBlockOfferingRequest request) {
        validateCapacityBlockDuration(request.getCapacityDurationHours());
        validateCapacityBlockInstanceCount(request.getInstanceCount());
        validateCapacityBlockStartDateRange(request.getStartDateRange());
        final DescribeCapacityBlockOfferingsRequest.Builder awsRequest = DescribeCapacityBlockOfferingsRequest
                .builder()
                .instanceType(request.getInstanceType())
                .instanceCount(request.getInstanceCount())
                .capacityDurationHours(request.getCapacityDurationHours())
                .startDateRange(toInstant(request.getStartDateRange()));
        Optional.ofNullable(request.getEndDateRange()).ifPresent(end -> awsRequest.endDateRange(toInstant(end)));
        try (Ec2Client client = clientProvider.client((AwsRegion) region)) {
            return client.describeCapacityBlockOfferingsPaginator(awsRequest.build())
                    .capacityBlockOfferings().stream()
                    .map(AwsCapacityReservationService::toCapacityBlockOffering)
                    .collect(Collectors.toList());
        }
    }

    /**
     * Lists the instance types AWS currently sells as capacity blocks in a region - the EC2 equivalent of
     * {@code aws ec2 describe-instance-types --filters "Name=supported-usage-class,Values=capacity-block"}.
     */
    @Override
    public List<CapacityBlockInstanceTypeInfo> listCapacityBlockInstanceTypes(final AbstractCloudRegion region) {
        final DescribeInstanceTypesRequest awsRequest = DescribeInstanceTypesRequest.builder()
                .filters(Filter.builder()
                        .name(SUPPORTED_USAGE_CLASS_FILTER)
                        .values(CAPACITY_BLOCK_USAGE_CLASS)
                        .build())
                .build();
        try (Ec2Client client = clientProvider.client((AwsRegion) region)) {
            return client.describeInstanceTypesPaginator(awsRequest)
                    .instanceTypes().stream()
                    .map(AwsCapacityReservationService::toCapacityBlockInstanceTypeInfo)
                    .collect(Collectors.toList());
        }
    }

    private static CapacityBlockInstanceTypeInfo toCapacityBlockInstanceTypeInfo(final InstanceTypeInfo info) {
        final GpuDeviceInfo gpu = Optional.ofNullable(info.gpuInfo())
                .map(gpuInfo -> gpuInfo.gpus().stream().findFirst().orElse(null))
                .orElse(null);
        return CapacityBlockInstanceTypeInfo.builder()
                .instanceType(info.instanceTypeAsString())
                .vCPU(Optional.ofNullable(info.vCpuInfo()).map(VCpuInfo::defaultVCpus).orElse(null))
                .memoryInMiB(Optional.ofNullable(info.memoryInfo()).map(MemoryInfo::sizeInMiB).orElse(null))
                .gpuCount(gpu == null ? null : gpu.count())
                .gpuDeviceName(gpu == null ? null : gpu.name())
                .build();
    }

    /**
     * AWS sells capacity blocks only in 1-day steps up to {@value #ONE_DAY_STEP_MAX_DAYS} days, then 7-day steps up
     * to {@value #SEVEN_DAY_STEP_MAX_DAYS} days. Checked here, before a request is built, rather than left for AWS's
     * own refusal to explain.
     */
    static void validateCapacityBlockDuration(final Integer hours) {
        Assert.isTrue(hours != null && hours % HOURS_PER_DAY == 0 && isValidCapacityBlockDurationDays(
                hours / HOURS_PER_DAY),
                String.format("Capacity block duration must be a whole number of days, in 1-day steps up to %d "
                        + "days or 7-day steps up to %d days; %s hours is neither.",
                        ONE_DAY_STEP_MAX_DAYS, SEVEN_DAY_STEP_MAX_DAYS, hours));
    }

    private static boolean isValidCapacityBlockDurationDays(final int days) {
        if (days >= 1 && days <= ONE_DAY_STEP_MAX_DAYS) {
            return true;
        }
        return days <= SEVEN_DAY_STEP_MAX_DAYS && days % SEVEN_DAY_STEP_DAYS == 0;
    }

    /**
     * AWS sells a capacity block for at most {@value #MAX_CAPACITY_BLOCK_INSTANCE_COUNT} instances in one offering.
     * Checked here, before a request is built, rather than left for AWS's own refusal to explain.
     */
    static void validateCapacityBlockInstanceCount(final Integer instanceCount) {
        Assert.isTrue(instanceCount != null && instanceCount >= 1
                        && instanceCount <= MAX_CAPACITY_BLOCK_INSTANCE_COUNT,
                String.format("Capacity block instance count must be an integer between 1 and %d; %s is not.",
                        MAX_CAPACITY_BLOCK_INSTANCE_COUNT, instanceCount));
    }

    /**
     * A search needs a start date to look from, and AWS only sells a capacity block starting up to
     * {@value #MAX_CAPACITY_BLOCK_START_DATE_LEAD_WEEKS} weeks in advance.
     */
    static void validateCapacityBlockStartDateRange(final LocalDateTime startDateRange) {
        final LocalDateTime latestStartDate = DateUtils.nowUTC().plusWeeks(MAX_CAPACITY_BLOCK_START_DATE_LEAD_WEEKS);
        Assert.isTrue(startDateRange != null && !startDateRange.isAfter(latestStartDate),
                String.format("Capacity block start date must be set, and no more than %d weeks away; %s is not.",
                        MAX_CAPACITY_BLOCK_START_DATE_LEAD_WEEKS, startDateRange));
    }

    private static CapacityBlockOffering toCapacityBlockOffering(
            final software.amazon.awssdk.services.ec2.model.CapacityBlockOffering offering) {
        return CapacityBlockOffering.builder()
                .offeringId(offering.capacityBlockOfferingId())
                .resourceType(StringUtils.isNotBlank(offering.ultraserverType())
                        ? CapacityBlockResourceType.ULTRASERVER
                        : CapacityBlockResourceType.INSTANCE)
                .instanceType(offering.instanceType())
                .availabilityZone(offering.availabilityZone())
                .instanceCount(offering.instanceCount())
                .startDate(toLocalDateTime(offering.startDate()))
                .endDate(toLocalDateTime(offering.endDate()))
                .capacityDurationHours(offering.capacityBlockDurationHours())
                .upfrontFeeAmount(StringUtils.isBlank(offering.upfrontFee()) ? null : new BigDecimal(
                        offering.upfrontFee()))
                .upfrontFeeCurrency(offering.currencyCode())
                .tenancy(offering.tenancyAsString())
                .build();
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
                .grantedCommitmentDuration(Optional.ofNullable(reservation.commitmentInfo())
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
