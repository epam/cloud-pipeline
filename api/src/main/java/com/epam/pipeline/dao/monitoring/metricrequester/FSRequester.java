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
import org.apache.commons.lang3.tuple.Pair;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FSRequester extends AbstractMetricRequester {

    FSRequester(final HeapsterElasticRestHighLevelClient client) {
        super(client);
    }

    @Override
    protected ELKUsageMetric metric() {
        return ELKUsageMetric.FS;
    }

    @Override
    public SearchRequest buildRequest(final Collection<String> resourceIds, final LocalDateTime from,
                                      final LocalDateTime to, final Map <String, String> additional) {
        return request(from, to,
                new SearchSourceBuilder().query(
                        QueryBuilders.boolQuery()
                                .filter(QueryBuilders.termsQuery(path(FIELD_METRICS_TAGS, FIELD_NODENAME_RAW),
                                        resourceIds))
                                .filter(QueryBuilders.termQuery(path(FIELD_METRICS_TAGS, FIELD_TYPE), NODE))
                                .filter(QueryBuilders.rangeQuery(metric().getTimestamp())
                                        .from(from.toInstant(ZoneOffset.UTC).toEpochMilli())
                                        .to(to.toInstant(ZoneOffset.UTC).toEpochMilli())))
                        .size(0)
                        .aggregation(ordered(AggregationBuilders.terms(AGGREGATION_NODE_NAME))
                                .field(path(FIELD_METRICS_TAGS, FIELD_NODENAME_RAW))
                                .subAggregation(ordered(AggregationBuilders.terms(AGGREGATION_DISK_NAME))
                                        .field(path(FIELD_METRICS_TAGS, RESOURCE_ID))
                                        .subAggregation(average(AVG_AGGREGATION + LIMIT, LIMIT))
                                        .subAggregation(average(AVG_AGGREGATION + USAGE, USAGE)))));
    }

    @Override
    public Map<String, Double> parseResponse(final SearchResponse response) {
        return ((Terms) response.getAggregations().get(AGGREGATION_NODE_NAME)).getBuckets().stream().collect(
                Collectors.toMap(MultiBucketsAggregation.Bucket::getKeyAsString,
                    b -> ((Terms) b.getAggregations().get(AGGREGATION_DISK_NAME)).getBuckets().stream()
                            .filter(diskBucket -> !SWAP_FILESYSTEM.equalsIgnoreCase(diskBucket.getKeyAsString()))
                            .map(this::toUsageAndLimit)
                            .collect(Collectors.collectingAndThen(
                                    Collectors.reducing(Pair.of(0d, 0d), this::toMergedUsageAndLimit),
                                    this::toUsageRate))));
    }

    private Pair<Double, Double> toUsageAndLimit(final Terms.Bucket diskBucket) {
        final double limit = ((Avg) diskBucket.getAggregations().get(AVG_AGGREGATION + LIMIT)).getValue();
        final double usage = ((Avg) diskBucket.getAggregations().get(AVG_AGGREGATION + USAGE)).getValue();
        return Pair.of(usage, limit);
    }

    private Pair<Double, Double> toMergedUsageAndLimit(final Pair<Double, Double> p1, final Pair<Double, Double> p2) {
        return Pair.of(p1.getLeft() + p2.getLeft(), p1.getRight() + p2.getRight());
    }

    private Double toUsageRate(final Pair<Double, Double> p) {
        return getRate(p.getLeft(), p.getRight());
    }

    private Double getRate(final Double usage, final Double limit) {
        return usage == null || limit == null ? null : usage / limit;
    }

    @Override
    protected SearchRequest buildStatsRequest(final String nodeName, final LocalDateTime from, final LocalDateTime to,
                                              final Duration interval) {
        return request(from, to,
                statsQuery(nodeName, NODE, from, to)
                        .size(0)
                        .aggregation(ordered(AggregationBuilders.terms(AGGREGATION_DISK_NAME))
                                .field(path(FIELD_METRICS_TAGS, RESOURCE_ID))
                                .subAggregation(dateHistogram(DISKS_HISTOGRAM, interval)
                                        .subAggregation(average(AVG_AGGREGATION + USAGE, USAGE))
                                        .subAggregation(average(AVG_AGGREGATION + LIMIT, LIMIT)))));
    }

    @Override
    protected List<MonitoringStats> parseStatsResponse(final SearchResponse response) {
        return Optional.ofNullable(response.getAggregations())
                .map(Aggregations::asList)
                .map(List::stream)
                .orElseGet(Stream::empty)
                .filter(it -> AGGREGATION_DISK_NAME.equals(it.getName()))
                .filter(it -> it instanceof Terms)
                .map(Terms.class::cast)
                .findFirst()
                .map(term -> Optional.of(term.getBuckets())
                        .map(List::stream)
                        .orElseGet(Stream::empty)
                        .flatMap(diskBucket -> Optional.ofNullable(diskBucket.getAggregations())
                                .map(Aggregations::asList)
                                .map(List::stream)
                                .orElseGet(Stream::empty)
                                .filter(it -> DISKS_HISTOGRAM.equals(it.getName()))
                                .findFirst()
                                .filter(it -> it instanceof MultiBucketsAggregation)
                                .map(MultiBucketsAggregation.class::cast)
                                .map(MultiBucketsAggregation::getBuckets)
                                .map(List::stream)
                                .orElseGet(Stream::empty)
                                .map(monitoringBucket -> toMonitoringStats(diskBucket, monitoringBucket))))
                .orElseGet(Stream::empty)
                .collect(Collectors.toList());
    }

    private MonitoringStats toMonitoringStats(final Terms.Bucket diskBucket,
                                              final MultiBucketsAggregation.Bucket monitoringBucket) {
        final MonitoringStats monitoringStats = new MonitoringStats();
        Optional.ofNullable(monitoringBucket.getKeyAsString()).ifPresent(monitoringStats::setStartTime);
        final List<Aggregation> aggregations = aggregations(monitoringBucket);
        final Optional<Long> memoryUtilization = longValue(aggregations, AVG_AGGREGATION + USAGE);
        final Optional<Long> memoryCapacity = longValue(aggregations, AVG_AGGREGATION + LIMIT);
        final MonitoringStats.DisksUsage disksUsage = new MonitoringStats.DisksUsage();
        final HashMap<String, MonitoringStats.DisksUsage.DiskStats> diskUsageMap = new HashMap<>();
        disksUsage.setStatsByDevices(diskUsageMap);
        monitoringStats.setDisksUsage(disksUsage);
        final MonitoringStats.DisksUsage.DiskStats diskStats = new MonitoringStats.DisksUsage.DiskStats();
        memoryUtilization.ifPresent(diskStats::setUsableSpace);
        memoryCapacity.ifPresent(diskStats::setCapacity);
        Optional.ofNullable(diskBucket.getKeyAsString())
                .ifPresent(disk -> disksUsage.getStatsByDevices().put(disk, diskStats));
        return monitoringStats;
    }
}
