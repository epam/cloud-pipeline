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

import com.epam.pipeline.entity.cluster.CloudRegionsConfiguration;
import com.epam.pipeline.entity.cluster.NetworkConfiguration;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservation;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservationStatus;
import com.epam.pipeline.entity.cluster.capacityreservation.CloudCapacityReservation;
import com.epam.pipeline.entity.cluster.capacityreservation.CloudCapacityReservationState;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.cluster.capacityreservation.cloud.CapacityReservationSubmissionUncertainException;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.region.CloudRegionManager;
import com.epam.pipeline.test.creator.cluster.capacityreservation.CapacityReservationCreatorUtils;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.ApplyCancellationCharges;
import software.amazon.awssdk.services.ec2.model.CancelCapacityReservationRequest;
import software.amazon.awssdk.services.ec2.model.CancelCapacityReservationResponse;
import software.amazon.awssdk.services.ec2.model.CapacityReservationCancellationQuote;
import software.amazon.awssdk.services.ec2.model.CapacityReservationCommitmentInfo;
import software.amazon.awssdk.services.ec2.model.CreateCapacityReservationCancellationQuoteRequest;
import software.amazon.awssdk.services.ec2.model.CreateCapacityReservationCancellationQuoteResponse;
import software.amazon.awssdk.services.ec2.model.CreateCapacityReservationRequest;
import software.amazon.awssdk.services.ec2.model.CreateCapacityReservationResponse;
import software.amazon.awssdk.services.ec2.model.DescribeCapacityReservationsRequest;
import software.amazon.awssdk.services.ec2.model.DescribeCapacityReservationsResponse;
import software.amazon.awssdk.services.ec2.model.DescribeInstanceTypeOfferingsRequest;
import software.amazon.awssdk.services.ec2.model.DescribeInstanceTypeOfferingsResponse;
import software.amazon.awssdk.services.ec2.model.InstanceTypeOffering;
import software.amazon.awssdk.services.ec2.model.Ec2Exception;
import software.amazon.awssdk.services.ec2.model.Filter;
import software.amazon.awssdk.services.ec2.model.InstanceMatchCriteria;
import software.amazon.awssdk.services.ec2.model.Tag;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class AwsCapacityReservationServiceTest {

    private static final Long REGION_ID = 1L;
    private static final Long RESERVATION_ID = 10L;
    private static final String REGION_CODE = "eu-central-1";
    private static final String ZONE = "eu-central-1b";
    private static final String CLOUD_ID = "cr-0123456789abcdef0";
    private static final String TOKEN = "cp-capacity-reservation-10-0";
    private static final long COMMITMENT_DURATION = 24 * 3600L;
    private static final int INSTANCE_COUNT = 8;
    private static final int LEAD_DAYS = 7;
    private static final String AWS_SCHEDULED = "scheduled";
    private static final String AWS_ACTIVE = "active";
    private static final long GRANTED_COMMITMENT_DURATION = 864000L;
    private static final String QUOTE_ID = "crcq-0123456789abcdef0";
    private static final int HTTP_BAD_REQUEST = 400;
    private static final String NOT_FOUND_ERROR_CODE = "InvalidCapacityReservationId.NotFound";
    private static final String MALFORMED_ERROR_CODE = "InvalidCapacityReservationId.Malformed";
    private static final int HTTP_INTERNAL_ERROR = 500;
    private static final int HTTP_SERVICE_UNAVAILABLE = 503;

    @Mock
    private CloudRegionManager regionManager;

    @Mock
    private AWSV2EC2ClientProvider clientProvider;

    @Mock
    private PreferenceManager preferenceManager;

    private final FakeEc2Client client = new FakeEc2Client();

    private AwsCapacityReservationService service;

    @BeforeEach
    public void setUp() {
        final AwsRegion region = new AwsRegion();
        region.setId(REGION_ID);
        region.setRegionCode(REGION_CODE);
        when(regionManager.load(REGION_ID)).thenReturn(region);
        when(clientProvider.client(any())).thenReturn(client);
        service = new AwsCapacityReservationService(regionManager, clientProvider, preferenceManager);
    }

    @Test
    public void shouldBuildAFutureDatedRequestAwsWillAccept() {
        final CapacityReservation reservation = reservation();
        client.created = awsReservation(AWS_SCHEDULED, null);

        service.createFutureDated(reservation);

        assertThat(client.createRequest.instanceMatchCriteria()).isEqualTo(InstanceMatchCriteria.TARGETED);
        assertThat(client.createRequest.deliveryPreferenceAsString()).isEqualTo("incremental");
        assertThat(client.createRequest.commitmentDuration()).isEqualTo(COMMITMENT_DURATION);
        assertThat(client.createRequest.startDate())
                .isEqualTo(reservation.getStartDate().toInstant(ZoneOffset.UTC));
        assertThat(client.createRequest.instanceCount()).isEqualTo(INSTANCE_COUNT);
        assertThat(client.createRequest.clientToken()).isEqualTo(TOKEN);
        assertThat(client.createRequest.instanceType()).isEqualTo(reservation.getInstanceType());
    }

    @Test
    public void shouldReportACreateWithNoReplyAsUncertain() {
        client.createFailure = SdkClientException.create("Unable to execute HTTP request: Read timed out");

        assertThatThrownBy(() -> service.createFutureDated(reservation()))
                .isInstanceOf(CapacityReservationSubmissionUncertainException.class)
                .hasCauseInstanceOf(SdkClientException.class);
    }

    @Test
    public void shouldReportThrottlingAsUncertain() {
        client.createFailure = ec2Error(HTTP_BAD_REQUEST, "RequestLimitExceeded");

        assertThatThrownBy(() -> service.createFutureDated(reservation()))
                .isInstanceOf(CapacityReservationSubmissionUncertainException.class);
    }

    @Test
    public void shouldReportAProviderSideErrorAsUncertain() {
        client.createFailure = ec2Error(HTTP_SERVICE_UNAVAILABLE, "Unavailable");

        assertThatThrownBy(() -> service.createFutureDated(reservation()))
                .isInstanceOf(CapacityReservationSubmissionUncertainException.class);
    }

    @Test
    public void shouldLeaveADefinitiveRefusalAsItIs() {
        client.createFailure = ec2Error(HTTP_BAD_REQUEST, "InvalidParameterValue");

        assertThatThrownBy(() -> service.createFutureDated(reservation()))
                .isInstanceOf(Ec2Exception.class);
    }

    @Test
    public void shouldLeaveInsufficientCapacityAsARefusalDespiteItsServerErrorStatus() {
        client.createFailure = ec2Error(HTTP_INTERNAL_ERROR, "InsufficientInstanceCapacity");

        assertThatThrownBy(() -> service.createFutureDated(reservation()))
                .isInstanceOf(Ec2Exception.class);
    }

    @Test
    public void shouldReportALookupThatAwsCouldNotAnswerAsUncertain() {
        client.describeFailure = ec2Error(HTTP_SERVICE_UNAVAILABLE, "Unavailable");

        assertThatThrownBy(() -> service.findByClientToken(reservation(), TOKEN))
                .isInstanceOf(CapacityReservationSubmissionUncertainException.class);
    }

    @Test
    public void shouldLeaveALookupRefusalAsItIs() {
        client.describeFailure = ec2Error(HTTP_BAD_REQUEST, "InvalidParameterValue");

        assertThatThrownBy(() -> service.findByClientToken(reservation(), TOKEN))
                .isInstanceOf(Ec2Exception.class);
    }

    @Test
    public void shouldReportALookupThatCouldNotAskAsUncertain() {
        client.describeFailure = SdkClientException.create("Unable to execute HTTP request: Connect timed out");

        assertThatThrownBy(() -> service.findByClientToken(reservation(), TOKEN))
                .isInstanceOf(CapacityReservationSubmissionUncertainException.class);
    }

    @Test
    public void shouldNotSendAnEndDate() {
        client.created = awsReservation(AWS_SCHEDULED, null);

        service.createFutureDated(reservation());

        assertThat(client.createRequest.endDate()).isNull();
    }

    @Test
    public void shouldTagTheReservationWithItsClientTokenAndId() {
        client.created = awsReservation(AWS_SCHEDULED, null);

        service.createFutureDated(reservation());

        assertThat(client.createRequest.tagSpecifications().get(0).tags())
                .extracting("key", "value")
                .contains(Tuple.tuple(AwsCapacityReservationService.CLIENT_TOKEN_TAG, TOKEN),
                        Tuple.tuple(AwsCapacityReservationService.RESERVATION_ID_TAG,
                                String.valueOf(RESERVATION_ID)));
    }

    @Test
    public void shouldLeaveTheZoneToAwsWhenNoneIsKnownYet() {
        client.created = awsReservation(AWS_SCHEDULED, null);

        service.createFutureDated(reservation());

        assertThat(client.createRequest.availabilityZone()).isNull();
    }

    @Test
    public void shouldPinTheZoneOnceItIsKnown() {
        final CapacityReservation reservation = reservation();
        reservation.setAvailabilityZone(ZONE);
        client.created = awsReservation(AWS_SCHEDULED, ZONE);

        service.createFutureDated(reservation);

        assertThat(client.createRequest.availabilityZone()).isEqualTo(ZONE);
    }

    @Test
    public void shouldReturnTheResolvedZoneAndId() {
        client.created = awsReservation(AWS_SCHEDULED, ZONE);

        final CloudCapacityReservation result = service.createFutureDated(reservation());

        assertThat(result.getCloudReservationId()).isEqualTo(CLOUD_ID);
        assertThat(result.getAvailabilityZone()).isEqualTo(ZONE);
        assertThat(result.getState()).isEqualTo(CloudCapacityReservationState.SCHEDULED);
    }

    @Test
    public void shouldCloseTheClient() {
        client.created = awsReservation(AWS_SCHEDULED, ZONE);

        service.createFutureDated(reservation());

        assertThat(client.closed).isTrue();
    }

    @Test
    public void shouldDescribeByReservationId() {
        final CapacityReservation reservation = reservation();
        reservation.setCloudReservationId(CLOUD_ID);
        client.described = Collections.singletonList(awsReservation(AWS_ACTIVE, ZONE));

        final CloudCapacityReservation result = service.describe(reservation);

        assertThat(result.getState()).isEqualTo(CloudCapacityReservationState.ACTIVE);
        assertThat(client.describeRequest.capacityReservationIds()).containsExactly(CLOUD_ID);
    }

    @Test
    public void shouldFailWhenAwsDoesNotKnowTheReservation() {
        final CapacityReservation reservation = pastTheGrace(reservation());
        reservation.setCloudReservationId(CLOUD_ID);
        client.described = Collections.emptyList();

        final CloudCapacityReservation result = service.describe(reservation);

        assertThat(result.getState()).isEqualTo(CloudCapacityReservationState.FAILED);
        assertThat(result.getStateReason()).contains(CLOUD_ID);
    }

    @Test
    public void shouldAskAgainWhileAReservationMayNotBeVisibleYet() {
        final CapacityReservation reservation = reservation();
        reservation.setStatus(CapacityReservationStatus.ASSESSING_BY_CLOUD_PROVIDER);
        reservation.setUpdated(DateUtils.nowUTC());
        reservation.setCloudReservationId(CLOUD_ID);
        client.describeFailure = ec2Error(HTTP_BAD_REQUEST, NOT_FOUND_ERROR_CODE);

        assertThatThrownBy(() -> service.describe(reservation))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(CLOUD_ID);
    }

    @Test
    public void shouldNotWaitForAReservationAwsHasAlreadyShown() {
        final CapacityReservation reservation = reservation();
        reservation.setStatus(CapacityReservationStatus.SCHEDULED);
        reservation.setUpdated(DateUtils.nowUTC());
        reservation.setCloudReservationId(CLOUD_ID);
        client.describeFailure = ec2Error(HTTP_BAD_REQUEST, NOT_FOUND_ERROR_CODE);

        assertThat(service.describe(reservation).getState()).isEqualTo(CloudCapacityReservationState.FAILED);
    }

    @Test
    public void shouldFailWhenAwsRefusesTheReservationIdAsNotFound() {
        final CapacityReservation reservation = pastTheGrace(reservation());
        reservation.setCloudReservationId(CLOUD_ID);
        client.describeFailure = ec2Error(HTTP_BAD_REQUEST, NOT_FOUND_ERROR_CODE);

        final CloudCapacityReservation result = service.describe(reservation);

        assertThat(result.getState()).isEqualTo(CloudCapacityReservationState.FAILED);
        assertThat(result.getStateReason()).contains(CLOUD_ID);
    }

    @Test
    public void shouldLeaveAnyOtherDescribeRefusalAsItIs() {
        final CapacityReservation reservation = reservation();
        reservation.setCloudReservationId(CLOUD_ID);
        client.describeFailure = ec2Error(HTTP_BAD_REQUEST, "UnauthorizedOperation");

        assertThatThrownBy(() -> service.describe(reservation)).isInstanceOf(Ec2Exception.class);
    }

    @Test
    public void shouldFindAnExistingReservationByItsClientTokenTag() {
        client.described = Collections.singletonList(awsReservation(AWS_SCHEDULED, ZONE));

        final Optional<CloudCapacityReservation> found = service.findByClientToken(reservation(), TOKEN);

        assertThat(found.isPresent()).isTrue();
        final Filter filter = client.describeRequest.filters().get(0);
        assertThat(filter.name()).isEqualTo("tag:" + AwsCapacityReservationService.CLIENT_TOKEN_TAG);
        assertThat(filter.values()).containsExactly(TOKEN);
    }

    @Test
    public void shouldReturnEmptyWhenNoReservationCarriesTheToken() {
        client.described = Collections.emptyList();

        assertThat(service.findByClientToken(reservation(), TOKEN).isPresent()).isFalse();
    }

    @Test
    public void shouldCancelPlainlyWhenNothingIsCommitted() {
        final CapacityReservation reservation = reservation();
        reservation.setCloudReservationId(CLOUD_ID);
        client.described = Collections.singletonList(awsReservation(AWS_ACTIVE, ZONE));

        service.cancel(reservation);

        assertThat(client.cancelRequest.capacityReservationId()).isEqualTo(CLOUD_ID);
        assertThat(client.cancelRequest.quoteId()).isNull();
        assertThat(client.quoteRequest).isNull();
    }

    @Test
    public void shouldCancelAScheduledReservationThroughAQuote() {
        final CapacityReservation reservation = reservation();
        reservation.setCloudReservationId(CLOUD_ID);
        client.described = Collections.singletonList(awsReservation(AWS_SCHEDULED, ZONE));

        service.cancel(reservation);

        assertThat(client.quoteRequest.capacityReservationId()).isEqualTo(CLOUD_ID);
        assertThat(client.cancelRequest.quoteId()).isEqualTo(QUOTE_ID);
        assertThat(client.cancelRequest.applyCancellationCharges())
                .isEqualTo(ApplyCancellationCharges.COMMITMENT_WIND_DOWN);
    }

    @Test
    public void shouldCancelACommittedActiveReservationThroughAQuote() {
        final CapacityReservation reservation = reservation();
        reservation.setCloudReservationId(CLOUD_ID);
        client.described = Collections.singletonList(
                committedActiveReservation(Instant.now().plus(Duration.ofDays(3))));

        service.cancel(reservation);

        assertThat(client.cancelRequest.applyCancellationCharges())
                .isEqualTo(ApplyCancellationCharges.COMMITMENT_WIND_DOWN);
    }

    @Test
    public void shouldNotPayToCancelOnceTheCommitmentHasElapsed() {
        final CapacityReservation reservation = reservation();
        reservation.setCloudReservationId(CLOUD_ID);
        client.described = Collections.singletonList(
                committedActiveReservation(Instant.now().minus(Duration.ofDays(1))));

        service.cancel(reservation);

        assertThat(client.cancelRequest.quoteId()).isNull();
        assertThat(client.quoteRequest).isNull();
    }

    @Test
    public void shouldStillCancelAReservationTheDescribeDidNotShow() {
        final CapacityReservation reservation = reservation();
        reservation.setCloudReservationId(CLOUD_ID);
        client.described = Collections.emptyList();

        service.cancel(reservation);

        assertThat(client.cancelRequest.capacityReservationId()).isEqualTo(CLOUD_ID);
        assertThat(client.cancelRequest.quoteId()).isNull();
    }

    @Test
    public void shouldTreatAReservationAwsCannotCancelAsUnknownAsNothingToCancel() {
        final CapacityReservation reservation = reservation();
        reservation.setCloudReservationId(CLOUD_ID);
        client.describeFailure = ec2Error(HTTP_BAD_REQUEST, NOT_FOUND_ERROR_CODE);
        client.cancelFailure = ec2Error(HTTP_BAD_REQUEST, NOT_FOUND_ERROR_CODE);

        service.cancel(reservation);

        assertThat(client.cancelRequest.capacityReservationId()).isEqualTo(CLOUD_ID);
    }

    @Test
    public void shouldLeaveAnyOtherRefusalToCancelAnUnseenReservationAsItIs() {
        final CapacityReservation reservation = reservation();
        reservation.setCloudReservationId(CLOUD_ID);
        client.described = Collections.emptyList();
        client.cancelFailure = ec2Error(HTTP_BAD_REQUEST, "IncorrectState");

        assertThatThrownBy(() -> service.cancel(reservation)).isInstanceOf(Ec2Exception.class);
    }

    @Test
    public void shouldTreatAnIdAwsCannotParseAsNothingToCancel() {
        final CapacityReservation reservation = reservation();
        reservation.setCloudReservationId(CLOUD_ID);
        client.describeFailure = ec2Error(HTTP_BAD_REQUEST, MALFORMED_ERROR_CODE);
        client.cancelFailure = ec2Error(HTTP_BAD_REQUEST, MALFORMED_ERROR_CODE);

        service.cancel(reservation);

        assertThat(client.cancelRequest.capacityReservationId()).isEqualTo(CLOUD_ID);
    }

    @Test
    public void shouldOfferOnlyConfiguredZonesThatOfferTheInstanceType() {
        client.offeredZones = Arrays.asList("eu-central-1c", ZONE, "eu-central-1a");
        when(preferenceManager.getPreference(SystemPreferences.CLUSTER_NETWORKS_CONFIG))
                .thenReturn(networksConfig(networks("eu-central-1a", ZONE, "eu-central-1d")));

        assertThat(service.candidateZones(reservation())).containsExactly("eu-central-1a", ZONE);
        assertThat(client.offeringsRequest.locationTypeAsString()).isEqualTo("availability-zone");
        assertThat(client.offeringsRequest.filters().get(0).values()).containsExactly(reservation().getInstanceType());
    }

    @Test
    public void shouldOfferEveryZoneThatOffersTheInstanceTypeWhenNoNetworksAreConfigured() {
        client.offeredZones = Arrays.asList("eu-central-1c", ZONE);
        when(preferenceManager.getPreference(SystemPreferences.CLUSTER_NETWORKS_CONFIG)).thenReturn(null);

        assertThat(service.candidateZones(reservation())).containsExactly(ZONE, "eu-central-1c");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldTargetTheReservationInRunInstancesTerms() {
        final CapacityReservation reservation = reservation();
        reservation.setCloudReservationId(CLOUD_ID);

        final Map<String, Object> specification = service.launchSpecification(reservation);

        assertThat(specification).containsOnlyKeys("CapacityReservationSpecification");
        final Map<String, Object> target =
                (Map<String, Object>) specification.get("CapacityReservationSpecification");
        assertThat((Map<String, Object>) target.get("CapacityReservationTarget"))
                .containsEntry("CapacityReservationId", CLOUD_ID);
    }

    @Test
    public void shouldRequireFiveDaysOfLead() {
        assertThat(service.getMinimumLead()).isEqualTo(Duration.ofDays(5));
    }

    @Test
    public void shouldTreatAnAlreadyReleasedReservationAsCancelled() {
        final CapacityReservation reservation = reservation();
        reservation.setCloudReservationId(CLOUD_ID);
        for (final String released : Arrays.asList("cancelled", "cancelling", "expired", "failed")) {
            client.described = Collections.singletonList(awsReservation(released, ZONE));

            service.cancel(reservation);

            assertThat(client.cancelRequest).as(released).isNull();
            assertThat(client.quoteRequest).as(released).isNull();
        }
    }

    @Test
    public void shouldKeepAPaymentFailureDistinctFromARefusedConfiguration() {
        assertThat(AwsCapacityReservationService.toState("payment-failed"))
                .isEqualTo(CloudCapacityReservationState.PAYMENT_FAILED);
        assertThat(AwsCapacityReservationService.toState("failed"))
                .isEqualTo(CloudCapacityReservationState.FAILED);
    }

    @Test
    public void shouldOnlySlideOnAStateAwsWillNotDeliver() {
        assertThat(AwsCapacityReservationService.toState("unsupported"))
                .isEqualTo(CloudCapacityReservationState.UNSUPPORTED);
    }

    @Test
    public void shouldPollRatherThanSlideOnStatesThatMayStillBeDelivered() {
        assertThat(AwsCapacityReservationService.toState("unavailable"))
                .isEqualTo(CloudCapacityReservationState.PENDING);
    }

    @Test
    public void shouldKeepLatenessDistinctFromPendingAndRefusal() {
        assertThat(AwsCapacityReservationService.toState("delayed"))
                .isEqualTo(CloudCapacityReservationState.DELAYED);
    }

    @Test
    public void shouldMapTheStatesAwsDocuments() {
        assertThat(AwsCapacityReservationService.toState(AWS_ACTIVE))
                .isEqualTo(CloudCapacityReservationState.ACTIVE);
        assertThat(AwsCapacityReservationService.toState(AWS_SCHEDULED))
                .isEqualTo(CloudCapacityReservationState.SCHEDULED);
        assertThat(AwsCapacityReservationService.toState("assessing"))
                .isEqualTo(CloudCapacityReservationState.PENDING);
        assertThat(AwsCapacityReservationService.toState("pending"))
                .isEqualTo(CloudCapacityReservationState.PENDING);
        assertThat(AwsCapacityReservationService.toState("failed"))
                .isEqualTo(CloudCapacityReservationState.FAILED);
        assertThat(AwsCapacityReservationService.toState("payment-failed"))
                .isEqualTo(CloudCapacityReservationState.PAYMENT_FAILED);
        assertThat(AwsCapacityReservationService.toState("cancelled"))
                .isEqualTo(CloudCapacityReservationState.CANCELLED);
        assertThat(AwsCapacityReservationService.toState("cancelling"))
                .isEqualTo(CloudCapacityReservationState.CANCELLED);
        assertThat(AwsCapacityReservationService.toState("expired"))
                .isEqualTo(CloudCapacityReservationState.EXPIRED);
    }

    @Test
    public void shouldTreatAnUnknownStateAsPending() {
        assertThat(AwsCapacityReservationService.toState("something-new"))
                .isEqualTo(CloudCapacityReservationState.PENDING);
        assertThat(AwsCapacityReservationService.toState(null))
                .isEqualTo(CloudCapacityReservationState.PENDING);
    }

    @Test
    public void shouldReadBackTheCommitmentAwsGranted() {
        client.described = Collections.singletonList(awsReservation(AWS_SCHEDULED, ZONE).toBuilder()
                .commitmentInfo(CapacityReservationCommitmentInfo.builder()
                        .commitmentDuration(GRANTED_COMMITMENT_DURATION)
                        .build())
                .build());

        assertThat(service.describe(reservation()).getGrantedCommitmentDuration())
                .isEqualTo(GRANTED_COMMITMENT_DURATION);
    }

    @Test
    public void shouldLeaveTheGrantedCommitmentUnsetWhenAwsHasNotSaid() {
        client.described = Collections.singletonList(awsReservation(AWS_SCHEDULED, ZONE));

        assertThat(service.describe(reservation()).getGrantedCommitmentDuration()).isNull();
    }

    @Test
    public void shouldOnlyExplainStatesThatNeedExplaining() {
        client.described = Collections.singletonList(awsReservation(AWS_ACTIVE, ZONE));
        assertThat(service.describe(reservation()).getStateReason()).isNull();

        client.described = Collections.singletonList(awsReservation("unsupported", ZONE));
        assertThat(service.describe(reservation()).getStateReason()).contains("unsupported");
    }

    private static CapacityReservation pastTheGrace(final CapacityReservation reservation) {
        reservation.setUpdated(DateUtils.nowUTC()
                .minus(AwsCapacityReservationService.UNKNOWN_RESERVATION_GRACE_PERIOD)
                .minusMinutes(1));
        return reservation;
    }

    private CapacityReservation reservation() {
        final CapacityReservation reservation = CapacityReservationCreatorUtils.getReservation(1L);
        reservation.setId(RESERVATION_ID);
        reservation.setRegionId(REGION_ID);
        reservation.setInstanceCount(INSTANCE_COUNT);
        reservation.setCommitmentDuration(COMMITMENT_DURATION);
        reservation.setClientToken(TOKEN);
        reservation.setStartDate(DateUtils.nowUTC().plusDays(LEAD_DAYS).withNano(0));
        reservation.setEndDate(reservation.getStartDate().plusSeconds(COMMITMENT_DURATION));
        reservation.setAvailabilityZone(null);
        return reservation;
    }

    private software.amazon.awssdk.services.ec2.model.CapacityReservation committedActiveReservation(
            final Instant commitmentEnd) {
        return awsReservation(AWS_ACTIVE, ZONE).toBuilder()
                .commitmentInfo(CapacityReservationCommitmentInfo.builder()
                        .commitmentEndDate(commitmentEnd)
                        .build())
                .build();
    }

    private software.amazon.awssdk.services.ec2.model.CapacityReservation awsReservation(final String state,
                                                                                         final String zone) {
        final LocalDateTime start = DateUtils.nowUTC().plusDays(LEAD_DAYS).withNano(0);
        return software.amazon.awssdk.services.ec2.model.CapacityReservation.builder()
                .capacityReservationId(CLOUD_ID)
                .availabilityZone(zone)
                .state(state)
                .startDate(start.toInstant(ZoneOffset.UTC))
                .endDate(start.plusSeconds(COMMITMENT_DURATION).toInstant(ZoneOffset.UTC))
                .tags(Tag.builder().key(AwsCapacityReservationService.CLIENT_TOKEN_TAG).value(TOKEN).build())
                .build();
    }

    private static CloudRegionsConfiguration networksConfig(final Map<String, String> networks) {
        final NetworkConfiguration region = new NetworkConfiguration();
        region.setName(REGION_CODE);
        region.setAllowedNetworks(networks);
        final CloudRegionsConfiguration configuration = new CloudRegionsConfiguration();
        configuration.setRegions(Collections.singletonList(region));
        return configuration;
    }

    private static Map<String, String> networks(final String... zones) {
        final Map<String, String> networks = new HashMap<>();
        for (final String zone : zones) {
            networks.put(zone, "subnet-" + zone);
        }
        return networks;
    }

    private static Ec2Exception ec2Error(final int statusCode, final String errorCode) {
        return (Ec2Exception) Ec2Exception.builder()
                .statusCode(statusCode)
                .awsErrorDetails(AwsErrorDetails.builder().errorCode(errorCode).errorMessage(errorCode).build())
                .build();
    }

    private static final class FakeEc2Client implements Ec2Client {

        private CreateCapacityReservationRequest createRequest;
        private DescribeCapacityReservationsRequest describeRequest;
        private CancelCapacityReservationRequest cancelRequest;
        private CreateCapacityReservationCancellationQuoteRequest quoteRequest;
        private software.amazon.awssdk.services.ec2.model.CapacityReservation created;
        private List<software.amazon.awssdk.services.ec2.model.CapacityReservation> described =
                Collections.emptyList();
        private boolean closed;
        private List<String> offeredZones = Collections.emptyList();
        private DescribeInstanceTypeOfferingsRequest offeringsRequest;
        private RuntimeException createFailure;
        private RuntimeException describeFailure;
        private RuntimeException cancelFailure;

        @Override
        public String serviceName() {
            return Ec2Client.SERVICE_NAME;
        }

        @Override
        public void close() {
            closed = true;
        }

        @Override
        public CreateCapacityReservationResponse createCapacityReservation(
                final CreateCapacityReservationRequest request) {
            this.createRequest = request;
            if (createFailure != null) {
                throw createFailure;
            }
            return CreateCapacityReservationResponse.builder().capacityReservation(created).build();
        }

        @Override
        public DescribeInstanceTypeOfferingsResponse describeInstanceTypeOfferings(
                final DescribeInstanceTypeOfferingsRequest request) {
            this.offeringsRequest = request;
            return DescribeInstanceTypeOfferingsResponse.builder()
                    .instanceTypeOfferings(offeredZones.stream()
                            .map(zone -> InstanceTypeOffering.builder().location(zone).build())
                            .collect(Collectors.toList()))
                    .build();
        }

        @Override
        public DescribeCapacityReservationsResponse describeCapacityReservations(
                final DescribeCapacityReservationsRequest request) {
            this.describeRequest = request;
            if (describeFailure != null) {
                throw describeFailure;
            }
            return DescribeCapacityReservationsResponse.builder().capacityReservations(described).build();
        }

        @Override
        public CancelCapacityReservationResponse cancelCapacityReservation(
                final CancelCapacityReservationRequest request) {
            this.cancelRequest = request;
            if (cancelFailure != null) {
                throw cancelFailure;
            }
            return CancelCapacityReservationResponse.builder().build();
        }

        @Override
        public CreateCapacityReservationCancellationQuoteResponse createCapacityReservationCancellationQuote(
                final CreateCapacityReservationCancellationQuoteRequest request) {
            this.quoteRequest = request;
            return CreateCapacityReservationCancellationQuoteResponse.builder()
                    .capacityReservationCancellationQuote(CapacityReservationCancellationQuote.builder()
                            .capacityReservationCancellationQuoteId(QUOTE_ID)
                            .capacityReservationId(request.capacityReservationId())
                            .build())
                    .build();
        }
    }
}
