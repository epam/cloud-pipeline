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
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.metrics.avg.Avg;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CPURequester extends AbstractMetricRequester {

    CPURequester(final RestHighLevelClient client) {
        super(client);
    }

    @Override
    protected ELKUsageMetric metric() {
        return ELKUsageMetric.CPU;
    }

    @Override
    public SearchRequest buildRequest(final Collection<String> resourceIds, final LocalDateTime from,
                                      final LocalDateTime to, final Map<String, String> additional) {
        return request(from, to,
                new SearchSourceBuilder()
                        .query(QueryBuilders.boolQuery()
                                .filter(QueryBuilders.termsQuery(path(FIELD_METRICS_TAGS, FIELD_POD_NAME_RAW),
                                        resourceIds))
                                .filter(QueryBuilders.termQuery(path(FIELD_METRICS_TAGS, FIELD_NAMESPACE_NAME),
                                        DEFAULT))
                                .filter(QueryBuilders.termQuery(path(FIELD_METRICS_TAGS, FIELD_TYPE), POD_CONTAINER))
                                .filter(QueryBuilders.rangeQuery(metric().getTimestamp())
                                        .from(from.toInstant(ZoneOffset.UTC).toEpochMilli())
                                        .to(to.toInstant(ZoneOffset.UTC).toEpochMilli())))
                        .size(0)
                        .aggregation(AggregationBuilders.terms(AGGREGATION_POD_NAME)
                                .field(path(FIELD_METRICS_TAGS, FIELD_POD_NAME_RAW))
                                .size(resourceIds.size())
                                .subAggregation(average(AVG_AGGREGATION + USAGE_RATE, USAGE_RATE))));
    }

    @Override
    public Map<String, Double> parseResponse(final SearchResponse response) {
        return ((Terms)response.getAggregations().get(AGGREGATION_POD_NAME)).getBuckets().stream()
                .filter(b -> Double.isFinite(((Avg) b.getAggregations().get(AVG_AGGREGATION + USAGE_RATE)).getValue()))
                .collect(Collectors.toMap(
                    b -> b.getKey().toString(),
                    b -> ((Avg) b.getAggregations().get(AVG_AGGREGATION + USAGE_RATE)).getValue()));
    }

    @Override
    protected SearchRequest buildStatsRequest(final String nodeName, final LocalDateTime from, final LocalDateTime to,
                                              final Duration interval) {

        return request(from, to,
                statsQuery(nodeName, NODE, from, to)
                        .size(0)
                        .aggregation(dateHistogram(CPU_HISTOGRAM, interval)
                                .subAggregation(average(AVG_AGGREGATION + CPU_UTILIZATION, NODE_UTILIZATION))
                                .subAggregation(average(AVG_AGGREGATION + CPU_CAPACITY, NODE_CAPACITY))));
    }

    @Override
    protected List<MonitoringStats> parseStatsResponse(final SearchResponse response) {
        return Optional.ofNullable(response.getAggregations())
                .map(Aggregations::asList)
                .map(List::stream)
                .orElseGet(Stream::empty)
                .filter(it -> CPU_HISTOGRAM.equals(it.getName()))
                .filter(it -> it instanceof MultiBucketsAggregation)
                .map(MultiBucketsAggregation.class::cast)
                .findFirst()
                .map(MultiBucketsAggregation::getBuckets)
                .map(List::stream)
                .orElseGet(Stream::empty)
                .map(this::toMonitoringStats)
                .collect(Collectors.toList());
    }

    private MonitoringStats toMonitoringStats(final MultiBucketsAggregation.Bucket bucket) {
        final MonitoringStats monitoringStats = new MonitoringStats();
        Optional.ofNullable(bucket.getKeyAsString()).ifPresent(monitoringStats::setStartTime);
        final List<Aggregation> aggregations = aggregations(bucket);
        final Optional<Double> utilization = doubleValue(aggregations, AVG_AGGREGATION + CPU_UTILIZATION);
        final Optional<Integer> capacity = longValue(aggregations, AVG_AGGREGATION + CPU_CAPACITY)
                .map(Object::toString)
                // CPU capacity is a number of cores times 1000. Therefore last three digits can be omitted.
                .map(it -> it.substring(0, it.length() - 3))
                .map(Integer::valueOf);
        final MonitoringStats.CPUUsage cpuUsage = new MonitoringStats.CPUUsage();
        utilization.ifPresent(cpuUsage::setLoad);
        monitoringStats.setCpuUsage(cpuUsage);
        final MonitoringStats.ContainerSpec containerSpec = new MonitoringStats.ContainerSpec();
        capacity.ifPresent(containerSpec::setNumberOfCores);
        monitoringStats.setContainerSpec(containerSpec);
        return monitoringStats;
    }
}
