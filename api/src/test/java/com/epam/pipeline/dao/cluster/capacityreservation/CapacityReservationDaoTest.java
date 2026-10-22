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

package com.epam.pipeline.dao.cluster.capacityreservation;

import com.epam.pipeline.dao.cluster.pool.NodePoolDao;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservation;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservationStatus;
import com.epam.pipeline.entity.cluster.pool.NodePool;
import com.epam.pipeline.test.creator.cluster.capacityreservation.CapacityReservationCreatorUtils;
import com.epam.pipeline.test.creator.cluster.pool.NodePoolCreatorUtils;
import com.epam.pipeline.test.jdbc.AbstractJdbcTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
public class CapacityReservationDaoTest extends AbstractJdbcTest {

    private static final String ANOTHER_OWNER = "another-user";

    @Autowired
    private CapacityReservationDao reservationDao;

    @Autowired
    private NodePoolDao poolDao;

    @Test
    public void shouldCreateAndLoadReservation() {
        final CapacityReservation created = reservationDao.create(
                CapacityReservationCreatorUtils.getReservation(createPool().getId()));

        assertThat(created.getId()).isNotNull();
        assertFound(reservationDao.find(created.getId()), created);
    }

    @Test
    public void shouldRoundTripEveryColumn() {
        final CapacityReservation origin = reservationDao.create(
                CapacityReservationCreatorUtils.getReservation(createPool().getId()));
        final CapacityReservation created = reservationDao.create(
                CapacityReservationCreatorUtils.getScheduledReservation(createPool().getId(),
                        origin.getId()));

        final Optional<CapacityReservation> loaded = reservationDao.find(created.getId());

        assertFound(loaded, created);
    }

    @Test
    public void shouldWriteOnlyWhatARetryRemembersWhileTheStatusIsTheExpectedOne() {
        final CapacityReservation created = reservationDao.create(
                CapacityReservationCreatorUtils.getReservation(createPool().getId()));
        final CapacityReservation retried = reservationDao.find(created.getId()).orElseThrow(AssertionError::new);
        retried.setStatusReason(CapacityReservationCreatorUtils.STATUS_REASON);
        retried.setUpdated(created.getUpdated().plusMinutes(1));
        retried.setStartDate(created.getRequestedStartDate());
        retried.setEndDate(created.getRequestedEndDate());
        retried.setAvailabilityZone(CapacityReservationCreatorUtils.AVAILABILITY_ZONE);
        retried.setName("not written by a retry");

        assertThat(reservationDao.updateRetry(retried)).isTrue();

        created.setStatusReason(retried.getStatusReason());
        created.setUpdated(retried.getUpdated());
        created.setStartDate(retried.getStartDate());
        created.setEndDate(retried.getEndDate());
        created.setAvailabilityZone(retried.getAvailabilityZone());
        assertFound(reservationDao.find(created.getId()), created);
    }

    @Test
    public void shouldNotWriteARetryOnceTheStatusHasMovedOn() {
        final CapacityReservation created = reservationDao.create(
                CapacityReservationCreatorUtils.getReservation(createPool().getId()));
        created.setStatus(CapacityReservationStatus.CANCELLED);
        reservationDao.update(created);
        final CapacityReservation staleCopy = reservationDao.find(created.getId()).orElseThrow(AssertionError::new);
        staleCopy.setStatus(CapacityReservationStatus.APPROVED);
        staleCopy.setStatusReason(CapacityReservationCreatorUtils.STATUS_REASON);
        staleCopy.setUpdated(created.getUpdated().plusMinutes(1));

        assertThat(reservationDao.updateRetry(staleCopy)).isFalse();

        assertFound(reservationDao.find(created.getId()), created);
    }

    @Test
    public void shouldUpdateReservation() {
        final CapacityReservation created = reservationDao.create(
                CapacityReservationCreatorUtils.getReservation(createPool().getId()));

        created.setName("renamed");
        created.setCloudReservationId(CapacityReservationCreatorUtils.CLOUD_RESERVATION_ID);
        created.setAvailabilityZone(CapacityReservationCreatorUtils.AVAILABILITY_ZONE);
        created.setClientToken(CapacityReservationCreatorUtils.CLIENT_TOKEN);
        created.setStatus(CapacityReservationStatus.APPROVED);
        created.setStatusReason(CapacityReservationCreatorUtils.STATUS_REASON);
        created.setUpdated(created.getUpdated().plusMinutes(1));
        created.setInstanceType("p5.4xlarge");
        created.setInstanceCount(created.getInstanceCount() + 1);
        created.setStartDate(created.getRequestedStartDate());
        created.setEndDate(created.getRequestedEndDate());
        created.setCommitmentDuration(created.getCommitmentDuration() + 1);
        created.setInstancePlatform("Windows");
        created.setAttempt(created.getAttempt() + 1);
        reservationDao.update(created);

        assertFound(reservationDao.find(created.getId()), created);
    }

