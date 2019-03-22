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

package com.epam.pipeline.manager.event;

import com.epam.pipeline.AbstractSpringTest;
import com.epam.pipeline.dao.event.EventDao;
import com.epam.pipeline.entity.configuration.RunConfiguration;
import com.epam.pipeline.entity.datastorage.aws.S3bucketDataStorage;
import com.epam.pipeline.entity.issue.Issue;
import com.epam.pipeline.entity.metadata.MetadataClass;
import com.epam.pipeline.entity.metadata.MetadataEntity;
import com.epam.pipeline.entity.pipeline.Folder;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.manager.EntityManager;
import com.epam.pipeline.manager.datastorage.DataStorageManager;
import com.epam.pipeline.manager.issue.IssueManager;
import com.epam.pipeline.manager.metadata.MetadataEntityManager;
import com.epam.pipeline.manager.pipeline.FolderCrudManager;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.security.SecuredEntityManager;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyListOf;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.internal.verification.VerificationModeFactory.times;

@SuppressWarnings("PMD.TooManyStaticImports")
public class FolderEventServiceTest extends AbstractSpringTest {

    private static final String METADATA_CLASS_NAME = "Sample";

    @Autowired
    private FolderEventService folderEventService;

    @MockBean
    private EventDao eventDao;
    @MockBean
    private IssueManager issueManager;
    @MockBean
    private FolderCrudManager folderManager;
    @MockBean
    private PipelineRunManager pipelineRunManager;
    @MockBean
    private DataStorageManager dataStorageManager;
    @MockBean
    private EntityManager entityManager;
    @MockBean
    private MetadataEntityManager metadataEntityManager;

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void shouldAddFolderEvent() {
        doNothing().when(entityManager).setManagers(anyListOf(SecuredEntityManager.class));

        Folder folder2 = new Folder(2L);
        Pipeline pipeline = new Pipeline(1L);
        S3bucketDataStorage dataStorage = new S3bucketDataStorage(1L, "", "");
        RunConfiguration runConfiguration = new RunConfiguration();
        runConfiguration.setId(1L);
        MetadataEntity metadataEntity = new MetadataEntity();
        metadataEntity.setClassEntity(new MetadataClass(1L, METADATA_CLASS_NAME));
        metadataEntity.setId(1L);

        Folder folder1 = new Folder(1L);
        folder1.setChildFolders(Collections.singletonList(folder2));
        folder1.setPipelines(Collections.singletonList(pipeline));
        folder1.setStorages(Collections.singletonList(dataStorage));
        folder1.setConfigurations(Collections.singletonList(runConfiguration));
        folder1.setMetadata(Collections.singletonMap(METADATA_CLASS_NAME, 1));

        when(issueManager.loadIssuesForEntity(any()))
                .thenReturn(Arrays.asList(Issue.builder().id(1L).build(), Issue.builder().id(2L).build()));
        when(pipelineRunManager.loadAllRunsByPipeline(anyLong()))
                .thenReturn(Arrays.asList(new PipelineRun(1L, ""), new PipelineRun(2L, "")));
        when(dataStorageManager.load(1L)).thenReturn(dataStorage);
        when(folderManager.load(1L)).thenReturn(folder1);
        when(folderManager.load(2L)).thenReturn(folder2);
        when(metadataEntityManager.loadMetadataEntityByClassNameAndFolderId(anyLong(), anyString()))
                .thenReturn(Collections.singletonList(metadataEntity));
        doNothing().when(eventDao).insertUpdateEvent(anyString(), anyLong());

        folderEventService.updateEventsWithChildrenAndIssues(1L);

        verify(eventDao).insertUpdateEvent("folder", 1L);
        verify(eventDao).insertUpdateEvent("folder", 2L);
        verify(eventDao).insertUpdateEvent("pipeline", 1L);
        verify(eventDao).insertUpdateEvent("run", 1L);
        verify(eventDao).insertUpdateEvent("run", 2L);
        verify(eventDao).insertUpdateEvent("S3", 1L);
        verify(eventDao).insertUpdateEvent("configuration", 1L);
        verify(eventDao).insertUpdateEvent("metadata_entity", 1L);
        verify(eventDao, times(6)).insertUpdateEvent("issue", 1L);
        verify(eventDao, times(6)).insertUpdateEvent("issue", 2L);
        verifyNoMoreInteractions(eventDao);
    }
}
