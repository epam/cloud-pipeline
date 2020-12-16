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

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.epam.pipeline.entity.metadata.MetadataClass;
import com.epam.pipeline.entity.metadata.MetadataEntity;
import com.epam.pipeline.entity.metadata.PipeConfValue;
import com.epam.pipeline.entity.pipeline.Folder;
import com.epam.pipeline.exception.MetadataReadingException;
import com.google.common.io.LineProcessor;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang.StringUtils;

@RequiredArgsConstructor
public class EntityLineProcessor implements LineProcessor<MetadataParsingResult> {

    private final String delimiter;
    private final Folder parent;
    private final MetadataClass metadataClass;
    private final Map<Integer, EntityTypeField> fields;
    private final boolean classColumnPresent;
    private final Map<String, MetadataEntity> currentResults = new HashMap<>();
    private final Map<String, Set<String>> referenceTypes = new HashMap<>();
    //externalId - field - array values
    private final Map<String, Map<String, Set<String>>> arrayValues = new HashMap<>();

    private boolean headerProcessed = false;

    @Override
    public boolean processLine(String line) {
        if (StringUtils.isBlank(line)) {
            return false;
        }
        if (!headerProcessed) {
            headerProcessed = true;
            return true;
        }
        String[] chunks = StringUtils.splitPreserveAllTokens(line, delimiter);
        if (chunks.length != fields.size() + (classColumnPresent ? 1 : 0)) {
            throw new MetadataReadingException("Size of line doesn't match header");
        }
        MetadataEntity entity = getOrCreateEntity(classColumnPresent ? chunks[0] : null);
        fields.forEach((index, field) -> {
            String value = chunks[index];
            if (StringUtils.isNotBlank(value)) {
                if (field.isReference()) {
                    referenceTypes.putIfAbsent(field.getType(), new HashSet<>());
                    referenceTypes.get(field.getType()).add(value);
                }
                if (field.isMultiValue()) {
                    arrayValues.putIfAbsent(entity.getExternalId(), new HashMap<>());
                }
                PipeConfValue previousValue = entity.getData().get(field.getName());
                Map<String, Set<String>> currentArrayValue = arrayValues.get(entity.getExternalId());
                entity.getData().put(field.getName(), getValue(field, value, previousValue, currentArrayValue));
            }
        });
        return true;
    }

    private PipeConfValue getValue(EntityTypeField field, String newValue, PipeConfValue previousValue,
                                   Map<String, Set<String>> currentArrayValue) {
        String value = field.isMultiValue() ? getArrayValue(field.getName(), newValue, currentArrayValue) :
                getSimpleValue(newValue, previousValue);
        return new PipeConfValue(field.getTypeString(), value);
    }

    private String getSimpleValue(String newValue, PipeConfValue previousValue) {
        return StringUtils.isBlank(newValue) ? previousValue.getValue() : newValue;
    }

    private String getArrayValue(String name, String newValue, Map<String, Set<String>> currentArrayValue) {
        currentArrayValue.putIfAbsent(name, new HashSet<>());
        Set<String> values = currentArrayValue.get(name);
        values.add(newValue);
        return buildArrayValue(values);
    }

    private String buildArrayValue(Collection<String> values) {
        return String.format("[%s]", values.stream()
                .sorted()
                .map(value -> String.format("\"%s\"", value))
                .collect(Collectors.joining(",")));
    }

    private MetadataEntity getOrCreateEntity(String id) {
        MetadataEntity entity = StringUtils.isBlank(id) 
                ? createEntity(UUID.randomUUID().toString())
                : Optional.ofNullable(currentResults.get(id)).orElseGet(() -> createEntity(id));
        currentResults.put(entity.getExternalId(), entity);
        return entity;
    }

    private MetadataEntity createEntity(String id) {
        MetadataEntity entity = new MetadataEntity();
        entity.setExternalId(id);
        entity.setParent(parent);
        entity.setClassEntity(metadataClass);
        entity.setData(new HashMap<>());
        return entity;
    }

    @Override
    public MetadataParsingResult getResult() {
        return new MetadataParsingResult(metadataClass, referenceTypes, currentResults);
    }
}
