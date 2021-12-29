/*
 * Copyright 2021 EPAM Systems, Inc. (https://www.epam.com/)
 *
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

package com.epam.pipeline.monitor.repo.user;

import com.epam.pipeline.entity.user.OnlineUsersEntity;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.monitor.repo.AbstractJpaTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class OnlineUsersRepositoryTest extends AbstractJpaTest {

    @Autowired
    private OnlineUsersRepository repository;
    @Autowired
    private TestEntityManager entityManager;

    @Test
    void shouldSaveOnlineUsers() {
        final OnlineUsersEntity entity = saveEntity(DateUtils.nowUTC());

        repository.save(entity);
        assertNotNull(entity.getId());

        entityManager.flush();
        entityManager.clear();

        assertNotNull(repository.findById(entity.getId()));
    }

    @Test
    void shouldDeleteExpiredUsersUsage() {
        saveEntity(DateUtils.nowUTC().minusDays(2));
        saveEntity(DateUtils.nowUTC().minusDays(3));
        final OnlineUsersEntity activeEntity = saveEntity(DateUtils.nowUTC());

        entityManager.flush();
        entityManager.clear();

        repository.deleteAllByLogDateLessThan(DateUtils.nowUTC().minusDays(1));

        entityManager.flush();
        entityManager.clear();

        final List<OnlineUsersEntity> result = StreamSupport.stream(repository.findAll().spliterator(), false)
                .collect(Collectors.toList());
        assertThat(result)
                .hasSize(1)
                .contains(activeEntity);
    }

    private OnlineUsersEntity saveEntity(final LocalDateTime date) {
        final OnlineUsersEntity entity = new OnlineUsersEntity();
        entity.setUserIds(Arrays.asList(1L, 2L));
        entity.setLogDate(date);

        repository.save(entity);
        return entity;
    }
}
