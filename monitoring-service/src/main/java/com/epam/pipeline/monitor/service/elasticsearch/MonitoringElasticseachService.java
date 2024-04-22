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

import com.epam.pipeline.monitor.model.node.NodeGpuUsages;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.rest.RestStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class MonitoringElasticseachService {

    private static final DateTimeFormatter INDEX_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    private final RestHighLevelClient elasticsearchClient;
    private final String indexNamePrefix;
    private final int bulkSize;

    public MonitoringElasticseachService(final RestHighLevelClient elasticsearchClient,
                                         @Value("${es.index.prefix:heapster-}") final String indexNamePrefix,
                                         @Value("${es.index.bulk.size:1000}") final int bulkSize) {
        this.elasticsearchClient = elasticsearchClient;
        this.indexNamePrefix = indexNamePrefix;
        this.bulkSize = bulkSize;
    }

    public void saveGpuUsages(final List<NodeGpuUsages> usages) {
        if (CollectionUtils.isEmpty(usages)) {
            return;
        }
        final String indexName = indexNamePrefix + LocalDateTime.now().format(INDEX_FORMATTER);
        final List<IndexRequest> indexRequests = ListUtils.emptyIfNull(usages).stream()
                .flatMap(usage -> NodeGpuUsagesIndexHelper.buildIndexRequests(indexName, usage).stream())
                .collect(Collectors.toList());
        ListUtils.partition(indexRequests, bulkSize).forEach(this::insertDocuments);
    }

    private void insertDocuments(final List<IndexRequest> indexRequests) {
        try {
            final BulkRequest request = new BulkRequest();
            indexRequests.forEach(request::add);
            final BulkResponse response = elasticsearchClient.bulk(request, RequestOptions.DEFAULT);
            // Thread.sleep(insertTimeout);
            validateBulkResponse(response);
        } catch (IOException e) {
            log.error("Partial error during index sync: {}.", e.getMessage());
        }
    }

    private void validateBulkResponse(final BulkResponse response) {
        Assert.state(response.status() == RestStatus.OK,
                "Failed to create Elasticsearch documents: " + response);

        if (ObjectUtils.isEmpty(response)) {
            log.debug("No documents were created in Elasticsearch.");
            return;
        }

        final Map<Boolean, List<BulkItemResponse>> indexResults = Arrays.stream(response.getItems())
                .collect(Collectors.partitioningBy(BulkItemResponse::isFailed));
        final List<BulkItemResponse> failed = indexResults.get(true);
        if (CollectionUtils.isNotEmpty(failed)) {
            failed.forEach(item -> log.error(
                    "Error for doc {} index {}: {}.", item.getId(), item.getIndex(), item.getFailureMessage()));
        }
    }
}
