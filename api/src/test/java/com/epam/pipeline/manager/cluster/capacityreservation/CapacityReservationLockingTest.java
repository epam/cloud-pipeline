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
import com.epam.pipeline.entity.cluster.pool.NodePool;
import com.epam.pipeline.manager.AbstractManagerTest;
import com.epam.pipeline.test.creator.cluster.capacityreservation.CapacityReservationCreatorUtils;
import com.epam.pipeline.test.creator.cluster.pool.NodePoolCreatorUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class CapacityReservationLockingTest extends AbstractManagerTest {

    private static final long TIMEOUT_SECONDS = 30;
    private static final long STILL_BLOCKED_MILLIS = 1000;

    @Autowired
    private CapacityReservationService reservationService;

    @Autowired
    private CapacityReservationDao reservationDao;

    @Autowired
    private NodePoolDao poolDao;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transaction;
    private ExecutorService executor;
    private Long poolId;
    private Long reservationId;

    @BeforeEach
    public void setUp() {
        transaction = new TransactionTemplate(transactionManager);
        executor = Executors.newFixedThreadPool(2);
        transaction.execute(status -> {
            final NodePool pool = NodePoolCreatorUtils.getPoolWithoutSchedule();
            pool.setAutoscaled(false);
            pool.setCapacityReservation(true);
            pool.setCount(0);
            poolId = poolDao.create(pool).getId();
            final CapacityReservation reservation = CapacityReservationCreatorUtils.getReservation(poolId);
            reservation.setStatus(CapacityReservationStatus.REQUIRED_APPROVE);
            reservationId = reservationDao.create(reservation).getId();
            return null;
        });
    }

    @AfterEach
    public void removeReservationAndPool() {
        executor.shutdownNow();
        transaction.execute(status -> {
            reservationDao.delete(reservationId);
            poolDao.delete(poolId);
            return null;
        });
    }

    @Test
    public void shouldNotApproveAReservationCancelledWhileTheApprovalWaited() throws Exception {
        final CountDownLatch cancelHoldsThePool = new CountDownLatch(1);
        final CountDownLatch finishTheCancel = new CountDownLatch(1);
        final Future<?> cancel = executor.submit(() -> transaction.execute(status -> {
            poolDao.findForUpdate(poolId).orElseThrow(AssertionError::new);
            cancelHoldsThePool.countDown();
            awaitQuietly(finishTheCancel);
            final CapacityReservation reservation = reservationDao.find(reservationId)
                    .orElseThrow(AssertionError::new);
            reservation.setStatus(CapacityReservationStatus.CANCELLED);
            return reservationDao.update(reservation);
        }));
        assertThat(cancelHoldsThePool.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

        final Future<CapacityReservation> approval = executor.submit(() -> reservationService.approve(reservationId));

        assertStillBlocked(approval);
        finishTheCancel.countDown();
        cancel.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThatThrownBy(() -> approval.get(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
        assertThat(reservationDao.find(reservationId).orElseThrow(AssertionError::new).getStatus())
                .isEqualTo(CapacityReservationStatus.CANCELLED);
    }

    private static void assertStillBlocked(final Future<?> write) throws InterruptedException {
        Thread.sleep(STILL_BLOCKED_MILLIS);
        assertThat(write.isDone()).as("the write should be waiting for the pool's row lock").isFalse();
    }

    private static void awaitQuietly(final CountDownLatch latch) {
        try {
            latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
