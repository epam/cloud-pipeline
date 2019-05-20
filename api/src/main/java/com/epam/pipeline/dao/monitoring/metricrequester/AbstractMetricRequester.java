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

package com.epam.pipeline.dao.monitoring.metricrequester;

import com.epam.pipeline.entity.cluster.monitoring.ELKUsageMetric;
import com.epam.pipeline.entity.cluster.monitoring.MonitoringStats;
import com.epam.pipeline.exception.PipelineException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.ParsedSingleValueNumericMetricsAggregation;
import org.elasticsearch.search.aggregations.metrics.avg.AvgAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

public abstract class AbstractMetricRequester implements MetricRequester, MonitoringRequester {

    private static final DateTimeFormatter DATE_FORMATTER =DateTimeFormatter.ofPattern("yyyy.MM.dd");

    private static final String INDEX_NAME_PATTERN = "heapster-%s";

    protected static final String FIELD_METRICS = "Metrics";
    protected static final String FIELD_METRICS_TAGS = "MetricsTags";
    protected static final String FIELD_NAMESPACE_NAME = "namespace_name";
    protected static final String FIELD_TYPE = "type";
    protected static final String FIELD_DOCUMENT_TYPE = "_type";

    protected static final String USAGE = "usage";
    protected static final String USAGE_RATE = "usage_rate";
    protected static final String NODE_UTILIZATION = "node_utilization";
    protected static final String NODE_CAPACITY = "node_capacity";
    protected static final String CPU_CAPACITY = "cpu_capacity";
    protected static final String CPU_UTILIZATION = "cpu_utilization";
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
    protected static final String AGGREGATION_POD_NAME = "pod_name";
    protected static final String FIELD_POD_NAME_RAW = "pod_name.raw";
    protected static final String AGGREGATION_NODE_NAME = "nodename";
    protected static final String FIELD_NODENAME_RAW = "nodename.raw";
    protected static final String AGGREGATION_DISK_NAME = "disk_name";

    protected static final String SYNTHETIC_NETWORK_INTERFACE = "summary";

    private RestHighLevelClient client;

    AbstractMetricRequester(final RestHighLevelClient client) {
        this.client = client;
    }

    protected abstract ELKUsageMetric metric();

    protected abstract SearchRequest buildStatsRequest(String nodeName, LocalDateTime from, LocalDateTime to,
                                                       Duration interval);

    protected abstract List<MonitoringStats> parseStatsResponse(SearchResponse response);

    public static MetricRequester getRequester(final ELKUsageMetric metric, final RestHighLevelClient client) {
        switch (metric) {
            case CPU:
                return new CPURequester(client);
            case MEM:
                return new MemoryRequester(client);
            case FS:
                return new FSRequester(client);
            default:
                throw new IllegalArgumentException("Metric type: " + metric.getName() + " isn't supported!");
        }
    }

    public static MonitoringRequester getStatsRequester(final ELKUsageMetric metric, final RestHighLevelClient client) {
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

    @Override
    public Map<String, Double> performRequest(final Collection<String> resourceIds,
                                              final LocalDateTime from, final LocalDateTime to) {
        return parseResponse(
                executeRequest(
                        buildRequest(
                                resourceIds, from, to, null))
        );
    }

    protected SearchResponse executeRequest(final SearchRequest searchRequest) {
        try {
            return client.search(searchRequest);
        } catch (IOException e) {
            throw new PipelineException(e);
        }
    }

    protected static String path(final String ...parts) {
        return String.join(".", parts);
    }

    protected SearchRequest request(final LocalDateTime from, final LocalDateTime to,
                                    final SearchSourceBuilder builder) {
        return new SearchRequest(getIndexNames(from, to)).types(metric().getName()).source(builder);
    }

    private static String[] getIndexNames(final LocalDateTime from, final LocalDateTime to) {
        return IntStream.iterate(0, i -> i + 1)
                .limit(Duration.between(from, to).toDays() + 1)
                .mapToObj(from::plusDays)
                .map(d -> d.format(DATE_FORMATTER))
                .map(dateStr -> String.format(INDEX_NAME_PATTERN, dateStr))
                .toArray(String[]::new);
    }

    protected Optional<Double> value(final List<Aggregation> aggregations, final String name) {
        return aggregations.stream()
                .filter(it -> name.equals(it.getName()))
                .findFirst()
                .filter(it -> it instanceof ParsedSingleValueNumericMetricsAggregation)
                .map(ParsedSingleValueNumericMetricsAggregation .class::cast)
                .map(ParsedSingleValueNumericMetricsAggregation::value);
    }

    protected Optional<Long> longValue(final List<Aggregation> aggregations, final String name) {
        return value(aggregations, name).map(Double::longValue);
    }

    @Override
    public List<MonitoringStats> requestStats(final String nodeName, final LocalDateTime from, final LocalDateTime to,
                                              final Duration interval) {
        final SearchRequest request = buildStatsRequest(nodeName, from, to, interval);
        return parseStatsResponse(executeRequest(request));
    }

    protected SearchSourceBuilder nodeStatsQuery(final String nodeName, final LocalDateTime from,
                                                 final LocalDateTime to) {
        return new SearchSourceBuilder()
                .query(QueryBuilders.boolQuery()
                        .filter(QueryBuilders.termsQuery(path(FIELD_METRICS_TAGS, FIELD_NODENAME_RAW), nodeName))
                        .filter(QueryBuilders.termQuery(path(FIELD_METRICS_TAGS, FIELD_TYPE), NODE))
                        .filter(QueryBuilders.termQuery(path(FIELD_DOCUMENT_TYPE), metric().getName()))
                        .filter(QueryBuilders.rangeQuery(metric().getTimestamp())
                                .from(from.toInstant(ZoneOffset.UTC).toEpochMilli())
                                .to(to.toInstant(ZoneOffset.UTC).toEpochMilli())))
                .size(0);
    }

    protected DateHistogramAggregationBuilder dateHistogram(final String name, final Duration interval) {
        return AggregationBuilders.dateHistogram(name)
                .field(metric().getTimestamp())
                .interval(interval.toMillis())
                .minDocCount(1L);
    }

    protected AvgAggregationBuilder average(final String name, final String field) {
        return AggregationBuilders.avg(name)
                .field(field(field));
    }

    private String field(final String name) {
        return path(FIELD_METRICS, metric().getName() + "/" + name, VALUE);
    }

    protected List<Aggregation> aggregations(final MultiBucketsAggregation.Bucket bucket) {
        return Optional.ofNullable(bucket.getAggregations())
                                .map(Aggregations::asList)
                                .orElseGet(Collections::emptyList);
    }
}
