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

import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.scheduling.AbstractSchedulingManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

/**
 * Schedules {@link CapacityReservationMonitorCore}. Split from it for the reason every scheduled manager here is:
 * the locked method has to be called through the proxy for {@code @SchedulerLock} to apply, so the schedule lives
 * outside the bean it drives.
 */
@Service
@RequiredArgsConstructor
public class CapacityReservationMonitorService extends AbstractSchedulingManager {

    private final CapacityReservationMonitorCore core;

    @PostConstruct
    public void init() {
        scheduleSecured(core::monitor, SystemPreferences.CLUSTER_CAPACITY_RESERVATION_MONITOR_CRON,
                "CapacityReservationMonitor");
    }
}
