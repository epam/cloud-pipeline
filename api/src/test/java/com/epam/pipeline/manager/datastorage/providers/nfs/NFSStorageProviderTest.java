/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.controller.vo.EntityVO;
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
import com.epam.pipeline.entity.metadata.MetadataEntry;
import com.epam.pipeline.entity.metadata.PipeConfValue;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.user.Role;
import com.epam.pipeline.manager.cluster.KubernetesManager;
import com.epam.pipeline.manager.datastorage.FileShareMountManager;
import com.epam.pipeline.manager.metadata.MetadataManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.region.*;
import com.epam.pipeline.manager.user.ExternalUIDManager;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.mapper.region.CloudRegionMapper;
import com.epam.pipeline.test.creator.CommonCreatorConstants;
import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.epam.pipeline.AbstractSpringTest;
import com.epam.pipeline.dao.datastorage.DataStorageDao;
import com.epam.pipeline.entity.datastorage.nfs.NFSDataStorage;
import com.epam.pipeline.manager.CmdExecutor;

import static org.junit.jupiter.api.Assertions.*;


@Transactional(propagation = Propagation.REQUIRES_NEW)
public class NFSStorageProviderTest extends AbstractSpringTest {
    private static final Integer DEFAULT_PAGE_SIZE = 40;
    private static final String TEST_PATH = "localhost";
    private static final String TEST_STORAGE_NAME = "testStorage";
    private static final String STORAGE_NAME = "bucket";
    private static final String TEST_PREFIX = ":/test";

    private static final Long TEST_USER_ID = 1L;
    private static final String TEST_USER_NAME = "test_user";
    private static final Integer UID_SEED = 70000;
    private static final Long EXTERNAL_UID = 5000L;
    private static final Long EXTERNAL_GID = 6000L;
    private static final String UID_FIELD = "linux_external_uid";
    private static final String GID_FIELD = "linux_external_gid";
    private static final Long ROLE_ID = 1L;
    public static final String TEST_FILE_NAME = "testFile.txt";

    @Mock
    private CmdExecutor mockCmdExecutor;

    @Mock
    private KubernetesManager kubernetesManager;

    @Mock
    private MetadataManager mockMetadataManager;

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

    @Autowired
    private ExternalUIDManager externalUIDManager;

    @MockBean
    CloudRegionAspect cloudRegionAspect;

    private Long awsRegionId;
    private FileShareMount awsFileShareMount;
    private FileShareMount azureFileShareMount;

    private static File testMountPoint = new File("test_mount_point");

    @BeforeAll
    public static void setUpClass() throws Exception {
        if (!testMountPoint.exists()) {
            testMountPoint.mkdir();
        }

        assertTrue(testMountPoint.exists(), "Could not create test mounting point!");
    }

    @BeforeEach
    public void setUp() throws Exception {

        MockitoAnnotations.initMocks(this);
        ReflectionTestUtils.setField(nfsStorageMounter, "dataStorageDao", dataStorageDao);
        ReflectionTestUtils.setField(nfsStorageMounter, "rootMountPoint", testMountPoint.getAbsolutePath());
        ReflectionTestUtils.setField(nfsStorageMounter, "cmdExecutor", mockCmdExecutor);

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

    @AfterAll
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

        assertEquals(dataStorage.getPath(), path);

        NFSDataStorage dataStorage2 = new NFSDataStorage(1L, TEST_STORAGE_NAME,
                TEST_PATH + ":root/" + STORAGE_NAME + 1);
        dataStorage2.setFileShareMountId(awsFileShareMount.getId());
        dataStorage2.setOwner("test@user.com");
        String path2 = nfsProvider.createStorage(dataStorage2);
        dataStorageDao.createDataStorage(dataStorage2);

        assertEquals(dataStorage2.getPath(), path2);


        File mountRootDir = new File(testMountPoint, TEST_PATH + "/root");
        assertTrue(mountRootDir.exists());

        File dataStorageRoot = new File(mountRootDir.getPath() + "/" + STORAGE_NAME);
        assertTrue(dataStorageRoot.exists());

        nfsProvider.deleteStorage(dataStorage);

        assertFalse(dataStorageRoot.exists());
        assertTrue(mountRootDir.exists());

        //emulate that database doesn't contain datastorages with mountRootDir
        dataStorageDao.deleteDataStorage(dataStorage.getId());

        nfsProvider.deleteStorage(dataStorage2);
        assertFalse(mountRootDir.exists());

    }

