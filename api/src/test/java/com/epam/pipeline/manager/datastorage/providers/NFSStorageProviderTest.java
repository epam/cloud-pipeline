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

package com.epam.pipeline.manager.datastorage.providers;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.Optional;

import com.epam.pipeline.controller.vo.CloudRegionVO;
import com.epam.pipeline.entity.datastorage.*;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.manager.datastorage.FileShareMountManager;
import com.epam.pipeline.manager.region.CloudRegionManager;
import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.internal.util.reflection.Whitebox;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.epam.pipeline.AbstractSpringTest;
import com.epam.pipeline.dao.datastorage.DataStorageDao;
import com.epam.pipeline.entity.datastorage.nfs.NFSDataStorage;
import com.epam.pipeline.manager.CmdExecutor;

@Transactional(propagation = Propagation.REQUIRES_NEW)
public class NFSStorageProviderTest extends AbstractSpringTest {
    private static final Integer DEFAULT_PAGE_SIZE = 40;
    private static final String TEST_PATH = "localhost";
    private static final String TEST_STORAGE_NAME = "testStorage";
    private static final String STORAGE_NAME = "bucket";

    @Mock
    private CmdExecutor mockCmdExecutor;

    @Autowired
    private DataStorageDao dataStorageDao;

    @Autowired
    private NFSStorageProvider nfsProvider;

    @Autowired
    private FileShareMountManager fileShareMountManager;

    @Autowired
    private CloudRegionManager regionManager;

    private FileShareMount awsFileShareMount;
    private FileShareMount azureFileShareMount;

    private static File testMountPoint = new File("test_mount_point");

    @BeforeClass
    public static void setUpClass() throws Exception {
        if (!testMountPoint.exists()) {
            testMountPoint.mkdir();
        }

        Assert.assertTrue("Could not create test mounting point!", testMountPoint.exists());
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        Whitebox.setInternalState(nfsProvider, "dataStorageDao", dataStorageDao);
        Whitebox.setInternalState(nfsProvider, "rootMountPoint", testMountPoint.getAbsolutePath());
        Whitebox.setInternalState(nfsProvider, "cmdExecutor", mockCmdExecutor);

        when(mockCmdExecutor.executeCommand(anyString())).thenReturn("");

        CloudRegionVO awsRegion = new CloudRegionVO();
        awsRegion.setName("region");
        awsRegion.setRegionCode("us-east-1");
        awsRegion.setProvider(CloudProvider.AWS);
        regionManager.create(awsRegion);

        awsFileShareMount = new FileShareMount();
        awsFileShareMount.setMountType(MountType.NFS);
        awsFileShareMount.setRegionId(regionManager.load(CloudProvider.AWS, "us-east-1").getId());
        fileShareMountManager.save(awsFileShareMount);

        CloudRegionVO azureRegion = new CloudRegionVO();
        azureRegion.setName("azure-centralus");
        azureRegion.setRegionCode("centralus");
        azureRegion.setProvider(CloudProvider.AZURE);
        azureRegion.setStorageAccount("azure_acc");
        azureRegion.setStorageAccountKey("azure_acc");
        regionManager.create(azureRegion);

        azureFileShareMount = new FileShareMount();
        azureFileShareMount.setMountType(MountType.NFS);
        azureFileShareMount.setRegionId(regionManager.load(CloudProvider.AZURE, "centralus").getId());
        fileShareMountManager.save(azureFileShareMount);

    }

    @AfterClass
    public static void tearDown() throws Exception {
        FileUtils.deleteQuietly(testMountPoint);
    }

