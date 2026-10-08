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
import com.epam.pipeline.dao.cluster.capacityreservation.CapacityReservationDao;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservation;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservationStatus;
import com.epam.pipeline.manager.notification.NotificationManager;
import com.epam.pipeline.manager.pipeline.PipelineRunCRUDService;
import com.epam.pipeline.test.creator.cluster.capacityreservation.CapacityReservationCreatorUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.util.Arrays;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class CapacityReservationStatusServiceTest {

    private static final String REASON = "AWS did not give a definitive answer, will retry";
    private static final Long RESERVATION_ID = 7L;

    @Mock
    private CapacityReservationDao reservationDao;

    @Mock
    private NotificationManager notificationManager;

    private CapacityReservationStatusService statusService;

    @BeforeEach
    public void setUp() {
        statusService = new CapacityReservationStatusService(reservationDao, notificationManager,
                mock(PipelineRunCRUDService.class), mock(MessageHelper.class));
    }

    @Test
    public void shouldPersistTheRetryWithoutMovingTheReservation() {
        final CapacityReservation reservation = approved();
        when(reservationDao.updateRetry(reservation)).thenReturn(true);

        assertThat(statusService.recordRetry(reservation, REASON)).isTrue();
        assertThat(reservation.getStatus()).isEqualTo(CapacityReservationStatus.APPROVED);
        assertThat(reservation.getStatusReason()).isEqualTo(REASON);
        assertThat(reservation.getUpdated()).isNotNull();
    }

    @Test
    public void shouldNeverWriteTheWholeRowFromAPossiblyStaleCopy() {
        final CapacityReservation reservation = approved();
        when(reservationDao.updateRetry(any())).thenReturn(false);

        assertThat(statusService.recordRetry(reservation, REASON)).isFalse();
        verify(reservationDao, never()).update(any());
    }

    @Test
    public void shouldNotNotifyAboutARetry() {
        final CapacityReservation reservation = approved();
        when(reservationDao.updateRetry(any())).thenReturn(true);

        statusService.recordRetry(reservation, REASON);

        verifyNoInteractions(notificationManager);
    }

    @Test
    public void shouldLetARefusedSubmissionAskForAnotherDateOrZone() {
        final CapacityReservation reservation = approved();
        givenTheRowIsWritten();

        statusService.transition(reservation, CapacityReservationStatus.APPROVED, REASON);

        assertThat(reservation.getStatus()).isEqualTo(CapacityReservationStatus.APPROVED);
        assertThat(reservation.getStatusReason()).isEqualTo(REASON);
        verify(reservationDao).update(reservation);
    }

    @Test
    public void shouldLetAReservationThatExpiredUnwatchedFinish() {
        givenTheRowIsWritten();
        for (final CapacityReservationStatus from : Arrays.asList(
                CapacityReservationStatus.ASSESSING_BY_CLOUD_PROVIDER, CapacityReservationStatus.SCHEDULED)) {
            final CapacityReservation reservation = approved();
            reservation.setStatus(from);

            statusService.transition(reservation, CapacityReservationStatus.FINISHED, null);

            assertThat(reservation.getStatus()).isEqualTo(CapacityReservationStatus.FINISHED);
        }
    }

    @Test
    public void shouldRejectATransitionTheStateMachineDoesNotAllow() {
        final CapacityReservation reservation = approved();

        assertThatThrownBy(() -> statusService.transition(reservation, CapacityReservationStatus.ACTIVE, null))
                .isInstanceOf(IllegalStateException.class);
        verify(reservationDao, never()).update(any());
    }

    private void givenTheRowIsWritten() {
        when(reservationDao.update(any()))
                .thenAnswer(invocation -> (CapacityReservation) invocation.getArguments()[0]);
    }

    private static CapacityReservation approved() {
        final CapacityReservation reservation = CapacityReservationCreatorUtils.getReservation(1L);
        reservation.setId(RESERVATION_ID);
        reservation.setStatus(CapacityReservationStatus.APPROVED);
        return reservation;
    }
}
