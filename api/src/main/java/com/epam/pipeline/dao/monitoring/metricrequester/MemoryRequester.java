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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MemoryRequester extends AbstractMetricRequester {

    MemoryRequester(final RestHighLevelClient client) {
        super(client);
    }

    @Override
    protected ELKUsageMetric metric() {
        return ELKUsageMetric.MEM;
    }

    @Override
    public SearchRequest buildRequest(final Collection<String> resourceIds, final LocalDateTime from,
                                      final LocalDateTime to, final Map<String, String> additional) {
        final SearchSourceBuilder builder = new SearchSourceBuilder()
                .query(QueryBuilders.boolQuery()
                        .filter(QueryBuilders.termsQuery(path(FIELD_METRICS_TAGS, NODENAME_RAW_FIELD),
                                resourceIds))
                        .filter(QueryBuilders.termQuery(path(FIELD_METRICS_TAGS, FIELD_TYPE), NODE))
                        .filter(QueryBuilders.rangeQuery(metric().getTimestamp())
                                .from(from.toInstant(ZoneOffset.UTC).toEpochMilli())
                                .to(to.toInstant(ZoneOffset.UTC).toEpochMilli())))
                .size(0)
                .aggregation(AggregationBuilders.terms(NODENAME_FIELD_VALUE)
                        .field(path(FIELD_METRICS_TAGS, NODENAME_RAW_FIELD))
                        .size(resourceIds.size())
                        .subAggregation(AggregationBuilders.avg(AVG_AGGREGATION + NODE_UTILIZATION)
                                .field(field(NODE_UTILIZATION))));

        return new SearchRequest(getIndexNames(from, to)).types(metric().getName()).source(builder);
    }

    @Override
    public Map<String, Double> parseResponse(final SearchResponse response) {
        return ((Terms) response.getAggregations().get(NODENAME_FIELD_VALUE)).getBuckets().stream()
                .collect(Collectors.toMap(
                    b -> b.getKey().toString(),
                    b -> ((Avg) b.getAggregations().get(AVG_AGGREGATION + NODE_UTILIZATION)).getValue()));
    }

    @Override
    public List<MonitoringStats> requestStats(final String nodeName, final LocalDateTime from, final LocalDateTime to,
                                              final Duration interval) {
        final SearchSourceBuilder builder = new SearchSourceBuilder()
                .query(QueryBuilders.boolQuery()
                        .filter(QueryBuilders.termsQuery(path(FIELD_METRICS_TAGS, NODENAME_RAW_FIELD), nodeName))
                        .filter(QueryBuilders.termQuery(path(FIELD_METRICS_TAGS, FIELD_TYPE), NODE))
                        .filter(QueryBuilders.termQuery(path(FIELD_DOCUMENT_TYPE), metric().getName()))
                        .filter(QueryBuilders.rangeQuery(metric().getTimestamp())
                                .from(from.toInstant(ZoneOffset.UTC).toEpochMilli())
                                .to(to.toInstant(ZoneOffset.UTC).toEpochMilli())))
                .size(0)
                .aggregation(AggregationBuilders.dateHistogram(MEMORY_HISTOGRAM)
                        .field(metric().getTimestamp())
                        .interval(interval.toMillis())
                        .minDocCount(1L)
                        .subAggregation(AggregationBuilders.avg(AVG_AGGREGATION + MEMORY_UTILIZATION)
                                .field(field(USAGE)))
                        .subAggregation(AggregationBuilders.avg(AVG_AGGREGATION + MEMORY_CAPACITY)
                                .field(field(NODE_CAPACITY))));

        final SearchRequest request =
                new SearchRequest(getIndexNames(from, to)).types(metric().getName()).source(builder);
        return parse(executeRequest(request));
    }

    private List<MonitoringStats> parse(final SearchResponse response) {
        return Optional.ofNullable(response.getAggregations())
                .map(Aggregations::asList)
                .map(List::stream)
                .orElseGet(Stream::empty)
                .filter(it -> MEMORY_HISTOGRAM.equals(it.getName()))
                .filter(it -> it instanceof MultiBucketsAggregation)
                .map(MultiBucketsAggregation.class::cast)
                .findFirst()
                .map(MultiBucketsAggregation::getBuckets)
                .map(List::stream)
                .orElseGet(Stream::empty)
                .map(bucket -> {
                    final Optional<String> intervalStartOrEnd = Optional.ofNullable(bucket.getKeyAsString());
                    final List<Aggregation> aggregations = Optional.ofNullable(bucket.getAggregations())
                            .map(Aggregations::asList)
                            .orElseGet(Collections::emptyList);
                    final Optional<Long> utilization = value(aggregations, AVG_AGGREGATION + MEMORY_UTILIZATION)
                            .map(Double::longValue);
                    final Optional<Long> capacity = value(aggregations, AVG_AGGREGATION + MEMORY_CAPACITY)
                            .map(Double::longValue);
                    final MonitoringStats monitoringStats = new MonitoringStats();
                    intervalStartOrEnd.ifPresent(monitoringStats::setStartTime);
                    final MonitoringStats.MemoryUsage memoryUsage = new MonitoringStats.MemoryUsage();
                    utilization.ifPresent(memoryUsage::setUsage);
                    capacity.ifPresent(memoryUsage::setCapacity);
                    monitoringStats.setMemoryUsage(memoryUsage);
                    final MonitoringStats.ContainerSpec containerSpec = new MonitoringStats.ContainerSpec();
                    capacity.ifPresent(containerSpec::setMaxMemory);
                    monitoringStats.setContainerSpec(containerSpec);
                    return monitoringStats;
                })
                .collect(Collectors.toList());
    }

}
