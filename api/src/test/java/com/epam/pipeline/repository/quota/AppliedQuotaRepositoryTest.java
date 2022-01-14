/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.repository.quota;

import com.epam.pipeline.entity.quota.AppliedQuotaEntity;
import com.epam.pipeline.entity.quota.QuotaActionEntity;
import com.epam.pipeline.entity.quota.QuotaEntity;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.test.creator.quota.QuotaCreatorsUtils;
import com.epam.pipeline.test.repository.AbstractJpaTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import javax.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static com.epam.pipeline.test.creator.quota.QuotaCreatorsUtils.quotaSidEntity;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

@Transactional
public class AppliedQuotaRepositoryTest extends AbstractJpaTest {

    @Autowired
    private QuotaRepository quotaRepository;
    @Autowired
    private AppliedQuotaRepository appliedQuotaRepository;
    @Autowired
    private TestEntityManager entityManager;

    @Test
    public void shouldFindActiveQuotasForUser() {
        final QuotaActionEntity quotaAction = createQuotaEntity();

        final AppliedQuotaEntity appliedQuotaEntity = new AppliedQuotaEntity();
        appliedQuotaEntity.setAction(quotaAction);
        appliedQuotaEntity.setExpense(10.0);
        final LocalDateTime nowUTC = DateUtils.nowUTC();
        appliedQuotaEntity.setModified(nowUTC);
        appliedQuotaEntity.setFrom(nowUTC.toLocalDate().withDayOfMonth(1));
        appliedQuotaEntity.setTo(nowUTC.toLocalDate().plusDays(1));

        appliedQuotaRepository.save(appliedQuotaEntity);

        entityManager.flush();
        entityManager.clear();

        final PipelineUser pipelineUser = new PipelineUser();
        pipelineUser.setUserName("USER");

        final List<AppliedQuotaEntity> result = appliedQuotaRepository.findAll(
                AppliedQuotaSpecification.userActiveQuotas(pipelineUser, null));
        assertThat(result, hasSize(1));
    }

    public QuotaActionEntity createQuotaEntity() {
        final QuotaEntity quota = QuotaCreatorsUtils.quotaEntity(Collections.singletonList(quotaSidEntity()));
        final QuotaActionEntity quotaAction = QuotaCreatorsUtils.quotaActionEntity(quota);
        quotaAction.setId(null);
        quota.setActions(Collections.singletonList(quotaAction));
        quota.setId(null);
        quotaRepository.save(quota);
        entityManager.flush();
        entityManager.clear();
        return quotaAction;
    }
}