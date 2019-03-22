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

import com.epam.pipeline.exception.MetadataReadingException;
import org.apache.commons.lang3.StringUtils;

public class MetadataEntityHeaderParser {
    private String delimiter;
    private ColumnHeaderParser headerParser = new ColumnHeaderParser();

    public MetadataEntityHeaderParser(String delimiter) {
        this.delimiter = delimiter;
    }

    public MetadataHeader readHeader(InputStream stream) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
            String line = reader.readLine();
            if (StringUtils.isBlank(line)) {
                throw new MetadataReadingException("Input file doesn't have expected header.");
            }
            String[] columns = line.split(delimiter);
            if (columns.length < 1) {
                throw new MetadataReadingException("At least one column should be present.");
            }
            MetadataHeader header = new MetadataHeader(headerParser.readClassColumn(columns[0]));
            for (int i = 1; i < columns.length; i++) {
                header.addField(i, headerParser.readFieldColumn(columns[i]));
            }
            return header;
        } catch (IOException e) {
            throw new MetadataReadingException(e.getMessage(), e);
        }
    }
}
