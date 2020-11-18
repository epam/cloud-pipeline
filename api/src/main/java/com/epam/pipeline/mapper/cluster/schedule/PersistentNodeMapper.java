/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.mapper.cluster.schedule;

import com.epam.pipeline.controller.vo.cluster.schedule.PersistentNodeVO;
import com.epam.pipeline.entity.cluster.schedule.NodeSchedule;
import com.epam.pipeline.entity.cluster.schedule.PersistentNode;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.Optional;

@Mapper(componentModel = "spring")
public interface PersistentNodeMapper {

    @Mapping(target = "created", ignore = true)
    @Mapping(target = "schedule", ignore = true)
    PersistentNode toEntity(PersistentNodeVO vo);

    @AfterMapping
    default void fillSchedule(final PersistentNodeVO vo, final @MappingTarget PersistentNode entity) {
        Optional.ofNullable(vo.getScheduleId())
                .ifPresent(scheduleId -> {
                    final NodeSchedule schedule = new NodeSchedule();
                    schedule.setId(scheduleId);
                    entity.setSchedule(schedule);
                });
    }
}
