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
import com.epam.pipeline.billingreportagent.service.impl.mapper.StorageBillingMapper;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.datastorage.NFSDataStorage;
import com.epam.pipeline.entity.datastorage.S3bucketDataStorage;
import com.epam.pipeline.entity.search.SearchDocumentType;
import com.epam.pipeline.entity.user.PipelineUser;
import org.apache.commons.lang3.StringUtils;
import org.assertj.core.util.Arrays;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(MockitoJUnitRunner.class)
@SuppressWarnings("checkstyle:MagicNumber")
public class AwsStorageToRequestConverterTest {

    private static final String BILLING_CENTER_KEY = "billing";
    private static final Long STORAGE_ID = 1L;
    private static final String STORAGE_NAME = "TestStorage";
    private static final int DOC_ID = 2;

    private static final long BYTES_IN_1_GB = 1L << 30;
    private static final String SIZE_SUM_SEARCH = "sizeSumSearch";
    private static final String REGION_FIELD = "storage_region";

    private static final String VALUE_RESULT_PATTERN_JSON = "{\"value\" : \"%d\"}";
    private static final String UNKNOWN_REGION = "unknownRegion";
    private static final String USER_NAME = "TestUser";
    private static final String GROUP_1 = "TestGroup1";
    private static final String GROUP_2 = "TestGroup2";
    private static final List<String> USER_GROUPS = java.util.Arrays.asList(GROUP_1, GROUP_2);
    private static final long STORAGE_LIMIT_TIER_1 = 51200L;
    private static final long STORAGE_LIMIT_TIER_2 = STORAGE_LIMIT_TIER_1 * 10;
    private static final LocalDateTime SYNC_END = LocalDateTime.of(2019, 11, 2, 0, 0);
    private static final LocalDateTime SYNC_START = SYNC_END.minusDays(1);
    private static final BigDecimal DAYS_IN_SYNC_MONTH = BigDecimal.valueOf(30);
    private static final String US_EAST_1 = Regions.US_EAST_1.getName().toLowerCase();

    private final PipelineUser testUser = PipelineUser.builder()
        .userName(USER_NAME)
        .groups(USER_GROUPS)
        .attributes(Collections.emptyMap())
        .build();

    private final EntityWithMetadata<PipelineUser> testUserWithMetadata = EntityWithMetadata.<PipelineUser>builder()
            .entity(testUser)
            .build();

    private ElasticsearchServiceClient elasticsearchClient = Mockito.mock(ElasticsearchServiceClient.class);
    private AwsStorageToBillingRequestConverter s3Converter;
    private AwsStorageToBillingRequestConverter nfsConverter;
    private final EntityContainer<AbstractDataStorage> s3StorageContainer =
        getStorageContainer(STORAGE_ID, STORAGE_NAME, STORAGE_NAME, DataStorageType.S3);
    private final EntityContainer<AbstractDataStorage> nfsStorageContainer =
        getStorageContainer(STORAGE_ID, STORAGE_NAME, STORAGE_NAME, DataStorageType.NFS);

    @BeforeEach
    public void init() {
        final StoragePricing pricingUsEast1 = new StoragePricing();
        pricingUsEast1.addPrice(new StoragePricingEntity(0L,
                                                         STORAGE_LIMIT_TIER_1 * BYTES_IN_1_GB,
                                                         BigDecimal.ONE.multiply(DAYS_IN_SYNC_MONTH)));
        final StoragePricing pricingUsEast2 = new StoragePricing();
        final long endRangeBytesTier1 = STORAGE_LIMIT_TIER_1 * BYTES_IN_1_GB;
        final long endRangeBytesTier2 = STORAGE_LIMIT_TIER_2 * BYTES_IN_1_GB;
        pricingUsEast2.addPrice(new StoragePricingEntity(0L,
                                                         endRangeBytesTier1,
                                                         BigDecimal.TEN.multiply(DAYS_IN_SYNC_MONTH)));
        pricingUsEast2.addPrice(new StoragePricingEntity(endRangeBytesTier1,
                                                         endRangeBytesTier2,
                                                         BigDecimal.valueOf(5).multiply(DAYS_IN_SYNC_MONTH)));
        pricingUsEast2.addPrice(new StoragePricingEntity(endRangeBytesTier2,
                                                         Long.MAX_VALUE,
                                                         BigDecimal.ONE.multiply(DAYS_IN_SYNC_MONTH)));

        final Map<Regions, StoragePricing> testPriceList = new HashMap<>();
        testPriceList.put(Regions.US_EAST_1, pricingUsEast1);
        testPriceList.put(Regions.US_EAST_2, pricingUsEast2);

        final AwsStorageServicePricing testStoragePricing =
            Mockito.spy(new AwsStorageServicePricing(StringUtils.EMPTY, testPriceList));
        Mockito.doNothing().when(testStoragePricing).updatePrices();

        s3Converter = new AwsStorageToBillingRequestConverter(
                new StorageBillingMapper(SearchDocumentType.S3_STORAGE, BILLING_CENTER_KEY),
                elasticsearchClient,
                StorageType.OBJECT_STORAGE,
                testStoragePricing);
        nfsConverter = new AwsStorageToBillingRequestConverter(
                new StorageBillingMapper(SearchDocumentType.NFS_STORAGE, BILLING_CENTER_KEY),
                elasticsearchClient,
                StorageType.FILE_STORAGE,
                testStoragePricing);
    }

