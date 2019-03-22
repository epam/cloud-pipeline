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
package com.epam.pipeline.elasticsearchagent.service.impl.converter.storage;

import com.epam.pipeline.elasticsearchagent.model.DataStorageDoc;
import com.epam.pipeline.elasticsearchagent.model.EntityContainer;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.NFSDataStorage;
import com.epam.pipeline.entity.datastorage.S3bucketDataStorage;
import com.epam.pipeline.entity.datastorage.StoragePolicy;
import com.epam.pipeline.entity.search.SearchDocumentType;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.epam.pipeline.elasticsearchagent.MapperVerificationUtils.verifyMetadata;
import static com.epam.pipeline.elasticsearchagent.MapperVerificationUtils.verifyNFSStorage;
import static com.epam.pipeline.elasticsearchagent.MapperVerificationUtils.verifyPermissions;
import static com.epam.pipeline.elasticsearchagent.MapperVerificationUtils.verifyPipelineUser;
import static com.epam.pipeline.elasticsearchagent.MapperVerificationUtils.verifyS3Storage;
import static com.epam.pipeline.elasticsearchagent.TestConstants.EXPECTED_METADATA;
import static com.epam.pipeline.elasticsearchagent.TestConstants.METADATA;
import static com.epam.pipeline.elasticsearchagent.TestConstants.PERMISSIONS_CONTAINER;
import static com.epam.pipeline.elasticsearchagent.TestConstants.TEST_NAME;
import static com.epam.pipeline.elasticsearchagent.TestConstants.TEST_PATH;
import static com.epam.pipeline.elasticsearchagent.TestConstants.TEST_REGION;
import static com.epam.pipeline.elasticsearchagent.TestConstants.USER;

@SuppressWarnings({"PMD.TooManyStaticImports"})
class DataStorageMapperTest {

    private static final Integer DURATION = 20;

    @Test
    void shouldMapS3DataStorage() throws IOException {
        DataStorageMapper mapper = new DataStorageMapper(SearchDocumentType.S3_STORAGE);

        StoragePolicy policy = new StoragePolicy();
        policy.setBackupDuration(DURATION);
        policy.setLongTermStorageDuration(DURATION);
        policy.setShortTermStorageDuration(DURATION);

        S3bucketDataStorage dataStorage = new S3bucketDataStorage();
        fillStorage(dataStorage);
        dataStorage.setStoragePolicy(policy);

        DataStorageDoc doc = DataStorageDoc
                .builder()
                .regionName(TEST_REGION)
                .storage(dataStorage)
                .build();

        XContentBuilder contentBuilder = mapper.map(buildContainer(doc));

        verifyS3Storage(dataStorage, TEST_REGION, contentBuilder);
        verifyPermissions(PERMISSIONS_CONTAINER, contentBuilder);
        verifyMetadata(EXPECTED_METADATA, contentBuilder);
        verifyPipelineUser(USER, contentBuilder);
    }

    @Test
    void shouldMapNFSDataStorage() throws IOException {
        DataStorageMapper mapper = new DataStorageMapper(SearchDocumentType.NFS_STORAGE);

        NFSDataStorage dataStorage = new NFSDataStorage();
        fillStorage(dataStorage);

        DataStorageDoc doc = DataStorageDoc
                .builder()
                .storage(dataStorage)
                .build();

        XContentBuilder contentBuilder = mapper.map(buildContainer(doc));

        verifyNFSStorage(dataStorage, contentBuilder);
        verifyPermissions(PERMISSIONS_CONTAINER, contentBuilder);
        verifyMetadata(EXPECTED_METADATA, contentBuilder);
        verifyPipelineUser(USER, contentBuilder);
    }

    private static EntityContainer<DataStorageDoc> buildContainer(DataStorageDoc doc) {
        return EntityContainer.<DataStorageDoc>builder()
                .entity(doc)
                .owner(USER)
                .metadata(METADATA)
                .permissions(PERMISSIONS_CONTAINER)
                .build();
    }

    private static void fillStorage(final AbstractDataStorage dataStorage) {
        dataStorage.setId(1L);
        dataStorage.setParentFolderId(1L);
        dataStorage.setName(TEST_NAME);
        dataStorage.setPath(TEST_PATH);
    }
}
