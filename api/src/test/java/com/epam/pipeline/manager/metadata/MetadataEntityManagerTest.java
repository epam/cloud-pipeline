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

package com.epam.pipeline.manager.metadata;

import com.epam.pipeline.AbstractSpringTest;
import com.epam.pipeline.entity.metadata.MetadataClass;
import com.epam.pipeline.entity.metadata.MetadataEntity;
import com.epam.pipeline.entity.pipeline.Folder;
import com.epam.pipeline.manager.ObjectCreatorUtils;
import com.epam.pipeline.manager.pipeline.FolderManager;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MetadataEntityManagerTest extends AbstractSpringTest {

    private static final String TEST_FOLDER_NAME = "FOLDER";
    private static final String SAMPLE_CLASS = "Sample";
    private static final String PAIR_CLASS = "Pair";
    private static final String ENTITY_NAME = "ENTITY_NAME";
    private static final String EXTERNAL_ID1 = "EXT_1";
    private static final String EXTERNAL_ID2 = "EXT_2";
    private static final int PAGE_SIZE = 10;

    @Autowired
    MetadataEntityManager entityManager;

    @Autowired
    FolderManager folderManager;

    private Folder folder;
    private MetadataClass sampleClass;
    private MetadataClass pairClass;

    @Before
    public void setUp() {
        folder = folderManager.create(ObjectCreatorUtils.createFolder(TEST_FOLDER_NAME, null));
        sampleClass = entityManager.createMetadataClass(SAMPLE_CLASS);
        pairClass = entityManager.createMetadataClass(PAIR_CLASS);
        //create sample
        entityManager.updateMetadataEntity(
                ObjectCreatorUtils.createMetadataEntityVo(sampleClass.getId(), folder.getId(),
                        ENTITY_NAME, EXTERNAL_ID1, Collections.emptyMap()));
        //create pair
        entityManager.updateMetadataEntity(ObjectCreatorUtils.createMetadataEntityVo(pairClass.getId(), folder.getId(),
                ENTITY_NAME, EXTERNAL_ID2, Collections.emptyMap()));
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void shouldDeleteAllMetadataInFolderWithoutClassProvided() {
        entityManager.deleteMetadataEntitiesInProject(folder.getId(), null);
        assertTrue(loadEntities(SAMPLE_CLASS, folder.getId()).isEmpty());
        assertTrue(loadEntities(PAIR_CLASS, folder.getId()).isEmpty());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void shouldDeleteOnlyClassMetadataInFolderWithClassProvided() {
        entityManager.deleteMetadataEntitiesInProject(folder.getId(), SAMPLE_CLASS);
        assertTrue(loadEntities(SAMPLE_CLASS, folder.getId()).isEmpty());
        assertEquals(1, loadEntities(PAIR_CLASS, folder.getId()).size());
    }

    private List<MetadataEntity> loadEntities(String sampleClass, Long folderId) {
        return entityManager.filterMetadata(
                ObjectCreatorUtils.createMetadataFilter(sampleClass, folderId, PAGE_SIZE, 1))
                .getElements();
    }
}