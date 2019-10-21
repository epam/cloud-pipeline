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
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.AggregationBuilders;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PodFSRequester extends FSRequester {

    public static final String FILE_SYSTEM_METRICS_TIMESTAMP = "FilesystemMetricsTimestamp";
    public static final String FILESYSTEM = "filesystem";

    PodFSRequester(final RestHighLevelClient client) {
        super(client);
    }

    @Override
    protected ELKUsageMetric metric() {
        return ELKUsageMetric.POD_FS;
    }

    @Override
    public Map<String, Double> performRequest(final Collection<String> resourceIds,
                                              final LocalDateTime from, final LocalDateTime to) {
        throw new UnsupportedOperationException();
    }

    @Override
    public SearchRequest buildRequest(final Collection<String> resourceIds,
                                      final LocalDateTime from,
                                      final LocalDateTime to,
                                      final Map <String, String> additional) {

        throw new UnsupportedOperationException();
    }

    @Override
    protected SearchRequest buildStatsRequest(final String nodeName, final LocalDateTime from, final LocalDateTime to,
                                              final Duration interval) {
        return request(from, to,
                statsQuery(nodeName, POD_CONTAINER, from, to)
                        .sort(ELKUsageMetric.POD_FS.getTimestamp())
                        .aggregation(AggregationBuilders.terms(AGGREGATION_DISK_NAME)
                                .field(path(FIELD_METRICS_TAGS, RESOURCE_ID))
                                .subAggregation(dateHistogram(DISKS_HISTOGRAM, interval)
                                        .subAggregation(average(AVG_AGGREGATION + USAGE, USAGE))
                                        .subAggregation(average(AVG_AGGREGATION + LIMIT, LIMIT)))));
    }

    @Override
    protected List<MonitoringStats> parseStatsResponse(final SearchResponse response) {
        return Optional.ofNullable(response.getHits())
                .map(SearchHits::getHits)
                .map(Arrays::stream)
                .orElseGet(Stream::empty)
                .map(SearchHit::getSource)
                .map(this::toMonitoringStats)
                .collect(Collectors.toList());
    }

    private MonitoringStats toMonitoringStats(final Map<String, Object> hitSourceMap) {
        final Map<String, String> metricsTags = MapUtils.emptyIfNull((Map) hitSourceMap.get(FIELD_METRICS_TAGS));
        final Map<String, Object> metrics = MapUtils.emptyIfNull((Map) hitSourceMap.get(FIELD_METRICS));
        final MonitoringStats monitoringStats = new MonitoringStats();
        Optional.ofNullable((String) hitSourceMap.get(ELKUsageMetric.POD_FS.getTimestamp()))
                .ifPresent(monitoringStats::setStartTime);
        final MonitoringStats.DisksUsage disksUsage = new MonitoringStats.DisksUsage();
        final HashMap<String, MonitoringStats.DisksUsage.DiskStats> diskUsageMap = new HashMap<>();
        disksUsage.setStatsByDevices(diskUsageMap);
        monitoringStats.setDisksUsage(disksUsage);

        final MonitoringStats.DisksUsage.DiskStats diskStats = new MonitoringStats.DisksUsage.DiskStats();
        Optional.ofNullable((Number) MapUtils.emptyIfNull((Map) metrics.get(FILESYSTEM + "/" + LIMIT)).get(VALUE))
                .map(Number::longValue)
                .ifPresent(diskStats::setCapacity);
        Optional.ofNullable((Number) MapUtils.emptyIfNull((Map) metrics.get(FILESYSTEM + "/" + USAGE)).get(VALUE))
                .map(Number::longValue)
                .ifPresent(diskStats::setUsableSpace);
        disksUsage.getStatsByDevices().put(metricsTags.get(RESOURCE_ID), diskStats);
        return monitoringStats;
    }
}
