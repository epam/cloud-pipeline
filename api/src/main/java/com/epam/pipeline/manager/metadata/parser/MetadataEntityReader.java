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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.epam.pipeline.entity.metadata.MetadataClass;
import com.epam.pipeline.entity.pipeline.Folder;
import com.google.common.io.CharStreams;
import com.google.common.io.LineProcessor;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class MetadataEntityReader {

    private final String delimiter;
    private final Folder parent;
    private final MetadataClass metadataClass;

    public MetadataParsingResult readData(InputStream inputStream, Map<Integer, EntityTypeField> fields,
                                          boolean classColumnPresent)
            throws IOException {
        LineProcessor<MetadataParsingResult> processor =
                new EntityLineProcessor(delimiter, parent, metadataClass, fields, classColumnPresent);
        try (Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
            return CharStreams.readLines(reader, processor);
        }
    }
}
