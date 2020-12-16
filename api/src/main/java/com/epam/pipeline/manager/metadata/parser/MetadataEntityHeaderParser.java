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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Optional;

import com.epam.pipeline.exception.MetadataReadingException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;

@RequiredArgsConstructor
public class MetadataEntityHeaderParser {
    private final String delimiter;
    private final String fallbackMetadataClass;
    private final ColumnHeaderParser headerParser = new ColumnHeaderParser();

    public MetadataHeader readHeader(InputStream stream) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
            String line = reader.readLine();
            if (StringUtils.isBlank(line)) {
                throw new MetadataReadingException("Input file header should have at least one column.");
            }
            String[] columns = line.split(delimiter);
            Optional<String> columnMetadataClass = headerParser.readClassColumn(columns[0]);
            MetadataHeader header = columnMetadataClass
                    .map(metadataClass -> new MetadataHeader(metadataClass, true))
                    .orElseGet(() -> new MetadataHeader(fallbackMetadataClass, false));
            for (int i = header.isClassColumnPresent() ? 1 : 0; i < columns.length; i++) {
                header.addField(i, headerParser.readFieldColumn(columns[i]));
            }
            return header;
        } catch (IOException e) {
            throw new MetadataReadingException(e.getMessage(), e);
        }
    }
}
