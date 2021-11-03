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

package com.epam.pipeline.manager.search;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Getter
@RequiredArgsConstructor
public enum SearchSourceFields {
    ID("id"),
    PARENT_ID("parentId"),
    NAME("name"),
    DESCRIPTION("description"),
    OWNER("ownerUserName"),
    PATH("path"),
    TEXT("text"),
    START_DATE("startDate"),
    END_DATE("endDate"),
    IMAGE("image"),
    SIZE("size"),
    LAST_MODIFIED("lastModified");

    public static final Set<String> ADDITIONAL_FIELDS = Collections.unmodifiableSet(Stream.of(
            PATH, TEXT, START_DATE, END_DATE, IMAGE, SIZE, LAST_MODIFIED)
            .map(SearchSourceFields::getFieldName)
            .collect(Collectors.toSet()));

    public static final Set<String> DATE_FIELDS = Collections.unmodifiableSet(Stream.of(
            START_DATE, END_DATE, LAST_MODIFIED)
            .map(SearchSourceFields::getFieldName)
            .collect(Collectors.toSet()));

    private final String fieldName;

}
