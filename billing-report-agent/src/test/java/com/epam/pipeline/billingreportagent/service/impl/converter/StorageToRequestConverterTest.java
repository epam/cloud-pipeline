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

package com.epam.pipeline.billingreportagent.service.impl.converter;

import com.amazonaws.regions.Regions;
import com.epam.pipeline.billingreportagent.model.EntityContainer;
import com.epam.pipeline.billingreportagent.model.EntityWithMetadata;
import com.epam.pipeline.billingreportagent.model.ResourceType;
import com.epam.pipeline.billingreportagent.model.StorageType;
import com.epam.pipeline.billingreportagent.model.billing.StoragePricing;
import com.epam.pipeline.billingreportagent.model.billing.StoragePricing.StoragePricingEntity;
import com.epam.pipeline.billingreportagent.service.ElasticsearchServiceClient;
import com.epam.pipeline.billingreportagent.service.ElasticsearchSynchronizer;
import com.epam.pipeline.billingreportagent.service.impl.TestUtils;
import com.epam.pipeline.billingreportagent.service.impl.loader.CloudRegionLoader;
import com.epam.pipeline.billingreportagent.service.impl.mapper.StorageBillingMapper;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.AzureBlobStorage;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.datastorage.FileShareMount;
import com.epam.pipeline.entity.datastorage.GSBucketStorage;
import com.epam.pipeline.entity.datastorage.MountType;
import com.epam.pipeline.entity.datastorage.NFSDataStorage;
import com.epam.pipeline.entity.datastorage.S3bucketDataStorage;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.entity.region.AzureRegion;
import com.epam.pipeline.entity.search.SearchDocumentType;
import com.epam.pipeline.entity.user.PipelineUser;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.DocWriteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.common.xcontent.DeprecationHandler;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.metrics.sum.ParsedSum;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RunWith(MockitoJUnitRunner.class)
@SuppressWarnings("checkstyle:MagicNumber")
public class StorageToRequestConverterTest {

    private static final String BILLING_CENTER_KEY = "billing";
    private static final Long STORAGE_ID = 1L;
    private static final String STORAGE_NAME = "TestStorage";
    private static final int DOC_ID = 2;
    private static final Long TEST_AWS_REGION_ID = 1L;
    private static final Long TEST_AZURE_REGION_ID = 2L;
    private static final Long TEST_GCP_REGION_ID = 3L;
    private static final String TEST_AZURE_REGION_NAME = "someregion";
    private static final Long TEST_NFS_MOUNT_ID = 1L;
    private static final Long TEST_AZURE_NETAPP_MOUNT_ID = 2L;
    private static final Long TEST_AZURE_FILES_MOUNT_ID = 3L;

    private static final long BYTES_IN_1_GB = 1L << 30;
    private static final String SIZE_SUM_SEARCH = "sizeSumSearch";
    private static final String REGION_FIELD = "storage_region";

    private static final String VALUE_RESULT_PATTERN_JSON = "{\"value\" : \"%d\"}";
    private static final String UNKNOWN_REGION = "unknownRegion";
    private static final String USER_NAME = "TestUser";
    private static final String GROUP_1 = "TestGroup1";
    private static final String GROUP_2 = "TestGroup2";
    private static final List<String> USER_GROUPS = Arrays.asList(GROUP_1, GROUP_2);
    private static final long STORAGE_LIMIT_TIER_1 = 51200L;
    private static final long STORAGE_LIMIT_TIER_2 = STORAGE_LIMIT_TIER_1 * 10;
    private static final LocalDateTime SYNC_END = LocalDateTime.of(2019, 11, 2, 0, 0);
    private static final LocalDateTime SYNC_START = SYNC_END.minusDays(1);
    private static final BigDecimal DAYS_IN_SYNC_MONTH = BigDecimal.valueOf(30);
    private static final String US_EAST_1 = Regions.US_EAST_1.getName().toLowerCase();
    private static final BigDecimal TIER_1_STORAGE_1_GB_DAILY_PRICE = BigDecimal.TEN;
    private static final BigDecimal TIER_2_STORAGE_1_GB_DAILY_PRICE = BigDecimal.valueOf(5);
    private static final BigDecimal OVER_TIER_2_STORAGE_1_GB_DAILY_PRICE = BigDecimal.ONE;
    private static final BigDecimal AZ_NFS_STORAGE_1_GB_DAILY_PRICE = BigDecimal.valueOf(2);

