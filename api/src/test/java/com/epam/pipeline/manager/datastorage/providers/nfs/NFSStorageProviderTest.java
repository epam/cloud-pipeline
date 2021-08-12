/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
 *
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

package com.epam.pipeline.manager.datastorage.providers.nfs;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.controller.vo.region.AWSRegionDTO;
import com.epam.pipeline.controller.vo.region.AzureRegionDTO;
import com.epam.pipeline.dao.region.CloudRegionDao;
import com.epam.pipeline.entity.datastorage.AbstractDataStorageItem;
import com.epam.pipeline.entity.datastorage.DataStorageFile;
import com.epam.pipeline.entity.datastorage.DataStorageFolder;
import com.epam.pipeline.entity.datastorage.DataStorageItemType;
import com.epam.pipeline.entity.datastorage.DataStorageListing;
import com.epam.pipeline.entity.datastorage.FileShareMount;
import com.epam.pipeline.entity.datastorage.MountType;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.manager.cluster.KubernetesManager;
import com.epam.pipeline.manager.datastorage.FileShareMountManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.region.*;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.mapper.region.CloudRegionMapper;
import com.epam.pipeline.test.creator.CommonCreatorConstants;
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
import org.springframework.boot.test.mock.mockito.MockBean;
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
    private static final String TEST_PREFIX = ":/test";

    @Mock
    private CmdExecutor mockCmdExecutor;

    @Mock
    private KubernetesManager kubernetesManager;

    @Autowired
    private DataStorageDao dataStorageDao;

    @Autowired
    private NFSStorageProvider nfsProvider;

    @Autowired
    private FileShareMountManager fileShareMountManager;

    @Autowired
    private CloudRegionMapper cloudRegionMapper;

    @Autowired
    private CloudRegionDao cloudRegionDao;

    @Autowired
    private MessageHelper messageHelper;

    @Autowired
    private PreferenceManager preferenceManager;

    @Autowired
    private AuthManager authManager;

    @Autowired
    private NFSStorageMounter nfsStorageMounter;

    @MockBean
    CloudRegionAspect cloudRegionAspect;

    private Long awsRegionId;
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
        Whitebox.setInternalState(nfsStorageMounter, "dataStorageDao", dataStorageDao);
        Whitebox.setInternalState(nfsStorageMounter, "rootMountPoint", testMountPoint.getAbsolutePath());
        Whitebox.setInternalState(nfsStorageMounter, "cmdExecutor", mockCmdExecutor);

        when(mockCmdExecutor.executeCommand(anyString())).thenReturn("");

        CloudRegionManager regionManager = new CloudRegionManager(cloudRegionDao, cloudRegionMapper,
                fileShareMountManager, messageHelper, preferenceManager, authManager, kubernetesManager, helpers());

        AWSRegionDTO awsRegion = new AWSRegionDTO();
        awsRegion.setName("region");
        awsRegion.setRegionCode("us-east-1");
        awsRegion.setProvider(CloudProvider.AWS);
        awsRegionId = regionManager.create(awsRegion).getId();

        awsFileShareMount = new FileShareMount();
        awsFileShareMount.setMountType(MountType.NFS);
        awsFileShareMount.setMountRoot(TEST_PATH);
        awsFileShareMount.setRegionId(awsRegionId);
        fileShareMountManager.save(awsFileShareMount);

        AzureRegionDTO azureRegion = new AzureRegionDTO();
        azureRegion.setName("azure-centralus");
        azureRegion.setRegionCode("centralus");
        azureRegion.setProvider(CloudProvider.AZURE);
        azureRegion.setStorageAccount("azure_acc");
        azureRegion.setStorageAccountKey("azure_acc");
        regionManager.create(azureRegion);

        azureFileShareMount = new FileShareMount();
        azureFileShareMount.setMountType(MountType.NFS);
        azureFileShareMount.setMountRoot(TEST_PATH);
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
                TEST_PATH + ":root/" + STORAGE_NAME);
        dataStorage.setFileShareMountId(awsFileShareMount.getId());
        dataStorage.setOwner("test@user.com");
        String path = nfsProvider.createStorage(dataStorage);
        dataStorageDao.createDataStorage(dataStorage);

        Assert.assertEquals(dataStorage.getPath(), path);

        NFSDataStorage dataStorage2 = new NFSDataStorage(1L, TEST_STORAGE_NAME,
                TEST_PATH + ":root/" + STORAGE_NAME + 1);
        dataStorage2.setFileShareMountId(awsFileShareMount.getId());
        dataStorage2.setOwner("test@user.com");
        String path2 = nfsProvider.createStorage(dataStorage2);
        dataStorageDao.createDataStorage(dataStorage2);

        Assert.assertEquals(dataStorage2.getPath(), path2);


        File mountRootDir = new File(testMountPoint, TEST_PATH + "/root");
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
        NFSDataStorage dataStorage = new NFSDataStorage(0L, TEST_STORAGE_NAME, TEST_PATH + TEST_PREFIX);
        dataStorage.setFileShareMountId(awsFileShareMount.getId());
        nfsProvider.createStorage(dataStorage);
        String testFileName = "testFile.txt";
        nfsProvider.createFile(dataStorage, testFileName, "testContent".getBytes());

        File dataStorageRoot = new File(testMountPoint, TEST_PATH + "/test");
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
    public void testCopyFile() {
        final NFSDataStorage storage = new NFSDataStorage(0L, TEST_STORAGE_NAME, TEST_PATH + TEST_PREFIX);
        storage.setFileShareMountId(awsFileShareMount.getId());
        nfsProvider.createStorage(storage);
        final Path rootPath = Paths.get(testMountPoint.toString(), TEST_PATH,  "test");
        final Path oldPath = rootPath.resolve("oldFilePath");
        final Path newPath = rootPath.resolve("newFilePath");
        nfsProvider.createFile(storage, oldPath.getFileName().toString(), CommonCreatorConstants.TEST_BYTES);

        nfsProvider.copyFile(storage, oldPath.getFileName().toString(), newPath.getFileName().toString());

        Assert.assertTrue(Files.exists(oldPath));
        Assert.assertTrue(Files.isRegularFile(oldPath));
        Assert.assertTrue(Files.exists(newPath));
        Assert.assertTrue(Files.isRegularFile(newPath));
        Assert.assertArrayEquals(CommonCreatorConstants.TEST_BYTES,
                nfsProvider.getFile(storage, newPath.getFileName().toString(), null, Long.MAX_VALUE).getContent());

        nfsProvider.deleteFile(storage, oldPath.getFileName().toString(), null, true);
        nfsProvider.deleteFile(storage, newPath.getFileName().toString(), null, true);
    }

    @Test
    public void testCopyFolder() {
        final NFSDataStorage storage = new NFSDataStorage(0L, TEST_STORAGE_NAME, TEST_PATH + TEST_PREFIX);
        storage.setFileShareMountId(awsFileShareMount.getId());
        nfsProvider.createStorage(storage);
        final Path rootPath = Paths.get(testMountPoint.toString(), TEST_PATH,  "test");
        final Path oldPath = rootPath.resolve("oldFolderPath");
        final Path newPath = rootPath.resolve("newFolderPath");
        nfsProvider.createFolder(storage, oldPath.getFileName().toString());

        nfsProvider.copyFolder(storage, oldPath.getFileName().toString(), newPath.getFileName().toString());

        Assert.assertTrue(Files.exists(oldPath));
        Assert.assertTrue(Files.isDirectory(oldPath));
        Assert.assertTrue(Files.exists(newPath));
        Assert.assertTrue(Files.isDirectory(newPath));

        nfsProvider.deleteFolder(storage, oldPath.getFileName().toString(), true);
        nfsProvider.deleteFolder(storage, newPath.getFileName().toString(), true);
    }

    @Test
    public void testMoveDeleteFile() {
        String rootPath = TEST_PATH + 1;
        FileShareMount awsFileShareMount = new FileShareMount();
        awsFileShareMount.setMountType(MountType.NFS);
        awsFileShareMount.setMountRoot(rootPath);
        awsFileShareMount.setRegionId(awsRegionId);
        fileShareMountManager.save(awsFileShareMount);

        NFSDataStorage dataStorage = new NFSDataStorage(0L, TEST_STORAGE_NAME, rootPath + TEST_PREFIX);
        dataStorage.setFileShareMountId(awsFileShareMount.getId());
        nfsProvider.createStorage(dataStorage);

        String testFileName = "testFile.txt";
        String testFolderName = "testFolder";
        String testFolder2Name = "testFolder2";
        nfsProvider.createFile(dataStorage, testFileName, "testContent".getBytes());
        nfsProvider.createFolder(dataStorage, testFolderName);
        nfsProvider.createFolder(dataStorage, testFolder2Name);

        File dataStorageRoot = new File(testMountPoint, rootPath + "/test");

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
        NFSDataStorage dataStorage = new NFSDataStorage(0L, TEST_STORAGE_NAME, TEST_PATH + TEST_PREFIX);
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

    private List<CloudRegionHelper> helpers() {
        AzureRegionHelper azure = mock(AzureRegionHelper.class);
        when(azure.getProvider()).thenReturn(CloudProvider.AZURE);
        AwsRegionHelper aws = mock(AwsRegionHelper.class);
        when(aws.getProvider()).thenReturn(CloudProvider.AWS);
        return Arrays.asList(new CloudRegionHelper[]{azure, aws});
    }
}