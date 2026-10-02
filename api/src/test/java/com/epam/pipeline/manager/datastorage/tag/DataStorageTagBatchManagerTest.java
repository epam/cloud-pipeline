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

package com.epam.pipeline.manager.datastorage.tag;

import com.epam.pipeline.dao.datastorage.DataStorageDao;
import com.epam.pipeline.dao.datastorage.tags.DataStorageTagDao;
import com.epam.pipeline.entity.datastorage.aws.S3bucketDataStorage;
import com.epam.pipeline.entity.datastorage.tag.DataStorageTagInsertBatchRequest;
import com.epam.pipeline.entity.datastorage.tag.DataStorageTagInsertRequest;
import com.epam.pipeline.entity.datastorage.tag.DataStorageTagLoadBatchRequest;
import com.epam.pipeline.entity.datastorage.tag.DataStorageTagLoadRequest;
import com.epam.pipeline.manager.datastorage.permissions.StoragePathPermissionsService;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.manager.security.storage.StoragePermissionManager;
import com.epam.pipeline.security.acl.AclPermission;
import com.epam.pipeline.test.creator.datastorage.DatastorageCreatorUtils;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;

import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class DataStorageTagBatchManagerTest {

    private static final Long STORAGE_ID = 1L;
    private static final Long ROOT_ID = 2L;
    private static final String OWNER = "OWNER";
    private static final String USER = "USER";
    private static final String PATH = "path";
    private static final String KEY = "key";
    private static final String VALUE = "value";

    private final DataStorageTagDao tagDao = mock(DataStorageTagDao.class);
    private final DataStorageDao storageDao = mock(DataStorageDao.class);
    private final AuthManager authManager = mock(AuthManager.class);
    private final StoragePermissionManager storagePermissionManager = mock(StoragePermissionManager.class);
    private final StoragePathPermissionsService storagePathPermissionsService =
            mock(StoragePathPermissionsService.class);

    private final DataStorageTagBatchManager manager = new DataStorageTagBatchManager(tagDao, storageDao,
            authManager, storagePermissionManager, storagePathPermissionsService);

    @Before
    public void setUp() {
        final S3bucketDataStorage storage = DatastorageCreatorUtils.getS3bucketDataStorage(STORAGE_ID, OWNER);
        storage.setRootId(ROOT_ID);
        storage.setPathPermissionsEnabled(true);
        doReturn(storage).when(storageDao).loadDataStorage(STORAGE_ID);
        doReturn(USER).when(authManager).getAuthorizedUser();
        doReturn(true).when(storagePermissionManager).isStorageReader();
    }

    @Test
    public void loadShouldNotFilterPathsForStorageReader() {
        manager.load(STORAGE_ID, new DataStorageTagLoadBatchRequest(
                Collections.singletonList(new DataStorageTagLoadRequest(PATH))));

        verify(storagePathPermissionsService, never()).filterFiles(anyLong(), anyList(), anyInt());
        verify(tagDao).batchLoad(ROOT_ID, Collections.singletonList(PATH));
    }

    @Test
    public void insertShouldFilterPathsForStorageReader() {
        doReturn(Collections.emptyList()).when(storagePathPermissionsService)
                .filterFiles(STORAGE_ID, Collections.singletonList(PATH), AclPermission.WRITE.getMask());

        manager.insert(STORAGE_ID, new DataStorageTagInsertBatchRequest(
                Collections.singletonList(new DataStorageTagInsertRequest(PATH, null, KEY, VALUE))));

        verify(storagePathPermissionsService)
                .filterFiles(STORAGE_ID, Collections.singletonList(PATH), AclPermission.WRITE.getMask());
        verify(tagDao, never()).batchUpsert(anyLong(), anyList());
    }
}
