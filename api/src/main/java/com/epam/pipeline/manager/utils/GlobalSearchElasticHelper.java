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

package com.epam.pipeline.manager.utils;

import com.epam.pipeline.entity.search.FacetedSearchResult;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.http.HttpHost;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.GetMappingsRequest;
import org.elasticsearch.client.indices.GetMappingsResponse;
import org.elasticsearch.cluster.metadata.MappingMetaData;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Slf4j
@RequiredArgsConstructor
public class GlobalSearchElasticHelper {

    private final PreferenceManager preferenceManager;

    public RestHighLevelClient buildClient() {
        return new RestHighLevelClient(buildLowLevelClientBuilder());
    }

    public RestClientBuilder buildLowLevelClientBuilder() {
        final String host = preferenceManager.getPreference(SystemPreferences.SEARCH_ELASTIC_HOST);
        final Integer port = preferenceManager.getPreference(SystemPreferences.SEARCH_ELASTIC_PORT);
        final String schema = preferenceManager.getPreference(SystemPreferences.SEARCH_ELASTIC_SCHEME);

        Assert.isTrue(Objects.nonNull(host) && Objects.nonNull(port) && Objects.nonNull(schema),
                "One or more of the following parameters is not configured: "
                        + SystemPreferences.SEARCH_ELASTIC_HOST.getKey() + ", "
                        + SystemPreferences.SEARCH_ELASTIC_PORT.getKey() + ", "
                        + SystemPreferences.SEARCH_ELASTIC_SCHEME.getKey()
        );
        return RestClient.builder(new HttpHost(host, port, schema));
    }

    public Set<String> getAvailableFacetFields(final String... indices) {
        try {
            GetMappingsResponse fieldMapping = buildClient().indices()
                    .getMapping(
                            new GetMappingsRequest().indices(indices),
                            RequestOptions.DEFAULT
                    );
            return fieldMapping.mappings().values()
                    .stream()
                    .map(MappingMetaData::sourceAsMap)
                    .flatMap(source -> {
                        final Object properties = source.get("properties");
                        if (properties instanceof Map) {
                            return ((Map<String, Object>) properties).keySet().stream().filter(Objects::nonNull);
                        }
                        return Stream.empty();
                    })
                    .collect(Collectors.toSet());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return Collections.emptySet();
    }

    public FacetedSearchResult buildFacets(final Collection<String> fields, final Map<String, List<String>> filters,
                                      final String... indices) {
        final SearchSourceBuilder searchSource = new SearchSourceBuilder()
                .query(getFacetedQuery(filters))
                .size(0);

        CollectionUtils.emptyIfNull(fields)
                .forEach(facet -> addTermAggregationToSource(searchSource, facet));

        SearchRequest searchRequest = new SearchRequest()
                .indicesOptions(IndicesOptions.strictExpandOpen())
                .indices(indices)
                .source(searchSource);

        try {
            final SearchResponse response = buildClient().search(searchRequest, RequestOptions.DEFAULT);
            return FacetedSearchResult.builder()
                    .totalHits(response.getHits().getTotalHits())
                    .facets(buildFacets(response.getAggregations()))
                    .build();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<String, Map<String, Long>> buildFacets(final Aggregations aggregations) {
        if (Objects.isNull(aggregations)) {
            return Collections.emptyMap();
        }
        return MapUtils.emptyIfNull(aggregations.asMap()).entrySet().stream()
                .map(e -> ImmutablePair.of(e.getKey(), buildFacetValues(e)))
                .filter(p -> !p.getValue().isEmpty())
                .collect(Collectors.toMap(ImmutablePair::getKey, ImmutablePair::getValue));
    }

    private Map<String, Long> buildFacetValues(final Map.Entry<String, Aggregation> entry) {
        final Terms fieldAggregation = (Terms) entry.getValue();
        if (Objects.isNull(fieldAggregation)) {
            return Collections.emptyMap();
        }
        return ListUtils.emptyIfNull(fieldAggregation.getBuckets()).stream()
                .collect(Collectors.toMap(Terms.Bucket::getKeyAsString, Terms.Bucket::getDocCount));
    }

    private QueryBuilder getFacetedQuery(final Map<String, List<String>> filters) {
        final BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        MapUtils.emptyIfNull(filters)
                .forEach((fieldName, values) -> boolQueryBuilder.must(filterToTermsQuery(fieldName, values)));
        return boolQueryBuilder;
    }

    private QueryBuilder filterToTermsQuery(final String fieldName, final List<String> values) {
        return QueryBuilders.termsQuery(fieldName, values);
    }

    private void addTermAggregationToSource(final SearchSourceBuilder searchSource, final String facet) {
        searchSource.aggregation(aggregateBy(facet));
    }

    private TermsAggregationBuilder aggregateBy(final String field) {
        return AggregationBuilders.terms(field)
                .field(field);
    }

}