    private final PipelineUser testUser = PipelineUser.builder()
        .userName(USER_NAME)
        .groups(USER_GROUPS)
        .attributes(Collections.emptyMap())
        .build();

    private final EntityWithMetadata<PipelineUser> testUserWithMetadata = EntityWithMetadata.<PipelineUser>builder()
        .entity(testUser)
        .build();

    private ElasticsearchServiceClient elasticsearchClient = Mockito.mock(ElasticsearchServiceClient.class);
    private CloudRegionLoader regionLoader = Mockito.mock(CloudRegionLoader.class);
    private FileShareMountsService fileShareMountsService = new FileShareMountsService(regionLoader);
    private StorageToBillingRequestConverter s3Converter;
    private StorageToBillingRequestConverter gcpConverter;
    private StorageToBillingRequestConverter azureBlobConverter;
    private StorageToBillingRequestConverter nfsConverter;
    private StorageToBillingRequestConverter azureNetAppConverter;
    private StorageToBillingRequestConverter azureFilesConverter;
    private final EntityContainer<AbstractDataStorage> s3StorageContainer =
        getStorageContainer(STORAGE_ID, STORAGE_NAME, STORAGE_NAME, DataStorageType.S3);
    private final EntityContainer<AbstractDataStorage> gcpStorageContainer =
        getStorageContainer(STORAGE_ID, STORAGE_NAME, STORAGE_NAME, DataStorageType.GS);
    private final EntityContainer<AbstractDataStorage> azureStorageContainer =
        getStorageContainer(STORAGE_ID, STORAGE_NAME, STORAGE_NAME, DataStorageType.AZ);
    private final EntityContainer<AbstractDataStorage> nfsStorageContainer =
        getStorageContainer(STORAGE_ID, STORAGE_NAME, STORAGE_NAME, DataStorageType.S3, MountType.NFS);
    private final EntityContainer<AbstractDataStorage> azureNetAppStorageContainer =
        getStorageContainer(STORAGE_ID, STORAGE_NAME, STORAGE_NAME, DataStorageType.AZ, MountType.NFS);
    private final EntityContainer<AbstractDataStorage> azureFilesStorageContainer =
        getStorageContainer(STORAGE_ID, STORAGE_NAME, STORAGE_NAME, DataStorageType.AZ, MountType.SMB);

    @BeforeEach
    public void init() {
        final StoragePricingService testStoragePricing = createTieredStoragePricing();
        final StoragePricingService testAzureNfsStoragePricing = createAzureNfsPricing();
        final List<EntityContainer<AbstractCloudRegion>> regionContainers =
            Stream.of(createAwsRegionWithShare(), createAzureRegionWithShares())
                .map(region -> EntityContainer.<AbstractCloudRegion>builder()
                    .entity(region)
                    .build())
                .collect(Collectors.toList());
        Mockito.when(regionLoader.loadAllEntities()).thenReturn(regionContainers);
        fileShareMountsService.updateSharesRegions();

        s3Converter = new StorageToBillingRequestConverter(
            new StorageBillingMapper(SearchDocumentType.S3_STORAGE, BILLING_CENTER_KEY),
            elasticsearchClient,
            StorageType.OBJECT_STORAGE,
            testStoragePricing,
            StringUtils.EMPTY,
            false);
        gcpConverter = new StorageToBillingRequestConverter(
            new StorageBillingMapper(SearchDocumentType.GS_STORAGE, BILLING_CENTER_KEY),
            elasticsearchClient,
            StorageType.OBJECT_STORAGE,
            testStoragePricing,
            StringUtils.EMPTY,
            false);
        azureBlobConverter = new StorageToBillingRequestConverter(
            new StorageBillingMapper(SearchDocumentType.AZ_BLOB_STORAGE, BILLING_CENTER_KEY),
            elasticsearchClient,
            StorageType.OBJECT_STORAGE,
            testStoragePricing,
            StringUtils.EMPTY,
            false);
        nfsConverter = new StorageToBillingRequestConverter(
            new StorageBillingMapper(SearchDocumentType.NFS_STORAGE, BILLING_CENTER_KEY),
            elasticsearchClient,
            StorageType.FILE_STORAGE,
            testStoragePricing,
            StringUtils.EMPTY,
            fileShareMountsService,
            MountType.NFS,
            false);
        azureNetAppConverter = new StorageToBillingRequestConverter(
            new StorageBillingMapper(SearchDocumentType.NFS_STORAGE, BILLING_CENTER_KEY),
            elasticsearchClient,
            StorageType.FILE_STORAGE,
            testAzureNfsStoragePricing,
            StringUtils.EMPTY,
            fileShareMountsService,
            MountType.NFS,
            false);
        azureFilesConverter = new StorageToBillingRequestConverter(
            new StorageBillingMapper(SearchDocumentType.NFS_STORAGE, BILLING_CENTER_KEY),
            elasticsearchClient,
            StorageType.FILE_STORAGE,
            testAzureNfsStoragePricing,
            StringUtils.EMPTY,
            fileShareMountsService,
            MountType.SMB,
            false);
    }

