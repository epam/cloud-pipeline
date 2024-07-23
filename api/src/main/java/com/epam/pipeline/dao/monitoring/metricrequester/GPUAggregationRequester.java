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
import org.apache.commons.collections4.CollectionUtils;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramAggregationBuilder;
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

public class GPUAggregationRequester extends AbstractGPUMetricsRequester {
    private static final String AVG_UTILIZATION_GPU_FIELD = "avg_utilization_gpu";
    private static final String AVG_UTILIZATION_MEMORY_FIELD = "avg_utilization_memory";
    private static final String MIN_UTILIZATION_GPU_FIELD = "min_utilization_gpu";
    private static final String MIN_UTILIZATION_MEMORY_FIELD = "min_utilization_memory";
    private static final String MAX_UTILIZATION_GPU_FIELD = "max_utilization_gpu";
    private static final String MAX_UTILIZATION_MEMORY_FIELD = "max_utilization_memory";
    private static final String ACTIVE_GPUS_FIELD = "active_gpus";
    private static final String CHARTS_HISTOGRAM = "charts_histogram";

    public GPUAggregationRequester(final HeapsterElasticRestHighLevelClient client) {
        super(client);
    }

    @Override
    public ELKUsageMetric metric() {
        return ELKUsageMetric.GPU_AGGS;
    }

    @Override
    public SearchRequest buildStatsRequest(final String nodeName, final LocalDateTime from, final LocalDateTime to,
                                           final Duration interval) {
        final SearchSourceBuilder aggregation = statsQuery(nodeName, NODE, from, to)
                .size(1)
                .aggregation(buildHistogram(interval));
        return request(from, to, aggregation);
    }

    @Override
    protected List<MonitoringStats> parseStatsResponse(final SearchResponse response) {
        final List<Aggregation> aggregations = Optional.ofNullable(response.getAggregations())
                .map(Aggregations::asList)
                .map(List::stream)
                .orElseGet(Stream::empty)
                .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(aggregations)) {
            return Collections.emptyList();
        }
        final String deviceName = getDeviceName(response.getHits());
        return getBucketStream(aggregations)
                .map(this::bucketToChart)
                .peek(monitoringStats -> monitoringStats.setGpuDeviceName(deviceName))
                .collect(Collectors.toList());
    }

    @Override
    public SearchRequest buildRequest(final Collection<String> resourceIds, final LocalDateTime from,
                                      final LocalDateTime to, final Map<String, String> additional) {
        return null;
    }

    @Override
    public Map<String, Double> parseResponse(SearchResponse response) {
        return Collections.emptyMap();
    }

    private Stream<? extends MultiBucketsAggregation.Bucket> getBucketStream(final List<Aggregation> aggregations) {
        return aggregations.stream()
                .filter(agg -> CHARTS_HISTOGRAM.equals(agg.getName()))
                .filter(agg -> agg instanceof MultiBucketsAggregation)
                .map(MultiBucketsAggregation.class::cast)
                .findFirst()
                .map(MultiBucketsAggregation::getBuckets)
                .map(List::stream)
                .orElseGet(Stream::empty);
    }

    private DateHistogramAggregationBuilder buildHistogram(final Duration interval) {
        final DateHistogramAggregationBuilder histogramBuilder = dateHistogram(CHARTS_HISTOGRAM, interval);

        // gpu utilization aggregations
        histogramBuilder
                .subAggregation(average(AVG_AGGREGATION + GPU_UTILIZATION, AVG_UTILIZATION_GPU_FIELD))
                .subAggregation(min(MIN_AGGREGATION + GPU_UTILIZATION, MIN_UTILIZATION_GPU_FIELD))
                .subAggregation(max(MAX_AGGREGATION + GPU_UTILIZATION, MAX_UTILIZATION_GPU_FIELD));

        // gpu memory utilization aggregations
        histogramBuilder
                .subAggregation(average(AVG_AGGREGATION + MEMORY_UTILIZATION, AVG_UTILIZATION_MEMORY_FIELD))
                .subAggregation(min(MIN_AGGREGATION + MEMORY_UTILIZATION, MIN_UTILIZATION_MEMORY_FIELD))
                .subAggregation(max(MAX_AGGREGATION + MEMORY_UTILIZATION, MAX_UTILIZATION_MEMORY_FIELD));

        // active gpu aggregations
        histogramBuilder
                .subAggregation(average(ACTIVE_GPUS_FIELD, ACTIVE_GPUS_FIELD))
                .subAggregation(max(ACTIVE_GPUS_FIELD, ACTIVE_GPUS_FIELD))
                .subAggregation(min(ACTIVE_GPUS_FIELD, ACTIVE_GPUS_FIELD));
        return histogramBuilder;
    }

    private MonitoringStats bucketToChart(final MultiBucketsAggregation.Bucket bucket) {
        final MonitoringStats monitoringStats = new MonitoringStats();
        Optional.ofNullable(bucket.getKeyAsString()).ifPresent(monitoringStats::setStartTime);
        monitoringStats.setGpuUsage(toGpuUsage(bucket));
        return monitoringStats;
    }

    private MonitoringStats.GPUUsage toGpuUsage(final MultiBucketsAggregation.Bucket bucket) {
        final List<Aggregation> aggregations = aggregations(bucket);
        return MonitoringStats.GPUUsage.builder()
                .gpuUtilization(buildMetrics(aggregations, GPU_UTILIZATION))
                .gpuMemoryUtilization(buildMetrics(aggregations, MEMORY_UTILIZATION))
                .activeGpus(MonitoringMetrics.builder()
                        .average(getDoubleValue(aggregations, AVG_AGGREGATION + ACTIVE_GPUS_FIELD))
                        .min(getDoubleValue(aggregations, MIN_AGGREGATION + ACTIVE_GPUS_FIELD))
                        .max(getDoubleValue(aggregations, MAX_AGGREGATION + ACTIVE_GPUS_FIELD))
                        .build())
                .build();
    }

    private MonitoringMetrics buildMetrics(final List<Aggregation> aggregations, final String metricName) {
        return MonitoringMetrics.builder()
                .average(getDoubleValue(aggregations, AVG_AGGREGATION + AVG_AGGREGATION + metricName))
                .min(getDoubleValue(aggregations, MIN_AGGREGATION + MIN_AGGREGATION + metricName))
                .max(getDoubleValue(aggregations, MAX_AGGREGATION + MAX_AGGREGATION + metricName))
                .build();
    }
}
