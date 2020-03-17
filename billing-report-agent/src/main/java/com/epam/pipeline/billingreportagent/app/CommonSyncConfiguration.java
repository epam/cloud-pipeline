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
import com.epam.pipeline.billingreportagent.service.impl.converter.AwsStoragePriceListLoader;
import com.epam.pipeline.billingreportagent.service.impl.converter.AzureStoragePriceListLoader;
import com.epam.pipeline.billingreportagent.service.impl.converter.GcpStoragePriceListLoader;
import com.epam.pipeline.billingreportagent.service.impl.converter.StoragePricingService;
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
    private static final String STORAGE_SYNC_DISABLE_PROP = "sync.storage.disable";

    @Value("${sync.index.common.prefix}")
    private String commonIndexPrefix;

    @Value("${sync.bulk.insert.size:1000}")
    private int bulkSize;

    @Value("${sync.storage.index.mapping}")
    private  String storageMapping;

    @Value("${sync.storage.index.name}")
    private  String storageIndexName;

    @Value("${sync.billing.center.key}")
    private String billingCenterKey;

    @Value("${sync.storage.file.index.pattern}")
    private  String fileIndexPattern;

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
        final @Value("${sync.run.index.name}") String runIndexName,
        final @Value("${sync.run.index.mapping}") String runMapping) {
        return new PipelineRunSynchronizer(runMapping,
                                           commonIndexPrefix,
                                           runIndexName,
                                           bulkSize,
                                           elasticsearchClient,
                                           indexService,
                                           mapper,
                                           loader);
    }

    @Bean
    @ConditionalOnProperty(value = STORAGE_SYNC_DISABLE_PROP, matchIfMissing = true, havingValue = FALSE)
    public StorageSynchronizer s3Synchronizer(final StorageLoader loader,
                                              final ElasticIndexService indexService,
                                              final ElasticsearchServiceClient elasticsearchClient) {
        final StorageBillingMapper mapper = new StorageBillingMapper(SearchDocumentType.S3_STORAGE, billingCenterKey);
        final StoragePricingService pricingService =
            new StoragePricingService(new AwsStoragePriceListLoader("AmazonS3"));
        return new StorageSynchronizer(storageMapping,
                                       commonIndexPrefix,
                                       storageIndexName,
                                       bulkSize,
                                       elasticsearchClient,
                                       loader,
                                       indexService,
                                       new StorageToBillingRequestConverter(mapper, elasticsearchClient,
                                                                            StorageType.OBJECT_STORAGE,
                                                                            pricingService,
                                                                            fileIndexPattern),
                                       DataStorageType.S3);
    }

    @Bean
    @ConditionalOnProperty(value = STORAGE_SYNC_DISABLE_PROP, matchIfMissing = true, havingValue = FALSE)
    public StorageSynchronizer efsSynchronizer(final StorageLoader loader,
                                               final ElasticIndexService indexService,
                                               final ElasticsearchServiceClient elasticsearchClient) {
        final StorageBillingMapper mapper = new StorageBillingMapper(SearchDocumentType.NFS_STORAGE, billingCenterKey);
        final StoragePricingService pricingService =
            new StoragePricingService(new AwsStoragePriceListLoader("AmazonEFS"));
        return new StorageSynchronizer(storageMapping,
                                       commonIndexPrefix,
                                       storageIndexName,
                                       bulkSize,
                                       elasticsearchClient,
                                       loader,
                                       indexService,
                                       new StorageToBillingRequestConverter(mapper, elasticsearchClient,
                                                                            StorageType.FILE_STORAGE,
                                                                            pricingService,
                                                                            fileIndexPattern),
                                       DataStorageType.NFS);
    }

    @Bean
    @ConditionalOnProperty(value = STORAGE_SYNC_DISABLE_PROP, matchIfMissing = true, havingValue = FALSE)
    public StorageSynchronizer gsSynchronizer(final StorageLoader loader,
                                              final ElasticIndexService indexService,
                                              final ElasticsearchServiceClient elasticsearchClient) {
        final StorageBillingMapper mapper = new StorageBillingMapper(SearchDocumentType.GS_STORAGE, billingCenterKey);
        final StoragePricingService pricingService =
            new StoragePricingService(new GcpStoragePriceListLoader());
        return new StorageSynchronizer(storageMapping,
                                       commonIndexPrefix,
                                       storageIndexName,
                                       bulkSize,
                                       elasticsearchClient,
                                       loader,
                                       indexService,
                                       new StorageToBillingRequestConverter(mapper, elasticsearchClient,
                                                                            StorageType.OBJECT_STORAGE,
                                                                            pricingService,
                                                                            fileIndexPattern),
                                       DataStorageType.GS);
    }

    @Bean
    @ConditionalOnProperty(value = STORAGE_SYNC_DISABLE_PROP, matchIfMissing = true, havingValue = FALSE)
    public StorageSynchronizer azureSynchronizer(final @Value("${sync.storage.azure.auth.file}") String authFile,
                                                 final @Value("${sync.storage.azure.offer.id}") String offerId,
                                                 final StorageLoader loader,
                                                 final ElasticIndexService indexService,
                                                 final ElasticsearchServiceClient elasticsearchClient) {
        final StorageBillingMapper mapper = new StorageBillingMapper(SearchDocumentType.AZ_BLOB_STORAGE,
                                                                     billingCenterKey);
        final StoragePricingService pricingService =
            new StoragePricingService(new AzureStoragePriceListLoader(offerId, authFile));
        return new StorageSynchronizer(storageMapping,
                                       commonIndexPrefix,
                                       storageIndexName,
                                       bulkSize,
                                       elasticsearchClient,
                                       loader,
                                       indexService,
                                       new StorageToBillingRequestConverter(mapper, elasticsearchClient,
                                                                            StorageType.OBJECT_STORAGE,
                                                                            pricingService,
                                                                            fileIndexPattern),
                                       DataStorageType.AZ);
    }
}
