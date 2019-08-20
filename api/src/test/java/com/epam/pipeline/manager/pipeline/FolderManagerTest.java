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

package com.epam.pipeline.manager.pipeline;

import com.epam.pipeline.AbstractSpringTest;
import com.epam.pipeline.controller.vo.CloudRegionVO;
import com.epam.pipeline.controller.vo.DataStorageVO;
import com.epam.pipeline.controller.vo.EntityVO;
import com.epam.pipeline.controller.vo.MetadataVO;
import com.epam.pipeline.controller.vo.PipelineVO;
import com.epam.pipeline.controller.vo.configuration.RunConfigurationVO;
import com.epam.pipeline.controller.vo.metadata.MetadataEntityVO;
import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import com.epam.pipeline.entity.configuration.RunConfiguration;
import com.epam.pipeline.entity.configuration.RunConfigurationEntry;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.datastorage.StoragePolicy;
import com.epam.pipeline.entity.metadata.FolderWithMetadata;
import com.epam.pipeline.entity.metadata.MetadataClass;
import com.epam.pipeline.entity.metadata.MetadataEntity;
import com.epam.pipeline.entity.metadata.MetadataEntry;
import com.epam.pipeline.entity.metadata.PipeConfValue;
import com.epam.pipeline.entity.pipeline.Folder;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.manager.MockS3Helper;
import com.epam.pipeline.manager.configuration.RunConfigurationManager;
import com.epam.pipeline.manager.datastorage.DataStorageManager;
import com.epam.pipeline.manager.datastorage.providers.aws.s3.S3StorageProvider;
import com.epam.pipeline.manager.git.GitManager;
import com.epam.pipeline.manager.metadata.MetadataEntityManager;
import com.epam.pipeline.manager.metadata.MetadataManager;
import com.epam.pipeline.manager.region.CloudRegionManager;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.epam.pipeline.manager.ObjectCreatorUtils.constructDataStorageVO;
import static com.epam.pipeline.manager.ObjectCreatorUtils.constructPipelineVO;
import static com.epam.pipeline.manager.ObjectCreatorUtils.createConfigEntry;
import static com.epam.pipeline.manager.ObjectCreatorUtils.createRunConfigurationVO;
import static com.epam.pipeline.utils.PasswordGenerator.generateRandomString;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@Transactional
@SuppressWarnings({"PMD.TooManyStaticImports"})
public class FolderManagerTest extends AbstractSpringTest {

    private static final String TEST_NAME = "Test";
    private static final String TEST_DESCRIPTION = "description";
    private static final String TEST_REPO = "http://gitlab.com/test/test.git";
    private static final String TEST_REPO_SSH = "git@gitlab.com:test/test.git";
    private static final String TEST_PATH = "path";
    private static final int STS_DURATION = 1;
    private static final int LTS_DURATION = 1;
    private static final int BACKUP_DURATION = 1;
    private static final String TEST_MOUNT_POINT = "testMountPoint";
    private static final String TEST_MOUNT_OPTIONS = "testMountOptions";

    private static final String TEST_USER = "Test";
    private static final String DATA_KEY_1 = "tag";
    private static final String DATA_TYPE_1 = "string";
    private static final String DATA_VALUE_1 = "OWNER";
    private static final String PROJECT_INDICATOR_VALUE = "project";

    private static final String FOLDER_TO_CLONE = "clone";
    private static final String CHILD_FOLDER_TO_CLONE = "child";
    private static final String TEST_NAME_1 = "other";
    private static final String TEST_CLONE_PREFIX = "test-pref";
    private static final String PROJECT_INDICATOR_TYPE = "type";
    private static final String DEFAULT_PARAM_TYPE = "string";

    private Folder folder;
    private Folder subFolder;

    @Autowired
    private FolderManager folderManager;

    @Autowired
    private PipelineManager pipelineManager;

    @Mock
    private GitManager gitManagerMock;

    @Autowired
    private DataStorageManager dataStorageManager;

    @SpyBean
    private S3StorageProvider storageProviderManager;

    @Autowired
    private MetadataManager metadataManager;

    @Autowired
    private MetadataEntityManager metadataEntityManager;

    @Autowired
    private RunConfigurationManager runConfigurationManager;

    @Autowired
    private CloudRegionManager cloudRegionManager;

    @Value("${storage.clone.name.suffix:}")
    private String storageSuffix;

