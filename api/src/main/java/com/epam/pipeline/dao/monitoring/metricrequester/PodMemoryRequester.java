/*
 * Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
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
import org.elasticsearch.action.search.SearchResponse;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * Overrides {@link MemoryRequester} class to support memory usage statistics for particular pod.
 * Since heapster provides different metrics fields for pod and node usage statistics
 * memory/node_capacity field replaced with memory/limit filed.
 */
public class PodMemoryRequester extends MemoryRequester {

    PodMemoryRequester(final HeapsterElasticRestHighLevelClient client) {
        super(client);
    }

    @Override
    protected ELKUsageMetric metric() {
        return ELKUsageMetric.POD_MEM;
    }

    @Override
    protected SearchRequest buildStatsRequest(final String nodeName, final LocalDateTime from,
                                              final LocalDateTime to, final Duration interval,
                                              final Long runId) {
        return request(from, to,
                statsQuery(nodeName, POD, from, to, runId)
                        .size(0)
                        .aggregation(dateHistogram(MEMORY_HISTOGRAM, interval)
                                .subAggregation(average(MEMORY_UTILIZATION, WORKING_SET))
                                .subAggregation(max(MEMORY_UTILIZATION, WORKING_SET))
                                .subAggregation(average(MEMORY_CAPACITY, LIMIT))));
    }

    @Override
    public SearchRequest buildRequest(final Collection<String> resourceIds, final LocalDateTime from,
                                      final LocalDateTime to, final Map<String, String> additional) {
        return null;
    }

    @Override
    public Map<String, Double> parseResponse(final SearchResponse response) {
        return Collections.emptyMap();
    }
}
