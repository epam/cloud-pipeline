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

import static org.junit.Assert.*;

import com.epam.pipeline.exception.MetadataReadingException;
import org.junit.Test;

import java.util.Optional;

public class ColumnHeaderParserTest {
    private static final String VALID_CLASS_COLUMN = "Sample:ID";
    private static final String INVALID_CLASS_COLUMN = "Sample";
    private static final String CLASS_NAME = "Sample";

    private static final String SIMPLE_ATTRIBUTE = "Tissue";
    private static final String REFERENCE_ATTRIBUTE = "patient:Participant:ID";
    private static final String MEMBERSHIP_ATTRIBUTE = "membership:samples:SampleSet:ID";
    private static final String INVALID_ATTRIBUTE = "patient:Participant";
    private static final String PATH_ATTRIBUTE = "read1:Path";

    private ColumnHeaderParser parser = new ColumnHeaderParser();

    @Test
    public void readClassColumn() {
        final Optional<String> parsedClassColumn = parser.readClassColumn(VALID_CLASS_COLUMN);
        assertTrue(parsedClassColumn.isPresent());
        assertEquals(CLASS_NAME, parsedClassColumn.get());
    }

    @Test
    public void readInvalidClassColumn() {
        final Optional<String> parsedClassColumn = parser.readClassColumn(INVALID_CLASS_COLUMN);
        assertFalse(parsedClassColumn.isPresent());
    }

    @Test
    public void readFieldColumn() throws Exception {
        EntityTypeField expectedSimple = new EntityTypeField(SIMPLE_ATTRIBUTE, EntityTypeField.DEFAULT_TYPE,
                false, false);
        EntityTypeField simpleField = parser.readFieldColumn(SIMPLE_ATTRIBUTE);
        assertEquals(expectedSimple, simpleField);

        EntityTypeField expectedReference = new EntityTypeField("patient", "Participant",
                true, false);
        EntityTypeField referenceField = parser.readFieldColumn(REFERENCE_ATTRIBUTE);
        assertEquals(expectedReference, referenceField);

        EntityTypeField expectedMembership = new EntityTypeField("samples", "SampleSet",
                true, true);
        EntityTypeField membershipField = parser.readFieldColumn(MEMBERSHIP_ATTRIBUTE);
        assertEquals(expectedMembership, membershipField);

        EntityTypeField expectedPath = new EntityTypeField("read1", "Path",
                false, false);
        EntityTypeField pathField = parser.readFieldColumn(PATH_ATTRIBUTE);
        assertEquals(expectedPath, pathField);
    }

    @Test(expected = MetadataReadingException.class)
    public void readInvalidColumn() throws Exception {
        parser.readFieldColumn(INVALID_ATTRIBUTE);
    }

}
