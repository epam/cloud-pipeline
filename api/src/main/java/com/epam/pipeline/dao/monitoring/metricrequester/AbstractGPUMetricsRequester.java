/*
 * Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
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

import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public abstract class AbstractGPUMetricsRequester extends AbstractMetricRequester {
    protected static final String GPU_DEVICE_NAME = "device_name";
    protected static final String INDEX_NAME_PATTERN = "cp-gpu-monitor-%s";

    AbstractGPUMetricsRequester(final HeapsterElasticRestHighLevelClient client) {
        super(client, INDEX_NAME_PATTERN);
    }

    protected String getDeviceName(final SearchHits hits) {
        return Optional.ofNullable(hits)
                .map(SearchHits::getHits)
                .map(Arrays::stream)
                .orElseGet(Stream::empty)
                .findFirst()
                .map(SearchHit::getSourceAsMap)
                .map(sourceMap -> sourceMap.get(FIELD_METRICS_TAGS))
                .filter(metricsTags -> metricsTags instanceof Map)
                .map(Map.class::cast)
                .map(metricsTags -> metricsTags.get(GPU_DEVICE_NAME))
                .map(String.class::cast)
                .orElse(null);
    }
}
