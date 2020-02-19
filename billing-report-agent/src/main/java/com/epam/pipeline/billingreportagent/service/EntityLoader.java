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
import com.epam.pipeline.billingreportagent.service.impl.CloudPipelineAPIClient;
import com.epam.pipeline.entity.metadata.MetadataEntry;
import com.epam.pipeline.entity.metadata.PipeConfValue;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.vo.EntityVO;
import org.apache.commons.collections4.MapUtils;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public interface EntityLoader<T> {

    List<EntityContainer<T>> loadAllEntities();

    List<EntityContainer<T>> loadAllEntitiesActiveInPeriod(LocalDateTime from, LocalDateTime to);

    default void buildUserData(final EntityContainer.EntityContainerBuilder<T> builder,
                               final String ownerName,
                               final Map<String, PipelineUser> users,
                               final CloudPipelineAPIClient apiClient) {
        final PipelineUser owner = users.get(ownerName);
        builder.owner(owner);
        Optional.ofNullable(owner)
                .ifPresent(user -> builder.ownerMetadata(
                        loadMetadata(user.getId(), AclClass.PIPELINE_USER, apiClient)));

    }

    default Map<String, String> loadMetadata(final Long id,
                                             final AclClass aclClass,
                                             final CloudPipelineAPIClient apiClient) {
        if (aclClass == null) {
            return Collections.emptyMap();
        }
        List<MetadataEntry> metadataEntries = apiClient
                .loadMetadataEntry(Collections.singletonList(new EntityVO(id, aclClass)));
        return prepareMetadataForEntity(metadataEntries);
    }

    default Map<String, String> prepareMetadataForEntity(final List<MetadataEntry> metadataEntries) {
        if (CollectionUtils.isEmpty(metadataEntries) || metadataEntries.size() != 1) {
            return Collections.emptyMap();
        }
        return Stream.of(MapUtils.emptyIfNull(metadataEntries.get(0).getData()))
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
