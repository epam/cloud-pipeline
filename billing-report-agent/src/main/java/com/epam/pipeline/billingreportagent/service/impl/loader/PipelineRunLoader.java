/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.billingreportagent.service.impl.loader;

import com.epam.pipeline.billingreportagent.model.EntityContainer;
import com.epam.pipeline.billingreportagent.service.EntityLoader;
import com.epam.pipeline.billingreportagent.service.impl.CloudPipelineAPIClient;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.user.PipelineUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class PipelineRunLoader implements EntityLoader<PipelineRun> {

    @Autowired
    private CloudPipelineAPIClient apiClient;

    @Override
    public List<EntityContainer<PipelineRun>> loadAllEntities() {
        return loadAllEntitiesActiveInPeriod(LocalDateTime.MIN, LocalDateTime.MAX);
    }

    @Override
    public List<EntityContainer<PipelineRun>> loadAllEntitiesActiveInPeriod(final LocalDateTime from,
                                                                            final LocalDateTime to) {
        final Map<String, PipelineUser> users =
            apiClient.loadAllUsers().stream().collect(Collectors.toMap(PipelineUser::getUserName, Function.identity()));
        return apiClient.loadAllPipelineRunsActiveInPeriod(from, to)
            .stream()
            .map(run -> EntityContainer.<PipelineRun>builder().entity(run).owner(users.get(run.getOwner())).build())
            .collect(Collectors.toList());
    }
}
