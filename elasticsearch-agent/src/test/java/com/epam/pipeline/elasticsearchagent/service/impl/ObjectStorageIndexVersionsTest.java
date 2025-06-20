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
package com.epam.pipeline.elasticsearchagent.service.impl;

import com.epam.pipeline.elasticsearchagent.service.ElasticsearchServiceClient;
import com.epam.pipeline.elasticsearchagent.service.ObjectStorageFileManager;
import com.epam.pipeline.elasticsearchagent.service.lock.LockService;
import com.epam.pipeline.entity.datastorage.*;
import com.epam.pipeline.entity.search.SearchDocumentType;
import com.epam.pipeline.vo.EntityPermissionVO;
import org.apache.commons.io.FilenameUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static com.epam.pipeline.elasticsearchagent.TestConstants.TEST_NAME;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.internal.verification.VerificationModeFactory.times;

@ExtendWith(MockitoExtension.class)
public class ObjectStorageIndexVersionsTest {

    private static final String TEST_BLOB_NAME_1 = "1";
    private static final String TEST_BLOB_NAME_2 = "2";
    private static final int BULK_SIZE = 1000;
    private static final String EXCLUDE_KEY = "key";
    private static final String EXCLUDE_VALUE = "value";

    private final Supplier<TemporaryCredentials> temporaryCredentials = () ->
            TemporaryCredentials.builder().region("").build();

    @Mock
    private IndexRequestContainer requestContainer;
    @Mock
    private ObjectStorageFileManager fileManager;
    @Mock
    private CloudPipelineAPIClient cloudPipelineAPIClient;
    @Mock
    private ElasticsearchServiceClient elasticsearchServiceClient;
    @Mock
    private ElasticIndexService elasticIndexService;
    @Mock
    private LockService lockService;

    private ObjectStorageIndexImpl objectStorageIndex;

    @BeforeEach
    public void init() {
        objectStorageIndex = spy(
                new ObjectStorageIndexImpl(
                        cloudPipelineAPIClient,
                        elasticsearchServiceClient,
                        elasticIndexService,
                        fileManager,
                        lockService,
                        TEST_NAME,
                        TEST_NAME,
                        BULK_SIZE,
                        BULK_SIZE,
                        DataStorageType.S3,
                        SearchDocumentType.S3_FILE,
                        ";", true,
                        EXCLUDE_KEY, EXCLUDE_VALUE)
        );
    }


    @Test
    public void shouldGroupFilesByVersions() {
        StoragePolicy storagePolicy = new StoragePolicy();
        storagePolicy.setVersioningEnabled(true);
        AbstractDataStorage dataStorage = new S3bucketDataStorage(
                1L, "storage", "storage", storagePolicy, null
        );
        dataStorage.setStoragePolicy(storagePolicy);
        final List<DataStorageFile> files = Arrays.asList(
                createVersion(TEST_BLOB_NAME_1), createVersion(TEST_BLOB_NAME_1), createVersion(TEST_BLOB_NAME_1),
                createVersion(TEST_BLOB_NAME_2)
        );
        setUpReturnValues(files);
        objectStorageIndex.indexStorage(dataStorage);
        verifyNumberOfInsertions(2);
    }
    private void setUpReturnValues(final List<DataStorageFile> files) {
        Mockito.doAnswer(i -> temporaryCredentials.get())
                .when(cloudPipelineAPIClient).generateTemporaryCredentials(any());

        Mockito.doAnswer(i -> requestContainer)
                .when(objectStorageIndex).getRequestContainer(any(String.class), any(Integer.class));

        Mockito.doAnswer(i -> new EntityPermissionVO())
                .when(cloudPipelineAPIClient).loadPermissionsForEntity(any(), any());

        Mockito.doAnswer(i -> files.stream())
                .when(fileManager)
                .versions(any(), any(), any(), anyBoolean());
    }

    private void verifyNumberOfInsertions(final int numberOfInvocation) {
        verify(requestContainer, times(numberOfInvocation)).add(any());
    }

    private DataStorageFile createVersion(final String name) {
        final DataStorageFile file = new DataStorageFile();
        file.setName(FilenameUtils.getName(name));
        file.setPath(name);
        file.setVersion(UUID.randomUUID().toString());
        file.setSize(1L);
        return file;
    }
}
