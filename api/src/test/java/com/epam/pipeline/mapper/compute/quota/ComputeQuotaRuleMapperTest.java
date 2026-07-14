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

package com.epam.pipeline.mapper.compute.quota;

import com.epam.pipeline.dto.compute.quota.ComputeQuotaRule;
import com.epam.pipeline.entity.compute.quota.ComputeQuotaRuleEntity;
import org.junit.Test;
import org.mapstruct.factory.Mappers;

import static com.epam.pipeline.test.creator.compute.quota.ComputeQuotaRuleCreatorsUtils.computeQuotaRule;
import static com.epam.pipeline.test.creator.compute.quota.ComputeQuotaRuleCreatorsUtils.computeQuotaRuleEntity;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

public class ComputeQuotaRuleMapperTest {

    private final ComputeQuotaRuleMapper mapper = Mappers.getMapper(ComputeQuotaRuleMapper.class);

    @Test
    public void shouldMapEntityToDto() {
        final ComputeQuotaRuleEntity entity = computeQuotaRuleEntity();

        final ComputeQuotaRule dto = mapper.toDto(entity);

        assertThat(dto.getId(), is(entity.getId()));
        assertThat(dto.getName(), is(entity.getName()));
        assertThat(dto.getDescription(), is(entity.getDescription()));
        assertThat(dto.getStrategyType(), is(entity.getStrategyType()));
        assertThat(dto.getFilterExpression(), is(entity.getFilterExpression()));
        assertThat(dto.getExcludeExpression(), is(entity.getExcludeExpression()));
        assertThat(dto.getAction(), notNullValue());
        assertThat(dto.getAction().getType(), is(entity.getActionType()));
        assertThat(dto.getAction().getValue(), is(entity.getActionValue()));
        assertThat(dto.getAction().getMessage(), is(entity.getActionMessage()));
        assertThat(dto.getAction().isPerIncident(), is(entity.isPerIncident()));
    }

    @Test
    public void shouldMapDtoToEntity() {
        final ComputeQuotaRule dto = computeQuotaRule();

        final ComputeQuotaRuleEntity entity = mapper.toEntity(dto);

        assertThat(entity.getId(), is(dto.getId()));
        assertThat(entity.getName(), is(dto.getName()));
        assertThat(entity.getDescription(), is(dto.getDescription()));
        assertThat(entity.getStrategyType(), is(dto.getStrategyType()));
        assertThat(entity.getFilterExpression(), is(dto.getFilterExpression()));
        assertThat(entity.getExcludeExpression(), is(dto.getExcludeExpression()));
        assertThat(entity.getActionType(), is(dto.getAction().getType()));
        assertThat(entity.getActionValue(), is(dto.getAction().getValue()));
        assertThat(entity.getActionMessage(), is(dto.getAction().getMessage()));
        assertThat(entity.isPerIncident(), is(dto.getAction().isPerIncident()));
        assertThat(entity.getCreatedDate(), nullValue());
        assertThat(entity.getModifiedDate(), nullValue());
    }
}
