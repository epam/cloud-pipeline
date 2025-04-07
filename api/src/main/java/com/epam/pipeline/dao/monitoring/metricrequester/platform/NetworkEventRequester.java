/*
 * Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.dao.monitoring.metricrequester.platform;

import com.epam.pipeline.dao.monitoring.metricrequester.HeapsterElasticRestHighLevelClient;
import com.epam.pipeline.entity.cluster.monitoring.platform.network.NetworkEventFilter;
import com.epam.pipeline.entity.cluster.monitoring.platform.histogram.HistogramBin;
import com.epam.pipeline.entity.cluster.monitoring.platform.histogram.HistogramType;
import com.epam.pipeline.utils.ElasticsearchUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.BucketOrder;
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class NetworkEventRequester extends AbstractPlatformMetricRequester {

    private static final String RUN_ID_FIELD = "run_id";
    private static final String RUN_HOST_NAME_FIELD = "host_name";
    private static final String RUN_HOST_IP_FIELD = "host_ip";
    private static final String RESOURCE_FIELD = "resource";
    private static final String RESOURCE_HOST_FIELD = "resource_host";
    private static final String REPORTER_FIELD = "reporter";
    private static final String METHOD_FIELD = "method";
    private static final String TIMESTAMP_FIELD = "timestamp";

    public static final String TIME_HISTOGRAM_NAME = "timestamp";
    public static final String RESOURCE_HISTOGRAM_NAME = "resource_host";
    public static final String RUN_ID_HISTOGRAM_NAME = "run_id";


    public NetworkEventRequester(final HeapsterElasticRestHighLevelClient client,
                                 final String indexNamePattern) {
        super(client, indexNamePattern);
    }

    @Override
    public Map<String, List<String>> performHistogramFilterRequest() {
        final NetworkEventFilter.NetworkEventFilterBuilder result = NetworkEventFilter.builder();
        final SearchRequest request = new SearchRequest()
                .source(
                    new SearchSourceBuilder()
                        .query(QueryBuilders.boolQuery())
                        .size(0)
                        .aggregation(ordered(AggregationBuilders.terms(REPORTER_FIELD).field(REPORTER_FIELD)))
                        .aggregation(ordered(AggregationBuilders.terms(METHOD_FIELD).field(METHOD_FIELD)))
                )
                .indices(getAllIndices())
                .indicesOptions(AbstractPlatformMetricRequester.INDICES_OPTIONS);
        log.debug("Logs request: {} ", request);

        ElasticsearchUtils.verifyResponse(executeRequest(request)).getAggregations()
                .asList()
                .stream()
                .map(Terms.class::cast)
                .forEach(terms -> {
                    List<String> values = terms.getBuckets().stream()
                            .map(MultiBucketsAggregation.Bucket::getKey)
                            .map(Object::toString)
                            .collect(Collectors.toList());
                    if (REPORTER_FIELD.equals(terms.getName())) {
                        result.reporter(values);
                    } else if (METHOD_FIELD.equals(terms.getName())) {
                        result.method(values);
                    }
                });

        return result.build().toMap();
    }

    @Override
    public SearchRequest buildHistogramRequest(final HistogramType histogramType, final LocalDateTime from,
                                               final LocalDateTime to, final int nBins,
                                               final Map<String, List<String>> additional) {
        BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery();
        addQueryFilterIfFilterExists(additional, REPORTER_FIELD, queryBuilder);
        addQueryFilterIfFilterExists(additional, METHOD_FIELD, queryBuilder);
        addQueryFilterIfFilterExists(additional, METHOD_FIELD, queryBuilder);
        addQueryFilterIfFilterExists(additional, RESOURCE_HOST_FIELD, queryBuilder);
        addQueryFilterIfFilterExists(additional, RUN_ID_FIELD, queryBuilder);
        addQueryFilterIfFilterExists(additional, RUN_HOST_NAME_FIELD, queryBuilder);
        addQueryFilterIfFilterExists(additional, RUN_HOST_IP_FIELD, queryBuilder);

        if (CollectionUtils.isNotEmpty(additional.getOrDefault(RESOURCE_FIELD, Collections.emptyList()))) {
            queryBuilder
                    .filter(QueryBuilders.matchQuery(RESOURCE_FIELD, additional.get(RESOURCE_FIELD).get(0)));
        }

        queryBuilder.filter(QueryBuilders.rangeQuery(TIMESTAMP_FIELD)
                    .from(from.toInstant(ZoneOffset.UTC).toEpochMilli())
                    .to(to.toInstant(ZoneOffset.UTC).toEpochMilli()));

        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
                .query(queryBuilder).size(0)
                .aggregation(getAggregation(histogramType, from, to, nBins));

        return new SearchRequest()
                .source(searchSourceBuilder)
                .indices(getIndexNames(from, to))
                .indicesOptions(INDICES_OPTIONS);
    }

    @Override
    protected HistogramBin toHistogramBin(final MultiBucketsAggregation.Bucket bucket) {
        return HistogramBin.builder().value(bucket.getKeyAsString()).count(bucket.getDocCount()).build();
    }

    @Override
    protected String[] getIndexNames(final LocalDateTime from, final LocalDateTime to) {
        // Getting - 1 day because some of the logs can be in prev day index due to rollover logic
        final LocalDate fromDate = from.toLocalDate().minusDays(1);
        final LocalDate toDate = to.toLocalDate();
        return Stream.iterate(fromDate, date -> date.plusDays(1))
                .limit(Period.between(fromDate, toDate).getDays() + 1)
                .map(date -> date.format(DATE_FORMATTER))
                .map(dateStr -> dateStr + "-*")
                .map(str -> String.format(indexNamePattern, str))
                .toArray(String[]::new);
    }

    private AggregationBuilder getAggregation(final HistogramType histogramType,
                                                     final LocalDateTime from, final LocalDateTime to,
                                                     final int nBins) {
        switch (histogramType) {
            case RESOURCE:
                return ordered(
                        AggregationBuilders.terms(RESOURCE_HISTOGRAM_NAME)
                            .field(RESOURCE_HOST_FIELD)
                            .size(nBins)
                            .minDocCount(1L)
                );
            case RUN:
                return ordered(
                        AggregationBuilders.terms(RUN_ID_HISTOGRAM_NAME)
                            .field(RUN_ID_FIELD)
                            .size(nBins)
                            .minDocCount(1L)
                );
            case TIME:
            default:
                final Duration interval = Duration.between(from, to).dividedBy(Math.max(1, nBins - 1));
                return AggregationBuilders.dateHistogram(TIME_HISTOGRAM_NAME)
                        .field(TIMESTAMP_FIELD)
                        .order(BucketOrder.key(true))
                        .interval(interval.toMillis());
        }
    }

    private static void addQueryFilterIfFilterExists(final Map<String, List<String>> additional,
                                                     final String reporterField, final BoolQueryBuilder queryBuilder) {
        if (CollectionUtils.isNotEmpty(additional.getOrDefault(reporterField, Collections.emptyList()))) {
            queryBuilder
                    .filter(QueryBuilders.termsQuery(reporterField, additional.get(reporterField)));
        }
    }
}

