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
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityBlockExtension;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityBlockExtensionStatus;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservation;
import com.epam.pipeline.entity.cluster.pool.NodePool;
import com.epam.pipeline.test.creator.cluster.capacityreservation.CapacityBlockExtensionCreatorUtils;
import com.epam.pipeline.test.creator.cluster.capacityreservation.CapacityReservationCreatorUtils;
import com.epam.pipeline.test.creator.cluster.pool.NodePoolCreatorUtils;
import com.epam.pipeline.test.jdbc.AbstractJdbcTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
public class CapacityBlockExtensionDaoTest extends AbstractJdbcTest {

    @Autowired
    private CapacityBlockExtensionDao extensionDao;

    @Autowired
    private CapacityReservationDao reservationDao;

    @Autowired
    private NodePoolDao poolDao;

    @Test
    public void shouldCreateAndLoadExtension() {
        final Long reservationId = createReservation().getId();
        final CapacityBlockExtension created =
                extensionDao.create(CapacityBlockExtensionCreatorUtils.getExtension(reservationId));

        assertThat(created.getId()).isNotNull();
        assertFound(extensionDao.find(created.getId()), created);
    }

    @Test
    public void shouldPersistTheOriginIdOfARetry() {
        final Long reservationId = createReservation().getId();
        final CapacityBlockExtension origin = extensionDao.create(
                CapacityBlockExtensionCreatorUtils.getExtension(reservationId));
        final CapacityBlockExtension retry = extensionDao.create(CapacityBlockExtensionCreatorUtils
                .getExtension(reservationId).toBuilder().originId(origin.getId()).build());

        assertFound(extensionDao.find(retry.getId()), retry);
    }

    @Test
    public void shouldUpdateExtension() {
        final Long reservationId = createReservation().getId();
        final CapacityBlockExtension created =
                extensionDao.create(CapacityBlockExtensionCreatorUtils.getExtension(reservationId));

        created.setStatus(CapacityBlockExtensionStatus.FAILED);
        created.setStatusReason(CapacityBlockExtensionCreatorUtils.STATUS_REASON);
        created.setUpdated(created.getUpdated().plusMinutes(1));
        created.setDurationHours(created.getDurationHours() * 2);
        created.setStartDate(created.getCreated());
        created.setEndDate(created.getCreated().plusHours(created.getDurationHours()));
        extensionDao.update(created);

        assertFound(extensionDao.find(created.getId()), created);
    }

    @Test
    public void shouldLoadExtensionsByReservationId() {
        final Long reservationId = createReservation().getId();
        final CapacityBlockExtension first =
                extensionDao.create(CapacityBlockExtensionCreatorUtils.getExtension(reservationId));
        final CapacityBlockExtension second =
                extensionDao.create(CapacityBlockExtensionCreatorUtils.getExtension(reservationId));
        final Long otherReservationId = createReservation().getId();
        extensionDao.create(CapacityBlockExtensionCreatorUtils.getExtension(otherReservationId));

        assertThat(extensionDao.loadByReservationId(reservationId)).containsExactly(first, second);
    }

    private static void assertFound(final Optional<CapacityBlockExtension> actual,
                                     final CapacityBlockExtension expected) {
        assertThat(actual.isPresent()).isTrue();
        assertThat(actual.get()).isEqualTo(expected);
    }

    private CapacityReservation createReservation() {
        final NodePool pool = poolDao.create(NodePoolCreatorUtils.getPoolWithoutSchedule());
        return reservationDao.create(CapacityReservationCreatorUtils.getCapacityBlockReservation(pool.getId()));
    }
}
