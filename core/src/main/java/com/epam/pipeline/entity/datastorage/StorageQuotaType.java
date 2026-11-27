/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.entity.datastorage;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RequiredArgsConstructor
@Getter
public enum StorageQuotaType {
    GIGABYTES("GB", "Gb"), PERCENTS("PERCENT", "%");

    private final String sourceString;
    private final String thresholdLabel;

    private static final Map<String, StorageQuotaType> ID_MAP = Stream.of(StorageQuotaType.values())
        .collect(Collectors.toMap(StorageQuotaType::getSourceString, Function.identity()));

    @JsonCreator
    public static StorageQuotaType fromString(final String string) {
        return ID_MAP.getOrDefault(string, null);
    }
}
