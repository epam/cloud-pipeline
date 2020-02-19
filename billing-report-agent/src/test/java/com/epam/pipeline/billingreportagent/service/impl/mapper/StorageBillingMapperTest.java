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

package com.epam.pipeline.billingreportagent.service.impl.mapper;

import com.epam.pipeline.billingreportagent.model.EntityContainer;
import com.epam.pipeline.billingreportagent.model.ResourceType;
import com.epam.pipeline.billingreportagent.model.StorageType;
import com.epam.pipeline.billingreportagent.model.billing.StorageBillingInfo;
import com.epam.pipeline.billingreportagent.service.ElasticsearchSynchronizer;
import com.epam.pipeline.billingreportagent.service.EntityToBillingRequestConverter;
import com.epam.pipeline.billingreportagent.service.impl.TestUtils;
import com.epam.pipeline.entity.datastorage.NFSDataStorage;
import com.epam.pipeline.entity.datastorage.S3bucketDataStorage;
import com.epam.pipeline.entity.search.SearchDocumentType;
import com.epam.pipeline.entity.user.PipelineUser;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.junit.Assert;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class StorageBillingMapperTest {

    private static final String BILLING_CENTER_KEY = "billing";
    private static final String TEST_USER_NAME = "User";
    private static final String TEST_GROUP_1 = "TestGroup1";
    private static final String TEST_GROUP_2 = "TestGroup2";
    private static final String TEST_REGION = "region-1";
    private static final long TEST_COST = 10;
    private static final long TEST_USAGE_BYTES = 600;
    private static final List<String> TEST_GROUPS = Arrays.asList(TEST_GROUP_1, TEST_GROUP_2);
    private static final LocalDate TEST_DATE = LocalDate.now();

    private final StorageBillingMapper s3Mapper = new StorageBillingMapper(
            SearchDocumentType.S3_STORAGE, BILLING_CENTER_KEY);
    private final StorageBillingMapper efsMapper = new StorageBillingMapper(
            SearchDocumentType.NFS_STORAGE, BILLING_CENTER_KEY);

    private final PipelineUser testUser = PipelineUser.builder()
        .userName(TEST_USER_NAME)
        .groups(TEST_GROUPS)
        .attributes(Collections.emptyMap())
        .build();

    @Test
    public void testStorageMapperMapS3Storage() throws IOException {
        final S3bucketDataStorage s3Storage = new S3bucketDataStorage();
        final StorageBillingInfo billing = StorageBillingInfo.builder()
            .storage(s3Storage)
            .storageType(StorageType.OBJECT_STORAGE)
            .regionName(TEST_REGION)
            .usageBytes(TEST_USAGE_BYTES)
            .cost(TEST_COST)
            .date(TEST_DATE)
            .build();

        final EntityContainer<StorageBillingInfo> billingContainer = EntityContainer.<StorageBillingInfo>builder()
            .entity(billing)
            .owner(testUser)
            .build();

        final XContentBuilder mappedBilling = s3Mapper.map(billingContainer);

        final Map<String, Object> mappedFields = TestUtils.getPuttedObject(mappedBilling);

        Assert.assertEquals(SearchDocumentType.S3_STORAGE.name(),
                            mappedFields.get(ElasticsearchSynchronizer.DOC_TYPE_FIELD));
        Assert.assertEquals(s3Storage.getId(), mappedFields.get("storage_id"));
        Assert.assertEquals(ResourceType.STORAGE.toString(), mappedFields.get("resource_type"));
        Assert.assertEquals(TEST_REGION, mappedFields.get("region"));
        Assert.assertEquals(s3Storage.getType().toString(), mappedFields.get("provider"));
        Assert.assertEquals(StorageType.OBJECT_STORAGE.toString(), mappedFields.get("storage_type"));
        Assert.assertEquals(TEST_USER_NAME, mappedFields.get("owner"));
        Assert.assertEquals((int) TEST_USAGE_BYTES, mappedFields.get("usage_bytes"));
        Assert.assertEquals((int) TEST_COST, mappedFields.get("cost"));
        Assert.assertEquals(EntityToBillingRequestConverter.SIMPLE_DATE_FORMAT.format(TEST_DATE),
                            mappedFields.get("created_date"));
        TestUtils.verifyStringArray(TEST_GROUPS, mappedFields.get("groups"));
    }

    @Test
    public void testStorageMapperMapEFSStorage() throws IOException {
        final NFSDataStorage efsStorage = new NFSDataStorage();
        final StorageBillingInfo billing = StorageBillingInfo.builder()
            .storage(efsStorage)
            .storageType(StorageType.FILE_STORAGE)
            .regionName(TEST_REGION)
            .usageBytes(TEST_USAGE_BYTES)
            .cost(TEST_COST)
            .build();

        final EntityContainer<StorageBillingInfo> billingContainer = EntityContainer.<StorageBillingInfo>builder()
            .entity(billing)
            .owner(testUser)
            .build();

        final XContentBuilder mappedBilling = efsMapper.map(billingContainer);

        final Map<String, Object> mappedFields = TestUtils.getPuttedObject(mappedBilling);

        Assert.assertEquals(SearchDocumentType.NFS_STORAGE.name(),
                            mappedFields.get(ElasticsearchSynchronizer.DOC_TYPE_FIELD));
        Assert.assertEquals(efsStorage.getId(), mappedFields.get("storage_id"));
        Assert.assertEquals(ResourceType.STORAGE.toString(), mappedFields.get("resource_type"));
        Assert.assertEquals(billing.getRegionName(), mappedFields.get("region"));
        Assert.assertEquals(efsStorage.getType().toString(), mappedFields.get("provider"));
        Assert.assertEquals(StorageType.FILE_STORAGE.toString(), mappedFields.get("storage_type"));
        Assert.assertEquals(testUser.getUserName(), mappedFields.get("owner"));
        Assert.assertEquals(billing.getUsageBytes().intValue(), mappedFields.get("usage_bytes"));
        Assert.assertEquals(billing.getCost().intValue(), mappedFields.get("cost"));
        TestUtils.verifyStringArray(TEST_GROUPS, mappedFields.get("groups"));
    }
}
