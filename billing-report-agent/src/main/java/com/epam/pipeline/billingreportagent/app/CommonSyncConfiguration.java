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

package com.epam.pipeline.billingreportagent.app;

import com.epam.pipeline.billingreportagent.model.StorageType;
import com.epam.pipeline.billingreportagent.service.ElasticsearchServiceClient;
import com.epam.pipeline.billingreportagent.service.ElasticsearchSynchronizer;
import com.epam.pipeline.billingreportagent.service.impl.BulkRequestSender;
import com.epam.pipeline.billingreportagent.service.impl.ElasticIndexService;
import com.epam.pipeline.billingreportagent.service.impl.converter.AwsStoragePricingService;
import com.epam.pipeline.billingreportagent.service.impl.converter.GcpStoragePricingService;
import com.epam.pipeline.billingreportagent.service.impl.synchronizer.PipelineRunSynchronizer;
import com.epam.pipeline.billingreportagent.service.impl.synchronizer.StorageSynchronizer;
import com.epam.pipeline.billingreportagent.service.impl.converter.StorageToBillingRequestConverter;
import com.epam.pipeline.billingreportagent.service.impl.loader.PipelineRunLoader;
import com.epam.pipeline.billingreportagent.service.impl.loader.StorageLoader;
import com.epam.pipeline.billingreportagent.service.impl.mapper.RunBillingMapper;
import com.epam.pipeline.billingreportagent.service.impl.mapper.StorageBillingMapper;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.search.SearchDocumentType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CommonSyncConfiguration {

    private static final String FALSE = "false";

    @Value("${sync.index.common.prefix}")
    private String commonIndexPrefix;

    @Value("${sync.bulk.insert.size:1000}")
    private int bulkSize;

    @Bean
    public BulkRequestSender bulkRequestSender(
        final ElasticsearchServiceClient elasticsearchClient) {
        return new BulkRequestSender(elasticsearchClient);
    }

    @Bean
    @ConditionalOnProperty(value = "sync.run.disable", matchIfMissing = true, havingValue = FALSE)
    public ElasticsearchSynchronizer pipelineRunSynchronizer(
        final RunBillingMapper mapper,
        final PipelineRunLoader loader,
        final ElasticIndexService indexService,
        final ElasticsearchServiceClient elasticsearchClient,
        final @Value("${sync.run.index.name}") String indexName,
        final @Value("${sync.run.index.mapping}") String runMapping,
        final @Value("${sync.run.bulk.insert.size:1000}") int bulkSize) {
        return new PipelineRunSynchronizer(runMapping,
                                           commonIndexPrefix,
                                           indexName,
                                           bulkSize,
                                           elasticsearchClient,
                                           indexService,
                                           mapper,
                                           loader);
    }

    @Bean
    @ConditionalOnProperty(value = "sync.storage.disable", matchIfMissing = true, havingValue = FALSE)
    public StorageSynchronizer s3Synchronizer(final @Value("${sync.storage.index.mapping}") String storageMapping,
                                              final @Value("${sync.storage.index.name}") String indexName,
                                              final @Value("${sync.billing.center.key}") String billingCenterKey,
                                              final StorageLoader loader,
                                              final ElasticIndexService indexService,
                                              final ElasticsearchServiceClient elasticsearchClient) {
        final StorageBillingMapper mapper = new StorageBillingMapper(SearchDocumentType.S3_STORAGE, billingCenterKey);
        return new StorageSynchronizer(storageMapping,
                                       commonIndexPrefix,
                                       indexName,
                                       bulkSize,
                                       elasticsearchClient,
                                       loader,
                                       indexService,
                                       new StorageToBillingRequestConverter(mapper, elasticsearchClient,
                                                                            StorageType.OBJECT_STORAGE,
                                                                            new AwsStoragePricingService("AmazonS3")),
                                       DataStorageType.S3);
    }

    @Bean
    @ConditionalOnProperty(value = "sync.storage.disable", matchIfMissing = true, havingValue = FALSE)
    public StorageSynchronizer efsSynchronizer(final @Value("${sync.storage.index.mapping}") String storageMapping,
                                              final @Value("${sync.storage.index.name}") String indexName,
                                               final @Value("${sync.billing.center.key}") String billingCenterKey,
                                              final StorageLoader loader,
                                              final ElasticIndexService indexService,
                                              final ElasticsearchServiceClient elasticsearchClient) {
        final StorageBillingMapper mapper = new StorageBillingMapper(SearchDocumentType.NFS_STORAGE, billingCenterKey);
        return new StorageSynchronizer(storageMapping,
                                       commonIndexPrefix,
                                       indexName,
                                       bulkSize,
                                       elasticsearchClient,
                                       loader,
                                       indexService,
                                       new StorageToBillingRequestConverter(mapper, elasticsearchClient,
                                                                            StorageType.FILE_STORAGE,
                                                                            new AwsStoragePricingService("AmazonEFS")),
                                       DataStorageType.NFS);
    }

    @Bean
    @ConditionalOnProperty(value = "sync.storage.disable", matchIfMissing = true, havingValue = FALSE)
    public StorageSynchronizer gsSynchronizer(final @Value("${sync.storage.index.mapping}") String storageMapping,
                                               final @Value("${sync.storage.index.name}") String indexName,
                                               final @Value("${sync.billing.center.key}") String billingCenterKey,
                                               final StorageLoader loader,
                                               final ElasticIndexService indexService,
                                               final ElasticsearchServiceClient elasticsearchClient) {
        final StorageBillingMapper mapper = new StorageBillingMapper(SearchDocumentType.GS_STORAGE, billingCenterKey);
        return new StorageSynchronizer(storageMapping,
                                       commonIndexPrefix,
                                       indexName,
                                       bulkSize,
                                       elasticsearchClient,
                                       loader,
                                       indexService,
                                       new StorageToBillingRequestConverter(mapper, elasticsearchClient,
                                                                            StorageType.OBJECT_STORAGE,
                                                                            new GcpStoragePricingService()),
                                       DataStorageType.GS);
    }
}
