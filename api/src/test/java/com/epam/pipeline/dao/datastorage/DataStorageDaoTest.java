/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.dao.datastorage;

import com.epam.pipeline.dao.pipeline.FolderDao;
import com.epam.pipeline.dao.region.CloudRegionDao;
import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.entity.BaseEntity;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.StoragePolicy;
import com.epam.pipeline.entity.datastorage.aws.S3bucketDataStorage;
import com.epam.pipeline.entity.datastorage.nfs.NFSDataStorage;
import com.epam.pipeline.entity.pipeline.Folder;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.manager.ObjectCreatorUtils;
import com.epam.pipeline.test.jdbc.AbstractJdbcTest;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static com.epam.pipeline.assertions.ProjectAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

@Transactional
public class DataStorageDaoTest extends AbstractJdbcTest {

    private static final String TEST_OWNER = "testOwner";
    private static final String TEST_STORAGE_NAME = "testStorage";
    private static final String TEST_STORAGE_PATH = "testPath";
    private static final int BACKUP_DURATION = 1;
    private static final int LTS_DURATION = 2;
    private static final int STS_DURATION = 3;
    private static final String UPDATED_VALUE = "UPDATED";

    @Autowired
    private DataStorageDao dataStorageDao;

    @Autowired
    private FolderDao folderDao;

    @Autowired
    private CloudRegionDao cloudRegionDao;

    private Folder testFolder;
    private S3bucketDataStorage s3Bucket;
    private NFSDataStorage nfsStorage;
    private AwsRegion awsRegion;
    private StoragePolicy policy;

    @Before
    public void setUp() {
        testFolder = buildFolder(null);

        awsRegion = new AwsRegion();
        awsRegion.setName("Default");
        awsRegion.setDefault(true);
        awsRegion.setRegionCode("us-east-1");
        cloudRegionDao.create(awsRegion);

        s3Bucket = new S3bucketDataStorage(null, TEST_STORAGE_NAME, TEST_STORAGE_PATH);
        s3Bucket.setDescription("testDescription");
        s3Bucket.setParentFolderId(testFolder.getId());
        s3Bucket.setRegionId(awsRegion.getId());
        s3Bucket.setOwner(TEST_OWNER);

        s3Bucket.setMountPoint("testMountPoint");
        s3Bucket.setMountOptions("testMountOptions");
        s3Bucket.setShared(true);
        s3Bucket.setAllowedCidrs(Arrays.asList("test1", "test2"));

        policy = new StoragePolicy();

        policy.setBackupDuration(BACKUP_DURATION);
        policy.setLongTermStorageDuration(LTS_DURATION);
        policy.setShortTermStorageDuration(STS_DURATION);
        policy.setVersioningEnabled(true);
        s3Bucket.setStoragePolicy(policy);

        nfsStorage = new NFSDataStorage(null, "NFS_STORAGE", "127.0.0.1@tcp1:/path");
        nfsStorage.setOwner(TEST_OWNER);
        nfsStorage.setDescription("NFS");
        nfsStorage.setParentFolderId(testFolder.getId());
        nfsStorage.setMountOptions("-s");
        nfsStorage.setMountPoint("nfs");
    }

    @Test
    public void shouldReturnStorageWithParents() {
        Folder childFolder = new Folder();
        childFolder.setName("childFolder");
        childFolder.setOwner(TEST_OWNER);
        childFolder.setParentId(testFolder.getId());
        folderDao.createFolder(childFolder);

        s3Bucket.setParentFolderId(childFolder.getId());
        dataStorageDao.createDataStorage(s3Bucket);

        Collection<AbstractDataStorage> storages = dataStorageDao.loadAllWithParents(1, 10);
        assertThat(storages)
                .hasSize(1)
                .containsOnly(s3Bucket)
                .extracting(AbstractDataStorage::getParent)
                .containsOnly(childFolder)
                .extracting(Folder::getParent)
                .containsOnly(testFolder);
    }

