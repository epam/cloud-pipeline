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

package com.epam.pipeline.dao.pipeline;

import com.epam.pipeline.AbstractSpringTest;
import com.epam.pipeline.controller.vo.DataStorageVO;
import com.epam.pipeline.controller.vo.EntityVO;
import com.epam.pipeline.dao.configuration.RunConfigurationDao;
import com.epam.pipeline.dao.datastorage.DataStorageDao;
import com.epam.pipeline.dao.metadata.MetadataClassDao;
import com.epam.pipeline.dao.metadata.MetadataDao;
import com.epam.pipeline.dao.metadata.MetadataEntityDao;
import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import com.epam.pipeline.entity.configuration.RunConfiguration;
import com.epam.pipeline.entity.configuration.RunConfigurationEntry;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.AbstractDataStorageFactory;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.metadata.FolderWithMetadata;
import com.epam.pipeline.entity.metadata.MetadataClass;
import com.epam.pipeline.entity.metadata.MetadataEntity;
import com.epam.pipeline.entity.metadata.MetadataEntry;
import com.epam.pipeline.entity.metadata.PipeConfValue;
import com.epam.pipeline.entity.pipeline.Folder;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.manager.ObjectCreatorUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class FolderDaoTest extends AbstractSpringTest {

    private static final String TEST_USER = "Test";
    private static final String TEST_NAME = "Test";
    private static final String TEST_REPO = "///";
    private static final String TEST_REPO_SSH = "git@test";
    private static final String TEST_DATASTORAGE = "test-datastorage";
    private static final String S3_TEST_DATASTORAGE_PATH = "s3://datastorage/data";
    private static final String TEST_ENTITY_NAME_1 = "test_entity";
    private static final String TEST_MOUNT_POINT = "testMountPoint";
    private static final String TEST_MOUNT_OPTIONS = "testMountOptions";
    private static final String DATA_KEY_1 = "tag";
    private static final String DATA_TYPE_1 = "string";
    private static final String DATA_VALUE_1 = "OWNER";
    private static final String CLASS_NAME_1 = "Sample";

    @Autowired
    private FolderDao folderDao;

    @Autowired
    private PipelineDao pipelineDao;

    @Autowired
    private DataStorageDao dataStorageDao;

    @Autowired
    private RunConfigurationDao configurationDao;

    @Autowired
    private MetadataEntityDao metadataEntityDao;

    @Autowired
    private MetadataClassDao metadataClassDao;

    @Autowired
    private MetadataDao metadataDao;

    private AbstractDataStorageFactory storageFactory = AbstractDataStorageFactory.getDefaultDataStorageFactory();

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void testCRUD() {
        Folder folder = getFolder();

        //load
        Folder loaded = folderDao.loadFolder(folder.getId());
        assertEquals(folder.getId(), loaded.getId());
        assertEquals(folder.getName(), loaded.getName());
        assertNull(folder.getParentId());

        //update
        Folder parent = new Folder();
        parent.setName("Parent");
        parent.setOwner(TEST_USER);
        folderDao.createFolder(parent);

        //loadAll, 2 root projects
        List<Folder> result = folderDao.loadAllFolders();
        assertEquals(2, result.size());

        folder.setParentId(parent.getId());
        folder.setName("Test2");
        folderDao.updateFolder(folder);
        Folder loaded2 = folderDao.loadFolder(folder.getId());
        assertEquals(folder.getId(), loaded2.getId());
        assertEquals(folder.getName(), loaded2.getName());
        assertEquals(folder.getParentId(), loaded2.getParentId());

        Folder loadedParent = folderDao.loadFolder(parent.getId());
        assertEquals(parent.getId(), loadedParent.getId());
        assertEquals(1, loadedParent.getChildFolders().size());
        assertEquals(folder.getId(), loadedParent.getChildFolders().get(0).getId());

        //loadAll
        List<Folder> result2 = folderDao.loadAllFolders();
        assertEquals(1, result2.size());

        //add pipeline
        Pipeline pipeline = ObjectCreatorUtils.constructPipeline(TEST_NAME, TEST_REPO, TEST_REPO_SSH, parent.getId());
        pipeline.setOwner(TEST_USER);
        pipelineDao.createPipeline(pipeline);
        Folder loadedParent2 = folderDao.loadFolder(parent.getId());
        assertEquals(pipeline.getId(), loadedParent2.getPipelines().get(0).getId());
        assertEquals(0, loadedParent2.getChildFolders().get(0).getPipelines().size());

        //delete
        folderDao.deleteFolder(folder.getId());
        assertNull(folderDao.loadFolder(folder.getId()));

        //loadAll
        List<Folder> result3 = folderDao.loadAllFolders();
        assertEquals(1, result3.size());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void testCreateAndLoadFolderWithStorages() {
        Folder folder = getFolder();

        //add datastorage
        AbstractDataStorage storage = addStorage(folder);

        Folder loaded = folderDao.loadFolder(folder.getId());
        assertEquals(folder.getId(), loaded.getId());
        assertEquals(folder.getName(), loaded.getName());
        assertNull(folder.getParentId());
        checkStorageIsPresent(storage, loaded);
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void testCreateAndLoadFolderWithConfigurations() {
        Folder folder = getFolder();
        AbstractDataStorage storage = addStorage(folder);
        RunConfiguration configuration = addConfiguration(folder);

        Folder loaded = folderDao.loadFolder(folder.getId());
        assertEquals(folder.getId(), loaded.getId());
        assertEquals(folder.getName(), loaded.getName());
        assertNull(folder.getParentId());

        checkStorageIsPresent(storage, loaded);

        checkConfigIsPresent(configuration, loaded);
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void testCreateAndLoadFolderWithMetadata() {
        Folder folder = getFolder();
        //create
        folderDao.createFolder(folder);

        Map<String, Integer> metadata = new HashMap<>();
        metadata.put(CLASS_NAME_1, 1);
        folder.setMetadata(metadata);

        //add metadata
        MetadataEntity metadataEntity = new MetadataEntity();
        metadataEntity.setName(TEST_ENTITY_NAME_1);
        MetadataClass metadataClass = createMetadataClass();
        metadataEntity.setClassEntity(metadataClass);
        metadataEntity.setParent(folder);
        Map<String, PipeConfValue> data = new HashMap<>();
        data.put(DATA_KEY_1, new PipeConfValue(DATA_TYPE_1, DATA_VALUE_1));
        metadataEntity.setData(data);
        metadataEntityDao.createMetadataEntity(metadataEntity);

        Folder loaded = folderDao.loadFolder(folder.getId());
        assertEquals(folder.getId(), loaded.getId());
        assertEquals(folder.getName(), loaded.getName());
        assertEquals(folder.getMetadata(), loaded.getMetadata());
        assertNull(folder.getParentId());

        Map<String, Integer> loadedMetadata = loaded.getMetadata();
        assertTrue(!loadedMetadata.isEmpty() && loadedMetadata.size() == 1);
        assertEquals(metadataEntity.getClassEntity().getName(), loadedMetadata.keySet().toArray()[0]);
        assertEquals(1, loadedMetadata.get(CLASS_NAME_1).intValue());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void testLoadParentFolders() {
        Folder folder = getFolder();
        Folder folder1 = createFolderWithParent(folder, "folder1");
        Folder folder2 = createFolderWithParent(folder1, "folder2");
        // folder that exists but mustn't be included in actualFolders
        createFolderWithParent(folder1, "folder3");
        List<Folder> expectedFolders = Stream.of(folder, folder1, folder2).collect(Collectors.toList());
        List<FolderWithMetadata> expectedFoldersWithMetadata = createObjectMetadata(
                expectedFolders);
        List<FolderWithMetadata> actualFoldersWithMetadata = folderDao.loadParentFolders(folder2.getId());
        assertTrue(CollectionUtils.isEqualCollection(actualFoldersWithMetadata, expectedFoldersWithMetadata));

        List<Folder> actualFolders = folderDao.loadFolderWithParents(folder2.getId());
        assertTrue(CollectionUtils.isEqualCollection(actualFolders, expectedFolders));
    }

    private void checkConfigIsPresent(RunConfiguration configuration, Folder loaded) {
        List<RunConfiguration> loadedConfigs = loaded.getConfigurations();
        assertEquals(1, loadedConfigs.size());
        assertEquals(configuration.getId(), loadedConfigs.get(0).getId());
        assertEquals(loaded.getId(), loadedConfigs.get(0).getParent().getId());
    }

    private void checkStorageIsPresent(AbstractDataStorage storage, Folder loaded) {
        List<AbstractDataStorage> loadedStorages = loaded.getStorages();
        assertEquals(1, loadedStorages.size());
        assertEquals(storage.getId(), loadedStorages.get(0).getId());
        assertEquals(loaded.getId(), loadedStorages.get(0).getParentFolderId());
        assertEquals(storage.getMountPoint(), loadedStorages.get(0).getMountPoint());
        assertEquals(storage.getMountOptions(), loadedStorages.get(0).getMountOptions());
    }

    private RunConfiguration addConfiguration(Folder folder) {
        RunConfigurationEntry entry =
                ObjectCreatorUtils.createConfigEntry(TEST_NAME, true, new PipelineConfiguration());

        RunConfiguration configuration = ObjectCreatorUtils
                .createConfiguration(TEST_NAME, TEST_NAME, folder.getId(),
                        TEST_USER, Collections.singletonList(entry));
        return configurationDao.create(configuration);
    }

    private AbstractDataStorage addStorage(Folder folder) {
        //add datastorage
        DataStorageVO storageVO = ObjectCreatorUtils.constructDataStorageVO(
                TEST_DATASTORAGE, TEST_DATASTORAGE, DataStorageType.S3,
                S3_TEST_DATASTORAGE_PATH, 1, 1, folder.getId(), TEST_MOUNT_POINT,
                TEST_MOUNT_OPTIONS);
        AbstractDataStorage storage = storageFactory.convertToDataStorage(storageVO, CloudProvider.AWS);
        storage.setOwner(TEST_USER);
        dataStorageDao.createDataStorage(storage);
        return storage;
    }

    public Folder getFolder() {
        Folder folder = new Folder();
        folder.setName(TEST_NAME);
        folder.setOwner(TEST_USER);
        //create
        folderDao.createFolder(folder);
        return folder;
    }

    private Folder createFolderWithParent(Folder parent, String name) {
        Folder folder = new Folder();
        folder.setName(name);
        folder.setOwner(TEST_USER);
        folder.setParentId(parent.getId());
        //create
        folderDao.createFolder(folder);
        return folder;
    }

    private MetadataClass createMetadataClass() {
        MetadataClass metadataClass = new MetadataClass();
        metadataClass.setName(CLASS_NAME_1);
        metadataClassDao.createMetadataClass(metadataClass);
        return metadataClass;
    }

    private List<FolderWithMetadata> createObjectMetadata(List<Folder> folders) {
        Map<String, PipeConfValue> data = new HashMap<>();
        data.put(DATA_KEY_1, new PipeConfValue(DATA_TYPE_1, DATA_VALUE_1));
        MetadataEntry metadataToSave = new MetadataEntry();
        List<FolderWithMetadata> foldersWithMetadata = new ArrayList<>();
        folders.forEach(folder -> {
            metadataToSave.setEntity(new EntityVO(folder.getId(), AclClass.FOLDER));
            metadataToSave.setData(data);
            metadataDao.registerMetadataItem(metadataToSave);
            FolderWithMetadata folderWithMetadata = convertToFolderWithMetadata(folder);
            folderWithMetadata.setData(data);
            foldersWithMetadata.add(folderWithMetadata);
        });
        return foldersWithMetadata;
    }

    private FolderWithMetadata convertToFolderWithMetadata(Folder folder) {
        FolderWithMetadata folderWithMetadata = new FolderWithMetadata();
        folderWithMetadata.setId(folder.getId());
        folderWithMetadata.setParentId(folder.getParentId());
        folderWithMetadata.setName(folder.getName());
        folderWithMetadata.setOwner(folder.getOwner());
        return folderWithMetadata;
    }
}
