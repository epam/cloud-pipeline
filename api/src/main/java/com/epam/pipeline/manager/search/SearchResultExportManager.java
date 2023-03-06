/*
 * Copyright 2022 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.config.Constants;
import com.epam.pipeline.controller.vo.search.FacetedSearchExportRequest;
import com.epam.pipeline.controller.vo.search.FacetedSearchExportVO;
import com.epam.pipeline.entity.search.FacetedSearchResult;
import com.epam.pipeline.entity.search.SearchDocument;
import com.epam.pipeline.exception.search.SearchException;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.opencsv.CSVWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.epam.pipeline.manager.search.SearchSourceFields.CLOUD_PATH;
import static com.epam.pipeline.manager.search.SearchSourceFields.LAST_MODIFIED;
import static com.epam.pipeline.manager.search.SearchSourceFields.MOUNT_PATH;
import static com.epam.pipeline.manager.search.SearchSourceFields.NAME;
import static com.epam.pipeline.manager.search.SearchSourceFields.OWNER;
import static com.epam.pipeline.manager.search.SearchSourceFields.PATH;
import static com.epam.pipeline.manager.search.SearchSourceFields.SIZE;
import static java.lang.String.format;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchResultExportManager {

    private static final DateTimeFormatter EXPORT_DATE_FORMATTER = DateTimeFormatter.ofPattern(
            Constants.EXPORT_DATE_TIME_FORMAT);
    private static final DateTimeFormatter ISO_DATE_FORMATTER = DateTimeFormatter.ofPattern(
            Constants.FMT_ISO_LOCAL_DATE);
    private static final char DELIMITER = ',';

    private final PreferenceManager preferenceManager;

    public byte[] export(final FacetedSearchExportRequest searchExportRequest, final FacetedSearchResult searchResult) {
        final FacetedSearchExportVO facetedSearchExportVO = Optional.ofNullable(
                searchExportRequest.getFacetedSearchExportVO())
                .orElseGet(FacetedSearchExportVO::new);

        final Character delimiter = Optional.ofNullable(facetedSearchExportVO.getDelimiter())
                .map(del -> del.charAt(0))
                .orElse(DELIMITER);

        final String searchResultDisplayNameTag = preferenceManager.getPreference(
                SystemPreferences.FACETED_FILTER_DISPLAY_NAME_TAG);
        final List<String> effectiveMetadataFields =
                ListUtils.emptyIfNull(searchExportRequest.getFacetedSearchRequest().getMetadataFields())
                        .stream().filter(field -> !field.equalsIgnoreCase(searchResultDisplayNameTag))
                        .collect(Collectors.toList());

        try (StringWriter writer = new StringWriter(); CSVWriter csvWriter = new CSVWriter(writer, delimiter)) {
            final String[] header = buildCsvHeader(effectiveMetadataFields, facetedSearchExportVO);
            csvWriter.writeNext(header, false);
            CollectionUtils.emptyIfNull(searchResult.getDocuments()).stream()
                    .map(
                        searchDocument -> createItem(
                            searchDocument, facetedSearchExportVO, effectiveMetadataFields,
                            searchResultDisplayNameTag
                        )
                    ).forEach(item -> csvWriter.writeNext(item, false));
            return writer.toString().getBytes(Charset.defaultCharset());
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new SearchException(e.getMessage(), e);
        }
    }

    private String[] createItem(final SearchDocument searchDocument, final FacetedSearchExportVO facetedSearchExportVO,
                                final List<String> metadataFields, final String searchResultDisplayNameTag) {
        final List<String> result = new ArrayList<>();
        final Map<String, String> attributes = MapUtils.emptyIfNull(searchDocument.getAttributes());
        if (facetedSearchExportVO.isIncludeName()) {
            final String fileName = getItem(searchDocument.getName());
            result.add(
                    StringUtils.isNotBlank(searchResultDisplayNameTag)
                            ? getItem(attributes.getOrDefault(searchResultDisplayNameTag, fileName))
                            : fileName
            );
        }
        if (facetedSearchExportVO.isIncludeChanged()) {
            final String changedData = Optional.ofNullable(attributes.get(LAST_MODIFIED.getFieldName()))
                    .map(field -> Optional.of(LocalDateTime.parse(getItem(field), ISO_DATE_FORMATTER))
                            .map(date -> date.format(EXPORT_DATE_FORMATTER))
                            .orElse(StringUtils.EMPTY))
                    .orElse(StringUtils.EMPTY);
            result.add(changedData);
        }
        if (facetedSearchExportVO.isIncludeSize()) {
            final String docSize = Optional.ofNullable(attributes.get(SIZE.getFieldName()))
                    .map(this::getItem)
                    .orElse(StringUtils.EMPTY);
            result.add(docSize);
        }
        final List<String> metadataValues = ListUtils.emptyIfNull(metadataFields)
                .stream()
                .map(field -> attributes.getOrDefault(field, StringUtils.EMPTY))
                .collect(Collectors.toList());
        result.addAll(metadataValues);
        if (facetedSearchExportVO.isIncludeOwner()) {
            result.add(getItem(searchDocument.getOwner()));
        }
        if (facetedSearchExportVO.isIncludePath()) {
            final String docPath = Optional.ofNullable(attributes.get(PATH.getFieldName()))
                    .map(this::getItem)
                    .orElse(StringUtils.EMPTY);
            result.add(docPath);
        }
        if (facetedSearchExportVO.isIncludeCloudPath()) {
            final String docCloudPath = Optional.ofNullable(attributes.get(CLOUD_PATH.getFieldName()))
                    .map(this::getItem)
                    .orElse(StringUtils.EMPTY);
            result.add(docCloudPath);
        }
        if (facetedSearchExportVO.isIncludeMountPath()) {
            final String docMountPath = Optional.ofNullable(attributes.get(MOUNT_PATH.getFieldName()))
                    .map(this::getItem)
                    .orElse(StringUtils.EMPTY);
            result.add(docMountPath);
        }
        return result.toArray(new String[0]);
    }

    private String[] buildCsvHeader(final List<String> metadataFields,
                                    final FacetedSearchExportVO facetedSearchExportVO) {
        final List<String> header = new ArrayList<>();
        if (facetedSearchExportVO.isIncludeName()) {
            header.add(columnHeaderMapper(NAME));
        }
        if (facetedSearchExportVO.isIncludeChanged()) {
            header.add(columnHeaderMapper(LAST_MODIFIED));
        }
        if (facetedSearchExportVO.isIncludeSize()) {
            header.add(columnHeaderMapper(SIZE));
        }
        header.addAll(ListUtils.emptyIfNull(metadataFields));
        if (facetedSearchExportVO.isIncludeOwner()) {
            header.add(columnHeaderMapper(OWNER));
        }
        if (facetedSearchExportVO.isIncludePath()) {
            header.add(columnHeaderMapper(PATH));
        }
        if (facetedSearchExportVO.isIncludeCloudPath()) {
            header.add(columnHeaderMapper(CLOUD_PATH));
        }
        if (facetedSearchExportVO.isIncludeMountPath()) {
            header.add(columnHeaderMapper(MOUNT_PATH));
        }
        return header.toArray(new String[0]);
    }

    private String getItem(final String field) {
        return StringUtils.isNotBlank(field) ? field : StringUtils.EMPTY;
    }

    private String columnHeaderMapper(final SearchSourceFields field) {
        switch (field) {
            case NAME: return "Name";
            case LAST_MODIFIED: return "Changed";
            case SIZE: return "Size";
            case OWNER: return "Owner";
            case PATH: return "Path";
            case CLOUD_PATH: return "Cloud path";
            case MOUNT_PATH: return "Mount path";
            default: throw new IllegalArgumentException(format("%s search source field is not supported", field));
        }
    }
}
