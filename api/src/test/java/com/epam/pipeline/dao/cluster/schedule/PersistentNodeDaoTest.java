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

package com.epam.pipeline.dao.cluster.schedule;

import com.epam.pipeline.AbstractSpringTest;
import com.epam.pipeline.entity.cluster.schedule.NodeSchedule;
import com.epam.pipeline.entity.cluster.schedule.PersistentNode;
import com.epam.pipeline.test.creator.cluster.schedule.NodeScheduleCreatorUtils;
import com.epam.pipeline.test.creator.cluster.schedule.PersistentNodeCreatorUtils;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;


@Transactional
public class PersistentNodeDaoTest extends AbstractSpringTest {

    private static final String NEW_VALUE = "new";

    @Autowired
    private PersistentNodeDao nodeDao;

    @Autowired
    private NodeScheduleDao scheduleDao;

    @Test
    public void shouldCreateAndLoadNewNodeWithoutSchedule() {
        final PersistentNode node = PersistentNodeCreatorUtils.getNodeWithoutSchedule();
        final PersistentNode created = nodeDao.create(node);
        assertThat(created.getId()).isNotNull();
        findAndAssertNode(created);
    }

    @Test
    public void shouldCreateAndLoadNewNodeWithSchedule() {
        final PersistentNode created = createNodeWithSchedule();
        assertThat(created.getId()).isNotNull();
        findAndAssertNode(created);
    }

    @Test
    public void shouldUpdateNode() {
        final PersistentNode node = PersistentNodeCreatorUtils.getNodeWithoutSchedule();
        final PersistentNode created = nodeDao.create(node);
        created.setCount(2);
        created.setName(NEW_VALUE);
        nodeDao.update(created);
        findAndAssertNode(created);
    }

    @Test
    public void shouldDeleteNode() {
        final PersistentNode node = PersistentNodeCreatorUtils.getNodeWithoutSchedule();
        final PersistentNode created = nodeDao.create(node);
        nodeDao.delete(created.getId());
        assertThat(nodeDao.find(created.getId()).isPresent()).isFalse();
        assertThat(nodeDao.loadAll()).isEmpty();
    }

    @Test
    public void shouldLoadAllNodes() {
        final PersistentNode first = createNodeWithSchedule();
        final PersistentNode second = createNodeWithSchedule();
        final List<PersistentNode> nodes = nodeDao.loadAll();
        assertThat(nodes).containsOnly(first, second);
    }

    private void findAndAssertNode(final PersistentNode expected) {
        final Optional<PersistentNode> loaded = nodeDao.find(expected.getId());
        assertThat(loaded.isPresent()).isTrue();
        final PersistentNode actual = loaded.get();
        assertThat(actual).isEqualTo(expected);
    }

    private PersistentNode createNodeWithSchedule() {
        final NodeSchedule schedule = NodeScheduleCreatorUtils.getWorkDaySchedule();
        schedule.getScheduleEntries()
                .add(NodeScheduleCreatorUtils.createScheduleEntry(
                        DayOfWeek.SUNDAY, NodeScheduleCreatorUtils.TEN_AM,
                        DayOfWeek.SUNDAY, NodeScheduleCreatorUtils.SIX_PM));
        final NodeSchedule dbSchedule = scheduleDao.create(schedule);
        final PersistentNode node = PersistentNodeCreatorUtils.getNodeWithoutSchedule();
        node.setSchedule(dbSchedule);
        return nodeDao.create(node);
    }
}
