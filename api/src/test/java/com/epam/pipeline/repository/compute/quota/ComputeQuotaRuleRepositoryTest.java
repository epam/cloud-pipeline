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

package com.epam.pipeline.repository.compute.quota;

import com.epam.pipeline.dto.compute.quota.ComputeQuotaActionType;
import com.epam.pipeline.dto.compute.quota.ComputeQuotaStrategyType;
import com.epam.pipeline.entity.compute.quota.ComputeQuotaRuleEntity;
import com.epam.pipeline.test.repository.AbstractJpaTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.transaction.annotation.Transactional;

import static com.epam.pipeline.test.creator.compute.quota.ComputeQuotaRuleCreatorsUtils.computeQuotaRuleEntity;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class ComputeQuotaRuleRepositoryTest extends AbstractJpaTest {

    @Autowired
    private ComputeQuotaRuleRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @Transactional
    public void crudTest() {
        final ComputeQuotaRuleEntity rule = computeQuotaRuleEntity();
        rule.setId(null);

        repository.save(rule);

        final Long id = rule.getId();
        assertThat(id, notNullValue());

        entityManager.flush();
        entityManager.clear();

        final ComputeQuotaRuleEntity loaded = repository.findOne(id);
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

        loaded.setActionType(ComputeQuotaActionType.INCOME);
        loaded.setStrategyType(ComputeQuotaStrategyType.RUN_STATE);
        repository.save(loaded);

        entityManager.flush();
        entityManager.clear();

        final ComputeQuotaRuleEntity updated = repository.findOne(id);
        assertThat(updated.getActionType(), is(ComputeQuotaActionType.INCOME));

        repository.delete(id);

        entityManager.flush();
        entityManager.clear();

        assertThat(repository.findOne(id), nullValue());
    }
}
