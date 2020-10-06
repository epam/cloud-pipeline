/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
import com.epam.pipeline.controller.vo.EntityVO;
import com.epam.pipeline.dao.issue.IssueDao;
import com.epam.pipeline.entity.issue.Issue;
import com.epam.pipeline.entity.metadata.CategoricalAttribute;
import com.epam.pipeline.entity.metadata.CategoricalAttributeValue;
import com.epam.pipeline.entity.metadata.MetadataEntry;
import com.epam.pipeline.entity.metadata.MetadataEntryWithIssuesCount;
import com.epam.pipeline.entity.metadata.PipeConfValue;
import com.epam.pipeline.entity.security.acl.AclClass;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertThat;

@Transactional
public class MetadataDaoTest extends AbstractSpringTest {

    private static final String AUTHOR = "Author";
    private static final Long ID_1 = 1L;
    private static final Long ID_2 = 2L;
    private static final Long ID_3 = 3L;
    private static final Long ID_4 = 4L;
    private static final AclClass CLASS_1 = AclClass.PIPELINE;
    private static final AclClass CLASS_2 = AclClass.DATA_STORAGE;
    private static final String DATA_KEY_1 = "tag";
    private static final String DATA_TYPE_1 = "string";
    private static final String DATA_VALUE_1 = "OWNER";
    private static final String DATA_KEY_2 = "role";
    private static final String DATA_TYPE_2 = "ref";
    private static final String DATA_VALUE_2 = "ADMIN";
    private static final String DATA_KEY_3 = "key";
    private static final String SENSITIVE_DATA_KEY = "sensitive_metadata_key";
    private static final String NON_EXISTING_DATA_KEY = "no_key";
    private static final String TEXT = "Text";

    @Autowired
    private IssueDao issueDao;
    @Autowired
    private MetadataDao metadataDao;

    @Test
    public void testOneMetadata() {
        EntityVO entityVO = new EntityVO(ID_1, CLASS_1);
        Map<String, PipeConfValue> data = new HashMap<>();
        data.put(DATA_KEY_1, new PipeConfValue(DATA_TYPE_1, DATA_VALUE_1));
        MetadataEntry metadataToSave = new MetadataEntry();
        metadataToSave.setEntity(entityVO);
        metadataToSave.setData(data);

        // metadata registration
        metadataDao.registerMetadataItem(metadataToSave);
        MetadataEntry createdMetadata = metadataDao.loadMetadataItem(entityVO);
        Assert.assertEquals(ID_1, createdMetadata.getEntity().getEntityId());
        Assert.assertEquals(CLASS_1, createdMetadata.getEntity().getEntityClass());
        Assert.assertEquals(data, createdMetadata.getData());

        // add key to metadata
        data.put(DATA_KEY_2, new PipeConfValue(DATA_TYPE_2, DATA_VALUE_2));
        metadataDao.uploadMetadataItemKey(entityVO, DATA_KEY_2, DATA_VALUE_2, DATA_TYPE_2);
        createdMetadata = metadataDao.loadMetadataItem(entityVO);
        Assert.assertEquals(ID_1, createdMetadata.getEntity().getEntityId());
        Assert.assertEquals(CLASS_1, createdMetadata.getEntity().getEntityClass());
        Assert.assertEquals(data, createdMetadata.getData());

        // delete key from metadata
        data.clear();
        data.put(DATA_KEY_1, new PipeConfValue(DATA_TYPE_1, DATA_VALUE_1));
        metadataDao.deleteMetadataItemKey(entityVO, DATA_KEY_2);
        createdMetadata = metadataDao.loadMetadataItem(entityVO);
        Assert.assertEquals(ID_1, createdMetadata.getEntity().getEntityId());
        Assert.assertEquals(CLASS_1, createdMetadata.getEntity().getEntityClass());
        Assert.assertEquals(data, createdMetadata.getData());

        // update metadata
        data.clear();
        data.put(DATA_KEY_2, new PipeConfValue(DATA_TYPE_2, DATA_VALUE_2));
        metadataToSave.setData(data);
        metadataDao.uploadMetadataItem(metadataToSave);
        createdMetadata = metadataDao.loadMetadataItem(entityVO);
        Assert.assertEquals(ID_1, createdMetadata.getEntity().getEntityId());
        Assert.assertEquals(CLASS_1, createdMetadata.getEntity().getEntityClass());
        Assert.assertEquals(data, createdMetadata.getData());

        // delete several keys from metadata
        data.put(DATA_KEY_1, new PipeConfValue(DATA_TYPE_1, DATA_VALUE_1));
        data.put(DATA_KEY_3, new PipeConfValue(DATA_TYPE_2, DATA_VALUE_1));
        metadataToSave.setData(data);
        Set<String> keysToDelete = new HashSet<>();
        keysToDelete.add(DATA_KEY_1);
        keysToDelete.add(DATA_KEY_3);
        keysToDelete.add(NON_EXISTING_DATA_KEY);
        metadataDao.deleteMetadataItemKeys(metadataToSave, keysToDelete);
        createdMetadata = metadataDao.loadMetadataItem(entityVO);
        Assert.assertEquals(ID_1, createdMetadata.getEntity().getEntityId());
        Assert.assertEquals(CLASS_1, createdMetadata.getEntity().getEntityClass());
        data.remove(DATA_KEY_1);
        data.remove(DATA_KEY_3);
        Assert.assertEquals(data, createdMetadata.getData());

        // delete metadata
        metadataDao.deleteMetadataItem(entityVO);
        createdMetadata = metadataDao.loadMetadataItem(entityVO);
        Assert.assertNull(createdMetadata);
    }

