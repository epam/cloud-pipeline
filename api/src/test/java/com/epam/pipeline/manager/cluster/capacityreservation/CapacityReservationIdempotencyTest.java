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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Never buy the same reservation twice.
 *
 * <p>The dangerous case is not a failed submission but an uncertain one: the provider accepted the request and we
 * never saw the reply - a timeout, a pod restart mid-call. Retrying blindly then buys a second reservation that
 * nothing in the platform knows about and nothing will ever cancel. So the token is deterministic, and a
 * reservation with a token but no provider id is looked up before anything is bought.
 */
@RunWith(MockitoJUnitRunner.class)
public class CapacityReservationIdempotencyTest {

    private static final Long RESERVATION_ID = 42L;
    private static final String EXISTING_CLOUD_ID = "cr-already-bought";

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
                .isEqualTo("cp-cr-42-0");
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
        reservation.setClientToken("cp-cr-42-0");
        reservation.setCloudReservationId(null);
        when(awsService.findByClientToken(any(), anyString())).thenReturn(Optional.of(
                CloudCapacityReservation.builder().cloudReservationId(EXISTING_CLOUD_ID).build()));

        final CloudCapacityReservation result = facade.submit(reservation);

        assertThat(result.getCloudReservationId()).isEqualTo(EXISTING_CLOUD_ID);
        verify(awsService, never()).createFutureDated(any());
    }

    @Test
    public void shouldCreateWhenNothingWasEverSubmitted() {
        final CapacityReservation reservation = reservation();
        reservation.setClientToken(null);
        when(awsService.createFutureDated(any()))
                .thenReturn(CloudCapacityReservation.builder().cloudReservationId("cr-new").build());

        final CloudCapacityReservation result = facade.submit(reservation);

        assertThat(result.getCloudReservationId()).isEqualTo("cr-new");
        // No token was persisted, so there is nothing a previous attempt could have created.
        verify(awsService, never()).findByClientToken(any(), anyString());
    }

    @Test
    public void shouldPersistTheTokenBeforeSubmitting() {
        final CapacityReservation reservation = reservation();
        reservation.setClientToken(null);
        when(awsService.createFutureDated(any()))
                .thenReturn(CloudCapacityReservation.builder().build());

        facade.submit(reservation);

        assertThat(reservation.getClientToken()).isEqualTo("cp-cr-42-0");
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