    private AbstractCloudRegion cloudRegion;

    @Before
    public void setUp() throws Exception {
        cloudRegion = cloudRegionManager.create(CloudRegionVO.builder().name("US")
                .regionCode("us-east-1")
                .isDefault(true)
                .provider(CloudProvider.AWS)
                .build());
        doReturn(new MockS3Helper()).when(storageProviderManager).getS3Helper(any());

        folder = new Folder();
        folder.setName(TEST_NAME);
        subFolder = new Folder();
        subFolder.setName(TEST_NAME_1);
        MockitoAnnotations.initMocks(this);
        when(gitManagerMock.getPipelineRevisions(any(Pipeline.class),
                any(Long.class))).thenReturn(Collections.emptyList());
        pipelineManager.setGitManager(gitManagerMock);
    }

    private static void assertDataStorages(AbstractDataStorage expected, AbstractDataStorage actual) {
        assertEquals(expected.getStoragePolicy(), actual.getStoragePolicy());
        assertEquals(expected.getAclClass(), actual.getAclClass());
        assertEquals(expected.getDelimiter(), actual.getDelimiter());
        assertEquals(expected.getDescription(), actual.getDescription());
        assertEquals(expected.getType(), actual.getType());
        assertEquals(expected.getOwner(), actual.getOwner());
    }

    private static void assertFolderMetadata(MetadataEntry expected, MetadataEntry actual) {
        assertEquals(expected.getEntity().getEntityClass(), actual.getEntity().getEntityClass());
        assertEquals(expected.getData(), actual.getData());
    }

    private static void assertMetadataEntity(MetadataEntity expected, MetadataEntity actual) {
        assertEquals(expected.getData(), actual.getData());
        assertEquals(expected.getClassEntity(), actual.getClassEntity());
        assertEquals(expected.getAclClass(), actual.getAclClass());
        assertEquals(expected.getExternalId(), actual.getExternalId());
    }

    @Test(expected = IllegalArgumentException.class)
    public void deleteFolderWithPipeline() throws Exception {
        folderManager.create(folder);
        PipelineVO pipeline = constructPipelineVO(TEST_NAME, TEST_REPO, TEST_REPO_SSH, folder.getId());
        pipelineManager.create(pipeline);
        folderManager.delete(folder.getId());
    }

    @Test(expected = IllegalArgumentException.class)
    public void deleteFolderWithStorage() throws Exception {
        folderManager.create(folder);
        DataStorageVO storageVO = constructDataStorageVO(
                TEST_NAME, TEST_DESCRIPTION, DataStorageType.S3, TEST_PATH,
                STS_DURATION, LTS_DURATION, folder.getId(), TEST_MOUNT_POINT, TEST_MOUNT_OPTIONS
        );
        dataStorageManager.create(storageVO, false, false, false);
        folderManager.delete(folder.getId());
    }

    @Test
    public void deleteFolderAfterMovingPipelineAndStorage() throws Exception {
        folderManager.create(folder);
        PipelineVO pipelineVO = constructPipelineVO(TEST_NAME, TEST_REPO, TEST_REPO_SSH, folder.getId());
        DataStorageVO storageVO = constructDataStorageVO(
                TEST_NAME, TEST_DESCRIPTION, DataStorageType.S3, TEST_PATH,
                STS_DURATION, LTS_DURATION, folder.getId(), TEST_MOUNT_POINT, TEST_MOUNT_OPTIONS
        );
        Pipeline pipeline = pipelineManager.create(pipelineVO);
        AbstractDataStorage storage = dataStorageManager.create(storageVO, false, false, false).getEntity();

        pipelineVO.setId(pipeline.getId());
        pipelineVO.setParentFolderId(null);
        pipelineManager.update(pipelineVO);

        storageVO.setParentFolderId(null);
        storageVO.setId(storage.getId());
        dataStorageManager.update(storageVO);

        folderManager.delete(folder.getId());
    }

    @Test
    public void deleteFolderWithChildrenForce() throws Exception {
        folderManager.create(subFolder);
        folder.setParent(subFolder);
        folderManager.create(folder);
        PipelineVO pipeline1 = constructPipelineVO(TEST_NAME, TEST_REPO, TEST_REPO_SSH, folder.getId());
        pipelineManager.create(pipeline1);
        generateDataStorage(folder.getId());

        PipelineVO pipeline2 = constructPipelineVO(TEST_NAME, TEST_REPO, TEST_REPO_SSH, subFolder.getId());
        pipelineManager.create(pipeline2);
        generateDataStorage(subFolder.getId());

        folderManager.deleteForce(folder.getId());
    }

