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
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramInterval;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class NetworkRequester extends AbstractMetricRequester {

    private static final String NETWORK_HISTOGRAM = "network_histogram";
    private static final String RX_RATE = "rx_rate";
    private static final String TX_RATE = "tx_rate";
    public static final String NETWORK_INTERFACE = "summary";

    NetworkRequester(final RestHighLevelClient client) {
        super(client);
    }

    @Override
    protected ELKUsageMetric metric() {
        return ELKUsageMetric.NETWORK;
    }

    @Override
    public SearchRequest buildRequest(final Collection<String> resourceIds, final LocalDateTime from, final LocalDateTime to, final Map<String, String> additional) {
        // TODO 17.05.19: Method NetworkRequester::buildRequest is not implemented yet.
        throw new RuntimeException("Method NetworkRequester::buildRequest is not implemented yet.");
    }

    @Override
    public Map<String, Double> parseResponse(final SearchResponse response) {
        // TODO 17.05.19: Method NetworkRequester::buildRequest is not implemented yet.
        throw new RuntimeException("Method NetworkRequester::buildRequest is not implemented yet.");
    }

    @Override
    public List<MonitoringStats> requestStats(final Collection<String> resourceIds, final LocalDateTime from, final LocalDateTime to) {
        final SearchSourceBuilder builder = new SearchSourceBuilder()
                .query(QueryBuilders.boolQuery()
                        .filter(QueryBuilders.termsQuery(path(FIELD_METRICS_TAGS, NODENAME_RAW_FIELD), resourceIds))
                        .filter(QueryBuilders.termQuery(path(FIELD_METRICS_TAGS, FIELD_TYPE), NODE))
                        .filter(QueryBuilders.termQuery(path(FIELD_DOCUMENT_TYPE), metric().getName())))
                .size(0)
                .aggregation(AggregationBuilders.dateHistogram(NETWORK_HISTOGRAM)
                        .field(metric().getTimestamp())
                        .interval(1L)
                        .dateHistogramInterval(DateHistogramInterval.minutes(5))
                        .subAggregation(AggregationBuilders.avg(AVG_AGGREGATION + RX_RATE)
                                .field(field(RX_RATE)))
                        .subAggregation(AggregationBuilders.avg(AVG_AGGREGATION + TX_RATE)
                                .field(field(TX_RATE))));

        final SearchRequest request =
                new SearchRequest(getIndexNames(from, to)).types(metric().getName()).source(builder);
        return parse(executeRequest(request));
    }

    private List<MonitoringStats> parse(final SearchResponse response) {
        return Optional.ofNullable(response.getAggregations())
                .map(Aggregations::asList)
                .map(List::stream)
                .orElseGet(Stream::empty)
                .filter(it -> NETWORK_HISTOGRAM.equals(it.getName()))
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
                    final Optional<Long> rxRate = value(aggregations, AVG_AGGREGATION + RX_RATE)
                            .map(Double::longValue);
                    final Optional<Long> txRate = value(aggregations, AVG_AGGREGATION + TX_RATE)
                            .map(Double::longValue);
                    final MonitoringStats monitoringStats = new MonitoringStats();
                    intervalStartOrEnd.ifPresent(monitoringStats::setStartTime);
                    final MonitoringStats.NetworkUsage.NetworkStats stats = new MonitoringStats.NetworkUsage.NetworkStats();
                    rxRate.ifPresent(stats::setRxBytes);
                    txRate.ifPresent(stats::setTxBytes);
                    final HashMap<String, MonitoringStats.NetworkUsage.NetworkStats> statsMap = new HashMap<>();
                    statsMap.put(NETWORK_INTERFACE, stats);
                    final MonitoringStats.NetworkUsage networkUsage = new MonitoringStats.NetworkUsage();
                    networkUsage.setStatsByInterface(statsMap);
                    monitoringStats.setNetworkUsage(networkUsage);
                    return monitoringStats;
                })
                .collect(Collectors.toList());
    }
}
