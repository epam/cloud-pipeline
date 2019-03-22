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
@ConditionalOnProperty(value = "sync.s3-storage.disable", matchIfMissing = true, havingValue = "false")
public class S3StorageSyncConfiguration {

    @Value("${sync.index.common.prefix}")
    private String commonIndexPrefix;

    @Bean
    @Qualifier("s3StorageMapper")
    public DataStorageMapper s3StorageMapper() {
        return new DataStorageMapper(SearchDocumentType.S3_STORAGE);
    }

    @Bean
    @Qualifier("s3StorageLoader")
    public DataStorageLoader s3StorageLoader(final CloudPipelineAPIClient apiClient) {
        return new DataStorageLoader(apiClient);
    }

    @Bean
    @Qualifier("s3EventProcessor")
    public DataStorageIndexCleaner dataStorageIndexCleaner(
            final @Value("${sync.s3-file.index.name}") String s3FileIndexName,
            final ElasticsearchServiceClient serviceClient) {
        return new DataStorageIndexCleaner(commonIndexPrefix, s3FileIndexName, serviceClient);
    }

    @Bean
    @Qualifier("s3EventConverter")
    public EventToRequestConverterImpl<DataStorageDoc> s3EventConverter(
            final @Qualifier("s3StorageMapper") DataStorageMapper s3StorageMapper,
            final @Qualifier("s3StorageLoader") DataStorageLoader s3StorageLoader,
            final @Qualifier("s3EventProcessor") DataStorageIndexCleaner indexCleaner,
            final @Value("${sync.s3-storage.index.name}") String indexName) {
        return new EventToRequestConverterImpl<>(
                commonIndexPrefix, indexName, s3StorageLoader, s3StorageMapper,
                Collections.singletonList(indexCleaner));
    }

    @Bean
    public EntitySynchronizer dataStorageS3Synchronizer(
            final @Qualifier("s3EventConverter") EventToRequestConverterImpl<DataStorageDoc> s3EventConverter,
            final PipelineEventDao eventDao,
            final ElasticIndexService indexService,
            final BulkRequestSender requestSender,
            final @Value("${sync.s3-storage.index.mapping}") String s3StorageMapping) {
        return new EntitySynchronizer(eventDao,
                PipelineEvent.ObjectType.S3,
                s3StorageMapping,
                s3EventConverter,
                indexService,
                requestSender);
    }
}
