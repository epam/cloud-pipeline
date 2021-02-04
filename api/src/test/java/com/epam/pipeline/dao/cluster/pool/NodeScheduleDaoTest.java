/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.entity.cluster.pool.NodeSchedule;
import com.epam.pipeline.entity.cluster.pool.ScheduleEntry;
import com.epam.pipeline.test.creator.cluster.schedule.NodeScheduleCreatorUtils;
import com.epam.pipeline.test.jdbc.AbstractJdbcTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
public class NodeScheduleDaoTest extends AbstractJdbcTest {

    public static final String NEW_NAME = "New";

    @Autowired
    private NodeScheduleDao nodeScheduleDao;

    @Test
    public void shouldCreateAndLoadNewSchedule() {
        final NodeSchedule newSchedule = NodeScheduleCreatorUtils.getWorkDaySchedule();
        final NodeSchedule created = nodeScheduleDao.create(newSchedule);
        assertThat(created.getId()).isNotNull();
        final Optional<NodeSchedule> loaded = nodeScheduleDao.find(created.getId());
        assertThat(loaded.isPresent()).isTrue();
        final NodeSchedule actual = loaded.get();
        assertThat(actual.getName()).isEqualTo(newSchedule.getName());
        assertThat(actual.getCreated()).isNotNull();
        assertThat(actual.getScheduleEntries()).containsExactly(newSchedule.getScheduleEntries().get(0));
    }

    @Test
    public void shouldUpdateScheduleName() {
        final NodeSchedule newSchedule = NodeScheduleCreatorUtils.getWorkDaySchedule();
        final NodeSchedule created = nodeScheduleDao.create(newSchedule);
        created.setName(NEW_NAME);
        nodeScheduleDao.update(created);

        final Optional<NodeSchedule> loaded = nodeScheduleDao.find(created.getId());
        assertThat(loaded.isPresent()).isTrue();
        final NodeSchedule actual = loaded.get();
        assertThat(actual.getName()).isEqualTo(NEW_NAME);
        assertThat(actual.getScheduleEntries()).containsExactly(newSchedule.getScheduleEntries().get(0));
    }

    @Test
    public void shouldUpdateScheduleEntries() {
        final NodeSchedule newSchedule = NodeScheduleCreatorUtils.getWorkDaySchedule();
        final NodeSchedule created = nodeScheduleDao.create(newSchedule);

        final ScheduleEntry workEntry = newSchedule.getScheduleEntries().get(0);
        final ScheduleEntry weekendEntry = NodeScheduleCreatorUtils.createScheduleEntry(
                DayOfWeek.SATURDAY, NodeScheduleCreatorUtils.TEN_AM,
                DayOfWeek.SUNDAY, NodeScheduleCreatorUtils.SIX_PM);
        created.getScheduleEntries().add(weekendEntry);
        nodeScheduleDao.update(created);

        final Optional<NodeSchedule> loaded = nodeScheduleDao.find(created.getId());
        assertThat(loaded.isPresent()).isTrue();
        final NodeSchedule actual = loaded.get();
        assertThat(actual.getScheduleEntries()).containsOnly(weekendEntry, workEntry);

    }

    @Test
    public void shouldDeleteSchedule() {
        final NodeSchedule created = nodeScheduleDao.create(NodeScheduleCreatorUtils.getWorkDaySchedule());
        nodeScheduleDao.delete(created.getId());
        assertThat(nodeScheduleDao.find(created.getId()).isPresent()).isFalse();
        assertThat(nodeScheduleDao.loadAll()).isEmpty();
    }

    @Test
    public void shouldLoadAllSchedules() {
        final NodeSchedule first = nodeScheduleDao.create(NodeScheduleCreatorUtils.getWorkDaySchedule());
        final NodeSchedule second = nodeScheduleDao.create(NodeScheduleCreatorUtils.getWorkDaySchedule());
        assertThat(nodeScheduleDao.loadAll()).containsOnly(first, second);
    }

}
