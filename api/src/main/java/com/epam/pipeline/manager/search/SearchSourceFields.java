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
    ID("id", null),
    PARENT_ID("parentId", null),
    NAME("name",  "Name"),
    DESCRIPTION("description", "Description"),
    OWNER("ownerUserName", "Owner"),
    PATH("path", "Path"),
    CLOUD_PATH("cloud_path", "Cloud path"),
    MOUNT_PATH("mount_path", "Mount path"),
    TEXT("text", null),
    START_DATE("startDate", "Started"),
    END_DATE("endDate", "Finished"),
    IMAGE("image", null),
    SIZE("size", "Size"),
    LAST_MODIFIED("lastModified", "Changed");

    public static final Set<String> ADDITIONAL_FIELDS = setOf(
            PATH, CLOUD_PATH, MOUNT_PATH, TEXT, START_DATE,
            END_DATE, IMAGE, SIZE, LAST_MODIFIED
    );
    public static final Set<String> DATE_FIELDS = setOf(START_DATE, END_DATE, LAST_MODIFIED);
    public static final Set<String> NUMERIC_FIELDS = setOf(SIZE);

    private static Set<String> setOf(final SearchSourceFields... fields) {
        return Collections.unmodifiableSet(Stream.of(fields)
                .map(SearchSourceFields::getFieldName)
                .collect(Collectors.toSet()));
    }

    private final String fieldName;
    private final String prettyName;
}
