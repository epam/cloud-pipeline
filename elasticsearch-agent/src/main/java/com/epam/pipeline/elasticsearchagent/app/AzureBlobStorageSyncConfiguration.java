/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
 *
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

package com.epam.pipeline.elasticsearchagent.app;

import com.epam.pipeline.elasticsearchagent.dao.PipelineEventDao;
import com.epam.pipeline.elasticsearchagent.model.DataStorageDoc;
import com.epam.pipeline.elasticsearchagent.model.PipelineEvent;
import com.epam.pipeline.elasticsearchagent.service.ElasticsearchServiceClient;
import com.epam.pipeline.elasticsearchagent.service.impl.BulkRequestSender;
import com.epam.pipeline.elasticsearchagent.service.impl.CloudPipelineAPIClient;
import com.epam.pipeline.elasticsearchagent.service.impl.ElasticIndexService;
import com.epam.pipeline.elasticsearchagent.service.impl.EntitySynchronizer;
import com.epam.pipeline.elasticsearchagent.service.impl.converter.EventToRequestConverterImpl;
import com.epam.pipeline.elasticsearchagent.service.impl.converter.storage.DataStorageIndexCleaner;
import com.epam.pipeline.elasticsearchagent.service.impl.converter.storage.DataStorageLoader;
import com.epam.pipeline.elasticsearchagent.service.impl.converter.storage.DataStorageMapper;
import com.epam.pipeline.entity.search.SearchDocumentType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;

@Configuration
@ConditionalOnProperty(value = "sync.az-blob-storage.disable", matchIfMissing = true, havingValue = "false")
public class AzureBlobStorageSyncConfiguration {

    @Value("${sync.index.common.prefix}")
    private String commonIndexPrefix;

    @Bean
    public DataStorageMapper azStorageMapper() {
        return new DataStorageMapper(SearchDocumentType.AZ_BLOB_STORAGE);
    }

    @Bean
    public DataStorageLoader azStorageLoader(final CloudPipelineAPIClient apiClient) {
        return new DataStorageLoader(apiClient);
    }

    @Bean
    public DataStorageIndexCleaner azEventProcessor(
            final @Value("${sync.az-blob.index.name}") String azFileIndexName,
            final ElasticsearchServiceClient serviceClient) {
        return new DataStorageIndexCleaner(commonIndexPrefix, azFileIndexName, serviceClient);
    }

    @Bean
    public EventToRequestConverterImpl<DataStorageDoc> azEventConverter(
            final @Qualifier("azStorageMapper") DataStorageMapper azStorageMapper,
            final @Qualifier("azStorageLoader") DataStorageLoader azStorageLoader,
            final @Qualifier("azEventProcessor") DataStorageIndexCleaner indexCleaner,
            final @Value("${sync.az-blob-storage.index.name}") String indexName) {
        return new EventToRequestConverterImpl<>(
                commonIndexPrefix, indexName, azStorageLoader, azStorageMapper,
                Collections.singletonList(indexCleaner));
    }

    @Bean
    public EntitySynchronizer dataStorageAzSynchronizer(
            final @Qualifier("azEventConverter") EventToRequestConverterImpl<DataStorageDoc> azEventConverter,
            final PipelineEventDao eventDao,
            final ElasticIndexService indexService,
            final BulkRequestSender requestSender,
            final @Value("${sync.az-blob-storage.index.mapping}") String azStorageMapping,
            final @Value("${sync.load.common.entity.chunk.size:1000}") int chunkSize) {
        return new EntitySynchronizer(eventDao,
                PipelineEvent.ObjectType.AZ,
                azStorageMapping,
                azEventConverter,
                indexService,
                requestSender,
                chunkSize);
    }
}
