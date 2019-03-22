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

package com.epam.pipeline.manager.metadata.writer;

import com.epam.pipeline.entity.metadata.MetadataClass;
import com.epam.pipeline.entity.metadata.MetadataEntity;
import com.epam.pipeline.entity.metadata.PipeConfValue;
import org.junit.Assert;
import org.junit.Test;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toMap;

@SuppressWarnings("PMD.AvoidDuplicateLiterals")
public class MetadataWriterTest {

    private static final String STRING_TYPE = "string";
    private static final String PATH_TYPE = "Path";
    private static final String SAMPLE = "Sample";
    private static final String SAMPLE_SET = "SampleSet";
    private static final String SAMPLES_ARRAY = "Array[Sample]";
    private static final String PARTICIPANTS_ARRAY = "Array[Participant]";
    private static final String PAIRS_ARRAY = "Array[Pair]";
    private static final String ID_1 = "id1";
    private static final String ID_2 = "id2";
    private static final String ID_3 = "id3";

    private MetadataWriter getMetadataWriter(final Writer writer) {
        return getMetadataWriter(MetadataFileFormat.CSV, writer);
    }

    private MetadataWriter getMetadataWriter(final MetadataFileFormat format, final Writer writer) {
        return new MetadataWriter(format, writer);
    }

    @Test
    public void writeEntitiesShouldReturnCommaSeparatedFileIfCsvFormatIsRequested() {
        final List<MetadataEntity> entities = Collections.singletonList(
                entity(SAMPLE, ID_1, dataOf(column(STRING_TYPE, "column", "value"))));
        final StringWriter stringWriter = new StringWriter();

        getMetadataWriter(MetadataFileFormat.CSV, stringWriter).writeEntities(SAMPLE, entities);

        assertCsvEquals(
                "Sample:ID,column\n" +
                "id1,value\n",
                stringWriter.toString());
    }

    @Test
    public void writeEntitiesShouldReturnTabSeparatedFileIfTsvFormatIsRequested() {
        final List<MetadataEntity> entities = Collections.singletonList(
                entity(SAMPLE, ID_1, dataOf(column(STRING_TYPE, "column", "value"))));
        final StringWriter stringWriter = new StringWriter();

        getMetadataWriter(MetadataFileFormat.TSV, stringWriter).writeEntities(SAMPLE, entities);

        assertCsvEquals(
                "Sample:ID\tcolumn\n" +
                "id1\tvalue\n",
                stringWriter.toString());
    }

    @Test
    public void writeEntitiesShouldFormatIdColumnType() {
        final List<MetadataEntity> entities = Collections.singletonList(
                entity(SAMPLE, ID_1, Collections.emptyMap()));
        final StringWriter stringWriter = new StringWriter();

        getMetadataWriter(stringWriter).writeEntities(SAMPLE, entities);

        assertCsvEquals(
                "Sample:ID\n" +
                "id1\n",
                stringWriter.toString());
    }

    @Test
    public void writeEntitiesShouldFormatStringColumnType() {
        final List<MetadataEntity> entities = Collections.singletonList(
                entity(SAMPLE, ID_1, dataOf(column(PATH_TYPE, "pathColumn", "s3://path/to/file"))));
        final StringWriter stringWriter = new StringWriter();

        getMetadataWriter(stringWriter).writeEntities(SAMPLE, entities);

        assertCsvEquals(
                "Sample:ID,pathColumn:Path\n" +
                "id1,s3://path/to/file\n",
                stringWriter.toString());
    }

    @Test
    public void writeEntitiesShouldFormatPathColumnType() {
        final List<MetadataEntity> entities = Collections.singletonList(
                entity(SAMPLE, ID_1, dataOf(column(PATH_TYPE, "pathColumn", "s3://path/to/file"))));
        final StringWriter stringWriter = new StringWriter();

        getMetadataWriter(stringWriter).writeEntities(SAMPLE, entities);

        assertCsvEquals(
                "Sample:ID,pathColumn:Path\n" +
                "id1,s3://path/to/file\n",
                stringWriter.toString());
    }

