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

package com.epam.pipeline.manager.cluster.pool;

import com.epam.pipeline.controller.vo.cluster.pool.NodePoolVO;
import com.epam.pipeline.dao.cluster.pool.NodePoolDao;
import com.epam.pipeline.entity.cluster.AMIConfiguration;
import com.epam.pipeline.entity.cluster.PriceType;
import com.epam.pipeline.entity.cluster.pool.NodePool;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.manager.AbstractManagerTest;
import com.epam.pipeline.manager.cluster.InstanceOfferManager;
import com.epam.pipeline.manager.region.CloudRegionManager;
import com.epam.pipeline.test.creator.cluster.pool.NodePoolCreatorUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

/**
 * A pool's reservation state is written once - on activation, on deactivation - and nothing retries it. So neither
 * that write nor an ordinary edit may work from a copy of the pool read before the other committed: the later
 * write would silently undo the earlier one.
 *
 * <p>Deliberately not transactional, unlike the other manager tests: the point is two transactions contending for
 * the same row, so each has to commit for real. The pool is created and removed around every test.
 */
public class NodePoolLockingTest extends AbstractManagerTest {

    private static final long TIMEOUT_SECONDS = 30;
    /** Long enough for an unblocked write to have finished; the blocked one must still be waiting. */
    private static final long STILL_BLOCKED_MILLIS = 1000;
    private static final int RESERVED_COUNT = 3;
    private static final String RENAMED = "renamed while locked";
    private static final String EDITED_AMI = "ami-set-by-the-edit";
    private static final String RESERVATION_ZONE = "zone-set-by-activation";

    @Autowired
    private NodePoolManager poolManager;

    @Autowired
    private NodePoolDao poolDao;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockBean
    private CloudRegionManager regionManager;

    /**
     * Validating an edit asks whether the instance and price types are offered, which is about the cloud rather
     * than about locking.
     */
    @MockBean
    private InstanceOfferManager instanceOfferManager;

    private TransactionTemplate transaction;
    private ExecutorService executor;
    private Long poolId;

    @BeforeEach
    public void setUp() {
        final AwsRegion region = new AwsRegion();
        region.setId(NodePoolCreatorUtils.getPoolWithoutSchedule().getRegionId());
        when(regionManager.load(any())).thenReturn((AbstractCloudRegion) region);
        when(instanceOfferManager.isPriceTypeAllowed(any())).thenReturn(true);
        when(instanceOfferManager.isToolInstanceAllowed(any(), any(), anyBoolean())).thenReturn(true);

        transaction = new TransactionTemplate(transactionManager);
        executor = Executors.newFixedThreadPool(2);
        poolId = transaction.execute(status -> {
            final NodePool pool = NodePoolCreatorUtils.getPoolWithoutSchedule();
            pool.setAutoscaled(false);
            pool.setCount(0);
            return poolDao.create(pool).getId();
        });
    }

    @AfterEach
    public void removePool() {
        executor.shutdownNow();
        transaction.execute(status -> {
            poolDao.delete(poolId);
            return null;
        });
    }

    /**
     * Activation merges into the pool's launch configuration. Reading it before a concurrent edit commits, and
     * writing the merge back after it, would drop whatever the edit changed - so activation has to read the pool
     * only once the edit is done.
     */
    @Test
    public void shouldApplyReservationStateToThePoolAsAConcurrentWriterLeftIt() throws Exception {
        final CountDownLatch editHoldsThePool = new CountDownLatch(1);
        final CountDownLatch finishTheEdit = new CountDownLatch(1);
        final Future<?> edit = executor.submit(() -> transaction.execute(status -> {
            final NodePool staleCopy = poolDao.findForUpdate(poolId).orElseThrow(AssertionError::new);
            editHoldsThePool.countDown();
            awaitQuietly(finishTheEdit);
            staleCopy.setName(RENAMED);
            staleCopy.setAmiConfiguration(withAmi(staleCopy.getAmiConfiguration()));
            return poolDao.update(staleCopy);
        }));
        assertThat(editHoldsThePool.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

        final Future<?> activation = executor.submit(() -> transaction.execute(status ->
                poolManager.applyReservationState(poolId, pool -> {
                    pool.setCount(RESERVED_COUNT);
                    pool.setAmiConfiguration(withZone(pool.getAmiConfiguration()));
                })));

        assertStillBlocked(activation);
        finishTheEdit.countDown();
        edit.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        activation.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        final NodePool pool = poolDao.find(poolId).orElseThrow(AssertionError::new);
        assertThat(pool.getCount()).isEqualTo(RESERVED_COUNT);
        assertThat(pool.getName()).isEqualTo(RENAMED);
        assertThat(pool.getAmiConfiguration().getAmi()).isEqualTo(EDITED_AMI);
        assertThat(pool.getAmiConfiguration().getAvailabilityZone()).isEqualTo(RESERVATION_ZONE);
    }

    /**
     * The reverse: activation is in progress, and an edit must read the pool only once activation has committed,
     * so what it merges with and writes back is the activated pool.
     */
    @Test
    public void shouldReadThePoolForAnEditOnlyOnceAConcurrentWriterHasCommitted() throws Exception {
        final CountDownLatch activationHoldsThePool = new CountDownLatch(1);
        final CountDownLatch finishTheActivation = new CountDownLatch(1);
        final Future<?> activation = executor.submit(() -> transaction.execute(status ->
                poolManager.applyReservationState(poolId, pool -> {
                    activationHoldsThePool.countDown();
                    awaitQuietly(finishTheActivation);
                    pool.setCount(RESERVED_COUNT);
                })));
        assertThat(activationHoldsThePool.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

        final Future<NodePool> edit = executor.submit(() -> poolManager.update(poolId, editWithCount(0)));

        assertStillBlocked(edit);
        finishTheActivation.countDown();
        activation.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        // An ordinary pool takes its count from the edit, so what matters is that the edit ran after activation
        // committed - it is what the edit overwrote, rather than what overwrote the edit.
        assertThat(edit.get(TIMEOUT_SECONDS, TimeUnit.SECONDS).getCount()).isZero();
    }

    private static AMIConfiguration withAmi(final AMIConfiguration current) {
        final AMIConfiguration configuration = Optional.ofNullable(current).orElseGet(AMIConfiguration::new);
        configuration.setAmi(EDITED_AMI);
        return configuration;
    }

    private static AMIConfiguration withZone(final AMIConfiguration current) {
        final AMIConfiguration configuration = Optional.ofNullable(current).orElseGet(AMIConfiguration::new);
        configuration.setAvailabilityZone(RESERVATION_ZONE);
        return configuration;
    }

    private NodePoolVO editWithCount(final int count) {
        final NodePool pool = NodePoolCreatorUtils.getPoolWithoutSchedule();
        final NodePoolVO vo = new NodePoolVO();
        vo.setName(RENAMED);
        vo.setRegionId(pool.getRegionId());
        vo.setInstanceType(pool.getInstanceType());
        vo.setInstanceDisk(pool.getInstanceDisk());
        vo.setPriceType(PriceType.ON_DEMAND);
        vo.setCount(count);
        return vo;
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