    private static void assertRunConfiguration(RunConfiguration expected, RunConfiguration actual) {
        assertEquals(expected.getAclClass(), actual.getAclClass());
        assertEquals(expected.getDescription(), actual.getDescription());
        assertEquals(expected.getName(), actual.getName());
        assertEquals(expected.getOwner(), actual.getOwner());
        assertEquals(expected.getEntries().size(), actual.getEntries().size());
        assertEntry((RunConfigurationEntry) expected.getEntries().get(0),
                (RunConfigurationEntry) actual.getEntries().get(0));
    }

    private static void assertEntry(RunConfigurationEntry expected, RunConfigurationEntry actual) {
        assertEquals(expected.getName(), actual.getName());
        assertEquals(expected.getDefaultConfiguration(), actual.getDefaultConfiguration());
        assertPipeConfiguration(expected.getConfiguration(), actual.getConfiguration());
    }

    private static void assertPipeConfiguration(PipelineConfiguration expected, PipelineConfiguration actual) {
        assertEquals(expected.getCmdTemplate(), actual.getCmdTemplate());
        assertEquals(expected.getDockerImage(), actual.getDockerImage());
        assertEquals(expected.getInstanceType(), actual.getInstanceType());
        assertEquals(expected.getInstanceDisk(), actual.getInstanceDisk());
    }

    static FolderWithMetadata initFolderWithMetadata(FolderWithMetadata folder, Long id, String name, Long parentId,
                                                     Map<String, PipeConfValue> metadata) {
        folder.setName(name);
        folder.setId(id);
        folder.setParentId(parentId);
        folder.setData(metadata);
        folder.setOwner(TEST_USER);
        return folder;
    }

    @Test
    public void createFolderTest() throws Exception {
        folderManager.create(folder);
        Folder loaded = folderManager.load(folder.getId());
        assertEquals(folder.getId(), loaded.getId());
        assertEquals(folder.getName(), loaded.getName());
        assertNull(folder.getParentId());
    }

    @Test
    public void loadFolderWithPipelineAndStorage() throws Exception {
        folderManager.create(folder);
        PipelineVO pipelineVO = constructPipelineVO(TEST_NAME, TEST_REPO, TEST_REPO_SSH, folder.getId());
        DataStorageVO storageVO = constructDataStorageVO(
                TEST_NAME, TEST_DESCRIPTION, DataStorageType.S3, TEST_PATH,
                STS_DURATION, LTS_DURATION, folder.getId(), TEST_MOUNT_POINT, TEST_MOUNT_OPTIONS
        );
        Pipeline pipeline = pipelineManager.create(pipelineVO);
        AbstractDataStorage storage = dataStorageManager.create(storageVO, false, false, false).getEntity();

        Folder loaded = folderManager.load(folder.getId());
        Pipeline loadedPipe = loaded.getPipelines().get(0);
        assertNotNull(loadedPipe);
        assertEquals(pipeline.getId(), loadedPipe.getId());
        assertEquals(pipeline.getName(), loadedPipe.getName());
        assertEquals(pipeline.getParentFolderId(), loadedPipe.getParentFolderId());

        AbstractDataStorage loadedStorage = loaded.getStorages().get(0);
        assertNotNull(loadedStorage);
        assertEquals(storage.getId(), loadedStorage.getId());
        assertEquals(storage.getName(), loadedStorage.getName());
        assertEquals(storage.getParentFolderId(), loadedStorage.getParentFolderId());
    }

    @Test(expected = IllegalArgumentException.class)
    public void deleteFolder() throws Exception {
        folderManager.create(folder);
        Folder loaded = folderManager.load(folder.getId());
        assertNotNull(loaded);
        folderManager.delete(folder.getId());
        folderManager.load(folder.getId());
    }


    @Test
    public void testListProjectsShouldReturnFolderWithIndicatorSet() {
        folderManager.create(folder);
        MetadataVO metadataVO = new MetadataVO();
        metadataVO.setEntity(new EntityVO(folder.getId(), AclClass.FOLDER));
        metadataVO.setData(Collections.singletonMap(PROJECT_INDICATOR_TYPE,
                new PipeConfValue(DEFAULT_PARAM_TYPE, PROJECT_INDICATOR_VALUE)));
        metadataManager.updateMetadataItem(metadataVO);

        List<Folder> folders = folderManager.loadAllProjects().getChildFolders();
        assertThat(folders.size(), is(1));
        assertThat(folders.get(0).getId(), is(folder.getId()));
    }

