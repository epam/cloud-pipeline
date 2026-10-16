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
import com.epam.pipeline.manager.cluster.capacityreservation.CapacityReservationMonitor.CapacityReservationMonitorCore;
import com.epam.pipeline.test.creator.cluster.capacityreservation.CapacityReservationCreatorUtils;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class CapacityReservationMonitorCoreTest {

    private static final Long POOL_ID = 1L;
    private static final Long FIRST_ID = 10L;
    private static final Long SECOND_ID = 11L;
    private static final String PROVIDER_UNREACHABLE = "provider unreachable";

    @Mock
    private CapacityReservationService reservationService;

    @Mock
    private CapacityReservationStateProcessor processor;

    @InjectMocks
    private CapacityReservationMonitorCore monitor;

    @Test
    public void shouldHandEveryListedReservationToTheProcessorWithTheStatusItWasListedIn() {
        givenListed(CapacityReservationStatus.SCHEDULED, FIRST_ID, SECOND_ID);

        monitor.processScheduled();

        verify(processor).poll(FIRST_ID, CapacityReservationStatus.SCHEDULED);
        verify(processor).poll(SECOND_ID, CapacityReservationStatus.SCHEDULED);
    }

    @Test
    public void shouldKeepProcessingAfterOneReservationFails() {
        givenListed(CapacityReservationStatus.SCHEDULED, FIRST_ID, SECOND_ID);
        doThrow(new IllegalStateException(PROVIDER_UNREACHABLE))
                .when(processor).poll(FIRST_ID, CapacityReservationStatus.SCHEDULED);

        monitor.processScheduled();

        verify(processor).poll(SECOND_ID, CapacityReservationStatus.SCHEDULED);
    }

    @Test
    public void shouldTakeAStatusWithoutReservationsAsNothingToDo() {
        when(reservationService.loadByStatus(any())).thenReturn(null);

        monitor.monitor();

        verify(processor, never()).submit(any(), any());
        verify(processor, never()).poll(any(), any());
        verify(processor, never()).finalizeIfEnding(any(), any());
        verify(processor, never()).finishIfEnded(any(), any());
    }

    @Test
    public void shouldRunTheStepOfEveryStatusOfTheCycle() {
        givenListed(CapacityReservationStatus.APPROVED, FIRST_ID);
        givenListed(CapacityReservationStatus.ASSESSING_BY_CLOUD_PROVIDER, FIRST_ID);
        givenListed(CapacityReservationStatus.SCHEDULED, FIRST_ID);
        givenListed(CapacityReservationStatus.ACTIVE, FIRST_ID);
        givenListed(CapacityReservationStatus.FINALIZING, FIRST_ID);

        monitor.monitor();

        verify(processor).submit(FIRST_ID, CapacityReservationStatus.APPROVED);
        verify(processor).poll(FIRST_ID, CapacityReservationStatus.ASSESSING_BY_CLOUD_PROVIDER);
        verify(processor).poll(FIRST_ID, CapacityReservationStatus.SCHEDULED);
        verify(processor).finalizeIfEnding(FIRST_ID, CapacityReservationStatus.ACTIVE);
        verify(processor).finishIfEnded(FIRST_ID, CapacityReservationStatus.FINALIZING);
    }

    private void givenListed(final CapacityReservationStatus status, final Long... ids) {
        final List<CapacityReservation> listed = new ArrayList<>();
        for (final Long id : ids) {
            final CapacityReservation reservation = CapacityReservationCreatorUtils.getReservation(POOL_ID);
            reservation.setId(id);
            reservation.setStatus(status);
            listed.add(reservation);
        }
        when(reservationService.loadByStatus(status)).thenReturn(listed);
    }
}
