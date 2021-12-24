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
import com.epam.pipeline.billingreportagent.service.DocumentMapper;
import com.epam.pipeline.billingreportagent.service.ElasticsearchDailySynchronizer;
import com.epam.pipeline.billingreportagent.service.ElasticsearchMergingFrame;
import com.epam.pipeline.billingreportagent.service.ElasticsearchMergingSynchronizer;
import com.epam.pipeline.billingreportagent.service.ElasticsearchServiceClient;
import com.epam.pipeline.billingreportagent.service.impl.BulkRequestSender;
import com.epam.pipeline.billingreportagent.service.impl.ElasticIndexService;
import com.epam.pipeline.billingreportagent.service.impl.converter.AwsStoragePriceListLoader;
import com.epam.pipeline.billingreportagent.service.impl.converter.AzureBlobStoragePriceListLoader;
import com.epam.pipeline.billingreportagent.service.impl.converter.AzureEARawPriceLoader;
import com.epam.pipeline.billingreportagent.service.impl.converter.AzureFilesStoragePriceListLoader;
import com.epam.pipeline.billingreportagent.service.impl.converter.AzureNetAppStoragePriceListLoader;
import com.epam.pipeline.billingreportagent.service.impl.converter.AzureRateCardRawPriceLoader;
import com.epam.pipeline.billingreportagent.service.impl.converter.FileShareMountsService;
import com.epam.pipeline.billingreportagent.service.impl.converter.GcpStoragePriceListLoader;
import com.epam.pipeline.billingreportagent.service.impl.converter.PriceLoadingMode;
import com.epam.pipeline.billingreportagent.service.impl.converter.StoragePricingService;
import com.epam.pipeline.billingreportagent.service.impl.converter.StorageToBillingRequestConverter;
import com.epam.pipeline.billingreportagent.service.impl.loader.CloudRegionLoader;
import com.epam.pipeline.billingreportagent.service.impl.loader.PipelineRunLoader;
import com.epam.pipeline.billingreportagent.service.impl.loader.StorageLoader;
import com.epam.pipeline.billingreportagent.service.impl.mapper.RunBillingMapper;
import com.epam.pipeline.billingreportagent.service.impl.mapper.StorageBillingMapper;
import com.epam.pipeline.billingreportagent.service.impl.synchronizer.PipelineRunSynchronizer;
import com.epam.pipeline.billingreportagent.service.impl.synchronizer.StorageSynchronizer;
import com.epam.pipeline.billingreportagent.service.impl.synchronizer.merging.EntityDocumentLoader;
import com.epam.pipeline.billingreportagent.service.impl.synchronizer.merging.InitialMergingSynchronizer;
import com.epam.pipeline.billingreportagent.service.impl.synchronizer.merging.MergingSynchronizer;
import com.epam.pipeline.billingreportagent.service.impl.synchronizer.merging.RunBillingDocumentLoader;
import com.epam.pipeline.billingreportagent.service.impl.synchronizer.merging.StorageBillingDocumentLoader;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.datastorage.MountType;
import com.epam.pipeline.entity.search.SearchDocumentType;
import org.apache.commons.lang3.StringUtils;
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

    @Value("${sync.bulk.insert.timeout:1000}")
    private long insertTimeout;

    @Value("${sync.storage.index.mapping}")
    private String storageMapping;

    @Value("${sync.storage.index.name}")
    private String storageIndexName;

    @Value("${sync.billing.center.key}")
    private String billingCenterKey;

    @Value("${sync.storage.file.index.pattern}")
    private String fileIndexPattern;

    @Value("${sync.storage.historical.billing.generation:false}")
    private boolean enableStorageHistoricalBillingGeneration;

    @Value("${sync.storage.period.recalculate.enable}")
    private boolean storagePeriodRecalculate;

    @Value("${sync.run.index.mapping}")
    private String runMapping;

    @Value("${sync.run.index.name}")
    private String runIndexName;

    @Value("${sync.run.period.recalculate.enable}")
    private boolean runPeriodRecalculate;

    @Bean
    public BulkRequestSender bulkRequestSender(
            final ElasticsearchServiceClient elasticsearchClient) {
        return new BulkRequestSender(elasticsearchClient);
    }

    @Bean
    @ConditionalOnProperty(value = "sync.run.disable", matchIfMissing = true, havingValue = FALSE)
    public ElasticsearchDailySynchronizer pipelineRunSynchronizer(
            final RunBillingMapper mapper,
            final PipelineRunLoader loader,
            final ElasticIndexService indexService,
            final ElasticsearchServiceClient elasticsearchClient) {
        return new PipelineRunSynchronizer(runMapping,
                commonIndexPrefix,
                runIndexName,
                bulkSize,
                insertTimeout,
                elasticsearchClient,
                indexService,
                mapper,
                loader);
    }

    @Bean
    @ConditionalOnProperty(value = "sync.run.period.monthly.disable", matchIfMissing = true, havingValue = FALSE)
    public ElasticsearchMergingSynchronizer pipelineRunMonthlySynchronizer(
            final ElasticsearchServiceClient elasticsearchClient,
            final ElasticIndexService indexService,
            final RunBillingDocumentLoader loader,
            final DocumentMapper mapper) {
        return getRunMergingSynchronizer(elasticsearchClient, indexService, loader, mapper,
                ElasticsearchMergingFrame.MONTH);
    }

    @Bean
    @ConditionalOnProperty(value = "sync.run.period.yearly.disable", matchIfMissing = true, havingValue = FALSE)
    public ElasticsearchMergingSynchronizer pipelineRunYearlySynchronizer(
            final ElasticsearchServiceClient elasticsearchClient,
            final ElasticIndexService indexService,
            final RunBillingDocumentLoader loader,
            final DocumentMapper mapper) {
        return getRunMergingSynchronizer(elasticsearchClient, indexService, loader, mapper,
                ElasticsearchMergingFrame.YEAR);
    }

    private ElasticsearchMergingSynchronizer getRunMergingSynchronizer(
            final ElasticsearchServiceClient elasticsearchClient,
            final ElasticIndexService indexService,
            final EntityDocumentLoader loader,
            final DocumentMapper mapper,
            final ElasticsearchMergingFrame frame) {
        final ElasticsearchMergingSynchronizer joiner = new MergingSynchronizer(
                getMergingSynchronizerName("Run", frame),
                runMapping,
                commonIndexPrefix,
                runIndexName,
                bulkSize,
                elasticsearchClient,
                indexService,
                loader,
                mapper,
                frame);
        return runPeriodRecalculate ? new InitialMergingSynchronizer(joiner) : joiner;
    }

    @Bean
    @ConditionalOnProperty(value = "sync.storage.s3.disable", matchIfMissing = true, havingValue = FALSE)
    public ElasticsearchDailySynchronizer s3Synchronizer(final StorageLoader loader,
                                                         final ElasticIndexService indexService,
                                                         final ElasticsearchServiceClient elasticsearchClient,
                                                         final @Value("${sync.storage.price.load.mode:api}")
                                                                 String priceMode,
                                                         final @Value("${sync.aws.json.price.endpoint.template}")
                                                                 String endpointTemplate) {
        final StorageBillingMapper mapper = new StorageBillingMapper(SearchDocumentType.S3_STORAGE, billingCenterKey);
        final StoragePricingService pricingService =
                new StoragePricingService(new AwsStoragePriceListLoader("AmazonS3",
                        PriceLoadingMode.valueOf(priceMode.toUpperCase()),
                        endpointTemplate));
        return new StorageSynchronizer(storageMapping,
                commonIndexPrefix,
                storageIndexName,
                bulkSize,
                insertTimeout,
                elasticsearchClient,
                loader,
                indexService,
                new StorageToBillingRequestConverter(mapper, elasticsearchClient,
                        StorageType.OBJECT_STORAGE,
                        pricingService,
                        fileIndexPattern,
                        enableStorageHistoricalBillingGeneration),
                DataStorageType.S3);
    }

    @Bean
    @ConditionalOnProperty(value = "sync.storage.efs.disable", matchIfMissing = true, havingValue = FALSE)
    public ElasticsearchDailySynchronizer efsSynchronizer(final StorageLoader loader,
                                                          final ElasticIndexService indexService,
                                                          final ElasticsearchServiceClient elasticsearchClient,
                                                          final @Value("${sync.storage.price.load.mode:api}")
                                                                  String priceMode,
                                                          final @Value("${sync.aws.json.price.endpoint.template}")
                                                                  String endpointTemplate,
                                                          final FileShareMountsService fileShareMountsService) {
        final StorageBillingMapper mapper = new StorageBillingMapper(SearchDocumentType.NFS_STORAGE, billingCenterKey);
        final StoragePricingService pricingService =
                new StoragePricingService(new AwsStoragePriceListLoader("AmazonEFS",
                        PriceLoadingMode.valueOf(priceMode.toUpperCase()),
                        endpointTemplate));
        return new StorageSynchronizer(storageMapping,
                commonIndexPrefix,
                storageIndexName,
                bulkSize,
                insertTimeout,
                elasticsearchClient,
                loader,
                indexService,
                new StorageToBillingRequestConverter(mapper, elasticsearchClient,
                        StorageType.FILE_STORAGE,
                        pricingService,
                        fileIndexPattern,
                        fileShareMountsService,
                        MountType.NFS,
                        enableStorageHistoricalBillingGeneration),
                DataStorageType.NFS);
    }

    @Bean
    @ConditionalOnProperty(value = "sync.storage.gs.disable", matchIfMissing = true, havingValue = FALSE)
    public ElasticsearchDailySynchronizer gsSynchronizer(final StorageLoader loader,
                                                         final ElasticIndexService indexService,
                                                         final ElasticsearchServiceClient elasticsearchClient) {
        final StorageBillingMapper mapper = new StorageBillingMapper(SearchDocumentType.GS_STORAGE, billingCenterKey);
        final StoragePricingService pricingService =
                new StoragePricingService(new GcpStoragePriceListLoader());
        return new StorageSynchronizer(storageMapping,
                commonIndexPrefix,
                storageIndexName,
                bulkSize,
                insertTimeout,
                elasticsearchClient,
                loader,
                indexService,
                new StorageToBillingRequestConverter(mapper, elasticsearchClient,
                        StorageType.OBJECT_STORAGE,
                        pricingService,
                        fileIndexPattern,
                        enableStorageHistoricalBillingGeneration),
                DataStorageType.GS);
    }

    @Bean
    @ConditionalOnProperty(value = "sync.storage.azure-blob.disable", matchIfMissing = true, havingValue = FALSE)
    public ElasticsearchDailySynchronizer azureBlobSynchronizer(
        final StorageLoader loader,
        final ElasticIndexService indexService,
        final ElasticsearchServiceClient elasticsearchClient,
        final CloudRegionLoader regionLoader,
        final AzureRateCardRawPriceLoader rawRateCardPriceLoader,
        final AzureEARawPriceLoader rawEAPriceLoader,
        final @Value("${sync.storage.azure-blob.category:General Block Blob}") String blobStorageCategory,
        final @Value("${sync.storage.azure-blob.redundancy:LRS}") String redundancyType) {
        final StorageBillingMapper mapper = new StorageBillingMapper(SearchDocumentType.AZ_BLOB_STORAGE,
                billingCenterKey);
        final StoragePricingService pricingService =
                new StoragePricingService(new AzureBlobStoragePriceListLoader(regionLoader,
                                                                              rawRateCardPriceLoader,
                                                                              rawEAPriceLoader,
                                                                              blobStorageCategory,
                                                                              redundancyType));
        return new StorageSynchronizer(storageMapping,
                commonIndexPrefix,
                storageIndexName,
                bulkSize,
                insertTimeout,
                elasticsearchClient,
                loader,
                indexService,
                new StorageToBillingRequestConverter(mapper, elasticsearchClient,
                        StorageType.OBJECT_STORAGE,
                        pricingService,
                        fileIndexPattern,
                        enableStorageHistoricalBillingGeneration),
                DataStorageType.AZ);
    }

    @Bean
    @ConditionalOnProperty(value = "sync.storage.azure-netapp.disable", matchIfMissing = true, havingValue = FALSE)
    public ElasticsearchDailySynchronizer azureNetAppSynchronizer(
            final StorageLoader loader,
            final ElasticIndexService indexService,
            final ElasticsearchServiceClient elasticsearchClient,
            final FileShareMountsService fileShareMountsService,
            final CloudRegionLoader regionLoader,
            final AzureRateCardRawPriceLoader rawRateCardPriceLoader,
            final AzureEARawPriceLoader rawEAPriceLoader,
            final @Value("${sync.storage.azure-netapp.tier:Standard}")
                    String storageTier) {
        final StorageBillingMapper mapper = new StorageBillingMapper(SearchDocumentType.NFS_STORAGE, billingCenterKey);
        final StoragePricingService pricingService =
                new StoragePricingService(new AzureNetAppStoragePriceListLoader(regionLoader, rawRateCardPriceLoader, rawEAPriceLoader, storageTier));
        return new StorageSynchronizer(storageMapping,
                commonIndexPrefix,
                storageIndexName,
                bulkSize,
                insertTimeout,
                elasticsearchClient,
                loader,
                indexService,
                new StorageToBillingRequestConverter(mapper, elasticsearchClient,
                        StorageType.FILE_STORAGE,
                        pricingService,
                        fileIndexPattern,
                        fileShareMountsService,
                        MountType.NFS,
                        enableStorageHistoricalBillingGeneration),
                DataStorageType.NFS);
    }

    @Bean
    @ConditionalOnProperty(value = "sync.storage.azure-files.disable", matchIfMissing = true, havingValue = FALSE)
    public ElasticsearchDailySynchronizer azureFilesSynchronizer(
            final StorageLoader loader,
            final ElasticIndexService indexService,
            final ElasticsearchServiceClient elasticsearchClient,
            final FileShareMountsService fileShareMountsService,
            final CloudRegionLoader regionLoader,
            final AzureRateCardRawPriceLoader rawRateCardPriceLoader,
            final AzureEARawPriceLoader rawEAPriceLoader,
            final @Value("${sync.storage.azure-files.tier:Cool LRS}")
                    String storageTier) {
        final StorageBillingMapper mapper = new StorageBillingMapper(SearchDocumentType.NFS_STORAGE, billingCenterKey);
        final StoragePricingService pricingService =
            new StoragePricingService(new AzureFilesStoragePriceListLoader(regionLoader, rawRateCardPriceLoader, rawEAPriceLoader, storageTier));
        return new StorageSynchronizer(storageMapping,
                                       commonIndexPrefix,
                                       storageIndexName,
                                       bulkSize,
                                       insertTimeout,
                                       elasticsearchClient,
                                       loader,
                                       indexService,
                                       new StorageToBillingRequestConverter(mapper, elasticsearchClient,
                                                                            StorageType.FILE_STORAGE,
                                                                            pricingService,
                                                                            fileIndexPattern,
                                                                            fileShareMountsService,
                                                                            MountType.SMB,
                                                                            enableStorageHistoricalBillingGeneration),
                                       DataStorageType.NFS);
    }

    @Bean
    @ConditionalOnProperty(value = "sync.storage.period.monthly.disable", matchIfMissing = true, havingValue = FALSE)
    public ElasticsearchMergingSynchronizer storageMonthlySynchronizer(
            final ElasticsearchServiceClient elasticsearchClient,
            final ElasticIndexService indexService,
            final StorageBillingDocumentLoader loader,
            final DocumentMapper mapper) {
        return getStorageMergingSynchronizer(elasticsearchClient, indexService, loader, mapper,
                ElasticsearchMergingFrame.MONTH);
    }

    @Bean
    @ConditionalOnProperty(value = "sync.storage.period.yearly.disable", matchIfMissing = true, havingValue = FALSE)
    public ElasticsearchMergingSynchronizer storageYearlySynchronizer(
            final ElasticsearchServiceClient elasticsearchClient,
            final ElasticIndexService indexService,
            final StorageBillingDocumentLoader loader,
            final DocumentMapper mapper) {
        return getStorageMergingSynchronizer(elasticsearchClient, indexService, loader, mapper,
                ElasticsearchMergingFrame.YEAR);
    }

    private ElasticsearchMergingSynchronizer getStorageMergingSynchronizer(
            final ElasticsearchServiceClient elasticsearchClient,
            final ElasticIndexService indexService,
            final EntityDocumentLoader loader,
            final DocumentMapper mapper,
            final ElasticsearchMergingFrame frame) {
        final ElasticsearchMergingSynchronizer joiner = new MergingSynchronizer(
                getMergingSynchronizerName("Storage", frame),
                storageMapping,
                commonIndexPrefix,
                storageIndexName,
                bulkSize,
                elasticsearchClient,
                indexService,
                loader,
                mapper,
                frame);
        return storagePeriodRecalculate ? new InitialMergingSynchronizer(joiner) : joiner;
    }

    private String getMergingSynchronizerName(final String entity, final ElasticsearchMergingFrame frame) {
        return String.format("%s%slyMergingSynchronizer", entity,
                StringUtils.capitalize(StringUtils.lowerCase(frame.name())));
    }

}
