/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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
import com.epam.pipeline.entity.metadata.MetadataFilter;
import com.epam.pipeline.entity.metadata.MetadataFilterOperator;
import com.epam.pipeline.entity.metadata.PipeConfValue;
import com.epam.pipeline.entity.pipeline.Folder;
import com.epam.pipeline.manager.ObjectCreatorUtils;
import com.epam.pipeline.manager.pipeline.FolderManager;
import org.apache.commons.lang.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MetadataEntityManagerTest extends AbstractSpringTest {

    private static final String TEST_FOLDER_NAME = "FOLDER";
    private static final String SAMPLE_CLASS = "Sample";
    private static final String PAIR_CLASS = "Pair";
    private static final String ENTITY_NAME = "ENTITY_NAME";
    private static final String EXTERNAL_ID1 = "EXT_1";
    private static final String EXTERNAL_ID2 = "EXT_2";
    private static final String EXTERNAL_ID3 = "EXTERNAL_ID3";
    private static final String EXTERNAL_ID4 = "EXTERNAL_ID4";
    private static final String EXTERNAL_ID5 = "EXTERNAL_ID5";
    private static final String TEST_DATE = "testDate";
    private static final String NULL_EXTERNAL_ID = null;
    private static final String BLANK_EXTERNAL_ID = "\n";
    private static final List<String> PREDEFINED_EXTERNAL_IDS = Arrays.asList(EXTERNAL_ID1, EXTERNAL_ID2);
    private static final int PAGE_SIZE = 10;
    private static final Map<String, PipeConfValue> DATA = Collections.singletonMap(TEST_STRING,
            new PipeConfValue(TEST_STRING, "TEST_VALUE"));
    private static final Map<String, PipeConfValue> DATA_WITH_DATE_FIELD_1 = Collections.singletonMap(TEST_DATE,
            new PipeConfValue(TEST_DATE, "20220910"));
    private static final String DATE2 = "20221010";
    private static final Map<String, PipeConfValue> DATA_WITH_DATE_FIELD_2 = Collections.singletonMap(TEST_DATE,
            new PipeConfValue(TEST_DATE, DATE2));
    private static final Map<String, PipeConfValue> DATA_WITH_DATE_FIELD_3 = Collections.singletonMap(TEST_DATE,
            new PipeConfValue(TEST_DATE, "20221110"));
    private static final Map<String, PipeConfValue> DATA_WITH_NULL_FIELD = Collections.singletonMap(TEST_STRING,
            new PipeConfValue(TEST_STRING, null));
    private static final Map<String, PipeConfValue> DATA_WITH_EMPTY_ARRAY_FIELD = Collections.singletonMap(TEST_STRING,
            new PipeConfValue(TEST_STRING, "[]"));
    private static final Map<String, PipeConfValue> DATA_WITH_EMPTY_OBJECT_FIELD = Collections.singletonMap(TEST_STRING,
            new PipeConfValue(TEST_STRING, "{}"));
    private static final Map<String, PipeConfValue> DATA_WITH_BLANK_FIELD_VALUE = Collections.singletonMap(TEST_STRING,
            new PipeConfValue(TEST_STRING, " "));

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

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void shouldCreateMetadataEntitiesWithAutogeneratedExternalIdIfNotSpecified() {
        entityManager.updateMetadataEntity(
                ObjectCreatorUtils.createMetadataEntityVo(sampleClass.getId(), folder.getId(),
                        ENTITY_NAME, NULL_EXTERNAL_ID, Collections.emptyMap()));
        entityManager.updateMetadataEntity(
                ObjectCreatorUtils.createMetadataEntityVo(sampleClass.getId(), folder.getId(),
                        ENTITY_NAME, BLANK_EXTERNAL_ID, Collections.emptyMap()));

        final List<MetadataEntity> loadedSamples = loadEntities(SAMPLE_CLASS, folder.getId());
        final List<MetadataEntity> loadedSamplesWithGeneratedIds = loadedSamples.stream()
                .filter(it -> !PREDEFINED_EXTERNAL_IDS.contains(it.getExternalId()))
                .collect(Collectors.toList());
        assertEquals(3, loadedSamples.size());
        assertEquals(2, loadedSamplesWithGeneratedIds.size());
        for (final MetadataEntity loadedSample : loadedSamplesWithGeneratedIds) {
            assertTrue(StringUtils.isNotBlank(loadedSample.getExternalId()));
        }
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void shouldFilterMetadataEntities() {
        entityManager.updateMetadataEntity(ObjectCreatorUtils.createMetadataEntityVo(sampleClass.getId(),
                folder.getId(),
                ENTITY_NAME,
                EXTERNAL_ID3,
                DATA));

        final MetadataFilter filter = getMetadataFilter(TEST_STRING, Collections.singletonList("TEST_VALUE"), null);
        final List<MetadataEntity> loadedSamples = entityManager.filterMetadata(filter).getElements();
        assertEquals(1, loadedSamples.size());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void shouldFilterMetadataEntitiesByNoField() {
        entityManager.updateMetadataEntity(ObjectCreatorUtils.createMetadataEntityVo(sampleClass.getId(),
                folder.getId(),
                ENTITY_NAME,
                EXTERNAL_ID3,
                DATA));

        final MetadataFilter filter = getMetadataFilter("TEST1", null, null);
        final List<MetadataEntity> loadedSamples = entityManager.filterMetadata(filter).getElements();
        assertEquals(2, loadedSamples.size());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void shouldFilterMetadataEntitiesByEmptyField() {
        entityManager.updateMetadataEntity(ObjectCreatorUtils.createMetadataEntityVo(sampleClass.getId(),
                folder.getId(), ENTITY_NAME, EXTERNAL_ID4, DATA_WITH_NULL_FIELD));
        entityManager.updateMetadataEntity(ObjectCreatorUtils.createMetadataEntityVo(sampleClass.getId(),
                folder.getId(), ENTITY_NAME, EXTERNAL_ID5, DATA_WITH_EMPTY_ARRAY_FIELD));
        entityManager.updateMetadataEntity(ObjectCreatorUtils.createMetadataEntityVo(sampleClass.getId(),
                folder.getId(), ENTITY_NAME, "EXTERNAL_ID6", DATA_WITH_EMPTY_OBJECT_FIELD));
        entityManager.updateMetadataEntity(ObjectCreatorUtils.createMetadataEntityVo(sampleClass.getId(),
                folder.getId(), ENTITY_NAME, "EXTERNAL_ID7", DATA_WITH_BLANK_FIELD_VALUE));

        final MetadataFilter filter = getMetadataFilter(TEST_STRING, null, null);
        final List<MetadataEntity> loadedSamples = entityManager.filterMetadata(filter).getElements();
        assertEquals(5, loadedSamples.size());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void shouldFilterMetadataEntitiesGTOperator() {
        entityManager.updateMetadataEntity(ObjectCreatorUtils.createMetadataEntityVo(sampleClass.getId(),
                folder.getId(), ENTITY_NAME, EXTERNAL_ID3, DATA_WITH_DATE_FIELD_1));
        entityManager.updateMetadataEntity(ObjectCreatorUtils.createMetadataEntityVo(sampleClass.getId(),
                folder.getId(), ENTITY_NAME, EXTERNAL_ID4, DATA_WITH_DATE_FIELD_2));
        entityManager.updateMetadataEntity(ObjectCreatorUtils.createMetadataEntityVo(sampleClass.getId(),
                folder.getId(), ENTITY_NAME, EXTERNAL_ID5, DATA_WITH_DATE_FIELD_3));

        final MetadataFilter filter = getMetadataFilter(TEST_DATE, Collections.singletonList(DATE2),
                MetadataFilterOperator.GE);
        final List<MetadataEntity> loadedSamples = entityManager.filterMetadata(filter).getElements();
        assertEquals(2, loadedSamples.size());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void shouldFilterMetadataEntitiesLTOperator() {
        entityManager.updateMetadataEntity(ObjectCreatorUtils.createMetadataEntityVo(sampleClass.getId(),
                folder.getId(), ENTITY_NAME, EXTERNAL_ID3, DATA_WITH_DATE_FIELD_1));
        entityManager.updateMetadataEntity(ObjectCreatorUtils.createMetadataEntityVo(sampleClass.getId(),
                folder.getId(), ENTITY_NAME, EXTERNAL_ID4, DATA_WITH_DATE_FIELD_2));
        entityManager.updateMetadataEntity(ObjectCreatorUtils.createMetadataEntityVo(sampleClass.getId(),
                folder.getId(), ENTITY_NAME, EXTERNAL_ID5, DATA_WITH_DATE_FIELD_3));

        final MetadataFilter filter = getMetadataFilter(TEST_DATE, Collections.singletonList(DATE2),
                MetadataFilterOperator.LE);
        final List<MetadataEntity> loadedSamples = entityManager.filterMetadata(filter).getElements();
        assertEquals(2, loadedSamples.size());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void shouldFilterMetadataEntitiesEQOperator() {
        entityManager.updateMetadataEntity(ObjectCreatorUtils.createMetadataEntityVo(sampleClass.getId(),
                folder.getId(), ENTITY_NAME, EXTERNAL_ID3, DATA_WITH_DATE_FIELD_1));
        entityManager.updateMetadataEntity(ObjectCreatorUtils.createMetadataEntityVo(sampleClass.getId(),
                folder.getId(), ENTITY_NAME, EXTERNAL_ID4, DATA_WITH_DATE_FIELD_2));
        entityManager.updateMetadataEntity(ObjectCreatorUtils.createMetadataEntityVo(sampleClass.getId(),
                folder.getId(), ENTITY_NAME, EXTERNAL_ID5, DATA_WITH_DATE_FIELD_3));

        final MetadataFilter filter = getMetadataFilter(TEST_DATE, Collections.singletonList(DATE2),
                MetadataFilterOperator.EQ);
        final List<MetadataEntity> loadedSamples = entityManager.filterMetadata(filter).getElements();
        assertEquals(1, loadedSamples.size());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void shouldFilterMetadataEntitiesNoOperator() {
        entityManager.updateMetadataEntity(ObjectCreatorUtils.createMetadataEntityVo(sampleClass.getId(),
                folder.getId(), ENTITY_NAME, EXTERNAL_ID3, DATA_WITH_DATE_FIELD_1));
        entityManager.updateMetadataEntity(ObjectCreatorUtils.createMetadataEntityVo(sampleClass.getId(),
                folder.getId(), ENTITY_NAME, EXTERNAL_ID4, DATA_WITH_DATE_FIELD_2));
        entityManager.updateMetadataEntity(ObjectCreatorUtils.createMetadataEntityVo(sampleClass.getId(),
                folder.getId(), ENTITY_NAME, EXTERNAL_ID5, DATA_WITH_DATE_FIELD_3));

        final MetadataFilter filter = getMetadataFilter(TEST_DATE, Collections.singletonList(DATE2),
                null);
        final List<MetadataEntity> loadedSamples = entityManager.filterMetadata(filter).getElements();
        assertEquals(1, loadedSamples.size());
    }

    private MetadataFilter getMetadataFilter(final String key, final List<String> value,
                                             final MetadataFilterOperator operator) {
        final MetadataFilter filter = new MetadataFilter();
        filter.setFolderId(folder.getId());
        filter.setMetadataClass(SAMPLE_CLASS);
        filter.setPage(1);
        filter.setPageSize(PAGE_SIZE);
        final List<MetadataFilter.FilterQuery> filters = new ArrayList<>();
        final MetadataFilter.FilterQuery filterQuery = new MetadataFilter.FilterQuery(key, operator, value, false);
        filters.add(filterQuery);
        filter.setFilters(filters);
        return filter;
    }

    private List<MetadataEntity> loadEntities(String sampleClass, Long folderId) {
        return entityManager.filterMetadata(
                ObjectCreatorUtils.createMetadataFilter(sampleClass, folderId, PAGE_SIZE, 1))
                .getElements();
    }
}
