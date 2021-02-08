/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.dao.cluster.pool;

import com.epam.pipeline.AbstractSpringTest;
import com.epam.pipeline.entity.cluster.pool.NodePool;
import com.epam.pipeline.entity.cluster.pool.NodeSchedule;
import com.epam.pipeline.test.creator.cluster.pool.NodePoolCreatorUtils;
import com.epam.pipeline.test.creator.cluster.pool.NodeScheduleCreatorUtils;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
public class NodePoolDaoTest extends AbstractSpringTest {

    private static final String NEW_VALUE = "new";

    @Autowired
    private NodePoolDao poolDao;

    @Autowired
    private NodeScheduleDao scheduleDao;

    @Test
    public void shouldCreateAndLoadNewPoolWithoutSchedule() {
        final NodePool pool = NodePoolCreatorUtils.getPoolWithoutSchedule();
        final NodePool created = poolDao.create(pool);
        assertThat(created.getId()).isNotNull();
        findAndAssertPool(created);
    }

    @Test
    public void shouldCreateAndLoadNewPoolWithFilter() {
        final NodePool pool = NodePoolCreatorUtils.getPoolWithoutSchedule();
        pool.setFilter(NodePoolCreatorUtils.getAllFilters());
        final NodePool created = poolDao.create(pool);
        assertThat(created.getId()).isNotNull();
        findAndAssertPool(created);
    }

    @Test
    public void shouldCreateAndLoadNewPoolWithSchedule() {
        final NodePool created = createPoolWithSchedule();
        assertThat(created.getId()).isNotNull();
        findAndAssertPool(created);
    }

    @Test
    public void shouldUpdatePool() {
        final NodePool pool = NodePoolCreatorUtils.getPoolWithoutSchedule();
        final NodePool created = poolDao.create(pool);
        created.setCount(2);
        created.setName(NEW_VALUE);
        poolDao.update(created);
        findAndAssertPool(created);
    }

    @Test
    public void shouldDeletePool() {
        final NodePool pool = NodePoolCreatorUtils.getPoolWithoutSchedule();
        final NodePool created = poolDao.create(pool);
        poolDao.delete(created.getId());
        assertThat(poolDao.find(created.getId()).isPresent()).isFalse();
        assertThat(poolDao.loadAll()).isEmpty();
    }

    @Test
    public void shouldLoadAllPools() {
        final NodePool first = createPoolWithSchedule();
        final NodePool second = createPoolWithSchedule();
        final List<NodePool> pools = poolDao.loadAll();
        assertThat(pools).containsOnly(first, second);
    }

    private void findAndAssertPool(final NodePool expected) {
        final Optional<NodePool> loaded = poolDao.find(expected.getId());
        assertThat(loaded.isPresent()).isTrue();
        final NodePool actual = loaded.get();
        assertThat(actual).isEqualTo(expected);
    }

    private NodePool createPoolWithSchedule() {
        final NodeSchedule schedule = NodeScheduleCreatorUtils.getWorkDaySchedule();
        schedule.getScheduleEntries()
                .add(NodeScheduleCreatorUtils.createScheduleEntry(
                        DayOfWeek.SUNDAY, NodeScheduleCreatorUtils.TEN_AM,
                        DayOfWeek.SUNDAY, NodeScheduleCreatorUtils.SIX_PM));
        final NodeSchedule dbSchedule = scheduleDao.create(schedule);
        final NodePool pool = NodePoolCreatorUtils.getPoolWithoutSchedule();
        pool.setSchedule(dbSchedule);
        return poolDao.create(pool);
    }
}