    @Test
    public void shouldFindReservationByNodePoolId() {
        final NodePool pool = createPool();
        final CapacityReservation created = reservationDao.create(
                CapacityReservationCreatorUtils.getReservation(pool.getId()));

        assertFound(reservationDao.findByNodePoolId(pool.getId()), created);
    }

    @Test
    public void shouldNotFindReservationForPoolWithoutOne() {
        final NodePool pool = createPool();

        assertThat(reservationDao.findByNodePoolId(pool.getId()).isPresent()).isFalse();
    }

    @Test
    public void shouldRejectSecondReservationForSamePool() {
        final NodePool pool = createPool();
        reservationDao.create(CapacityReservationCreatorUtils.getReservation(pool.getId()));

        assertThatThrownBy(() -> reservationDao.create(
                CapacityReservationCreatorUtils.getReservation(pool.getId())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    public void shouldLoadReservationsByNodePoolIdsInOneQuery() {
        final CapacityReservation first = reservationDao.create(
                CapacityReservationCreatorUtils.getReservation(createPool().getId()));
        final CapacityReservation second = reservationDao.create(
                CapacityReservationCreatorUtils.getReservation(createPool().getId()));
        final NodePool poolWithoutReservation = createPool();

        final List<CapacityReservation> loaded = reservationDao.loadByNodePoolIds(
                Arrays.asList(first.getNodePoolId(), second.getNodePoolId(), poolWithoutReservation.getId()));

        assertThat(loaded).containsExactlyInAnyOrder(first, second);
    }

    @Test
    public void shouldReturnEmptyListForNoNodePoolIds() {
        assertThat(reservationDao.loadByNodePoolIds(Collections.emptyList())).isEmpty();
    }

    @Test
    public void shouldLoadReservationsByStatus() {
        final CapacityReservation pending = reservationDao.create(
                CapacityReservationCreatorUtils.getReservation(createPool().getId()));
        final CapacityReservation scheduled = reservationDao.create(
                CapacityReservationCreatorUtils.getScheduledReservation(createPool().getId()));

        assertThat(reservationDao.loadByStatus(CapacityReservationStatus.REQUIRED_APPROVE))
                .containsExactly(pending);
        assertThat(reservationDao.loadByStatus(CapacityReservationStatus.SCHEDULED))
                .containsExactly(scheduled);
        assertThat(reservationDao.loadByStatus(CapacityReservationStatus.ACTIVE)).isEmpty();
    }

    @Test
    public void shouldLoadReservationsByOwner() {
        final CapacityReservation owned = reservationDao.create(
                CapacityReservationCreatorUtils.getReservation(createPool().getId()));
        final CapacityReservation other = CapacityReservationCreatorUtils.getReservation(createPool().getId());
        other.setOwner(ANOTHER_OWNER);
        reservationDao.create(other);

        assertThat(reservationDao.loadByOwner(CapacityReservationCreatorUtils.OWNER))
                .containsExactly(owned);
    }

    @Test
    public void shouldLoadAllReservations() {
        final CapacityReservation first = reservationDao.create(
                CapacityReservationCreatorUtils.getReservation(createPool().getId()));
        final CapacityReservation second = reservationDao.create(
                CapacityReservationCreatorUtils.getReservation(createPool().getId()));

        assertThat(reservationDao.loadAll()).containsExactlyInAnyOrder(first, second);
    }

    @Test
    public void shouldDeleteReservation() {
        final CapacityReservation created = reservationDao.create(
                CapacityReservationCreatorUtils.getReservation(createPool().getId()));

        reservationDao.delete(created.getId());

        assertThat(reservationDao.find(created.getId()).isPresent()).isFalse();
    }

    @Test
    public void shouldNullOriginIdWhenOriginalIsDeleted() {
        final CapacityReservation original = reservationDao.create(
                CapacityReservationCreatorUtils.getReservation(createPool().getId()));
        final CapacityReservation retry = CapacityReservationCreatorUtils.getReservation(createPool().getId());
        retry.setOriginId(original.getId());
        final CapacityReservation createdRetry = reservationDao.create(retry);
        assertThat(createdRetry.getOriginId()).isEqualTo(original.getId());

        reservationDao.delete(original.getId());

        final Optional<CapacityReservation> reloaded = reservationDao.find(createdRetry.getId());
        assertThat(reloaded.isPresent()).isTrue();
        assertThat(reloaded.get().getOriginId()).isNull();
    }

    private static void assertFound(final Optional<CapacityReservation> actual,
                                    final CapacityReservation expected) {
        assertThat(actual.isPresent()).isTrue();
        assertThat(actual.get()).isEqualTo(expected);
    }

    private NodePool createPool() {
        return poolDao.create(NodePoolCreatorUtils.getPoolWithoutSchedule());
    }
}
