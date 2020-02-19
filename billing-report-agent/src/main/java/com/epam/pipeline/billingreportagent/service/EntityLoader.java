/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.billingreportagent.service;

import com.epam.pipeline.billingreportagent.model.EntityContainer;
import com.epam.pipeline.billingreportagent.model.EntityWithMetadata;
import com.epam.pipeline.billingreportagent.service.impl.CloudPipelineAPIClient;
import com.epam.pipeline.entity.metadata.MetadataEntry;
import com.epam.pipeline.entity.metadata.PipeConfValue;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.vo.EntityVO;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public interface EntityLoader<T> {

    List<EntityContainer<T>> loadAllEntities();

    List<EntityContainer<T>> loadAllEntitiesActiveInPeriod(LocalDateTime from, LocalDateTime to);

    default Map<String, EntityWithMetadata<PipelineUser>> prepareUsers(final CloudPipelineAPIClient apiClient) {

        final Map<String, PipelineUser> users =
                apiClient.loadAllUsers()
                        .stream()
                        .collect(Collectors.toMap(PipelineUser::getUserName, Function.identity()));

        final Map<Long, Map<String, String>> metadata = apiClient.loadMetadataEntry(users.values()
                .stream()
                .map(user -> new EntityVO(user.getId(), AclClass.PIPELINE_USER))
                .collect(Collectors.toList()))
                .stream()
                .collect(Collectors.toMap(data -> data.getEntity().getEntityId(), this::prepareMetadataEntry));

        return users.entrySet()
                .stream()
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    entry -> {
                        final PipelineUser user = entry.getValue();
                        return EntityWithMetadata.<PipelineUser>builder()
                            .entity(user)
                            .metadata(metadata.get(user.getId()))
                            .build();
                    }));
    }

    default Map<String, String> prepareMetadataEntry(final MetadataEntry metadataEntry) {
        return Stream.of(metadataEntry.getData())
                .map(Map::entrySet)
                .flatMap(Set::stream)
                .collect(
                    HashMap::new,
                    (map, entry) -> {
                        final String value = Optional.ofNullable(entry.getValue())
                            .map(PipeConfValue::getValue)
                            .orElse(null);
                        map.put(entry.getKey(), value);
                    },
                    HashMap::putAll);
    }
}
