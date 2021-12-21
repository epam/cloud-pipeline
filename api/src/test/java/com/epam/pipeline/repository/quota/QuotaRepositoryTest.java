/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.repository.quota;

import com.epam.pipeline.assertions.quota.QuotaAssertions;
import com.epam.pipeline.dto.quota.QuotaType;
import com.epam.pipeline.entity.quota.QuotaActionEntity;
import com.epam.pipeline.entity.quota.QuotaEntity;
import com.epam.pipeline.test.creator.quota.QuotaCreatorsUtils;
import com.epam.pipeline.test.repository.AbstractJpaTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;

import static com.epam.pipeline.test.creator.quota.QuotaCreatorsUtils.quotaSidEntity;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class QuotaRepositoryTest extends AbstractJpaTest {

    @Autowired
    private QuotaRepository quotaRepository;
    @Autowired
    private TestEntityManager entityManager;

    @Test
    @Transactional
    public void crudTest() {
        final QuotaEntity quota = QuotaCreatorsUtils.quotaEntity(Collections.singletonList(quotaSidEntity()));
        final QuotaActionEntity quotaAction = QuotaCreatorsUtils.quotaActionEntity(quota);
        quotaAction.setId(null);
        quota.setActions(Collections.singletonList(quotaAction));
        quota.setId(null);

        quotaRepository.save(quota);

        final Long quotaId = quota.getId();
        assertThat(quotaId, notNullValue());

        entityManager.flush();
        entityManager.clear();

        final QuotaEntity loaded = quotaRepository.findOne(quotaId);
        QuotaAssertions.assertEquals(quota, loaded);

        loaded.setType(QuotaType.GROUP);
        quotaRepository.save(loaded);

        entityManager.flush();
        entityManager.clear();

        final QuotaEntity loadedAfterUpdate = quotaRepository.findOne(quotaId);
        assertThat(loadedAfterUpdate.getId(), is(quotaId));
        assertThat(loadedAfterUpdate.getType(), is(QuotaType.GROUP));

        quotaRepository.delete(quotaId);

        entityManager.flush();
        entityManager.clear();

        assertThat(quotaRepository.findOne(quotaId), nullValue());
    }
}
