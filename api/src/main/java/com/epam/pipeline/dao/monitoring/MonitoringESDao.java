/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.dao.monitoring;

import com.epam.pipeline.entity.cluster.monitoring.ELKUsageMetric;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.exception.PipelineException;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.metrics.avg.Avg;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Repository;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.epam.pipeline.entity.cluster.monitoring.ELKUsageMetric.CPU;

@Repository
@ConditionalOnProperty("monitoring.elasticsearch.url")
public class MonitoringESDao {
    protected static final DateTimeFormatter DATE_FORMATTER =DateTimeFormatter.ofPattern("yyyy.MM.dd");

    private static final String INDEX_NAME_PATTERN = "heapster-%s";
    private static final String INDEX_NAME_TOKEN = "heapster-";

    private static final String FIELD_METRICS_TAGS = "MetricsTags";
    private static final String FIELD_POD_NAME_RAW = "pod_name.raw";
    private static final String FIELD_NAMESPACE_NAME = "namespace_name";
    private static final String FIELD_TYPE = "type";

    private static final String USAGE = "usage";
    private static final String USAGE_RATE = "usage_rate";
    private static final String LIMIT = "limit";
    
    private static final String AGGREGATION_POD_NAME = "pod_name";
    private static final String AGGREGATION_METRIC_RATE = "_avg_rate";

    private RestHighLevelClient client;
    private RestClient lowLevelClient;

    @Autowired
    public MonitoringESDao(RestHighLevelClient client, RestClient lowLevelClient) {
        this.client = client;
        this.lowLevelClient = lowLevelClient;
    }

    public Map<String, Double> loadUsageRateMetrics(final ELKUsageMetric metric, Collection<String> podIds,
                                                    final LocalDateTime from, LocalDateTime to) {
        if (metric == CPU) {
            return loadAvgMetrics(podIds, CPU.getName(), USAGE_RATE, CPU.getTimestamp(), from, to)
                    .entrySet()
                    .stream()
                    .collect(HashMap::new,
                            (m, e) -> m.put(e.getKey(), Double.isFinite(e.getValue()) ? e.getValue() : null),
                            Map::putAll);
        }

        final Map<String, Double> limits =
                loadAvgMetrics(podIds, metric.getName(), LIMIT, metric.getTimestamp(), from, to);
        final Map<String, Double> usages =
                loadAvgMetrics(podIds, metric.getName(), USAGE, metric.getTimestamp(), from, to);

        return getUsageRates(limits, usages);
    }

    /**
     * Delete indices, that are older than retention period (in days)
     * @param retentionPeriodDays retention period (in days)
     */
    public void deleteIndices(final int retentionPeriodDays) {
        try {
            final Response response = lowLevelClient.performRequest(HttpMethod.GET.name(), "/_cat/indices");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.getEntity().getContent()))) {
                final String indicesToDelete = reader.lines()
                        .flatMap(l -> Arrays.stream(l.split(" ")))
                        .filter(str -> str.startsWith(INDEX_NAME_TOKEN)).map(name -> {
                            String dateString = name.substring(INDEX_NAME_TOKEN.length());
                            try {
                                return new ImmutablePair<>(name, LocalDate.parse(dateString, DATE_FORMATTER)
                                        .atStartOfDay(ZoneOffset.UTC).toLocalDateTime());
                            } catch (IllegalArgumentException e) {
                                return new ImmutablePair<String, LocalDateTime>(name, null);
                            }
                        })
                        .filter(pair -> pair.right != null && olderThanRetentionPeriod(retentionPeriodDays, pair.right))
                        .map(pair -> pair.left)
                        .collect(Collectors.joining(","));

                lowLevelClient.performRequest(HttpMethod.DELETE.name(), "/" + indicesToDelete);
            }
        } catch (IOException e) {
            throw new PipelineException(e);
        }
    }

    private Map<String, Double> loadAvgMetrics(final Collection<String> podIds, final String metricType,
                                               final String metricName, final String rangeBy,
                                               final LocalDateTime from, final LocalDateTime to) {
        if (CollectionUtils.isEmpty(podIds)) {
            return Collections.emptyMap();
        }

        final SearchSourceBuilder builder = new SearchSourceBuilder()
            .query(QueryBuilders.boolQuery()
                 .filter(QueryBuilders.termsQuery(path(FIELD_METRICS_TAGS, FIELD_POD_NAME_RAW), podIds))
                 .filter(QueryBuilders.termQuery(path(FIELD_METRICS_TAGS, FIELD_NAMESPACE_NAME), "default"))
                 .filter(QueryBuilders.termQuery(path(FIELD_METRICS_TAGS, FIELD_TYPE), "pod_container"))
                 .filter(QueryBuilders.rangeQuery(rangeBy)
                             .from(from.toInstant(ZoneOffset.UTC).toEpochMilli())
                             .to(to.toInstant(ZoneOffset.UTC).toEpochMilli())))
            .size(0)
            .aggregation(AggregationBuilders.terms(AGGREGATION_POD_NAME)
                             .field(path(FIELD_METRICS_TAGS, FIELD_POD_NAME_RAW))
                             .size(podIds.size())
                             .subAggregation(AggregationBuilders.avg(metricType + AGGREGATION_METRIC_RATE)
                                     .field("Metrics." + metricType + "/" + metricName + ".value")));

        final SearchRequest request = new SearchRequest(getIndexNames(from, to)).types(metricType).source(builder);

        SearchResponse response;
        try {
            response = client.search(request);
        } catch (IOException e) {
            throw new PipelineException(e);
        }

        final Terms terms = response.getAggregations().get(AGGREGATION_POD_NAME);
        return terms.getBuckets().stream()
            .collect(Collectors.toMap(
                b -> b.getKey().toString(),
                b -> ((Avg) b.getAggregations().get(metricType + AGGREGATION_METRIC_RATE)).getValue()));
    }

    private String path(String ...parts) {
        return String.join(".", parts);
    }

    private String[] getIndexNames(final LocalDateTime from, final LocalDateTime to) {
        return Stream.of(from, to)
            .map(d -> d.format(DATE_FORMATTER))
            .distinct()
            .map(dateStr -> String.format(INDEX_NAME_PATTERN, dateStr))
            .toArray(String[]::new);
    }

    private Map<String, Double> getUsageRates(final Map<String, Double> limits, final Map<String, Double> usages) {
        return limits.entrySet().stream()
                .collect(HashMap::new,
                (m, e) -> {
                    Double usage = usages.get(e.getKey());
                    Double limit = e.getValue();
                    m.put(e.getKey(), getRate(usage, limit));
                },
                Map::putAll);
    }

    private boolean olderThanRetentionPeriod(final int retentionPeriod, final LocalDateTime date) {
        return date.isBefore(DateUtils.nowUTC().minusDays(retentionPeriod + 1L));
    }

    private Double getRate(final Double usage, final Double limit) {
        return usage == null || limit == null ? null : usage / limit;
    }
}
