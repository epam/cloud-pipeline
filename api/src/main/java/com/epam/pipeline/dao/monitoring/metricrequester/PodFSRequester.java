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
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.search.aggregations.AggregationBuilders;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;

public class PodFSRequester extends FSRequester {

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
                podStatsQuery(nodeName, from, to)
                        .aggregation(AggregationBuilders.terms(AGGREGATION_DISK_NAME)
                                .field(path(FIELD_METRICS_TAGS, RESOURCE_ID))
                                .subAggregation(dateHistogram(DISKS_HISTOGRAM, interval)
                                        .subAggregation(average(AVG_AGGREGATION + USAGE, USAGE))
                                        .subAggregation(average(AVG_AGGREGATION + LIMIT, LIMIT)))));
    }

}
