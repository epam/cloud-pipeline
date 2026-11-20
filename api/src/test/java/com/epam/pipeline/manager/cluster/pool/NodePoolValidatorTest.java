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

package com.epam.pipeline.manager.cluster.pool;

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.controller.vo.cluster.pool.CapacityReservationRequest;
import com.epam.pipeline.controller.vo.cluster.pool.NodePoolVO;
import com.epam.pipeline.entity.cluster.InstanceOffer;
import com.epam.pipeline.entity.cluster.PriceType;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservationType;
import com.epam.pipeline.entity.cluster.pool.NodePoolType;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.cloud.CloudFacade;
import com.epam.pipeline.manager.cluster.InstanceOfferManager;
import com.epam.pipeline.manager.cluster.capacityreservation.CapacityReservationCloudFacade;
import com.epam.pipeline.manager.pipeline.ToolManager;
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

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class NodePoolValidatorTest {

    private static final Long REGION_ID = 1L;
    private static final String INSTANCE_TYPE = "m5.large";
    private static final String FALLBACK_TYPE = "m5.xlarge";
    private static final String OUT_OF_FAMILY_TYPE = "c5.large";
    private static final String FAMILY_PATTERN = "m5.*";
    private static final int MAX_FALLBACKS = 5;
    private static final int START_LEAD_DAYS = 10;
    private static final int END_WINDOW_DAYS = 40;
    private static final int COMMITMENT_DAYS = 20;
    private static final long COMMITMENT_DURATION = Duration.ofDays(COMMITMENT_DAYS).getSeconds();
    private static final int INSTANCE_DISK = 100;
    private static final int INSTANCE_COUNT = 2;
    private static final int LOW_VCPU = 2;
    private static final int FALLBACK_INSTANCE_COUNT = 1;

    @Mock
    private MessageHelper messageHelper;

    @Mock
    private CloudRegionManager regionManager;

    @Mock
    private InstanceOfferManager instanceOfferManager;

    @Mock
    private NodeScheduleManager scheduleManager;

    @Mock
    private ToolManager toolManager;

    @Mock
    private CloudFacade cloudFacade;

    @Mock
    private PreferenceManager preferenceManager;

    @Mock
    private CapacityReservationCloudFacade reservationCloudFacade;

    private final AwsRegion region = new AwsRegion();

    private NodePoolValidator validator;

    @BeforeEach
    public void setUp() {
        region.setId(REGION_ID);
        when(regionManager.load(REGION_ID)).thenReturn(region);
        when(reservationCloudFacade.isSupported(CloudProvider.AWS)).thenReturn(true);
        when(instanceOfferManager.isPriceTypeAllowed(anyString())).thenReturn(true);
        when(instanceOfferManager.isToolInstanceAllowed(anyString(), anyLong(), anyBoolean())).thenReturn(true);
        when(instanceOfferManager.findOffer(anyString(), anyLong())).thenReturn(Optional.empty());
        final Map<CapacityReservationType, String> families = new HashMap<>();
        families.put(CapacityReservationType.FUTURE_DATED, FAMILY_PATTERN);
        families.put(CapacityReservationType.CAPACITY_BLOCK, FAMILY_PATTERN);
        when(preferenceManager.getPreference(SystemPreferences.CLUSTER_CAPACITY_RESERVATION_INSTANCE_FAMILIES))
                .thenReturn(families);
        when(preferenceManager.getPreference(
                SystemPreferences.CLUSTER_CAPACITY_RESERVATION_FALLBACK_CANDIDATES_MAX_COUNT))
                .thenReturn(MAX_FALLBACKS);
        validator = new NodePoolValidator(messageHelper, regionManager, instanceOfferManager, scheduleManager,
                toolManager, cloudFacade, preferenceManager, reservationCloudFacade);
    }

    @Test
    public void shouldAcceptAFutureDatedRequestWithValidFallbackCandidates() {
        final NodePoolVO vo = futureDatedVO(singleFallback(FALLBACK_TYPE));

        validator.validate(vo);
    }

    @Test
    public void shouldAcceptAFutureDatedRequestWithNoFallbackCandidates() {
        final NodePoolVO vo = futureDatedVO(null);

        validator.validate(vo);
    }

    @Test
    public void shouldAcceptACapacityBlockRequestWithNoFallbackCandidates() {
        final NodePoolVO vo = capacityBlockVO(null);

        validator.validate(vo);
    }

    @Test
    public void shouldRejectFallbackCandidatesOnACapacityBlockRequest() {
        final NodePoolVO vo = capacityBlockVO(singleFallback(FALLBACK_TYPE));

        assertThatThrownBy(() -> validator.validate(vo)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void shouldRejectABlankFallbackInstanceType() {
        final NodePoolVO vo = futureDatedVO(singleFallback(" "));

        assertThatThrownBy(() -> validator.validate(vo)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void shouldRejectAMissingFallbackInstanceCount() {
        final NodePoolVO vo = futureDatedVO(Collections.singletonList(candidate(FALLBACK_TYPE, null)));

        assertThatThrownBy(() -> validator.validate(vo)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void shouldRejectAFallbackInstanceCountBelowOne() {
        final NodePoolVO vo = futureDatedVO(Collections.singletonList(candidate(FALLBACK_TYPE, 0)));

        assertThatThrownBy(() -> validator.validate(vo)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void shouldRejectAFallbackFamilyThePatternDoesNotAllow() {
        final NodePoolVO vo = futureDatedVO(singleFallback(OUT_OF_FAMILY_TYPE));

        assertThatThrownBy(() -> validator.validate(vo)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void shouldRejectAFallbackWithTooFewTotalVcpus() {
        when(instanceOfferManager.findOffer(FALLBACK_TYPE, REGION_ID))
                .thenReturn(Optional.of(InstanceOffer.builder().vCPU(LOW_VCPU).build()));
        final NodePoolVO vo = futureDatedVO(singleFallback(FALLBACK_TYPE));

        assertThatThrownBy(() -> validator.validate(vo)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void shouldRejectMoreFallbacksThanThePreferenceAllows() {
        final NodePoolVO vo = futureDatedVO(candidates(MAX_FALLBACKS + 1));

        assertThatThrownBy(() -> validator.validate(vo)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void shouldAcceptExactlyAsManyFallbacksAsThePreferenceAllows() {
        final NodePoolVO vo = futureDatedVO(candidates(MAX_FALLBACKS));

        validator.validate(vo);
    }

    @Test
    public void shouldNotLimitFallbacksWhenThePreferenceIsDisabled() {
        when(preferenceManager.getPreference(
                SystemPreferences.CLUSTER_CAPACITY_RESERVATION_FALLBACK_CANDIDATES_MAX_COUNT))
                .thenReturn(-1);
        final NodePoolVO vo = futureDatedVO(candidates(MAX_FALLBACKS + 1));

        validator.validate(vo);
    }

    @Test
    public void shouldRejectAFutureDatedRequestWhoseMainFamilyIsNotAllowed() {
        final NodePoolVO vo = futureDatedVO(null);
        vo.setInstanceType(OUT_OF_FAMILY_TYPE);

        assertThatThrownBy(() -> validator.validate(vo)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void shouldRejectACapacityBlockRequestWithoutAnOfferingId() {
        final NodePoolVO vo = capacityBlockVO(null);
        vo.getCapacityReservationRequest().setCapacityBlockOfferingId(null);

        assertThatThrownBy(() -> validator.validate(vo)).isInstanceOf(IllegalArgumentException.class);
    }

    private List<CapacitySpecification> singleFallback(final String instanceType) {
        return Collections.singletonList(candidate(instanceType, FALLBACK_INSTANCE_COUNT));
    }

    private List<CapacitySpecification> candidates(final int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> candidate(FALLBACK_TYPE, FALLBACK_INSTANCE_COUNT))
                .collect(Collectors.toList());
    }

    private static CapacitySpecification candidate(final String instanceType, final Integer count) {
        return CapacitySpecification.builder().instanceType(instanceType).instanceCount(count).build();
    }

    private NodePoolVO futureDatedVO(final List<CapacitySpecification> fallbacks) {
        final NodePoolVO vo = baseVO();
        vo.setCapacityReservationRequest(CapacityReservationRequest.builder()
                .reservationType(CapacityReservationType.FUTURE_DATED)
                .requestedStartDate(DateUtils.nowUTC().plusDays(START_LEAD_DAYS))
                .requestedEndDate(DateUtils.nowUTC().plusDays(END_WINDOW_DAYS))
                .commitmentDuration(COMMITMENT_DURATION)
                .fallbackCandidates(fallbacks)
                .build());
        return vo;
    }

    private NodePoolVO capacityBlockVO(final List<CapacitySpecification> fallbacks) {
        final NodePoolVO vo = baseVO();
        vo.setCapacityReservationRequest(CapacityReservationRequest.builder()
                .reservationType(CapacityReservationType.CAPACITY_BLOCK)
                .capacityBlockOfferingId("cbr-0123456789abcdef0")
                .fallbackCandidates(fallbacks)
                .build());
        return vo;
    }

    private NodePoolVO baseVO() {
        final NodePoolVO vo = new NodePoolVO();
        vo.setRegionId(REGION_ID);
        vo.setPriceType(PriceType.ON_DEMAND);
        vo.setInstanceType(INSTANCE_TYPE);
        vo.setInstanceDisk(INSTANCE_DISK);
        vo.setPoolType(NodePoolType.STANDARD);
        vo.setCount(INSTANCE_COUNT);
        return vo;
    }
}
