/*
 * Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.monitor.service.elasticsearch;

import com.epam.pipeline.entity.reporter.NodeReporterGpuUsages;
import com.epam.pipeline.monitor.model.node.NodeGpuUsages;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.collections4.ListUtils;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@SuppressWarnings({"HideUtilityClassConstructor"})
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class GpuMonitorIndexHelper {

    private static final String INDEX_TYPE = "gpu";
    private static final String TIMESTAMP_FIELD = "GpuMetricsTimestamp";
    private static final String METRICS_FIELD = "Metrics";
    private static final String TAGS_FIELD = "MetricsTags";
    private static final String GPU_UTILIZATION_FIELD = "gpu/utilization_gpu";
    private static final String MEMORY_UTILIZATION_FIELD = "gpu/utilization_memory";
    private static final String INDEX_FIELD = "index";
    private static final String NODENAME_FIELD = "nodename";
    private static final String TYPE_FIELD = "type";
    private static final String NODE_TYPE = "node";
    private static final String VALUE_FIELD = "value";
    private static final DateTimeFormatter TIMESTAMP_FIELD_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    public static List<IndexRequest> buildIndexRequests(final String indexName, final NodeGpuUsages usages) {
        final String timestamp = usages.getTimestamp().format(TIMESTAMP_FIELD_FORMATTER);
        return ListUtils.emptyIfNull(usages.getUsages()).stream()
                .map(usage -> buildIndexRequest(indexName, usage, usages.getNodename(), timestamp))
                .collect(Collectors.toList());
    }

    private static IndexRequest buildIndexRequest(final String indexName, final NodeReporterGpuUsages usages,
                                                  final String nodename, final String timestamp) {
        return new IndexRequest()
                .index(indexName)
                .type(INDEX_TYPE)
                .id(UUID.randomUUID().toString())
                .source(buildDocument(usages, nodename, timestamp));
    }

    private static XContentBuilder buildDocument(final NodeReporterGpuUsages usages, final String nodename,
                                                 final String timestamp) {
        try (XContentBuilder jsonBuilder = XContentFactory.jsonBuilder()) {
            jsonBuilder.startObject();

            jsonBuilder.field(TIMESTAMP_FIELD, timestamp);

            jsonBuilder
                    .startObject(METRICS_FIELD)
                    .startObject(GPU_UTILIZATION_FIELD)
                    .field(VALUE_FIELD, usages.getUtilizationGpu())
                    .endObject()
                    .startObject(MEMORY_UTILIZATION_FIELD)
                    .field(VALUE_FIELD, usages.getUtilizationMemory())
                    .endObject()
                    .endObject();

            jsonBuilder.startObject(TAGS_FIELD)
                    .field(INDEX_FIELD, usages.getIndex())
                    .field(NODENAME_FIELD, nodename)
                    .field(TYPE_FIELD, NODE_TYPE)
                    .endObject();

            return jsonBuilder.endObject();
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to create elasticsearch document: ", e);
        }
    }
}