    @Test
    public void testListProjectsShouldNotReturnFolderWithoutIndicatorSet() {
        folderManager.create(folder);
        List<Folder> folders = folderManager.loadAllProjects().getChildFolders();
        assertThat(folders.isEmpty(), is(true));
    }

    @Test
    public void testGetProjectFolderFromProjectTree() {
        Map<String, PipeConfValue> data = new HashMap<>();
        data.put(DATA_KEY_1, new PipeConfValue(DATA_TYPE_1, DATA_VALUE_1));
        Map<String, PipeConfValue> dataWithIndicator = new HashMap<>();
        dataWithIndicator.put(DATA_KEY_1, new PipeConfValue(DATA_TYPE_1, PROJECT_INDICATOR_VALUE));
        FolderWithMetadata folder = initFolderWithMetadata(new FolderWithMetadata(), 1L, "folder", null, data);
        FolderWithMetadata folder1 = initFolderWithMetadata(new FolderWithMetadata(), 2L, "folder1", folder.getId(),
                dataWithIndicator);
        FolderWithMetadata folder2 = initFolderWithMetadata(new FolderWithMetadata(), 3L, "folder2", folder1.getId(),
                data);

        Map<Long, FolderWithMetadata> folders = new HashMap<>();
        folders.put(folder.getId(), folder);
        folders.put(folder1.getId(), folder1);
        folders.put(folder2.getId(), folder2);

        Set<Pair<String, String>> indicators = new HashSet<>();
        indicators.add(new ImmutablePair<>(DATA_KEY_1, PROJECT_INDICATOR_VALUE));

        // case: single indicator
        Folder actualFolder = folderManager.getProjectFolder(folders, folder2.getId(), indicators);
        assertEquals(folder1.getId(), actualFolder.getId());
        assertEquals(folder1.getName(), actualFolder.getName());
        assertEquals(folder1.getParentId(), actualFolder.getParentId());
        assertEquals(folder1.getOwner(), actualFolder.getOwner());

        // case: several indicators
        indicators.add(new ImmutablePair<>(PROJECT_INDICATOR_VALUE, DATA_KEY_1));
        dataWithIndicator = new HashMap<>();
        dataWithIndicator.put(PROJECT_INDICATOR_VALUE, new PipeConfValue(DATA_TYPE_1, DATA_KEY_1));
        folder2.setData(dataWithIndicator);
        folders.put(folder2.getId(), folder2);
        actualFolder = folderManager.getProjectFolder(folders, folder2.getId(), indicators);
        assertEquals(folder2.getId(), actualFolder.getId());
        assertEquals(folder2.getName(), actualFolder.getName());
        assertEquals(folder2.getParentId(), actualFolder.getParentId());
        assertEquals(folder2.getOwner(), actualFolder.getOwner());

        //case: no matches (null parent)
        folder1.setData(data);
        folder2.setData(data);
        folders.put(folder1.getId(), folder1);
        folders.put(folder2.getId(), folder2);
        actualFolder = folderManager.getProjectFolder(folders, folder2.getId(), indicators);
        assertNull(actualFolder);

        // case: no matches (no null parent)
        folders.remove(folder.getId());
        actualFolder = folderManager.getProjectFolder(folders, folder2.getId(), indicators);
        assertNull(actualFolder);
    }

