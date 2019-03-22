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
import com.epam.pipeline.dao.pipeline.FolderDao;
import com.epam.pipeline.entity.configuration.RunConfiguration;
import com.epam.pipeline.entity.datastorage.aws.S3bucketDataStorage;
import com.epam.pipeline.entity.metadata.FolderWithMetadata;
import com.epam.pipeline.entity.metadata.MetadataEntity;
import com.epam.pipeline.entity.metadata.PipeConfValue;
import com.epam.pipeline.entity.pipeline.Folder;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.manager.EntityManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.epam.pipeline.manager.pipeline.FolderManagerTest.initFolderWithMetadata;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;

public class FolderManagerGetProjectTest extends AbstractSpringTest {

    private static final String DATA_KEY_1 = "tag";
    private static final String DATA_TYPE_1 = "string";
    private static final String DATA_VALUE_1 = "OWNER";
    private static final String PROJECT_INDICATOR_VALUE = "project";
    private Map<String, PipeConfValue> data = new HashMap<>();
    private Map<String, PipeConfValue> dataWithIndicator = new HashMap<>();
    private FolderWithMetadata folder1 = new FolderWithMetadata();
    private FolderWithMetadata folder2 = new FolderWithMetadata();
    private FolderWithMetadata folder3 = new FolderWithMetadata();

    @Autowired
    private FolderManager folderManager;

    @MockBean
    private FolderDao folderDao;

    @MockBean
    private EntityManager entityManager;

    @Before
    public void setUp() {
        data.put(DATA_KEY_1, new PipeConfValue(DATA_TYPE_1, DATA_VALUE_1));
        dataWithIndicator.put(DATA_KEY_1, new PipeConfValue(DATA_TYPE_1, PROJECT_INDICATOR_VALUE));

        folder1 = initFolderWithMetadata(folder1, 1L, "folder1", null, data);
        folder2 = initFolderWithMetadata(folder2, 2L, "folder2", folder1.getId(), dataWithIndicator);
        folder3 = initFolderWithMetadata(folder3, 3L, "folder3", folder2.getId(), data);
        Mockito.when(folderDao.loadParentFolders(Matchers.any(Long.class)))
                .thenReturn(Stream.of(folder1, folder2, folder3).collect(Collectors.toList()));

        PreferenceManager preferenceManager = mock(PreferenceManager.class);
        ReflectionTestUtils.setField(folderManager, "preferenceManager", preferenceManager);
        Mockito.when(preferenceManager.getPreference(SystemPreferences.UI_PROJECT_INDICATOR))
                .thenReturn("tag=project,other=indicator");
    }

    @Test
    public void getProjectShouldWorkWithFolderAsInputEntity() {
        Mockito.when(entityManager.load(Matchers.any(AclClass.class), Matchers.any(Long.class)))
                .thenReturn(folder3);
        Folder actualFolder = folderManager.getProject(folder3.getId(), AclClass.FOLDER);
        assertFolders(folder2, actualFolder);
    }

    @Test
    public void getProjectShouldWorkWithoutParent() {
        Mockito.when(entityManager.load(Matchers.any(AclClass.class), Matchers.any(Long.class)))
                .thenReturn(folder1);
        FolderWithMetadata project = folderManager.getProject(folder1.getId(), AclClass.FOLDER);
        assertNull(project);
    }

    @Test
    public void getProjectShouldWorkWithPipelineAsInputEntity() {
        Pipeline pipeline = new Pipeline();
        pipeline.setId(1L);
        pipeline.setParent(folder3);

        Mockito.when(entityManager.load(Matchers.any(AclClass.class), Matchers.any(Long.class)))
                .thenReturn(pipeline);

        Folder actualFolder = folderManager.getProject(pipeline.getId(), AclClass.PIPELINE);
        assertFolders(folder2, actualFolder);
    }

    @Test
    public void getProjectShouldWorkWithConfigurationAsInputEntity() {
        RunConfiguration configuration = new RunConfiguration();
        configuration.setId(1L);
        configuration.setParent(folder3);

        Mockito.when(entityManager.load(Matchers.any(AclClass.class), Matchers.any(Long.class)))
                .thenReturn(configuration);

        Folder actualFolder = folderManager.getProject(configuration.getId(), AclClass.CONFIGURATION);
        assertFolders(folder2, actualFolder);
    }

    @Test
    public void getProjectShouldWorkWithDataStorageAsInputEntity() {
        S3bucketDataStorage dataStorage = new S3bucketDataStorage(1L, "dataStorage", "path_to_bucket");
        dataStorage.setParent(folder3);

        Mockito.when(entityManager.load(Matchers.any(AclClass.class), Matchers.any(Long.class)))
                .thenReturn(dataStorage);

        Folder actualFolder = folderManager.getProject(dataStorage.getId(), AclClass.DATA_STORAGE);
        assertFolders(folder2, actualFolder);
    }

    @Test
    public void getProjectShouldWorkWithMetadataAsInputEntity() {
        MetadataEntity metadataEntity = new MetadataEntity();
        metadataEntity.setId(1L);
        metadataEntity.setParent(folder3);

        Mockito.when(entityManager.load(Matchers.any(AclClass.class), Matchers.any(Long.class)))
                .thenReturn(metadataEntity);

        Folder actualFolder = folderManager.getProject(metadataEntity.getId(), AclClass.METADATA_ENTITY);
        assertFolders(folder2, actualFolder);
    }

    @Test(expected = IllegalArgumentException.class)
    public void getProjectShouldFailWithUnsupportedInputEntity() {
        folderManager.getProject(1L, AclClass.TOOL);
    }

    @Test
    public void getProjectShouldFailIfProjectNotFound() {
        Mockito.when(entityManager.load(Matchers.any(AclClass.class), Matchers.any(Long.class)))
                .thenReturn(folder3);
        folder2 = initFolderWithMetadata(folder2, 2L, "folder2", folder1.getId(), data);
        FolderWithMetadata project = folderManager.getProject(folder3.getId(), AclClass.FOLDER);
        assertNull(project);
    }

    private void assertFolders(Folder expectedFolder, Folder actualFolder) {
        assertEquals(expectedFolder.getId(), actualFolder.getId());
        assertEquals(expectedFolder.getParentId(), actualFolder.getParentId());
        assertEquals(expectedFolder.getName(), actualFolder.getName());
        assertEquals(expectedFolder.getAclClass(), actualFolder.getAclClass());
    }

}
