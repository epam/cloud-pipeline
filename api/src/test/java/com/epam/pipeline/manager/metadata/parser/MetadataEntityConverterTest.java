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

import com.epam.pipeline.entity.metadata.FireCloudClass;
import com.epam.pipeline.entity.metadata.MetadataClass;
import com.epam.pipeline.entity.metadata.MetadataEntity;
import com.epam.pipeline.entity.metadata.PipeConfValue;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static java.lang.System.lineSeparator;
import static org.junit.Assert.assertEquals;

public class MetadataEntityConverterTest {
    private static final String TEST_ARGUMENT1 = "age";
    private static final String TEST_VALUE_1 = "52";
    private static final String TEST_VALUE_2 = "46";
    private static final String TEST_ARGUMENT2 = "gender";
    private static final String STRING_TYPE = "string";
    private static final String PARTICIPANT_TYPE_NAME = "participant";
    private static final String PARTICIPANT_ATTRIBUTE_TYPE = "Participant:ID";
    private static final String PARTICIPANT_SET_TYPE_NAME = "ParticipantSet";
    private static final String SAMPLE_TYPE_NAME = "Sample";
    private static final String SAMPLE_ATTRIBUTE_TYPE = "Sample:ID";
    private static final String SAMPLE_SET_TYPE_NAME = "SampleSet";
    private static final String PAIR_TYPE_NAME = "Pair";
    private static final String PAIR_SET_TYPE_NAME = "PairSet";
    private static final String CASE_SAMPLE_ARGUMENT = "case_sample";
    private static final String CONTROL_SAMPLE_ARGUMENT = "control_sample";
    private static final String PARTICIPANT_ENTITY_ID1 = "HCC1143_WE";
    private static final String PARTICIPANT_ENTITY_ID2 = "HCC1954_100_gene";
    private static final String SAMPLE_ENTITY_ID1 = "HCC1143_Normal_WE";
    private static final String SAMPLE_ENTITY_ID2 = "HCC1143_Tumor_WE";
    private static final String PAIR_ENTITY_ID1 = "HCC1143_WE_pair";
    private static final String PAIR_ENTITY_ID2 = "HCC1954_100_gene_pair";

    private MetadataClass participantClass;
    private MetadataClass sampleClass;
    private MetadataClass pairClass;
    private MetadataClass participantSetClass;
    private MetadataClass sampleSetClass;
    private MetadataClass pairSetClass;

    @Before
    public void setup() {
        participantClass = getMetadataClass(PARTICIPANT_TYPE_NAME, FireCloudClass.PARTICIPANT);
        sampleClass = getMetadataClass(SAMPLE_TYPE_NAME, FireCloudClass.SAMPLE);
        pairClass = getMetadataClass(PAIR_TYPE_NAME, FireCloudClass.PAIR);
        participantSetClass = getMetadataClass(PARTICIPANT_SET_TYPE_NAME, FireCloudClass.PARTICIPANT_SET);
        sampleSetClass = getMetadataClass(SAMPLE_SET_TYPE_NAME, FireCloudClass.SAMPLE_SET);
        pairSetClass = getMetadataClass(PAIR_SET_TYPE_NAME, FireCloudClass.PAIR_SET);
    }

