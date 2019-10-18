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
@ConditionalOnProperty(value = "sync.nfs-storage.disable", matchIfMissing = true, havingValue = "false")
public class NFSStorageSyncConfiguration {

    @Value("${sync.index.common.prefix}")
    private String commonIndexPrefix;

    @Bean
    public DataStorageMapper nfsStorageMapper() {
        return new DataStorageMapper(SearchDocumentType.NFS_STORAGE);
    }

    @Bean
    public DataStorageLoader nfsStorageLoader(final CloudPipelineAPIClient apiClient) {
        return new DataStorageLoader(apiClient);
    }

    @Bean
    public DataStorageIndexCleaner nfsStorageIndexCleaner(
            final @Value("${sync.nfs-file.index.name}") String nfsFileIndexName,
            final ElasticsearchServiceClient serviceClient) {
        return new DataStorageIndexCleaner(commonIndexPrefix, nfsFileIndexName, serviceClient);
    }

    @Bean
    public EventToRequestConverterImpl<DataStorageDoc> nfsEventConverter(
            final @Qualifier("nfsStorageMapper") DataStorageMapper nfsStorageMapper,
            final @Qualifier("nfsStorageLoader") DataStorageLoader nfsStorageLoader,
            final @Qualifier("nfsStorageIndexCleaner") DataStorageIndexCleaner nfsStorageIndexCleaner,
            final @Value("${sync.nfs-storage.index.name}") String indexName) {
        return  new EventToRequestConverterImpl<>(
                commonIndexPrefix, indexName, nfsStorageLoader, nfsStorageMapper,
                Collections.singletonList(nfsStorageIndexCleaner));
    }
    @Bean
    public EntitySynchronizer dataStorageNfsSynchronizer(
            final @Qualifier("nfsEventConverter") EventToRequestConverterImpl<DataStorageDoc> nfsEventConverter,
            final PipelineEventDao eventDao,
            final ElasticIndexService indexService,
            final BulkRequestSender requestSender,
            final @Value("${sync.nfs-storage.index.mapping}") String nfsStorageMapping,
            final @Value("${sync.load.common.entity.chunk.size:1000}") int chunkSize) {
        return new EntitySynchronizer(eventDao,
                PipelineEvent.ObjectType.NFS,
                nfsStorageMapping,
                nfsEventConverter,
                indexService,
                requestSender,
                chunkSize);
    }
}
