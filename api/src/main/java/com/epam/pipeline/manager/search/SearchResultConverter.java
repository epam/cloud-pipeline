/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.StorageUsage;
import com.epam.pipeline.entity.search.SearchDocument;
import com.epam.pipeline.entity.search.SearchDocumentType;
import com.epam.pipeline.entity.search.SearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.EnumUtils;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHitField;
import org.elasticsearch.search.SearchHits;
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
                                    final Set<String> aclFilterFields) {
        return SearchResult.builder()
                .searchSucceeded(!searchResult.isTimedOut())
                .totalHits(searchResult.getHits().getTotalHits())
                .documents(buildDocuments(searchResult.getHits(), typeFieldName, aclFilterFields))
                .aggregates(buildAggregates(searchResult.getAggregations(), aggregation))
                .build();
    }

    public StorageUsage buildStorageUsageResponse(final SearchResponse searchResponse,
                                                  final AbstractDataStorage dataStorage, final String path) {
        final ParsedSum sumAggResult = searchResponse.getAggregations().get(STORAGE_SIZE_AGG_NAME);
        return StorageUsage.builder()
                .id(dataStorage.getId())
                .name(dataStorage.getName())
                .type(dataStorage.getType())
                .path(path)
                .size(new Double(sumAggResult.getValue()).longValue())
                .count(searchResponse.getHits().getTotalHits())
                .build();
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
                                                final Set<String> aclFilterFields) {
        if (hits == null || hits.getHits() == null) {
            return Collections.emptyList();
        }
        return Arrays.stream(hits.getHits())
                .map(hit -> buildDocument(hit, typeFieldName, aclFilterFields))
                .collect(Collectors.toList());
    }

    private SearchDocument buildDocument(final SearchHit hit,
                                         final String typeFieldName,
                                         final Set<String> aclFilterFields) {
        return SearchDocument.builder()
                .elasticId(hit.getId())
                .score(hit.getScore())
                .id(getFieldIfPresent(hit, "id"))
                .name(getFieldIfPresent(hit, "name"))
                .parentId(getFieldIfPresent(hit, "parentId"))
                .type(EnumUtils.getEnum(SearchDocumentType.class, getFieldIfPresent(hit, typeFieldName)))
                .description(getFieldIfPresent(hit, "description"))
                .highlights(buildHighlights(hit.getHighlightFields(), aclFilterFields))
                .build();
    }

    private String getFieldIfPresent(final SearchHit hit, final String fieldName) {
        return Optional.ofNullable(hit.getField(fieldName))
                .map(SearchHitField::getValue)
                .map(Object::toString)
                .orElse(null);
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
}
