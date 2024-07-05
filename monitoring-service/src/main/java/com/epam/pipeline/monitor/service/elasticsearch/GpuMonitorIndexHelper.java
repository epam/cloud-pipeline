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
import com.epam.pipeline.monitor.model.node.GpuUsageStats;
import com.epam.pipeline.monitor.model.node.GpuUsageSummary;
import com.epam.pipeline.monitor.model.node.GpuUsages;
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

    private static final String GPU_INDEX_TYPE = "gpu";
    private static final String GPU_STATS_INDEX_TYPE = "gpu_stats";
    private static final String GPU_TIMESTAMP_FIELD = "GpuMetricsTimestamp";
    private static final String GPU_STATS_TIMESTAMP_FIELD = "GpuStatsMetricsTimestamp";
    private static final String METRICS_FIELD = "Metrics";
    private static final String TAGS_FIELD = "MetricsTags";
    private static final String GPU_UTILIZATION_FIELD = "gpu/utilization_gpu";
    private static final String MEMORY_UTILIZATION_FIELD = "gpu/utilization_memory";
    private static final String MEMORY_USED_FIELD = "gpu/used_memory";
    private static final String ACTIVE_GPUS_FIELD = "gpu/active_gpus";
    private static final String AVG_GPU_UTILIZATION_FIELD = "gpu/avg_utilization_gpu";
    private static final String AVG_MEMORY_UTILIZATION_FIELD = "gpu/avg_utilization_memory";
    private static final String AVG_MEMORY_USED_FIELD = "gpu/avg_used_memory";
    private static final String MIN_GPU_UTILIZATION_FIELD = "gpu/min_utilization_gpu";
    private static final String MIN_MEMORY_UTILIZATION_FIELD = "gpu/min_utilization_memory";
    private static final String MIN_MEMORY_USED_FIELD = "gpu/min_used_memory";
    private static final String MAX_GPU_UTILIZATION_FIELD = "gpu/max_utilization_gpu";
    private static final String MAX_MEMORY_UTILIZATION_FIELD = "gpu/max_utilization_memory";
    private static final String MAX_MEMORY_USED_FIELD = "gpu/max_used_memory";
    private static final String INDEX_FIELD = "index";
    private static final String NODENAME_FIELD = "nodename";
    private static final String TYPE_FIELD = "type";
    private static final String NODE_TYPE = "node";
    private static final String VALUE_FIELD = "value";
    private static final DateTimeFormatter TIMESTAMP_FIELD_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    public static List<IndexRequest> buildIndexRequests(final String indexName, final GpuUsages usages) {
        final String timestamp = usages.getTimestamp().format(TIMESTAMP_FIELD_FORMATTER);
        final List<IndexRequest> indexRequests = ListUtils.emptyIfNull(usages.getUsages()).stream()
                .map(usage -> buildGpuIndexRequest(indexName, usage, usages.getNodename(), timestamp))
                .collect(Collectors.toList());
        indexRequests.add(buildGpuStatsIndexRequest(indexName, usages.getStats(), usages.getNodename(), timestamp));
        return indexRequests;
    }

    private static IndexRequest buildGpuStatsIndexRequest(final String indexName, final GpuUsageStats stats,
                                                          final String nodename, final String timestamp) {
        return buildIndexRequest(indexName, GPU_STATS_INDEX_TYPE, buildGpuStatDocument(stats, nodename, timestamp));
    }

    private static IndexRequest buildGpuIndexRequest(final String indexName, final NodeReporterGpuUsages usages,
                                                     final String nodename, final String timestamp) {
        return buildIndexRequest(indexName, GPU_INDEX_TYPE, buildGpuDocument(usages, nodename, timestamp));
    }

    private static IndexRequest buildIndexRequest(final String indexName, final String indexType,
                                                  final XContentBuilder source) {
        return new IndexRequest()
                .index(indexName)
                .type(indexType)
                .id(UUID.randomUUID().toString())
                .source(source);
    }

    private static XContentBuilder buildGpuDocument(final NodeReporterGpuUsages usages, final String nodename,
                                                    final String timestamp) {
        try (XContentBuilder jsonBuilder = XContentFactory.jsonBuilder()) {
            jsonBuilder.startObject();

            jsonBuilder.field(GPU_TIMESTAMP_FIELD, timestamp);

            jsonBuilder.startObject(METRICS_FIELD);
            fillMetricsValue(jsonBuilder, GPU_UTILIZATION_FIELD, usages.getUtilizationGpu());
            fillMetricsValue(jsonBuilder, MEMORY_UTILIZATION_FIELD, usages.getMemoryUtilization());
            fillMetricsValue(jsonBuilder, MEMORY_USED_FIELD, usages.getMemoryUsed());
            jsonBuilder.endObject();

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

    private static XContentBuilder buildGpuStatDocument(final GpuUsageStats stats, final String nodename,
                                                        final String timestamp) {
        try (XContentBuilder jsonBuilder = XContentFactory.jsonBuilder()) {
            jsonBuilder.startObject();

            jsonBuilder.field(GPU_STATS_TIMESTAMP_FIELD, timestamp);

            jsonBuilder.startObject(METRICS_FIELD);
            fillMetricsValue(jsonBuilder, ACTIVE_GPUS_FIELD, stats.getActiveGpusUtilization());
            fillSummaryMetricsValues(jsonBuilder, stats.getAverage(),
                    AVG_GPU_UTILIZATION_FIELD, AVG_MEMORY_UTILIZATION_FIELD, AVG_MEMORY_USED_FIELD);
            fillSummaryMetricsValues(jsonBuilder, stats.getMin(),
                    MIN_GPU_UTILIZATION_FIELD, MIN_MEMORY_UTILIZATION_FIELD, MIN_MEMORY_USED_FIELD);
            fillSummaryMetricsValues(jsonBuilder, stats.getMax(),
                    MAX_GPU_UTILIZATION_FIELD, MAX_MEMORY_UTILIZATION_FIELD, MAX_MEMORY_USED_FIELD);
            jsonBuilder.endObject();

            jsonBuilder.startObject(TAGS_FIELD)
                    .field(NODENAME_FIELD, nodename)
                    .field(TYPE_FIELD, NODE_TYPE)
                    .endObject();

            return jsonBuilder.endObject();
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to create elasticsearch document: ", e);
        }
    }

    private static void fillMetricsValue(final XContentBuilder builder, final String filed, final Integer value)
            throws IOException {
        builder.startObject(filed)
                .field(VALUE_FIELD, value)
        .endObject();
    }

    private static void fillSummaryMetricsValues(final XContentBuilder builder, final GpuUsageSummary summary,
                                                 final String utilizationGpuFiled,
                                                 final String utilizationMemoryField,
                                                 final String usedMemoryField) throws IOException {
        fillMetricsValue(builder, utilizationGpuFiled, summary.getGpuUtilization());
        fillMetricsValue(builder, utilizationMemoryField, summary.getMemoryUtilization());
        fillMetricsValue(builder, usedMemoryField, summary.getMemoryUsage());
    }
}
