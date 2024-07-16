/*
 * Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
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
import com.epam.pipeline.entity.cluster.monitoring.MonitoringMetrics;
import com.epam.pipeline.entity.cluster.monitoring.MonitoringStats;
import com.nimbusds.jose.util.Pair;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GPUDetailsRequester extends AbstractMetricRequester {
    private static final String DETAILS_HISTOGRAM = "details_histogram";
    private static final String UTILIZATION_GPU = "utilization_gpu";
    private static final String UTILIZATION_GPU_MEMORY = "utilization_memory";
    private static final String USED_GPU_MEMORY = "used_memory";
    private static final String DEVICE_NAME_AGGREGATION = "device_name";
    private static final String DEVICE_ID_RAW_FIELD = "index.raw";
    private static final String INDEX_NAME_PATTERN = "cp-gpu-monitor-%s";

    public GPUDetailsRequester(final HeapsterElasticRestHighLevelClient client) {
        super(client, INDEX_NAME_PATTERN);
    }

    @Override
    public ELKUsageMetric metric() {
        return ELKUsageMetric.GPU;
    }

    @Override
    public SearchRequest buildStatsRequest(final String nodeName, final LocalDateTime from, final LocalDateTime to,
                                           final Duration interval) {
        final SearchSourceBuilder aggregation = statsQuery(nodeName, NODE, from, to)
                .size(0)
                .aggregation(ordered(AggregationBuilders.terms(DEVICE_NAME_AGGREGATION)
                        .field(path(FIELD_METRICS_TAGS, DEVICE_ID_RAW_FIELD))
                        .subAggregation(buildHistogram(interval))));
        return request(from, to, aggregation);
    }

    @Override
    protected List<MonitoringStats> parseStatsResponse(final SearchResponse response) {
        return Optional.ofNullable(response.getAggregations())
                .map(Aggregations::asList)
                .map(List::stream)
                .orElseGet(Stream::empty)
                .filter(agg -> DEVICE_NAME_AGGREGATION.equals(agg.getName()))
                .filter(agg -> agg instanceof Terms)
                .map(Terms.class::cast)
                .findFirst()
                .map(term -> Optional.of(term.getBuckets())
                        .map(List::stream)
                        .orElseGet(Stream::empty)
                        .collect(Collectors.toMap(
                                MultiBucketsAggregation.Bucket::getKeyAsString, this::getDetailsBuckets))
                        .entrySet().stream()
                        .flatMap(entry -> entry.getValue().stream().map(value -> Pair.of(entry.getKey(), value)))
                        .collect(Collectors.groupingBy(pair -> pair.getRight().getKeyAsString()))
                        .entrySet().stream()
                        .map(entry -> toChart(entry.getKey(), entry.getValue()))
                        .collect(Collectors.toList()))
                .orElse(Collections.emptyList());
    }

    @Override
    public SearchRequest buildRequest(final Collection<String> resourceIds, final LocalDateTime from,
                                      final LocalDateTime to, final Map<String, String> additional) {
        return null;
    }

    @Override
    public Map<String, Double> parseResponse(final SearchResponse response) {
        return Collections.emptyMap();
    }

    private List<MultiBucketsAggregation.Bucket> getDetailsBuckets(final Terms.Bucket bucket) {
        return Optional.ofNullable(bucket.getAggregations())
                .map(Aggregations::asList)
                .map(List::stream)
                .orElseGet(Stream::empty)
                .filter(agg -> DETAILS_HISTOGRAM.equals(agg.getName()))
                .findFirst()
                .filter(agg -> agg instanceof MultiBucketsAggregation)
                .map(MultiBucketsAggregation.class::cast)
                .map(MultiBucketsAggregation::getBuckets)
                .map(List::stream)
                .orElseGet(Stream::empty)
                .collect(Collectors.toList());
    }

    private MonitoringStats toChart(final String startDate,
                                    final List<Pair<String, MultiBucketsAggregation.Bucket>> pairs) {
        final MonitoringStats monitoringStats = new MonitoringStats();
        monitoringStats.setStartTime(startDate);
        monitoringStats.setGpuDetails(pairs.stream().collect(Collectors.toMap(Pair::getLeft, this::toGpuUsage)));
        return monitoringStats;
    }

    private MonitoringStats.GPUUsage toGpuUsage(final Pair<String, MultiBucketsAggregation.Bucket> pair) {
        final List<Aggregation> aggregations = aggregations(pair.getRight());
        return MonitoringStats.GPUUsage.builder()
                .gpuUtilization(buildMetrics(aggregations, UTILIZATION_GPU))
                .gpuMemoryUtilization(buildMetrics(aggregations, UTILIZATION_GPU_MEMORY))
                .gpuMemoryUsed(buildMetrics(aggregations, USED_GPU_MEMORY))
                .build();
    }

    private DateHistogramAggregationBuilder buildHistogram(final Duration interval) {
        final DateHistogramAggregationBuilder histogramBuilder = dateHistogram(DETAILS_HISTOGRAM, interval);

        // gpu utilization aggregations
        histogramBuilder
                .subAggregation(average(UTILIZATION_GPU, UTILIZATION_GPU))
                .subAggregation(min(UTILIZATION_GPU, UTILIZATION_GPU))
                .subAggregation(max(UTILIZATION_GPU, UTILIZATION_GPU));

        // gpu memory utilization aggregations
        histogramBuilder
                .subAggregation(average(UTILIZATION_GPU_MEMORY, UTILIZATION_GPU_MEMORY))
                .subAggregation(min(UTILIZATION_GPU_MEMORY, UTILIZATION_GPU_MEMORY))
                .subAggregation(max(UTILIZATION_GPU_MEMORY, UTILIZATION_GPU_MEMORY));

        // gpu memory usage aggregations
        histogramBuilder
                .subAggregation(average(USED_GPU_MEMORY, USED_GPU_MEMORY))
                .subAggregation(max(USED_GPU_MEMORY, USED_GPU_MEMORY))
                .subAggregation(min(USED_GPU_MEMORY, USED_GPU_MEMORY));
        return histogramBuilder;
    }

    private MonitoringMetrics buildMetrics(final List<Aggregation> aggregations, final String metricName) {
        return MonitoringMetrics.builder()
                .average(getDoubleValue(aggregations, AVG_AGGREGATION + metricName))
                .min(getDoubleValue(aggregations, MIN_AGGREGATION + metricName))
                .max(getDoubleValue(aggregations, MAX_AGGREGATION + metricName))
                .build();
    }

    private Double getDoubleValue(final List<Aggregation> aggregations, final String metricName) {
        return doubleValue(aggregations, metricName).orElse(null);
    }
}
