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
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.region.CloudRegionManager;
import com.epam.pipeline.vo.CapacitySpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class CapacityBlockOfferingServiceTest {

    private static final Long REGION_ID = 1L;
    private static final String INSTANCE_TYPE = "p5.48xlarge";
    private static final String OTHER_INSTANCE_TYPE = "p5e.48xlarge";
    private static final int INSTANCE_COUNT = 8;
    private static final int DURATION_HOURS = 336;
    private static final int MAX_CANDIDATES = 5;

    @Mock
    private CloudRegionManager regionManager;

    @Mock
    private CapacityReservationCloudFacade cloudFacade;

    @Mock
    private PreferenceManager preferenceManager;

    private final AwsRegion region = new AwsRegion();

    private CapacityBlockOfferingService service;

    @BeforeEach
    public void setUp() {
        region.setId(REGION_ID);
        when(regionManager.load(REGION_ID)).thenReturn(region);
        when(preferenceManager.getPreference(SystemPreferences.CLUSTER_CAPACITY_BLOCK_OFFERING_CANDIDATES_MAX_COUNT))
                .thenReturn(MAX_CANDIDATES);
        service = new CapacityBlockOfferingService(regionManager, cloudFacade, mock(MessageHelper.class),
                preferenceManager);
    }

    @Test
    public void shouldDelegateToTheFacadeWithTheResolvedRegion() {
        final CapacityBlockOfferingRequest request = validRequest();
        final CapacitySpecification candidate = request.getCandidates().get(0);
        final List<CapacityBlockOffering> offerings = Collections.singletonList(
                CapacityBlockOffering.builder().offeringId("cbr-1").startDate(DateUtils.nowUTC()).build());
        when(cloudFacade.listCapacityBlockOfferings(region, request, candidate)).thenReturn(offerings);

        assertThat(service.listOfferings(request)).isEqualTo(offerings);
        verify(cloudFacade).listCapacityBlockOfferings(region, request, candidate);
    }

    @Test
    public void shouldSearchEveryCandidateAndSortTheResultByStartDateAscending() {
        final CapacitySpecification first = CapacitySpecification.builder()
                .instanceType(INSTANCE_TYPE).instanceCount(INSTANCE_COUNT).build();
        final CapacitySpecification second = CapacitySpecification.builder()
                .instanceType(OTHER_INSTANCE_TYPE).instanceCount(INSTANCE_COUNT).build();
        final CapacityBlockOfferingRequest request = validRequest().toBuilder()
                .candidates(Arrays.asList(first, second))
                .build();
        final CapacityBlockOffering later = CapacityBlockOffering.builder()
                .offeringId("cbr-later").instanceType(INSTANCE_TYPE)
                .startDate(DateUtils.nowUTC().plusDays(10)).build();
        final CapacityBlockOffering earlier = CapacityBlockOffering.builder()
                .offeringId("cbr-earlier").instanceType(OTHER_INSTANCE_TYPE)
                .startDate(DateUtils.nowUTC().plusDays(1)).build();
        when(cloudFacade.listCapacityBlockOfferings(region, request, first))
                .thenReturn(Collections.singletonList(later));
        when(cloudFacade.listCapacityBlockOfferings(region, request, second))
                .thenReturn(Collections.singletonList(earlier));

        assertThat(service.listOfferings(request)).containsExactly(earlier, later);
    }

    @Test
    public void shouldRejectAnEmptyCandidatesList() {
        final CapacityBlockOfferingRequest request = validRequest().toBuilder()
                .candidates(Collections.emptyList())
                .build();

        assertThatThrownBy(() -> service.listOfferings(request)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(cloudFacade);
    }

    @Test
    public void shouldRejectAMissingCandidatesList() {
        final CapacityBlockOfferingRequest request = validRequest().toBuilder()
                .candidates(null)
                .build();

        assertThatThrownBy(() -> service.listOfferings(request)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(cloudFacade);
    }

    @Test
    public void shouldRejectACandidateWithABlankInstanceType() {
        final CapacityBlockOfferingRequest request = validRequest().toBuilder()
                .candidates(Collections.singletonList(
                        CapacitySpecification.builder().instanceType(" ").instanceCount(INSTANCE_COUNT).build()))
                .build();

        assertThatThrownBy(() -> service.listOfferings(request)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(cloudFacade);
    }

    @Test
    public void shouldRejectACandidateWithAnInstanceCountBelowOne() {
        final CapacityBlockOfferingRequest request = validRequest().toBuilder()
                .candidates(Collections.singletonList(
                        CapacitySpecification.builder().instanceType(INSTANCE_TYPE).instanceCount(0).build()))
                .build();

        assertThatThrownBy(() -> service.listOfferings(request)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(cloudFacade);
    }

    @Test
    public void shouldRejectMoreCandidatesThanThePreferenceAllows() {
        final List<CapacitySpecification> tooMany = Arrays.asList(
                CapacitySpecification.builder().instanceType(INSTANCE_TYPE).instanceCount(1).build(),
                CapacitySpecification.builder().instanceType(INSTANCE_TYPE).instanceCount(2).build(),
                CapacitySpecification.builder().instanceType(INSTANCE_TYPE).instanceCount(3).build(),
                CapacitySpecification.builder().instanceType(INSTANCE_TYPE).instanceCount(4).build(),
                CapacitySpecification.builder().instanceType(INSTANCE_TYPE).instanceCount(5).build(),
                CapacitySpecification.builder().instanceType(INSTANCE_TYPE).instanceCount(6).build());
        final CapacityBlockOfferingRequest request = validRequest().toBuilder().candidates(tooMany).build();

        assertThatThrownBy(() -> service.listOfferings(request)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(cloudFacade);
    }

    @Test
    public void shouldMergeCandidatesEqualInInstanceTypeAndCount() {
        final CapacitySpecification first = CapacitySpecification.builder()
                .instanceType(INSTANCE_TYPE).instanceCount(INSTANCE_COUNT).build();
        final CapacitySpecification duplicate = CapacitySpecification.builder()
                .instanceType(INSTANCE_TYPE).instanceCount(INSTANCE_COUNT).build();
        final CapacityBlockOfferingRequest request = validRequest().toBuilder()
                .candidates(Arrays.asList(first, duplicate))
                .build();
        final CapacityBlockOffering offering = CapacityBlockOffering.builder()
                .offeringId("cbr-1").startDate(DateUtils.nowUTC()).build();
        when(cloudFacade.listCapacityBlockOfferings(region, request, first))
                .thenReturn(Collections.singletonList(offering));

        assertThat(service.listOfferings(request)).containsExactly(offering);
        verify(cloudFacade, times(1)).listCapacityBlockOfferings(region, request, first);
    }

    @Test
    public void shouldTreatCandidatesOfTheSameTypeButDifferentCountAsDistinct() {
        final CapacitySpecification fewer = CapacitySpecification.builder()
                .instanceType(INSTANCE_TYPE).instanceCount(INSTANCE_COUNT).build();
        final CapacitySpecification more = CapacitySpecification.builder()
                .instanceType(INSTANCE_TYPE).instanceCount(INSTANCE_COUNT + 1).build();
        final CapacityBlockOfferingRequest request = validRequest().toBuilder()
                .candidates(Arrays.asList(fewer, more))
                .build();
        final CapacityBlockOffering fewerOffering = CapacityBlockOffering.builder()
                .offeringId("cbr-fewer").startDate(DateUtils.nowUTC()).build();
        final CapacityBlockOffering moreOffering = CapacityBlockOffering.builder()
                .offeringId("cbr-more").startDate(DateUtils.nowUTC().plusDays(1)).build();
        when(cloudFacade.listCapacityBlockOfferings(region, request, fewer))
                .thenReturn(Collections.singletonList(fewerOffering));
        when(cloudFacade.listCapacityBlockOfferings(region, request, more))
                .thenReturn(Collections.singletonList(moreOffering));

        assertThat(service.listOfferings(request)).containsExactly(fewerOffering, moreOffering);
        verify(cloudFacade, times(1)).listCapacityBlockOfferings(region, request, fewer);
        verify(cloudFacade, times(1)).listCapacityBlockOfferings(region, request, more);
    }

    @Test
    public void shouldNotLimitCandidatesWhenThePreferenceIsDisabled() {
        when(preferenceManager.getPreference(SystemPreferences.CLUSTER_CAPACITY_BLOCK_OFFERING_CANDIDATES_MAX_COUNT))
                .thenReturn(-1);
        final List<CapacitySpecification> many = Arrays.asList(
                CapacitySpecification.builder().instanceType(INSTANCE_TYPE).instanceCount(1).build(),
                CapacitySpecification.builder().instanceType(INSTANCE_TYPE).instanceCount(2).build(),
                CapacitySpecification.builder().instanceType(INSTANCE_TYPE).instanceCount(3).build(),
                CapacitySpecification.builder().instanceType(INSTANCE_TYPE).instanceCount(4).build(),
                CapacitySpecification.builder().instanceType(INSTANCE_TYPE).instanceCount(5).build(),
                CapacitySpecification.builder().instanceType(INSTANCE_TYPE).instanceCount(6).build());
        final CapacityBlockOfferingRequest request = validRequest().toBuilder().candidates(many).build();
        many.forEach(candidate -> when(cloudFacade.listCapacityBlockOfferings(region, request, candidate))
                .thenReturn(Collections.emptyList()));

        assertThat(service.listOfferings(request)).isEmpty();
        many.forEach(candidate -> verify(cloudFacade, times(1))
                .listCapacityBlockOfferings(region, request, candidate));
    }

    @Test
    public void shouldPropagateAFacadeFailureForAnyCandidateAndReturnNoPartialResult() {
        final CapacitySpecification first = CapacitySpecification.builder()
                .instanceType(INSTANCE_TYPE).instanceCount(INSTANCE_COUNT).build();
        final CapacitySpecification second = CapacitySpecification.builder()
                .instanceType(OTHER_INSTANCE_TYPE).instanceCount(INSTANCE_COUNT).build();
        final CapacityBlockOfferingRequest request = validRequest().toBuilder()
                .candidates(Arrays.asList(first, second))
                .build();
        when(cloudFacade.listCapacityBlockOfferings(region, request, first))
                .thenReturn(Collections.singletonList(
                        CapacityBlockOffering.builder().offeringId("cbr-1").startDate(DateUtils.nowUTC()).build()));
        when(cloudFacade.listCapacityBlockOfferings(region, request, second))
                .thenThrow(new RuntimeException("AWS is unavailable"));

        assertThatThrownBy(() -> service.listOfferings(request)).isInstanceOf(RuntimeException.class);
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
        when(cloudFacade.listCapacityBlockOfferings(region, request, request.getCandidates().get(0)))
                .thenReturn(Collections.emptyList());

        assertThat(service.listOfferings(request)).isEmpty();
    }

    @Test
    public void shouldRejectAMissingRegion() {
        final CapacityBlockOfferingRequest request = validRequest().toBuilder().regionId(null).build();

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

    private static CapacitySpecification candidate() {
        return CapacitySpecification.builder()
                .instanceType(INSTANCE_TYPE)
                .instanceCount(INSTANCE_COUNT)
                .build();
    }

    private static CapacityBlockOfferingRequest validRequest() {
        return CapacityBlockOfferingRequest.builder()
                .regionId(REGION_ID)
                .candidates(Collections.singletonList(candidate()))
                .capacityDurationHours(DURATION_HOURS)
                .startDateRange(DateUtils.nowUTC())
                .build();
    }
}
