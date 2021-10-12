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

package com.epam.pipeline.entity.datastorage.nfs;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@AllArgsConstructor
@Getter
public enum NFSObserverEventType {
    CREATED("c"),
    MODIFIED("m"),
    MOVED_FROM("mf"),
    MOVED_TO("mt"),
    DELETED("d");

    private final String eventCode;

    private static final Map<String, NFSObserverEventType> CODES = Stream.of(NFSObserverEventType.values())
        .collect(Collectors.toMap(NFSObserverEventType::getEventCode, Function.identity()));

    public static NFSObserverEventType fromCode(final String code) {
        final NFSObserverEventType event = CODES.get(code);
        if (event == null) {
            throw new IllegalArgumentException("Unsupported event code.");
        }
        return event;
    }
}
