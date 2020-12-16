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

package com.epam.pipeline.manager.metadata.parser;

import static com.epam.pipeline.manager.metadata.parser.MetadataFileBuilder.SAMPLE1_ID;
import static com.epam.pipeline.manager.metadata.parser.MetadataFileBuilder.prepareInputData;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.epam.pipeline.entity.metadata.MetadataClass;
import com.epam.pipeline.entity.metadata.MetadataEntity;
import com.epam.pipeline.entity.metadata.PipeConfValue;
import com.epam.pipeline.entity.pipeline.Folder;
import com.epam.pipeline.manager.utils.MetadataParsingUtils;
import org.apache.commons.lang.StringUtils;
import org.junit.Test;

public class MetadataEntityReaderTest {

    private static final List<String> DATA_WITH_MISSING_IDS = Arrays.asList(
            "Sample:ID${d}name\n", 
            "s1${d}Sample1\n",
            "${d}Sample2\n",
            "${d}Sample3\n");

    private static final List<String> DATA_WITH_OMITTED_CLASS_COLUMN = Arrays.asList(
            "name\n",
            "Sample1\n",
            "Sample2\n",
            "Sample3\n");
    private static final String NAME_FIELD_NAME = "name";
    private static final String TYPE_FIELD_NAME = "type";
    private static final String PAIRS_FIELD_NAME = "pairs";
    private static final String PATIENT_FIELD_NAME = "patient";

    private Folder parent = new Folder(1L);
    private MetadataClass metadataClass = new MetadataClass(1L, MetadataFileBuilder.SAMPLE_CLASS_NAME);

    @Test
    public void readData() throws Exception {
        Map<Integer, EntityTypeField> fields = getFields();
        MetadataParsingResult expectedResult = getExpectedResult();
        InputStream tabData = prepareInputData(MetadataParsingUtils.TAB_DELIMITER);
        MetadataParsingResult tabEntities =
                new MetadataEntityReader(MetadataParsingUtils.TAB_DELIMITER, parent, metadataClass)
                        .readData(tabData, fields, true);
        compareResults(expectedResult, tabEntities);

        InputStream csvData = prepareInputData(MetadataParsingUtils.CSV_DELIMITER);
        MetadataParsingResult csvEntities =
                new MetadataEntityReader(MetadataParsingUtils.CSV_DELIMITER, parent, metadataClass)
                        .readData(csvData, fields, true);
        compareResults(expectedResult, csvEntities);
    }

    @Test
    public void readDataWithMissingIds() throws IOException {
        final InputStream data = prepareInputData(MetadataParsingUtils.TAB_DELIMITER, DATA_WITH_MISSING_IDS);
        final MetadataParsingResult entities =
                new MetadataEntityReader(MetadataParsingUtils.TAB_DELIMITER, parent, metadataClass)
                        .readData(data, getNameField(), true);

        final Map<String, MetadataEntity> parsedEntitiesMap = entities.getEntities();
        final List<String> parsedEntityGeneratedIds = parsedEntitiesMap.keySet().stream()
                .filter(it -> !it.equals(SAMPLE1_ID))
                .collect(Collectors.toList());
        assertEquals(3, parsedEntitiesMap.size());
        assertEquals(2, parsedEntityGeneratedIds.size());
        for (final String parsedEntityGeneratedId : parsedEntityGeneratedIds) {
            final MetadataEntity parsedEntity = parsedEntitiesMap.get(parsedEntityGeneratedId);
            assertTrue(StringUtils.isNotBlank(parsedEntityGeneratedId));
            assertEquals(parsedEntityGeneratedId, parsedEntity.getExternalId());
        }
    }

    @Test
    public void readDataWithOmittedClassColumn() throws IOException {
        final InputStream data = prepareInputData(MetadataParsingUtils.TAB_DELIMITER, DATA_WITH_OMITTED_CLASS_COLUMN);
        final MetadataParsingResult entities =
                new MetadataEntityReader(MetadataParsingUtils.TAB_DELIMITER, parent, metadataClass)
                        .readData(data, getNameFieldWithoutClassColumn(), false);

        assertEquals(metadataClass, entities.getMetadataClass());
        final Map<String, MetadataEntity> parsedEntitiesMap = entities.getEntities();
        assertEquals(3, parsedEntitiesMap.size());
        for (final String parsedEntityGeneratedId : parsedEntitiesMap.keySet()) {
            final MetadataEntity parsedEntity = parsedEntitiesMap.get(parsedEntityGeneratedId);
            assertTrue(StringUtils.isNotBlank(parsedEntityGeneratedId));
            assertEquals(parsedEntityGeneratedId, parsedEntity.getExternalId());
            assertTrue(StringUtils.isNotBlank(parsedEntity.getData().get(NAME_FIELD_NAME).getValue()));
        }
    }