    @Test
    public void writeEntitiesShouldFormatReferenceColumnType() {
        final List<MetadataEntity> entities = Collections.singletonList(
                entity(SAMPLE, ID_1, dataOf(column("Sample:ID", "sample_a", "s1"))));
        final StringWriter stringWriter = new StringWriter();

        getMetadataWriter(stringWriter).writeEntities(SAMPLE, entities);

        assertCsvEquals(
                "Sample:ID,sample_a:Sample:ID\n" +
                "id1,s1\n",
                stringWriter.toString());
    }

    @Test
    public void writeEntitiesShouldFormatMembershipColumnType() {
        final List<MetadataEntity> entities = Collections.singletonList(
                entity(SAMPLE_SET, ID_1, dataOf(column(SAMPLES_ARRAY, "samples", "[\"s1\",\"s2\"]"))));
        final StringWriter stringWriter = new StringWriter();

        getMetadataWriter(stringWriter).writeEntities(SAMPLE_SET, entities);

        assertCsvEquals(
                "SampleSet:ID,membership:samples:Sample:ID\n" +
                "id1,s1\n" +
                "id1,s2\n",
                stringWriter.toString());
    }

    @Test
    public void writeEntitiesShouldFormatSeveralMembershipColumns() {
        final List<MetadataEntity> entities = Collections.singletonList(
                entity(SAMPLE_SET, ID_1, dataOf(column(PARTICIPANTS_ARRAY, "participants", "[\"p1\",\"p2\"]"),
                                                column(PAIRS_ARRAY, "pairs", "[\"pair1\",\"pair2\"]"),
                                                column(SAMPLES_ARRAY, "samples", "[\"s1\",\"s2\"]"))));
        final StringWriter stringWriter = new StringWriter();

        getMetadataWriter(stringWriter).writeEntities(SAMPLE_SET, entities);

        assertCsvEquals(
                "SampleSet:ID," +
                        "membership:pairs:Pair:ID," +
                        "membership:participants:Participant:ID," +
                        "membership:samples:Sample:ID\n" +
                "id1,pair1,p1,s1\n" +
                "id1,pair1,p2,s1\n" +
                "id1,pair2,p1,s1\n" +
                "id1,pair2,p2,s1\n" +
                "id1,pair1,p1,s2\n" +
                "id1,pair1,p2,s2\n" +
                "id1,pair2,p1,s2\n" +
                "id1,pair2,p2,s2\n",
                stringWriter.toString());
    }

    @Test
    public void writeEntitiesShouldFormatSeveralMembershipColumnsEvenIfMembershipArraysContainsDuplications() {
        final List<MetadataEntity> entities = Collections.singletonList(
                entity(SAMPLE_SET, ID_1, dataOf(column(PARTICIPANTS_ARRAY, "participants", "[\"p1\",\"p1\",\"p2\"]"),
                                                column(SAMPLES_ARRAY, "samples", "[\"s1\",\"s2\",\"s2\"]"))));
        final StringWriter stringWriter = new StringWriter();

        getMetadataWriter(stringWriter).writeEntities(SAMPLE_SET, entities);

        assertCsvEquals(
                "SampleSet:ID," +
                        "membership:participants:Participant:ID," +
                        "membership:samples:Sample:ID\n" +
                        "id1,p1,s1\n" +
                        "id1,p1,s2\n" +
                        "id1,p2,s1\n" +
                        "id1,p2,s2\n",
                stringWriter.toString());
    }

    @Test
    public void writeEntitiesShouldFormatSeveralEntities() {
        final List<MetadataEntity> entities = Arrays.asList(
                entity(SAMPLE, ID_1, dataOf(column(STRING_TYPE, "a column", "first a value"),
                                            column(STRING_TYPE, "b column", "first b value"))),
                entity(SAMPLE, ID_2, dataOf(column(STRING_TYPE, "a column", "second a value"),
                                            column(STRING_TYPE, "b column", "second b value"))));
        final StringWriter stringWriter = new StringWriter();

        getMetadataWriter(stringWriter).writeEntities(SAMPLE, entities);

        assertCsvEquals(
                "Sample:ID,a column,b column\n" +
                "id1,first a value,first b value\n" +
                "id2,second a value,second b value\n",
                stringWriter.toString());
    }

