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

package com.epam.pipeline.dao.monitoring.metricrequester;

import com.epam.pipeline.entity.cluster.monitoring.ELKUsageMetric;
import com.epam.pipeline.entity.cluster.monitoring.MonitoringStats;
import com.epam.pipeline.exception.PipelineException;
import org.apache.commons.lang3.tuple.Pair;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.script.Script;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.BucketOrder;
import org.elasticsearch.search.aggregations.PipelineAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.ParsedSingleValueNumericMetricsAggregation;
import org.elasticsearch.search.aggregations.metrics.avg.AvgAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.max.MaxAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.min.MinAggregationBuilder;
import org.elasticsearch.search.aggregations.pipeline.bucketscript.BucketScriptPipelineAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.io.IOException;
import java.lang.reflect.Field;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class AbstractMetricRequester implements MetricRequester, MonitoringRequester {

    private static final DateTimeFormatter DATE_FORMATTER =DateTimeFormatter.ofPattern("yyyy.MM.dd");

    private static final String INDEX_NAME_PATTERN = "heapster-%s";

    private static final IndicesOptions INDICES_OPTIONS = IndicesOptions.STRICT_EXPAND_OPEN_CLOSED;

    private static final String ORDER_FIELD = "order";

    protected static final String FIELD_METRICS = "Metrics";
    protected static final String FIELD_METRICS_TAGS = "MetricsTags";
    protected static final String FIELD_NAMESPACE_NAME = "namespace_name";
    protected static final String FIELD_TYPE = "type";
    protected static final String FIELD_DOCUMENT_TYPE = "_type";

    protected static final String USAGE = "usage";
    protected static final String USAGE_RATE = "usage_rate";
    protected static final String NODE_UTILIZATION = "node_utilization";
    protected static final String NODE_CAPACITY = "node_capacity";
    protected static final String WORKING_SET = "working_set";
    protected static final String CPU_CAPACITY = "cpu_capacity";
    protected static final String CPU_UTILIZATION = "cpu_utilization";
    protected static final String GPU_UTILIZATION = "gpu_utilization";
    protected static final String MEMORY_UTILIZATION = "memory_utilization";
    protected static final String MEMORY_CAPACITY = "memory_capacity";
    protected static final String LIMIT = "limit";
    protected static final String VALUE = "value";
    protected static final String DEFAULT = "default";

    protected static final String CPU_HISTOGRAM = "cpu_histogram";
    protected static final String MEMORY_HISTOGRAM = "memory_histogram";
    protected static final String NETWORK_HISTOGRAM = "network_histogram";
    protected static final String DISKS_HISTOGRAM = "disks_histogram";

    protected static final String RX_RATE = "rx_rate";
    protected static final String TX_RATE = "tx_rate";

    protected static final String NODE = "node";
    protected static final String RESOURCE_ID = "resource_id";
    protected static final String POD_CONTAINER = "pod_container";

    protected static final String AVG_AGGREGATION = "avg_";
    protected static final String MAX_AGGREGATION = "max_";
    protected static final String MIN_AGGREGATION = "min_";
    protected static final String DIVISION_AGGREGATION = "division_";
    protected static final String AGGREGATION_POD_NAME = "pod_name";
    protected static final String FIELD_POD_NAME_RAW = "pod_name.raw";
    protected static final String AGGREGATION_NODE_NAME = "nodename";
    protected static final String FIELD_NODENAME_RAW = "nodename.raw";
    protected static final String AGGREGATION_DISK_NAME = "disk_name";

    protected static final String SYNTHETIC_NETWORK_INTERFACE = "summary";
    protected static final String SWAP_FILESYSTEM = "tmpfs";

    private final HeapsterElasticRestHighLevelClient client;
    private final String indexNamePattern;

    AbstractMetricRequester(final HeapsterElasticRestHighLevelClient client) {
        this.client = client;
        this.indexNamePattern = INDEX_NAME_PATTERN;
    }

    AbstractMetricRequester(final HeapsterElasticRestHighLevelClient client,
                            final String indexNamePattern) {
        this.client = client;
        this.indexNamePattern = indexNamePattern;
    }

    protected abstract ELKUsageMetric metric();

    protected abstract SearchRequest buildStatsRequest(String nodeName, LocalDateTime from, LocalDateTime to,
                                                       Duration interval);

    protected abstract List<MonitoringStats> parseStatsResponse(SearchResponse response);

    public static MetricRequester getRequester(final ELKUsageMetric metric,
                                               final HeapsterElasticRestHighLevelClient client) {
        switch (metric) {
            case CPU:
                return new CPURequester(client);
            case MEM:
                return new MemoryRequester(client);
            case FS:
                return new FSRequester(client);
            case NETWORK:
                return new NetworkRequester(client);
            default:
                throw new IllegalArgumentException("Metric type: " + metric.getName() + " isn't supported!");
        }
    }

    public static MonitoringRequester getStatsRequester(final ELKUsageMetric metric,
                                                        final HeapsterElasticRestHighLevelClient client) {
        switch (metric) {
            case CPU:
                return new CPURequester(client);
            case MEM:
                return new MemoryRequester(client);
            case FS:
                return new FSRequester(client);
            case POD_FS:
                return new PodFSRequester(client);
            case NETWORK:
                return new NetworkRequester(client);
            default:
                throw new IllegalArgumentException("Metric type: " + metric.getName() + " isn't supported!");
        }
    }

    @Override
    public Map<String, Double> performRequest(final Collection<String> resourceIds,
                                              final LocalDateTime from, final LocalDateTime to) {
        return parseResponse(
                executeRequest(
                        buildRequest(
                                resourceIds, from, to, null))
        );
    }

    public Map<String, Double> collectAggregation(final SearchResponse response,
                                                  final String aggName, final String subAggName) {
        return ((Terms)response.getAggregations().get(aggName)).getBuckets()
                .stream()
                .map(b -> Pair.of(b.getKey().toString(),
                        doubleValue(aggregations(b), subAggName))
                )
                .filter(pair -> pair.getRight().isPresent())
                .collect(Collectors.toMap(Pair::getLeft, p -> p.getRight().get()));
    }

    protected SearchResponse executeRequest(final SearchRequest searchRequest) {
        try {
            return client.searchHeapsterElastic(searchRequest, RequestOptions.DEFAULT);
        } catch (IOException e) {
            throw new PipelineException(e);
        }
    }

    protected static String path(final String ...parts) {
        return String.join(".", parts);
    }

    protected SearchRequest request(final LocalDateTime from, final LocalDateTime to,
                                    final SearchSourceBuilder builder) {
        return new SearchRequest(getIndexNames(from, to))
                .types(metric().getName())
                .source(builder)
                .indicesOptions(INDICES_OPTIONS);
    }

    private String[] getIndexNames(final LocalDateTime from, final LocalDateTime to) {
        final LocalDate fromDate = from.toLocalDate();
        final LocalDate toDate = to.toLocalDate();
        return Stream.iterate(fromDate, date -> date.plusDays(1))
                .limit(Period.between(fromDate, toDate).getDays() + 1)
                .map(date -> date.format(DATE_FORMATTER))
                .map(str -> String.format(indexNamePattern, str))
                .toArray(String[]::new);
    }

    protected Optional<Double> doubleValue(final List<Aggregation> aggregations, final String name) {
        return aggregations.stream()
                .filter(it -> name.equals(it.getName()))
                .findFirst()
                .filter(it -> it instanceof ParsedSingleValueNumericMetricsAggregation)
                .map(ParsedSingleValueNumericMetricsAggregation .class::cast)
                .map(ParsedSingleValueNumericMetricsAggregation::value)
                .filter(it -> !it.isInfinite());
    }

    protected Optional<Long> longValue(final List<Aggregation> aggregations, final String name) {
        return doubleValue(aggregations, name).map(Double::longValue);
    }

    @Override
    public List<MonitoringStats> requestStats(final String nodeName, final LocalDateTime from, final LocalDateTime to,
                                              final Duration interval) {
        final SearchRequest request = buildStatsRequest(nodeName, from, to, interval);
        return parseStatsResponse(executeRequest(request));
    }

    protected SearchSourceBuilder statsQuery(final String nodeName, final String type,
                                             final LocalDateTime from, final LocalDateTime to) {
        return new SearchSourceBuilder()
                .query(QueryBuilders.boolQuery()
                        .filter(QueryBuilders.termsQuery(path(FIELD_METRICS_TAGS, FIELD_NODENAME_RAW), nodeName))
                        .filter(QueryBuilders.termQuery(path(FIELD_METRICS_TAGS, FIELD_TYPE), type))
                        .filter(QueryBuilders.termQuery(path(FIELD_DOCUMENT_TYPE), metric().getName()))
                        .filter(QueryBuilders.rangeQuery(metric().getTimestamp())
                                .from(from.toInstant(ZoneOffset.UTC).toEpochMilli())
                                .to(to.toInstant(ZoneOffset.UTC).toEpochMilli())));
    }

    protected DateHistogramAggregationBuilder dateHistogram(final String name, final Duration interval) {
        return AggregationBuilders.dateHistogram(name)
                .field(metric().getTimestamp())
                .interval(interval.toMillis())
                .minDocCount(1L);
    }

    protected AvgAggregationBuilder average(final String name, final String field) {
        return AggregationBuilders.avg(AVG_AGGREGATION + name)
                .field(field(field));
    }

    protected MaxAggregationBuilder max(final String name, final String field) {
        return AggregationBuilders.max(MAX_AGGREGATION + name).field(field(field));
    }

    protected MinAggregationBuilder min(final String name, final String field) {
        return AggregationBuilders.min(MIN_AGGREGATION + name).field(field(field));
    }

    protected PipelineAggregationBuilder division(final String name, final String divider, final String divisor) {
        final Map<String, String> variables = new HashMap<>(2);
        variables.put("divider", divider);
        variables.put("divisor", divisor);
        final Script script = new Script("params.divider / params.divisor");
        return new BucketScriptPipelineAggregationBuilder(name, variables, script);
    }

    private String field(final String name) {
        return path(FIELD_METRICS, metric().getName() + "/" + name, VALUE);
    }

    protected List<Aggregation> aggregations(final MultiBucketsAggregation.Bucket bucket) {
        return Optional.ofNullable(bucket.getAggregations())
                                .map(Aggregations::asList)
                                .orElseGet(Collections::emptyList);
    }

    protected TermsAggregationBuilder ordered(final TermsAggregationBuilder terms) {
        try {
            final Field field = terms.getClass().getDeclaredField(ORDER_FIELD);
            field.setAccessible(true);
            field.set(terms, BucketOrder.count(false));
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new PipelineException(e);
        }
        return terms;
    }

    protected Double getDoubleValue(final List<Aggregation> aggregations, final String metricName) {
        return doubleValue(aggregations, metricName).orElse(null);
    }
}
