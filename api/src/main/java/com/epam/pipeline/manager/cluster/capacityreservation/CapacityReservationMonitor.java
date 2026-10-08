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
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservationStatus;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.scheduling.AbstractSchedulingManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.SchedulerLock;
import org.apache.commons.collections4.ListUtils;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.function.BiConsumer;

@Service
@RequiredArgsConstructor
public class CapacityReservationMonitor extends AbstractSchedulingManager {

    private final CapacityReservationMonitorCore core;

    @PostConstruct
    public void init() {
        scheduleSecured(core::monitor, SystemPreferences.CLUSTER_CAPACITY_RESERVATION_MONITOR_CRON,
                "CapacityReservationMonitor");
    }

    /**
     * Lists the reservations each step of the cycle is due for, and hands them to
     * {@link CapacityReservationStateProcessor} one at a time. The work itself, and the transaction around it,
     * belongs to the processor; a reservation that fails costs its own step only.
     */
    @Component
    @Slf4j
    @RequiredArgsConstructor
    static class CapacityReservationMonitorCore {

        private final CapacityReservationService reservationService;
        private final CapacityReservationStateProcessor processor;

        @SchedulerLock(name = "CapacityReservationMonitor_monitor", lockAtMostForString = "PT5M")
        public void monitor() {
            log.debug("Running CapacityReservation monitor cycle");
            processApproved();
            processAssessing();
            processScheduled();
            processActive();
            processFinalizing();
            log.debug("Finished CapacityReservation monitor cycle");
        }

        void processApproved() {
            forEach(CapacityReservationStatus.APPROVED, processor::submit);
        }

        void processAssessing() {
            forEach(CapacityReservationStatus.ASSESSING_BY_CLOUD_PROVIDER, processor::poll);
        }

        void processScheduled() {
            forEach(CapacityReservationStatus.SCHEDULED, processor::poll);
        }

        void processActive() {
            forEach(CapacityReservationStatus.ACTIVE, processor::finalizeIfEnding);
        }

        void processFinalizing() {
            forEach(CapacityReservationStatus.FINALIZING, processor::finishIfEnded);
        }

        @SuppressWarnings("PMD.AvoidCatchingGenericException")
        private void forEach(final CapacityReservationStatus status,
                             final BiConsumer<Long, CapacityReservationStatus> step) {
            for (final CapacityReservation reservation :
                    ListUtils.emptyIfNull(reservationService.loadByStatus(status))) {
                try {
                    step.accept(reservation.getId(), status);
                } catch (Exception e) {
                    log.error("Failed to process capacity reservation {} in status {}",
                            reservation.getId(), status, e);
                }
            }
        }
    }
}