    @Test
    public void shouldReturnFolderWithoutParents() {
        s3Bucket.setParentFolderId(null);
        dataStorageDao.createDataStorage(s3Bucket);

        Collection<AbstractDataStorage> storages = dataStorageDao.loadAllWithParents(1, 10);
        assertThat(storages)
                .hasSize(1)
                .containsOnly(s3Bucket)
                .extracting(AbstractDataStorage::getParent)
                .containsNull();
    }

    @Test
    public void shouldCreateNewS3Storage() {
        dataStorageDao.createDataStorage(s3Bucket);
        validateCreatedStorage(s3Bucket);
    }

    @Test
    public void shouldSetCreatedDateForNewStorageIfNotProvided() {
        s3Bucket.setCreatedDate(null);
        dataStorageDao.createDataStorage(s3Bucket);
        validateCreatedStorage(s3Bucket);
    }

    @Test
    public void shouldCreateNewNFSStorageWithNullPolicy() {
        nfsStorage.setStoragePolicy(null);
        dataStorageDao.createDataStorage(nfsStorage);
        validateCreatedStorage(nfsStorage);
    }

    @Test
    public void shouldCreateNewNFSStorage() {
        dataStorageDao.createDataStorage(nfsStorage);
        validateCreatedStorage(nfsStorage);
    }

    @Test
    public void shouldUpdateStorage() {
        dataStorageDao.createDataStorage(s3Bucket);
        s3Bucket.setName(UPDATED_VALUE);
        s3Bucket.setDescription(UPDATED_VALUE);
        s3Bucket.getStoragePolicy().setBackupDuration(BACKUP_DURATION + 1);
        s3Bucket.getStoragePolicy().setLongTermStorageDuration(LTS_DURATION + 1);
        s3Bucket.getStoragePolicy().setLongTermStorageDuration(STS_DURATION + 1);
        s3Bucket.getStoragePolicy().setVersioningEnabled(false);
        s3Bucket.setParentFolderId(null);
        s3Bucket.setOwner(UPDATED_VALUE);
        s3Bucket.setMountOptions(UPDATED_VALUE);
        s3Bucket.setMountPoint(UPDATED_VALUE);
        dataStorageDao.updateDataStorage(s3Bucket);
        validateS3Storage(dataStorageDao.loadDataStorage(s3Bucket.getId()), s3Bucket);
    }

    @Test
    public void shouldDeleteStorage() {
        dataStorageDao.createDataStorage(s3Bucket);
        Long storageId = s3Bucket.getId();
        dataStorageDao.deleteDataStorage(storageId);
        assertThat(dataStorageDao.loadDataStorage(storageId))
                .isNull();
        assertThat(dataStorageDao.loadAllDataStorages())
                .extracting(BaseEntity::getId)
                .doesNotContain(storageId);
    }

    @Test
    public void shouldLoadExistingStorageInList() {
        dataStorageDao.createDataStorage(s3Bucket);
        List<AbstractDataStorage> dataStorages = dataStorageDao.loadAllDataStorages();
        assertThat(dataStorages).hasSize(1);
        validateS3Storage(dataStorages.get(0), s3Bucket);
    }

    @Test
    public void shouldLoadExistingS3StorageById() {
        dataStorageDao.createDataStorage(s3Bucket);
        AbstractDataStorage loaded = dataStorageDao.loadDataStorage(s3Bucket.getId());
        validateS3Storage(loaded, s3Bucket);
    }

    @Test
    public void shouldLoadExistingNFSStorageById() {
        dataStorageDao.createDataStorage(nfsStorage);
        AbstractDataStorage loaded = dataStorageDao.loadDataStorage(nfsStorage.getId());
        validateNFSStorage(loaded, nfsStorage);
    }

    @Test
    public void shouldLoadExistingNFSStorageInList() {
        dataStorageDao.createDataStorage(nfsStorage);
        List<AbstractDataStorage> dataStorages = dataStorageDao.loadAllDataStorages();
        assertThat(dataStorages).hasSize(1);
        validateNFSStorage(dataStorages.get(0), nfsStorage);
    }

