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

package com.epam.pipeline.billingreportagent.service.impl.converter;

import com.amazonaws.regions.Regions;
import com.epam.pipeline.billingreportagent.model.EntityContainer;
import com.epam.pipeline.billingreportagent.model.ResourceType;
import com.epam.pipeline.billingreportagent.model.StorageType;
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

    private static final String INDEX_S3 = "cp-test-index-s3";
    private static final String INDEX_EFS = "cp-test-index-efs";
    private static final Long STORAGE_ID = 1L;
    private static final String STORAGE_NAME = "TestStorage";
    private static final int DOC_ID = 2;

    private static final long BYTES_IN_1_GB = 1L << 30;
    private static final String SIZE_SUM_SEARCH = "sizeSumSearch";
    private static final String REGION_FIELD = "storage_region";
    private static final String INDEX_PATTERN = "%s-daily-%d-%s";

    private static final String VALUE_RESULT_PATTERN_JSON = "{\"value\" : \"%d\"}";
    private static final String UNKNOWN_REGION = "unknownRegion";
    private static final String USER_NAME = "TestUser";
    private static final String GROUP_1 = "TestGroup1";
    private static final String GROUP_2 = "TestGroup2";
    private static final List<String> USER_GROUPS = java.util.Arrays.asList(GROUP_1, GROUP_2);

    private final PipelineUser testUser = PipelineUser.builder()
        .userName(USER_NAME)
        .groups(USER_GROUPS)
        .build();

    private final Map<Regions, BigDecimal> testPriceList = new HashMap<>();
    private BigDecimal defaultPrice;
    private ElasticsearchServiceClient elasticsearchClient = Mockito.mock(ElasticsearchServiceClient.class);

    @BeforeEach
    public void init() {
        testPriceList.put(Regions.US_EAST_1, BigDecimal.ONE);
        testPriceList.put(Regions.US_EAST_2, BigDecimal.TEN);
        defaultPrice = BigDecimal.TEN;
    }

    @Test
    public void convertStorageToBilling() {
        final StorageBillingMapper mapper = new StorageBillingMapper(SearchDocumentType.S3_STORAGE);
        final AwsStorageToBillingRequestConverter s3converter =
            new AwsStorageToBillingRequestConverter(mapper, elasticsearchClient, StorageType.OBJECT_STORAGE,
                                                    testPriceList);
        final LocalDate syncDate = LocalDate.now();
        final int daysInMonth = YearMonth.of(syncDate.getYear(), syncDate.getMonthValue()).lengthOfMonth();

        final BigDecimal priceGbMonth = BigDecimal.TEN.multiply(BigDecimal.valueOf(daysInMonth));
        final long dailyCost1Gb = s3converter.calculateDailyCost(BYTES_IN_1_GB, priceGbMonth, syncDate);
        Assert.assertEquals(BigDecimal.TEN.scaleByPowerOfTen(2).longValue(), dailyCost1Gb);

        final long dailyCost1Byte = s3converter.calculateDailyCost(1L, priceGbMonth, syncDate);
        Assert.assertEquals(BigDecimal.ONE.longValue(), dailyCost1Byte);
    }

    @Test
    public void testS3StorageConverting() throws IOException {
        final StorageBillingMapper mapper = new StorageBillingMapper(SearchDocumentType.S3_STORAGE);
        final AwsStorageToBillingRequestConverter converter =
            new AwsStorageToBillingRequestConverter(mapper, elasticsearchClient, StorageType.OBJECT_STORAGE,
                                                    testPriceList);
        final LocalDateTime syncEnd = LocalDateTime.now();
        final LocalDateTime syncStart = syncEnd.minusDays(1);

        final EntityContainer<AbstractDataStorage> s3StorageContainer =
            getStorageContainer(STORAGE_ID, STORAGE_NAME, STORAGE_NAME, DataStorageType.S3);
        final AbstractDataStorage s3Storage = s3StorageContainer.getEntity();

        createElasticsearchSearchContext(BYTES_IN_1_GB, Regions.US_EAST_1.getName().toLowerCase());

        final DocWriteRequest request = converter.convertEntityToRequests(s3StorageContainer, INDEX_S3,
                                                                          syncStart, syncEnd).get(0);
        final String expectedIndex = String.format(INDEX_PATTERN,
                                                   INDEX_S3,
                                                   s3Storage.getId(),
                                                   converter.parseDateToString(syncStart.toLocalDate()));
        final Map<String, Object> requestFieldsMap = ((IndexRequest) request).sourceAsMap();
        Assert.assertEquals(expectedIndex, request.index());
        Assert.assertEquals(SearchDocumentType.S3_STORAGE.name(),
                            requestFieldsMap.get(ElasticsearchSynchronizer.DOC_TYPE_FIELD));
        final Long expectedCost =
            converter.calculateDailyCost(BYTES_IN_1_GB, testPriceList.get(Regions.US_EAST_1), syncEnd.toLocalDate());
        assertFields(s3Storage, requestFieldsMap, Regions.US_EAST_1.getName().toLowerCase(), StorageType.OBJECT_STORAGE,
                     BYTES_IN_1_GB, expectedCost);
    }

    @Test
    public void testEFSStorageConverting() throws IOException {
        final StorageBillingMapper mapper = new StorageBillingMapper(SearchDocumentType.NFS_STORAGE);
        final AwsStorageToBillingRequestConverter converter =
            new AwsStorageToBillingRequestConverter(mapper, elasticsearchClient, StorageType.FILE_STORAGE,
                                                    testPriceList);
        final LocalDateTime syncEnd = LocalDateTime.now();
        final LocalDateTime syncStart = syncEnd.minusDays(1);

        final EntityContainer<AbstractDataStorage> nfsStorageContainer =
            getStorageContainer(STORAGE_ID, STORAGE_NAME, STORAGE_NAME, DataStorageType.NFS);
        final AbstractDataStorage nfsStorage = nfsStorageContainer.getEntity();

        createElasticsearchSearchContext(BYTES_IN_1_GB, Regions.US_EAST_1.getName().toLowerCase());

        final DocWriteRequest request = converter.convertEntityToRequests(nfsStorageContainer, INDEX_EFS,
                                                                          syncStart, syncEnd).get(0);
        final String expectedIndex = String.format(INDEX_PATTERN,
                                                   INDEX_EFS,
                                                   nfsStorage.getId(),
                                                   converter.parseDateToString(syncStart.toLocalDate()));
        final Map<String, Object> requestFieldsMap = ((IndexRequest) request).sourceAsMap();
        Assert.assertEquals(expectedIndex, request.index());
        Assert.assertEquals(SearchDocumentType.NFS_STORAGE.name(),
                            requestFieldsMap.get(ElasticsearchSynchronizer.DOC_TYPE_FIELD));
        final Long expectedCost =
            converter.calculateDailyCost(BYTES_IN_1_GB, testPriceList.get(Regions.US_EAST_1), syncEnd.toLocalDate());
        assertFields(nfsStorage, requestFieldsMap, Regions.US_EAST_1.getName().toLowerCase(), StorageType.FILE_STORAGE,
                     BYTES_IN_1_GB, expectedCost);
    }


    @Test
    public void testStorageWithUnknownRegionConverting() throws IOException {
        final StorageBillingMapper mapper = new StorageBillingMapper(SearchDocumentType.S3_STORAGE);
        final AwsStorageToBillingRequestConverter converter =
            new AwsStorageToBillingRequestConverter(mapper, elasticsearchClient, StorageType.OBJECT_STORAGE,
                                                    testPriceList);
        final LocalDateTime syncEnd = LocalDateTime.now();
        final LocalDateTime syncStart = syncEnd.minusDays(1);

        final EntityContainer<AbstractDataStorage> s3StorageContainer =
            getStorageContainer(STORAGE_ID, STORAGE_NAME, STORAGE_NAME, DataStorageType.S3);
        final AbstractDataStorage s3Storage = s3StorageContainer.getEntity();

        createElasticsearchSearchContext(BYTES_IN_1_GB, UNKNOWN_REGION);

        final DocWriteRequest request = converter.convertEntityToRequests(s3StorageContainer, INDEX_S3,
                                                                          syncStart, syncEnd).get(0);
        final String expectedIndex = String.format(INDEX_PATTERN,
                                                   INDEX_S3,
                                                   s3Storage.getId(),
                                                   converter.parseDateToString(syncStart.toLocalDate()));
        final Map<String, Object> requestFieldsMap = ((IndexRequest) request).sourceAsMap();
        Assert.assertEquals(expectedIndex, request.index());
        Assert.assertEquals(SearchDocumentType.S3_STORAGE.name(),
                            requestFieldsMap.get(ElasticsearchSynchronizer.DOC_TYPE_FIELD));
        final Long expectedCost =
            converter.calculateDailyCost(BYTES_IN_1_GB, defaultPrice, syncEnd.toLocalDate());
        assertFields(s3Storage, requestFieldsMap, null, StorageType.OBJECT_STORAGE, BYTES_IN_1_GB,
                     expectedCost);
    }

    private void createElasticsearchSearchContext(final Long storageSize, final String region) throws IOException {
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
        final SearchHits hits = new SearchHits(Arrays.array(hit), 1, 1);

        final SearchResponse response = Mockito.mock(SearchResponse.class);
        Mockito.when(response.getAggregations()).thenReturn(aggregations);
        Mockito.when(response.getHits()).thenReturn(hits);
        Mockito.when(elasticsearchClient.search(Mockito.any())).thenReturn(response);
    }

    private void assertFields(final AbstractDataStorage storage, final Map<String, Object> fieldMap,
                              final String region, final StorageType storageType, final Long usage, final Long cost) {
        Assert.assertEquals(storage.getId().intValue(), fieldMap.get("id"));
        Assert.assertEquals(ResourceType.STORAGE.toString(), fieldMap.get("resource_type"));
        Assert.assertEquals(region, fieldMap.get("region"));
        Assert.assertEquals(storage.getType().toString(), fieldMap.get("provider"));
        Assert.assertEquals(storageType.toString(), fieldMap.get("storage_type"));
        Assert.assertEquals(testUser.getUserName(), fieldMap.get("owner"));
        Assert.assertEquals(usage.intValue(), fieldMap.get("usage"));
        Assert.assertEquals(cost.intValue(), fieldMap.get("cost"));
        TestUtils.verifyStringArray(USER_GROUPS, fieldMap.get("groups"));
    }

    public EntityContainer<AbstractDataStorage> getStorageContainer(final Long id,
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
            .owner(testUser)
            .build();
    }
}