    private void compareResults(MetadataParsingResult expected, MetadataParsingResult actual) {
        assertEquals(expected.getMetadataClass(), actual.getMetadataClass());
        assertEquals(expected.getReferences(), actual.getReferences());
        Map<String, MetadataEntity> actualEntities = actual.getEntities();
        assertEquals(expected.getEntities().size(), actualEntities.size());
        expected.getEntities().forEach((externalId, entity) -> {
            assertTrue(actualEntities.containsKey(externalId));
            MetadataEntity actualEntity = actualEntities.get(externalId);
            assertEquals(entity.getExternalId(), actualEntity.getExternalId());
            assertEquals(entity.getName(), actualEntity.getName());
        });
    }

    private Map<Integer, EntityTypeField> getNameField() {
        MetadataHeader fields = new MetadataHeader(MetadataFileBuilder.SAMPLE_CLASS_NAME, true);
        fields.addField(1, new EntityTypeField(NAME_FIELD_NAME, EntityTypeField.DEFAULT_TYPE, false, false));
        return fields.getFields();
    }

    private Map<Integer, EntityTypeField> getNameFieldWithoutClassColumn() {
        MetadataHeader fields = new MetadataHeader(MetadataFileBuilder.SAMPLE_CLASS_NAME, true);
        fields.addField(0, new EntityTypeField(NAME_FIELD_NAME, EntityTypeField.DEFAULT_TYPE, false, false));
        return fields.getFields();
    }

    private Map<Integer, EntityTypeField> getFields() {
        MetadataHeader fields = new MetadataHeader(MetadataFileBuilder.SAMPLE_CLASS_NAME, true);
        fields.addField(1, 
                new EntityTypeField(NAME_FIELD_NAME, EntityTypeField.DEFAULT_TYPE, false, false));
        fields.addField(2, 
                new EntityTypeField(TYPE_FIELD_NAME, EntityTypeField.DEFAULT_TYPE, false, false));
        fields.addField(3, 
                new EntityTypeField(PATIENT_FIELD_NAME, MetadataFileBuilder.PARTICIPANT_CLASS_NAME, true, false));
        fields.addField(4, 
                new EntityTypeField(PAIRS_FIELD_NAME, MetadataFileBuilder.PAIR_CLASS_NAME, true, true));
        return fields.getFields();
    }

    public MetadataParsingResult getExpectedResult() {
        Map<String, Set<String>> references = new HashMap<>();
        references.put("Participant", new HashSet<>(Arrays.asList("p1", "p2")));
        references.put("Pair", new HashSet<>(Arrays.asList("set1", "set2")));
        Map<String, MetadataEntity> results = getExpectedEntities().stream().collect(
                Collectors.toMap(MetadataEntity::getExternalId, Function.identity()));
        return new MetadataParsingResult(metadataClass, references, results);
    }


    public List<MetadataEntity> getExpectedEntities() {
        MetadataEntity sample1 = createSample("s1", "Sample1", "DNA", "p1", "[\"set1\",\"set2\"]");
        MetadataEntity sample2 = createSample("s2", "Sample2", "RNA", "p2", "[\"set1\"]");
        return Arrays.asList(sample1, sample2);
    }

    private MetadataEntity createSample(String id, String name, String type, String patient, String pairs) {
        MetadataEntity sample = new MetadataEntity();
        sample.setClassEntity(metadataClass);
        sample.setParent(parent);
        sample.setExternalId(id);
        Map<String, PipeConfValue> data = new HashMap<>();
        data.put(NAME_FIELD_NAME, new PipeConfValue(EntityTypeField.DEFAULT_TYPE, name));
        data.put(TYPE_FIELD_NAME, new PipeConfValue(EntityTypeField.DEFAULT_TYPE, type));
        data.put(PATIENT_FIELD_NAME, new PipeConfValue("Participant:ID", patient));
        data.put(PAIRS_FIELD_NAME, new PipeConfValue("Array[Pair]", pairs));
        sample.setData(data);
        return sample;
    }


}
