/*
 * Copyright 2021-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.dto.quota.AppliedQuota;
import com.epam.pipeline.dto.quota.Quota;
import com.epam.pipeline.dto.quota.QuotaAction;
import com.epam.pipeline.dto.quota.QuotaType;
import com.epam.pipeline.entity.quota.AppliedQuotaEntity;
import com.epam.pipeline.entity.quota.QuotaActionEntity;
import com.epam.pipeline.entity.quota.QuotaEntity;
import com.epam.pipeline.entity.quota.QuotaSidEntity;
import com.epam.pipeline.entity.user.SidImpl;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;


@Mapper(componentModel = "spring")
public interface QuotaMapper {

    SidImpl recipientToDto(QuotaSidEntity entity);

    QuotaSidEntity recipientToEntity(SidImpl dto);

    @Mapping(target = "activeAction", ignore = true)
    QuotaAction actionToEntity(QuotaActionEntity entity);

    @Mapping(target = "quota", ignore = true)
    QuotaActionEntity actionToDto(QuotaAction dto);

    QuotaEntity quotaToEntity(Quota dto);

    Quota quotaToDto(QuotaEntity entity);

    AppliedQuotaEntity appliedQuotaToEntity(AppliedQuota dto);

    @Mapping(target = "quota", ignore = true)
    AppliedQuota appliedQuotaToDto(AppliedQuotaEntity entity);

    @AfterMapping
    default void fillAppliedQuota(final AppliedQuotaEntity entity, final @MappingTarget AppliedQuota dto) {
        final QuotaEntity quota = entity.getAction().getQuota();
        dto.setQuota(quotaToDto(quota));
    }

    @AfterMapping
    default void fillQuota(final Quota dto, final @MappingTarget QuotaEntity entity) {
        if (QuotaType.GROUP.equals(dto.getType()) || QuotaType.USER.equals(dto.getType())) {
            entity.setSubject(dto.getSubject().toUpperCase());
        }
    }
}