    @Test
    public void shouldLoadExistingS3StorageByName() {
        dataStorageDao.createDataStorage(s3Bucket);
        AbstractDataStorage loaded = dataStorageDao.loadDataStorageByNameOrPath(TEST_STORAGE_NAME, null);
        validateS3Storage(loaded, s3Bucket);
    }

    @Test
    public void shouldLoadExistingS3StorageByPath() {
        dataStorageDao.createDataStorage(s3Bucket);
        AbstractDataStorage loaded = dataStorageDao.loadDataStorageByNameOrPath(TEST_STORAGE_PATH, TEST_STORAGE_PATH);
        validateS3Storage(loaded, s3Bucket);
    }

    @Test
    public void shouldReturnZeroTotalCountWithoutStorages() {
        assertThat(dataStorageDao.loadTotalCount()).isEqualTo(0);
    }

    @Test
    public void shouldReturnTotalCountWithStorages() {
        dataStorageDao.createDataStorage(s3Bucket);
        assertThat(dataStorageDao.loadTotalCount()).isEqualTo(1);
    }

    @Test
    public void shouldReturnStorageWithoutParentAsRoot() {
        AbstractDataStorage rootBucket = s3Bucket;
        rootBucket.setParentFolderId(null);
        dataStorageDao.createDataStorage(rootBucket);

        AbstractDataStorage storageInFolder = ObjectCreatorUtils.clone(s3Bucket);
        storageInFolder.setParentFolderId(testFolder.getId());
        dataStorageDao.createDataStorage(storageInFolder);

        assertThat(dataStorageDao.loadRootDataStorages())
                .hasSize(1)
                .doesNotContain(storageInFolder)
                .contains(rootBucket);
    }

    @Test
    public void shouldReturnStorageByNameAndParent() {
        dataStorageDao.createDataStorage(s3Bucket);
        AbstractDataStorage loaded =
                dataStorageDao.loadDataStorageByNameAndParentId(s3Bucket.getName(), testFolder.getId());
        validateS3Storage(loaded, s3Bucket);
    }

    @Test
    public void shouldReturnStorageByPathAndParent() {
        dataStorageDao.createDataStorage(s3Bucket);
        AbstractDataStorage loaded =
                dataStorageDao.loadDataStorageByNameAndParentId(s3Bucket.getPath(), testFolder.getId());
        validateS3Storage(loaded, s3Bucket);
    }

    @Test
    public void shouldNotReturnStorageByPathWithWrongParent() {
        s3Bucket.setParentFolderId(null);
        dataStorageDao.createDataStorage(s3Bucket);

        AbstractDataStorage loaded =
                dataStorageDao.loadDataStorageByNameAndParentId(s3Bucket.getPath(), testFolder.getId());
        assertThat(loaded).isNull();
    }

    @Test
    public void shouldLockByIds() {
        dataStorageDao.createDataStorage(s3Bucket);
        dataStorageDao.createDataStorage(nfsStorage);
        dataStorageDao.updateLocks(Arrays.asList(s3Bucket.getId(), nfsStorage.getId()), true);
        assertThat(dataStorageDao.loadAllDataStorages())
                .extracting(AbstractSecuredEntity::isLocked)
                .doesNotContain(false);
    }

    @Test
    public void shouldUnLockByIds() {
        dataStorageDao.createDataStorage(s3Bucket);
        dataStorageDao.createDataStorage(nfsStorage);
        dataStorageDao.updateLocks(Arrays.asList(s3Bucket.getId(), nfsStorage.getId()), false);
        assertThat(dataStorageDao.loadAllDataStorages())
                .extracting(AbstractSecuredEntity::isLocked)
                .doesNotContain(true);
    }

    @Test
    public void shouldReturnNFSByFullPath() {
        dataStorageDao.createDataStorage(nfsStorage);
        List<AbstractDataStorage> dataStorages = dataStorageDao.loadDataStoragesByNFSPath(nfsStorage.getPath());
        assertThat(dataStorages)
                .hasSize(1)
                .contains(nfsStorage);
        validateNFSStorage(dataStorages.get(0), nfsStorage);
    }

