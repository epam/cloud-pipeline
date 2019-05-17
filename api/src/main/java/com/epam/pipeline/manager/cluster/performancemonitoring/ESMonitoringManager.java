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

package com.epam.pipeline.manager.cluster.performancemonitoring;

import com.epam.pipeline.dao.monitoring.metricrequester.AbstractMetricRequester;
import com.epam.pipeline.entity.cluster.monitoring.ELKUsageMetric;
import com.epam.pipeline.entity.cluster.monitoring.MonitoringStats;
import com.epam.pipeline.entity.utils.DateUtils;
import lombok.RequiredArgsConstructor;
import org.elasticsearch.client.RestHighLevelClient;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RequiredArgsConstructor
public class ESMonitoringManager implements UsageMonitoringManager {

    private final RestHighLevelClient client;

    @Override
    public List<MonitoringStats> getStatsForNode(final String nodeName) {
        final long timeRange = 20;
        final LocalDateTime now = DateUtils.nowUTC();
        final LocalDateTime end = now;
        final LocalDateTime start = end.minusMinutes(timeRange);
        return Stream.of(ELKUsageMetric.CPU, ELKUsageMetric.MEM, ELKUsageMetric.FS, ELKUsageMetric.NETWORK)
                .map(it -> AbstractMetricRequester.getStatsRequester(it, client))
                .map(it -> it.requestStats(Collections.singletonList(nodeName), start, end))
                .flatMap(List::stream)
                .sorted(Comparator.comparing(MonitoringStats::getStartTime))
                .collect(Collectors.toList());
    }

    @Override
    public long getDiskAvailableForDocker(final String nodeName, final String podId, final String dockerImage) {
        // TODO 15.05.19: Method ESMonitoringManager::getDiskAvailableForDocker is not implemented yet.
        throw new RuntimeException("Method ESMonitoringManager::getDiskAvailableForDocker is not implemented yet.");
    }
}
