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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class CapacityReservationCloudFacadeTest {

    @Mock
    private CapacityReservationCloudService awsService;

    private CapacityReservationCloudFacade facade;

    @Before
    public void setUp() {
        when(awsService.getProvider()).thenReturn(CloudProvider.AWS);
        facade = new CapacityReservationCloudFacade(Collections.singletonList(awsService));
    }

    @Test
    public void shouldPurchaseACapacityBlockForACapacityBlockReservation() {
        final CapacityReservation reservation = CapacityReservationCreatorUtils.getCapacityBlockReservation(1L);
        final CloudCapacityReservation purchased = CloudCapacityReservation.builder().build();
        when(awsService.purchaseCapacityBlock(reservation)).thenReturn(purchased);

        final CloudCapacityReservation result = facade.create(reservation);

        assertThat(result).isEqualTo(purchased);
        assertThat(reservation.getClientToken()).isNotBlank();
        verify(awsService, never()).createFutureDated(any());
    }

    @Test
    public void shouldCreateAFutureDatedReservationForAFutureDatedReservation() {
        final CapacityReservation reservation = CapacityReservationCreatorUtils.getReservation(1L);
        final CloudCapacityReservation created = CloudCapacityReservation.builder().build();
        when(awsService.createFutureDated(reservation)).thenReturn(created);

        final CloudCapacityReservation result = facade.create(reservation);

        assertThat(result).isEqualTo(created);
        assertThat(reservation.getClientToken()).isNotBlank();
        verify(awsService, never()).purchaseCapacityBlock(any());
    }

}
