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

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class CapacityReservationIdempotencyTest {

    private static final Long RESERVATION_ID = 42L;
    private static final String EXISTING_CLOUD_ID = "cr-already-bought";
    private static final String FIRST_ATTEMPT_TOKEN = "cp-capacity-reservation-42-0";
    private static final String SECOND_ATTEMPT_TOKEN = "cp-capacity-reservation-42-1";
    private static final int PROVIDER_LEAD_DAYS = 5;

    @Mock
    private CapacityReservationCloudService awsService;

    private CapacityReservationCloudFacade facade;

    @BeforeEach
    public void setUp() {
        when(awsService.getProvider()).thenReturn(CloudProvider.AWS);
        facade = new CapacityReservationCloudFacade(Collections.singletonList(awsService));
    }

    @Test
    public void shouldDeriveADeterministicTokenFromIdAndAttempt() {
        final CapacityReservation reservation = reservation();

        assertThat(CapacityReservationCloudFacade.clientToken(reservation))
                .isEqualTo(CapacityReservationCloudFacade.clientToken(reservation))
                .isEqualTo(FIRST_ATTEMPT_TOKEN);
    }

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

    @Test
    public void shouldRequestTheNewDateAfterASlideInsteadOfAdoptingTheRefusedReservation() {
        final CapacityReservation reservation = reservation();
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
        verify(awsService, never()).findByClientToken(any(), anyString());
    }

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

    @Test
    public void shouldNotLookForALostSubmissionWhenTheProviderIdIsKnown() {
        final CapacityReservation reservation = reservation();
        reservation.setCloudReservationId(EXISTING_CLOUD_ID);

        assertThat(facade.findSubmitted(reservation).isPresent()).isFalse();
        verify(awsService, never()).findByClientToken(any(), anyString());
    }

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
