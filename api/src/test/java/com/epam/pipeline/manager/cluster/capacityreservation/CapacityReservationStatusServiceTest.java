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
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservationType;
import com.epam.pipeline.entity.notification.NotificationType;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.manager.notification.NotificationManager;
import com.epam.pipeline.manager.pipeline.PipelineRunCRUDService;
import com.epam.pipeline.test.creator.cluster.capacityreservation.CapacityReservationCreatorUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class CapacityReservationStatusServiceTest {

    private static final String REASON = "AWS did not give a definitive answer, will retry";
    private static final Long RESERVATION_ID = 7L;
    private static final String RUN_OWNER = "run-owner";
    private static final String OTHER_RUN_OWNER = "other-run-owner";

    @Mock
    private CapacityReservationDao reservationDao;

    @Mock
    private NotificationManager notificationManager;

    @Mock
    private PipelineRunCRUDService runCRUDService;

    @Mock
    private MessageHelper messageHelper;

    private CapacityReservationStatusService statusService;

    @Before
    public void setUp() {
        statusService = new CapacityReservationStatusService(reservationDao, notificationManager,
                runCRUDService, messageHelper);
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

        verifyZeroInteractions(notificationManager);
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
    public void shouldNotNotifyWhenARefusedRequestOnlyMovesToAnotherCandidate() {
        final CapacityReservation reservation = approved();
        givenTheRowIsWritten();

        statusService.transition(reservation, CapacityReservationStatus.APPROVED, REASON);

        verifyZeroInteractions(notificationManager);
    }

    @Test
    public void shouldNotifyWhenTheStatusActuallyChanges() {
        final CapacityReservation reservation = approved();
        givenTheRowIsWritten();

        statusService.transition(reservation, CapacityReservationStatus.ASSESSING_BY_CLOUD_PROVIDER, null);

        verify(notificationManager).notifyCapacityReservation(reservation,
                NotificationType.CAPACITY_RESERVATION_STATUS_CHANGED, Collections.emptyList());
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
    public void shouldCopyTheRunOwnersOfTheNodePoolWhenFinalizing() {
        final CapacityReservation reservation = active();
        givenTheRowIsWritten();
        when(runCRUDService.loadRunsByPoolId(reservation.getNodePoolId()))
                .thenReturn(Arrays.asList(runOwnedBy(RUN_OWNER), runOwnedBy(OTHER_RUN_OWNER), runOwnedBy(RUN_OWNER)));

        statusService.transition(reservation, CapacityReservationStatus.FINALIZING, null);

        verify(notificationManager).notifyCapacityReservation(reservation,
                NotificationType.CAPACITY_RESERVATION_FINALIZING, Arrays.asList(RUN_OWNER, OTHER_RUN_OWNER));
    }

    @Test
    public void shouldCopyTheRunOwnersOfACapacityBlockWhenFinalizing() {
        final CapacityReservation reservation = active();
        reservation.setReservationType(CapacityReservationType.CAPACITY_BLOCK);
        givenTheRowIsWritten();
        when(runCRUDService.loadRunsByPoolId(reservation.getNodePoolId()))
                .thenReturn(Collections.singletonList(runOwnedBy(RUN_OWNER)));

        statusService.transition(reservation, CapacityReservationStatus.FINALIZING, null);

        verify(notificationManager).notifyCapacityReservation(reservation,
                NotificationType.CAPACITY_RESERVATION_FINALIZING, Collections.singletonList(RUN_OWNER));
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

    private static CapacityReservation active() {
        final CapacityReservation reservation = approved();
        reservation.setStatus(CapacityReservationStatus.ACTIVE);
        return reservation;
    }

    private static PipelineRun runOwnedBy(final String owner) {
        final PipelineRun run = new PipelineRun();
        run.setOwner(owner);
        return run;
    }
}
