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

package com.epam.pipeline.manager.notification;

import com.epam.pipeline.config.JsonMapper;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservation;
import com.epam.pipeline.entity.notification.NotificationType;
import com.epam.pipeline.test.creator.cluster.capacityreservation.CapacityReservationCreatorUtils;
import org.junit.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class NotificationParameterManagerTest {

    private final NotificationParameterManager parameterManager = new NotificationParameterManager(new JsonMapper());

    @Test
    public void shouldIncludeTheOfferingIdForACapacityBlock() {
        final CapacityReservation reservation = CapacityReservationCreatorUtils.getCapacityBlockReservation(1L);

        final Map<String, Object> parameters = parameterManager.build(
                NotificationType.CAPACITY_RESERVATION_STATUS_CHANGED, reservation);

        assertThat(parameters.get("capacityBlockOfferingId"))
                .isEqualTo(CapacityReservationCreatorUtils.CAPACITY_BLOCK_OFFERING_ID);
    }

    @Test
    public void shouldLeaveTheOfferingIdAbsentForAFutureDatedReservation() {
        final CapacityReservation reservation = CapacityReservationCreatorUtils.getReservation(1L);

        final Map<String, Object> parameters = parameterManager.build(
                NotificationType.CAPACITY_RESERVATION_STATUS_CHANGED, reservation);

        assertThat(parameters.get("capacityBlockOfferingId")).isNull();
    }
}
