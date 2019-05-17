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
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramInterval;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.metrics.avg.Avg;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FSRequester extends AbstractMetricRequester {

    private static final String DISKNAME_AGG_NAME = "diskname";
    private static final String DISKS = "disks";
    private static final String DISKS_HISTOGRAM = "disks_histogram";

    FSRequester(final RestHighLevelClient client) {
        super(client);
    }

    @Override
    protected ELKUsageMetric metric() {
        return ELKUsageMetric.FS;
    }

    @Override
    public Map<String, Double> performRequest(final Collection<String> resourceIds,
                                              final LocalDateTime from, final LocalDateTime to) {
        final Map<String, String> diskNamesResponse =
                parseDiskNamesResponse(executeRequest(buildDiskNameRequest(resourceIds, from, to)));
        return parseResponse(
                executeRequest(
                        buildRequest(
                                resourceIds, from, to, diskNamesResponse))
        );
    }

    @Override
    public SearchRequest buildRequest(final Collection<String> resourceIds, final LocalDateTime from,
                                      final LocalDateTime to, final Map <String, String> additional) {

        final SearchSourceBuilder builder = new SearchSourceBuilder().query(
                QueryBuilders.boolQuery().must(getQueryWithNodeToDiskMatching(resourceIds, additional))
                        .filter(QueryBuilders.termQuery(path(FIELD_METRICS_TAGS, FIELD_TYPE), NODE))
                        .filter(QueryBuilders.rangeQuery(metric().getTimestamp())
                                .from(from.toInstant(ZoneOffset.UTC).toEpochMilli())
                                .to(to.toInstant(ZoneOffset.UTC).toEpochMilli())))
                .size(0)
                .aggregation(AggregationBuilders.terms(NODENAME_FIELD_VALUE)
                        .field(path(FIELD_METRICS_TAGS, NODENAME_RAW_FIELD))
                            .subAggregation(AggregationBuilders.avg(AVG_AGGREGATION + LIMIT)
                                    .field(field(LIMIT)))
                            .subAggregation(AggregationBuilders.avg(AVG_AGGREGATION + USAGE)
                                    .field(field(USAGE))));

        return new SearchRequest(getIndexNames(from, to)).types(ELKUsageMetric.FS.getName()).source(builder);
    }

    private BoolQueryBuilder getQueryWithNodeToDiskMatching(final Collection<String> resourceIds,
                                                            final Map<String, String> additional) {
        final BoolQueryBuilder result = QueryBuilders.boolQuery();
        resourceIds.forEach(node -> {
            final String nodeDiskName = additional.get(node);
            if (nodeDiskName != null) {
                final BoolQueryBuilder query = QueryBuilders.boolQuery();
                final List<QueryBuilder> must = query.must();
                must.add(QueryBuilders.termQuery(path(FIELD_METRICS_TAGS, NODENAME_RAW_FIELD), node));
                must.add(QueryBuilders.termQuery(path(FIELD_METRICS_TAGS, RESOURCE_ID), nodeDiskName));
                result.should().add(query);
            }
        });
        return result;
    }

    @Override
    public Map<String, Double> parseResponse(final SearchResponse response) {
        return ((Terms) response.getAggregations().get(NODENAME_FIELD_VALUE)).getBuckets().stream().collect(
            HashMap::new,
            (m, b) -> {
                final double limit = ((Avg) b.getAggregations().get(AVG_AGGREGATION + LIMIT)).getValue();
                final double usage = ((Avg) b.getAggregations().get(AVG_AGGREGATION + USAGE)).getValue();
                m.put(b.getKey().toString(), getRate(usage, limit));
            },
            Map::putAll
        );
    }


    private SearchRequest buildDiskNameRequest(final Collection<String> resourceIds,
                                               final LocalDateTime from, final LocalDateTime to) {
        final SearchSourceBuilder builder = new SearchSourceBuilder()
                .query(QueryBuilders.boolQuery()
                        .filter(QueryBuilders.termsQuery(path(FIELD_METRICS_TAGS, NODENAME_RAW_FIELD),
                                resourceIds))
                        .filter(QueryBuilders.termQuery(path(FIELD_METRICS_TAGS, FIELD_TYPE), POD_CONTAINER))
                        .filter(QueryBuilders.rangeQuery(ELKUsageMetric.FS.getTimestamp())
                                .from(from.toInstant(ZoneOffset.UTC).toEpochMilli())
                                .to(to.toInstant(ZoneOffset.UTC).toEpochMilli())))
                .size(0)
                .aggregation(AggregationBuilders.terms(NODENAME_FIELD_VALUE)
                    .field(path(FIELD_METRICS_TAGS, NODENAME_RAW_FIELD))
                        .subAggregation(AggregationBuilders.terms(DISKNAME_AGG_NAME)
                            .field(path(FIELD_METRICS_TAGS, RESOURCE_ID))));

        return new SearchRequest(getIndexNames(from, to)).types(ELKUsageMetric.FS.getName()).source(builder);
    }

    private Map<String, String> parseDiskNamesResponse(final SearchResponse response) {
        return  ((Terms) response.getAggregations().get(NODENAME_FIELD_VALUE))
                .getBuckets().stream()
                .map((b) -> new ImmutablePair<>(
                        b.getKey().toString(),
                        ((Terms) b.getAggregations().get(DISKNAME_AGG_NAME)).getBuckets().stream().findFirst()
                                .map(d -> d.getKey().toString()).orElse(null)))

                .collect(HashMap::new, (m, b) -> m.put(b.getKey(), b.getValue()), Map::putAll);
    }

    private Double getRate(final Double usage, final Double limit) {
        return usage == null || limit == null ? null : usage / limit;
    }

    @Override
    public List<MonitoringStats> requestStats(final Collection<String> resourceIds, final LocalDateTime from,
                                              final LocalDateTime to) {
        final SearchSourceBuilder builder = new SearchSourceBuilder()
                .query(QueryBuilders.boolQuery()
                        .filter(QueryBuilders.termsQuery(path(FIELD_METRICS_TAGS, NODENAME_RAW_FIELD), resourceIds))
                        .filter(QueryBuilders.termQuery(path(FIELD_METRICS_TAGS, FIELD_TYPE), NODE))
                        .filter(QueryBuilders.termQuery(path(FIELD_DOCUMENT_TYPE), metric().getName())))
                .size(0)
                .aggregation(AggregationBuilders.terms(DISKS)
                        .field(path(FIELD_METRICS_TAGS, RESOURCE_ID))
                        .subAggregation(AggregationBuilders.dateHistogram(DISKS_HISTOGRAM)
                                .field(metric().getTimestamp())
                                .interval(1L)
                                .dateHistogramInterval(DateHistogramInterval.minutes(5))
                                .subAggregation(AggregationBuilders.avg(AVG_AGGREGATION + USAGE)
                                        .field(field(USAGE)))
                                .subAggregation(AggregationBuilders.avg(AVG_AGGREGATION + LIMIT)
                                        .field(field(LIMIT)))));

        final SearchRequest request =
                new SearchRequest(getIndexNames(from, to)).types(metric().getName()).source(builder);
        return parse(executeRequest(request));
    }

    private List<MonitoringStats> parse(final SearchResponse response) {
        return Optional.ofNullable(response.getAggregations())
                .map(Aggregations::asList)
                .map(List::stream)
                .orElseGet(Stream::empty)
                .filter(it -> DISKS.equals(it.getName()))
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
                                .map(monitoringBucket -> {
                                    final Optional<String> disk = Optional.ofNullable(diskBucket.getKeyAsString());
                                    final Optional<String> intervalStartOrEnd = Optional.ofNullable(monitoringBucket.getKeyAsString());
                                    final List<Aggregation> aggregations = Optional.ofNullable(monitoringBucket.getAggregations())
                                            .map(Aggregations::asList)
                                            .orElseGet(Collections::emptyList);
                                    final Optional<Long> memoryUtilization = value(aggregations, AVG_AGGREGATION + USAGE)
                                            .map(Double::longValue);
                                    final Optional<Long> memoryCapacity = value(aggregations, AVG_AGGREGATION + LIMIT)
                                            .map(Double::longValue);
                                    final MonitoringStats monitoringStats = new MonitoringStats();
                                    intervalStartOrEnd.ifPresent(monitoringStats::setStartTime);
                                    final MonitoringStats.DisksUsage disksUsage = new MonitoringStats.DisksUsage();
                                    final HashMap<String, MonitoringStats.DisksUsage.DiskStats> diskUsageMap = new HashMap<>();
                                    disksUsage.setStatsByDevices(diskUsageMap);
                                    monitoringStats.setDisksUsage(disksUsage);
                                    final MonitoringStats.DisksUsage.DiskStats diskStats = new MonitoringStats.DisksUsage.DiskStats();
                                    memoryUtilization.ifPresent(diskStats::setUsableSpace);
                                    memoryCapacity.ifPresent(diskStats::setCapacity);
                                    disk.ifPresent(d -> disksUsage.getStatsByDevices().put(d, diskStats));
                                    return monitoringStats;
                                })))
                .orElseGet(Stream::empty)
                .collect(Collectors.toList());
    }
}
