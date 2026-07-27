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

package com.epam.pipeline.repository.credits;

import com.epam.pipeline.dao.user.UserDao;
import com.epam.pipeline.dto.credits.PlatformUsageCreditsUserBalanceFilterVO;
import com.epam.pipeline.entity.credits.PlatformUsageCreditsUserBalanceEntity;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.test.repository.AbstractJpaTest;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.epam.pipeline.test.creator.user.UserCreatorUtils.getPipelineUser;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

@Transactional
public class PlatformUsageCreditsUserBalanceRepositoryTest extends AbstractJpaTest {

    private static final String USER1 = "BALANCE_USER1";
    private static final String USER2 = "BALANCE_USER2";
    private static final int VALUE_HIGH = 2000;
    private static final int VALUE_LOW = 500;
    private static final int RESET_VALUE = 1500;
    private static final int MIN_BALANCE = 24;
    private static final int MAX_BALANCE = 3000;
    private static final int DEFAULT_BALANCE = 2000;
    private static final int DELTA = 100;
    private static final int BALANCE_NEAR_MAX = 2950;
    private static final int BALANCE_NEAR_MIN = 50;

    @Autowired
    private PlatformUsageCreditsUserBalanceRepository repository;

    @Autowired
    private UserDao userDao;

    @Autowired
    private TestEntityManager entityManager;

    private PipelineUser user1;
    private PipelineUser user2;

    @Before
    public void setUp() {
        user1 = userDao.createUser(getPipelineUser(USER1), Collections.emptyList());
        user2 = userDao.createUser(getPipelineUser(USER2), Collections.emptyList());
    }

    @Test
    public void shouldSaveAndFindByUserId() {
        final PlatformUsageCreditsUserBalanceEntity entity = entity(user1.getId(), VALUE_HIGH);
        repository.save(entity);

        entityManager.flush();
        entityManager.clear();

        final Optional<PlatformUsageCreditsUserBalanceEntity> loaded = repository.findByUserId(user1.getId());
        assertThat(loaded.isPresent(), is(true));
        assertThat(loaded.get().getUserId(), is(user1.getId()));
        assertThat(loaded.get().getCurrentValue(), is(VALUE_HIGH));
        assertThat(loaded.get().getModifiedDate(), notNullValue());
    }

    @Test
    public void shouldReturnEmptyWhenUserHasNoBalance() {
        assertThat(repository.findByUserId(user1.getId()).isPresent(), is(false));
    }

    @Test
    public void shouldUpdateBalance() {
        final PlatformUsageCreditsUserBalanceEntity entity = entity(user1.getId(), VALUE_LOW);
        repository.save(entity);
        entityManager.flush();
        entityManager.clear();

        final PlatformUsageCreditsUserBalanceEntity loaded = repository.findByUserId(user1.getId()).get();
        loaded.setCurrentValue(VALUE_HIGH);
        repository.save(loaded);
        entityManager.flush();
        entityManager.clear();

        assertThat(repository.findByUserId(user1.getId()).get().getCurrentValue(), is(VALUE_HIGH));
    }