    @Test
    public void testCreateDeleteAzureSmbStorage() {
        NFSDataStorage dataStorage = new NFSDataStorage(0L, TEST_STORAGE_NAME,
                TEST_PATH + "/root/" + STORAGE_NAME);
        dataStorage.setFileShareMountId(azureFileShareMount.getId());
        dataStorage.setOwner("test@user.com");
        String path = nfsProvider.createStorage(dataStorage);
        dataStorageDao.createDataStorage(dataStorage);

        assertEquals(dataStorage.getPath(), path);


        File mountRootDir = new File(testMountPoint, TEST_PATH + "/root");
        assertTrue(mountRootDir.exists());

        File dataStorageRoot = new File(mountRootDir.getPath() + "/" + STORAGE_NAME);
        assertTrue(dataStorageRoot.exists());

        nfsProvider.deleteStorage(dataStorage);

        assertFalse(dataStorageRoot.exists());
        assertFalse(mountRootDir.exists());

    }

    @Test
    public void testCreateFileFolderAndList() {
        NFSDataStorage dataStorage = new NFSDataStorage(0L, TEST_STORAGE_NAME, TEST_PATH + TEST_PREFIX);
        dataStorage.setFileShareMountId(awsFileShareMount.getId());
        nfsProvider.createStorage(dataStorage);
        nfsProvider.createFile(dataStorage, TEST_FILE_NAME, "testContent".getBytes());

        File dataStorageRoot = new File(testMountPoint, TEST_PATH + "/test");
        File testFile = new File(dataStorageRoot, TEST_FILE_NAME);
        assertTrue(testFile.exists());

        String testFolderName = "testFolder";
        nfsProvider.createFolder(dataStorage, testFolderName);

        File testFolder = new File(dataStorageRoot, testFolderName);
        assertTrue(testFolder.exists());

        DataStorageListing listing = nfsProvider.getItems(dataStorage, null, false, DEFAULT_PAGE_SIZE, null, null);
        assertFalse(listing.getResults().isEmpty());

        Optional<AbstractDataStorageItem>
            loadedFile = listing.getResults().stream()
            .filter(i -> i.getType() == DataStorageItemType.File)
            .findFirst();

        assertTrue(loadedFile.isPresent());
        assertEquals(TEST_FILE_NAME, loadedFile.get().getName());
        assertEquals(TEST_FILE_NAME, loadedFile.get().getPath());
        assertNotNull(((DataStorageFile) loadedFile.get()).getChanged());

        Optional<AbstractDataStorageItem>
            loadedFolder = listing.getResults().stream()
            .filter(i -> i.getType() == DataStorageItemType.Folder)
            .findFirst();

        assertTrue(loadedFolder.isPresent());
        assertEquals(testFolderName, loadedFolder.get().getName());
        assertEquals(testFolderName + "/", loadedFolder.get().getPath());
        assertNull(listing.getNextPageMarker());

        listing = nfsProvider.getItems(dataStorage, null, false, 1, null, null);
        assertEquals("2", listing.getNextPageMarker());
        listing = nfsProvider.getItems(dataStorage, null, false, 1, listing.getNextPageMarker(), null);
        assertNull(listing.getNextPageMarker());
        assertFalse(listing.getResults().isEmpty());
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

        assertTrue(Files.exists(oldPath));
        assertTrue(Files.isRegularFile(oldPath));
        assertTrue(Files.exists(newPath));
        assertTrue(Files.isRegularFile(newPath));
        assertArrayEquals(CommonCreatorConstants.TEST_BYTES,
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

        assertTrue(Files.exists(oldPath));
        assertTrue(Files.isDirectory(oldPath));
        assertTrue(Files.exists(newPath));
        assertTrue(Files.isDirectory(newPath));

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

        String testFolderName = "testFolder";
        String testFolder2Name = "testFolder2";
        nfsProvider.createFile(dataStorage, TEST_FILE_NAME, "testContent".getBytes());
        nfsProvider.createFolder(dataStorage, testFolderName);
        nfsProvider.createFolder(dataStorage, testFolder2Name);

        File dataStorageRoot = new File(testMountPoint, rootPath + "/test");

        String newFilePath = testFolderName + "/" + TEST_FILE_NAME;
        DataStorageFile file = nfsProvider.moveFile(dataStorage, TEST_FILE_NAME, newFilePath);

        assertEquals(newFilePath, file.getPath());

        File oldFileLocation = new File(dataStorageRoot, TEST_FILE_NAME);
        File newFileLocation = new File(dataStorageRoot, newFilePath);
        assertTrue(newFileLocation.exists());
        assertFalse(oldFileLocation.exists());

        String newFolder2Path = testFolderName + "/" + testFolder2Name;
        DataStorageFolder folder = nfsProvider.moveFolder(dataStorage, testFolder2Name, newFolder2Path);

        assertEquals(newFolder2Path, folder.getPath());

        File oldFolderLocation = new File(dataStorageRoot, testFolder2Name);
        File newFolderLocation = new File(dataStorageRoot, newFolder2Path);
        assertTrue(newFolderLocation.exists());
        assertFalse(oldFolderLocation.exists());

        nfsProvider.deleteFile(dataStorage, newFilePath, null, true);
        assertFalse(newFileLocation.exists());

        nfsProvider.deleteFolder(dataStorage, newFolder2Path, true);
        assertFalse(newFolderLocation.exists());
    }

    @Test
    public void testEditFile() {
        NFSDataStorage dataStorage = new NFSDataStorage(0L, TEST_STORAGE_NAME, TEST_PATH + TEST_PREFIX);
        dataStorage.setFileShareMountId(awsFileShareMount.getId());
        nfsProvider.createStorage(dataStorage);

        byte[] testContent = "testContent".getBytes();
        byte[] newContent = "new content".getBytes();

        DataStorageFile file = nfsProvider.createFile(dataStorage, TEST_FILE_NAME, testContent);

        assertArrayEquals(
                testContent,
                nfsProvider.getFile(dataStorage, TEST_FILE_NAME, file.getVersion(), Long.MAX_VALUE).getContent()
        );

        DataStorageFile updatedFile = nfsProvider.createFile(dataStorage, TEST_FILE_NAME, newContent);

        assertArrayEquals(
                newContent,
                nfsProvider.getFile(dataStorage, TEST_FILE_NAME, updatedFile.getVersion(), Long.MAX_VALUE).getContent()
        );
    }

    @Test
    public void shouldUseDefaultUidAndGidWhenExternalDisabled() {
        setupExternalIdTest(false, null, null);

        createFileForChownTest();

        final Long expectedUid = TEST_USER_ID + UID_SEED;
        verifyChownCommand(expectedUid, expectedUid);
    }

    @Test
    public void shouldUseExternalUidAndGidFromMetadata() {
        setupExternalIdTest(true, UID_FIELD, GID_FIELD);
        mockUserMetadata(UID_FIELD, EXTERNAL_UID.toString());
        mockUserMetadata(GID_FIELD, EXTERNAL_GID.toString());
        mockRoleMetadata(EXTERNAL_GID.toString());

        createFileForChownTest();

        verifyChownCommand(EXTERNAL_UID, EXTERNAL_GID);
    }

    @Test
    public void shouldFallBackToDefaultUidWhenMetadataMissing() {
        setupExternalIdTest(true, UID_FIELD, null);
        mockEmptyUserMetadata(UID_FIELD);

        createFileForChownTest();

        final Long expectedUid = TEST_USER_ID + UID_SEED;
        verifyChownCommand(expectedUid, expectedUid);
    }

    @Test
    public void shouldFallBackToDefaultGidWhenGidMetadataMissing() {
        setupExternalIdTest(true, UID_FIELD, GID_FIELD);
        mockUserMetadata(UID_FIELD, EXTERNAL_UID.toString());
        mockEmptyUserMetadata(GID_FIELD);

        createFileForChownTest();

        verifyChownCommand(EXTERNAL_UID, EXTERNAL_UID);
    }

    @Test
    public void shouldFallBackToDefaultGidWhenRoleDoesNotMatchGid() {
        setupExternalIdTest(true, UID_FIELD, GID_FIELD);
        mockUserMetadata(UID_FIELD, EXTERNAL_UID.toString());
        mockUserMetadata(GID_FIELD, EXTERNAL_GID.toString());
        mockRoleMetadata("9999");

        createFileForChownTest();

        verifyChownCommand(EXTERNAL_UID, EXTERNAL_UID);
    }

    @Test
    public void shouldFallBackToDefaultGidWhenUserHasNoRoles() {
        final PipelineUser userNoRoles = PipelineUser.builder()
                .id(TEST_USER_ID)
                .userName(TEST_USER_NAME)
                .roles(Collections.emptyList())
                .admin(false)
                .build();
        setupExternalIdTest(true, UID_FIELD, GID_FIELD, userNoRoles);
        mockUserMetadata(UID_FIELD, EXTERNAL_UID.toString());
        mockUserMetadata(GID_FIELD, EXTERNAL_GID.toString());

        createFileForChownTest();

        verifyChownCommand(EXTERNAL_UID, EXTERNAL_UID);
    }

    private void setupExternalIdTest(final boolean externalEnabled,
                                     final String uidFieldName,
                                     final String gidFieldName) {
        final Role role = new Role();
        role.setId(ROLE_ID);
        role.setName("ROLE_TEST_GROUP");

        final PipelineUser user = PipelineUser.builder()
                .id(TEST_USER_ID)
                .userName(TEST_USER_NAME)
                .roles(Collections.singletonList(role))
                .admin(false)
                .build();
        setupExternalIdTest(externalEnabled, uidFieldName, gidFieldName, user);
    }

    private void setupExternalIdTest(final boolean externalEnabled,
                                     final String uidFieldName,
                                     final String gidFieldName,
                                     final PipelineUser user) {
        final PreferenceManager mockPreferenceManager = mock(PreferenceManager.class);
        final AuthManager mockAuthManager = mock(AuthManager.class);

        when(mockPreferenceManager.getPreference(SystemPreferences.SYSTEM_SSH_DEFAULT_ROOT_USER_ENABLED))
                .thenReturn(false);
        when(mockPreferenceManager.getPreference(SystemPreferences.LAUNCH_UID_SEED))
                .thenReturn(UID_SEED);
        when(mockPreferenceManager.getPreference(SystemPreferences.LAUNCH_EXTERNAL_UID_ENABLE))
                .thenReturn(externalEnabled);
        when(mockPreferenceManager.getPreference(SystemPreferences.LAUNCH_EXTERNAL_UID_FIELD_NAME))
                .thenReturn(uidFieldName);
        when(mockPreferenceManager.getPreference(SystemPreferences.LAUNCH_EXTERNAL_GID_FIELD_NAME))
                .thenReturn(gidFieldName);
        when(mockAuthManager.getCurrentUser()).thenReturn(user);

        ReflectionTestUtils.setField(nfsProvider, "preferenceManager", mockPreferenceManager);
        ReflectionTestUtils.setField(nfsProvider, "authManager", mockAuthManager);
        ReflectionTestUtils.setField(externalUIDManager, "preferenceManager", mockPreferenceManager);
        ReflectionTestUtils.setField(externalUIDManager, "metadataManager", mockMetadataManager);
    }

    private void createFileForChownTest() {
        final NFSDataStorage dataStorage = new NFSDataStorage(0L, TEST_STORAGE_NAME, TEST_PATH + TEST_PREFIX);
        dataStorage.setFileShareMountId(awsFileShareMount.getId());
        nfsProvider.createStorage(dataStorage);
        nfsProvider.createFile(dataStorage, TEST_FILE_NAME, "content".getBytes());
    }

    private void verifyChownCommand(final Long expectedUid, final Long expectedGid) {
        final ArgumentCaptor<String> cmdCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockCmdExecutor).executeCommand(cmdCaptor.capture());
        final List<String> commands = cmdCaptor.getAllValues();
        final String chownCmd = commands.stream()
                .filter(cmd -> cmd.contains("chown"))
                .reduce((first, second) -> second)
                .orElse(null);
        Assert.assertNotNull("Expected chown command to be executed", chownCmd);
        Assert.assertTrue("Expected uid " + expectedUid + " in command: " + chownCmd,
                chownCmd.contains(expectedUid + ":" + expectedGid));
    }

