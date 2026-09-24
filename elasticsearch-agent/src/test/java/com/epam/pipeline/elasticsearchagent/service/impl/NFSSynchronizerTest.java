/*
 * Copyright 2026 EPAM Systems, Inc. (https://www.epam.com/)
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
import com.epam.pipeline.entity.datastorage.DataStorageWithShareMount;
import com.epam.pipeline.entity.datastorage.NFSDataStorage;
import com.epam.pipeline.entity.datastorage.S3bucketDataStorage;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.exception.PipelineResponseApiException;
import com.epam.pipeline.vo.EntityPermissionVO;
import com.epam.pipeline.vo.EntityVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;

import static com.epam.pipeline.elasticsearchagent.TestConstants.TEST_NAME;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class NFSSynchronizerTest {

    private static final String EXCLUDE_KEY = "key";
    private static final String EXCLUDE_VALUE = "value";
    private static final Long INCLUDED_STORAGE_ID = 1L;
    private static final Long EXCLUDED_STORAGE_ID = 2L;
    private static final Long S3_STORAGE_ID = 3L;
    private static final Long DELETED_STORAGE_ID = 4L;
    private static final Long EXISTING_STORAGE_ID = 5L;

    private final DataStorageWithShareMount includedStorage = new DataStorageWithShareMount(
            new NFSDataStorage(INCLUDED_STORAGE_ID, "included", "nfs-host:/included"), null);
    private final DataStorageWithShareMount excludedStorage = new DataStorageWithShareMount(
            new NFSDataStorage(EXCLUDED_STORAGE_ID, "excluded", "nfs-host:/excluded"), null);
    private final DataStorageWithShareMount s3Storage = new DataStorageWithShareMount(
            new S3bucketDataStorage(S3_STORAGE_ID, "s3", "s3-bucket"), null);
    private final NFSDataStorage deletedStorage =
            new NFSDataStorage(DELETED_STORAGE_ID, "deleted", "nfs-host:/deleted");
    private final NFSDataStorage existingStorage =
            new NFSDataStorage(EXISTING_STORAGE_ID, "existing", "nfs-host:/existing");

    @Mock
    private CloudPipelineAPIClient cloudPipelineAPIClient;
    @Mock
    private ElasticsearchServiceClient elasticsearchServiceClient;
    @Mock
    private ElasticIndexService elasticIndexService;
    @Mock
    private NFSStorageMounter nfsMounter;

    private NFSSynchronizer nfsSynchronizer;

    @BeforeEach
    public void setUp() {
        nfsSynchronizer = spy(new NFSSynchronizer(TEST_NAME, TEST_NAME, TEST_NAME, TEST_NAME, 1,
                cloudPipelineAPIClient, elasticsearchServiceClient, elasticIndexService, nfsMounter,
                EXCLUDE_KEY, EXCLUDE_VALUE));
    }

    @Test
    public void shouldNotIndexStoragesExcludedByMetadata() {
        mockStoragesWithoutIndexing();
        when(cloudPipelineAPIClient.searchEntriesByMetadata(AclClass.DATA_STORAGE, EXCLUDE_KEY, EXCLUDE_VALUE))
                .thenReturn(Collections.singletonList(new EntityVO(EXCLUDED_STORAGE_ID, AclClass.DATA_STORAGE)));

        nfsSynchronizer.synchronize(null, null);

        verify(nfsSynchronizer, times(1)).createIndexAndDocuments(includedStorage);
        verify(nfsSynchronizer, never()).createIndexAndDocuments(excludedStorage);
        verify(nfsSynchronizer, never()).createIndexAndDocuments(s3Storage);
    }

    @Test
    public void shouldIndexAllNfsStoragesIfNoneExcludedByMetadata() {
        mockStoragesWithoutIndexing();
        when(cloudPipelineAPIClient.searchEntriesByMetadata(AclClass.DATA_STORAGE, EXCLUDE_KEY, EXCLUDE_VALUE))
                .thenReturn(null);

        nfsSynchronizer.synchronize(null, null);

        verify(nfsSynchronizer, times(1)).createIndexAndDocuments(includedStorage);
        verify(nfsSynchronizer, times(1)).createIndexAndDocuments(excludedStorage);
        verify(nfsSynchronizer, never()).createIndexAndDocuments(s3Storage);
    }

    @Test
    public void shouldIndexRemainingStoragesWhenStorageIsDeletedDuringSync() {
        when(cloudPipelineAPIClient.loadAllDataStoragesWithMounts())
                .thenReturn(Arrays.asList(new DataStorageWithShareMount(deletedStorage, null),
                        new DataStorageWithShareMount(existingStorage, null)));
        doThrow(new PipelineResponseApiException("entity with id '4' and class 'DATA_STORAGE' was not found"))
                .when(cloudPipelineAPIClient).loadPermissionsForEntity(DELETED_STORAGE_ID, AclClass.DATA_STORAGE);
        doReturn(new EntityPermissionVO())
                .when(cloudPipelineAPIClient).loadPermissionsForEntity(EXISTING_STORAGE_ID, AclClass.DATA_STORAGE);

        nfsSynchronizer.synchronize(null, null);

        verify(elasticsearchServiceClient, never()).getIndexNameByAlias(alias(DELETED_STORAGE_ID));
        verify(elasticsearchServiceClient).getIndexNameByAlias(alias(EXISTING_STORAGE_ID));
        verify(nfsMounter).tryToMountStorage(eq(existingStorage), any());
    }

    private void mockStoragesWithoutIndexing() {
        doNothing().when(nfsSynchronizer).createIndexAndDocuments(any());
        when(cloudPipelineAPIClient.loadAllDataStoragesWithMounts())
                .thenReturn(Arrays.asList(includedStorage, excludedStorage, s3Storage));
    }

    private String alias(final Long storageId) {
        return TEST_NAME + TEST_NAME + "-" + storageId;
    }
}
