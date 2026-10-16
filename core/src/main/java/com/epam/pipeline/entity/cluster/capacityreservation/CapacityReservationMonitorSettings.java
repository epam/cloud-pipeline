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

package com.epam.pipeline.entity.cluster.capacityreservation;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The {@code cluster.capacity.reservation.monitor.settings} preference.
 *
 * <p>{@code finalizingLeadHours} is how long before a reservation's end its pool stops taking new runs: the pool's
 * window is shortened by that much, so a run is never started on capacity that is about to go away. Runs already on
 * the pool's nodes keep going until the reservation itself ends.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CapacityReservationMonitorSettings {

    private Integer finalizingLeadHours;
}
