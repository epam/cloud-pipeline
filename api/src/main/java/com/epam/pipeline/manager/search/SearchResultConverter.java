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

import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.StorageUsage;
import com.epam.pipeline.entity.search.SearchDocument;
import com.epam.pipeline.entity.search.SearchDocumentType;
import com.epam.pipeline.entity.search.SearchResult;
import com.epam.pipeline.exception.search.SearchException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.elasticsearch.action.search.MultiSearchRequest;
import org.elasticsearch.action.search.MultiSearchResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.document.DocumentField;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedTerms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.metrics.sum.ParsedSum;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collection;
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

    private static final String STORAGE_SIZE_AGG_NAME = "sizeSum";
    private static final String STORAGE_SIZE_BY_TIER_AGG_NAME = "sizeSumByTier";
    private static final String STANDARD_TIER = "STANDARD";
    private static final long ZERO = 0L;


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

    public StorageUsage buildStorageUsageResponse(final MultiSearchRequest searchRequest,
                                                  final MultiSearchResponse searchResponse,
                                                  final AbstractDataStorage dataStorage,
                                                  final String path) {
        final MultiSearchResponse.Item[] responses = tryExtractAllResponses(searchResponse, searchRequest);
        final Map<String, StorageUsage.StorageUsageStats> statsByTiers = buildStorageUsageStatsByStorageTier(responses);

        final Optional<StorageUsage.StorageUsageStats> standardUsageOp =
                Optional.ofNullable(statsByTiers.get(STANDARD_TIER));
        return StorageUsage.builder()
                .id(dataStorage.getId())
                .name(dataStorage.getName())
                .type(dataStorage.getType())
                .path(path)
                .size(
                    standardUsageOp.map(StorageUsage.StorageUsageStats::getSize).orElse(ZERO))
                .oldVersionsSize(
                        standardUsageOp.map(StorageUsage.StorageUsageStats::getOldVersionsSize).orElse(ZERO))
                .count(standardUsageOp.map(StorageUsage.StorageUsageStats::getCount).orElse(ZERO))
                .effectiveSize(
                    standardUsageOp.map(StorageUsage.StorageUsageStats::getEffectiveSize).orElse(ZERO))
                .oldVersionsEffectiveSize(
                        standardUsageOp.map(StorageUsage.StorageUsageStats::getOldVersionsEffectiveSize).orElse(ZERO))
                .effectiveCount(standardUsageOp.map(StorageUsage.StorageUsageStats::getEffectiveCount).orElse(ZERO))
                .usage(statsByTiers)
                .build();
    }

    private Map<String, StorageUsage.StorageUsageStats> buildStorageUsageStatsByStorageTier(
            final MultiSearchResponse.Item[] responses) {

        final List<String> storageClasses = Arrays.stream(responses).map(
            r -> Optional.ofNullable(r.getResponse())
                    .map(SearchResponse::getAggregations)
                    .map(aggregations -> aggregations.<ParsedTerms>get(STORAGE_SIZE_BY_TIER_AGG_NAME).getBuckets())
                    .orElse(Collections.emptyList())
            ).flatMap(Collection::stream).map(MultiBucketsAggregation.Bucket::getKeyAsString)
            .distinct().collect(Collectors.toList());

        return storageClasses.stream().map(storageClass -> {
            final StorageUsage.StorageUsageStats.StorageUsageStatsBuilder storageUsageStats =
                    StorageUsage.StorageUsageStats.builder();

            final Pair<Long, Long> totalCurrentSizeAndCount;
            final Pair<Long, Long> totalOldVersionSizeAndCount;
            final Pair<Long, Long> effectiveCurrentSizeAndCount;
            final Pair<Long, Long> effectiveOldVersionSizeAndCount;
            if (responses.length > 2) {
                totalCurrentSizeAndCount = extractSizeAndCountFromResponse(responses, storageClass, 0);
                effectiveCurrentSizeAndCount = extractSizeAndCountFromResponse(responses, storageClass, 1);
                totalOldVersionSizeAndCount = extractSizeAndCountFromResponse(responses, storageClass, 2);
                effectiveOldVersionSizeAndCount = extractSizeAndCountFromResponse(responses, storageClass, 3);
            } else {
                totalCurrentSizeAndCount = extractSizeAndCountFromResponse(responses, storageClass, 0);
                effectiveCurrentSizeAndCount = totalCurrentSizeAndCount;
                totalOldVersionSizeAndCount = extractSizeAndCountFromResponse(responses, storageClass, 1);
                effectiveOldVersionSizeAndCount = totalOldVersionSizeAndCount;
            }
            return storageUsageStats
                    .storageClass(storageClass)
                    .size(totalCurrentSizeAndCount.getRight()).count(totalCurrentSizeAndCount.getLeft())
                    .effectiveSize(effectiveCurrentSizeAndCount.getRight())
                    .effectiveCount(effectiveCurrentSizeAndCount.getLeft())
                    .oldVersionsSize(totalOldVersionSizeAndCount.getRight())
                    .oldVersionsEffectiveSize(effectiveOldVersionSizeAndCount.getRight()).build();
        }).collect(Collectors.toMap(StorageUsage.StorageUsageStats::getStorageClass, s -> s));
    }

    private ImmutablePair<Long, Long> extractSizeAndCountFromResponse(
            final MultiSearchResponse.Item[] responses, final String tier, final int requestType) {
        return Optional.ofNullable(tryExtractResponse(responses, requestType))
                .map(SearchResponse::getAggregations).map(a -> a.<ParsedTerms>get(STORAGE_SIZE_BY_TIER_AGG_NAME))
                .map(bkts -> bkts.getBucketByKey(tier))
                .map(bucket -> {
                        long size = Optional.ofNullable(bucket.getAggregations())
                                .map(agg -> agg.<ParsedSum>get(STORAGE_SIZE_AGG_NAME))
                                .map(ParsedSum::getValue)
                                .orElse(0.0)
                                .longValue();
                        return ImmutablePair.of(
                                    bucket.getDocCount(),
                                    size);
                        }
                ).orElse(ImmutablePair.of(ZERO, ZERO));
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
        final Map<String, DocumentField> fields = hit.getFields();
        return SearchDocument.builder()
                .elasticId(hit.getId())
                .score(hit.getScore())
                .id(getFieldIfPresent(fields, "id"))
                .name(getFieldIfPresent(fields, "name"))
                .parentId(getFieldIfPresent(fields, "parentId"))
                .type(EnumUtils.getEnum(SearchDocumentType.class, getFieldIfPresent(fields, typeFieldName)))
                .description(getFieldIfPresent(fields, "description"))
                .highlights(buildHighlights(hit.getHighlightFields(), aclFilterFields))
                .build();
    }

    private String getFieldIfPresent(Map<String, DocumentField> fields, final String fieldName) {
        return Optional.ofNullable(fields.get(fieldName))
                .map(DocumentField::getValue)
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

    private MultiSearchResponse.Item[] tryExtractAllResponses(final MultiSearchResponse searchResponse,
                                                              final MultiSearchRequest searchRequest) {
        final int batchSize = searchRequest.requests().size();
        final MultiSearchResponse.Item[] items = Optional.ofNullable(searchResponse)
            .map(MultiSearchResponse::getResponses)
            .filter(searchResponses -> ArrayUtils.getLength(searchResponses) > 0)
            .orElseThrow(() -> new SearchException(
                "Empty multi-search response from ES, unable to calculate storage consumption."));
        final int responsesCount = items.length;
        if (batchSize != items.length) {
            throw new SearchException(String.format(
                "Unexpected number of responses during storage size calculation: %d expected, but %d found.",
                    batchSize, responsesCount));
        }
        return items;
    }

    private SearchResponse tryExtractResponse(final MultiSearchResponse.Item[] responses, final int index) {
        final MultiSearchResponse.Item responseItem = responses[index];
        if (responseItem == null) {
            throw new SearchException(
                String.format("Empty response item with id=[%d], unable to return storage usage.", index));
        }
        if (responseItem.isFailure()) {
            throw new SearchException(
                String.format("Error in response item with id=[%d], unable to return storage usage: %s",
                              index, responseItem.getFailureMessage()));
        }
        return responseItem.getResponse();
    }

}