    @Test
    public void shouldReturnNFSByPrefixPath() {
        dataStorageDao.createDataStorage(nfsStorage);
        String prefix = nfsStorage.getPath().substring(0, nfsStorage.getPath().length() - 2);

        List<AbstractDataStorage> dataStorages = dataStorageDao.loadDataStoragesByNFSPath(prefix);
        assertThat(dataStorages)
                .hasSize(1)
                .contains(nfsStorage);
        validateNFSStorage(dataStorages.get(0), nfsStorage);
    }

    @Test
    public void shouldBotReturnNFSByWrongPath() {
        dataStorageDao.createDataStorage(nfsStorage);
        List<AbstractDataStorage> dataStorages = dataStorageDao.loadDataStoragesByNFSPath(s3Bucket.getPath());
        assertThat(dataStorages).isEmpty();
    }

    @Test
    public void shouldLoadStorageWithFolders() {
        Folder root = buildFolder(null);
        root.setParentId(0L);
        Folder folder = buildFolder(root.getId());
        folder.setParent(root);
        Folder parent = buildFolder(folder.getId());
        parent.setParent(folder);

        s3Bucket.setParentFolderId(parent.getId());
        dataStorageDao.createDataStorage(s3Bucket);

        AbstractDataStorage loaded = dataStorageDao.loadStorageWithParents(s3Bucket.getId());
        validateCommonStorage(loaded, s3Bucket);
        verifyFolderTree(parent, loaded.getParent());
    }

    private void validateCreatedStorage(AbstractDataStorage actual) {
        assertThat(actual.getId())
                .isNotNull()
                .isGreaterThan(0L);
        assertThat(actual.getCreatedDate())
                .isNotNull();
    }

    private void validateNFSStorage(AbstractDataStorage actual, NFSDataStorage nfsStorage) {
        validateCommonStorage(actual, nfsStorage);
        assertThat(actual)
                .isInstanceOfAny(NFSDataStorage.class);
    }

    private void validateS3Storage(AbstractDataStorage actual, S3bucketDataStorage expected) {
        validateCommonStorage(actual, expected);
        assertThat(actual)
                .isInstanceOfAny(S3bucketDataStorage.class);
        S3bucketDataStorage bucket = (S3bucketDataStorage) actual;

        assertThat(bucket)
                .hasRegionId(expected.getRegionId())
                .hasAllowedCidrs(expected.getAllowedCidrs());
    }

    private void validateCommonStorage(AbstractDataStorage actual, AbstractDataStorage expected) {
        assertThat(actual)
                .isNotNull()
                .hasId(expected.getId())
                .hasName(expected.getName())
                .isLocked(expected.isLocked())
                .hasOwner(expected.getOwner())
                .hasDescription(expected.getDescription())
                .hasPath(expected.getPath())
                .hasType(expected.getType())
                .hasParentFolderId(expected.getParentFolderId())
                .hasPolicy(expected.getStoragePolicy())
                .hasMountPoint(expected.getMountPoint())
                .hasMountOptions(expected.getMountOptions())
                .hasDelimiter(expected.getDelimiter())
                .hasPathMask(expected.getPathMask())
                .isShared(expected.isShared())
                .isPolicySuported(expected.isPolicySupported());
    }

    private Folder buildFolder(final Long parentId) {
        Folder folder = ObjectCreatorUtils.createFolder("testFolder", parentId);
        folder.setOwner(TEST_OWNER);
        folderDao.createFolder(folder);
        return folder;
    }

    private void verifyFolderTree(final Folder expected, final Folder actual) {
        Assert.assertEquals(expected.getId(), actual.getId());
        Assert.assertEquals(expected.getParentId(), actual.getParentId());
        if (expected.getParent() != null) {
            verifyFolderTree(expected.getParent(), actual.getParent());
        }
    }
}
