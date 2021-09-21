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

import static com.epam.pipeline.manager.utils.MetadataParsingUtils.CSV_DELIMITER;
import static org.junit.Assert.assertEquals;

import java.io.IOException;

import com.epam.pipeline.exception.MetadataReadingException;
import com.google.common.io.ByteSource;
import org.junit.Test;

public class MetadataEntityHeaderParserTest {
    private static final String TAB_HEADER = "Sample:ID\tname\ttype\tpatient:Participant:ID\tmembership:pairs:Pair:ID";
    private static final String CSV_HEADER = "Sample:ID,name,type,patient:Participant:ID,membership:pairs:Pair:ID";
    private static final String OMITTED_CLASS_HEADER = "name,type,patient:Participant:ID,membership:pairs:Pair:ID";
    private static final String INVALID_FIELD_HEADER = 
            "name,type:INVALID_TYPE,patient:Participant:ID,membership:pairs:Pair:ID";
    private static final String BLANK_HEADER = " ";
    private static final String CLASS_NAME = "Sample";
    private static final String FALLBACK_CLASS_NAME = "FallbackSample";
    private final EntityTypeField nameField = new EntityTypeField("name", EntityTypeField.DEFAULT_TYPE, false, false);
    private final EntityTypeField typeField = new EntityTypeField("type", EntityTypeField.DEFAULT_TYPE, false, false);
    private final EntityTypeField participantField = new EntityTypeField("patient", "Participant", true, false);
    private final EntityTypeField pairsField = new EntityTypeField("pairs", "Pair", true, true);

    @Test
    public void testReadHeader() throws IOException {
        MetadataHeader expected = getMetadataHeader(CLASS_NAME, true);

        MetadataHeader tabHeader = new MetadataEntityHeaderParser("\t", FALLBACK_CLASS_NAME)
                .readHeader(ByteSource.wrap(TAB_HEADER.getBytes()).openStream());
        assertEquals(expected, tabHeader);

        MetadataHeader csvHeader = new MetadataEntityHeaderParser(CSV_DELIMITER, FALLBACK_CLASS_NAME)
                .readHeader(ByteSource.wrap(CSV_HEADER.getBytes()).openStream());
        assertEquals(expected, csvHeader);
    }

    @Test
    public void testReadOmittedClassColumn() throws IOException {
        final MetadataHeader expectedHeader = getMetadataHeader(FALLBACK_CLASS_NAME, false);
        
        MetadataHeader omittedClassHeader = new MetadataEntityHeaderParser(CSV_DELIMITER, FALLBACK_CLASS_NAME)
                .readHeader(ByteSource.wrap(OMITTED_CLASS_HEADER.getBytes()).openStream());
        
        assertEquals(expectedHeader, omittedClassHeader);
    }

    @Test(expected = MetadataReadingException.class)
    public void testReadInvalidFieldsColumn() throws IOException {
        new MetadataEntityHeaderParser(CSV_DELIMITER, FALLBACK_CLASS_NAME)
                .readHeader(ByteSource.wrap(INVALID_FIELD_HEADER.getBytes()).openStream());
    }

    @Test(expected = MetadataReadingException.class)
    public void testReadBlankHeader() throws IOException {
        new MetadataEntityHeaderParser(CSV_DELIMITER, FALLBACK_CLASS_NAME)
                .readHeader(ByteSource.wrap(BLANK_HEADER.getBytes()).openStream());
    }

    private MetadataHeader getMetadataHeader(final String className, final boolean classColumnPresent) {
        int initialIndex = classColumnPresent ? 1 : 0;
        MetadataHeader expected = new MetadataHeader(className, classColumnPresent);
        expected.addField(initialIndex, nameField);
        expected.addField(initialIndex + 1, typeField);
        expected.addField(initialIndex + 2, participantField);
        expected.addField(initialIndex + 3, pairsField);
        return expected;
    }
}