    private void mockUserMetadata(final String key, final String value) {
        final EntityVO userEntity = new EntityVO(TEST_USER_ID, AclClass.PIPELINE_USER);
        final MetadataEntry entry = new MetadataEntry();
        entry.setEntity(userEntity);
        final Map<String, PipeConfValue> data = new HashMap<>();
        data.put(key, new PipeConfValue("string", value));
        entry.setData(data);
        when(mockMetadataManager.listMetadataItemsByKey(eq(key), eq(Collections.singletonList(userEntity))))
                .thenReturn(Collections.singletonList(entry));
    }

    private void mockEmptyUserMetadata(final String key) {
        final EntityVO userEntity = new EntityVO(TEST_USER_ID, AclClass.PIPELINE_USER);
        when(mockMetadataManager.listMetadataItemsByKey(eq(key), eq(Collections.singletonList(userEntity))))
                .thenReturn(Collections.emptyList());
    }

    private void mockRoleMetadata(final String value) {
        final EntityVO roleEntity = new EntityVO(ROLE_ID, AclClass.ROLE);
        final MetadataEntry entry = new MetadataEntry();
        entry.setEntity(roleEntity);
        final Map<String, PipeConfValue> data = new HashMap<>();
        data.put(NFSStorageProviderTest.UID_FIELD, new PipeConfValue("string", value));
        entry.setData(data);
        when(mockMetadataManager.listMetadataItemsByKey(eq(NFSStorageProviderTest.UID_FIELD),
                eq(Collections.singletonList(roleEntity)))).thenReturn(Collections.singletonList(entry));
    }

    private List<CloudRegionHelper> helpers() {
        AzureRegionHelper azure = mock(AzureRegionHelper.class);
        when(azure.getProvider()).thenReturn(CloudProvider.AZURE);
        AwsRegionHelper aws = mock(AwsRegionHelper.class);
        when(aws.getProvider()).thenReturn(CloudProvider.AWS);
        return Arrays.asList(new CloudRegionHelper[]{azure, aws});
    }
}