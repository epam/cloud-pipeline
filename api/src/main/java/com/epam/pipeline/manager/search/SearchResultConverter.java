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

import com.epam.pipeline.controller.vo.search.ScrollingParameters;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.StorageUsage;
import com.epam.pipeline.entity.search.FacetedSearchResult;
import com.epam.pipeline.entity.search.SearchDocument;
import com.epam.pipeline.entity.search.SearchDocumentType;
import com.epam.pipeline.entity.search.SearchResult;
import com.epam.pipeline.exception.search.SearchException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.metrics.sum.ParsedSum;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchResultConverter {

    private static final String STORAGE_SIZE_AGG_NAME = "sizeSumSearch";

    public SearchResult buildResult(final SearchResponse searchResult,
                                    final String aggregation,
                                    final String typeFieldName,
                                    final Set<String> aclFilterFields,
                                    final Set<String> metadataSourceFields,
                                    final ScrollingParameters scrollingParameters) {
        return SearchResult.builder()
                .searchSucceeded(!searchResult.isTimedOut())
                .totalHits(searchResult.getHits().getTotalHits())
                .documents(buildDocuments(searchResult, typeFieldName, aclFilterFields, metadataSourceFields,
                                          scrollingParameters))
                .aggregates(buildAggregates(searchResult.getAggregations(), aggregation))
                .build();
    }

    public StorageUsage buildStorageUsageResponse(final SearchResponse searchResponse,
                                                  final AbstractDataStorage dataStorage, final String path) {
        final Long size = Optional.ofNullable(searchResponse.getAggregations())
                .map(aggregations -> aggregations.<ParsedSum>get(STORAGE_SIZE_AGG_NAME))
                .map(result -> new Double(result.getValue()).longValue())
                .orElseThrow(() -> new SearchException(
                    "Empty aggregations/value in ES response, unable to calculate storage usage for id="
                    + dataStorage.getId()));
        return StorageUsage.builder()
                .id(dataStorage.getId())
                .name(dataStorage.getName())
                .type(dataStorage.getType())
                .path(path)
                .size(size)
                .count(searchResponse.getHits().getTotalHits())
                .build();
    }

    public FacetedSearchResult buildFacetedResult(final SearchResponse response, final String typeFieldName,
                                                  final Set<String> aclFilterFields,
                                                  final Set<String> metadataSourceFields,
                                                  final ScrollingParameters scrollingParameters) {
        return FacetedSearchResult.builder()
                .totalHits(response.getHits().getTotalHits())
                .documents(buildDocuments(response, typeFieldName, aclFilterFields, metadataSourceFields,
                                          scrollingParameters))
                .facets(buildFacets(response.getAggregations()))
                .build();
    }

    private List<SearchDocument> buildDocuments(final SearchResponse searchResult, final String typeFieldName,
                                                final Set<String> aclFilterFields,
                                                final Set<String> metadataSourceFields,
                                                final ScrollingParameters scrollingParameters) {
        final List<SearchDocument> documents =
            buildDocuments(searchResult.getHits(), typeFieldName, aclFilterFields, metadataSourceFields);
        if (Objects.nonNull(scrollingParameters) && scrollingParameters.isScrollingBackward()) {
            Collections.reverse(documents);
        }
        return documents;
    }

    private Map<SearchDocumentType, Long> buildAggregates(final Aggregations aggregations,
                                                          final String aggregation) {
        if (aggregations == null || aggregations.get(aggregation) == null) {
            return Collections.emptyMap();
        }
        final Terms types = aggregations.get(aggregation);
        return types.getBuckets()
                .stream()
                .filter(bucket -> {
                    String key = bucket.getKeyAsString();
                    if (EnumUtils.isValidEnum(SearchDocumentType.class, key)) {
                        return true;
                    }
                    log.error("Unexpected document type: " + key);
                    return false;
                })
                .collect(Collectors.toMap(bucket -> SearchDocumentType.valueOf(bucket.getKeyAsString()),
                        MultiBucketsAggregation.Bucket::getDocCount));
    }

    private List<SearchDocument> buildDocuments(final SearchHits hits,
                                                final String typeFieldName,
                                                final Set<String> aclFilterFields,
                                                final Set<String> metadataSourceFields) {
        if (hits == null || hits.getHits() == null) {
            return Collections.emptyList();
        }
        return Arrays.stream(hits.getHits())
                .map(hit -> buildDocument(hit, typeFieldName, aclFilterFields, metadataSourceFields))
                .collect(Collectors.toList());
    }

    private SearchDocument buildDocument(final SearchHit hit,
                                         final String typeFieldName,
                                         final Set<String> aclFilterFields,
                                         final Set<String> metadataSourceFields) {
        final Map<String, Object> sourceFields = hit.getSourceAsMap();
        return SearchDocument.builder()
                .elasticId(hit.getId())
                .score(hit.getScore())
                .id(getSourceFieldIfPresent(sourceFields, SearchSourceFields.ID))
                .name(getSourceFieldIfPresent(sourceFields, SearchSourceFields.NAME))
                .parentId(getSourceFieldIfPresent(sourceFields, SearchSourceFields.PARENT_ID))
                .description(getSourceFieldIfPresent(sourceFields, SearchSourceFields.DESCRIPTION))
                .owner(getSourceFieldIfPresent(sourceFields, SearchSourceFields.OWNER))
                .attributes(getSourceRemainAttributes(sourceFields, metadataSourceFields))
                .type(EnumUtils.getEnum(SearchDocumentType.class, getFieldIfPresent(sourceFields, typeFieldName)))
                .highlights(buildHighlights(hit.getHighlightFields(), aclFilterFields))
                .build();
    }

    private String getFieldIfPresent(final Map<String, Object> sourceFields, final String fieldName) {
        return Optional.ofNullable(sourceFields.get(fieldName))
                .map(Object::toString)
                .orElse(null);
    }

    private String getSourceFieldIfPresent(final Map<String, Object> sourceFields, final SearchSourceFields field) {
        return Optional.ofNullable(sourceFields.get(field.getFieldName()))
                .map(Object::toString)
                .orElse(null);
    }

    private boolean isFieldAdditionalOrMetadata(final String field, final Set<String> additionalFields,
                                                final Set<String> metadataSourceFields) {
        if (StringUtils.isBlank(field)) {
            return false;
        }
        return additionalFields.contains(field) || metadataSourceFields.contains(field);
    }

    private Map<String, String> getSourceRemainAttributes(final Map<String, Object> sourceFields,
                                                          final Set<String> metadataSourceFields) {
        final Set<String> additionalFields = SearchSourceFields.ADDITIONAL_FIELDS;
        return MapUtils.emptyIfNull(sourceFields).entrySet().stream()
                .filter(entry -> isFieldAdditionalOrMetadata(entry.getKey(), additionalFields, metadataSourceFields)
                        && Objects.nonNull(entry.getValue()))
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().toString()));
    }

    private List<SearchDocument.HightLight> buildHighlights(final Map<String, HighlightField> highlightFields,
                                                            final Set<String> aclFilterFields) {
        return MapUtils.emptyIfNull(highlightFields).values().stream()
                .filter(highlightField -> !aclFilterFields.contains(highlightField.getName()))
                .map(field -> SearchDocument.HightLight.builder()
                        .fieldName(field.getName())
                        .matches(Arrays.stream(field.getFragments())
                                .map(Text::string)
                                .collect(Collectors.toList()))
                        .build())
                .collect(Collectors.toList());
    }

    private Map<String, Map<String, Long>> buildFacets(final Aggregations aggregations) {
        if (Objects.isNull(aggregations)) {
            return Collections.emptyMap();
        }
        return MapUtils.emptyIfNull(aggregations.asMap()).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, this::buildFacetValues));
    }

    private Map<String, Long> buildFacetValues(final Map.Entry<String, Aggregation> entry) {
        final Terms fieldAggregation = (Terms) entry.getValue();
        if (Objects.isNull(fieldAggregation)) {
            return Collections.emptyMap();
        }
        return ListUtils.emptyIfNull(fieldAggregation.getBuckets()).stream()
                .collect(Collectors.toMap(Terms.Bucket::getKeyAsString, Terms.Bucket::getDocCount));
    }
}
