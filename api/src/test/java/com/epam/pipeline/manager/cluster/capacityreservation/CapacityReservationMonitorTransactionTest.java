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

import com.epam.pipeline.dao.cluster.capacityreservation.CapacityReservationDao;
import com.epam.pipeline.dao.cluster.pool.NodePoolDao;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservation;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservationStatus;
import com.epam.pipeline.entity.cluster.capacityreservation.CloudCapacityReservation;
import com.epam.pipeline.entity.cluster.capacityreservation.CloudCapacityReservationState;
import com.epam.pipeline.entity.cluster.pool.NodePool;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.AbstractManagerTest;
import com.epam.pipeline.manager.cluster.capacityreservation.CapacityReservationMonitor.CapacityReservationMonitorCore;
import com.epam.pipeline.manager.region.CloudRegionManager;
import com.epam.pipeline.test.creator.cluster.capacityreservation.CapacityReservationCreatorUtils;
import com.epam.pipeline.test.creator.cluster.pool.NodePoolCreatorUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * A monitor step is several writes that only make sense together - here, a reservation becoming ACTIVE and its pool
 * being switched on. Committed one by one, a failure between them left the reservation ACTIVE with its pool still
 * off, and nothing ever retried the activation.
 *
 * <p>Deliberately not transactional, unlike the other manager tests: what is under test is what the monitor's own
 * transactions commit, so they have to commit for real. The rows are created and removed around every test.
 */
public class CapacityReservationMonitorTransactionTest extends AbstractManagerTest {

    private static final String CLOUD_ID = "cr-0123456789abcdef0";
    private static final String ZONE = "us-east-1c";
    private static final int DURATION_HOURS = 24;

    @Autowired
    private CapacityReservationMonitorCore monitor;

    @Autowired
    private CapacityReservationDao reservationDao;

    @Autowired
    private NodePoolDao poolDao;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @SpyBean
    private CapacityReservationService reservationService;

    /**
     * The provider is not what is under test; it only has to report that the capacity is now live.
     */
    @MockBean
    private CapacityReservationCloudFacade cloudFacade;

    /**
     * Activation looks the reservation's region up to find the subnet for its zone; this database has no regions.
     */
    @MockBean
    private CloudRegionManager regionManager;

    private TransactionTemplate transaction;
    private Long poolId;
    private Long reservationId;

    @BeforeEach
    public void setUp() {
        final AwsRegion region = new AwsRegion();
        region.setRegionCode("us-east-1");
        when(regionManager.load(any())).thenReturn((AbstractCloudRegion) region);
        transaction = new TransactionTemplate(transactionManager);
        transaction.execute(status -> {
            final NodePool pool = NodePoolCreatorUtils.getPoolWithoutSchedule();
            pool.setAutoscaled(false);
            pool.setCapacityReservation(true);
            pool.setCount(0);
            poolId = poolDao.create(pool).getId();

            final CapacityReservation reservation = CapacityReservationCreatorUtils.getReservation(poolId);
            reservation.setStatus(CapacityReservationStatus.SCHEDULED);
            reservation.setCloudReservationId(CLOUD_ID);
            reservation.setAvailabilityZone(ZONE);
            reservation.setStartDate(reservation.getRequestedStartDate());
            reservation.setEndDate(reservation.getRequestedStartDate().plusHours(DURATION_HOURS));
            reservationId = reservationDao.create(reservation).getId();
            return null;
        });
        final LocalDateTime start = reservationDao.find(reservationId).orElseThrow(AssertionError::new)
                .getStartDate();
        when(cloudFacade.describe(any())).thenReturn(CloudCapacityReservation.builder()
                .cloudReservationId(CLOUD_ID)
                .availabilityZone(ZONE)
                .state(CloudCapacityReservationState.ACTIVE)
                .startDate(start)
                .endDate(start.plusHours(DURATION_HOURS))
                .build());
    }