    @Test
    public void testListMetadata() {
        EntityVO entity1 = new EntityVO(ID_1, CLASS_1);
        Map<String, PipeConfValue> data1 = new HashMap<>();
        data1.put(DATA_KEY_1, new PipeConfValue(DATA_TYPE_1, DATA_VALUE_1));
        MetadataEntry metadataToSave1 = new MetadataEntry();
        metadataToSave1.setEntity(entity1);
        metadataToSave1.setData(data1);
        EntityVO entity2 = new EntityVO(ID_2, CLASS_2);
        Map<String, PipeConfValue> data2 = new HashMap<>();
        data2.put(DATA_KEY_2, new PipeConfValue(DATA_TYPE_2, DATA_VALUE_2));
        MetadataEntry metadataToSave2 = new MetadataEntry();
        metadataToSave2.setEntity(entity2);
        metadataToSave2.setData(data2);

        metadataDao.registerMetadataItem(metadataToSave1);
        metadataDao.registerMetadataItem(metadataToSave2);
        List<EntityVO> entities = new ArrayList<>();
        entities.add(entity1);
        entities.add(entity2);
        List<MetadataEntry> metadataEntries = new ArrayList<>();
        metadataEntries.add(metadataToSave1);
        metadataEntries.add(metadataToSave2);
        List<MetadataEntry> createdMetadata = metadataDao.loadMetadataItems(entities);
        Assert.assertEquals(metadataEntries, createdMetadata);

        metadataDao.deleteMetadataItem(entity1);
        metadataDao.deleteMetadataItem(entity2);
        createdMetadata = metadataDao.loadMetadataItems(entities);
        Assert.assertNull(createdMetadata);
    }

    @Test
    public void testMetadataWithIssues() {
        Pair<EntityVO, MetadataEntry> pipelinePair = createItems("pipeline", AclClass.PIPELINE, true);
        Pair<EntityVO, MetadataEntry> folderPair = createItems("folder", AclClass.FOLDER, true);
        Pair<EntityVO, MetadataEntry> configurationPair = createItems("configuration", AclClass.CONFIGURATION, false);
        List<MetadataEntryWithIssuesCount> expectedMetadataEntries = new ArrayList<>();
        expectedMetadataEntries.add(
                convertMetadataWithIssues(pipelinePair.getLeft(), pipelinePair.getRight().getData(), 1));
        expectedMetadataEntries.add(
                convertMetadataWithIssues(folderPair.getLeft(), folderPair.getRight().getData(), 1));
        expectedMetadataEntries.add(convertMetadataWithIssues(configurationPair.getLeft(), null, 1));
        List<MetadataEntryWithIssuesCount> actualMetadataEntries = metadataDao.loadMetadataItemsWithIssues(
                Stream.of(pipelinePair.getLeft(), folderPair.getLeft(), configurationPair.getLeft())
                        .collect(Collectors.toList()));
        Map<EntityVO, MetadataEntry> expectedMap = expectedMetadataEntries.stream()
                .collect(Collectors.toMap(MetadataEntry::getEntity, Function.identity()));
        actualMetadataEntries.forEach(m -> Assert.assertEquals(expectedMap.get(m.getEntity()), m));
    }

