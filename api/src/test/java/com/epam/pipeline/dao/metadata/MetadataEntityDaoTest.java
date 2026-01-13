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

package com.epam.pipeline.dao.metadata;

import com.epam.pipeline.dao.pipeline.FolderDao;
import com.epam.pipeline.entity.BaseEntity;
import com.epam.pipeline.entity.metadata.Facet;
import com.epam.pipeline.entity.metadata.LogicalSearchOperator;
import com.epam.pipeline.entity.metadata.MetadataClass;
import com.epam.pipeline.entity.metadata.MetadataClassDescription;
import com.epam.pipeline.entity.metadata.MetadataEntity;
import com.epam.pipeline.entity.metadata.MetadataField;
import com.epam.pipeline.entity.metadata.MetadataFilter;
import com.epam.pipeline.entity.metadata.PipeConfValue;
import com.epam.pipeline.entity.pipeline.Folder;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.ObjectCreatorUtils;
import com.epam.pipeline.manager.metadata.parser.EntityTypeField;
import com.epam.pipeline.test.jdbc.AbstractJdbcTest;
import org.junit.jupiter.api.Test;
import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class MetadataEntityDaoTest extends AbstractJdbcTest {

    private static final String TEST_USER = "Test";
    private static final String TEST_NAME = "Test";
    private static final String TEST_ENTITY_NAME_1 = "test_entity";
    private static final String TEST_ENTITY_NAME_2 = "test_entity_2";
    private static final String TEST_ENTITY_NAME_3 = "test_entity_3";
    private static final String CLASS_NAME_1 = "Sample";
    private static final String CLASS_NAME_2 = "Participant";
    private static final String CLASS_NAME_3 = "Batch";
    private static final String EXTERNAL_ID_1 = "externalId1";
    private static final String EXTERNAL_ID_2 = "externalId2";
    private static final String EXTERNAL_ID_3 = "externalId3";
    private static final String DATA_KEY_1 = "tag";
    private static final String DATA_TYPE_1 = "string";
    private static final String DATA_VALUE_1 = "OWNER";
    private static final String DATA_KEY_2 = "role";
    private static final String DATA_TYPE_2 = "Participant:ID";
    private static final String DATA_VALUE_2 = "ADMIN";

    @Autowired
    private FolderDao folderDao;

    @Autowired
    private MetadataEntityDao metadataEntityDao;

    @Autowired
    private MetadataClassDao metadataClassDao;

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void testDeleteMetadataInFolder() {
        MetadataClass metadataClass = createMetadataClass(CLASS_NAME_1);
        Map<String, PipeConfValue> data = new HashMap<>();

        Folder folder1 = createFolder();
        Folder folder2 = createFolder();

        // 2 entities in folder1
        createMetadataEntity(folder1, metadataClass, EXTERNAL_ID_1, data);
        createMetadataEntity(folder1, metadataClass, EXTERNAL_ID_2, data);

        //1 entity in folder2
        createMetadataEntity(folder2, metadataClass, EXTERNAL_ID_2, data);

        List<MetadataEntity> folder1List = metadataEntityDao
                .loadMetadataEntityByClassNameAndFolderId(folder1.getId(), CLASS_NAME_1);
        assertEquals(2, folder1List.size());

        List<MetadataEntity> folder2List = metadataEntityDao
                .loadMetadataEntityByClassNameAndFolderId(folder2.getId(), CLASS_NAME_1);
        assertEquals(1, folder2List.size());

        metadataEntityDao.deleteMetadataFromFolder(folder1.getId());

        folder1List = metadataEntityDao
                .loadMetadataEntityByClassNameAndFolderId(folder1.getId(), CLASS_NAME_1);
        assertEquals(0, folder1List.size());
        folder2List = metadataEntityDao
                .loadMetadataEntityByClassNameAndFolderId(folder2.getId(), CLASS_NAME_1);
        assertEquals(1, folder2List.size());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void testCRUDMetadataEntity() {
        Folder folder = createFolder();

        // metadata entity and metadata class creation
        MetadataClass metadataClass = createMetadataClass(CLASS_NAME_1);

        Map<String, PipeConfValue> data = new LinkedHashMap<>();
        data.put(DATA_KEY_1, new PipeConfValue(DATA_TYPE_1, DATA_VALUE_1));

        MetadataEntity metadataEntity = createMetadataEntity(folder, metadataClass, EXTERNAL_ID_1, data);

        MetadataEntity result = metadataEntityDao.loadAllMetadataEntities().get(0);
        compareMetadata(metadataEntity, result, true);

        // update metadata entity
        metadataEntity.setName(TEST_ENTITY_NAME_2);
        data.put(DATA_KEY_2, new PipeConfValue(DATA_TYPE_2, DATA_VALUE_2));
        metadataEntity.setData(data);
        metadataEntityDao.updateMetadataEntity(metadataEntity);
        MetadataEntity updateResult = metadataEntityDao.loadMetadataEntityById(result.getId());
        assertEquals(metadataEntity.getName(), updateResult.getName());
        assertEquals(metadataEntity.getData(), updateResult.getData());

        // update metadata entity data key
        data.put(DATA_KEY_2, new PipeConfValue(DATA_TYPE_1, DATA_VALUE_2));
        metadataEntity.setData(data);
        metadataEntityDao.updateMetadataEntityDataKey(metadataEntity, DATA_KEY_2, DATA_VALUE_2, DATA_TYPE_1);
        MetadataEntity updateKeyResult = metadataEntityDao
                .loadMetadataEntityByClassNameAndFolderId(metadataEntity.getParent().getId(),
                        metadataEntity.getClassEntity().getName()).get(0);
        assertEquals(metadataEntity.getData(), updateKeyResult.getData());

        // load metadata entity from root
        MetadataEntity metadataEntityInRoot = new MetadataEntity();
        metadataEntityInRoot.setName(TEST_ENTITY_NAME_3);
        metadataEntityInRoot.setClassEntity(metadataClass);
        metadataEntityInRoot.setExternalId(EXTERNAL_ID_1);
        metadataEntityInRoot.setData(data);
        metadataEntityInRoot.setParent(new Folder());
        metadataEntityDao.createMetadataEntity(metadataEntityInRoot);

        MetadataEntity rootResult = metadataEntityDao.loadRootMetadataEntities().get(0);
        assertEquals(metadataEntityInRoot.getId(), rootResult.getId());
        assertEquals(metadataEntityInRoot.getClassEntity().getName(), rootResult.getClassEntity().getName());
        assertEquals(metadataEntityInRoot.getData(), rootResult.getData());

        // load by external ids
        MetadataEntity entity2 = createMetadataEntity(folder, metadataClass, EXTERNAL_ID_2, data);
        Set<MetadataEntity> existing = metadataEntityDao
                .loadExisting(folder.getId(), metadataClass.getName(),
                        new HashSet<>(Arrays.asList(EXTERNAL_ID_1, EXTERNAL_ID_2)));
        assertEquals(new HashSet<>(Arrays.asList(metadataEntity, entity2)), existing);

        //load by inner ids
        Set<MetadataEntity> entitiesByIds =
                metadataEntityDao.loadByIds(Collections.singleton(entity2.getId()));
        assertEquals(Collections.singleton(entity2), entitiesByIds);

        //load with folders
        rootResult = metadataEntityDao.loadMetadataEntityWithParents(metadataEntityInRoot.getId());
        assertEquals(metadataEntityInRoot.getId(), rootResult.getId());
        assertEquals(metadataEntityInRoot.getData(), rootResult.getData());

        // delete key from metadata entity
        data.clear();
        data.put(DATA_KEY_1, new PipeConfValue(DATA_TYPE_1, DATA_VALUE_1));
        metadataEntity.setData(data);
        metadataEntityDao.deleteMetadataItemKey(metadataEntity.getId(), DATA_KEY_2);
        MetadataEntity deletedKeyResult = metadataEntityDao.loadMetadataEntityById(metadataEntity.getId());
        assertEquals(metadataEntity.getData(), deletedKeyResult.getData());

        // delete metadata entity
        metadataEntityDao.deleteMetadataEntity(metadataEntity.getId());
        MetadataEntity deletedEntity = metadataEntityDao.loadMetadataEntityById(metadataEntity.getId());
        assertNull(deletedEntity);
    }


    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void testLoadReferenceQuery() {
        Folder folder = createFolder();
        MetadataClass sampleClass = createMetadataClass(CLASS_NAME_1);
        MetadataClass participantClass = createMetadataClass(CLASS_NAME_2);
        MetadataClass batchClass = createMetadataClass(CLASS_NAME_3);

        Map<String, PipeConfValue> data = new HashMap<>();
        data.put(DATA_KEY_1, new PipeConfValue(DATA_TYPE_1, DATA_VALUE_1));
        MetadataEntity batch1 = createMetadataEntity(folder, batchClass, EXTERNAL_ID_1, data);

        //create a second batch to check that it isn't returned in query results
        createMetadataEntity(folder, batchClass, EXTERNAL_ID_2, data);

        data = new HashMap<>();
        data.put(DATA_KEY_1, new PipeConfValue(DATA_TYPE_1, DATA_VALUE_1));
        data.put("Batch", new PipeConfValue("Batch:ID", batch1.getExternalId()));
        MetadataEntity participant1 =
                createMetadataEntity(folder, participantClass, EXTERNAL_ID_1, data);

        MetadataEntity participant2 =
                createMetadataEntity(folder, participantClass, EXTERNAL_ID_2, data);

        data = new HashMap<>();
        data.put(DATA_KEY_1, new PipeConfValue(DATA_TYPE_1, DATA_VALUE_1));
        data.put("Participants", new PipeConfValue("Array[Participant]",
                String.format("[\"%s\",\"%s\"]", participant1.getExternalId(), participant2.getExternalId())));
        MetadataEntity sample = createMetadataEntity(folder, sampleClass, EXTERNAL_ID_1, data);

        List<MetadataEntity> links = metadataEntityDao
                .loadAllReferences(Collections.singletonList(sample.getId()), folder.getId());
        assertEquals(new HashSet<>(Arrays.asList(batch1, participant1, participant2, sample)),
                new HashSet<>(links));
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void testLoadReferenceQueryWithParticipantsOnly() {
        Folder folder = createFolder();
        MetadataClass participantClass = createMetadataClass(CLASS_NAME_2);
        MetadataClass sampleClass = createMetadataClass(CLASS_NAME_1);

        Map<String, PipeConfValue> data = new HashMap<>();
        data.put(DATA_KEY_1, new PipeConfValue(DATA_TYPE_1, DATA_VALUE_1));
        MetadataEntity participant1 =
                createMetadataEntity(folder, participantClass, EXTERNAL_ID_1, data);
        MetadataEntity participant2 =
                createMetadataEntity(folder, participantClass, EXTERNAL_ID_2, data);

        data = new HashMap<>();
        data.put(DATA_KEY_1, new PipeConfValue(DATA_TYPE_1, DATA_VALUE_1));
        data.put("Participants", new PipeConfValue("Array[Participant]",
                String.format("[\"%s\",\"%s\"]", participant1.getExternalId(), participant2.getExternalId())));
        createMetadataEntity(folder, sampleClass, EXTERNAL_ID_1, data);

        List<MetadataEntity> links = metadataEntityDao.loadAllReferences(
                Arrays.asList(participant1.getId(), participant2.getId()), folder.getId());
        assertEquals(new HashSet<>(Arrays.asList(participant1, participant2)), new HashSet<>(links));
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void testBatchQueries() {

        Folder folder = createFolder();
        MetadataClass metadataClass = createMetadataClass(CLASS_NAME_1);
        Map<String, PipeConfValue> data = new HashMap<>();
        data.put(DATA_KEY_1, new PipeConfValue(DATA_TYPE_1, DATA_VALUE_1));

        MetadataEntity entity1 = ObjectCreatorUtils.createMetadataEntity(folder, metadataClass, TEST_ENTITY_NAME_1,
                EXTERNAL_ID_1, data, DateUtils.now());
        MetadataEntity entity2 = ObjectCreatorUtils.createMetadataEntity(folder, metadataClass, TEST_ENTITY_NAME_1,
                EXTERNAL_ID_2, data, DateUtils.now());
        Collection<MetadataEntity> result =
                metadataEntityDao.batchInsert(Arrays.asList(entity1, entity2));
        assertTrue(result.stream().allMatch(e -> e.getId() != null));

        data.put(DATA_KEY_2, new PipeConfValue(DATA_TYPE_2, DATA_VALUE_2));
        entity1.setData(data);
        entity2.setData(data);
        metadataEntityDao.batchUpdate(Arrays.asList(entity1, entity2));

        List<MetadataEntity> loaded = metadataEntityDao
                .loadMetadataEntityByClassNameAndFolderId(folder.getId(), metadataClass.getName());
        assertTrue(loaded.stream().allMatch(e -> e.getData().equals(data)));

        Set<Long> entitiesToDelete = Stream.of(entity1.getId(), entity2.getId()).collect(Collectors.toSet());
        metadataEntityDao.deleteMetadataEntities(entitiesToDelete);
        assertEquals(0, metadataEntityDao.loadAllMetadataEntities().stream()
                .filter(entity -> entitiesToDelete.contains(entity.getId())).count());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void testSearchWithUnderscore() {
        MetadataClass metadataClass = createMetadataClass(CLASS_NAME_1);
        Map<String, PipeConfValue> data = new HashMap<>();
        data.put(DATA_KEY_1, new PipeConfValue(DATA_TYPE_1, DATA_VALUE_1 + "1"));
        Folder folder = createFolder();
        // sample with "OWNER1" data, shouldn't be returned in search results
        createMetadataEntity(folder, metadataClass, EXTERNAL_ID_1, data);

        Map<String, PipeConfValue> dataUnderscore = new HashMap<>();
        dataUnderscore.put(DATA_KEY_1, new PipeConfValue(DATA_TYPE_1, DATA_VALUE_1 + "_1"));
        // sample with "OWNER_1" data, should  be returned in search results
        MetadataEntity sampleUnderscore = createMetadataEntity(folder, metadataClass, EXTERNAL_ID_2, dataUnderscore);

        MetadataFilter filter = createFilter(folder.getId(), metadataClass.getName(),
                Collections.singletonList(DATA_VALUE_1 + "_"), Collections.emptyList(), null, false);
        checkFilterRequest(filter, Collections.singletonList(sampleUnderscore));
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void testSearch() {
        MetadataClass metadataClass1 = createMetadataClass(CLASS_NAME_1);
        MetadataClass metadataClass2 = createMetadataClass(CLASS_NAME_2);

        Map<String, PipeConfValue> data1 = new HashMap<>();
        data1.put(DATA_KEY_1, new PipeConfValue(DATA_TYPE_1, DATA_VALUE_1));
        Folder folder1 = createFolder();
        Folder folder2 = createFolder();
        MetadataEntity folder1Sample1 = createMetadataEntity(folder1, metadataClass1, EXTERNAL_ID_1, data1);

        Map<String, PipeConfValue> data2 = new HashMap<>();
        data2.put(DATA_KEY_1, new PipeConfValue(DATA_TYPE_1, DATA_VALUE_2));
        data2.put(DATA_KEY_2, new PipeConfValue(DATA_TYPE_1, DATA_VALUE_1));

        MetadataEntity folder1Sample2 = createMetadataEntity(folder1, metadataClass1, EXTERNAL_ID_2, data2);

        //these objects are created just to check that requests doesn't return data from another folder/class
        createMetadataEntity(folder1, metadataClass2, EXTERNAL_ID_1, data2);
        createMetadataEntity(folder2, metadataClass1, EXTERNAL_ID_1, data1);

        List<MetadataEntity> expectedSamples12 = Arrays.asList(folder1Sample1, folder1Sample2);
        List<MetadataEntity> expectedSamples21 = Arrays.asList(folder1Sample2, folder1Sample1);

        //test request with only folder and class
        MetadataFilter filterEmpty = createFilter(folder1.getId(), metadataClass1.getName(),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), false);
        checkFilterRequest(filterEmpty, expectedSamples12);

        MetadataFilter filterEmptyRecursive = createFilter(folder1.getId(), metadataClass1.getName(),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), true);
        checkFilterRequest(filterEmptyRecursive, expectedSamples21);

        //sorting
        List<MetadataFilter.OrderBy> tagSortingAsc =
                Arrays.asList(new MetadataFilter.OrderBy(DATA_KEY_1, false, false),
                        new MetadataFilter.OrderBy("id", false, true));
        MetadataFilter order = createFilter(folder1.getId(), metadataClass1.getName(),
                Collections.emptyList(), Collections.emptyList(), tagSortingAsc, false);
        checkFilterRequest(order, expectedSamples21);

        MetadataFilter orderRecursive = createFilter(folder1.getId(), metadataClass1.getName(),
                Collections.emptyList(), Collections.emptyList(), tagSortingAsc, true);
        checkFilterRequest(orderRecursive, expectedSamples21);

        MetadataFilter orderDesc = createFilter(folder1.getId(), metadataClass1.getName(),
                Collections.emptyList(), Collections.emptyList(),
                Arrays.asList(new MetadataFilter.OrderBy(DATA_KEY_1, true, false),
                        new MetadataFilter.OrderBy("id", false, true)), false);
        checkFilterRequest(orderDesc, expectedSamples12);

        //filter by field
        MetadataFilter filterByField = createFilter(folder1.getId(), metadataClass1.getName(),
                Collections.emptyList(),
                Collections.singletonList(
                        new MetadataFilter.FilterQuery("externalId", Collections.singletonList(EXTERNAL_ID_2),
                                true)),
                Collections.emptyList(), false);
        checkFilterRequest(filterByField, Collections.singletonList(folder1Sample2));

        //filter by json
        MetadataFilter filterByValue = createFilter(folder1.getId(), metadataClass1.getName(),
                Collections.emptyList(),
                Collections.singletonList(
                        new MetadataFilter.FilterQuery(DATA_KEY_1, Collections.singletonList(DATA_VALUE_2), false)),
                Collections.emptyList(), false);
        checkFilterRequest(filterByValue, Collections.singletonList(folder1Sample2));

        MetadataFilter filterByValueRec = createFilter(folder1.getId(), metadataClass1.getName(),
                Collections.emptyList(),
                Collections.singletonList(
                        new MetadataFilter.FilterQuery(DATA_KEY_1, Collections.singletonList(DATA_VALUE_1), false)),
                Collections.emptyList(), true);
        checkFilterRequest(filterByValueRec, Collections.singletonList(folder1Sample1));

        MetadataFilter filterByValueWrongValue = createFilter(folder1.getId(), metadataClass1.getName(),
                Collections.emptyList(),
                Collections.singletonList(
                        new MetadataFilter.FilterQuery(DATA_KEY_1, Collections.singletonList(TEST_USER), false)),
                Collections.emptyList(), true);
        checkFilterRequest(filterByValueWrongValue, Collections.emptyList());

        MetadataFilter filterByValueWrongKey = createFilter(folder1.getId(), metadataClass1.getName(),
                Collections.emptyList(),
                Collections.singletonList(
                        new MetadataFilter.FilterQuery(DATA_VALUE_2, Collections.singletonList(DATA_VALUE_2), false)),
                Collections.emptyList(), true);
        checkFilterRequest(filterByValueWrongKey, Collections.emptyList());

        //search
        MetadataFilter searchBothMatch = createFilter(folder1.getId(), metadataClass1.getName(),
                Collections.singletonList("ner"), Collections.emptyList(),
                Collections.singletonList(new MetadataFilter.OrderBy("id", false, true)),
                true);
        checkFilterRequest(searchBothMatch, expectedSamples12);

        MetadataFilter searchOneMatch = createFilter(folder1.getId(), metadataClass1.getName(),
                Collections.singletonList("MIN"), Collections.emptyList(), Collections.emptyList(), true);
        checkFilterRequest(searchOneMatch, Collections.singletonList(folder1Sample2));

        MetadataFilter searchNoneMatch = createFilter(folder1.getId(), metadataClass1.getName(),
                Collections.singletonList(DATA_KEY_1), Collections.emptyList(), Collections.emptyList(), true);
        checkFilterRequest(searchNoneMatch, Collections.emptyList());

        //search by external ID:
        MetadataFilter searchByExternalId = createFilter(folder1.getId(), metadataClass1.getName(),
                Collections.emptyList(), Collections.emptyList(),
                Collections.singletonList(new MetadataFilter.OrderBy("id", false, true)),
                false, Collections.singletonList(EXTERNAL_ID_1), null, null, null);
        checkFilterRequest(searchByExternalId, Collections.singletonList(folder1Sample1));
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void testDateFilter() {
        MetadataClass metadataClass1 = createMetadataClass(CLASS_NAME_1);
        Folder folder1 = createFolder();
        LocalDateTime date1 = LocalDateTime.now();
        LocalDateTime date2 = LocalDateTime.now().minusDays(1);
        MetadataEntity folder1Sample1 = ObjectCreatorUtils.createMetadataEntity(folder1, metadataClass1,
                TEST_ENTITY_NAME_1, EXTERNAL_ID_1, new HashMap<>(),
                Date.from(date1.atZone(ZoneId.systemDefault()).toInstant()));
        metadataEntityDao.createMetadataEntity(folder1Sample1);
        MetadataEntity folder1Sample2 = ObjectCreatorUtils.createMetadataEntity(folder1, metadataClass1,
                TEST_ENTITY_NAME_1, EXTERNAL_ID_2, new HashMap<>(),
                Date.from(date2.atZone(ZoneId.systemDefault()).toInstant()));
        metadataEntityDao.createMetadataEntity(folder1Sample2);

        MetadataFilter filterByDate = createFilter(folder1.getId(), metadataClass1.getName(),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), false,
                Collections.emptyList(), date1, date1.plusDays(1), null);
        MetadataFilter filterByDate2 = createFilter(folder1.getId(), metadataClass1.getName(),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), false,
                Collections.emptyList(), date2, date1, null);

        checkFilterRequest(filterByDate, Collections.singletonList(folder1Sample1));
        checkFilterRequest(filterByDate2, Arrays.asList(folder1Sample1, folder1Sample2));
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void testGetKeys() {
        MetadataClass metadataClass = createMetadataClass(CLASS_NAME_1);
        Map<String, PipeConfValue> data1 = new HashMap<>();
        data1.put(DATA_KEY_1, new PipeConfValue(DATA_TYPE_1, DATA_VALUE_1));
        Folder folder = createFolder();
        MetadataEntity entity = createMetadataEntity(folder, metadataClass, EXTERNAL_ID_1, data1);
        Set<MetadataField> defaultKeys = new HashSet<>(MetadataEntityDao.MetadataEntityParameters.fieldNames.values());

        checkGetKeys(metadataClass, folder, defaultKeys, getDataField(DATA_KEY_1));

        Map<String, PipeConfValue> data2 = new HashMap<>();
        data2.put(DATA_KEY_2, new PipeConfValue(DATA_TYPE_1, DATA_VALUE_1));
        createMetadataEntity(folder, metadataClass, EXTERNAL_ID_2, data2);

        checkGetKeys(metadataClass, folder, defaultKeys, getDataField(DATA_KEY_1), getDataField(DATA_KEY_2));

        metadataEntityDao.deleteMetadataEntity(entity.getId());
        checkGetKeys(metadataClass, folder, defaultKeys, getDataField(DATA_KEY_2));
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void testGetMetadataKeys() {
        MetadataClass metadataClass1 = createMetadataClass(CLASS_NAME_1);
        MetadataClass metadataClass2 = createMetadataClass(CLASS_NAME_2);

        Folder parent = createFolder();
        Folder child = createFolder(parent.getId());

        Map<String, PipeConfValue> data1 = new HashMap<>();
        data1.put(DATA_KEY_1, new PipeConfValue(DATA_TYPE_1, DATA_VALUE_1));
        createMetadataEntity(child, metadataClass1, EXTERNAL_ID_1, data1);

        Map<String, PipeConfValue> data2 = new HashMap<>();
        data2.put(DATA_KEY_2, new PipeConfValue(DATA_TYPE_2, DATA_VALUE_1));
        createMetadataEntity(parent, metadataClass2, EXTERNAL_ID_1, data2);

        Collection<MetadataClassDescription> metadataFields =
                metadataEntityDao.getMetadataFields(parent.getId());
        assertEquals(2, metadataFields.size());
        Map<Long, MetadataClassDescription> results = metadataFields.stream()
                .collect(Collectors.toMap(e -> e.getMetadataClass().getId(), Function.identity()));
        assertEquals(Collections.singletonList(new EntityTypeField(DATA_KEY_1, DATA_TYPE_1)),
                results.get(metadataClass1.getId()).getFields());
        assertEquals(Collections.singletonList(new EntityTypeField(DATA_KEY_2, CLASS_NAME_2, true, false)),
                results.get(metadataClass2.getId()).getFields());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void shouldInsertCopiesOfExistentMetadataEntities() {
        Folder rootFolder = createFolder();
        Folder sourceFolder = createFolder(rootFolder.getId());
        MetadataClass metadataClass1 = createMetadataClass(CLASS_NAME_1);
        MetadataClass metadataClass2 = createMetadataClass(CLASS_NAME_2);
        Map<String, PipeConfValue> data = new HashMap<>();
        MetadataEntity metadataEntity1 = createMetadataEntity(sourceFolder, metadataClass1, EXTERNAL_ID_1, data);
        MetadataEntity metadataEntity2 = createMetadataEntity(sourceFolder, metadataClass2, EXTERNAL_ID_2, data);
        Folder destinationFolder = createFolder(rootFolder.getId());

        metadataEntityDao.insertCopiesOfExistentMetadataEntities(sourceFolder.getId(), destinationFolder.getId());

        List<MetadataEntity> expected = Stream.of(metadataEntity1, metadataEntity2).collect(Collectors.toList());
        List<MetadataEntity> entitiesStoredInSourceFolder = metadataEntityDao.loadAllMetadataEntities().stream()
                .filter(entity -> Objects.equals(entity.getParent().getId(), sourceFolder.getId()))
                .collect(Collectors.toList());
        checkSearchResult(expected, entitiesStoredInSourceFolder);

        expected.forEach(entity -> entity.setParent(destinationFolder));
        List<MetadataEntity> entitiesStoredInDestinationFolder = metadataEntityDao.loadAllMetadataEntities().stream()
                .filter(entity -> Objects.equals(entity.getParent().getId(), destinationFolder.getId()))
                .collect(Collectors.toList());
        assertEquals(expected.size(), entitiesStoredInDestinationFolder.size());
        Map<String, MetadataEntity> expectedMap =
                expected.stream().collect(Collectors.toMap(MetadataEntity::getExternalId, Function.identity()));
        entitiesStoredInDestinationFolder.forEach(e -> compareMetadata(expectedMap.get(e.getExternalId()), e, false));
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void shouldLoadMetadataEntityWithFolders() {
        Folder root = createFolder(null);
        root.setParentId(0L);
        Folder folder = createFolder(root.getId());
        folder.setParent(root);
        Folder parent = createFolder(folder.getId());
        parent.setParent(folder);

        MetadataClass metadataClass = createMetadataClass(CLASS_NAME_1);
        Map<String, PipeConfValue> data = new LinkedHashMap<>();
        data.put(DATA_KEY_1, new PipeConfValue(DATA_TYPE_1, DATA_VALUE_1));
        MetadataEntity metadataEntity = createMetadataEntity(parent, metadataClass, EXTERNAL_ID_1, data);

        MetadataEntity result = metadataEntityDao.loadMetadataEntityWithParents(metadataEntity.getId());
        assertEquals(metadataEntity.getId(), result.getId());
        assertEquals(metadataEntity.getName(), result.getName());
        assertEquals(metadataEntity.getParent().getId(), result.getParent().getId());
        assertEquals(metadataEntity.getExternalId(), result.getExternalId());
        assertEquals(metadataEntity.getData(), result.getData());
        verifyFolderTree(parent, result.getParent());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void testMultipleFieldFilter() {
        MetadataClass metadataClass1 = createMetadataClass(CLASS_NAME_1);
        Folder folder1 = createFolder();

        Map<String, PipeConfValue> data1 = new HashMap<>();
        data1.put(DATA_KEY_1, new PipeConfValue(DATA_TYPE_1, DATA_VALUE_1));
        MetadataEntity folder1Sample1 = createMetadataEntity(folder1, metadataClass1, EXTERNAL_ID_1, data1);

        Map<String, PipeConfValue> data2 = new HashMap<>();
        data2.put(DATA_KEY_1, new PipeConfValue(DATA_TYPE_1, DATA_VALUE_2));
        MetadataEntity folder1Sample2 = createMetadataEntity(folder1, metadataClass1, EXTERNAL_ID_2, data2);

        MetadataFilter filterByMultipleValues = createFilter(folder1.getId(), metadataClass1.getName(),
                Collections.emptyList(),
                Collections.singletonList(new MetadataFilter.FilterQuery(DATA_KEY_1,
                        Arrays.asList(DATA_VALUE_2.substring(0, DATA_VALUE_2.length() / 2), DATA_VALUE_1), false)),
                Collections.emptyList(), false);
        checkFilterRequest(filterByMultipleValues, Arrays.asList(folder1Sample1, folder1Sample2));
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void testSubstringMatchFieldFilter() {
        MetadataClass metadataClass1 = createMetadataClass(CLASS_NAME_1);
        Folder folder1 = createFolder();

        Map<String, PipeConfValue> data1 = new HashMap<>();
        data1.put(DATA_KEY_1, new PipeConfValue(DATA_TYPE_1, DATA_VALUE_1));
        //this object is created to check that request doesn't return data that does not match the filter
        createMetadataEntity(folder1, metadataClass1, EXTERNAL_ID_1, data1);

        Map<String, PipeConfValue> data2 = new HashMap<>();
        data2.put(DATA_KEY_1, new PipeConfValue(DATA_TYPE_1, DATA_VALUE_2));
        MetadataEntity folder1Sample2 = createMetadataEntity(folder1, metadataClass1, EXTERNAL_ID_2, data2);
        MetadataFilter filterByValueSubstring = createFilter(folder1.getId(), metadataClass1.getName(),
                Collections.emptyList(),
                Collections.singletonList(new MetadataFilter.FilterQuery(DATA_KEY_1,
                        Collections.singletonList(DATA_VALUE_2.substring(0, DATA_VALUE_2.length() / 2)), false)),
                Collections.emptyList(), false);
        checkFilterRequest(filterByValueSubstring, Collections.singletonList(folder1Sample2));
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void testSearchByExternalId() {
        MetadataClass metadataClass1 = createMetadataClass(CLASS_NAME_1);
        Folder folder1 = createFolder();

        Map<String, PipeConfValue> data1 = new HashMap<>();
        data1.put(DATA_KEY_1, new PipeConfValue(DATA_TYPE_1, DATA_VALUE_1));
        //this object is created to check that request doesn't return data that does not match the search
        createMetadataEntity(folder1, metadataClass1, EXTERNAL_ID_1, data1);

        Map<String, PipeConfValue> data2 = new HashMap<>();
        data2.put(DATA_KEY_1, new PipeConfValue(DATA_TYPE_1, DATA_VALUE_2));
        MetadataEntity folder1Sample2 = createMetadataEntity(folder1, metadataClass1, EXTERNAL_ID_2, data2);

        MetadataFilter searchByExternalId = createFilter(folder1.getId(), metadataClass1.getName(),
                Collections.singletonList(EXTERNAL_ID_2.substring(EXTERNAL_ID_2.length() / 2)),
                Collections.emptyList(),
                Collections.singletonList(new MetadataFilter.OrderBy("id", false, true)), true);
        checkFilterRequest(searchByExternalId, Collections.singletonList(folder1Sample2));
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void testSearchANDOperator() {
        MetadataClass metadataClass1 = createMetadataClass(CLASS_NAME_1);
        Folder folder1 = createFolder();

        Map<String, PipeConfValue> data1 = new HashMap<>();
        data1.put(DATA_KEY_1, new PipeConfValue(DATA_TYPE_1, DATA_VALUE_1));
        createMetadataEntity(folder1, metadataClass1, EXTERNAL_ID_1, data1);

        Map<String, PipeConfValue> data2 = new HashMap<>();
        data2.put(DATA_KEY_1, new PipeConfValue(DATA_TYPE_1, DATA_VALUE_2));
        createMetadataEntity(folder1, metadataClass1, EXTERNAL_ID_2, data2);

        MetadataFilter searchANDOperator = createFilter(folder1.getId(), metadataClass1.getName(),
                Arrays.asList(DATA_VALUE_1.substring(DATA_VALUE_1.length() / 2),
                        DATA_VALUE_2.substring(DATA_VALUE_2.length() / 2)), Collections.emptyList(),
                Collections.singletonList(new MetadataFilter.OrderBy("id", false, true)), true);
        searchANDOperator.setLogicalSearchOperator(LogicalSearchOperator.AND);
        checkFilterRequest(searchANDOperator, Collections.emptyList());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void testSearchOROperator() {
        MetadataClass metadataClass1 = createMetadataClass(CLASS_NAME_1);
        Folder folder1 = createFolder();

        Map<String, PipeConfValue> data1 = new HashMap<>();
        data1.put(DATA_KEY_1, new PipeConfValue(DATA_TYPE_1, DATA_VALUE_1));
        MetadataEntity folder1Sample1 = createMetadataEntity(folder1, metadataClass1, EXTERNAL_ID_1, data1);

        Map<String, PipeConfValue> data2 = new HashMap<>();
        data2.put(DATA_KEY_1, new PipeConfValue(DATA_TYPE_1, DATA_VALUE_2));
        MetadataEntity folder1Sample2 = createMetadataEntity(folder1, metadataClass1, EXTERNAL_ID_2, data2);

        MetadataFilter searchANDOperator = createFilter(folder1.getId(), metadataClass1.getName(),
                Arrays.asList(DATA_VALUE_1.substring(DATA_VALUE_1.length() / 2),
                        DATA_VALUE_2.substring(DATA_VALUE_2.length() / 2)), Collections.emptyList(),
                Collections.singletonList(new MetadataFilter.OrderBy("id", false, true)), true);
        searchANDOperator.setLogicalSearchOperator(LogicalSearchOperator.OR);
        checkFilterRequest(searchANDOperator, Arrays.asList(folder1Sample1, folder1Sample2));
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void testCombineFilterAndSearch() {
        MetadataClass metadataClass1 = createMetadataClass(CLASS_NAME_1);
        Folder folder1 = createFolder();

        Map<String, PipeConfValue> data1 = new HashMap<>();
        data1.put(DATA_KEY_1, new PipeConfValue(DATA_TYPE_1, DATA_VALUE_1));

        Map<String, PipeConfValue> data2 = new HashMap<>();
        data2.put(DATA_KEY_1, new PipeConfValue(DATA_TYPE_1, DATA_VALUE_2));

        MetadataEntity folder1Sample1 = createMetadataEntity(folder1, metadataClass1, EXTERNAL_ID_1, data2);
        MetadataEntity folder1Sample2 = createMetadataEntity(folder1, metadataClass1, EXTERNAL_ID_2, data2);

        //this object is created to check that request doesn't return data that does not match the search
        createMetadataEntity(folder1, metadataClass1, EXTERNAL_ID_1 + 2, data1);

        MetadataFilter combineSearchAndFilter = createFilter(folder1.getId(), metadataClass1.getName(),
                Arrays.asList(DATA_VALUE_2.substring(DATA_VALUE_2.length() / 2),
                        DATA_VALUE_1.substring(DATA_VALUE_1.length() / 2)),
                Collections.singletonList(new MetadataFilter.FilterQuery(DATA_KEY_1,
                        Collections.singletonList(DATA_VALUE_2.substring(0, DATA_VALUE_2.length() / 2)), false)),
                Collections.singletonList(new MetadataFilter.OrderBy("id", false, true)), true);
        checkFilterRequest(combineSearchAndFilter, Arrays.asList(folder1Sample1, folder1Sample2));
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void testFacetQuery() {
        final Folder folder1 = createFolder();
        // used only to prove isolation by folder
        final Folder folder2 = createFolder();

        final MetadataClass metadataClass = createMetadataClass(CLASS_NAME_1);

        final String key1 = "key 1";
        final String key2 = "key2";
        final String key3 = "key3";
        final String key4 = "key4";

        final String value1 = "key1-value1";
        final String value21 = "key2-value1";
        final String value22 = "key2-value2";
        final String value3 = "key3-value1";
        final String value4 = "key4-value1";

        final Map<String, PipeConfValue> data1 = new HashMap<>();
        data1.put(key1, new PipeConfValue(DATA_TYPE_1, value1));
        data1.put(key2, new PipeConfValue(DATA_TYPE_1, StringUtils.EMPTY));
        data1.put(key3, new PipeConfValue(DATA_TYPE_1, StringUtils.EMPTY));
        createMetadataEntity(folder1, metadataClass, EXTERNAL_ID_1, data1);

        final Map<String, PipeConfValue> data2 = new HashMap<>();
        data2.put(key1, new PipeConfValue(DATA_TYPE_1, value1));
        data2.put(key2, new PipeConfValue(DATA_TYPE_1, value21));
        data2.put(key3, new PipeConfValue(DATA_TYPE_1, null));
        createMetadataEntity(folder1, metadataClass, EXTERNAL_ID_2, data2);

        final Map<String, PipeConfValue> data3 = new HashMap<>();
        data3.put(key1, new PipeConfValue(DATA_TYPE_1, value1));
        data3.put(key2, new PipeConfValue(DATA_TYPE_1, value22));
        data3.put(key4, new PipeConfValue(DATA_TYPE_1, value4));
        createMetadataEntity(folder1, metadataClass, EXTERNAL_ID_3, data3);

        final Map<String, PipeConfValue> data4 = new HashMap<>();
        data4.put(key1, new PipeConfValue(DATA_TYPE_1, value1));
        data4.put(key2, new PipeConfValue(DATA_TYPE_1, value21));
        data4.put(key3, new PipeConfValue(DATA_TYPE_1, value3));
        createMetadataEntity(folder2, metadataClass, EXTERNAL_ID_1, data4);

        MetadataFilter filter = createFilterWithFacet(folder1.getId(), metadataClass.getName(),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList());

        //checking empty facet request
        Map<String, Facet> facetMap = metadataEntityDao.groupFacets(filter);
        Assert.assertTrue(facetMap.isEmpty());

        //checking facet request
        filter = createFilterWithFacet(folder1.getId(), metadataClass.getName(),
                Collections.emptyList(),
                Arrays.asList(new MetadataFilter.FacetRequest(key1, false),
                        new MetadataFilter.FacetRequest(key2, false),
                        new MetadataFilter.FacetRequest(key3, false)),
                Collections.emptyList());
        facetMap = metadataEntityDao.groupFacets(filter);

        Assert.assertFalse(facetMap.isEmpty());
        Assert.assertEquals(3, facetMap.size());
        Assert.assertTrue(facetMap.containsKey(key1));

        Facet facet = facetMap.get(key1);
        Assert.assertEquals(0, facet.getEmpty().intValue());
        Assert.assertEquals(1, facet.getCounts().size());
        Assert.assertTrue(facet.getCounts().containsKey(value1));
        Assert.assertEquals(3, facet.getCounts().get(value1).intValue());

        Assert.assertTrue(facetMap.containsKey(key2));
        facet = facetMap.get(key2);
        Assert.assertEquals(2, facet.getCounts().size());
        Assert.assertTrue(facet.getCounts().containsKey(value21));
        Assert.assertTrue(facet.getCounts().containsKey(value22));
        Assert.assertEquals(1, facet.getCounts().get(value21).intValue());
        Assert.assertEquals(1, facet.getCounts().get(value22).intValue());
        Assert.assertEquals(1, facet.getEmpty().intValue());

        Assert.assertTrue(facetMap.containsKey(key3));
        facet = facetMap.get(key3);
        Assert.assertEquals(0, facet.getCounts().size());
        Assert.assertEquals(3, facet.getEmpty().intValue());

        //checking facet request with filters
        filter = createFilterWithFacet(folder1.getId(), metadataClass.getName(),
                Collections.singletonList(new MetadataFilter.FilterQuery(key2, Arrays.asList(value21, value22), false)),
                Arrays.asList(
                    new MetadataFilter.FacetRequest(key1, false),
                    new MetadataFilter.FacetRequest(key4, false)),
                Collections.emptyList());
        facetMap = metadataEntityDao.groupFacets(filter);

        Assert.assertFalse(facetMap.isEmpty());
        Assert.assertEquals(2, facetMap.size());
        Assert.assertTrue(facetMap.containsKey(key1));

        facet = facetMap.get(key1);
        Assert.assertEquals(0, facet.getEmpty().intValue());
        Assert.assertEquals(1, facet.getCounts().size());
        Assert.assertTrue(facet.getCounts().containsKey(value1));
        Assert.assertEquals(2, facet.getCounts().get(value1).intValue());

        Assert.assertTrue(facetMap.containsKey(key4));

        facet = facetMap.get(key4);
        Assert.assertEquals(1, facet.getEmpty().intValue());
        Assert.assertEquals(1, facet.getCounts().size());
        Assert.assertTrue(facet.getCounts().containsKey(value4));
        Assert.assertEquals(1, facet.getCounts().get(value4).intValue());

        //checking facet request with search queries
        filter = createFilterWithFacet(folder1.getId(), metadataClass.getName(),
                Collections.emptyList(),
                Arrays.asList(
                    new MetadataFilter.FacetRequest(key1, false),
                    new MetadataFilter.FacetRequest(key4, false)),
                Collections.singletonList(value4));
        facetMap = metadataEntityDao.groupFacets(filter);

        Assert.assertFalse(facetMap.isEmpty());
        Assert.assertEquals(2, facetMap.size());
        Assert.assertTrue(facetMap.containsKey(key1));

        facet = facetMap.get(key1);
        Assert.assertEquals(0, facet.getEmpty().intValue());
        Assert.assertEquals(1, facet.getCounts().size());
        Assert.assertTrue(facet.getCounts().containsKey(value1));
        Assert.assertEquals(1, facet.getCounts().get(value1).intValue());

        Assert.assertTrue(facetMap.containsKey(key4));

        facet = facetMap.get(key4);
        Assert.assertEquals(0, facet.getEmpty().intValue());
        Assert.assertEquals(1, facet.getCounts().size());
        Assert.assertTrue(facet.getCounts().containsKey(value4));
        Assert.assertEquals(1, facet.getCounts().get(value4).intValue());
    }

    private MetadataField getDataField(String key) {
        return new MetadataField(key, null, false);
    }

    private void checkGetKeys(MetadataClass metadataClass, Folder folder, Set<MetadataField> defaultKeys,
            MetadataField... expectedKeys) {
        List<MetadataField> keys =
                metadataEntityDao.getMetadataKeys(folder.getId(), metadataClass.getId());
        Set<MetadataField> expected = new HashSet<>(defaultKeys);
        expected.addAll(Arrays.asList(expectedKeys));
        assertEquals(expected, new HashSet<>(keys));
    }

    private void checkFilterRequest(MetadataFilter filter, List<MetadataEntity> expected) {
        List<MetadataEntity> result = metadataEntityDao.filterEntities(filter);
        checkSearchResult(expected, result);
        int count = metadataEntityDao.countEntities(filter);
        assertEquals(expected.size(), count);
    }

    private void checkSearchResult(List<MetadataEntity> expected, List<MetadataEntity> actual) {
        assertEquals(expected.size(), actual.size());
        Map<Long, MetadataEntity> expectedMap =
                expected.stream().collect(Collectors.toMap(BaseEntity::getId, Function.identity()));
        actual.forEach(e -> compareMetadata(expectedMap.get(e.getId()), e, true));
    }

    private void compareMetadata(MetadataEntity metadataEntity, MetadataEntity result, boolean compareExactly) {
        if (compareExactly) {
            assertEquals(metadataEntity.getId(), result.getId());
            assertEquals(metadataEntity.getCreatedDate(), result.getCreatedDate());
        }
        assertEquals(metadataEntity.getName(), result.getName());
        assertEquals(metadataEntity.getClassEntity().getName(), result.getClassEntity().getName());
        assertEquals(metadataEntity.getParent().getId(), result.getParent().getId());
        assertEquals(metadataEntity.getExternalId(), result.getExternalId());
        assertEquals(metadataEntity.getData(), result.getData());
    }

    private MetadataFilter createFilter(Long folderId, String className,
            List<String> searchQueries, List<MetadataFilter.FilterQuery> filters,
            List<MetadataFilter.OrderBy> sorting, boolean recursive) {
        return createFilter(folderId, className, searchQueries, filters, sorting, recursive, null, null, null, null);
    }

    private MetadataFilter createFilterWithFacet(Long folderId, String className,
                                                 List<MetadataFilter.FilterQuery> filters,
                                                 List<MetadataFilter.FacetRequest> facets,
                                                 List<String> searchQueries
                                                 ) {
        return createFilter(folderId, className, searchQueries, filters, null, false, null, null, null, facets);
    }

    private MetadataFilter createFilter(Long folderId, String className,
                                        List<String> searchQueries, List<MetadataFilter.FilterQuery> filters,
                                        List<MetadataFilter.OrderBy> sorting, boolean recursive,
                                        List<String> externalIds, LocalDateTime startDateFrom,
                                        LocalDateTime endDateTo, List<MetadataFilter.FacetRequest> facets) {
        MetadataFilter filter = new MetadataFilter();
        filter.setFolderId(folderId);
        filter.setMetadataClass(className);
        filter.setRecursive(recursive);
        filter.setPage(1);
        filter.setPageSize(10);
        filter.setFilters(filters);
        filter.setOrderBy(sorting);
        filter.setSearchQueries(searchQueries);
        filter.setExternalIdQueries(externalIds);
        filter.setStartDateFrom(startDateFrom);
        filter.setEndDateTo(endDateTo);
        filter.setFacets(facets);
        return filter;
    }

    private Folder createFolder() {
        return createFolder(null);
    }

    private Folder createFolder(Long parentId) {
        Folder folder = new Folder();
        folder.setName(TEST_NAME);
        folder.setOwner(TEST_USER);
        folder.setParentId(parentId);
        folderDao.createFolder(folder);
        return folder;
    }

    private MetadataEntity createMetadataEntity(Folder folder, MetadataClass metadataClass,
            String externalId, Map<String, PipeConfValue> data) {

        MetadataEntity metadataEntity = ObjectCreatorUtils.createMetadataEntity(folder, metadataClass,
                TEST_ENTITY_NAME_1, externalId, data, DateUtils.now());
        metadataEntityDao.createMetadataEntity(metadataEntity);
        return metadataEntity;
    }

    private MetadataClass createMetadataClass(String name) {
        MetadataClass metadataClass = ObjectCreatorUtils.createMetadataClass(name);
        metadataClassDao.createMetadataClass(metadataClass);
        return metadataClass;
    }

    private void verifyFolderTree(final Folder expected, final Folder actual) {
        assertEquals(expected.getId(), actual.getId());
        assertEquals(expected.getParentId(), actual.getParentId());
        if (expected.getParent() != null) {
            verifyFolderTree(expected.getParent(), actual.getParent());
        }
    }
}
