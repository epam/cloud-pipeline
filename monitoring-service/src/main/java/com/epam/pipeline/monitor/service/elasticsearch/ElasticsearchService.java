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

import io.swagger.models.HttpMethod;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.rest.RestStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ElasticsearchService {

    private final RestHighLevelClient elasticsearchClient;
    private final int bulkSize;

    public ElasticsearchService(final RestHighLevelClient elasticsearchClient,
                               @Value("${es.index.bulk.size:1000}") final int bulkSize) {
        this.elasticsearchClient = elasticsearchClient;
        this.bulkSize = bulkSize;
    }

    public void createIndexIfNotExists(final String indexName, final String source) {
        log.debug("Start to create Elasticsearch index ...");
        try {
            if (isIndexExists(indexName)) {
                log.debug("Index with name {} already exists", indexName);
                return;
            }
            final Request request = new Request(HttpMethod.PUT.name(), indexName);
            request.setJsonEntity(source);

            final Response response = elasticsearchClient.getLowLevelClient().performRequest(request);
            final int statusCode = response.getStatusLine().getStatusCode();
            Assert.isTrue(statusCode == RestStatus.OK.getStatus(),
                    "Create Elasticsearch index: " + response);
        } catch (IOException e) {
            throw new ElasticsearchException("Failed to create index request: " + e.getMessage(), e);
        }
        log.debug("Elasticsearch index with name {} was created.", indexName);
    }

    public void insertBulkDocuments(final List<IndexRequest> indexRequests) {
        ListUtils.partition(indexRequests, bulkSize).forEach(this::insertDocuments);
    }

    private void insertDocuments(final List<IndexRequest> indexRequests) {
        try {
            final BulkRequest request = new BulkRequest();
            indexRequests.forEach(request::add);
            final BulkResponse response = elasticsearchClient.bulk(request);
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

    private boolean isIndexExists(final String indexName) {
        try {
            final Request request = new Request(HttpMethod.HEAD.name(), indexName);
            final Response response = elasticsearchClient.getLowLevelClient().performRequest(request);
            final int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == RestStatus.NOT_FOUND.getStatus()) {
                return false;
            }
            if (statusCode == RestStatus.OK.getStatus()) {
                return true;
            }
            throw new ElasticsearchException("Failed to send the request to checks index " + response);
        } catch (IOException e) {
            throw new ElasticsearchException("Failed to send the request to checks index " + e.getMessage(), e);
        }
    }
}