    @Test
    public void testShouldSearchMetadataByClassAndKeyValuePair() {
        EntityVO entityVO = new EntityVO(ID_1, CLASS_1);
        Map<String, PipeConfValue> data = new HashMap<>();
        data.put(DATA_KEY_1, new PipeConfValue(null, DATA_VALUE_1));
        data.put(DATA_KEY_2, new PipeConfValue(DATA_TYPE_2, DATA_VALUE_1));
        MetadataEntry metadataToSave = new MetadataEntry();
        metadataToSave.setEntity(entityVO);
        metadataToSave.setData(data);
        metadataDao.registerMetadataItem(metadataToSave);

        EntityVO entityVO2 = new EntityVO(ID_1, CLASS_2);
        metadataToSave.setEntity(entityVO2);
        metadataToSave.setData(data);
        metadataDao.registerMetadataItem(metadataToSave);

        List<EntityVO> loadedEntities = metadataDao.searchMetadataByClassAndKeyValue(CLASS_1,
                Collections.singletonMap(DATA_KEY_1, new PipeConfValue(null, DATA_VALUE_1)));
        Assert.assertEquals(1, loadedEntities.size());
        Assert.assertEquals(entityVO, loadedEntities.get(0));
    }

    @Test
    public void testLoadUniqueAttributes() {
        createMetadataForEntity(ID_1, CLASS_1, DATA_KEY_1, DATA_TYPE_1, DATA_VALUE_1);
        createMetadataForEntity(ID_2, CLASS_1, DATA_KEY_1, DATA_TYPE_1, DATA_VALUE_1);
        createMetadataForEntity(ID_3, CLASS_1, DATA_KEY_1, DATA_TYPE_1, DATA_VALUE_2);
        createMetadataForEntity(ID_4, CLASS_1, DATA_KEY_2, DATA_TYPE_1, DATA_VALUE_2);
        final List<String> uniqueAttributeValues =
            metadataDao.loadUniqueValuesFromEntitiesAttribute(CLASS_1, DATA_KEY_1);
        Assert.assertEquals(2, uniqueAttributeValues.size());
        assertThat(uniqueAttributeValues, CoreMatchers.hasItems(DATA_VALUE_1, DATA_VALUE_2));
        final List<String> emptyValues =
            metadataDao.loadUniqueValuesFromEntitiesAttribute(CLASS_1, NON_EXISTING_DATA_KEY);
        Assert.assertEquals(0, emptyValues.size());
    }

    @Test
    public void testBuildFullMetadataDict() {
        createMetadataForEntity(ID_1, CLASS_1, DATA_KEY_1, DATA_TYPE_1, DATA_VALUE_1);
        createMetadataForEntity(ID_2, CLASS_1, DATA_KEY_1, DATA_TYPE_1, DATA_VALUE_2);
        createMetadataForEntityWithSensitiveValue(ID_3, CLASS_1, DATA_KEY_2, DATA_TYPE_1, DATA_VALUE_1, DATA_VALUE_2);
        final Map<String, List<String>> metadataDict = metadataDao.buildFullMetadataDict().stream()
            .collect(Collectors.toMap(CategoricalAttribute::getKey,
                attribute -> attribute.getValues().stream()
                                          .map(CategoricalAttributeValue::getValue)
                                          .collect(Collectors.toList())));
        Assert.assertEquals(2, metadataDict.size());
        assertThat(metadataDict.get(DATA_KEY_1), CoreMatchers.is(Arrays.asList(DATA_VALUE_1, DATA_VALUE_2)));
        assertThat(metadataDict.get(DATA_KEY_2), CoreMatchers.is(Collections.singletonList(DATA_VALUE_1)));
        Assert.assertFalse(metadataDict.containsKey(SENSITIVE_DATA_KEY));
        final MetadataEntry metadataEntryWithSensitiveField = metadataDao.loadMetadataItem(new EntityVO(ID_3, CLASS_1));
        final Map<String, PipeConfValue> sensitiveEntryData = metadataEntryWithSensitiveField.getData();
        Assert.assertEquals(2, sensitiveEntryData.size());
        assertMetadataValue(sensitiveEntryData.get(DATA_KEY_2), DATA_TYPE_1, DATA_VALUE_1);
        assertMetadataValue(sensitiveEntryData.get(SENSITIVE_DATA_KEY), DATA_TYPE_1, DATA_VALUE_2);
    }