    @Test
    public void shouldCloneFolderWithDataStorages() {
        Folder sourceFolder = new Folder();
        sourceFolder.setName(FOLDER_TO_CLONE);
        folderManager.create(sourceFolder);
        DataStorageVO dataStorageVO = constructDataStorageVO(
                TEST_NAME, TEST_DESCRIPTION, DataStorageType.S3, TEST_PATH,
                STS_DURATION, LTS_DURATION, sourceFolder.getId(), TEST_MOUNT_POINT, TEST_MOUNT_OPTIONS
        );
        StoragePolicy storagePolicy = dataStorageVO.getStoragePolicy();
        storagePolicy.setBackupDuration(BACKUP_DURATION);
        dataStorageVO.setStoragePolicy(storagePolicy);
        dataStorageVO.setRegionId(cloudRegion.getId());
        AbstractDataStorage expectedDataStorage = dataStorageManager.create(dataStorageVO, true, true, false)
                .getEntity();

        Folder childSourceFolder = new Folder();
        childSourceFolder.setName(CHILD_FOLDER_TO_CLONE);
        childSourceFolder.setParentId(sourceFolder.getId());
        folderManager.create(childSourceFolder);
        dataStorageVO.setParentFolderId(childSourceFolder.getId());
        dataStorageVO.setName(TEST_NAME_1);
        dataStorageVO.setPath(TEST_NAME_1);
        dataStorageManager.create(dataStorageVO, true, true, false);

        Folder destinationFolder = new Folder();
        destinationFolder.setName(TEST_NAME);
        folderManager.create(destinationFolder);

        folderManager.cloneFolder(sourceFolder.getId(), destinationFolder.getId(), TEST_CLONE_PREFIX);

        destinationFolder = folderManager.loadByNameOrId(TEST_NAME);
        destinationFolder = folderManager.load(destinationFolder.getId());
        Folder clonedFolder = destinationFolder.getChildFolders().get(0);
        AbstractDataStorage clonedDataStorage = clonedFolder.getStorages().get(0);
        clonedDataStorage = dataStorageManager.load(clonedDataStorage.getId());
        assertTrue(clonedDataStorage.getName().startsWith(TEST_CLONE_PREFIX + storageSuffix));
        assertTrue(clonedDataStorage.getPath().startsWith((TEST_CLONE_PREFIX + storageSuffix).toLowerCase()));
        assertDataStorages(expectedDataStorage, clonedDataStorage);

        Folder clonedChildFolder = clonedFolder.getChildFolders().get(0);
        clonedDataStorage = clonedChildFolder.getStorages().get(0);
        clonedDataStorage = dataStorageManager.load(clonedDataStorage.getId());
        assertTrue(clonedDataStorage.getName().startsWith(TEST_CLONE_PREFIX + storageSuffix));
        assertTrue(clonedDataStorage.getPath().startsWith((TEST_CLONE_PREFIX + storageSuffix).toLowerCase()));
        assertDataStorages(expectedDataStorage, clonedDataStorage);
    }

    @Test
    public void shouldCloneFolderWithFolderMetadata() {
        Folder sourceFolder = new Folder();
        sourceFolder.setName(FOLDER_TO_CLONE);
        folderManager.create(sourceFolder);
        Map<String, PipeConfValue> metadata = new HashMap<>();
        metadata.put(DATA_KEY_1, new PipeConfValue(DATA_TYPE_1, DATA_VALUE_1));
        MetadataVO metadataVO = new MetadataVO();
        metadataVO.setEntity(new EntityVO(sourceFolder.getId(), AclClass.FOLDER));
        metadataVO.setData(metadata);
        MetadataEntry expectedMetadata = metadataManager.updateMetadataItem(metadataVO);

        Folder childSourceFolder = new Folder();
        childSourceFolder.setName(CHILD_FOLDER_TO_CLONE);
        childSourceFolder.setParentId(sourceFolder.getId());
        folderManager.create(childSourceFolder);
        metadataVO.setEntity(new EntityVO(childSourceFolder.getId(), AclClass.FOLDER));
        metadataManager.updateMetadataItem(metadataVO);

        Folder destinationFolder = new Folder();
        destinationFolder.setName(TEST_NAME);
        folderManager.create(destinationFolder);

        folderManager.cloneFolder(sourceFolder.getId(), destinationFolder.getId(), TEST_CLONE_PREFIX);

        destinationFolder = folderManager.load(folderManager.loadByNameOrId(TEST_NAME).getId());
        Folder clonedFolder = destinationFolder.getChildFolders().get(0);
        MetadataEntry clonedFolderMetadata = metadataManager.loadMetadataItem(clonedFolder.getId(), AclClass.FOLDER);
        assertEquals(clonedFolder.getId(), clonedFolderMetadata.getEntity().getEntityId());
        assertFolderMetadata(expectedMetadata, clonedFolderMetadata);

        Folder clonedChildFolder = clonedFolder.getChildFolders().get(0);
        clonedFolderMetadata = metadataManager.loadMetadataItem(clonedChildFolder.getId(), AclClass.FOLDER);
        assertEquals(clonedChildFolder.getId(), clonedFolderMetadata.getEntity().getEntityId());
        assertFolderMetadata(expectedMetadata, clonedFolderMetadata);
    }

