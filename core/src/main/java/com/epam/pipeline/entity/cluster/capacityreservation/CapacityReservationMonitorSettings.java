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
 * Tunables for the capacity reservation monitor, held in a system preference so they can be changed
 * without a release.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CapacityReservationMonitorSettings {

    /**
     * How long before its end date a reservation is flagged as finalizing, so the people with work on the
     * pool are warned while they can still act.
     */
    private Integer finalizingLeadHours;
}
