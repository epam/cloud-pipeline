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
import com.epam.pipeline.entity.cluster.schedule.PersistentNode;
import com.epam.pipeline.test.creator.cluster.schedule.PersistentNodeCreatorUtils;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
public class PersistentNodeDaoTest extends AbstractSpringTest {

    @Autowired
    private PersistentNodeDao nodeDao;

    @Test
    public void shouldCreateAndLoadNewNode() {
        final PersistentNode node = PersistentNodeCreatorUtils.getNodeWithoutSchedule();
        final PersistentNode created = nodeDao.create(node);
        assertThat(created.getId()).isNotNull();
        final Optional<PersistentNode> loaded = nodeDao.find(created.getId());
        assertThat(loaded.isPresent()).isTrue();
        final PersistentNode actual = loaded.get();
        assertThat(actual).isEqualTo(created);
    }

}