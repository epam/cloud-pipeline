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

package com.epam.pipeline.dao.metadata;

import com.epam.pipeline.AbstractSpringTest;
import com.epam.pipeline.dao.pipeline.FolderDao;
import com.epam.pipeline.entity.BaseEntity;
import com.epam.pipeline.entity.metadata.*;
import com.epam.pipeline.entity.pipeline.Folder;
import com.epam.pipeline.manager.ObjectCreatorUtils;
import com.epam.pipeline.manager.metadata.parser.EntityTypeField;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MetadataEntityDaoTest extends AbstractSpringTest {

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
        Assert.assertEquals(2, folder1List.size());

        List<MetadataEntity> folder2List = metadataEntityDao
                .loadMetadataEntityByClassNameAndFolderId(folder2.getId(), CLASS_NAME_1);
        Assert.assertEquals(1, folder2List.size());

        metadataEntityDao.deleteMetadataFromFolder(folder1.getId());

        folder1List = metadataEntityDao
                .loadMetadataEntityByClassNameAndFolderId(folder1.getId(), CLASS_NAME_1);
        Assert.assertEquals(0, folder1List.size());
        folder2List = metadataEntityDao
                .loadMetadataEntityByClassNameAndFolderId(folder2.getId(), CLASS_NAME_1);
        Assert.assertEquals(1, folder2List.size());
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
        Assert.assertEquals(metadataEntity.getName(), updateResult.getName());
        Assert.assertEquals(metadataEntity.getData(), updateResult.getData());

        // update metadata entity data key
        data.put(DATA_KEY_2, new PipeConfValue(DATA_TYPE_1, DATA_VALUE_2));
        metadataEntity.setData(data);
        metadataEntityDao.updateMetadataEntityDataKey(metadataEntity, DATA_KEY_2, DATA_VALUE_2, DATA_TYPE_1);
        MetadataEntity updateKeyResult = metadataEntityDao
                .loadMetadataEntityByClassNameAndFolderId(metadataEntity.getParent().getId(),
                        metadataEntity.getClassEntity().getName()).get(0);
        Assert.assertEquals(metadataEntity.getData(), updateKeyResult.getData());

        // load metadata entity from root
        MetadataEntity metadataEntityInRoot = new MetadataEntity();
        metadataEntityInRoot.setName(TEST_ENTITY_NAME_3);
        metadataEntityInRoot.setClassEntity(metadataClass);
        metadataEntityInRoot.setExternalId(EXTERNAL_ID_1);
        metadataEntityInRoot.setData(data);
        metadataEntityInRoot.setParent(new Folder());
        metadataEntityDao.createMetadataEntity(metadataEntityInRoot);

        MetadataEntity rootResult = metadataEntityDao.loadRootMetadataEntities().get(0);
        Assert.assertEquals(metadataEntityInRoot.getId(), rootResult.getId());
        Assert.assertEquals(metadataEntityInRoot.getClassEntity().getName(), rootResult.getClassEntity().getName());
        Assert.assertEquals(metadataEntityInRoot.getData(), rootResult.getData());

        // load by external ids
        MetadataEntity entity2 = createMetadataEntity(folder, metadataClass, EXTERNAL_ID_2, data);
        Set<MetadataEntity> existing = metadataEntityDao
                .loadExisting(folder.getId(), metadataClass.getName(),
                        new HashSet<>(Arrays.asList(EXTERNAL_ID_1, EXTERNAL_ID_2)));
        Assert.assertEquals(new HashSet<>(Arrays.asList(metadataEntity, entity2)), existing);

        //load by inner ids
        Set<MetadataEntity> entitiesByIds =
                metadataEntityDao.loadByIds(Collections.singleton(entity2.getId()));
        Assert.assertEquals(Collections.singleton(entity2), entitiesByIds);

        //load with folders
        rootResult = metadataEntityDao.loadMetadataEntityWithParents(metadataEntityInRoot.getId());
        Assert.assertEquals(metadataEntityInRoot.getId(), rootResult.getId());
        Assert.assertEquals(metadataEntityInRoot.getData(), rootResult.getData());

        // delete key from metadata entity
        data.clear();
        data.put(DATA_KEY_1, new PipeConfValue(DATA_TYPE_1, DATA_VALUE_1));
        metadataEntity.setData(data);
        metadataEntityDao.deleteMetadataItemKey(metadataEntity.getId(), DATA_KEY_2);
        MetadataEntity deletedKeyResult = metadataEntityDao.loadMetadataEntityById(metadataEntity.getId());
        Assert.assertEquals(metadataEntity.getData(), deletedKeyResult.getData());

        // delete metadata entity
        metadataEntityDao.deleteMetadataEntity(metadataEntity.getId());
        MetadataEntity deletedEntity = metadataEntityDao.loadMetadataEntityById(metadataEntity.getId());
        Assert.assertNull(deletedEntity);
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
        Assert.assertEquals(new HashSet<>(Arrays.asList(batch1, participant1, participant2, sample)),
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
        Assert.assertEquals(new HashSet<>(Arrays.asList(participant1, participant2)), new HashSet<>(links));
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void testBatchQueries() {

        Folder folder = createFolder();
        MetadataClass metadataClass = createMetadataClass(CLASS_NAME_1);
        Map<String, PipeConfValue> data = new HashMap<>();
        data.put(DATA_KEY_1, new PipeConfValue(DATA_TYPE_1, DATA_VALUE_1));

        MetadataEntity entity1 = ObjectCreatorUtils.createMetadataEntity(folder, metadataClass, TEST_ENTITY_NAME_1,
                EXTERNAL_ID_1, data);
        MetadataEntity entity2 = ObjectCreatorUtils.createMetadataEntity(folder, metadataClass, TEST_ENTITY_NAME_1,
                EXTERNAL_ID_2, data);
        Collection<MetadataEntity> result =
                metadataEntityDao.batchInsert(Arrays.asList(entity1, entity2));
        Assert.assertTrue(result.stream().allMatch(e -> e.getId() != null));

        data.put(DATA_KEY_2, new PipeConfValue(DATA_TYPE_2, DATA_VALUE_2));
        entity1.setData(data);
        entity2.setData(data);
        metadataEntityDao.batchUpdate(Arrays.asList(entity1, entity2));

        List<MetadataEntity> loaded = metadataEntityDao
                .loadMetadataEntityByClassNameAndFolderId(folder.getId(), metadataClass.getName());
        Assert.assertTrue(loaded.stream().allMatch(e -> e.getData().equals(data)));

        Set<Long> entitiesToDelete = Stream.of(entity1.getId(), entity2.getId()).collect(Collectors.toSet());
        metadataEntityDao.deleteMetadataEntities(entitiesToDelete);
        Assert.assertEquals(0, metadataEntityDao.loadAllMetadataEntities().stream()
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
                Arrays.asList(new MetadataFilter.OrderBy(DATA_KEY_1, false),
                        new MetadataFilter.OrderBy("id", false));
        MetadataFilter order = createFilter(folder1.getId(), metadataClass1.getName(),
                Collections.emptyList(), Collections.emptyList(), tagSortingAsc, false);
        checkFilterRequest(order, expectedSamples21);

        MetadataFilter orderRecursive = createFilter(folder1.getId(), metadataClass1.getName(),
                Collections.emptyList(), Collections.emptyList(), tagSortingAsc, true);
        checkFilterRequest(orderRecursive, expectedSamples21);

        MetadataFilter orderDesc = createFilter(folder1.getId(), metadataClass1.getName(),
                Collections.emptyList(), Collections.emptyList(),
                Arrays.asList(new MetadataFilter.OrderBy(DATA_KEY_1, true),
                        new MetadataFilter.OrderBy("id", false)), false);
        checkFilterRequest(orderDesc, expectedSamples12);

        //filter by field
        MetadataFilter filterByField = createFilter(folder1.getId(), metadataClass1.getName(),
                Collections.emptyList(),
                Collections.singletonList(new MetadataFilter.FilterQuery("externalId", EXTERNAL_ID_2)),
                Collections.emptyList(), false);
        checkFilterRequest(filterByField, Collections.singletonList(folder1Sample2));

        //filter by json
        MetadataFilter filterByValue = createFilter(folder1.getId(), metadataClass1.getName(),
                Collections.emptyList(),
                Collections.singletonList(new MetadataFilter.FilterQuery(DATA_KEY_1, DATA_VALUE_2)),
                Collections.emptyList(), false);
        checkFilterRequest(filterByValue, Collections.singletonList(folder1Sample2));

        MetadataFilter filterByValueRec = createFilter(folder1.getId(), metadataClass1.getName(),
                Collections.emptyList(),
                Collections.singletonList(new MetadataFilter.FilterQuery(DATA_KEY_1, DATA_VALUE_1)),
                Collections.emptyList(), true);
        checkFilterRequest(filterByValueRec, Collections.singletonList(folder1Sample1));

        MetadataFilter filterByValueWrongValue = createFilter(folder1.getId(), metadataClass1.getName(),
                Collections.emptyList(),
                Collections.singletonList(new MetadataFilter.FilterQuery(DATA_KEY_1, TEST_USER)),
                Collections.emptyList(), true);
        checkFilterRequest(filterByValueWrongValue, Collections.emptyList());

        MetadataFilter filterByValueWrongKey = createFilter(folder1.getId(), metadataClass1.getName(),
                Collections.emptyList(),
                Collections.singletonList(new MetadataFilter.FilterQuery(DATA_VALUE_2, DATA_VALUE_2)),
                Collections.emptyList(), true);
        checkFilterRequest(filterByValueWrongKey, Collections.emptyList());

        //search
        MetadataFilter searchBothMatch = createFilter(folder1.getId(), metadataClass1.getName(),
                Collections.singletonList("ner"), Collections.emptyList(),
                Collections.singletonList(new MetadataFilter.OrderBy("id", false)),
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
                Collections.singletonList(new MetadataFilter.OrderBy("id", false)),
                false, Collections.singletonList(EXTERNAL_ID_1));
        checkFilterRequest(searchByExternalId, Collections.singletonList(folder1Sample1));

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
        Assert.assertEquals(2, metadataFields.size());
        Map<Long, MetadataClassDescription> results = metadataFields.stream()
                .collect(Collectors.toMap(e -> e.getMetadataClass().getId(), Function.identity()));
        Assert.assertEquals(Collections.singletonList(new EntityTypeField(DATA_KEY_1, DATA_TYPE_1)),
                results.get(metadataClass1.getId()).getFields());
        Assert.assertEquals(Collections.singletonList(new EntityTypeField(DATA_KEY_2, CLASS_NAME_2, true, false)),
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
        Assert.assertEquals(expected.size(), entitiesStoredInDestinationFolder.size());
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
        Assert.assertEquals(metadataEntity.getId(), result.getId());
        Assert.assertEquals(metadataEntity.getName(), result.getName());
        Assert.assertEquals(metadataEntity.getParent().getId(), result.getParent().getId());
        Assert.assertEquals(metadataEntity.getExternalId(), result.getExternalId());
        Assert.assertEquals(metadataEntity.getData(), result.getData());
        verifyFolderTree(parent, result.getParent());
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
        Assert.assertEquals(expected, new HashSet<>(keys));
    }

    private void checkFilterRequest(MetadataFilter filter, List<MetadataEntity> expected) {
        List<MetadataEntity> result = metadataEntityDao.filterEntities(filter);
        checkSearchResult(expected, result);
        int count = metadataEntityDao.countEntities(filter);
        Assert.assertEquals(expected.size(), count);
    }

    private void checkSearchResult(List<MetadataEntity> expected, List<MetadataEntity> actual) {
        Assert.assertEquals(expected.size(), actual.size());
        Map<Long, MetadataEntity> expectedMap =
                expected.stream().collect(Collectors.toMap(BaseEntity::getId, Function.identity()));
        actual.forEach(e -> compareMetadata(expectedMap.get(e.getId()), e, true));
    }

    private void compareMetadata(MetadataEntity metadataEntity, MetadataEntity result, boolean compareIds) {
        if (compareIds) {
            Assert.assertEquals(metadataEntity.getId(), result.getId());
        }
        Assert.assertEquals(metadataEntity.getName(), result.getName());
        Assert.assertEquals(metadataEntity.getClassEntity().getName(), result.getClassEntity().getName());
        Assert.assertEquals(metadataEntity.getParent().getId(), result.getParent().getId());
        Assert.assertEquals(metadataEntity.getExternalId(), result.getExternalId());
        Assert.assertEquals(metadataEntity.getData(), result.getData());
    }

    private MetadataFilter createFilter(Long folderId, String className,
            List<String> searchQueries, List<MetadataFilter.FilterQuery> filters,
            List<MetadataFilter.OrderBy> sorting, boolean recursive) {
        return createFilter(folderId, className, searchQueries, filters, sorting, recursive, null);
    }

    private MetadataFilter createFilter(Long folderId, String className,
            List<String> searchQueries, List<MetadataFilter.FilterQuery> filters,
            List<MetadataFilter.OrderBy> sorting, boolean recursive, List<String> externalIds) {
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
                TEST_ENTITY_NAME_1, externalId, data);
        metadataEntityDao.createMetadataEntity(metadataEntity);
        return metadataEntity;
    }

    private MetadataClass createMetadataClass(String name) {
        MetadataClass metadataClass = ObjectCreatorUtils.createMetadataClass(name);
        metadataClassDao.createMetadataClass(metadataClass);
        return metadataClass;
    }

    private void verifyFolderTree(final Folder expected, final Folder actual) {
        Assert.assertEquals(expected.getId(), actual.getId());
        Assert.assertEquals(expected.getParentId(), actual.getParentId());
        if (expected.getParent() != null) {
            verifyFolderTree(expected.getParent(), actual.getParent());
        }
    }
}
