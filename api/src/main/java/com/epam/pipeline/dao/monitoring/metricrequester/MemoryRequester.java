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
import org.apache.commons.collections4.MapUtils;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.metrics.avg.Avg;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MemoryRequester extends AbstractMetricRequester implements MonitoringRequester {

    MemoryRequester(final RestHighLevelClient client) {
        super(client);
    }

    @Override
    public SearchRequest buildRequest(final Collection<String> resourceIds, final LocalDateTime from,
                                      final LocalDateTime to, final Map<String, String> additional) {
        final SearchSourceBuilder builder = new SearchSourceBuilder()
                .query(QueryBuilders.boolQuery()
                        .filter(QueryBuilders.termsQuery(path(FIELD_METRICS_TAGS, NODENAME_RAW_FIELD),
                                resourceIds))
                        .filter(QueryBuilders.termQuery(path(FIELD_METRICS_TAGS, FIELD_TYPE), NODE))
                        .filter(QueryBuilders.rangeQuery(ELKUsageMetric.MEM.getTimestamp())
                                .from(from.toInstant(ZoneOffset.UTC).toEpochMilli())
                                .to(to.toInstant(ZoneOffset.UTC).toEpochMilli())))
                .size(0)
                .aggregation(AggregationBuilders.terms(NODENAME_FIELD_VALUE)
                        .field(path(FIELD_METRICS_TAGS, NODENAME_RAW_FIELD))
                        .size(resourceIds.size())
                        .subAggregation(AggregationBuilders.avg(AVG_AGGREGATION + NODE_UTILIZATION)
                                .field("Metrics." + ELKUsageMetric.MEM.getName()
                                        + "/" + NODE_UTILIZATION + ".value")));

        return new SearchRequest(getIndexNames(from, to)).types(ELKUsageMetric.MEM.getName()).source(builder);
    }

    @Override
    public Map<String, Double> parseResponse(final SearchResponse response) {
        return ((Terms) response.getAggregations().get(NODENAME_FIELD_VALUE)).getBuckets().stream()
                .collect(Collectors.toMap(
                    b -> b.getKey().toString(),
                    b -> ((Avg) b.getAggregations().get(AVG_AGGREGATION + NODE_UTILIZATION)).getValue()));
    }

    @Override
    public List<MonitoringStats> requestStats(final Collection<String> resourceIds, final LocalDateTime from,
                                              final LocalDateTime to) {
        final SearchSourceBuilder builder = new SearchSourceBuilder()
                .query(QueryBuilders.boolQuery()
                        .filter(QueryBuilders.termsQuery(path(FIELD_METRICS_TAGS, NODENAME_RAW_FIELD),
                                resourceIds))
                        .filter(QueryBuilders.termQuery(path(FIELD_METRICS_TAGS, FIELD_TYPE), NODE))
                        .filter(QueryBuilders.rangeQuery(ELKUsageMetric.MEM.getTimestamp())
                                .from(from.toInstant(ZoneOffset.UTC).toEpochMilli())
                                .to(to.toInstant(ZoneOffset.UTC).toEpochMilli())));

        final SearchRequest request =
                new SearchRequest(getIndexNames(from, to)).types(ELKUsageMetric.MEM.getName()).source(builder);
        return parse(executeRequest(request));
    }

    private List<MonitoringStats> parse(final SearchResponse response) {
        return Optional.ofNullable(response.getHits())
                .map(SearchHits::getHits)
                .map(Arrays::stream)
                .orElseGet(Stream::empty)
                .map(hit -> {
                    final Map<String, Object> source = MapUtils.emptyIfNull(hit.getSource());
                    final String timestamp = (String) source.get(ELKUsageMetric.MEM.getTimestamp());
                    final MonitoringStats monitoringStats = new MonitoringStats();
                    monitoringStats.setStartTime(timestamp);
                    final Optional<Long> memoryUsageValue = Optional.of(source)
                            .map(map -> map.get("Metrics"))
                            .filter(this::isMap)
                            .map(this::toMap)
                            .map(map -> map.get("memory/usage"))
                            .filter(this::isMap)
                            .map(this::toMap)
                            .map(map -> map.get("value"))
                            .filter(Long.class::isInstance)
                            .map(it -> (Long) it);
                    final MonitoringStats.MemoryUsage memoryUsage = new MonitoringStats.MemoryUsage();
                    memoryUsageValue.ifPresent(memoryUsage::setUsage);
                    monitoringStats.setMemoryUsage(memoryUsage);
                    // TODO 15.05.19: Add capacity.
                    return monitoringStats;
                })
                .collect(Collectors.toList());
    }

    private boolean isMap(final Object object) {
        return object instanceof Map;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(final Object object) {
        return (Map<String, Object>) object;
    }
}
