/*
 * Copyright 2024 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.dao;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public interface MetadataTagsUtils {
    Pattern FILTER_PATTERN = Pattern.compile("@FILTER@");

    static String buildTagsFilterClause(final String query, final Map<String, List<String>> tags) {
        return FILTER_PATTERN.matcher(query).replaceFirst(tags.entrySet().stream()
                .map(entry -> tagFilterQuery(entry.getKey(), entry.getValue()))
                .collect(Collectors.joining(" AND ")));
    }

    static String tagFilterQuery(final String key, final List<String> values) {
        return String.format("(m.data->'%s'->>'value' IN (%s))", key,
                values.stream()
                        .map(value -> String.format("'%s'", value))
                        .collect(Collectors.joining(", ")));
    }
}
