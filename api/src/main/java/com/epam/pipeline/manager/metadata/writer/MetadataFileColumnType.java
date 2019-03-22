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

import com.epam.pipeline.manager.metadata.parser.EntityTypeField;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.function.BiFunction;
import java.util.function.Predicate;

@RequiredArgsConstructor
public enum MetadataFileColumnType {
    DEFAULT(type -> type.equals("string"),
        (field, type) -> field),
    PATH(type -> type.equals("Path"),
        (field, type) -> String.format("%s:Path", field)),
    REFERENCE(EntityTypeField::isReferenceType,
        (field, type) -> String.format("%s:%s", field, type)),
    MEMBERSHIP(EntityTypeField::isArrayType,
        (field, type) -> String.format("membership:%s:%s:ID", field, unwrapString("Array[", type, "]"))),
    ID(anythingElse -> true,
        (field, type) -> field + ":ID");

    private final Predicate<String> typeMatcher;
    private final BiFunction<String, String, String> fieldAndTypeToColumn;

    public static MetadataFileColumnType from(final String fieldType) {
        return Arrays.stream(values())
                .filter(possibleType -> possibleType.typeMatcher.test(fieldType))
                .findFirst()
                .orElseThrow(() -> new RuntimeException(
                        String.format("%s is not a valid metadata entity column type.", fieldType)));
    }

    public String retrieveColumnName(final String metadataField, final String metadataFieldType) {
        return fieldAndTypeToColumn.apply(metadataField, metadataFieldType);
    }

    private static String unwrapString(final String prefixToRemove,
                                       final String stringToBeUnwrapped,
                                       final String suffixToRemove) {
        return StringUtils.removeEnd(StringUtils.removeStart(stringToBeUnwrapped, prefixToRemove),
                suffixToRemove);
    }
}
