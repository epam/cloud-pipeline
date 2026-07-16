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

package com.epam.pipeline.mapper.credits;

import com.epam.pipeline.dto.credits.PlatformUsageCreditsUpdateRule;
import com.epam.pipeline.entity.credits.PlatformUsageCreditsUpdateRuleEntity;
import org.junit.Test;
import org.mapstruct.factory.Mappers;

import static com.epam.pipeline.test.creator.credits.PlatformUsageCreditsRuleCreatorsUtils.platformUsageCreditsRule;
import static com.epam.pipeline.test.creator.credits.PlatformUsageCreditsRuleCreatorsUtils
        .platformUsageCreditsRuleEntity;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

public class PlatformUsageCreditsUpdateRuleMapperTest {

    private final PlatformUsageCreditsRuleMapper mapper = Mappers.getMapper(PlatformUsageCreditsRuleMapper.class);

    @Test
    public void shouldMapEntityToDto() {
        final PlatformUsageCreditsUpdateRuleEntity entity = platformUsageCreditsRuleEntity();

        final PlatformUsageCreditsUpdateRule dto = mapper.toDto(entity);

        assertThat(dto.getId(), is(entity.getId()));
        assertThat(dto.getName(), is(entity.getName()));
        assertThat(dto.getDescription(), is(entity.getDescription()));
        assertThat(dto.getStrategyType(), is(entity.getStrategyType()));
        assertThat(dto.getStatement(), is(entity.getStatement()));
        assertThat(dto.getExclude(), is(entity.getExclude()));
        assertThat(dto.getAction(), notNullValue());
        assertThat(dto.getAction().getType(), is(entity.getActionType()));
        assertThat(dto.getAction().getValue(), is(entity.getActionValue()));
        assertThat(dto.getAction().getMessage(), is(entity.getActionMessage()));
        assertThat(dto.getAction().isPerIncident(), is(entity.isPerIncident()));
    }

    @Test
    public void shouldMapDtoToEntity() {
        final PlatformUsageCreditsUpdateRule dto = platformUsageCreditsRule();

        final PlatformUsageCreditsUpdateRuleEntity entity = mapper.toEntity(dto);

        assertThat(entity.getId(), is(dto.getId()));
        assertThat(entity.getName(), is(dto.getName()));
        assertThat(entity.getDescription(), is(dto.getDescription()));
        assertThat(entity.getStrategyType(), is(dto.getStrategyType()));
        assertThat(entity.getStatement(), is(dto.getStatement()));
        assertThat(entity.getExclude(), is(dto.getExclude()));
        assertThat(entity.getActionType(), is(dto.getAction().getType()));
        assertThat(entity.getActionValue(), is(dto.getAction().getValue()));
        assertThat(entity.getActionMessage(), is(dto.getAction().getMessage()));
        assertThat(entity.isPerIncident(), is(dto.getAction().isPerIncident()));
        assertThat(entity.getCreatedDate(), nullValue());
        assertThat(entity.getModifiedDate(), nullValue());
    }
}
