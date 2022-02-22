/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.repository.cluster.pool;

import com.epam.pipeline.entity.cluster.pool.NodePoolUsageEntity;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.test.repository.AbstractJpaTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertNotNull;

public class NodePoolUsageRepositoryTest extends AbstractJpaTest {
    private static final Long POOL_ID = 1L;
    private static final Integer NODES_COUNT = 1;

    @Autowired
    private NodePoolUsageRepository repository;
    @Autowired
    private TestEntityManager entityManager;

    @Test
    @Transactional
    public void shouldSaveEntity() {
        final NodePoolUsageEntity entity = saveEntity(DateUtils.nowUTC());

        repository.save(entity);
        assertNotNull(entity.getId());

        entityManager.flush();
        entityManager.clear();

        assertNotNull(repository.findOne(entity.getId()));
    }

    @Test
    @Transactional
    public void shouldDeleteExpiredEntities() {
        saveEntity(DateUtils.nowUTC().minusDays(2));
        saveEntity(DateUtils.nowUTC().minusDays(3));
        final NodePoolUsageEntity activeEntity = saveEntity(DateUtils.nowUTC());

        entityManager.flush();
        entityManager.clear();

        repository.deleteByLogDateLessThan(DateUtils.nowUTC().minusDays(1));

        entityManager.flush();
        entityManager.clear();

        final List<Long> result = StreamSupport.stream(repository.findAll().spliterator(), false)
                .map(NodePoolUsageEntity::getId)
                .collect(Collectors.toList());
        assertThat(result)
                .hasSize(1)
                .contains(activeEntity.getId());
    }

    @Test
    @Transactional
    public void shouldFindEntitiesByPeriod() {
        saveEntity(DateUtils.nowUTC().minusDays(5));
        final NodePoolUsageEntity entity = saveEntity(DateUtils.nowUTC().minusDays(3));
        saveEntity(DateUtils.nowUTC().minusDays(1));

        entityManager.flush();
        entityManager.clear();

        final List<Long> result = repository.findByLogDateGreaterThanAndLogDateLessThan(
                DateUtils.nowUTC().minusDays(4),
                DateUtils.nowUTC().minusDays(2)).stream()
                .map(NodePoolUsageEntity::getId)
                .collect(Collectors.toList());

        assertThat(result)
                .hasSize(1)
                .contains(entity.getId());
    }


    private NodePoolUsageEntity saveEntity(final LocalDateTime date) {
        final NodePoolUsageEntity entity = new NodePoolUsageEntity();
        entity.setLogDate(date);
        entity.setNodePoolId(POOL_ID);
        entity.setOccupiedNodesCount(NODES_COUNT);
        entity.setTotalNodesCount(NODES_COUNT);
        repository.save(entity);
        return entity;
    }
}