    @Test
    public void writeEntitiesShouldFormatSeveralEntitiesWithMembershipColumns() {
        final List<MetadataEntity> entities = Arrays.asList(
                entity(SAMPLE_SET, ID_1, dataOf(column(SAMPLES_ARRAY, "samples", "[\"s1\",\"s2\",\"s3\"]"))),
                entity(SAMPLE_SET, ID_2, dataOf(column(SAMPLES_ARRAY, "samples", "[\"s1\",\"s2\",\"s3\"]"))));
        final StringWriter stringWriter = new StringWriter();

        getMetadataWriter(stringWriter).writeEntities(SAMPLE, entities);

        assertCsvEquals(
                "Sample:ID,membership:samples:Sample:ID\n" +
                "id1,s1\n" +
                "id1,s2\n" +
                "id1,s3\n" +
                "id2,s1\n" +
                "id2,s2\n" +
                "id2,s3\n",
                stringWriter.toString());
    }

    @Test
    public void writeEntitiesShouldFormatSeveralEntitiesWithSeveralMembershipColumns() {
        final List<MetadataEntity> entities = Arrays.asList(
                entity(SAMPLE_SET, ID_1, dataOf(column(PARTICIPANTS_ARRAY, "participants", "[\"p1\",\"p2\"]"),
                                                column(SAMPLES_ARRAY, "samples", "[\"s1\",\"s2\"]"))),
                entity(SAMPLE_SET, ID_2, dataOf(column(PARTICIPANTS_ARRAY, "participants", "[\"p3\",\"p4\"]"),
                                                column(SAMPLES_ARRAY, "samples", "[\"s3\",\"s4\"]"))));
        final StringWriter stringWriter = new StringWriter();

        getMetadataWriter(stringWriter).writeEntities(SAMPLE_SET, entities);

        assertCsvEquals(
                "SampleSet:ID,membership:participants:Participant:ID,membership:samples:Sample:ID\n" +
                "id1,p1,s1\n" +
                "id1,p2,s1\n" +
                "id1,p1,s2\n" +
                "id1,p2,s2\n" +
                "id2,p3,s3\n" +
                "id2,p4,s3\n" +
                "id2,p3,s4\n" +
                "id2,p4,s4\n",
                stringWriter.toString());
    }

    @Test
    public void writeEntitiesShouldOnlyWrapInQuotesFieldsWhichContainSeparator() {
        final List<MetadataEntity> entities = Collections.singletonList(
                entity(SAMPLE, ID_1,
                        dataOf(column(STRING_TYPE, "column name with , separator", "value with , separator"))));
        final StringWriter stringWriter = new StringWriter();

        getMetadataWriter(stringWriter).writeEntities(SAMPLE, entities);

        assertCsvEquals(
                "Sample:ID,\"column name with , separator\"\n" +
                "id1,\"value with , separator\"\n",
                stringWriter.toString());
    }

    @Test
    public void writeEntitiesShouldFormatColumnValuesInCorrectOrder() {
        final List<MetadataEntity> entities = Arrays.asList(
                entity(SAMPLE, ID_1, dataOf(column(STRING_TYPE, "string1", "s11"),
                                            column(STRING_TYPE, "string2", "s12"),
                                            column(STRING_TYPE, "string3", "s13"))),
                entity(SAMPLE, ID_2, dataOf(column(STRING_TYPE, "string3", "s23"),
                                            column(STRING_TYPE, "string1", "s21"),
                                            column(STRING_TYPE, "string2", "s22"))));
        final StringWriter stringWriter = new StringWriter();

        getMetadataWriter(stringWriter).writeEntities(SAMPLE, entities);

        assertCsvEquals(
                "Sample:ID,string1,string2,string3\n" +
                "id1,s11,s12,s13\n" +
                "id2,s21,s22,s23\n",
                stringWriter.toString());
    }

