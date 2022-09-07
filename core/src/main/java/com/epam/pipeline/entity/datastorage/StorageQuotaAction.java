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
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.RequiredArgsConstructor;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RequiredArgsConstructor
public enum StorageQuotaAction {
    EMAIL("EMAIL"), READ_ONLY("READONLY"), DISABLE("DISABLE_MOUNT"), UNKNOWN(null);

    private final String code;

    private static final Map<String, StorageQuotaAction> ID_MAP = Stream.of(StorageQuotaAction.values())
        .collect(Collectors.toMap(StorageQuotaAction::getCode, Function.identity()));

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static StorageQuotaAction fromCode(final String code) {
        return ID_MAP.getOrDefault(code, UNKNOWN);
    }
}
