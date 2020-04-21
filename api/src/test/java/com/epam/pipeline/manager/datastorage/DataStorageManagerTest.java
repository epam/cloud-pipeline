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

package com.epam.pipeline.manager.datastorage;

import com.epam.pipeline.AbstractSpringTest;
import com.epam.pipeline.controller.vo.DataStorageVO;
import com.epam.pipeline.dao.region.CloudRegionDao;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.datastorage.StoragePolicy;
import com.epam.pipeline.entity.datastorage.aws.S3bucketDataStorage;
import com.epam.pipeline.entity.pipeline.Folder;
import com.epam.pipeline.entity.preference.Preference;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.manager.MockS3Helper;
import com.epam.pipeline.manager.ObjectCreatorUtils;
import com.epam.pipeline.manager.datastorage.providers.aws.s3.S3StorageProvider;
import com.epam.pipeline.manager.pipeline.FolderManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.region.CloudRegionManager;
import org.apache.commons.lang3.SystemUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Collectors;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;

public class DataStorageManagerTest extends AbstractSpringTest {

    private static final int STS_DURATION = 1;
    private static final int LTS_DURATION = 11;
    private static final Long WITHOUT_PARENT_ID = null;
    private static final String PATH = "path";
    private static final String NAME = "name";
    private static final String DESCRIPTION = "description";
    private static final String CHANGED = "changed";
    private static final String TEST_MOUNT_POINT = "testMountPoint";
    private static final String TEST_MOUNT_OPTIONS = "testMountOptions";
    private static final String FORBIDDEN_MOUNT_POINT = "/runs/";
    private static final String FORBIDDEN_MOUNT_POINT_2 = "/runs/run";
    private static final String FORBIDDEN_MOUNT_POINT_3 = "/runs/run/dir";
    private static final String SHARED_BASE_URL = "https://localhost:9999/shared/";
    private static final String SHARED_BASE_URL_TEMPLATE = SHARED_BASE_URL + "%d";


    @Autowired
    private DataStorageManager storageManager;

    @Autowired
    private FolderManager folderManager;

    @SpyBean
    private S3StorageProvider storageProviderManager;

    @MockBean
    private CloudRegionManager regionManager;

    @Autowired
    private PreferenceManager preferenceManager;

    @Autowired
    private CloudRegionDao cloudRegionDao;

