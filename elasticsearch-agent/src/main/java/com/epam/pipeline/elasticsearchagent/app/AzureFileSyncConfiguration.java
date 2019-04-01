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

import com.epam.pipeline.elasticsearchagent.service.ElasticsearchServiceClient;
import com.epam.pipeline.elasticsearchagent.service.ObjectStorageFileManager;
import com.epam.pipeline.elasticsearchagent.service.ObjectStorageIndex;
import com.epam.pipeline.elasticsearchagent.service.impl.AzureBlobManager;
import com.epam.pipeline.elasticsearchagent.service.impl.CloudPipelineAPIClient;
import com.epam.pipeline.elasticsearchagent.service.impl.ElasticIndexService;
import com.epam.pipeline.elasticsearchagent.service.impl.ObjectStorageIndexImpl;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(value = "sync.az-blob.disable", matchIfMissing = true, havingValue = "false")
public class AzureFileSyncConfiguration {

    @Value("${sync.index.common.prefix}")
    private String indexPrefix;

    @Value("${sync.az-blob.index.mapping}")
    private String indexSettingsPath;

    @Value("${sync.az-blob.bulk.insert.size:1000}")
    private Integer bulkInsertSize;

    @Value("${sync.az-blob.index.name}")
    private String indexName;

    @Bean
    @Qualifier("azFileManager")
    public ObjectStorageFileManager objectStorageFileManager() {
        return new AzureBlobManager();
    }

    @Bean
    @Qualifier("azFileSynchronizer")
    public ObjectStorageIndex azFileSynchronizer(final CloudPipelineAPIClient apiClient,
                                                 final ElasticsearchServiceClient esClient,
                                                 final ElasticIndexService indexService) {
        return new ObjectStorageIndexImpl(apiClient, esClient, indexService,
                objectStorageFileManager(), indexPrefix + indexName,
                indexSettingsPath, bulkInsertSize, DataStorageType.AZ);
    }
}
