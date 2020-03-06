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

package com.epam.pipeline.billingreportagent.service.impl.synchronizer;

import com.epam.pipeline.billingreportagent.exception.ElasticClientException;
import com.epam.pipeline.billingreportagent.model.EntityContainer;
import com.epam.pipeline.billingreportagent.service.ElasticsearchServiceClient;
import com.epam.pipeline.billingreportagent.service.ElasticsearchSynchronizer;
import com.epam.pipeline.billingreportagent.service.EntityLoader;
import com.epam.pipeline.billingreportagent.service.EntityToBillingRequestConverter;
import com.epam.pipeline.billingreportagent.service.impl.BulkRequestSender;
import com.epam.pipeline.billingreportagent.service.impl.ElasticIndexService;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.DocWriteRequest;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Data
@Slf4j
@SuppressWarnings("PMD.AvoidCatchingGenericException")
public class StorageSynchronizer implements ElasticsearchSynchronizer {

    private final String storageIndexMappingFile;
    private final String indexPrefix;
    private final EntityLoader<AbstractDataStorage> loader;
    private final EntityToBillingRequestConverter<AbstractDataStorage> storageToBillingRequestConverter;
    private final ElasticIndexService indexService;
    private final BulkRequestSender requestSender;
    private final DataStorageType storageType;

    public StorageSynchronizer(final @Value("${sync.storage.index.mapping}") String storageIndexMappingFile,
                               final @Value("${sync.index.common.prefix}") String indexPrefix,
                               final @Value("${sync.storage.index.name}") String storageIndexName,
                               final @Value("${sync.bulk.insert.size:1000}") Integer bulkInsertSize,
                               final ElasticsearchServiceClient elasticsearchServiceClient,
                               final EntityLoader<AbstractDataStorage> loader,
                               final ElasticIndexService indexService,
                               final EntityToBillingRequestConverter<AbstractDataStorage> storageToBillingReqConverter,
                               final DataStorageType storageType) {
        this.storageIndexMappingFile = storageIndexMappingFile;
        this.indexPrefix = indexPrefix + storageIndexName;
        this.loader = loader;
        this.storageToBillingRequestConverter = storageToBillingReqConverter;
        this.indexService = indexService;
        this.requestSender = new BulkRequestSender(elasticsearchServiceClient, bulkInsertSize);
        this.storageType = storageType;
    }

    @Override
    public void synchronize(LocalDateTime lastSyncTime, LocalDateTime syncStart) {
        log.debug("Started {} storage billing synchronization", storageType);
        final List<EntityContainer<AbstractDataStorage>> entityContainers = loader.loadAllEntities();
        entityContainers.removeIf(storage -> storage.getEntity().getType() != storageType);
        final List<DocWriteRequest> storageBillingRequests =
            createStorageBillingRequest(entityContainers, lastSyncTime, syncStart);

        log.info("{} document requests created", storageBillingRequests.size());

        storageBillingRequests.stream()
            .map(DocWriteRequest::index)
            .distinct()
            .forEach(index -> {
                try {
                    indexService.createIndexIfNotExists(index, storageIndexMappingFile);
                } catch (ElasticClientException e) {
                    log.warn("Can't create index {}!", index);
                }
            });

        requestSender.indexDocuments(storageBillingRequests);
        log.debug("Successfully finished {} storage billing synchronization.", storageType);
    }

    private List<DocWriteRequest> createStorageBillingRequest(final List<EntityContainer<AbstractDataStorage>> storages,
                                                              final LocalDateTime previousSync,
                                                              final LocalDateTime syncStart) {
        try {
            return buildDocRequests(storages, previousSync, syncStart);
        } catch (Exception e) {
            log.error("An error during storage billing synchronization: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<DocWriteRequest> buildDocRequests(final List<EntityContainer<AbstractDataStorage>> storages,
                                                   final LocalDateTime previousSync,
                                                   final LocalDateTime syncStart) {
        return storageToBillingRequestConverter.convertEntitiesToRequests(storages, indexPrefix,
                                                                          previousSync, syncStart);
    }
}
