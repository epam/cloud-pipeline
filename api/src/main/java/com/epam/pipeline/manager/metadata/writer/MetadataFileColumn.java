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


import com.epam.pipeline.entity.metadata.MetadataEntity;
import com.epam.pipeline.entity.metadata.PipeConfValue;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
@EqualsAndHashCode
public class MetadataFileColumn {

    private static final String EMPTY_STRING = "";

    private final String name;
    private final String field;
    private final MetadataFileColumnType type;

    public static MetadataFileColumn from(final String metadataField, final String fieldType) {
        return from(metadataField, fieldType, MetadataFileColumnType.from(fieldType));
    }

    @SuppressWarnings("PMD.ShortMethodName")
    public static MetadataFileColumn id(final String metadataClass) {
        return from(metadataClass, "ignored", MetadataFileColumnType.ID);
    }

    private static MetadataFileColumn from(final String metadataField,
                                           final String fieldType,
                                           final MetadataFileColumnType type) {
        return new MetadataFileColumn(type.retrieveColumnName(metadataField, fieldType), metadataField, type);
    }

    public String getValueOf(final MetadataEntity entity) {
        return Optional.of(entity)
                .map(MetadataEntity::getData)
                .map(data -> data.get(field))
                .map(PipeConfValue::getValue)
                .orElse(EMPTY_STRING);
    }

}