    @Test
    public void shouldCloneFolderWithMetadataEntities() {
        Folder sourceFolder = new Folder();
        sourceFolder.setName(FOLDER_TO_CLONE);
        folderManager.create(sourceFolder);
        Map<String, PipeConfValue> metadata = new HashMap<>();
        metadata.put(DATA_KEY_1, new PipeConfValue(DATA_TYPE_1, DATA_VALUE_1));
        MetadataClass metadataClass = metadataEntityManager.createMetadataClass(TEST_NAME);
        MetadataEntityVO metadataEntity = new MetadataEntityVO();
        metadataEntity.setParentId(sourceFolder.getId());
        metadataEntity.setClassName(metadataClass.getName());
        metadataEntity.setClassId(metadataClass.getId());
        metadataEntity.setData(metadata);
        metadataEntity.setEntityName(TEST_NAME);
        MetadataEntity expectedMetadata = metadataEntityManager.updateMetadataEntity(metadataEntity);

        Folder childSourceFolder = new Folder();
        childSourceFolder.setName(CHILD_FOLDER_TO_CLONE);
        childSourceFolder.setParentId(sourceFolder.getId());
        folderManager.create(childSourceFolder);
        metadataEntity.setParentId(childSourceFolder.getId());
        metadataEntityManager.updateMetadataEntity(metadataEntity);

        Folder destinationFolder = new Folder();
        destinationFolder.setName(TEST_NAME);
        folderManager.create(destinationFolder);

        folderManager.cloneFolder(sourceFolder.getId(), destinationFolder.getId(), TEST_CLONE_PREFIX);

        destinationFolder = folderManager.loadByNameOrId(TEST_NAME);
        destinationFolder = folderManager.load(destinationFolder.getId());
        Folder clonedFolder = destinationFolder.getChildFolders().get(0);
        MetadataEntity clonedMetadata = metadataEntityManager
                .loadMetadataEntityByClassNameAndFolderId(clonedFolder.getId(), metadataClass.getName()).get(0);
        assertMetadataEntity(expectedMetadata, clonedMetadata);

        Folder clonedChildFolder = clonedFolder.getChildFolders().get(0);
        clonedMetadata = metadataEntityManager
                .loadMetadataEntityByClassNameAndFolderId(clonedChildFolder.getId(), metadataClass.getName()).get(0);
        assertMetadataEntity(expectedMetadata, clonedMetadata);
    }

    @Test
    public void shouldCloneWithRunConfiguration() {
        Folder sourceFolder = new Folder();
        sourceFolder.setName(FOLDER_TO_CLONE);
        folderManager.create(sourceFolder);
        PipelineConfiguration pipelineConfiguration = new PipelineConfiguration();
        pipelineConfiguration.setCmdTemplate(TEST_NAME);
        RunConfigurationEntry entry = createConfigEntry(TEST_NAME, true, pipelineConfiguration);
        RunConfigurationVO runConfigurationVO = createRunConfigurationVO(
                TEST_NAME, TEST_DESCRIPTION, sourceFolder.getId(), Collections.singletonList(entry));
        RunConfiguration expectedRunConfiguration = runConfigurationManager.create(runConfigurationVO);

        Folder childSourceFolder = new Folder();
        childSourceFolder.setName(CHILD_FOLDER_TO_CLONE);
        childSourceFolder.setParentId(sourceFolder.getId());
        folderManager.create(childSourceFolder);
        runConfigurationVO.setParentId(childSourceFolder.getId());
        runConfigurationManager.create(runConfigurationVO);

        Folder destinationFolder = new Folder();
        destinationFolder.setName(TEST_NAME);
        folderManager.create(destinationFolder);

        folderManager.cloneFolder(sourceFolder.getId(), destinationFolder.getId(), TEST_CLONE_PREFIX);

        destinationFolder = folderManager.loadByNameOrId(TEST_NAME);
        destinationFolder = folderManager.load(destinationFolder.getId());
        Folder clonedFolder = destinationFolder.getChildFolders().get(0);
        RunConfiguration clonedRunConfiguration = runConfigurationManager.loadAll().stream()
                .filter(conf -> Objects.equals(conf.getParent().getId(), clonedFolder.getId()))
                .collect(Collectors.toList())
                .get(0);
        assertRunConfiguration(expectedRunConfiguration, clonedRunConfiguration);

        Folder clonedChildFolder = clonedFolder.getChildFolders().get(0);
        clonedRunConfiguration = runConfigurationManager.loadAll().stream()
                .filter(conf -> Objects.equals(conf.getParent().getId(), clonedChildFolder.getId()))
                .collect(Collectors.toList())
                .get(0);
        assertRunConfiguration(expectedRunConfiguration, clonedRunConfiguration);
    }

