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
import com.epam.pipeline.billingreportagent.model.EntityWithMetadata;
import com.epam.pipeline.billingreportagent.model.ResourceType;
import com.epam.pipeline.billingreportagent.model.StorageType;
import com.epam.pipeline.billingreportagent.model.billing.StorageBillingInfo;
import com.epam.pipeline.billingreportagent.service.AbstractEntityMapper;
import com.epam.pipeline.billingreportagent.service.ElasticsearchSynchronizer;
import com.epam.pipeline.billingreportagent.service.EntityToBillingRequestConverter;
import com.epam.pipeline.billingreportagent.service.impl.TestUtils;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.datastorage.MountType;
import com.epam.pipeline.entity.datastorage.NFSDataStorage;
import com.epam.pipeline.entity.datastorage.S3bucketDataStorage;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.entity.search.SearchDocumentType;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.utils.DateUtils;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.junit.Assert;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class StorageBillingMapperTest {

    private static final String BILLING_CENTER_KEY = "billing";
    private static final long TEST_USER_ID = 1L;
    private static final String TEST_USER_NAME = "User";
    private static final String TEST_GROUP_1 = "TestGroup1";
    private static final String TEST_GROUP_2 = "TestGroup2";
    private static final long TEST_REGION_ID = 1L;
    private static final String TEST_REGION_NAME = "test-region";
    private static final CloudProvider TEST_REGION_PROVIDER = CloudProvider.AWS;
    private static final long TEST_COST = 10;
    private static final long TEST_USAGE_BYTES = 600;
    private static final List<String> TEST_GROUPS = Arrays.asList(TEST_GROUP_1, TEST_GROUP_2);
    private static final Date TEST_JAVA_DATE = DateUtils.now();
    private static final LocalDate TEST_DATE = DateUtils.convertDateToLocalDateTime(TEST_JAVA_DATE).toLocalDate();
    private static final String TEST_STORAGE_NAME = "storage_name";
    private static final String TEST_STORAGE_PATH = "storage_path";

    private final StorageBillingMapper s3Mapper = new StorageBillingMapper(
            SearchDocumentType.S3_STORAGE, BILLING_CENTER_KEY);
    private final StorageBillingMapper efsMapper = new StorageBillingMapper(
            SearchDocumentType.NFS_STORAGE, BILLING_CENTER_KEY);

    private final PipelineUser testUser = PipelineUser.builder()
        .id(TEST_USER_ID)
        .userName(TEST_USER_NAME)
        .groups(TEST_GROUPS)
        .attributes(Collections.emptyMap())
        .build();

    private final EntityWithMetadata<PipelineUser> testUserWithMetadata = EntityWithMetadata.<PipelineUser>builder()
            .entity(testUser)
            .build();

    @Test
    public void testStorageMapperMapS3Storage() throws IOException {
        final S3bucketDataStorage storage = new S3bucketDataStorage();
        storage.setName(TEST_STORAGE_NAME);
        storage.setName(TEST_STORAGE_PATH);
        storage.setCreatedDate(TEST_JAVA_DATE);
        final StorageBillingInfo billing = StorageBillingInfo.builder()
            .storage(storage)
            .resourceStorageType(StorageType.OBJECT_STORAGE)
            .objectStorageType(DataStorageType.S3)
            .fileStorageType(null)
            .usageBytes(TEST_USAGE_BYTES)
            .cost(TEST_COST)
            .date(TEST_DATE)
            .build();
        final AbstractCloudRegion region = new AwsRegion();
        region.setId(TEST_REGION_ID);
        region.setName(TEST_REGION_NAME);
        final EntityContainer<StorageBillingInfo> billingContainer = EntityContainer.<StorageBillingInfo>builder()
            .entity(billing)
            .owner(testUserWithMetadata)
            .region(region)
            .build();

        final XContentBuilder mappedBilling = s3Mapper.map(billingContainer);

        final Map<String, Object> mappedFields = TestUtils.getPuttedObject(mappedBilling);

        Assert.assertEquals(SearchDocumentType.S3_STORAGE.name(),
                            mappedFields.get(ElasticsearchSynchronizer.DOC_TYPE_FIELD));
        Assert.assertEquals(EntityToBillingRequestConverter.SIMPLE_DATE_FORMAT.format(TEST_DATE),
                mappedFields.get("created_date"));
        Assert.assertEquals(ResourceType.STORAGE.toString(), mappedFields.get("resource_type"));
        Assert.assertEquals((int) TEST_REGION_ID, mappedFields.get("cloudRegionId"));
        Assert.assertEquals(TEST_REGION_NAME, mappedFields.get("cloud_region_name"));
        Assert.assertEquals(TEST_REGION_PROVIDER.toString(), mappedFields.get("cloud_region_provider"));

        Assert.assertEquals(storage.getId(), mappedFields.get("storage_id"));
        Assert.assertEquals(storage.getName(), mappedFields.get("storage_name"));
        Assert.assertEquals(storage.getPath(), mappedFields.get("storage_path"));
        Assert.assertEquals(StorageType.OBJECT_STORAGE.toString(), mappedFields.get("storage_type"));
        Assert.assertEquals(storage.getType().toString(), mappedFields.get("provider"));
        Assert.assertEquals(null, mappedFields.get("file_storage_type"));
        Assert.assertEquals(storage.getType().toString(), mappedFields.get("object_storage_type"));
        Assert.assertEquals(AbstractEntityMapper.SIMPLE_DATE_FORMAT.format(TEST_JAVA_DATE),
                mappedFields.get("storage_created_date"));

        Assert.assertEquals((int) TEST_USAGE_BYTES, mappedFields.get("usage_bytes"));
        Assert.assertEquals((int) TEST_USAGE_BYTES, mappedFields.get("usage_bytes_avg"));
        Assert.assertEquals((int) TEST_COST, mappedFields.get("cost"));

        Assert.assertEquals((int) TEST_USER_ID, mappedFields.get("owner_id"));
        Assert.assertEquals(TEST_USER_NAME, mappedFields.get("owner"));
        TestUtils.verifyStringArray(TEST_GROUPS, mappedFields.get("groups"));
    }

    @Test
    public void testStorageMapperMapEFSStorage() throws IOException {
        final NFSDataStorage storage = new NFSDataStorage();
        storage.setName(TEST_STORAGE_NAME);
        storage.setName(TEST_STORAGE_PATH);
        storage.setCreatedDate(TEST_JAVA_DATE);
        final StorageBillingInfo billing = StorageBillingInfo.builder()
            .storage(storage)
            .resourceStorageType(StorageType.FILE_STORAGE)
            .objectStorageType(null)
            .fileStorageType(MountType.NFS)
            .usageBytes(TEST_USAGE_BYTES)
            .cost(TEST_COST)
            .date(TEST_DATE)
            .build();
        final AbstractCloudRegion region = new AwsRegion();
        region.setId(TEST_REGION_ID);
        region.setName(TEST_REGION_NAME);

        final EntityContainer<StorageBillingInfo> billingContainer = EntityContainer.<StorageBillingInfo>builder()
            .entity(billing)
            .owner(testUserWithMetadata)
            .region(region)
            .build();

        final XContentBuilder mappedBilling = efsMapper.map(billingContainer);

        final Map<String, Object> mappedFields = TestUtils.getPuttedObject(mappedBilling);

        Assert.assertEquals(SearchDocumentType.NFS_STORAGE.name(),
                mappedFields.get(ElasticsearchSynchronizer.DOC_TYPE_FIELD));
        Assert.assertEquals(EntityToBillingRequestConverter.SIMPLE_DATE_FORMAT.format(TEST_DATE),
                mappedFields.get("created_date"));
        Assert.assertEquals(ResourceType.STORAGE.toString(), mappedFields.get("resource_type"));
        Assert.assertEquals((int) TEST_REGION_ID, mappedFields.get("cloudRegionId"));
        Assert.assertEquals(TEST_REGION_NAME, mappedFields.get("cloud_region_name"));
        Assert.assertEquals(TEST_REGION_PROVIDER.toString(), mappedFields.get("cloud_region_provider"));

        Assert.assertEquals(storage.getId(), mappedFields.get("storage_id"));
        Assert.assertEquals(storage.getName(), mappedFields.get("storage_name"));
        Assert.assertEquals(storage.getPath(), mappedFields.get("storage_path"));
        Assert.assertEquals(StorageType.FILE_STORAGE.toString(), mappedFields.get("storage_type"));
        Assert.assertEquals(storage.getType().toString(), mappedFields.get("provider"));
        Assert.assertEquals(MountType.NFS.toString(), mappedFields.get("file_storage_type"));
        Assert.assertEquals(null, mappedFields.get("object_storage_type"));
        Assert.assertEquals(AbstractEntityMapper.SIMPLE_DATE_FORMAT.format(TEST_JAVA_DATE),
                mappedFields.get("storage_created_date"));

        Assert.assertEquals((int) TEST_USAGE_BYTES, mappedFields.get("usage_bytes"));
        Assert.assertEquals((int) TEST_USAGE_BYTES, mappedFields.get("usage_bytes_avg"));
        Assert.assertEquals((int) TEST_COST, mappedFields.get("cost"));

        Assert.assertEquals(TEST_USER_NAME, mappedFields.get("owner"));
        TestUtils.verifyStringArray(TEST_GROUPS, mappedFields.get("groups"));
    }
}