    @Before
    public void setUp() {
        doReturn(new MockS3Helper()).when(storageProviderManager).getS3Helper(any(S3bucketDataStorage.class));
        doReturn(new AwsRegion()).when(regionManager).loadOrDefault(any());
        doReturn(new AwsRegion()).when(regionManager).getAwsRegion(any());
        Preference systemIndependentBlackList = SystemPreferences.DATA_STORAGE_NFS_MOUNT_BLACK_LIST.toPreference();
        systemIndependentBlackList.setValue(
            Arrays.asList(SystemPreferences.DATA_STORAGE_NFS_MOUNT_BLACK_LIST.getDefaultValue().split(",")).stream()
                .map(p -> Paths.get(p).toString()).collect(Collectors.joining(","))
        );
        preferenceManager.update(Collections.singletonList(systemIndependentBlackList));
        cloudRegionDao.create(ObjectCreatorUtils.getDefaultAwsRegion());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void saveDataStorageTest() throws Exception {
        DataStorageVO storageVO = ObjectCreatorUtils.constructDataStorageVO(NAME, DESCRIPTION, DataStorageType.S3,
                PATH, STS_DURATION, LTS_DURATION, WITHOUT_PARENT_ID, TEST_MOUNT_POINT, TEST_MOUNT_OPTIONS
        );
        AbstractDataStorage saved = storageManager.create(storageVO, false, false, false).getEntity();
        AbstractDataStorage loaded = storageManager.load(saved.getId());
        compareDataStorage(saved, loaded);
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void updateDataStorageTest() throws Exception {

        Folder folder = new Folder();
        folder.setName("testfolder");
        folderManager.create(folder);

        DataStorageVO storageVO = ObjectCreatorUtils.constructDataStorageVO(NAME, DESCRIPTION,
                DataStorageType.S3, PATH, STS_DURATION, LTS_DURATION, folder.getId(), TEST_MOUNT_POINT,
                                                                            TEST_MOUNT_OPTIONS
        );
        AbstractDataStorage saved = storageManager.create(storageVO, false, false, false).getEntity();

        Folder newFolder = new Folder();
        newFolder.setName("newtestfolder");
        folderManager.create(newFolder);

        //test that we can change parent folder for storage
        storageVO.setId(saved.getId());
        storageVO.setParentFolderId(newFolder.getId());

        storageManager.update(storageVO);
        AbstractDataStorage loaded = storageManager.load(saved.getId());
        assertDataStorageAccordingToUpdateStorageVO(storageVO, loaded);

        //test that we can change description for storage
        storageVO.setDescription(CHANGED);

        storageManager.update(storageVO);
        loaded = storageManager.load(saved.getId());
        assertDataStorageAccordingToUpdateStorageVO(storageVO, loaded);
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
    public void testCreateENSDataStorage() {
        DataStorageVO storageVO = ObjectCreatorUtils.constructDataStorageVO(NAME, DESCRIPTION, DataStorageType.NFS,
                                                                            PATH, WITHOUT_PARENT_ID, TEST_MOUNT_POINT,
                                                                            TEST_MOUNT_OPTIONS);
        AbstractDataStorage saved = storageManager.create(storageVO, false, false, false).getEntity();
        AbstractDataStorage loaded = storageManager.load(saved.getId());
        compareDataStorage(saved, loaded);
    }

    @Test(expected = IllegalArgumentException.class)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void testFailCreateOfStorageWithForbiddenMountPoint() {
        DataStorageVO storageVO = ObjectCreatorUtils.constructDataStorageVO(NAME, DESCRIPTION, DataStorageType.NFS,
                PATH, WITHOUT_PARENT_ID, FORBIDDEN_MOUNT_POINT,
                TEST_MOUNT_OPTIONS);
        storageManager.create(storageVO, false, false, false);
    }

    @Test(expected = IllegalArgumentException.class)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void testFailCreateStorageWithRootMountPoint() {
        DataStorageVO storageVO = ObjectCreatorUtils.constructDataStorageVO(NAME, DESCRIPTION, DataStorageType.NFS,
                                                                            PATH, WITHOUT_PARENT_ID, "/",
                                                                            TEST_MOUNT_OPTIONS);
        storageManager.create(storageVO, false, false, false);
    }

    @Test(expected = IllegalArgumentException.class)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void testFailCreateOfStorageWithForbiddenMountPointWildCard() {
        Preference preference = SystemPreferences.DATA_STORAGE_NFS_MOUNT_BLACK_LIST.toPreference();
        preference.setValue(SystemPreferences.DATA_STORAGE_NFS_MOUNT_BLACK_LIST.getDefaultValue()
                            + makeSysIndependentPath(",/runs/*"));
        preferenceManager.update(Collections.singletonList(preference));

        DataStorageVO storageVO = ObjectCreatorUtils.constructDataStorageVO(NAME, DESCRIPTION, DataStorageType.NFS,
                PATH, WITHOUT_PARENT_ID, FORBIDDEN_MOUNT_POINT_2,
                TEST_MOUNT_OPTIONS);
        storageManager.create(storageVO, false, false, false);
    }

    @Test(expected = IllegalArgumentException.class)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void testFailCreateOfStorageWithForbiddenMountPointWildCard2() {
        Preference preference = SystemPreferences.DATA_STORAGE_NFS_MOUNT_BLACK_LIST.toPreference();
        preference.setValue(SystemPreferences.DATA_STORAGE_NFS_MOUNT_BLACK_LIST.getDefaultValue()
                            + makeSysIndependentPath(",/runs/**/dir"));
        preferenceManager.update(Collections.singletonList(preference));

        DataStorageVO storageVO = ObjectCreatorUtils.constructDataStorageVO(NAME, DESCRIPTION, DataStorageType.NFS,
                PATH, WITHOUT_PARENT_ID, FORBIDDEN_MOUNT_POINT_3,
                TEST_MOUNT_OPTIONS);
        storageManager.create(storageVO, false, false, false);
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void testGenerateSharedURLForSharedStorage() {
        Preference preference = SystemPreferences.BASE_API_SHARED.toPreference();
        preference.setValue(SHARED_BASE_URL_TEMPLATE);
        preferenceManager.update(Collections.singletonList(preference));

        DataStorageVO storageVO = ObjectCreatorUtils.constructDataStorageVO(NAME, DESCRIPTION, DataStorageType.S3,
                PATH, STS_DURATION, LTS_DURATION, WITHOUT_PARENT_ID, TEST_MOUNT_POINT, TEST_MOUNT_OPTIONS
        );
        storageVO.setShared(true);
        AbstractDataStorage saved = storageManager.create(storageVO, false, false, false).getEntity();
        String url = storageManager.generateSharedUrlForStorage(saved.getId());
        Assert.assertEquals(SHARED_BASE_URL + saved.getId(), url);
    }

    @Test(expected = IllegalArgumentException.class)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void testFailToGenerateSharedURLForNotSharedStorage() {
        DataStorageVO storageVO = ObjectCreatorUtils.constructDataStorageVO(NAME, DESCRIPTION, DataStorageType.S3,
                PATH, STS_DURATION, LTS_DURATION, WITHOUT_PARENT_ID, TEST_MOUNT_POINT, TEST_MOUNT_OPTIONS
        );
        AbstractDataStorage saved = storageManager.create(storageVO, false, false, false).getEntity();
        storageManager.generateSharedUrlForStorage(saved.getId());
    }

    @Test(expected = IllegalArgumentException.class)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void testFailUpdateOfStorageWithForbiddenMountPoint() {
        DataStorageVO storageVO = ObjectCreatorUtils.constructDataStorageVO(NAME, DESCRIPTION, DataStorageType.NFS,
                PATH, WITHOUT_PARENT_ID, TEST_MOUNT_POINT,
                TEST_MOUNT_OPTIONS);
        AbstractDataStorage saved = storageManager.create(storageVO, false, false, false).getEntity();
        storageVO.setId(saved.getId());
        storageVO.setMountPoint(FORBIDDEN_MOUNT_POINT);
        storageManager.update(storageVO);
    }

    private void assertDataStorageAccordingToUpdateStorageVO(DataStorageVO updateStorageVO,
                                                             AbstractDataStorage loaded) {
        Assert.assertNotNull(loaded);
        Assert.assertEquals(updateStorageVO.getName(), loaded.getName());
        Assert.assertEquals(updateStorageVO.getDescription(), loaded.getDescription());
        Assert.assertEquals(updateStorageVO.getParentFolderId(), loaded.getParentFolderId());
    }

    private void compareDataStorage(AbstractDataStorage saved, AbstractDataStorage loaded) {
        Assert.assertNotNull(loaded);
        Assert.assertEquals(saved.getName(), loaded.getName());
        Assert.assertEquals(saved.getDescription(), loaded.getDescription());
        StoragePolicy savedStoragePolicy = saved.getStoragePolicy();
        StoragePolicy loadedStoragePolicy = loaded.getStoragePolicy();
        Assert.assertEquals(savedStoragePolicy.getShortTermStorageDuration(),
                loadedStoragePolicy.getShortTermStorageDuration());
        Assert.assertEquals(savedStoragePolicy.getLongTermStorageDuration(),
                loadedStoragePolicy.getLongTermStorageDuration());
        Assert.assertEquals(savedStoragePolicy.getIncompleteUploadCleanupDays(),
                loadedStoragePolicy.getIncompleteUploadCleanupDays());
        Assert.assertEquals(saved.getType(), loaded.getType());
    }

    private String makeSysIndependentPath(String path) {
        if (SystemUtils.IS_OS_WINDOWS) {
            return path.replace('/', '\\');
        }

        return path;
    }
}
