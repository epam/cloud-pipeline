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
import com.epam.pipeline.billingreportagent.model.EntityWithMetadata;
import com.epam.pipeline.billingreportagent.service.EntityLoader;
import com.epam.pipeline.billingreportagent.service.impl.CloudPipelineAPIClient;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.metadata.MetadataEntry;
import com.epam.pipeline.entity.metadata.PipeConfValue;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.vo.EntityVO;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class StorageLoader implements EntityLoader<AbstractDataStorage> {

    private final CloudPipelineAPIClient apiClient;

    public StorageLoader(final CloudPipelineAPIClient apiClient) {
        this.apiClient = apiClient;
    }

    @Override
    public List<EntityContainer<AbstractDataStorage>> loadAllEntities() {
        final Map<String, EntityWithMetadata<PipelineUser>> usersWithMetadata = prepareUsers(apiClient);
        final List<AbstractDataStorage> storages = apiClient.loadAllDataStorages();

        final Map<Long, Map<String, PipeConfValue>> metadataEntries = apiClient.loadMetadataEntry(storages.stream()
                .map(s -> new EntityVO(s.getId(), AclClass.DATA_STORAGE)).collect(Collectors.toList()))
                .stream()
                .collect(Collectors.toMap(me -> me.getEntity().getEntityId(), MetadataEntry::getData));

        return storages
                .stream()
                .map(storage -> EntityContainer.<AbstractDataStorage>builder()
                        .entity(storage)
                        .metadata(metadataEntries.getOrDefault(storage.getId(), Collections.emptyMap()))
                        .owner(usersWithMetadata.get(storage.getOwner()))
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public List<EntityContainer<AbstractDataStorage>> loadAllEntitiesActiveInPeriod(final LocalDateTime from,
                                                                                    final LocalDateTime to) {
        return loadAllEntities();
    }
}
