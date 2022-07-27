/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.manager.datastorage;

import com.epam.pipeline.AbstractSpringTest;
import com.epam.pipeline.controller.vo.DataStorageVO;
import com.epam.pipeline.dao.region.CloudRegionDao;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.datastorage.aws.S3bucketDataStorage;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.manager.MockS3Helper;
import com.epam.pipeline.manager.ObjectCreatorUtils;
import com.epam.pipeline.manager.datastorage.providers.aws.s3.S3StorageProvider;
import com.epam.pipeline.manager.region.CloudRegionManager;
import com.epam.pipeline.manager.security.CheckPermissionHelper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.transaction.annotation.Transactional;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;

@Transactional
public class DataStoragePathLoaderTest extends AbstractSpringTest {

    private static final int STS_DURATION = 1;
    private static final int LTS_DURATION = 11;
    private static final Long WITHOUT_PARENT_ID = null;
    private static final String PATH = "bucket";
    private static final String NAME = "name";
    private static final String DESCRIPTION = "description";
    public static final String FOLDER = "/folder";

    @SpyBean
    private S3StorageProvider storageProviderManager;

    @MockBean
    private CloudRegionManager regionManager;

    @Autowired
    private CloudRegionDao cloudRegionDao;

    @Autowired
    private DataStorageManager storageManager;

    @Autowired
    private DataStoragePathLoader pathLoader;

    @MockBean
    private CheckPermissionHelper permissionHelper;

    @Before
    public void setUp() {
        doReturn(new MockS3Helper()).when(storageProviderManager)
                .getS3Helper(any(S3bucketDataStorage.class));
        doReturn(new AwsRegion()).when(regionManager).loadOrDefault(any());
        doReturn(new AwsRegion()).when(regionManager).getAwsRegion(any());
        cloudRegionDao.create(ObjectCreatorUtils.getDefaultAwsRegion());
    }

    @Test
    public void shouldLoadStorageById() {
        final AbstractDataStorage storage = createStorage(PATH);
        loadAndAssertStorage(storage, String.valueOf(storage.getId()));
    }

    @Test
    public void shouldLoadStorageByName() {
        loadAndAssertStorage(createStorage(PATH), NAME);
    }

    @Test
    public void shouldLoadStorageByPath() {
        loadAndAssertStorage(createStorage(PATH), PATH);
    }

    @Test
    public void shouldLoadStorageByPrefixWithDelimiter() {
        doReturn(true).when(permissionHelper).isAllowed(eq("READ"), any());
        loadAndAssertStorage(createStorage(PATH), PATH + "/folder/");
    }

    @Test
    public void shouldLoadStorageByPrefixWithoutDelimiter() {
        doReturn(true).when(permissionHelper).isAllowed(eq("READ"), any());
        loadAndAssertStorage(createStorage(PATH), PATH + "/folder/test");
    }

    @Test
    public void shouldReturnLongestMatch() {
        doReturn(true).when(permissionHelper).isAllowed(eq("READ"), any());
        createStorage(PATH);
        loadAndAssertStorage(createStorage(NAME + "1", PATH + FOLDER), PATH + FOLDER);
    }

    private AbstractDataStorage createStorage(final String path) {
        return createStorage(NAME, path);
    }

    private AbstractDataStorage createStorage(final String name, final String path) {
        final DataStorageVO storageVO = ObjectCreatorUtils.constructDataStorageVO(name, DESCRIPTION, DataStorageType.S3,
                path, STS_DURATION, LTS_DURATION, WITHOUT_PARENT_ID, "", "");
        return storageManager.create(storageVO, false, false, false).getEntity();
    }

    private void loadAndAssertStorage(final AbstractDataStorage expected, final String query) {
        AbstractDataStorage loaded = pathLoader.loadDataStorageByPathOrId(query);
        Assert.assertEquals(expected.getId(), loaded.getId());
    }

}