    @Test
    public void testCalculateDailyCostWithGivenPrice() {
        final LocalDate syncDate = SYNC_END.toLocalDate();
        final BigDecimal priceGbMonth =
            TIER_1_STORAGE_1_GB_DAILY_PRICE.multiply(BigDecimal.valueOf(DAYS_IN_SYNC_MONTH.longValue()));
        final long dailyCost1Gb = s3Converter.calculateDailyCost(BYTES_IN_1_GB, priceGbMonth, syncDate);
        Assert.assertEquals(TIER_1_STORAGE_1_GB_DAILY_PRICE.scaleByPowerOfTen(2).longValue(), dailyCost1Gb);

        final long dailyCost1Byte = s3Converter.calculateDailyCost(1L, priceGbMonth, syncDate);
        Assert.assertEquals(OVER_TIER_2_STORAGE_1_GB_DAILY_PRICE.longValue(), dailyCost1Byte);
    }

    @Test
    public void testCalculateDailyCostWithPricingList() {
        final LocalDate syncDate = LocalDate.of(2019, 11, 2);
        final int daysInMonth = YearMonth.of(syncDate.getYear(), syncDate.getMonthValue()).lengthOfMonth();
        Assert.assertEquals(30, daysInMonth);

        final long totalSize = 3 * STORAGE_LIMIT_TIER_2;
        final long storageUsedTier2 = STORAGE_LIMIT_TIER_2 - STORAGE_LIMIT_TIER_1;
        final long storageUsedTier3 = totalSize - STORAGE_LIMIT_TIER_2;

        final BigDecimal expectedPrice = TIER_1_STORAGE_1_GB_DAILY_PRICE
            .multiply(BigDecimal.valueOf(STORAGE_LIMIT_TIER_1))
            .add(TIER_2_STORAGE_1_GB_DAILY_PRICE.multiply(BigDecimal.valueOf(storageUsedTier2)))
            .add(OVER_TIER_2_STORAGE_1_GB_DAILY_PRICE.multiply(BigDecimal.valueOf(storageUsedTier3)));

        final long dailyCostForTotalSize =
            s3Converter.calculateDailyCost(totalSize * BYTES_IN_1_GB, Regions.US_EAST_2.getName(), syncDate);

        Assert.assertEquals(expectedPrice.scaleByPowerOfTen(2).longValue(), dailyCostForTotalSize);
    }

    @Test
    public void testS3StorageConverting() throws IOException {
        testStorageConverting(s3Converter, s3StorageContainer, SearchDocumentType.S3_STORAGE,
                              TEST_AWS_REGION_ID, US_EAST_1, StorageType.OBJECT_STORAGE,
                              OVER_TIER_2_STORAGE_1_GB_DAILY_PRICE);
    }

    @Test
    public void testEFSStorageConverting() throws IOException {
        testStorageConverting(nfsConverter, nfsStorageContainer, SearchDocumentType.NFS_STORAGE,
                              TEST_AWS_REGION_ID, US_EAST_1, StorageType.FILE_STORAGE,
                              OVER_TIER_2_STORAGE_1_GB_DAILY_PRICE);
    }

    @Test
    public void testGcpStorageConverting() throws IOException {
        testStorageConverting(gcpConverter, gcpStorageContainer, SearchDocumentType.GS_STORAGE,
                              TEST_GCP_REGION_ID, US_EAST_1, StorageType.OBJECT_STORAGE,
                              OVER_TIER_2_STORAGE_1_GB_DAILY_PRICE);
    }

    @Test
    public void testAzureBlobStorageConverting() throws IOException {
        testStorageConverting(azureBlobConverter, azureStorageContainer, SearchDocumentType.AZ_BLOB_STORAGE,
                              TEST_AZURE_REGION_ID, US_EAST_1, StorageType.OBJECT_STORAGE,
                              OVER_TIER_2_STORAGE_1_GB_DAILY_PRICE);
    }