    @Test
    public void entitiesShouldBeConverted() {
        Map<String, PipeConfValue> participantData1 = new HashMap<>();
        participantData1.put(TEST_ARGUMENT1, new PipeConfValue(STRING_TYPE, TEST_VALUE_1));
        participantData1.put(TEST_ARGUMENT2, new PipeConfValue(STRING_TYPE, "F"));
        MetadataEntity participant1 = getEntity(participantClass, PARTICIPANT_ENTITY_ID1, participantData1);

        Map<String, PipeConfValue> participantData2 = new HashMap<>();
        participantData2.put(TEST_ARGUMENT1, new PipeConfValue(STRING_TYPE, TEST_VALUE_2));
        participantData2.put(TEST_ARGUMENT2, new PipeConfValue(STRING_TYPE, "M"));
        MetadataEntity participant2 = getEntity(participantClass, PARTICIPANT_ENTITY_ID2, participantData2);

        Map<String, PipeConfValue> sampleData1 = new HashMap<>();
        sampleData1.put(PARTICIPANT_TYPE_NAME, new PipeConfValue(PARTICIPANT_ATTRIBUTE_TYPE, PARTICIPANT_ENTITY_ID1));
        sampleData1.put(TEST_ARGUMENT1, new PipeConfValue(STRING_TYPE, TEST_VALUE_1));
        MetadataEntity sample1 = getEntity(sampleClass, SAMPLE_ENTITY_ID1, sampleData1);

        Map<String, PipeConfValue> sampleData2 = new HashMap<>();
        sampleData2.put(PARTICIPANT_TYPE_NAME, new PipeConfValue(PARTICIPANT_ATTRIBUTE_TYPE, PARTICIPANT_ENTITY_ID2));
        sampleData2.put(TEST_ARGUMENT1, new PipeConfValue(STRING_TYPE, TEST_VALUE_2));
        MetadataEntity sample2 = getEntity(sampleClass, SAMPLE_ENTITY_ID2, sampleData2);

        Map<String, PipeConfValue> pairData1 = new HashMap<>();
        pairData1.put(PARTICIPANT_TYPE_NAME, new PipeConfValue(PARTICIPANT_ATTRIBUTE_TYPE, PARTICIPANT_ENTITY_ID1));
        pairData1.put(CASE_SAMPLE_ARGUMENT, new PipeConfValue(SAMPLE_ATTRIBUTE_TYPE, SAMPLE_ENTITY_ID2));
        pairData1.put(CONTROL_SAMPLE_ARGUMENT, new PipeConfValue(SAMPLE_ATTRIBUTE_TYPE, SAMPLE_ENTITY_ID1));
        pairData1.put(TEST_ARGUMENT1, new PipeConfValue(STRING_TYPE, "52"));
        MetadataEntity pair1 = getEntity(pairClass, PAIR_ENTITY_ID1, pairData1);

        Map<String, PipeConfValue> pairData2 = new HashMap<>();
        pairData2.put(PARTICIPANT_TYPE_NAME, new PipeConfValue(PARTICIPANT_ATTRIBUTE_TYPE, PARTICIPANT_ENTITY_ID2));
        pairData2.put(CASE_SAMPLE_ARGUMENT, new PipeConfValue(SAMPLE_ATTRIBUTE_TYPE, "HCC1954_Tumor_100_gene"));
        pairData2.put(CONTROL_SAMPLE_ARGUMENT, new PipeConfValue(SAMPLE_ATTRIBUTE_TYPE, "HCC1954_Normal_100_gene"));
        pairData2.put(TEST_ARGUMENT1, new PipeConfValue(STRING_TYPE, TEST_VALUE_2));
        MetadataEntity pair2 = getEntity(pairClass, PAIR_ENTITY_ID2, pairData2);

        Map<String, PipeConfValue> participantSetData = new HashMap<>();
        participantSetData.put(TEST_ARGUMENT1, new PipeConfValue(STRING_TYPE, TEST_VALUE_2));
        participantSetData.put(PARTICIPANT_SET_TYPE_NAME,
                new PipeConfValue("Array[Participant]", "[\"HCC1143_WE\",\"HCC1954_100_gene\"]"));
        MetadataEntity participantSet = getEntity(participantSetClass, "HCC_participants", participantSetData);

        Map<String, PipeConfValue> sampleSetData = new HashMap<>();
        sampleSetData.put(TEST_ARGUMENT1, new PipeConfValue(STRING_TYPE, TEST_VALUE_2));
        sampleSetData.put(SAMPLE_SET_TYPE_NAME, new PipeConfValue("Array[Sample]",
                "[\"HCC1143_Tumor_WE\",\"HCC1143_Normal_WE\"]"));
        MetadataEntity sampleSet = getEntity(sampleSetClass, "HCC_samples", sampleSetData);

        Map<String, PipeConfValue> pairSetData = new HashMap<>();
        pairSetData.put(TEST_ARGUMENT1, new PipeConfValue(STRING_TYPE, TEST_VALUE_2));
        pairSetData.put(PAIR_SET_TYPE_NAME, new PipeConfValue("Array[Pair]",
                "[\"HCC1143_WE_pair\",\"HCC1954_100_gene_pair\"]"));
        MetadataEntity pairSet = getEntity(pairSetClass, "HCC_pairs", pairSetData);

        List<MetadataEntity> entities = new ArrayList<>();
        entities.add(participant1);
        entities.add(participant2);
        entities.add(sample1);
        entities.add(sample2);
        entities.add(pair1);
        entities.add(pair2);
        entities.add(participantSet);
        entities.add(sampleSet);
        entities.add(pairSet);

        Map<String, String> result = MetadataEntityConverter.convert(entities);
        assertEquals(9, result.size());
        assertEquals("entity:participant_id\tgender\tage" + lineSeparator() +
                     "HCC1143_WE\tF\t52" + lineSeparator() +
                     "HCC1954_100_gene\tM\t46",
                result.get("participant"));
        assertEquals("entity:sample_id\tparticipant\tage" + lineSeparator() +
                     "HCC1143_Normal_WE\tHCC1143_WE\t52" + lineSeparator() +
                     "HCC1143_Tumor_WE\tHCC1954_100_gene\t46", result.get("sample"));
        assertEquals("entity:pair_id\tcase_sample\tcontrol_sample\tparticipant\tage" + lineSeparator() +
                     "HCC1143_WE_pair\tHCC1143_Tumor_WE\tHCC1143_Normal_WE\tHCC1143_WE\t52" + lineSeparator() +
                     "HCC1954_100_gene_pair\tHCC1954_Tumor_100_gene\tHCC1954_Normal_100_gene\tHCC1954_100_gene\t46",
                result.get("pair"));
        assertEquals("entity:participant_set_id\tage" + lineSeparator() +
                     "HCC_participants\t46",
                result.get("participant_set_entity"));
        assertEquals("membership:participant_set_id\tparticipant" + lineSeparator() +
                     "HCC_participants\tHCC1143_WE" + lineSeparator() +
                     "HCC_participants\tHCC1954_100_gene", result.get("participant_set_membership"));
        assertEquals("entity:sample_set_id\tage" + lineSeparator() +
                     "HCC_samples\t46", result.get("sample_set_entity"));
        assertEquals("membership:sample_set_id\tsample" + lineSeparator() +
                     "HCC_samples\tHCC1143_Tumor_WE" + lineSeparator() +
                     "HCC_samples\tHCC1143_Normal_WE", result.get("sample_set_membership"));
        assertEquals("entity:pair_set_id\tage" + lineSeparator() +
                     "HCC_pairs\t46", result.get("pair_set_entity"));
        assertEquals("membership:pair_set_id\tpair" + lineSeparator() +
                     "HCC_pairs\tHCC1143_WE_pair" + lineSeparator() +
                     "HCC_pairs\tHCC1954_100_gene_pair", result.get("pair_set_membership"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldFailIfFireCloudClassIsNull() {
        MetadataClass entityClass = new MetadataClass();
        entityClass.setName(PARTICIPANT_TYPE_NAME);

        Map<String, PipeConfValue> participantData = new HashMap<>();
        participantData.put(TEST_ARGUMENT1, new PipeConfValue(STRING_TYPE, TEST_VALUE_1));
        MetadataEntity participant = getEntity(entityClass, PARTICIPANT_ENTITY_ID1, participantData);

        MetadataEntityConverter.convert(Collections.singletonList(participant));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldFailIfSampleWithoutParticipantAttribute() {
        Map<String, PipeConfValue> sampleData = new HashMap<>();
        sampleData.put(TEST_ARGUMENT1, new PipeConfValue(STRING_TYPE, TEST_VALUE_1));
        MetadataEntity sample = getEntity(sampleClass, SAMPLE_ENTITY_ID1, sampleData);

        MetadataEntityConverter.convert(Collections.singletonList(sample));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldFailIfPairWithoutParticipantAttribute() {
        Map<String, PipeConfValue> pairData = new HashMap<>();
        pairData.put(CASE_SAMPLE_ARGUMENT, new PipeConfValue(SAMPLE_ATTRIBUTE_TYPE, SAMPLE_ENTITY_ID2));
        pairData.put(CONTROL_SAMPLE_ARGUMENT, new PipeConfValue(SAMPLE_ATTRIBUTE_TYPE, SAMPLE_ENTITY_ID1));
        MetadataEntity pair = getEntity(pairClass, PAIR_ENTITY_ID1, pairData);

        MetadataEntityConverter.convert(Collections.singletonList(pair));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldFailIfPairWithoutControlSampleAttribute() {
        Map<String, PipeConfValue> pairData = new HashMap<>();
        pairData.put(PARTICIPANT_TYPE_NAME, new PipeConfValue(PARTICIPANT_TYPE_NAME, PARTICIPANT_ENTITY_ID1));
        pairData.put(CASE_SAMPLE_ARGUMENT, new PipeConfValue(SAMPLE_ATTRIBUTE_TYPE, SAMPLE_ENTITY_ID2));
        MetadataEntity pair = getEntity(pairClass, PAIR_ENTITY_ID1, pairData);

        MetadataEntityConverter.convert(Collections.singletonList(pair));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldFailIfPairWithoutCaseSampleAttribute() {
        Map<String, PipeConfValue> pairData = new HashMap<>();
        pairData.put(PARTICIPANT_TYPE_NAME, new PipeConfValue(PARTICIPANT_TYPE_NAME, PARTICIPANT_ENTITY_ID1));
        pairData.put(CONTROL_SAMPLE_ARGUMENT, new PipeConfValue(SAMPLE_ATTRIBUTE_TYPE, SAMPLE_ENTITY_ID1));
        MetadataEntity pair = getEntity(pairClass, PAIR_ENTITY_ID1, pairData);

        MetadataEntityConverter.convert(Collections.singletonList(pair));
    }

    @Test(expected = IllegalStateException.class)
    public void shouldFailWithInconsistentData() {
        Map<String, PipeConfValue> participantData1 = new HashMap<>();
        participantData1.put(TEST_ARGUMENT1, new PipeConfValue(STRING_TYPE, TEST_VALUE_1));
        MetadataEntity participant1 = getEntity(participantClass, PARTICIPANT_ENTITY_ID1, participantData1);

        Map<String, PipeConfValue> participantData2 = new HashMap<>();
        participantData2.put(TEST_ARGUMENT2, new PipeConfValue(STRING_TYPE, "M"));
        MetadataEntity participant2 = getEntity(participantClass, PARTICIPANT_ENTITY_ID2, participantData2);

        List<MetadataEntity> entities = new ArrayList<>();
        entities.add(participant1);
        entities.add(participant2);

        MetadataEntityConverter.convert(entities);
    }

    @Test(expected = IllegalStateException.class)
    public void shouldFailWithMissingColumnInData() {
        Map<String, PipeConfValue> participantData1 = new HashMap<>();
        participantData1.put(TEST_ARGUMENT1, new PipeConfValue(STRING_TYPE, TEST_VALUE_1));
        participantData1.put(TEST_ARGUMENT2, new PipeConfValue(STRING_TYPE, "F"));
        MetadataEntity participant1 = getEntity(participantClass, PARTICIPANT_ENTITY_ID1, participantData1);

        Map<String, PipeConfValue> participantData2 = new HashMap<>();
        participantData2.put(TEST_ARGUMENT2, new PipeConfValue(STRING_TYPE, "M"));
        MetadataEntity participant2 = getEntity(participantClass, PARTICIPANT_ENTITY_ID2, participantData2);

        List<MetadataEntity> entities = new ArrayList<>();
        entities.add(participant1);
        entities.add(participant2);

        MetadataEntityConverter.convert(entities);
    }

    @Test(expected = IllegalStateException.class)
    public void shouldFailIfParticipantSetWithoutParticipants() {
        Map<String, PipeConfValue> participantSetData = new HashMap<>();
        participantSetData.put(TEST_ARGUMENT1, new PipeConfValue(STRING_TYPE, TEST_VALUE_2));
        MetadataEntity participantSet = getEntity(participantSetClass, "HCC_participants", participantSetData);

        MetadataEntityConverter.convert(Collections.singletonList(participantSet));
    }

    @Test(expected = IllegalStateException.class)
    public void shouldFailIfSampleSetWithoutSamples() {
        Map<String, PipeConfValue> sampleSetData = new HashMap<>();
        sampleSetData.put(TEST_ARGUMENT1, new PipeConfValue(STRING_TYPE, TEST_VALUE_2));
        MetadataEntity sampleSet = getEntity(sampleSetClass, "HCC_samples", sampleSetData);

        MetadataEntityConverter.convert(Collections.singletonList(sampleSet));
    }

    @Test(expected = IllegalStateException.class)
    public void shouldFailIfPairSetWithoutPairs() {
        Map<String, PipeConfValue> pairSetData = new HashMap<>();
        pairSetData.put(TEST_ARGUMENT1, new PipeConfValue(STRING_TYPE, TEST_VALUE_2));
        MetadataEntity pairSet = getEntity(pairSetClass, "HCC_pairs", pairSetData);

        MetadataEntityConverter.convert(Collections.singletonList(pairSet));
    }

    private static MetadataEntity getEntity(MetadataClass entityClass, String externalName,
                                            Map<String, PipeConfValue> data) {
        MetadataEntity entity = new MetadataEntity();
        entity.setClassEntity(entityClass);
        entity.setExternalId(externalName);
        entity.setData(data);
        return entity;
    }

    private static MetadataClass getMetadataClass(String name, FireCloudClass type) {
        MetadataClass entityClass = new MetadataClass();
        entityClass.setName(name);
        entityClass.setFireCloudClassName(type);
        return entityClass;
    }
}