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

import com.epam.pipeline.dto.credits.CreditsUpdateRuleActionType;
import com.epam.pipeline.dto.credits.PlatformUsageCreditsStrategyType;
import com.epam.pipeline.entity.credits.PlatformUsageCreditsUpdateRuleEntity;
import com.epam.pipeline.test.repository.AbstractJpaTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.transaction.annotation.Transactional;

import static com.epam.pipeline.test.creator.credits.PlatformUsageCreditsRuleCreatorsUtils.platformUsageCreditsRuleEntity;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class PlatformUsageCreditsUpdateRuleRepositoryTest extends AbstractJpaTest {

    @Autowired
    private PlatformUsageCreditsRuleRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @Transactional
    public void crudTest() {
        final PlatformUsageCreditsUpdateRuleEntity rule = platformUsageCreditsRuleEntity();
        rule.setId(null);

        repository.save(rule);

        final Long id = rule.getId();
        assertThat(id, notNullValue());

        entityManager.flush();
        entityManager.clear();

        final PlatformUsageCreditsUpdateRuleEntity loaded = repository.findOne(id);
        assertThat(loaded, notNullValue());
        assertThat(loaded.getName(), is(rule.getName()));
        assertThat(loaded.getDescription(), is(rule.getDescription()));
        assertThat(loaded.getStrategyType(), is(rule.getStrategyType()));
        assertThat(loaded.getActionType(), is(rule.getActionType()));
        assertThat(loaded.getActionValue(), is(rule.getActionValue()));
        assertThat(loaded.getActionMessage(), is(rule.getActionMessage()));
        assertThat(loaded.isPerIncident(), is(rule.isPerIncident()));
        assertThat(loaded.getFilterExpression(), notNullValue());
        assertThat(loaded.getFilterExpression().getField(),
                is(rule.getFilterExpression().getField()));
        assertThat(loaded.getFilterExpression().getValue(),
                is(rule.getFilterExpression().getValue()));
        assertThat(loaded.getFilterExpression().getDuration(),
                is(rule.getFilterExpression().getDuration()));

        loaded.setActionType(CreditsUpdateRuleActionType.INCOME);
        loaded.setStrategyType(PlatformUsageCreditsStrategyType.RUN_STATE);
        repository.save(loaded);

        entityManager.flush();
        entityManager.clear();

        final PlatformUsageCreditsUpdateRuleEntity updated = repository.findOne(id);
        assertThat(updated.getActionType(), is(CreditsUpdateRuleActionType.INCOME));

        repository.delete(id);

        entityManager.flush();
        entityManager.clear();

        assertThat(repository.findOne(id), nullValue());
    }
}