    @Test
    public void testAzureNetAppStorageConverting() throws IOException {
        testStorageConverting(azureNetAppConverter, azureNetAppStorageContainer, SearchDocumentType.NFS_STORAGE,
                              TEST_AZURE_REGION_ID, TEST_AZURE_REGION_NAME, StorageType.FILE_STORAGE,
                              AZ_NFS_STORAGE_1_GB_DAILY_PRICE);
    }

    @Test
    public void testAzureFilesStorageConverting() throws IOException {
        testStorageConverting(azureFilesConverter, azureFilesStorageContainer, SearchDocumentType.NFS_STORAGE,
                              TEST_AZURE_REGION_ID, TEST_AZURE_REGION_NAME, StorageType.FILE_STORAGE,
                              AZ_NFS_STORAGE_1_GB_DAILY_PRICE);
    }

    @Test
    public void testInvalidResponseConverting() throws IOException {
        final EntityContainer<AbstractDataStorage> s3StorageContainer =
            getStorageContainer(STORAGE_ID, STORAGE_NAME, STORAGE_NAME, DataStorageType.S3);
        createElasticsearchSearchContext(0L, true, UNKNOWN_REGION);
        final List<DocWriteRequest> requests = s3Converter.convertEntityToRequests(s3StorageContainer,
                                                                                   TestUtils.COMMON_INDEX_PREFIX,
                                                                                   SYNC_START, SYNC_END);
        Assert.assertEquals(0, requests.size());
    }

    @Test
    public void testStorageWithNoInfoConverting() throws IOException {
        final EntityContainer<AbstractDataStorage> s3StorageContainer =
            getStorageContainer(STORAGE_ID, STORAGE_NAME, STORAGE_NAME, DataStorageType.S3);
        createElasticsearchSearchContext(0L, false, US_EAST_1);
        Mockito.when(elasticsearchClient.isIndexExists(Mockito.anyString())).thenReturn(false);
        final List<DocWriteRequest> requests = s3Converter.convertEntityToRequests(s3StorageContainer,
                                                                                   TestUtils.COMMON_INDEX_PREFIX,
                                                                                   SYNC_START, SYNC_END);
        Assert.assertEquals(0, requests.size());
    }

    private void testStorageConverting(final StorageToBillingRequestConverter converter,
                                       final EntityContainer<AbstractDataStorage> storageContainer,
                                       final SearchDocumentType desiredType,
                                       final Long regionId,
                                       final String regionName,
                                       final StorageType storageType,
                                       final BigDecimal storageDailyPriceGb) throws IOException {
        final AbstractDataStorage azureStorage = storageContainer.getEntity();
        createElasticsearchSearchContext(BYTES_IN_1_GB, false, regionName);
        final DocWriteRequest request = converter.convertEntityToRequests(storageContainer,
                                                                          TestUtils.STORAGE_BILLING_PREFIX,
                                                                          SYNC_START, SYNC_END).get(0);
        final String expectedIndex = TestUtils.buildBillingIndex(TestUtils.STORAGE_BILLING_PREFIX, SYNC_START);
        final Map<String, Object> requestFieldsMap = ((IndexRequest) request).sourceAsMap();
        Assert.assertEquals(expectedIndex, request.index());
        Assert.assertEquals(desiredType.name(), requestFieldsMap.get(ElasticsearchSynchronizer.DOC_TYPE_FIELD));
        assertFields(azureStorage, requestFieldsMap, regionId, storageType, BYTES_IN_1_GB,
                     storageDailyPriceGb.scaleByPowerOfTen(2).longValue());
    }

