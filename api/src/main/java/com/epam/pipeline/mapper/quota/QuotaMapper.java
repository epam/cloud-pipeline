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
import org.apache.commons.collections4.CollectionUtils;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.Objects;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface QuotaMapper {

    QuotaAction actionToEntity(QuotaActionEntity entity);

    @Mapping(target = "quota", ignore = true)
    QuotaActionEntity actionToDto(QuotaAction dto);

    @Mapping(target = "informedUsers", ignore = true)
    QuotaEntity quotaToEntity(Quota dto);

    @Mapping(target = "informedUsers", ignore = true)
    Quota quotaToDto(QuotaEntity entity);

    @AfterMapping
    default void finishEntityUsers(final Quota dto, final @MappingTarget QuotaEntity entity) {
        if (Objects.isNull(dto) || CollectionUtils.isEmpty(dto.getInformedUsers())) {
            return;
        }
        entity.setInformedUsers(dto.getInformedUsers().stream()
                .map(this::buildUser)
                .collect(Collectors.toList()));
    }

    @AfterMapping
    default void finishDtoUsers(final QuotaEntity entity, final @MappingTarget Quota dto) {
        if (Objects.isNull(entity) || CollectionUtils.isEmpty(entity.getInformedUsers())) {
            return;
        }
        dto.setInformedUsers(entity.getInformedUsers().stream()
                .map(PipelineUser::getId)
                .collect(Collectors.toList()));
    }

    default PipelineUser buildUser(final Long id) {
        final PipelineUser user = new PipelineUser();
        user.setId(id);
        return user;
    }
}
