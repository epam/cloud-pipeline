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
import com.epam.pipeline.config.JsonMapper;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservation;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservationPolicy;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservationStatus;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservationType;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.manager.user.UserManager;
import com.epam.pipeline.test.creator.cluster.capacityreservation.CapacityReservationCreatorUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class CapacityReservationApprovalServiceTest {

    private static final Long ORIGIN_ID = 1L;
    private static final Long REGION_ID = 1L;
    private static final String INSTANCE_TYPE = "p5.48xlarge";
    private static final int INSTANCE_COUNT = 2;
    private static final String OWNER = "user";
    private static final long ORIGIN_DURATION = 14 * 24 * 3600L;
    private static final long ONE_HOUR = 3600L;

    @Mock
    private MessageHelper messageHelper;

    @Mock
    private PreferenceManager preferenceManager;

    @Mock
    private AuthManager authManager;

    @Mock
    private UserManager userManager;

    @Mock
    private CapacityReservationService reservationService;

    private CapacityReservationApprovalService service;

    @Before
    public void setUp() {
        service = new CapacityReservationApprovalService(messageHelper, preferenceManager, authManager, userManager);
        ReflectionTestUtils.setField(service, "reservationService", reservationService);
        when(authManager.isAdmin()).thenReturn(false);
        when(preferenceManager.getPreference(SystemPreferences.CLUSTER_CAPACITY_RESERVATION_POLICIES))
                .thenReturn(Collections.emptyList());
    }

    @Test
    public void shouldInheritApprovalWhenRetryAsksForFewerDaysThanTheOrigin() {
        final CapacityReservation origin = failedOrigin(ORIGIN_DURATION);
        when(reservationService.find(ORIGIN_ID)).thenReturn(Optional.of(origin));
        final CapacityReservation retry = retryOf(origin, ORIGIN_DURATION - ONE_HOUR);

        assertThat(service.evaluate(retry)).isEqualTo(CapacityReservationStatus.APPROVED);
    }

    @Test
    public void shouldInheritApprovalWhenRetryAsksForTheSameNumberOfDaysAsTheOrigin() {
        final CapacityReservation origin = failedOrigin(ORIGIN_DURATION);
        when(reservationService.find(ORIGIN_ID)).thenReturn(Optional.of(origin));
        final CapacityReservation retry = retryOf(origin, ORIGIN_DURATION);

        assertThat(service.evaluate(retry)).isEqualTo(CapacityReservationStatus.APPROVED);
    }

    @Test
    public void shouldNotInheritApprovalWhenRetryAsksForMoreDaysThanTheOrigin() {
        final CapacityReservation origin = failedOrigin(ORIGIN_DURATION);
        when(reservationService.find(ORIGIN_ID)).thenReturn(Optional.of(origin));
        final CapacityReservation retry = retryOf(origin, ORIGIN_DURATION + ONE_HOUR);

        assertThat(service.evaluate(retry)).isEqualTo(CapacityReservationStatus.REQUIRED_APPROVE);
    }

    @Test
    public void shouldInheritApprovalForACapacityBlockRetryAskingForFewerDaysToo() {
        final CapacityReservation origin = failedOrigin(ORIGIN_DURATION).toBuilder()
                .reservationType(CapacityReservationType.CAPACITY_BLOCK)
                .build();
        when(reservationService.find(ORIGIN_ID)).thenReturn(Optional.of(origin));
        final CapacityReservation retry = retryOf(origin, ORIGIN_DURATION - ONE_HOUR);

        assertThat(service.evaluate(retry)).isEqualTo(CapacityReservationStatus.APPROVED);
    }

    @Test
    public void shouldDenyACapacityBlockMatchingADenyPolicy() {
        when(preferenceManager.getPreference(SystemPreferences.CLUSTER_CAPACITY_RESERVATION_POLICIES))
                .thenReturn(policies("DENY"));
        final CapacityReservation request = CapacityReservationCreatorUtils.getCapacityBlockReservation(1L);

        assertThatThrownBy(() -> service.evaluate(request)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void shouldAutoApproveACapacityBlockMatchingAnAutoApprovePolicy() {
        when(preferenceManager.getPreference(SystemPreferences.CLUSTER_CAPACITY_RESERVATION_POLICIES))
                .thenReturn(policies("AUTO_APPROVE"));
        final CapacityReservation request = CapacityReservationCreatorUtils.getCapacityBlockReservation(1L);

        assertThat(service.evaluate(request)).isEqualTo(CapacityReservationStatus.APPROVED);
    }

    @Test
    public void shouldRequireApprovalForACapacityBlockWhoseTypeDoesNotMatchThePolicy() {
        when(preferenceManager.getPreference(SystemPreferences.CLUSTER_CAPACITY_RESERVATION_POLICIES))
                .thenReturn(policies("AUTO_APPROVE", "reservation.type", "FUTURE_DATED"));
        final CapacityReservation request = CapacityReservationCreatorUtils.getCapacityBlockReservation(1L);

        assertThat(service.evaluate(request)).isEqualTo(CapacityReservationStatus.REQUIRED_APPROVE);
    }

    private static List<CapacityReservationPolicy> policies(final String action) {
        return policies(action, "reservation.type", "CAPACITY_BLOCK");
    }

    private static List<CapacityReservationPolicy> policies(final String action, final String field,
                                                             final String value) {
        final String json = "[{"
                + "  \"action\": \"" + action + "\","
                + "  \"statement\": {"
                + "    \"type\": \"LOGICAL\", \"field\": \"" + field + "\", \"operand\": \"=\", "
                + "    \"value\": \"" + value + "\""
                + "  }"
                + "}]";
        return JsonMapper.parseData(json, new TypeReference<List<CapacityReservationPolicy>>() { }, new ObjectMapper());
    }

    private static CapacityReservation failedOrigin(final long durationSeconds) {
        return CapacityReservation.builder()
                .id(ORIGIN_ID)
                .status(CapacityReservationStatus.FAILED)
                .instanceType(INSTANCE_TYPE)
                .instanceCount(INSTANCE_COUNT)
                .commitmentDuration(durationSeconds)
                .reservationType(CapacityReservationType.FUTURE_DATED)
                .cloudProvider(CloudProvider.AWS)
                .regionId(REGION_ID)
                .owner(OWNER)
                .build();
    }

    private static CapacityReservation retryOf(final CapacityReservation origin, final long durationSeconds) {
        return CapacityReservation.builder()
                .originId(origin.getId())
                .instanceType(origin.getInstanceType())
                .instanceCount(origin.getInstanceCount())
                .commitmentDuration(durationSeconds)
                .reservationType(origin.getReservationType())
                .cloudProvider(origin.getCloudProvider())
                .regionId(origin.getRegionId())
                .owner(origin.getOwner())
                .build();
    }
}
