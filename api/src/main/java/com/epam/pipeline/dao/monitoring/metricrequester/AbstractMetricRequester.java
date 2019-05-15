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
import com.epam.pipeline.exception.PipelineException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RestHighLevelClient;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Stream;

public abstract class AbstractMetricRequester implements MetricRequester {

    private static final DateTimeFormatter DATE_FORMATTER =DateTimeFormatter.ofPattern("yyyy.MM.dd");

    private static final String INDEX_NAME_PATTERN = "heapster-%s";

    protected static final String FIELD_METRICS_TAGS = "MetricsTags";
    protected static final String FIELD_NAMESPACE_NAME = "namespace_name";
    protected static final String FIELD_TYPE = "type";

    protected static final String USAGE = "usage";
    protected static final String USAGE_RATE = "usage_rate";
    protected static final String NODE_UTILIZATION = "node_utilization";
    protected static final String LIMIT = "limit";

    protected static final String NODE = "node";
    protected static final String RESOURCE_ID = "resource_id";
    protected static final String POD_CONTAINER = "pod_container";

    protected static final String AVG_AGGREGATION = "avg_";
    protected static final String AGGREGATION_POD_NAME = "pod_name";
    protected static final String FIELD_POD_NAME_RAW = "pod_name.raw";
    protected static final String NODENAME_FIELD_VALUE = "nodename";
    protected static final String NODENAME_RAW_FIELD = "nodename.raw";

    private RestHighLevelClient client;

    AbstractMetricRequester(final RestHighLevelClient client) {
        this.client = client;
    }


    public static MetricRequester getRequester(final ELKUsageMetric metric, final RestHighLevelClient client) {
        switch (metric) {
            case CPU:
                return new CPURequester(client);
            case MEM:
                return new MemoryRequester(client);
            case FS:
                return new FSRequester(client);
            default:
                throw new IllegalArgumentException("Metric type: " + metric.getName() + " isn't supported!");

        }
    }

    public static MonitoringRequester getStatsRequester(final ELKUsageMetric metric, final RestHighLevelClient client) {
        switch (metric) {
            case CPU:
                return new CPURequester(client);
            case MEM:
                return new MemoryRequester(client);
            default:
                throw new IllegalArgumentException("Metric type: " + metric.getName() + " isn't supported!");

        }
    }

    public Map<String, Double> performRequest(final Collection<String> resourceIds,
                                              final LocalDateTime from, final LocalDateTime to) {
        return parseResponse(
                executeRequest(
                        buildRequest(
                                resourceIds, from, to, null))
        );
    }

    protected SearchResponse executeRequest(final SearchRequest searchRequest) {
        try {
            return client.search(searchRequest);
        } catch (IOException e) {
            throw new PipelineException(e);
        }
    }

    protected static String[] getIndexNames(final LocalDateTime from, final LocalDateTime to) {
        return Stream.of(from, to)
                .map(d -> d.format(DATE_FORMATTER))
                .distinct()
                .map(dateStr -> String.format(INDEX_NAME_PATTERN, dateStr))
                .toArray(String[]::new);
    }

    protected static String path(final String ...parts) {
        return String.join(".", parts);
    }
}