    @Test
    public void shouldDeleteBalance() {
        final PlatformUsageCreditsUserBalanceEntity entity = entity(user1.getId(), VALUE_HIGH);
        repository.save(entity);
        entityManager.flush();
        entityManager.clear();

        repository.delete(entity.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(repository.findByUserId(user1.getId()).isPresent(), is(false));
    }

    @Test
    public void shouldFilterByUserIds() {
        repository.save(entity(user1.getId(), VALUE_HIGH));
        repository.save(entity(user2.getId(), VALUE_LOW));
        entityManager.flush();
        entityManager.clear();

        final PlatformUsageCreditsUserBalanceFilterVO filter = filterVO(
                Collections.singletonList(user1.getId()), null, null);
        final Page<PlatformUsageCreditsUserBalanceEntity> page =
                repository.findAll(PlatformUsageCreditsUserBalanceSpecification.build(filter),
                        new PageRequest(0, 10));

        assertThat(page.getTotalElements(), is(1L));
        assertThat(page.getContent().get(0).getUserId(), is(user1.getId()));
    }

    @Test
    public void shouldFilterByBalanceGreaterThan() {
        repository.save(entity(user1.getId(), VALUE_HIGH));
        repository.save(entity(user2.getId(), VALUE_LOW));
        entityManager.flush();
        entityManager.clear();

        final PlatformUsageCreditsUserBalanceFilterVO filter = filterVO(null, VALUE_LOW, ">");
        final Page<PlatformUsageCreditsUserBalanceEntity> page =
                repository.findAll(PlatformUsageCreditsUserBalanceSpecification.build(filter),
                        new PageRequest(0, 10));

        assertThat(page.getTotalElements(), is(1L));
        assertThat(page.getContent().get(0).getCurrentValue(), is(VALUE_HIGH));
    }

    @Test
    public void shouldFilterByBalanceLessThan() {
        repository.save(entity(user1.getId(), VALUE_HIGH));
        repository.save(entity(user2.getId(), VALUE_LOW));
        entityManager.flush();
        entityManager.clear();

        final PlatformUsageCreditsUserBalanceFilterVO filter = filterVO(null, VALUE_HIGH, "<");
        final Page<PlatformUsageCreditsUserBalanceEntity> page =
                repository.findAll(PlatformUsageCreditsUserBalanceSpecification.build(filter),
                        new PageRequest(0, 10));

        assertThat(page.getTotalElements(), is(1L));
        assertThat(page.getContent().get(0).getCurrentValue(), is(VALUE_LOW));
    }

    @Test
    public void shouldFilterByBalanceEqualTo() {
        repository.save(entity(user1.getId(), VALUE_HIGH));
        repository.save(entity(user2.getId(), VALUE_LOW));
        entityManager.flush();
        entityManager.clear();

        final PlatformUsageCreditsUserBalanceFilterVO filter = filterVO(null, VALUE_HIGH, "=");
        final Page<PlatformUsageCreditsUserBalanceEntity> page =
                repository.findAll(PlatformUsageCreditsUserBalanceSpecification.build(filter),
                        new PageRequest(0, 10));

        assertThat(page.getTotalElements(), is(1L));
        assertThat(page.getContent().get(0).getCurrentValue(), is(VALUE_HIGH));
    }

    @Test
    public void shouldCombineUserIdsAndBalanceFilter() {
        repository.save(entity(user1.getId(), VALUE_HIGH));
        repository.save(entity(user2.getId(), VALUE_HIGH));
        entityManager.flush();
        entityManager.clear();

        final PlatformUsageCreditsUserBalanceFilterVO filter = filterVO(
                Collections.singletonList(user1.getId()), VALUE_LOW, ">");
        final Page<PlatformUsageCreditsUserBalanceEntity> page =
                repository.findAll(PlatformUsageCreditsUserBalanceSpecification.build(filter),
                        new PageRequest(0, 10));

        assertThat(page.getTotalElements(), is(1L));
        assertThat(page.getContent().get(0).getUserId(), is(user1.getId()));
    }

    @Test
    public void shouldReturnAllWhenNoFiltersApplied() {
        repository.save(entity(user1.getId(), VALUE_HIGH));
        repository.save(entity(user2.getId(), VALUE_LOW));
        entityManager.flush();
        entityManager.clear();

        final PlatformUsageCreditsUserBalanceFilterVO filter = filterVO(null, null, null);
        final Page<PlatformUsageCreditsUserBalanceEntity> page =
                repository.findAll(PlatformUsageCreditsUserBalanceSpecification.build(filter),
                        new PageRequest(0, 10));

        assertThat(page.getTotalElements(), is(2L));
    }

    @Test
    public void shouldResetAll() {
        repository.save(entity(user1.getId(), VALUE_HIGH));
        repository.save(entity(user2.getId(), VALUE_LOW));
        entityManager.flush();
        entityManager.clear();

        repository.resetAll(RESET_VALUE);
        entityManager.flush();
        entityManager.clear();

        assertThat(repository.findByUserId(user1.getId()).get().getCurrentValue(), is(RESET_VALUE));
        assertThat(repository.findByUserId(user2.getId()).get().getCurrentValue(), is(RESET_VALUE));
    }

    @Test
    public void shouldCreateMissingBalanceRowsOnResetAll() {
        repository.save(entity(user1.getId(), VALUE_HIGH));
        // user2 has no balance row
        entityManager.flush();
        entityManager.clear();

        repository.resetAll(RESET_VALUE);
        entityManager.flush();
        entityManager.clear();

        assertThat(repository.findByUserId(user1.getId()).get().getCurrentValue(), is(RESET_VALUE));
        assertThat(repository.findByUserId(user2.getId()).get().getCurrentValue(), is(RESET_VALUE));
    }

    @Test
    public void shouldSupportPaging() {
        repository.save(entity(user1.getId(), VALUE_HIGH));
        repository.save(entity(user2.getId(), VALUE_LOW));
        entityManager.flush();
        entityManager.clear();

        final PlatformUsageCreditsUserBalanceFilterVO filter = filterVO(null, null, null);
        final Page<PlatformUsageCreditsUserBalanceEntity> firstPage =
                repository.findAll(PlatformUsageCreditsUserBalanceSpecification.build(filter),
                        new PageRequest(0, 1));

        assertThat(firstPage.getTotalElements(), is(2L));
        assertThat(firstPage.getContent().size(), is(1));

        final Page<PlatformUsageCreditsUserBalanceEntity> secondPage =
                repository.findAll(PlatformUsageCreditsUserBalanceSpecification.build(filter),
                        new PageRequest(1, 1));

        assertThat(secondPage.getContent().size(), is(1));
    }

    @Test
    public void shouldFilterMultipleUserIds() {
        repository.save(entity(user1.getId(), VALUE_HIGH));
        repository.save(entity(user2.getId(), VALUE_LOW));
        entityManager.flush();
        entityManager.clear();

        final PlatformUsageCreditsUserBalanceFilterVO filter = filterVO(
                Arrays.asList(user1.getId(), user2.getId()), null, null);
        final Page<PlatformUsageCreditsUserBalanceEntity> page =
                repository.findAll(PlatformUsageCreditsUserBalanceSpecification.build(filter),
                        new PageRequest(0, 10));

        assertThat(page.getTotalElements(), is(2L));
    }

    @Test
    public void shouldIncreaseBalanceAtomicallyAndReturnNewValue() {
        repository.save(entity(user1.getId(), VALUE_HIGH));
        entityManager.flush();
        entityManager.clear();

        final Object[] result = repository.atomicUpdateBalance(
                user1.getId(), DELTA, DEFAULT_BALANCE, MIN_BALANCE, MAX_BALANCE).get(0);
        entityManager.flush();
        entityManager.clear();

        assertThat(((Number) result[0]).intValue(), is(VALUE_HIGH + DELTA));
        assertThat(((Number) result[1]).intValue(), is(DELTA));
        assertThat(repository.findByUserId(user1.getId()).get().getCurrentValue(), is(VALUE_HIGH + DELTA));
    }

    @Test
    public void shouldDeductBalanceAtomicallyAndReturnNewValue() {
        repository.save(entity(user1.getId(), VALUE_HIGH));
        entityManager.flush();
        entityManager.clear();

        final Object[] result = repository.atomicUpdateBalance(
                user1.getId(), -DELTA, DEFAULT_BALANCE, MIN_BALANCE, MAX_BALANCE).get(0);
        entityManager.flush();
        entityManager.clear();

        assertThat(((Number) result[0]).intValue(), is(VALUE_HIGH - DELTA));
        assertThat(((Number) result[1]).intValue(), is(-DELTA));
        assertThat(repository.findByUserId(user1.getId()).get().getCurrentValue(), is(VALUE_HIGH - DELTA));
    }

    @Test
    public void shouldClampIncreaseToMax() {
        repository.save(entity(user1.getId(), BALANCE_NEAR_MAX));
        entityManager.flush();
        entityManager.clear();

        final Object[] result = repository.atomicUpdateBalance(
                user1.getId(), DELTA * 2, DEFAULT_BALANCE, MIN_BALANCE, MAX_BALANCE).get(0);
        entityManager.flush();
        entityManager.clear();

        assertThat(((Number) result[0]).intValue(), is(MAX_BALANCE));
        assertThat(((Number) result[1]).intValue(), is(MAX_BALANCE - BALANCE_NEAR_MAX));
        assertThat(repository.findByUserId(user1.getId()).get().getCurrentValue(), is(MAX_BALANCE));
    }

    @Test
    public void shouldClampDeductionToMin() {
        repository.save(entity(user1.getId(), BALANCE_NEAR_MIN));
        entityManager.flush();
        entityManager.clear();

        final Object[] result = repository.atomicUpdateBalance(
                user1.getId(), -DELTA, DEFAULT_BALANCE, MIN_BALANCE, MAX_BALANCE).get(0);
        entityManager.flush();
        entityManager.clear();

        assertThat(((Number) result[0]).intValue(), is(MIN_BALANCE));
        assertThat(((Number) result[1]).intValue(), is(MIN_BALANCE - BALANCE_NEAR_MIN));
        assertThat(repository.findByUserId(user1.getId()).get().getCurrentValue(), is(MIN_BALANCE));
    }

    @Test
    public void shouldCreateRowFromDefaultBalanceWhenNoRowExists() {
        // user1 has no balance row
        final Object[] result = repository.atomicUpdateBalance(
                user1.getId(), DELTA, DEFAULT_BALANCE, MIN_BALANCE, MAX_BALANCE).get(0);
        entityManager.flush();
        entityManager.clear();

        assertThat(((Number) result[0]).intValue(), is(DEFAULT_BALANCE + DELTA));
        assertThat(((Number) result[1]).intValue(), is(DELTA));
        assertThat(repository.findByUserId(user1.getId()).get().getCurrentValue(), is(DEFAULT_BALANCE + DELTA));
    }

    @Test
    public void shouldClampDefaultPlusDeltaToMaxOnFirstInsert() {
        // no row; default + delta would exceed max
        final int highDefault = MAX_BALANCE - DELTA / 2;
        final Object[] result = repository.atomicUpdateBalance(
                user1.getId(), DELTA, highDefault, MIN_BALANCE, MAX_BALANCE).get(0);
        entityManager.flush();
        entityManager.clear();

        assertThat(((Number) result[0]).intValue(), is(MAX_BALANCE));
        assertThat(((Number) result[1]).intValue(), is(MAX_BALANCE - highDefault));
        assertThat(repository.findByUserId(user1.getId()).get().getCurrentValue(), is(MAX_BALANCE));
    }

    private static PlatformUsageCreditsUserBalanceEntity entity(final Long userId, final int value) {
        return PlatformUsageCreditsUserBalanceEntity.builder()
                .userId(userId)
                .currentValue(value)
                .modifiedDate(DateUtils.nowUTC())
                .build();
    }

    private static PlatformUsageCreditsUserBalanceFilterVO filterVO(
            final List<Long> userIds, final Integer value, final String operation) {
        return PlatformUsageCreditsUserBalanceFilterVO.builder()
                .userIds(userIds)
                .value(value)
                .operation(operation)
                .page(0)
                .pageSize(10)
                .build();
    }
}
