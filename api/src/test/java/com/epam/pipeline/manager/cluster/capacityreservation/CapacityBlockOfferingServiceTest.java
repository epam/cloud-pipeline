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

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.controller.vo.cluster.capacityreservation.CapacityBlockOfferingRequest;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityBlockInstanceTypeInfo;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityBlockOffering;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityBlockResourceType;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.region.CloudRegionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class CapacityBlockOfferingServiceTest {

    private static final Long REGION_ID = 1L;
    private static final String INSTANCE_TYPE = "p5.48xlarge";
    private static final int INSTANCE_COUNT = 8;
    private static final int DURATION_HOURS = 336;

    @Mock
    private CloudRegionManager regionManager;

    @Mock
    private CapacityReservationCloudFacade cloudFacade;

    private final AwsRegion region = new AwsRegion();

    private CapacityBlockOfferingService service;

    @BeforeEach
    public void setUp() {
        region.setId(REGION_ID);
        when(regionManager.load(REGION_ID)).thenReturn(region);
        service = new CapacityBlockOfferingService(regionManager, cloudFacade, mock(MessageHelper.class));
    }

    @Test
    public void shouldDelegateToTheFacadeWithTheResolvedRegion() {
        final CapacityBlockOfferingRequest request = validRequest();
        final List<CapacityBlockOffering> offerings = Collections.singletonList(
                CapacityBlockOffering.builder().offeringId("cbr-1").build());
        when(cloudFacade.listCapacityBlockOfferings(region, request)).thenReturn(offerings);

        assertThat(service.listOfferings(request)).isEqualTo(offerings);
    }

    @Test
    public void shouldRejectAMissingRegion() {
        final CapacityBlockOfferingRequest request = validRequest().toBuilder().regionId(null).build();

        assertThatThrownBy(() -> service.listOfferings(request)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(cloudFacade);
    }

    @Test
    public void shouldRejectAMissingInstanceType() {
        final CapacityBlockOfferingRequest request = validRequest().toBuilder().instanceType(null).build();

        assertThatThrownBy(() -> service.listOfferings(request)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(cloudFacade);
    }

    @Test
    public void shouldRejectAnInstanceCountBelowOne() {
        final CapacityBlockOfferingRequest request = validRequest().toBuilder().instanceCount(0).build();

        assertThatThrownBy(() -> service.listOfferings(request)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(cloudFacade);
    }

    @Test
    public void shouldRejectAMissingDuration() {
        final CapacityBlockOfferingRequest request = validRequest().toBuilder().capacityDurationHours(null).build();

        assertThatThrownBy(() -> service.listOfferings(request)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(cloudFacade);
    }

    @Test
    public void shouldRejectAMissingStartDate() {
        final CapacityBlockOfferingRequest request = validRequest().toBuilder().startDateRange(null).build();

        assertThatThrownBy(() -> service.listOfferings(request)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(cloudFacade);
    }

    @Test
    public void shouldRejectAnUltraserverResourceType() {
        final CapacityBlockOfferingRequest request = validRequest().toBuilder()
                .resourceType(CapacityBlockResourceType.ULTRASERVER)
                .build();

        assertThatThrownBy(() -> service.listOfferings(request)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(cloudFacade);
    }

    @Test
    public void shouldAcceptAnExplicitInstanceResourceType() {
        final CapacityBlockOfferingRequest request = validRequest().toBuilder()
                .resourceType(CapacityBlockResourceType.INSTANCE)
                .build();
        when(cloudFacade.listCapacityBlockOfferings(region, request)).thenReturn(Collections.emptyList());

        assertThat(service.listOfferings(request)).isEmpty();
    }

    @Test
    public void shouldListInstanceTypesForTheResolvedRegion() {
        final List<CapacityBlockInstanceTypeInfo> instanceTypes = Collections.singletonList(
                CapacityBlockInstanceTypeInfo.builder().instanceType(INSTANCE_TYPE).build());
        when(cloudFacade.listCapacityBlockInstanceTypes(region)).thenReturn(instanceTypes);

        assertThat(service.listInstanceTypes(REGION_ID)).isEqualTo(instanceTypes);
    }

    @Test
    public void shouldRejectAMissingRegionWhenListingInstanceTypes() {
        assertThatThrownBy(() -> service.listInstanceTypes(null)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(cloudFacade);
    }

    private static CapacityBlockOfferingRequest validRequest() {
        return CapacityBlockOfferingRequest.builder()
                .regionId(REGION_ID)
                .instanceType(INSTANCE_TYPE)
                .instanceCount(INSTANCE_COUNT)
                .capacityDurationHours(DURATION_HOURS)
                .startDateRange(DateUtils.nowUTC())
                .build();
    }
}
