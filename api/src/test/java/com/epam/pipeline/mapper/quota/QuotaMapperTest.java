/*
 * Copyright 2021 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.mapper.quota;

import com.epam.pipeline.dto.quota.Quota;
import com.epam.pipeline.dto.quota.QuotaAction;
import com.epam.pipeline.entity.quota.QuotaActionEntity;
import com.epam.pipeline.entity.quota.QuotaEntity;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.test.creator.CommonCreatorConstants;
import com.epam.pipeline.test.creator.quota.QuotaCreatorsUtils;
import com.epam.pipeline.test.creator.user.UserCreatorUtils;
import org.junit.Test;
import org.mapstruct.factory.Mappers;

import java.util.Collections;

import static com.epam.pipeline.assertions.quota.QuotaAssertions.assertEquals;

public class QuotaMapperTest {
    private final QuotaMapper mapper = Mappers.getMapper(QuotaMapper.class);

    @Test
    public void shouldMapEntityToDto() {
        final PipelineUser pipelineUser = UserCreatorUtils.getPipelineUser(CommonCreatorConstants.TEST_NAME);
        final QuotaEntity quotaEntity = QuotaCreatorsUtils.quotaEntity(Collections.singletonList(pipelineUser));
        final QuotaActionEntity quotaActionEntity = QuotaCreatorsUtils.quotaActionEntity(quotaEntity);
        quotaEntity.setActions(Collections.singletonList(quotaActionEntity));

        final Quota result = mapper.quotaToDto(quotaEntity);
        assertEquals(quotaEntity, result);
    }

    @Test
    public void shouldMapDtoToEntity() {
        final Quota quotaDto = QuotaCreatorsUtils.quota(Collections.singletonList(CommonCreatorConstants.ID));
        final QuotaAction quotaActionDto = QuotaCreatorsUtils.quotaAction();
        quotaDto.setActions(Collections.singletonList(quotaActionDto));

        final QuotaEntity result = mapper.quotaToEntity(quotaDto);
        assertEquals(result, quotaDto);
    }
}
