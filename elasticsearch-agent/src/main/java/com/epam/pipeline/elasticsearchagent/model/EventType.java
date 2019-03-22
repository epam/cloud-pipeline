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
package com.epam.pipeline.elasticsearchagent.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.AbstractMap.SimpleEntry;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Getter
@RequiredArgsConstructor
public enum EventType {

    INSERT('I'), UPDATE('U'), DELETE('D');

    private final char code;

    private static final Map<Character, EventType> CODES = Stream.of(
            new SimpleEntry<>(INSERT.code, INSERT),
            new SimpleEntry<>(UPDATE.code, UPDATE),
            new SimpleEntry<>(DELETE.code, DELETE))
            .collect(Collectors.toMap(SimpleEntry::getKey, SimpleEntry::getValue));

    public static EventType fromCode(char code) {
        EventType eventType = CODES.get(code);
        if (eventType == null) {
            throw new IllegalArgumentException("Unsupported event code.");
        }
        return eventType;
    }
}