    @Test
    public void testCalculateDailyCostWithGivenPrice() {
        final LocalDate syncDate = SYNC_END.toLocalDate();
        final BigDecimal priceGbMonth = BigDecimal.TEN.multiply(BigDecimal.valueOf(DAYS_IN_SYNC_MONTH.longValue()));
        final long dailyCost1Gb = s3Converter.calculateDailyCost(BYTES_IN_1_GB, priceGbMonth, syncDate);
        Assert.assertEquals(BigDecimal.TEN.scaleByPowerOfTen(2).longValue(), dailyCost1Gb);

        final long dailyCost1Byte = s3Converter.calculateDailyCost(1L, priceGbMonth, syncDate);
        Assert.assertEquals(BigDecimal.ONE.longValue(), dailyCost1Byte);
    }

    @Test
    public void testCalculateDailyCostWithPricingList() {
        final LocalDate syncDate = LocalDate.of(2019, 11, 2);
        final int daysInMonth = YearMonth.of(syncDate.getYear(), syncDate.getMonthValue()).lengthOfMonth();
        Assert.assertEquals(30, daysInMonth);

        final long totalSize = 3 * STORAGE_LIMIT_TIER_2;
        final long storageUsedTier2 = STORAGE_LIMIT_TIER_2 - STORAGE_LIMIT_TIER_1;
        final long storageUsedTier3 = totalSize - STORAGE_LIMIT_TIER_2;

        final BigDecimal expectedPrice = BigDecimal.TEN.multiply(BigDecimal.valueOf(STORAGE_LIMIT_TIER_1))
            .add(BigDecimal.valueOf(5).multiply(BigDecimal.valueOf(storageUsedTier2)))
            .add(BigDecimal.ONE.multiply(BigDecimal.valueOf(storageUsedTier3)));

        final long dailyCostForTotalSize =
            s3Converter.calculateDailyCost(totalSize * BYTES_IN_1_GB, Regions.US_EAST_2, syncDate);

        Assert.assertEquals(expectedPrice.scaleByPowerOfTen(2).longValue(), dailyCostForTotalSize);
    }

    @Test
    public void testS3StorageConverting() throws IOException {
        final AbstractDataStorage s3Storage = s3StorageContainer.getEntity();
        createElasticsearchSearchContext(BYTES_IN_1_GB, false, US_EAST_1);
        final DocWriteRequest request = s3Converter.convertEntityToRequests(s3StorageContainer,
                                                                            TestUtils.STORAGE_BILLING_PREFIX,
                                                                            SYNC_START, SYNC_END).get(0);

        final String expectedIndex = TestUtils.buildBillingIndex(TestUtils.STORAGE_BILLING_PREFIX, SYNC_START);
        Assert.assertEquals(s3Storage.getId().toString(), request.id());
        final Map<String, Object> requestFieldsMap = ((IndexRequest) request).sourceAsMap();
        Assert.assertEquals(expectedIndex, request.index());
        Assert.assertEquals(SearchDocumentType.S3_STORAGE.name(),
                            requestFieldsMap.get(ElasticsearchSynchronizer.DOC_TYPE_FIELD));
        assertFields(s3Storage, requestFieldsMap, US_EAST_1, StorageType.OBJECT_STORAGE,
                     BYTES_IN_1_GB, BigDecimal.ONE.scaleByPowerOfTen(2).longValue());
    }

