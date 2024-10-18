/*
 * Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.mapper.user;

import com.epam.pipeline.controller.vo.user.RunnerSidVO;
import com.epam.pipeline.entity.user.RunnerSid;
import org.apache.commons.collections4.ListUtils;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;
import java.util.stream.Collectors;


@Mapper(componentModel = "spring")
public interface RunnerSidMapper {

    @Mapping(target = "pipelines", ignore = true)
    @Mapping(target = "tools", ignore = true)
    RunnerSid toEntity(RunnerSidVO vo);

    @AfterMapping
    default void fillLists(final RunnerSidVO dto,
                           final @MappingTarget RunnerSid entity) {
        entity.setPipelinesList(collectIds(dto.getPipelines()));
        entity.setToolsList(collectIds(dto.getTools()));
    }

    default String collectIds(List<Long> ids) {
        return ListUtils.emptyIfNull(ids)
                .stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }
}
