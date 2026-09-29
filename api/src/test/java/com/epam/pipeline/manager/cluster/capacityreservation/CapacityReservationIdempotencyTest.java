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
import com.epam.pipeline.entity.cluster.capacityreservation.CloudCapacityReservation;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.manager.cluster.capacityreservation.cloud.CapacityReservationCloudService;
import com.epam.pipeline.test.creator.cluster.capacityreservation.CapacityReservationCreatorUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
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
@RunWith(MockitoJUnitRunner.class)
public class CapacityReservationIdempotencyTest {

    private static final Long RESERVATION_ID = 42L;
    private static final String EXISTING_CLOUD_ID = "cr-already-bought";
    private static final String FIRST_ATTEMPT_TOKEN = "cp-cr-42-0";
    private static final String SECOND_ATTEMPT_TOKEN = "cp-cr-42-1";

    @Mock
    private CapacityReservationCloudService awsService;

    private CapacityReservationCloudFacade facade;

    @Before
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

        final CloudCapacityReservation result = facade.submit(reservation);

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

        final CloudCapacityReservation result = facade.submit(reservation);

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

        final CloudCapacityReservation result = facade.submit(reservation);

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

        final CloudCapacityReservation result = facade.submit(reservation);

        assertThat(result.getCloudReservationId()).isEqualTo("cr-new-date");
        verify(awsService).findByClientToken(reservation, SECOND_ATTEMPT_TOKEN);
        assertThat(reservation.getClientToken()).isEqualTo(SECOND_ATTEMPT_TOKEN);
    }

    @Test
    public void shouldPersistTheTokenBeforeSubmitting() {
        final CapacityReservation reservation = reservation();
        reservation.setClientToken(null);
        when(awsService.findByClientToken(any(), anyString())).thenReturn(Optional.empty());
        when(awsService.createFutureDated(any()))
                .thenReturn(CloudCapacityReservation.builder().build());

        facade.submit(reservation);

        assertThat(reservation.getClientToken()).isEqualTo(FIRST_ATTEMPT_TOKEN);
    }

    @Test
    public void shouldNotAskTheProviderToCancelSomethingItNeverCreated() {
        final CapacityReservation reservation = reservation();
        reservation.setCloudReservationId(null);

        facade.cancel(reservation);

        verify(awsService, never()).cancel(any());
    }

    @Test
    public void shouldRejectAProviderWithNoImplementation() {
        final CapacityReservation reservation = reservation();
        reservation.setCloudProvider(CloudProvider.AZURE);

        assertThatThrownBy(() -> facade.submit(reservation))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private CapacityReservation reservation() {
        final CapacityReservation reservation = CapacityReservationCreatorUtils.getReservation(1L);
        reservation.setId(RESERVATION_ID);
        reservation.setAttempt(0);
        return reservation;
    }
}