    private void createElasticsearchSearchContext(final Long storageSize,
                                                  final boolean isEmptyResponse,
                                                  final String region) throws IOException {
        final XContentParser parser =
            XContentType.JSON.xContent().createParser(NamedXContentRegistry.EMPTY,
                                                      DeprecationHandler.THROW_UNSUPPORTED_OPERATION,
                                                      String.format(VALUE_RESULT_PATTERN_JSON, storageSize));
        final ParsedSum sumAgg = ParsedSum.fromXContent(parser, SIZE_SUM_SEARCH);
        final Aggregations aggregations = new Aggregations(Collections.singletonList(sumAgg));
        final SearchHit hit = new SearchHit(DOC_ID,
                                            StringUtils.EMPTY,
                                            new Text(StringUtils.EMPTY),
                                            Collections.emptyMap());
        final XContentBuilder jsonBuilder = XContentFactory.jsonBuilder()
            .startObject()
            .field(REGION_FIELD, region)
            .endObject();

        hit.sourceRef(BytesReference.bytes(jsonBuilder));
        final SearchHits hits = isEmptyResponse
                                ? new SearchHits(new SearchHit[0], 0, 0)
                                : new SearchHits(org.assertj.core.util.Arrays.array(hit), 1, 1);

        final SearchResponse response = Mockito.mock(SearchResponse.class);
        Mockito.when(response.getAggregations()).thenReturn(aggregations);
        Mockito.when(response.getHits()).thenReturn(hits);
        Mockito.when(elasticsearchClient.isIndexExists(Mockito.anyString())).thenReturn(true);
        Mockito.when(elasticsearchClient.search(Mockito.any())).thenReturn(response);
    }

    private void assertFields(final AbstractDataStorage storage, final Map<String, Object> fieldMap,
                              final Long region, final StorageType storageType, final Long usage, final Long cost) {
        Assert.assertEquals(storage.getId().intValue(), fieldMap.get("storage_id"));
        Assert.assertEquals(ResourceType.STORAGE.toString(), fieldMap.get("resource_type"));
        Assert.assertEquals(region.intValue(), fieldMap.get("cloudRegionId"));
        Assert.assertEquals(storage.getType().toString(), fieldMap.get("provider"));
        Assert.assertEquals(storageType.toString(), fieldMap.get("storage_type"));
        Assert.assertEquals(testUser.getUserName(), fieldMap.get("owner"));
        Assert.assertEquals(usage.intValue(), fieldMap.get("usage_bytes"));
        Assert.assertEquals(cost.intValue(), fieldMap.get("cost"));
        TestUtils.verifyStringArray(USER_GROUPS, fieldMap.get("groups"));
    }

    private StoragePricingService createTieredStoragePricing() {
        final StoragePricing pricingUsEast1 = new StoragePricing();
        pricingUsEast1.addPrice(new StoragePricingEntity(0L,
                                                         STORAGE_LIMIT_TIER_1 * BYTES_IN_1_GB,
                                                         OVER_TIER_2_STORAGE_1_GB_DAILY_PRICE
                                                             .multiply(DAYS_IN_SYNC_MONTH)));
        final StoragePricing pricingUsEast2 = new StoragePricing();
        final long endRangeBytesTier1 = STORAGE_LIMIT_TIER_1 * BYTES_IN_1_GB;
        final long endRangeBytesTier2 = STORAGE_LIMIT_TIER_2 * BYTES_IN_1_GB;
        pricingUsEast2.addPrice(new StoragePricingEntity(0L,
                                                         endRangeBytesTier1,
                                                         TIER_1_STORAGE_1_GB_DAILY_PRICE.multiply(DAYS_IN_SYNC_MONTH)));
        pricingUsEast2.addPrice(new StoragePricingEntity(endRangeBytesTier1,
                                                         endRangeBytesTier2,
                                                         TIER_2_STORAGE_1_GB_DAILY_PRICE.multiply(DAYS_IN_SYNC_MONTH)));
        pricingUsEast2.addPrice(new StoragePricingEntity(endRangeBytesTier2,
                                                         Long.MAX_VALUE,
                                                         OVER_TIER_2_STORAGE_1_GB_DAILY_PRICE
                                                             .multiply(DAYS_IN_SYNC_MONTH)));

        final Map<String, StoragePricing> testPriceList = new HashMap<>();
        testPriceList.put(Regions.US_EAST_1.getName(), pricingUsEast1);
        testPriceList.put(Regions.US_EAST_2.getName(), pricingUsEast2);

        final StoragePricingService testStoragePricing = Mockito.spy(new StoragePricingService(testPriceList));
        Mockito.doNothing().when(testStoragePricing).updatePrices();
        return testStoragePricing;
    }

