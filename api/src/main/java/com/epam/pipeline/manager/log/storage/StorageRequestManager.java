/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.log.storage;

import com.epam.pipeline.entity.log.storage.Sorting;
import com.epam.pipeline.entity.log.storage.StorageRequestStat;
import com.epam.pipeline.entity.log.storage.StorageStatsRequest;
import com.epam.pipeline.exception.PipelineException;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.utils.GlobalSearchElasticHelper;
import com.epam.pipeline.utils.ElasticsearchUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.ParsedSingleValueNumericMetricsAggregation;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StorageRequestManager {
    private static final String READ_REQUESTS = "read_requests";
    private static final String WRITE_REQUESTS = "write_requests";
    private static final String TOTAL_REQUESTS = "total_requests";
    private static final String USER_ID = "user_id";
    private static final Sorting DEFAULT_SORTING = new Sorting(READ_REQUESTS, SortOrder.DESC);
    private static final IndicesOptions INDICES_OPTIONS = IndicesOptions.fromOptions(true,
            SearchRequest.DEFAULT_INDICES_OPTIONS.allowNoIndices(),
            SearchRequest.DEFAULT_INDICES_OPTIONS.expandWildcardsOpen(),
            SearchRequest.DEFAULT_INDICES_OPTIONS.expandWildcardsClosed(),
            SearchRequest.DEFAULT_INDICES_OPTIONS);

    private final GlobalSearchElasticHelper elasticHelper;
    private final PreferenceManager preferenceManager;
    public StorageRequestStat getStatistics(final StorageStatsRequest request) {
        final Sorting sorting = resolveSorting(request.getSorting());
        final SearchSourceBuilder source = new SearchSourceBuilder()
                .query(constructQueryFilter(request))
                .sort(sorting.getField(), sorting.getOrder())
                .size(Optional.ofNullable(request.getMaxEntries()).orElse(10));

        final SearchRequest esRequest = new SearchRequest()
                .source(source)
                .indices(preferenceManager.getPreference(SystemPreferences.SEARCH_ELASTIC_REQUESTS_INDEX_PREFIX))
                .indicesOptions(INDICES_OPTIONS);
        final SearchResponse response = ElasticsearchUtils.verifyResponse(executeRequest(esRequest));
        return mapHitsToEntry(response.getHits(), request);
    }

    public StorageRequestStat getGroupedStats(final StorageStatsRequest request) {
        final SearchSourceBuilder source = new SearchSourceBuilder()
                .query(constructQueryFilter(request))
                .size(Optional.ofNullable(request.getMaxEntries()).orElse(10));

        final TermsAggregationBuilder userAggregation = AggregationBuilders.terms(request.getGroupBy())
                .field(request.getGroupBy()).size(Integer.MAX_VALUE);

        userAggregation.subAggregation(AggregationBuilders.sum(READ_REQUESTS).field(READ_REQUESTS));
        userAggregation.subAggregation(AggregationBuilders.sum(WRITE_REQUESTS).field(WRITE_REQUESTS));
        userAggregation.subAggregation(AggregationBuilders.sum(TOTAL_REQUESTS).field(TOTAL_REQUESTS));
        source.aggregation(userAggregation);

        final SearchRequest esRequest = new SearchRequest()
                .source(source)
                .indices(preferenceManager.getPreference(SystemPreferences.SEARCH_ELASTIC_REQUESTS_INDEX_PREFIX))
                .indicesOptions(INDICES_OPTIONS);
        final SearchResponse response = ElasticsearchUtils.verifyResponse(executeRequest(esRequest));
        return mapAggregationToEntry(response.getAggregations(), request);
    }

    private StorageRequestStat mapAggregationToEntry(final Aggregations aggregations,
                                                     final StorageStatsRequest request) {
        final StorageRequestStat.StorageRequestStatBuilder builder =
                StorageRequestStat.builder().userId(request.getUserId());
        if (Objects.isNull(aggregations) || Objects.isNull(aggregations.get(request.getGroupBy()))) {
            return builder.build();
        }
        final Terms aggregation = aggregations.get(request.getGroupBy());
        final Terms.Bucket bucket = aggregation.getBucketByKey(request.getUserId().toString());
        if (Objects.isNull(bucket)) {
            log.debug("Failed to load storage requests for user {}", request.getUserId());
            return builder.build();
        }
        return builder.readRequests(parseAggregation(READ_REQUESTS, bucket.getAggregations()))
                .writeRequests(parseAggregation(WRITE_REQUESTS, bucket.getAggregations()))
                .totalRequests(parseAggregation(TOTAL_REQUESTS, bucket.getAggregations()))
                .build();
    }

    private StorageRequestStat mapHitsToEntry(final SearchHits searchHit,
                                              final StorageStatsRequest request) {

        final StorageRequestStat.StorageRequestStatBuilder builder =
                StorageRequestStat.builder().userId(request.getUserId());
        if (Objects.isNull(searchHit) || Objects.isNull(searchHit.getHits())) {
            return builder.build();
        }
        return builder.statistics(Arrays.stream(searchHit.getHits())
                        .map(this::mapStorageEntry).collect(Collectors.toList()))
                .build();
    }

    private Long parseAggregation(final String name,
                                  final Aggregations aggregations) {
        return Optional.ofNullable(aggregations)
                .map(aggs -> aggs.<ParsedSingleValueNumericMetricsAggregation>get(name))
                .map(ParsedSingleValueNumericMetricsAggregation::value)
                .filter(it -> !it.isInfinite())
                .map(Double::longValue).orElse(0L);
    }

    private StorageRequestStat.Entry mapStorageEntry(final SearchHit hit) {
        final Map<String, Object> doc = hit.getSourceAsMap();
        return StorageRequestStat.Entry.builder()
                .storageId((Integer) doc.get("storage_id"))
                .storageName((String) doc.getOrDefault("storage_name", StringUtils.EMPTY))
                .readRequests((Integer) doc.get(READ_REQUESTS))
                .writeRequests((Integer) doc.get(WRITE_REQUESTS))
                .totalRequests((Integer) doc.get(TOTAL_REQUESTS))
                .build();
    }

    private QueryBuilder constructQueryFilter(final StorageStatsRequest request) {
        final BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        boolQuery.filter(QueryBuilders.termQuery(USER_ID, request.getUserId()));
        ElasticsearchUtils.addRangeFilter(boolQuery, request.getFromDate(),
                request.getToDate(), "period");
        return boolQuery;
    }

    private Sorting resolveSorting(final Sorting sorting) {
        if (Objects.isNull(sorting) || StringUtils.isBlank(sorting.getField())) {
            return DEFAULT_SORTING;
        }
        return sorting;
    }

    private SearchResponse executeRequest(final SearchRequest searchRequest) {
        try (RestHighLevelClient client = elasticHelper.buildClient()) {
            return client.search(searchRequest, RequestOptions.DEFAULT);
        } catch (IOException e) {
            throw new PipelineException(e);
        }
    }
}