    @AfterEach
    public void removeReservationAndPool() {
        transaction.execute(status -> {
            reservationDao.delete(reservationId);
            poolDao.delete(poolId);
            return null;
        });
    }

    @Test
    public void shouldCommitTheStatusAndThePoolTogether() {
        monitor.processScheduled();

        assertThat(statusOfReservation()).isEqualTo(CapacityReservationStatus.ACTIVE);
        assertThat(countOfPool()).isEqualTo(CapacityReservationCreatorUtils.getReservation(poolId).getInstanceCount());
    }

    /**
     * The failure the step boundary exists for: the pool cannot be switched on, so the reservation must not be
     * recorded as ACTIVE either - left SCHEDULED, the next cycle sees the capacity live and tries the whole step
     * again.
     */
    @Test
    public void shouldRollTheStatusBackWhenThePoolCannotBeSwitchedOn() {
        doThrow(new IllegalStateException("pool write failed")).when(reservationService).activatePool(any());

        monitor.processScheduled();

        assertThat(statusOfReservation()).isEqualTo(CapacityReservationStatus.SCHEDULED);
        assertThat(countOfPool()).isZero();
    }

    /**
     * The step that ends a reservation writes FINISHED and switches its pool off. Committed apart, a failure between
     * them left the pool running on-demand nodes behind a reservation marked finished - which nothing looks at again.
     */
    @Test
    public void shouldKeepAReservationFinalizingWhenItsPoolCannotBeSwitchedOff() {
        givenReservation(CapacityReservationStatus.FINALIZING, reservation -> {
            reservation.setStartDate(DateUtils.nowUTC().minusHours(DURATION_HOURS + 1));
            reservation.setEndDate(DateUtils.nowUTC().minusHours(1));
        });
        doThrow(new IllegalStateException("pool write failed")).when(reservationService).deactivatePool(any());

        monitor.processFinalizing();

        assertThat(statusOfReservation()).isEqualTo(CapacityReservationStatus.FINALIZING);
    }

    /**
     * A delayed reservation is released only after the next date is written, so a release that fails leaves the
     * reservation exactly as it was - still pointing at the delayed one, for the next cycle to release.
     */
    @Test
    public void shouldKeepADelayedReservationAsItWasWhenReleasingItFails() {
        givenReservation(CapacityReservationStatus.ASSESSING_BY_CLOUD_PROVIDER, reservation -> { });
        final CapacityReservation before = reservationDao.find(reservationId).orElseThrow(AssertionError::new);
        when(cloudFacade.describe(any())).thenReturn(CloudCapacityReservation.builder()
                .cloudReservationId(CLOUD_ID)
                .state(CloudCapacityReservationState.DELAYED)
                .build());
        doThrow(new IllegalStateException("provider unreachable")).when(cloudFacade).cancel(any());

        monitor.processAssessing();

        final CapacityReservation after = reservationDao.find(reservationId).orElseThrow(AssertionError::new);
        assertThat(after.getStatus()).isEqualTo(CapacityReservationStatus.ASSESSING_BY_CLOUD_PROVIDER);
        assertThat(after.getCloudReservationId()).isEqualTo(CLOUD_ID);
        assertThat(after.getAttempt()).isEqualTo(before.getAttempt());
        assertThat(after.getStartDate()).isEqualTo(before.getStartDate());
    }

    private void givenReservation(final CapacityReservationStatus status,
                                  final Consumer<CapacityReservation> change) {
        transaction.execute(ignored -> {
            final CapacityReservation reservation = reservationDao.find(reservationId)
                    .orElseThrow(AssertionError::new);
            reservation.setStatus(status);
            change.accept(reservation);
            return reservationDao.update(reservation);
        });
    }

    private CapacityReservationStatus statusOfReservation() {
        return reservationDao.find(reservationId).orElseThrow(AssertionError::new).getStatus();
    }

    private int countOfPool() {
        return poolDao.find(poolId).orElseThrow(AssertionError::new).getCount();
    }
}