    private StoragePricingService createAzureNfsPricing() {
        final StoragePricing pricingAzureNfs = new StoragePricing();
        pricingAzureNfs
            .addPrice(new StoragePricingEntity(0L,
                                               Long.MAX_VALUE,
                                               AZ_NFS_STORAGE_1_GB_DAILY_PRICE.multiply(DAYS_IN_SYNC_MONTH)));
        final Map<String, StoragePricing> testPriceListAzureNfs = new HashMap<>();
        testPriceListAzureNfs.put(TEST_AZURE_REGION_NAME, pricingAzureNfs);
        final StoragePricingService testAzureNfsStoragePricing =
            Mockito.spy(new StoragePricingService(testPriceListAzureNfs));
        Mockito.doNothing().when(testAzureNfsStoragePricing).updatePrices();
        return testAzureNfsStoragePricing;
    }

    private AwsRegion createAwsRegionWithShare() {
        final AwsRegion region = new AwsRegion();
        region.setId(TEST_AWS_REGION_ID);
        final FileShareMount fileShareMount = new FileShareMount();
        fileShareMount.setId(TEST_NFS_MOUNT_ID);
        fileShareMount.setRegionId(TEST_AWS_REGION_ID);
        fileShareMount.setMountType(MountType.NFS);
        region.setFileShareMounts(Collections.singletonList(fileShareMount));
        return region;
    }

    private AzureRegion createAzureRegionWithShares() {
        final AzureRegion region = new AzureRegion();
        region.setId(TEST_AZURE_REGION_ID);
        region.setRegionCode(TEST_AZURE_REGION_NAME);
        final FileShareMount netAppShareMount =
            getFileShareMount(TEST_AZURE_REGION_ID, TEST_AZURE_NETAPP_MOUNT_ID, MountType.NFS);
        final FileShareMount smbShareMount =
            getFileShareMount(TEST_AZURE_REGION_ID, TEST_AZURE_FILES_MOUNT_ID, MountType.SMB);
        region.setFileShareMounts(Arrays.asList(netAppShareMount, smbShareMount));
        return region;
    }

    private FileShareMount getFileShareMount(final Long regionId, final Long shareId, final MountType shareType) {
        final FileShareMount netAppShareMount = new FileShareMount();
        netAppShareMount.setId(shareId);
        netAppShareMount.setRegionId(regionId);
        netAppShareMount.setMountType(shareType);
        return netAppShareMount;
    }

    private EntityContainer<AbstractDataStorage> getStorageContainer(final Long id,
                                                                     final String name,
                                                                     final String path,
                                                                     final DataStorageType storageType) {
        return getStorageContainer(id, name, path, storageType, null);
    }

    private EntityContainer<AbstractDataStorage> getStorageContainer(final Long id,
                                                                     final String name,
                                                                     final String path,
                                                                     final DataStorageType storageType,
                                                                     final MountType shareMountType) {
        final AbstractDataStorage storage;
        if (shareMountType == null) {
            switch (storageType) {
                case GS:
                    final GSBucketStorage gsBucketStorage = new GSBucketStorage(id, name, path, null, null);
                    gsBucketStorage.setRegionId(TEST_GCP_REGION_ID);
                    storage = gsBucketStorage;
                    break;
                case AZ:
                    final AzureBlobStorage azureBlobStorage = new AzureBlobStorage(id, name, path, null, null);
                    azureBlobStorage.setRegionId(TEST_AZURE_REGION_ID);
                    storage = azureBlobStorage;
                    break;
                default:
                    final S3bucketDataStorage s3bucketDataStorage = new S3bucketDataStorage(id, name, path);
                    s3bucketDataStorage.setRegionId(TEST_AWS_REGION_ID);
                    storage = s3bucketDataStorage;
                    break;
            }
        } else {
            final Long shareMountId = storageType == DataStorageType.AZ
                                      ? shareMountType == MountType.NFS
                                        ? TEST_AZURE_NETAPP_MOUNT_ID
                                        : TEST_AZURE_FILES_MOUNT_ID
                                      : TEST_NFS_MOUNT_ID;
            storage = getNfsDataStorage(id, name, path, shareMountId);
        }
        storage.setCreatedDate(Date.from(SYNC_START.atZone(ZoneId.systemDefault()).toInstant()));
        return EntityContainer.<AbstractDataStorage>builder()
            .entity(storage)
            .owner(testUserWithMetadata)
            .build();
    }

    private AbstractDataStorage getNfsDataStorage(final Long id, final String name, final String path,
                                                  final Long shareMountId) {
        AbstractDataStorage storage;
        final NFSDataStorage nfsDataStorage = new NFSDataStorage(id, name, path);
        nfsDataStorage.setFileShareMountId(shareMountId);
        storage = nfsDataStorage;
        return storage;
    }
}