    @Test
    public void shouldCloneFolderTree() {
        Folder sourceFolder = new Folder();
        sourceFolder.setName(FOLDER_TO_CLONE);
        folderManager.create(sourceFolder);

        Folder childSourceFolder = new Folder();
        childSourceFolder.setName(CHILD_FOLDER_TO_CLONE);
        childSourceFolder.setParentId(sourceFolder.getId());
        folderManager.create(childSourceFolder);

        Folder destinationFolder = new Folder();
        destinationFolder.setName(TEST_NAME);
        folderManager.create(destinationFolder);

        Folder clonedFolder = folderManager.cloneFolder(sourceFolder.getId(), destinationFolder.getId(),
                TEST_CLONE_PREFIX);
        assertTrue(CollectionUtils.isEmpty(clonedFolder.getChildFolders()));

        clonedFolder = folderManager.load(clonedFolder.getId());
        assertEquals(destinationFolder.getId(), clonedFolder.getParentId());
        assertNotNull(clonedFolder);
        assertTrue(clonedFolder.getName().startsWith(TEST_CLONE_PREFIX));

        Folder clonedChildFolder = clonedFolder.getChildFolders().get(0);
        assertNotNull(clonedChildFolder);
        assertEquals(CHILD_FOLDER_TO_CLONE, clonedChildFolder.getName());
    }

    @Test
    public void testDataStorageCountInFolder() {
        Folder sourceFolder = generateFolder(null);

        Folder childFolderWithStorage1 = generateFolder(sourceFolder.getId());
        generateDataStorage(childFolderWithStorage1.getId());

        Folder childFolderWithoutStorage = generateFolder(childFolderWithStorage1.getId());

        Folder childFolderWithStorage2 = generateFolder(sourceFolder.getId());
        generateDataStorage(childFolderWithStorage2.getId());

        Folder childFolderWithStorage3 = generateFolder(childFolderWithStorage2.getId());
        generateDataStorage(childFolderWithStorage3.getId());
        generateDataStorage(childFolderWithStorage3.getId());

        assertEquals(4, folderManager.countDataStorages(folderManager.load(sourceFolder.getId())));
        assertEquals(0, folderManager.countDataStorages(folderManager.load(childFolderWithoutStorage.getId())));
        assertEquals(1, folderManager.countDataStorages(folderManager.load(childFolderWithStorage1.getId())));
        assertEquals(3, folderManager.countDataStorages(folderManager.load(childFolderWithStorage2.getId())));
        assertEquals(2, folderManager.countDataStorages(folderManager.load(childFolderWithStorage3.getId())));
    }

    @Test(expected = IllegalStateException.class)
    public void cloneFolderWithExistentFolderNameShouldFail() {
        Folder sourceFolder = new Folder();
        sourceFolder.setName(FOLDER_TO_CLONE);
        folderManager.create(sourceFolder);

        Folder childSourceFolder = new Folder();
        childSourceFolder.setName(CHILD_FOLDER_TO_CLONE);
        childSourceFolder.setParentId(sourceFolder.getId());
        folderManager.create(childSourceFolder);

        folderManager.cloneFolder(childSourceFolder.getId(), sourceFolder.getId(), CHILD_FOLDER_TO_CLONE);
    }

    private void generateDataStorage(Long parentId) {
        DataStorageVO dataStorageVO = constructDataStorageVO(
                generateRandomString(10), TEST_DESCRIPTION, DataStorageType.S3, generateRandomString(10),
                STS_DURATION, LTS_DURATION, parentId, TEST_MOUNT_POINT, TEST_MOUNT_OPTIONS
        );
        dataStorageManager.create(dataStorageVO, true, true, false);
    }

    private Folder generateFolder(Long parentId) {
        Folder folder = new Folder();
        folder.setName(TEST_NAME + generateRandomString(10));
        folder.setParentId(parentId);
        folderManager.create(folder);
        return folder;
    }
}
