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
import com.epam.pipeline.entity.datastorage.DataStorageType;
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
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.elasticsearch.action.search.MultiSearchRequest;
import org.elasticsearch.action.search.MultiSearchResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedTerms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.metrics.sum.ParsedSum;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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

    public StorageUsage buildStorageUsageResponse(final MultiSearchRequest searchRequest,
                                                  final MultiSearchResponse searchResponse,
                                                  final AbstractDataStorage dataStorage,
                                                  final String path) {
        final MultiSearchResponse.Item[] responses = tryExtractAllResponses(searchResponse, searchRequest);
        final Map<String, StorageUsage.StorageUsageStats> statsByTiers =
                buildStorageUsageStatsByStorageTier(dataStorage.getType(), responses);

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
            final DataStorageType dataStorageType, final MultiSearchResponse.Item[] responses) {

        final Set<String> storageClasses = dataStorageType.getStorageClasses();

        return storageClasses.stream().map(storageClass -> {
            final StorageUsage.StorageUsageStats.StorageUsageStatsBuilder storageUsageStats =
                    StorageUsage.StorageUsageStats.builder();

            final Pair<Long, Long> totalCurrentSizeAndCount;
            final Pair<Long, Long> effectiveCurrentSizeAndCount;
            final Long totalOldVersionSize;
            final Long effectiveOldVersionSize;
            if (responses.length > 1) {
                totalCurrentSizeAndCount = extractSizeAndCountForCurrentFileVersion(responses, storageClass, 0);
                effectiveCurrentSizeAndCount = extractSizeAndCountForCurrentFileVersion(responses, storageClass, 1);
                totalOldVersionSize = extractSizeOldFileVersion(responses, storageClass, 0);
                effectiveOldVersionSize = extractSizeOldFileVersion(responses, storageClass, 1);
            } else {
                totalCurrentSizeAndCount = extractSizeAndCountForCurrentFileVersion(responses, storageClass, 0);
                effectiveCurrentSizeAndCount = totalCurrentSizeAndCount;
                totalOldVersionSize = extractSizeOldFileVersion(responses, storageClass, 0);
                effectiveOldVersionSize = totalOldVersionSize;
            }
            return storageUsageStats
                    .storageClass(storageClass)
                    .size(totalCurrentSizeAndCount.getRight()).count(totalCurrentSizeAndCount.getLeft())
                    .effectiveSize(effectiveCurrentSizeAndCount.getRight())
                    .effectiveCount(effectiveCurrentSizeAndCount.getLeft())
                    .oldVersionsSize(totalOldVersionSize)
                    .oldVersionsEffectiveSize(effectiveOldVersionSize).build();
        }).filter(stats -> stats.getSize() > 0 || stats.getOldVersionsSize() > 0 || stats.getCount() > 0)
        .collect(Collectors.toMap(StorageUsage.StorageUsageStats::getStorageClass, s -> s));
    }

    private Long extractSizeOldFileVersion(
            final MultiSearchResponse.Item[] responses, final String storageClass, final int requestType) {
        return Optional.ofNullable(tryExtractResponse(responses, requestType))
                .map(SearchResponse::getAggregations)
                .map(a -> a.<ParsedSum>get(String.format("ov_%s_size_agg", storageClass.toLowerCase(Locale.ROOT))))
                .map(a -> Double.valueOf(a.getValue()).longValue()).orElse(ZERO);
    }

    private ImmutablePair<Long, Long> extractSizeAndCountForCurrentFileVersion(
            final MultiSearchResponse.Item[] responses, final String tier, final int requestType) {
        return Optional.ofNullable(tryExtractResponse(responses, requestType))
                .map(SearchResponse::getAggregations).map(a -> a.<ParsedTerms>get(STORAGE_SIZE_BY_TIER_AGG_NAME))
                .map(bkts -> bkts.getBucketByKey(tier))
                .map(bucket -> ImmutablePair.of(
                        bucket.getDocCount(),
                        new Double(
                                bucket.getAggregations().<ParsedSum>get(STORAGE_SIZE_AGG_NAME).getValue()
                        ).longValue())
                ).orElse(ImmutablePair.of(ZERO, ZERO));
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
