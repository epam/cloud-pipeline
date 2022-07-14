/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.repository.user;

import com.epam.pipeline.entity.user.OnlineUsersEntity;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.test.repository.AbstractJpaTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertNotNull;

public class OnlineUsersRepositoryTest extends AbstractJpaTest {

    @Autowired
    private OnlineUsersRepository repository;
    @Autowired
    private TestEntityManager entityManager;

    @Test
    @Transactional
    public void shouldSaveOnlineUsers() {
        final OnlineUsersEntity entity = saveEntity(DateUtils.nowUTC());

        repository.save(entity);
        assertNotNull(entity.getId());

        entityManager.flush();
        entityManager.clear();

        assertNotNull(repository.findOne(entity.getId()));
    }

    @Test
    @Transactional
    public void shouldDeleteExpiredUsersUsage() {
        saveEntity(DateUtils.nowUTC().minusDays(2));
        saveEntity(DateUtils.nowUTC().minusDays(3));
        final OnlineUsersEntity activeEntity = saveEntity(DateUtils.nowUTC());

        entityManager.flush();
        entityManager.clear();

        repository.deleteByLogDateLessThan(DateUtils.nowUTC().minusDays(1));

        entityManager.flush();
        entityManager.clear();

        final List<Long> result = StreamSupport.stream(repository.findAll().spliterator(), false)
                .map(OnlineUsersEntity::getId)
                .collect(Collectors.toList());
        assertThat(result)
                .hasSize(1)
                .contains(activeEntity.getId());
    }

    @Test
    @Transactional
    public void shouldFindUsersByPeriod() {
        saveEntity(DateUtils.nowUTC().minusDays(5));
        final OnlineUsersEntity entity = saveEntity(DateUtils.nowUTC().minusDays(3));
        saveEntity(DateUtils.nowUTC().minusDays(1));

        entityManager.flush();
        entityManager.clear();

        final List<Long> result = repository.findByLogDateGreaterThanAndLogDateLessThan(
                DateUtils.nowUTC().minusDays(4),
                DateUtils.nowUTC().minusDays(2)).stream()
                .map(OnlineUsersEntity::getId)
                .collect(Collectors.toList());

        assertThat(result)
                .hasSize(1)
                .contains(entity.getId());
    }

    @Test
    @Transactional
    public void shouldFindUsersByPeriodAndUsers() {
        saveEntity(DateUtils.nowUTC().minusDays(6));
        final List<Long> users = new ArrayList<>();
        users.add(1L);
        saveEntity(DateUtils.nowUTC().minusDays(4), users);
        final OnlineUsersEntity entity = saveEntity(DateUtils.nowUTC().minusDays(3));
        saveEntity(DateUtils.nowUTC().minusDays(1));

        entityManager.flush();
        entityManager.clear();

        final List<Long> result = repository.findByLogDateGreaterThanAndLogDateLessThanAndUserIdsIn(
                DateUtils.nowUTC().minusDays(5),
                DateUtils.nowUTC().minusDays(2),
                Collections.singleton(2L)).stream()
                .map(OnlineUsersEntity::getId)
                .collect(Collectors.toList());

        assertThat(result)
                .hasSize(1)
                .contains(entity.getId());
    }

    private OnlineUsersEntity saveEntity(final LocalDateTime date) {
        final List<Long> userIds = new ArrayList<>();
        userIds.add(1L);
        userIds.add(2L);
        return saveEntity(date, userIds);
    }

    private OnlineUsersEntity saveEntity(final LocalDateTime date, final List<Long> users) {
        final OnlineUsersEntity entity = new OnlineUsersEntity();
        entity.setUserIds(users);
        entity.setLogDate(date);
        repository.save(entity);
        return entity;
    }
}
