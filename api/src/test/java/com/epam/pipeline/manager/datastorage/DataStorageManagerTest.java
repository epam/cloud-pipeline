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

package com.epam.pipeline.manager.datastorage;

import com.epam.pipeline.AbstractSpringTest;
import com.epam.pipeline.controller.vo.DataStorageVO;
import com.epam.pipeline.dao.docker.DockerRegistryDao;
import com.epam.pipeline.dao.region.CloudRegionDao;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.datastorage.NFSStorageMountStatus;
import com.epam.pipeline.entity.datastorage.StoragePolicy;
import com.epam.pipeline.entity.datastorage.aws.S3bucketDataStorage;
import com.epam.pipeline.entity.datastorage.nfs.NFSDataStorage;
import com.epam.pipeline.entity.docker.ToolVersion;
import com.epam.pipeline.entity.metadata.MetadataEntry;
import com.epam.pipeline.entity.pipeline.DockerRegistry;
import com.epam.pipeline.entity.pipeline.Folder;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.pipeline.ToolFingerprint;
import com.epam.pipeline.entity.pipeline.ToolGroup;
import com.epam.pipeline.entity.pipeline.ToolVersionFingerprint;
import com.epam.pipeline.entity.preference.Preference;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.manager.MockS3Helper;
import com.epam.pipeline.manager.ObjectCreatorUtils;
import com.epam.pipeline.manager.datastorage.providers.aws.s3.S3StorageProvider;
import com.epam.pipeline.manager.docker.DockerClient;
import com.epam.pipeline.manager.docker.DockerClientFactory;
import com.epam.pipeline.manager.docker.ToolVersionManager;
import com.epam.pipeline.manager.metadata.MetadataManager;
import com.epam.pipeline.manager.pipeline.FolderManager;
import com.epam.pipeline.manager.pipeline.ToolGroupManager;
import com.epam.pipeline.manager.pipeline.ToolManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.region.CloudRegionManager;
import com.epam.pipeline.util.TestUtils;
import com.google.common.collect.Sets;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.SystemUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

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
    private static final String TEST_VERSION = "latest";
    private static final String TEST_IMAGE = "library/image";
    private static final String TEST_USER = "test";
    private static final String TEST_CPU = "500m";
    private static final String TEST_RAM = "1Gi";
    private static final String TOOL_GROUP = "library";
    private static final String TEST_REPO = "repository";

    private static final String TEST_DIGEST = "sha256:aaa";
    private static final Long TEST_SIZE = 123L;
    private static final Date TEST_LAST_MODIFIED_DATE = new Date();
    public static final String PLATFORM = "linux";
    public static final String DAV_MOUNT_TAG = "dav-mount";
    public static final long SECS_IN_HOUR = 3600L;
    public static final long SECS_IN_MIN = 60L;


    @Mock
    private DockerRegistry dockerRegistry;
    @Mock
    private DockerClient dockerClient;
    @MockBean
    private DockerClientFactory dockerClientFactory;

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

    @Autowired
    private ToolManager toolManager;

    @Autowired
    private ToolVersionManager toolVersionManager;

    @Autowired
    private DockerRegistryDao registryDao;

    @Autowired
    private ToolGroupManager toolGroupManager;

    @Autowired
    private MetadataManager metadataManager;

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
    public void testAddRelationForDataStorageAndToolVersion() {
        final Tool tool = createTool();
        DataStorageVO storageVO = ObjectCreatorUtils.constructDataStorageVO(NAME, DESCRIPTION, DataStorageType.S3,
                PATH, STS_DURATION, LTS_DURATION, WITHOUT_PARENT_ID, TEST_MOUNT_POINT, TEST_MOUNT_OPTIONS
        );
        storageVO.setToolsToMount(
                Collections.singletonList(ToolFingerprint.builder().id(tool.getId())
                        .versions(
                                Collections.singletonList(ToolVersionFingerprint.builder()
                                        .id(toolVersionManager.loadToolVersion(tool.getId(), TEST_VERSION).getId())
                                        .version(TEST_VERSION)
                                        .build())
                        ).build()
                )
        );
        AbstractDataStorage saved = storageManager.create(storageVO, false, false, false).getEntity();
        AbstractDataStorage loaded = storageManager.load(saved.getId());
        Assert.assertFalse(loaded.getToolsToMount().isEmpty());

        storageVO.setId(loaded.getId());
        storageVO.setToolsToMount(
                Collections.singletonList(ToolFingerprint.builder().id(tool.getId()).build())
        );

        storageManager.update(storageVO);
        storageVO.setId(loaded.getId());
        storageManager.update(storageVO);
        loaded = storageManager.load(saved.getId());
        Assert.assertFalse(loaded.getToolsToMount().isEmpty());
        Assert.assertTrue(
                CollectionUtils.isEmpty(
                        loaded.getToolsToMount().stream().filter(t -> t.getVersions() != null)
                                .flatMap(v -> v.getVersions().stream())
                                .collect(Collectors.toList())
                )
        );
    }

    @Test(expected = IllegalArgumentException.class)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void testCantAddRelationForDataStorageAndToolVersionIfItDoesNotExists() {
        final Tool tool = createTool();
        DataStorageVO storageVO = ObjectCreatorUtils.constructDataStorageVO(NAME, DESCRIPTION, DataStorageType.S3,
                PATH, STS_DURATION, LTS_DURATION, WITHOUT_PARENT_ID, TEST_MOUNT_POINT, TEST_MOUNT_OPTIONS
        );
        storageVO.setToolsToMount(
                Collections.singletonList(ToolFingerprint.builder().id(tool.getId())
                        .versions(
                                Collections.singletonList(ToolVersionFingerprint.builder()
                                        .id(toolVersionManager.loadToolVersion(tool.getId(), TEST_VERSION).getId())
                                        .version(TEST_VERSION + "_fake")
                                        .build())
                        ).build()
                )
        );
        storageManager.create(storageVO, false, false, false);
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void testRelationForDataStorageAndToolVersionIsRemovedWhenDeleteToolVersions() {
        final Tool tool = createTool();
        DataStorageVO storageVO = ObjectCreatorUtils.constructDataStorageVO(NAME, DESCRIPTION, DataStorageType.S3,
                PATH, STS_DURATION, LTS_DURATION, WITHOUT_PARENT_ID, TEST_MOUNT_POINT, TEST_MOUNT_OPTIONS
        );
        storageVO.setToolsToMount(
                    Collections.singletonList(ToolFingerprint.builder().id(tool.getId())
                            .versions(
                                    Collections.singletonList(ToolVersionFingerprint.builder()
                                            .id(toolVersionManager.loadToolVersion(tool.getId(), TEST_VERSION).getId())
                                            .version(TEST_VERSION)
                                            .build())
                            ).build()
                    )
        );
        AbstractDataStorage saved = storageManager.create(storageVO, false,
                false, false).getEntity();
        AbstractDataStorage loaded = storageManager.load(saved.getId());
        Assert.assertFalse(loaded.getToolsToMount().isEmpty());
        toolVersionManager.deleteToolVersions(tool.getId());
        loaded = storageManager.load(saved.getId());
        Assert.assertTrue(loaded.getToolsToMount().isEmpty());

    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void testRelationForDataStorageAndToolVersionIsRemovedWhenDeleteToolVersion() {
        final Tool tool = createTool();
        DataStorageVO storageVO = ObjectCreatorUtils.constructDataStorageVO(NAME, DESCRIPTION, DataStorageType.S3,
                PATH, STS_DURATION, LTS_DURATION, WITHOUT_PARENT_ID, TEST_MOUNT_POINT, TEST_MOUNT_OPTIONS
        );
        storageVO.setToolsToMount(
                Collections.singletonList(ToolFingerprint.builder().id(tool.getId())
                        .versions(
                                Collections.singletonList(ToolVersionFingerprint.builder()
                                        .id(toolVersionManager.loadToolVersion(tool.getId(), TEST_VERSION).getId())
                                        .version(TEST_VERSION)
                                        .build())
                        ).build()
                )
        );
        AbstractDataStorage saved = storageManager.create(storageVO, false,
                false, false).getEntity();
        AbstractDataStorage loaded = storageManager.load(saved.getId());
        Assert.assertFalse(loaded.getToolsToMount().isEmpty());
        toolVersionManager.deleteToolVersion(tool.getId(), TEST_VERSION);
        loaded = storageManager.load(saved.getId());
        Assert.assertTrue(loaded.getToolsToMount().isEmpty());
    }

    private Tool createTool() {
        TestUtils.configureDockerClientMock(dockerClient, dockerClientFactory);

        final DockerRegistry registry = new DockerRegistry();
        registry.setPath(TEST_REPO);
        registry.setOwner(TEST_USER);
        registryDao.createDockerRegistry(registry);

        final ToolGroup firstGroup = new ToolGroup();
        firstGroup.setName(TOOL_GROUP);
        firstGroup.setRegistryId(registry.getId());
        toolGroupManager.create(firstGroup);

        Tool tool = new Tool();
        tool.setImage(TEST_IMAGE);
        tool.setRam(TEST_RAM);
        tool.setCpu(TEST_CPU);
        tool.setOwner(TEST_USER);
        tool.setToolGroup(TOOL_GROUP);
        tool.setToolGroupId(firstGroup.getId());
        tool.setRegistryId(registry.getId());
        tool = toolManager.create(tool, false);

        ToolVersion toolVersion = ToolVersion
                .builder()
                .digest(TEST_DIGEST)
                .size(TEST_SIZE)
                .version(TEST_VERSION)
                .modificationDate(TEST_LAST_MODIFIED_DATE)
                .toolId(tool.getId())
                .build();
        when(dockerClient.getVersionAttributes(any(DockerRegistry.class), anyString(), anyString()))
                .thenReturn(toolVersion);

        toolVersionManager.updateOrCreateToolVersion(
                tool.getId(), TEST_VERSION, tool.getImage(), dockerRegistry, dockerClient
        );

        return tool;
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
    public void existsDataStorageTest() throws Exception {
        DataStorageVO storageVO = ObjectCreatorUtils.constructDataStorageVO(NAME, DESCRIPTION, DataStorageType.S3,
                                                                            PATH, STS_DURATION, LTS_DURATION,
                                                                            WITHOUT_PARENT_ID, TEST_MOUNT_POINT,
                                                                            TEST_MOUNT_OPTIONS);
        AbstractDataStorage saved = storageManager.create(storageVO, false, false, false).getEntity();
        Assert.assertTrue(storageManager.exists(saved.getId()));
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void loadByIdsDataStorageTest() throws Exception {
        DataStorageVO storageVO = ObjectCreatorUtils.constructDataStorageVO(NAME, DESCRIPTION, DataStorageType.S3,
                PATH, STS_DURATION, LTS_DURATION, WITHOUT_PARENT_ID, TEST_MOUNT_POINT, TEST_MOUNT_OPTIONS
        );
        AbstractDataStorage saved = storageManager.create(storageVO, false, false, false).getEntity();
        storageVO.setName(storageVO.getName() + UUID.randomUUID());
        storageVO.setPath(storageVO.getPath() + UUID.randomUUID());
        AbstractDataStorage saved2 = storageManager.create(storageVO, false, false, false).getEntity();
        storageVO.setName(storageVO.getName() + UUID.randomUUID());
        storageVO.setPath(storageVO.getPath() + UUID.randomUUID());
        storageManager.create(storageVO, false, false, false).getEntity();

        List<AbstractDataStorage> loaded = storageManager.getDatastoragesByIds(
                Arrays.asList(saved.getId(), saved2.getId())
        );
        Assert.assertTrue(
                loaded.stream().anyMatch(ds -> ds.getId().equals(saved.getId()) || ds.getId().equals(saved2.getId()))
        );
        Assert.assertEquals(2, loaded.size());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void loadByPathsDataStorageTest() throws Exception {
        DataStorageVO storageVO = ObjectCreatorUtils.constructDataStorageVO(NAME, DESCRIPTION, DataStorageType.S3,
                PATH, STS_DURATION, LTS_DURATION, WITHOUT_PARENT_ID, TEST_MOUNT_POINT, TEST_MOUNT_OPTIONS
        );
        AbstractDataStorage saved = storageManager.create(storageVO, false, false, false).getEntity();
        storageVO.setName(storageVO.getName() + UUID.randomUUID());
        storageVO.setPath(storageVO.getPath() + UUID.randomUUID());
        AbstractDataStorage saved2 = storageManager.create(storageVO, false, false, false).getEntity();
        storageVO.setName(storageVO.getName() + UUID.randomUUID());
        storageVO.setPath(storageVO.getPath() + UUID.randomUUID());
        storageManager.create(storageVO, false, false, false).getEntity();

        List<AbstractDataStorage> loaded = storageManager.getDatastoragesByPaths(
                Arrays.asList(saved.getPath(), saved2.getPath())
        );
        Assert.assertTrue(
                loaded.stream().anyMatch(ds -> ds.getPath().equals(saved.getPath())
                        || ds.getPath().equals(saved2.getPath()))
        );
        Assert.assertEquals(2, loaded.size());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void requestDavMountByIdsDataStorageTest() throws Exception {
        DataStorageVO storageVO = ObjectCreatorUtils.constructDataStorageVO(NAME, DESCRIPTION, DataStorageType.S3,
                PATH, STS_DURATION, LTS_DURATION, WITHOUT_PARENT_ID, TEST_MOUNT_POINT, TEST_MOUNT_OPTIONS
        );
        AbstractDataStorage saved = storageManager.create(storageVO, false, false, false).getEntity();

        storageManager.requestDataStorageDavMount(saved.getId(), SECS_IN_HOUR);

        List<MetadataEntry> metadataEntries = metadataManager.searchMetadataEntriesByClassAndKeyValue(
                AclClass.DATA_STORAGE, DAV_MOUNT_TAG, null);
        Assert.assertFalse(metadataEntries.isEmpty());
        Assert.assertEquals(saved.getId(), metadataEntries.get(0).getEntity().getEntityId());
    }

    @Test(expected = IllegalStateException.class)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void failToRequestForSmallerTimeDavMountIfAlreadyExistsTest() throws Exception {
        DataStorageVO storageVO = ObjectCreatorUtils.constructDataStorageVO(NAME, DESCRIPTION, DataStorageType.S3,
                PATH, STS_DURATION, LTS_DURATION, WITHOUT_PARENT_ID, TEST_MOUNT_POINT, TEST_MOUNT_OPTIONS
        );
        AbstractDataStorage saved = storageManager.create(storageVO, false, false, false).getEntity();

        storageManager.requestDataStorageDavMount(saved.getId(), SECS_IN_HOUR);

        List<MetadataEntry> metadataEntries = metadataManager.searchMetadataEntriesByClassAndKeyValue(
                AclClass.DATA_STORAGE, DAV_MOUNT_TAG, null);
        Assert.assertEquals(saved.getId(), metadataEntries.get(0).getEntity().getEntityId());

        storageManager.requestDataStorageDavMount(saved.getId(),
                SECS_IN_MIN);
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void successToRequestForBiggerTimeDavMountIfAlreadyExistsTest() throws Exception {
        DataStorageVO storageVO = ObjectCreatorUtils.constructDataStorageVO(NAME, DESCRIPTION, DataStorageType.S3,
                PATH, STS_DURATION, LTS_DURATION, WITHOUT_PARENT_ID, TEST_MOUNT_POINT, TEST_MOUNT_OPTIONS
        );
        AbstractDataStorage saved = storageManager.create(storageVO, false, false, false).getEntity();

        storageManager.requestDataStorageDavMount(saved.getId(), SECS_IN_HOUR);

        List<MetadataEntry> metadataEntries = metadataManager.searchMetadataEntriesByClassAndKeyValue(
                AclClass.DATA_STORAGE, DAV_MOUNT_TAG, null);
        Assert.assertEquals(saved.getId(), metadataEntries.get(0).getEntity().getEntityId());
        long firstValue = Long.parseLong(metadataEntries.get(0).getData().get(DAV_MOUNT_TAG).getValue());

        storageManager.requestDataStorageDavMount(saved.getId(), 2 * SECS_IN_HOUR);
        metadataEntries = metadataManager.searchMetadataEntriesByClassAndKeyValue(
                AclClass.DATA_STORAGE, DAV_MOUNT_TAG, null);
        Assert.assertEquals(saved.getId(), metadataEntries.get(0).getEntity().getEntityId());
        long secondValue = Long.parseLong(metadataEntries.get(0).getData().get(DAV_MOUNT_TAG).getValue());

        Assert.assertTrue(secondValue > firstValue);
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void callOffRequestDavMountByIdsDataStorageTest() throws Exception {
        DataStorageVO storageVO = ObjectCreatorUtils.constructDataStorageVO(NAME, DESCRIPTION, DataStorageType.S3,
                PATH, STS_DURATION, LTS_DURATION, WITHOUT_PARENT_ID, TEST_MOUNT_POINT, TEST_MOUNT_OPTIONS
        );
        AbstractDataStorage saved = storageManager.create(storageVO, false, false, false).getEntity();

        storageManager.requestDataStorageDavMount(saved.getId(), SECS_IN_HOUR);

        List<MetadataEntry> metadataEntries = metadataManager.searchMetadataEntriesByClassAndKeyValue(
                AclClass.DATA_STORAGE, DAV_MOUNT_TAG, null);
        Assert.assertFalse(metadataEntries.isEmpty());

        storageManager.callOffDataStorageDavMount(saved.getId());

        metadataEntries = metadataManager.searchMetadataEntriesByClassAndKeyValue(
                AclClass.DATA_STORAGE, DAV_MOUNT_TAG, null);
        Assert.assertTrue(metadataEntries.isEmpty());
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

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
    public void testUpdateDataStorageMountStatus() {
        final DataStorageVO storageVO =
            ObjectCreatorUtils.constructDataStorageVO(NAME, DESCRIPTION, DataStorageType.NFS, PATH, WITHOUT_PARENT_ID,
                                                      TEST_MOUNT_POINT, TEST_MOUNT_OPTIONS);
        final AbstractDataStorage saved = storageManager.create(storageVO, false, false, false).getEntity();
        assertNfsMountStatus(saved, NFSStorageMountStatus.ACTIVE);
        assertNfsMountStatus(storageManager.load(saved.getId()), NFSStorageMountStatus.ACTIVE);
        final AbstractDataStorage updated = storageManager.updateMountStatus(saved, NFSStorageMountStatus.READ_ONLY);
        assertNfsMountStatus(updated, NFSStorageMountStatus.READ_ONLY);
        assertNfsMountStatus(storageManager.load(saved.getId()), NFSStorageMountStatus.READ_ONLY);
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
    public void testCreateDefaultUserStorage() {
        final Optional<AbstractDataStorage> defaultStorage = storageManager.createDefaultStorageForUser(NAME);
        Assert.assertTrue(defaultStorage.isPresent());
        final AbstractDataStorage defaultStorageContent = defaultStorage.get();
        final String expectedDefaultUserStorageName =
            TestUtils.DEFAULT_STORAGE_NAME_PATTERN.replace(TestUtils.TEMPLATE_REPLACE_MARK, NAME);
        Assert.assertEquals(expectedDefaultUserStorageName, defaultStorageContent.getName());
        Assert.assertEquals(expectedDefaultUserStorageName, defaultStorageContent.getPath());
        Assert.assertEquals(DataStorageType.S3, defaultStorageContent.getType());
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

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void testFailCreateDefaultUserStorageWhenSameNameExists() {
        final String expectedDefaultUserStorageName =
            TestUtils.DEFAULT_STORAGE_NAME_PATTERN.replace(TestUtils.TEMPLATE_REPLACE_MARK, NAME);
        DataStorageVO storageVO = ObjectCreatorUtils.constructDataStorageVO(expectedDefaultUserStorageName,
                                                                            DESCRIPTION, DataStorageType.S3,
                                                                            expectedDefaultUserStorageName,
                                                                            STS_DURATION, LTS_DURATION,
                                                                            WITHOUT_PARENT_ID, TEST_MOUNT_POINT,
                                                                            TEST_MOUNT_OPTIONS);
        storageManager.create(storageVO, false, false, false);
        Assert.assertFalse(storageManager.createDefaultStorageForUser(NAME).isPresent());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void testResolveSizeMasks() {
        final String firstStorageName = "storage1";
        final String secondStorageName = "storage2";
        final String thirdStorageName = "anotherStorage";

        final String mask1 = "mask1";
        final String mask2 = "mask2";
        final String mask3 = "mask3";
        final String mask4 = "mask4";
        final String mask5 = "mask5";

        final Map<String, Set<String>> sizeMasks = new HashMap<>();
        final String wildcardRegex = "*";
        final String firstAndSecondPrefixRegex = "storage*";
        sizeMasks.put(wildcardRegex, Sets.newHashSet(mask1, mask2));
        sizeMasks.put(firstStorageName, Sets.newHashSet(mask3, mask4));
        sizeMasks.put(firstAndSecondPrefixRegex, Sets.newHashSet(mask4, mask5));

        assertResolvedSizeMasks(sizeMasks, firstStorageName, mask1, mask2, mask3, mask4, mask5);
        assertResolvedSizeMasks(sizeMasks, secondStorageName, mask1, mask2, mask4, mask5);
        assertResolvedSizeMasks(sizeMasks, thirdStorageName, mask1, mask2);
    }

    private void assertResolvedSizeMasks(final Map<String, Set<String>> masksMapping, final String storageName,
                                         final String... expectedMasks) {
        final AbstractDataStorage storage = new NFSDataStorage();
        storage.setName(storageName);
        final int initialMappingSize = masksMapping.size();
        assertThat(storageManager.resolveSizeMasks(masksMapping, storage))
            .hasSize(expectedMasks.length)
            .containsOnly(expectedMasks);
        assertThat(masksMapping).hasSize(initialMappingSize);
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

    private void assertNfsMountStatus(final AbstractDataStorage storage, final NFSStorageMountStatus status) {
        Assert.assertEquals(DataStorageType.NFS, storage.getType());
        final NFSDataStorage nfsDataStorage = (NFSDataStorage) storage;
        Assert.assertEquals(status, nfsDataStorage.getMountStatus());
    }
}
