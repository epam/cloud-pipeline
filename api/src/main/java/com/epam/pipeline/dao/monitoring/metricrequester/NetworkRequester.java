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
import org.apache.commons.lang.NotImplementedException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class NetworkRequester extends AbstractMetricRequester {

    NetworkRequester(final RestHighLevelClient client) {
        super(client);
    }

    @Override
    protected ELKUsageMetric metric() {
        return ELKUsageMetric.NETWORK;
    }

    @Override
    public SearchRequest buildRequest(final Collection<String> resourceIds, final LocalDateTime from,
                                      final LocalDateTime to, final Map<String, String> additional) {
        throw new NotImplementedException("Method NetworkRequester::buildRequest is not implemented yet.");
    }

    @Override
    public Map<String, Double> parseResponse(final SearchResponse response) {
        throw new NotImplementedException("Method NetworkRequester::buildRequest is not implemented yet.");
    }

    @Override
    protected SearchRequest buildStatsRequest(final String nodeName, final LocalDateTime from, final LocalDateTime to,
                                              final Duration interval) {
        return request(from, to,
                nodeStatsQuery(nodeName, from, to)
                        .aggregation(dateHistogram(NETWORK_HISTOGRAM, interval)
                                .subAggregation(average(AVG_AGGREGATION + RX_RATE, RX_RATE))
                                .subAggregation(average(AVG_AGGREGATION + TX_RATE, TX_RATE))));
    }

    @Override
    protected List<MonitoringStats> parseStatsResponse(final SearchResponse response) {
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
                .map(this::toMonitoringStats)
                .collect(Collectors.toList());
    }

    private MonitoringStats toMonitoringStats(final MultiBucketsAggregation.Bucket bucket) {
        final MonitoringStats monitoringStats = new MonitoringStats();
        Optional.ofNullable(bucket.getKeyAsString()).ifPresent(monitoringStats::setStartTime);
        final List<Aggregation> aggregations = aggregations(bucket);
        final Optional<Long> rxRate = longValue(aggregations, AVG_AGGREGATION + RX_RATE);
        final Optional<Long> txRate = longValue(aggregations, AVG_AGGREGATION + TX_RATE);
        final MonitoringStats.NetworkUsage.NetworkStats stats = new MonitoringStats.NetworkUsage.NetworkStats();
        rxRate.ifPresent(stats::setRxBytes);
        txRate.ifPresent(stats::setTxBytes);
        final HashMap<String, MonitoringStats.NetworkUsage.NetworkStats> statsMap = new HashMap<>();
        statsMap.put(SYNTHETIC_NETWORK_INTERFACE, stats);
        final MonitoringStats.NetworkUsage networkUsage = new MonitoringStats.NetworkUsage();
        networkUsage.setStatsByInterface(statsMap);
        monitoringStats.setNetworkUsage(networkUsage);
        return monitoringStats;
    }
}