    @Test
    public void testEFSStorageConverting() throws IOException {
        final AbstractDataStorage nfsStorage = nfsStorageContainer.getEntity();
        createElasticsearchSearchContext(BYTES_IN_1_GB, false, US_EAST_1);

        final DocWriteRequest request = nfsConverter.convertEntityToRequests(nfsStorageContainer,
                                                                             TestUtils.STORAGE_BILLING_PREFIX,
                                                                             SYNC_START, SYNC_END).get(0);
        final String expectedIndex = TestUtils.buildBillingIndex(TestUtils.STORAGE_BILLING_PREFIX, SYNC_START);
        Assert.assertEquals(nfsStorage.getId().toString(), request.id());
        final Map<String, Object> requestFieldsMap = ((IndexRequest) request).sourceAsMap();
        Assert.assertEquals(expectedIndex, request.index());
        Assert.assertEquals(SearchDocumentType.NFS_STORAGE.name(),
                            requestFieldsMap.get(ElasticsearchSynchronizer.DOC_TYPE_FIELD));
        assertFields(nfsStorage, requestFieldsMap, US_EAST_1, StorageType.FILE_STORAGE,
                     BYTES_IN_1_GB, BigDecimal.ONE.scaleByPowerOfTen(2).longValue());
    }

    @Test
    public void testStorageWithUnknownRegionConverting() throws IOException {
        final AbstractDataStorage s3Storage = s3StorageContainer.getEntity();
        createElasticsearchSearchContext(BYTES_IN_1_GB, false, UNKNOWN_REGION);
        final DocWriteRequest request = s3Converter.convertEntityToRequests(s3StorageContainer,
                                                                            TestUtils.STORAGE_BILLING_PREFIX,
                                                                            SYNC_START, SYNC_END).get(0);
        final String expectedIndex = TestUtils.buildBillingIndex(TestUtils.STORAGE_BILLING_PREFIX, SYNC_START);
        Assert.assertEquals(s3Storage.getId().toString(), request.id());
        final Map<String, Object> requestFieldsMap = ((IndexRequest) request).sourceAsMap();
        Assert.assertEquals(expectedIndex, request.index());
        Assert.assertEquals(SearchDocumentType.S3_STORAGE.name(),
                            requestFieldsMap.get(ElasticsearchSynchronizer.DOC_TYPE_FIELD));
        assertFields(s3Storage, requestFieldsMap, null, StorageType.OBJECT_STORAGE, BYTES_IN_1_GB,
                     BigDecimal.TEN.scaleByPowerOfTen(2).longValue());
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
                                : new SearchHits(Arrays.array(hit), 1, 1);

        final SearchResponse response = Mockito.mock(SearchResponse.class);
        Mockito.when(response.getAggregations()).thenReturn(aggregations);
        Mockito.when(response.getHits()).thenReturn(hits);
        Mockito.when(elasticsearchClient.isIndexExists(Mockito.anyString())).thenReturn(true);
        Mockito.when(elasticsearchClient.search(Mockito.any())).thenReturn(response);
    }

    private void assertFields(final AbstractDataStorage storage, final Map<String, Object> fieldMap,
                              final String region, final StorageType storageType, final Long usage, final Long cost) {
        Assert.assertEquals(storage.getId().intValue(), fieldMap.get("storage_id"));
        Assert.assertEquals(ResourceType.STORAGE.toString(), fieldMap.get("resource_type"));
        Assert.assertEquals(region, fieldMap.get("region"));
        Assert.assertEquals(storage.getType().toString(), fieldMap.get("provider"));
        Assert.assertEquals(storageType.toString(), fieldMap.get("storage_type"));
        Assert.assertEquals(testUser.getUserName(), fieldMap.get("owner"));
        Assert.assertEquals(usage.intValue(), fieldMap.get("usage_bytes"));
        Assert.assertEquals(cost.intValue(), fieldMap.get("cost"));
        TestUtils.verifyStringArray(USER_GROUPS, fieldMap.get("groups"));
    }

    private EntityContainer<AbstractDataStorage> getStorageContainer(final Long id,
                                                                     final String name,
                                                                     final String path,
                                                                     final DataStorageType storageType) {
        final AbstractDataStorage storage;
        if (storageType.equals(DataStorageType.S3)) {
            storage = new S3bucketDataStorage(id, name, path);
        } else {
            storage = new NFSDataStorage(id, name, path);
        }
        return EntityContainer.<AbstractDataStorage>builder()
            .entity(storage)
            .owner(testUserWithMetadata)
            .build();
    }
}
