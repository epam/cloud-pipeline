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

package com.epam.pipeline.manager.cluster.capacityreservation;

import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservation;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservationStatus;
import com.epam.pipeline.entity.cluster.capacityreservation.CloudCapacityReservation;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.cluster.capacityreservation.cloud.CapacityReservationCloudService;
import com.epam.pipeline.manager.cluster.capacityreservation.cloud.CapacityReservationSubmissionUncertainException;
import com.epam.pipeline.test.creator.cluster.capacityreservation.CapacityReservationCreatorUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Never buy the same reservation twice.
 *
 * <p>The dangerous case is not a failed submission but an uncertain one: the provider accepted the request and we
 * never saw the reply - a timeout, a pod restart mid-call. Retrying blindly then buys a second reservation that
 * nothing in the platform knows about and nothing will ever cancel. So the token is deterministic, and a
 * reservation with no provider id is looked up by the token of its current attempt before anything is bought.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class CapacityReservationIdempotencyTest {

    private static final Long RESERVATION_ID = 42L;
    private static final String EXISTING_CLOUD_ID = "cr-already-bought";
    private static final String FIRST_ATTEMPT_TOKEN = "cp-cr-42-0";
    private static final String SECOND_ATTEMPT_TOKEN = "cp-cr-42-1";
    private static final int PROVIDER_LEAD_DAYS = 5;

    @Mock
    private CapacityReservationCloudService awsService;

    private CapacityReservationCloudFacade facade;

    @BeforeEach
    public void setUp() {
        when(awsService.getProvider()).thenReturn(CloudProvider.AWS);
        facade = new CapacityReservationCloudFacade(Collections.singletonList(awsService));
    }

    /**
     * The token must not vary between two calls for the same attempt, or the provider cannot recognise the repeat.
     */
    @Test
    public void shouldDeriveADeterministicTokenFromIdAndAttempt() {
        final CapacityReservation reservation = reservation();

        assertThat(CapacityReservationCloudFacade.clientToken(reservation))
                .isEqualTo(CapacityReservationCloudFacade.clientToken(reservation))
                .isEqualTo(FIRST_ATTEMPT_TOKEN);
    }

    /**
     * A new date is a new request, not a retry of the old one - so it must not reuse the token, or the provider
     * would return the previous reservation for the previous date.
     */
    @Test
    public void shouldChangeTheTokenWhenTheAttemptChanges() {
        final CapacityReservation reservation = reservation();
        final String first = CapacityReservationCloudFacade.clientToken(reservation);
        reservation.setAttempt(reservation.getAttempt() + 1);

        assertThat(CapacityReservationCloudFacade.clientToken(reservation)).isNotEqualTo(first);
    }

    @Test
    public void shouldAdoptAnExistingReservationInsteadOfBuyingAnother() {
        final CapacityReservation reservation = reservation();
        // Submitted before, but the reply never arrived: a token was persisted and no provider id came back.
        reservation.setClientToken(FIRST_ATTEMPT_TOKEN);
        reservation.setCloudReservationId(null);
        when(awsService.findByClientToken(any(), eq(FIRST_ATTEMPT_TOKEN))).thenReturn(Optional.of(
                CloudCapacityReservation.builder().cloudReservationId(EXISTING_CLOUD_ID).build()));

        final CloudCapacityReservation result = submitted(reservation);

        assertThat(result.getCloudReservationId()).isEqualTo(EXISTING_CLOUD_ID);
        verify(awsService, never()).createFutureDated(any());
    }

    @Test
    public void shouldCreateWhenNothingWasEverSubmitted() {
        final CapacityReservation reservation = reservation();
        reservation.setClientToken(null);
        when(awsService.findByClientToken(any(), anyString())).thenReturn(Optional.empty());
        when(awsService.createFutureDated(any()))
                .thenReturn(CloudCapacityReservation.builder().cloudReservationId("cr-new").build());

        final CloudCapacityReservation result = submitted(reservation);

        assertThat(result.getCloudReservationId()).isEqualTo("cr-new");
        verify(awsService).findByClientToken(reservation, FIRST_ATTEMPT_TOKEN);
    }

    /**
     * A reply can be lost before the token was ever persisted - the token is only saved with the next status
     * change, so a restart mid-call leaves none. It is derived from the id and attempt, so the lookup still finds
     * what that call bought.
     */
    @Test
    public void shouldAdoptALostReplyEvenWhenNoTokenWasPersisted() {
        final CapacityReservation reservation = reservation();
        reservation.setClientToken(null);
        reservation.setCloudReservationId(null);
        when(awsService.findByClientToken(any(), eq(FIRST_ATTEMPT_TOKEN))).thenReturn(Optional.of(
                CloudCapacityReservation.builder().cloudReservationId(EXISTING_CLOUD_ID).build()));

        final CloudCapacityReservation result = submitted(reservation);

        assertThat(result.getCloudReservationId()).isEqualTo(EXISTING_CLOUD_ID);
        verify(awsService, never()).createFutureDated(any());
    }

    /**
     * After a slide the stored token still names the previous attempt, and the provider still holds the
     * reservation it refused for the previous date. Looking up by the stored token would adopt that refusal
     * again, so the new date would never be asked for - the request would slide on its own old answer until the
     * window ran out.
     */
    @Test
    public void shouldRequestTheNewDateAfterASlideInsteadOfAdoptingTheRefusedReservation() {
        final CapacityReservation reservation = reservation();
        // As left by a slide: the attempt moved on, the old provider id was cleared, the stored token was not.
        reservation.setAttempt(1);
        reservation.setClientToken(FIRST_ATTEMPT_TOKEN);
        reservation.setCloudReservationId(null);
        when(awsService.findByClientToken(any(), eq(FIRST_ATTEMPT_TOKEN))).thenReturn(Optional.of(
                CloudCapacityReservation.builder().cloudReservationId(EXISTING_CLOUD_ID).build()));
        when(awsService.findByClientToken(any(), eq(SECOND_ATTEMPT_TOKEN))).thenReturn(Optional.empty());
        when(awsService.createFutureDated(any()))
                .thenReturn(CloudCapacityReservation.builder().cloudReservationId("cr-new-date").build());

        final CloudCapacityReservation result = submitted(reservation);

        assertThat(result.getCloudReservationId()).isEqualTo("cr-new-date");
        verify(awsService).findByClientToken(reservation, SECOND_ATTEMPT_TOKEN);
        assertThat(reservation.getClientToken()).isEqualTo(SECOND_ATTEMPT_TOKEN);
    }

    /**
     * A lookup that could not ask has not found "nothing". Buying anyway would be the blind retry the lookup exists
     * to prevent, so the uncertainty goes back to the caller instead.
     */
    @Test
    public void shouldNotBuyWhenTheLookupCouldNotAsk() {
        final CapacityReservation reservation = reservation();
        reservation.setCloudReservationId(null);
        when(awsService.findByClientToken(any(), anyString())).thenThrow(
                new CapacityReservationSubmissionUncertainException("timed out", new IllegalStateException()));

        assertThatThrownBy(() -> submitted(reservation))
                .isInstanceOf(CapacityReservationSubmissionUncertainException.class);
        verify(awsService, never()).createFutureDated(any());
    }

    @Test
    public void shouldPersistTheTokenBeforeSubmitting() {
        final CapacityReservation reservation = reservation();
        reservation.setClientToken(null);
        when(awsService.findByClientToken(any(), anyString())).thenReturn(Optional.empty());
        when(awsService.createFutureDated(any()))
                .thenReturn(CloudCapacityReservation.builder().build());

        submitted(reservation);

        assertThat(reservation.getClientToken()).isEqualTo(FIRST_ATTEMPT_TOKEN);
    }

    @Test
    public void shouldAskTheProviderHowALaunchTargetsTheReservation() {
        final CapacityReservation reservation = reservation();
        when(awsService.launchSpecification(reservation)).thenReturn(Collections.singletonMap("key", "value"));

        assertThat(facade.launchSpecification(reservation)).containsEntry("key", "value");
    }

    @Test
    public void shouldAskTheProviderWhichZonesCanTakeTheReservation() {
        final CapacityReservation reservation = reservation();
        when(awsService.candidateZones(reservation)).thenReturn(Collections.singletonList("us-east-1c"));

        assertThat(facade.candidateZones(reservation)).containsExactly("us-east-1c");
    }

    @Test
    public void shouldReportTheEarliestStartTheProviderAcceptsNow() {
        when(awsService.getMinimumLead()).thenReturn(Duration.ofDays(PROVIDER_LEAD_DAYS));
        final LocalDateTime before = DateUtils.nowUTC();

        final LocalDateTime earliest = facade.earliestAcceptedStart(reservation());

        assertThat(earliest.isBefore(before.plusDays(PROVIDER_LEAD_DAYS))).isFalse();
        assertThat(earliest.isAfter(DateUtils.nowUTC().plusDays(PROVIDER_LEAD_DAYS))).isFalse();
    }

    @Test
    public void shouldNotAskTheProviderToCancelSomethingItNeverCreated() {
        final CapacityReservation reservation = reservation();
        reservation.setStatus(CapacityReservationStatus.REQUIRED_APPROVE);
        reservation.setCloudReservationId(null);

        facade.cancel(reservation);

        verify(awsService, never()).cancel(any());
        // Never submitted, so there is nothing a lost reply could have bought.
        verify(awsService, never()).findByClientToken(any(), anyString());
    }

    /**
     * An approved reservation whose submission is being retried may already exist at the provider. Cancelling it
     * only here would leave that one billing, with nothing left that could ever find it.
     */
    @Test
    public void shouldReleaseWhatALostSubmissionBoughtWhenCancelled() {
        final CapacityReservation reservation = reservation();
        reservation.setStatus(CapacityReservationStatus.APPROVED);
        reservation.setCloudReservationId(null);
        when(awsService.findByClientToken(reservation, FIRST_ATTEMPT_TOKEN)).thenReturn(Optional.of(
                CloudCapacityReservation.builder().cloudReservationId(EXISTING_CLOUD_ID).build()));

        facade.cancel(reservation);

        verify(awsService).cancel(reservation);
        assertThat(reservation.getCloudReservationId()).isEqualTo(EXISTING_CLOUD_ID);
    }

    @Test
    public void shouldCancelNothingWhenAnApprovedReservationWasNeverBought() {
        final CapacityReservation reservation = reservation();
        reservation.setStatus(CapacityReservationStatus.APPROVED);
        reservation.setCloudReservationId(null);
        when(awsService.findByClientToken(any(), anyString())).thenReturn(Optional.empty());

        facade.cancel(reservation);

        verify(awsService, never()).cancel(any());
    }

    @Test
    public void shouldRejectAProviderWithNoImplementation() {
        final CapacityReservation reservation = reservation();
        reservation.setCloudProvider(CloudProvider.AZURE);

        assertThatThrownBy(() -> submitted(reservation))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * A reservation that already has its provider id has nothing a lost reply could have bought.
     */
    @Test
    public void shouldNotLookForALostSubmissionWhenTheProviderIdIsKnown() {
        final CapacityReservation reservation = reservation();
        reservation.setCloudReservationId(EXISTING_CLOUD_ID);

        assertThat(facade.findSubmitted(reservation).isPresent()).isFalse();
        verify(awsService, never()).findByClientToken(any(), anyString());
    }

    /**
     * What the monitor does with the facade: adopts what the lookup finds, and creates only when it finds nothing.
     */
    private CloudCapacityReservation submitted(final CapacityReservation reservation) {
        return facade.findSubmitted(reservation).orElseGet(() -> facade.create(reservation));
    }

    private CapacityReservation reservation() {
        final CapacityReservation reservation = CapacityReservationCreatorUtils.getReservation(1L);
        reservation.setId(RESERVATION_ID);
        reservation.setAttempt(0);
        return reservation;
    }
}