    @Test
    public void shouldReturnMetadataKeysForClass() {
        createMetadataForEntity(ID_1, CLASS_1, DATA_KEY_1, DATA_TYPE_1, DATA_VALUE_1);
        createMetadataForEntity(ID_2, CLASS_1, DATA_KEY_2, DATA_TYPE_1, DATA_VALUE_2);
        final Set<String> metadataKeys = metadataDao.loadMetadataKeys(CLASS_1);
        assertThat(metadataKeys, containsInAnyOrder(DATA_KEY_1, DATA_KEY_2));
    }

    private void assertMetadataValue(final PipeConfValue sensitivePipeConfValue, final String dataType,
                                     final String dataValue) {
        Assert.assertEquals(sensitivePipeConfValue.getType(), dataType);
        Assert.assertEquals(sensitivePipeConfValue.getValue(), dataValue);
    }

    private void createMetadataForEntity(final Long entityId, final AclClass entityClass,
                                         final String dataKey, final String dataType, final String dataValue) {
        createMetadataForEntityWithSensitiveValue(entityId, entityClass, dataKey, dataType, dataValue, null);
    }

    private void createMetadataForEntityWithSensitiveValue(final Long entityId, final AclClass entityClass,
                                                           final String dataKey, final String dataType,
                                                           final String dataValue, final String sensitiveDataValue) {
        final EntityVO entityVO = new EntityVO(entityId, entityClass);
        final Map<String, PipeConfValue> data = new HashMap<>();
        data.put(dataKey, new PipeConfValue(dataType, dataValue));
        if (sensitiveDataValue != null) {
            data.put(SENSITIVE_DATA_KEY, new PipeConfValue(dataType, sensitiveDataValue));
        }
        final MetadataEntry metadataToSave = new MetadataEntry();
        metadataToSave.setEntity(entityVO);
        metadataToSave.setData(data);
        metadataDao.registerMetadataItem(metadataToSave);
    }

    private Issue getIssue(String name, EntityVO entity) {
        Issue issue = new Issue();
        issue.setName(name);
        issue.setAuthor(AUTHOR);
        issue.setEntity(entity);
        issue.setText(TEXT);
        return issue;
    }

    private Pair<EntityVO, MetadataEntry> createItems(String issueName, AclClass aclClass, boolean createMetadata) {
        EntityVO entity = new EntityVO(1L, aclClass);
        Issue issue = getIssue(issueName, entity);
        issueDao.createIssue(issue);
        MetadataEntry metadataEntry = new MetadataEntry();
        if (createMetadata) {
            metadataEntry.setEntity(entity);
            metadataEntry.setData(new HashMap<>());
            metadataDao.registerMetadataItem(metadataEntry);
        }
        return new ImmutablePair<>(entity, metadataEntry);
    }

    private static MetadataEntryWithIssuesCount convertMetadataWithIssues(EntityVO entityVO,
                                                                          Map<String, PipeConfValue> data,
                                                                          long issuesCount) {
        MetadataEntryWithIssuesCount metadataEntryWithIssuesCount = new MetadataEntryWithIssuesCount();
        metadataEntryWithIssuesCount.setIssuesCount(issuesCount);
        metadataEntryWithIssuesCount.setData(data);
        metadataEntryWithIssuesCount.setEntity(entityVO);
        return metadataEntryWithIssuesCount;
    }
}
