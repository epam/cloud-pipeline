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

import com.epam.pipeline.elasticsearchagent.service.ElasticsearchServiceClient;
import com.epam.pipeline.elasticsearchagent.service.ObjectStorageFileManager;
import com.epam.pipeline.elasticsearchagent.service.ObjectStorageIndex;
import com.epam.pipeline.elasticsearchagent.service.impl.*;
import com.epam.pipeline.elasticsearchagent.service.lock.LockService;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.search.SearchDocumentType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GSFileSyncConfiguration {

    @Value("${sync.index.common.prefix}")
    private String indexPrefix;

    @Value("${sync.gs-file.index.name}")
    private String indexName;

    @Value("${sync.gs-file.index.mapping}")
    private String indexSettingsPath;

    @Value("${sync.gs-file.bulk.insert.size:1000}")
    private Integer bulkInsertSize;

    @Value("${sync.gs-file.bulk.load.tags.size:100}")
    private Integer bulkLoadTagsSize;

    @Value("${sync.gs-file.tag.value.delimiter:;}")
    private String tagDelimiter;

    @Value("${sync.gs-file.storage.exclude.metadata.key:Billing status}")
    private String storageExcludeKey;

    @Value("${sync.gs-file.storage.exclude.metadata.value:Exclude}")
    private String storageExcludeValue;

    @Bean
    public ObjectStorageFileManager gsFileManager() {
        return new GsBucketFileManager();
    }

    @Bean
    @ConditionalOnProperty(value = "sync.gs-file.disable", matchIfMissing = true, havingValue = "false")
    public ObjectStorageIndex gsFileSynchronizer(
            final CloudPipelineAPIClient apiClient,
            final ElasticsearchServiceClient esClient,
            final ElasticIndexService indexService,
            final @Qualifier("gsFileManager") ObjectStorageFileManager gsFileManager,
            final LockService lockService) {
        return new ObjectStorageIndexImpl(apiClient, esClient, indexService,
                gsFileManager, lockService, indexPrefix + indexName,
                indexSettingsPath, bulkInsertSize, bulkLoadTagsSize,
                DataStorageType.GS,
                SearchDocumentType.GS_FILE,
                tagDelimiter, false,
                storageExcludeKey, storageExcludeValue);
    }

}
