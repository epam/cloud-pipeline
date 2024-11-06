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

package com.epam.pipeline.manager.search;

import com.epam.pipeline.entity.search.SearchDocument;
import com.epam.pipeline.entity.search.SearchTemplateExportColumnData;
import com.epam.pipeline.entity.search.SearchTemplateExportSheetMapping;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SearchTemplateMappingResolver {

    public SearchTemplateMappingResolver() {
        // no-op
    }

    public SearchTemplateExportColumnData prepareColumnData(final List<SearchDocument> documents,
                                                            final SearchTemplateExportSheetMapping mapping) {
        return SearchTemplateExportColumnData.builder()
                .columnValues(resolveValues(documents, mapping))
                .columnStringIndex(mapping.getColumn().toUpperCase(Locale.ROOT))
                .rowStartIndex(to0based(mapping.getStartRow()))
                .build();
    }

    private String[] resolveValues(final List<SearchDocument> documents,
                                  final SearchTemplateExportSheetMapping columnMapping) {
        final String value = columnMapping.getValue();
        final boolean keepUnresolved = columnMapping.isKeepUnresolved();
        final Set<String> placeholders = findPlaceholders(value);
        final List<String> values = ListUtils.emptyIfNull(documents).stream()
                .map(doc -> resolveValue(value, placeholders, doc, keepUnresolved))
                .collect(Collectors.toList());
        if (columnMapping.isUnique()) {
            return values.stream()
                    .filter(StringUtils::isNotBlank)
                    .distinct()
                    .toArray(String[]::new);
        }
        return values.toArray(new String[0]);
    }

    private Set<String> findPlaceholders(final String mappingValue) {
        final Set<String> matchList = new HashSet<>();
        final Pattern regex = Pattern.compile("\\{(.*?)\\}");
        final Matcher regexMatcher = regex.matcher(mappingValue);

        while (regexMatcher.find()) {
            matchList.add(regexMatcher.group(1));
        }

        return matchList;
    }

    private String resolveValue(final String mappingsValue, final Set<String> placeholders,
                                final SearchDocument document, final boolean keepUnresolved) {
        final Map<String, String> attributes = MapUtils.emptyIfNull(document.getAttributes());
        String resolvedValue = mappingsValue;
        for (String placeholder : placeholders) {
            final String value = findValue(placeholder, document, attributes, keepUnresolved);
            if (Objects.nonNull(value)) {
                resolvedValue = resolvedValue.replace("{" + placeholder + "}", value);
            }
        }
        return resolvedValue;
    }

    private int to0based(final Integer startIndex) {
        return Objects.isNull(startIndex) ? 0 : startIndex - 1;
    }

    private String findValueInAttributes(final SearchSourceFields field, final Map<String, String> attributes) {
        return attributes.get(field.getFieldName());
    }

    private String findValueInDocument(final SearchSourceFields field, final String documentValue,
                                       final Map<String, String> attributes) {
        return StringUtils.isNotBlank(documentValue)
                ? documentValue
                : findValueInAttributes(field, attributes);
    }

    private String findValue(final String placeholder, final SearchDocument document,
                             final Map<String, String> attributes, final boolean keepUnresolved) {
        final String value = findValue(placeholder, document, attributes);
        if (Objects.nonNull(value)) {
            return value;
        }
        return keepUnresolved ? null : StringUtils.EMPTY;
    }

    private String findValue(final String placeholder, final SearchDocument document,
                             final Map<String, String> attributes) {
        if (SearchSourceFields.NAME.getPrettyName().equals(placeholder)) {
            return findValueInDocument(SearchSourceFields.NAME, document.getName(), attributes);
        }

        if (SearchSourceFields.DESCRIPTION.getPrettyName().equals(placeholder)) {
            return findValueInDocument(SearchSourceFields.DESCRIPTION, document.getDescription(), attributes);
        }

        if (SearchSourceFields.OWNER.getPrettyName().equals(placeholder)) {
            return findValueInDocument(SearchSourceFields.OWNER, document.getOwner(), attributes);
        }

        if (SearchSourceFields.LAST_MODIFIED.getPrettyName().equals(placeholder)) {
            return findValueInAttributes(SearchSourceFields.LAST_MODIFIED, attributes);
        }

        if (SearchSourceFields.SIZE.getPrettyName().equals(placeholder)) {
            return findValueInAttributes(SearchSourceFields.SIZE, attributes);
        }

        if (SearchSourceFields.PATH.getPrettyName().equals(placeholder)) {
            return findValueInAttributes(SearchSourceFields.PATH, attributes);
        }

        if (SearchSourceFields.CLOUD_PATH.getPrettyName().equals(placeholder)) {
            return findValueInAttributes(SearchSourceFields.CLOUD_PATH, attributes);
        }

        if (SearchSourceFields.MOUNT_PATH.getPrettyName().equals(placeholder)) {
            return findValueInAttributes(SearchSourceFields.CLOUD_PATH, attributes);
        }

        if (SearchSourceFields.START_DATE.getPrettyName().equals(placeholder)) {
            return findValueInAttributes(SearchSourceFields.START_DATE, attributes);
        }

        if (SearchSourceFields.END_DATE.getPrettyName().equals(placeholder)) {
            return findValueInAttributes(SearchSourceFields.END_DATE, attributes);
        }

        return attributes.get(placeholder);
    }
}
