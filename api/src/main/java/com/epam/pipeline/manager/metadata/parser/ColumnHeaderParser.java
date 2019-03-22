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

import static com.epam.pipeline.manager.metadata.parser.EntityTypeField.NAME_DELIMITER;
import static com.epam.pipeline.manager.metadata.parser.EntityTypeField.PATH_TYPE;
import static com.epam.pipeline.manager.metadata.parser.EntityTypeField.REFERENCE_SUFFIX;

import com.epam.pipeline.exception.MetadataReadingException;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

@NoArgsConstructor
public class ColumnHeaderParser {

    private static final String MEMBERSHIP_PREFIX = "membership";
    private static final int CLASS_COLUMN_PARTS = 2;
    private static final int PATH_COLUMN_PARTS = 2;
    private static final int REFERENCE_COLUMN_PARTS = 3;
    private static final int MEMBERSHIP_COLUMN_PARTS = 4;

    public String readClassColumn(String name) {
        if (StringUtils.isBlank(name)) {
            throw new MetadataReadingException("Missing column name.");
        }
        String[] chunks = name.split(NAME_DELIMITER);
        if (chunks.length != CLASS_COLUMN_PARTS || !REFERENCE_SUFFIX.equals(chunks[1])) {
            throw new MetadataReadingException("First column must match format: 'Type:ID'.");
        }
        if (StringUtils.isBlank(chunks[0])) {
            throw new MetadataReadingException("Entity type shouldn't be empty");
        }
        return chunks[0];
    }

    public EntityTypeField readFieldColumn(String name) {
        if (StringUtils.isBlank(name)) {
            throw new MetadataReadingException("Missing column name.");
        }
        String[] chunks = name.split(NAME_DELIMITER);
        if (chunks.length == 1) {
            return new EntityTypeField(chunks[0]);
        } else if (chunks.length == PATH_COLUMN_PARTS && chunks[1].equals(PATH_TYPE)) {
            return new EntityTypeField(chunks[0], PATH_TYPE);
        } else if (chunks.length == REFERENCE_COLUMN_PARTS) {
            if (!REFERENCE_SUFFIX.equals(chunks[chunks.length - 1])) {
                throw new MetadataReadingException("Reference field column must match format: 'Name:Type:ID'.");
            }
            return new EntityTypeField(chunks[0], chunks[1], true, false);
        } else if (chunks.length == MEMBERSHIP_COLUMN_PARTS && MEMBERSHIP_PREFIX.equals(chunks[0])) {
            if (!REFERENCE_SUFFIX.equals(chunks[chunks.length - 1])) {
                throw new MetadataReadingException(
                        "Membership field column must match format: 'membership:Name:Type:ID'.");
            }
            return new EntityTypeField(chunks[1], chunks[2], true, true);
        }
        throw new MetadataReadingException("Unknown field column format");
    }
}
