/*
 * Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.repository.auth;

import com.epam.pipeline.entity.access.AccessCodeEntity;
import com.epam.pipeline.repository.access.AccessCodeRepository;
import com.epam.pipeline.test.repository.AbstractJpaTest;
import com.nimbusds.oauth2.sdk.pkce.CodeChallengeMethod;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class AccessCodeRepositoryTest extends AbstractJpaTest {
    private static final String USER_NAME = "TEST_USER";
    private static final int EXPIRED_MINUTES_1 = 15;
    private static final int EXPIRED_MINUTES_2 = 16;
    private static final int ACTIVE_MINUTES = 5;
    private static final int THRESHOLD = 10;

    @Autowired
    private AccessCodeRepository repository;
    @Autowired
    private TestEntityManager entityManager;

    @Test
    @Transactional
    public void shouldLoadByCode() {
        final String code = repository.save(entity(LocalDateTime.now())).getCode();

        entityManager.clear();

        assertThat(repository.findByCode(code).isPresent(), is(true));
    }

    @Test
    @Transactional
    public void shouldLoadByCodeChallenge() {
        final String codeChallenge = repository.save(entity(LocalDateTime.now())).getCodeChallenge();

        entityManager.clear();

        assertThat(repository.findByCodeChallenge(codeChallenge).isPresent(), is(true));
    }

    @Test
    @Transactional
    public void shouldDeleteExpiredCodes() {
        final Long expired1 = repository.save(entity(LocalDateTime.now().minusMinutes(EXPIRED_MINUTES_1))).getId();
        final Long expired2 = repository.save(entity(LocalDateTime.now().minusMinutes(EXPIRED_MINUTES_2))).getId();
        final Long active = repository.save(entity(LocalDateTime.now().minusMinutes(ACTIVE_MINUTES))).getId();
        repository.deleteExpired(THRESHOLD);

        entityManager.clear();

        final Iterable<AccessCodeEntity> all = repository.findAllById(Arrays.asList(expired1, expired2, active));
        assertThat(StreamSupport.stream(all.spliterator(), false).count(), is(1L));
    }

    private AccessCodeEntity entity(final LocalDateTime timestamp) {
        return AccessCodeEntity.builder()
                .code(UUID.randomUUID().toString())
                .codeChallenge(UUID.randomUUID().toString())
                .codeChallengeMethod(CodeChallengeMethod.PLAIN.getValue())
                .issued(false)
                .userName(USER_NAME)
                .created(timestamp)
                .build();
    }
}