    @Test
    public void testCreateDeleteStorage() {
        NFSDataStorage dataStorage = new NFSDataStorage(0L, TEST_STORAGE_NAME,
                TEST_PATH + 1 + ":root/" + STORAGE_NAME);
        dataStorage.setFileShareMountId(awsFileShareMount.getId());
        dataStorage.setOwner("test@user.com");
        String path = nfsProvider.createStorage(dataStorage);
        dataStorageDao.createDataStorage(dataStorage);

        Assert.assertEquals(dataStorage.getPath(), path);

        NFSDataStorage dataStorage2 = new NFSDataStorage(1L, TEST_STORAGE_NAME,
                TEST_PATH + 1 + ":root/" + STORAGE_NAME + 1);
        dataStorage2.setOwner("test@user.com");
        String path2 = nfsProvider.createStorage(dataStorage2);
        dataStorageDao.createDataStorage(dataStorage2);

        Assert.assertEquals(dataStorage2.getPath(), path2);


        File mountRootDir = new File(testMountPoint, TEST_PATH + 1 + "/root");
        Assert.assertTrue(mountRootDir.exists());

        File dataStorageRoot = new File(mountRootDir.getPath() + "/" + STORAGE_NAME);
        Assert.assertTrue(dataStorageRoot.exists());

        nfsProvider.deleteStorage(dataStorage);

        Assert.assertFalse(dataStorageRoot.exists());
        Assert.assertTrue(mountRootDir.exists());

        //emulate that database doesn't contain datastorages with mountRootDir
        dataStorageDao.deleteDataStorage(dataStorage.getId());

        nfsProvider.deleteStorage(dataStorage2);
        Assert.assertFalse(mountRootDir.exists());

    }

    @Test
    public void testCreateDeleteAzureSmbStorage() {
        NFSDataStorage dataStorage = new NFSDataStorage(0L, TEST_STORAGE_NAME,
                TEST_PATH + "/root/" + STORAGE_NAME);
        dataStorage.setFileShareMountId(azureFileShareMount.getId());
        dataStorage.setOwner("test@user.com");
        String path = nfsProvider.createStorage(dataStorage);
        dataStorageDao.createDataStorage(dataStorage);

        Assert.assertEquals(dataStorage.getPath(), path);


        File mountRootDir = new File(testMountPoint, TEST_PATH + "/root");
        Assert.assertTrue(mountRootDir.exists());

        File dataStorageRoot = new File(mountRootDir.getPath() + "/" + STORAGE_NAME);
        Assert.assertTrue(dataStorageRoot.exists());

        nfsProvider.deleteStorage(dataStorage);

        Assert.assertFalse(dataStorageRoot.exists());
        Assert.assertFalse(mountRootDir.exists());

    }

    @Test
    public void testCreateFileFolderAndList() {
        NFSDataStorage dataStorage = new NFSDataStorage(0L, TEST_STORAGE_NAME, TEST_PATH + 2 + ":/test");
        dataStorage.setFileShareMountId(awsFileShareMount.getId());
        nfsProvider.createStorage(dataStorage);
        String testFileName = "testFile.txt";
        nfsProvider.createFile(dataStorage, testFileName, "testContent".getBytes());

        File dataStorageRoot = new File(testMountPoint, TEST_PATH + 2 + "/test");
        File testFile = new File(dataStorageRoot, testFileName);
        Assert.assertTrue(testFile.exists());

        String testFolderName = "testFolder";
        nfsProvider.createFolder(dataStorage, testFolderName);

        File testFolder = new File(dataStorageRoot, testFolderName);
        Assert.assertTrue(testFolder.exists());

        DataStorageListing listing = nfsProvider.getItems(dataStorage, null, false, DEFAULT_PAGE_SIZE, null);
        Assert.assertFalse(listing.getResults().isEmpty());

        Optional<AbstractDataStorageItem>
            loadedFile = listing.getResults().stream()
            .filter(i -> i.getType() == DataStorageItemType.File)
            .findFirst();

        Assert.assertTrue(loadedFile.isPresent());
        Assert.assertEquals(testFileName, loadedFile.get().getName());
        Assert.assertEquals(testFileName, loadedFile.get().getPath());
        Assert.assertNotNull(((DataStorageFile) loadedFile.get()).getChanged());

        Optional<AbstractDataStorageItem>
            loadedFolder = listing.getResults().stream()
            .filter(i -> i.getType() == DataStorageItemType.Folder)
            .findFirst();

        Assert.assertTrue(loadedFolder.isPresent());
        Assert.assertEquals(testFolderName, loadedFolder.get().getName());
        Assert.assertEquals(testFolderName + "/", loadedFolder.get().getPath());
        Assert.assertNull(listing.getNextPageMarker());

        listing = nfsProvider.getItems(dataStorage, null, false, 1, null);
        Assert.assertEquals("2", listing.getNextPageMarker());
        listing = nfsProvider.getItems(dataStorage, null, false, 1, listing.getNextPageMarker());
        Assert.assertNull(listing.getNextPageMarker());
        Assert.assertFalse(listing.getResults().isEmpty());
    }