    @Test
    public void writeEntitiesShouldFormatCsvWithMissingColumnsInSeveralEntities() {
        final List<MetadataEntity> entities = Arrays.asList(
                entity(SAMPLE, ID_1, dataOf(column(STRING_TYPE, "string1", "s1"))),
                entity(SAMPLE, ID_2, dataOf(column(STRING_TYPE, "string2", "s2"))),
                entity(SAMPLE, ID_3, dataOf(column(STRING_TYPE, "string3", "s3"))));
        final StringWriter stringWriter = new StringWriter();

        getMetadataWriter(stringWriter).writeEntities(SAMPLE, entities);

        assertCsvEquals(
                "Sample:ID,string1,string2,string3\n" +
                "id1,s1,,\n" +
                "id2,,s2,\n" +
                "id3,,,s3\n",
                stringWriter.toString());
    }

    @Test
    public void writeEntitiesShouldFormatCsvIfSomeOfEntitiesDoesNotHaveAnyColumnValuesButOthersHave() {
        final List<MetadataEntity> entities = Arrays.asList(
                entity(SAMPLE, ID_1, dataOf(column(STRING_TYPE, "string1", "s1"))),
                entity(SAMPLE, ID_2, Collections.emptyMap()),
                entity(SAMPLE, ID_3, null));
        final StringWriter stringWriter = new StringWriter();

        getMetadataWriter(stringWriter).writeEntities(SAMPLE, entities);

        assertCsvEquals(
                "Sample:ID,string1\n" +
                "id1,s1\n" +
                "id2,\n" +
                "id3,\n",
                stringWriter.toString());
    }

    @Test
    public void writeEntitiesShouldFormatCsvWithoutAnyColumnsButIdColumn() {
        final List<MetadataEntity> entities = Arrays.asList(
                entity(SAMPLE, ID_1, Collections.emptyMap()),
                entity(SAMPLE, ID_2, null));
        final StringWriter stringWriter = new StringWriter();

        getMetadataWriter(stringWriter).writeEntities(SAMPLE, entities);

        assertCsvEquals(
                "Sample:ID\n" +
                "id1\n" +
                "id2\n",
                stringWriter.toString());
    }

    private Map.Entry<String, PipeConfValue> column(final String columnType, final String column, final String value) {
        return new AbstractMap.SimpleEntry<>(column, new PipeConfValue(columnType, value));
    }

    @SafeVarargs
    private final Map<String, PipeConfValue> dataOf(final Map.Entry<String, PipeConfValue>... entries) {
        return Arrays.stream(entries).collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private MetadataEntity entity(final String entityClass, final String id, final Map<String, PipeConfValue> data) {
        final MetadataEntity metadataEntity = new MetadataEntity();
        metadataEntity.setExternalId(id);
        metadataEntity.setData(data);
        final MetadataClass metadataClass = new MetadataClass();
        metadataClass.setName(entityClass);
        metadataEntity.setClassEntity(metadataClass);
        return metadataEntity;
    }

    private void assertCsvEquals(final String expectedCsv, final String actualCsv) {
        final String[] expectedRecords = expectedCsv.split("\n");
        final String[] actualRecords = actualCsv.split("\n");
        final String expectedHeader = expectedRecords[0];
        final String actualHeader = actualRecords[0];
        Assert.assertEquals("Csv header mismatch (ignoring order)", expectedHeader, actualHeader);
        final List<String> expectedLines = Arrays.stream(expectedRecords).skip(1).collect(Collectors.toList());
        final List<String> actualLines = Arrays.stream(actualRecords).skip(1).collect(Collectors.toList());
        Assert.assertTrue(String.format("Csv content mismatch:\nExpected:%s\nActual:%s", expectedLines, actualLines),
                expectedLines.size() == actualLines.size()
                        && expectedLines.containsAll(actualLines)
                        && actualLines.containsAll(expectedLines)
        );
    }
}