    @Test
    public void testMoveDeleteFile() {
        NFSDataStorage dataStorage = new NFSDataStorage(0L, "testStorage", TEST_PATH + 3 + ":/test");
        dataStorage.setFileShareMountId(awsFileShareMount.getId());
        nfsProvider.createStorage(dataStorage);

        String testFileName = "testFile.txt";
        String testFolderName = "testFolder";
        String testFolder2Name = "testFolder2";
        nfsProvider.createFile(dataStorage, testFileName, "testContent".getBytes());
        nfsProvider.createFolder(dataStorage, testFolderName);
        nfsProvider.createFolder(dataStorage, testFolder2Name);

        File dataStorageRoot = new File(testMountPoint, TEST_PATH + 3 + "/test");

        String newFilePath = testFolderName + "/" + testFileName;
        DataStorageFile file = nfsProvider.moveFile(dataStorage, testFileName, newFilePath);

        Assert.assertEquals(newFilePath, file.getPath());

        File oldFileLocation = new File(dataStorageRoot, testFileName);
        File newFileLocation = new File(dataStorageRoot, newFilePath);
        Assert.assertTrue(newFileLocation.exists());
        Assert.assertFalse(oldFileLocation.exists());

        String newFolder2Path = testFolderName + "/" + testFolder2Name;
        DataStorageFolder folder = nfsProvider.moveFolder(dataStorage, testFolder2Name, newFolder2Path);

        Assert.assertEquals(newFolder2Path, folder.getPath());

        File oldFolderLocation = new File(dataStorageRoot, testFolder2Name);
        File newFolderLocation = new File(dataStorageRoot, newFolder2Path);
        Assert.assertTrue(newFolderLocation.exists());
        Assert.assertFalse(oldFolderLocation.exists());

        nfsProvider.deleteFile(dataStorage, newFilePath, null, true);
        Assert.assertFalse(newFileLocation.exists());

        nfsProvider.deleteFolder(dataStorage, newFolder2Path, true);
        Assert.assertFalse(newFolderLocation.exists());
    }

    @Test
    public void testEditFile() {
        NFSDataStorage dataStorage = new NFSDataStorage(0L, "testStorage", TEST_PATH + 3 + ":/test");
        dataStorage.setFileShareMountId(awsFileShareMount.getId());
        nfsProvider.createStorage(dataStorage);

        String testFileName = "testFile.txt";
        byte[] testContent = "testContent".getBytes();
        byte[] newContent = "new content".getBytes();

        DataStorageFile file = nfsProvider.createFile(dataStorage, testFileName, testContent);

        Assert.assertArrayEquals(
                testContent,
                nfsProvider.getFile(dataStorage, testFileName, file.getVersion(), Long.MAX_VALUE).getContent()
        );

        DataStorageFile updatedFile = nfsProvider.createFile(dataStorage, testFileName, newContent);

        Assert.assertArrayEquals(
                newContent,
                nfsProvider.getFile(dataStorage, testFileName, updatedFile.getVersion(), Long.MAX_VALUE).getContent()
        );
    }
}