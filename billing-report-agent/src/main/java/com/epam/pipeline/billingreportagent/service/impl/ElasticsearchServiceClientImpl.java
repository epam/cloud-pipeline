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

package com.epam.pipeline.billingreportagent.service.impl;

import com.epam.pipeline.billingreportagent.service.ElasticsearchServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.DocWriteRequest;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequest;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequest.AliasActions;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.get.GetIndexRequest;
import org.elasticsearch.action.admin.indices.get.GetIndexResponse;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.rest.RestStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

@Service
@Slf4j
@RequiredArgsConstructor
public class ElasticsearchServiceClientImpl implements ElasticsearchServiceClient {

    private final RestHighLevelClient client;

    @Override
    public Stream<String> indices() {
        try {
            final GetIndexRequest request = new GetIndexRequest()
                    .indices("*")
                    .indicesOptions(IndicesOptions.strictExpandOpen());
            final GetIndexResponse response = client.indices().get(request, RequestOptions.DEFAULT);
            return Arrays.stream(response.getIndices());
        } catch (IOException e) {
            throw new ElasticsearchException("Failed to list indices.", e);
        }
    }

    @Override
    public void createIndex(final String indexName, final String source) {
        final CreateIndexRequest request = new CreateIndexRequest(indexName);
        request.source(source, XContentType.JSON);
        try {
            final CreateIndexResponse createIndexResponse = client.indices().create(request, RequestOptions.DEFAULT);
            Assert.isTrue(createIndexResponse.isAcknowledged(),
                          "Create Elasticsearch index: " + createIndexResponse.toString());
        } catch (IOException e) {
            throw new ElasticsearchException("Failed to create index request: " + e.getMessage(), e);
        }
        log.debug("Elasticsearch index with name {} was created.", indexName);
    }

    @Override
    public BulkResponse sendRequests(final List<? extends DocWriteRequest> docWriteRequests) {
        if (CollectionUtils.isEmpty(docWriteRequests)) {
            log.warn("Index requests are empty. ");
            return null;
        }
        final BulkRequest bulkRequest = new BulkRequest();
        docWriteRequests.forEach(bulkRequest::add);

        try {
            final BulkResponse bulkResponse = client.bulk(bulkRequest, RequestOptions.DEFAULT);
            Assert.isTrue(bulkResponse.status() == RestStatus.OK,
                    "Failed to create Elasticsearch documents: " + bulkResponse.toString());

            return bulkResponse;
        } catch(IOException e) {
            throw new ElasticsearchException("Failed to insert Elasticsearch documents: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteIndex(String indexName) {
        log.debug("Start to delete index...");
        try {
            if (!isIndexExists(indexName)) {
                log.debug("Index with name does not exist. ");
                return;
            }
            final DeleteIndexRequest request = new DeleteIndexRequest(indexName);
            client.indices().delete(request, RequestOptions.DEFAULT);

        } catch (ElasticsearchException exception) {
            if (exception.status() == RestStatus.NOT_FOUND) {
                throw new ElasticsearchException("Response status " + RestStatus.NOT_FOUND + ": " +
                        exception.getMessage(), exception);
            }
            throw new ElasticsearchException("Failed to delete Elasticsearch index: " + exception.getMessage(),
                    exception);
        } catch (IOException e) {
            throw new ElasticsearchException("Failed to delete Elasticsearch index: " + e.getMessage(), e);
        }
        log.debug("Stop to delete index...");
    }

    @Override
    public boolean isIndexExists(final String indexName) {
        final GetIndexRequest request = new GetIndexRequest();
        request.indices(indexName);
        try {
            return client.indices().exists(request, RequestOptions.DEFAULT);
        } catch (IOException e) {
            throw new ElasticsearchException("Failed to send the request to checks index " + e.getMessage(), e);
        }
    }

    @Override
    public void createIndexAlias(final String indexName, final String indexAlias) {
        final IndicesAliasesRequest request = new IndicesAliasesRequest();
        final AliasActions aliasAction =
                new AliasActions(AliasActions.Type.ADD)
                        .index(indexName)
                        .alias(indexAlias);
        request.addAliasAction(aliasAction);
        try {
            client.indices().updateAliases(request, RequestOptions.DEFAULT);
        } catch (IOException e) {
            throw new ElasticsearchException("Failed to send create alias request" + e.getMessage(), e);
        }
    }

    @Override
    public String getIndexNameByAlias(final String alias) {
        if (!isIndexExists(alias)) {
            return null;
        }
        try {
            final GetIndexRequest request = new GetIndexRequest();
            request.indices(alias);

            final GetIndexResponse getIndexResponse = client.indices().get(request, RequestOptions.DEFAULT);
            if (getIndexResponse.aliases().isEmpty()) {
                throw new ElasticsearchException("No alias is available.");
            }
            String[] indices = getIndexResponse.indices();
            if (indices.length != 1) {
                throw new ElasticsearchException("Unexpected indexes count: {}", indices.length);
            }
            return indices[0];
        } catch (IOException e) {
            throw new ElasticsearchException("Failed to get alias name:" + e.getMessage(), e);
        }
    }

    @Override
    public void refreshIndex(final String[] indices) {
        try {
            log.debug("Refreshing indices {}...", (Object[]) indices);
            final RefreshRequest request = new RefreshRequest()
                    .indices(indices)
                    .indicesOptions(IndicesOptions.strictExpandOpen());
            client.indices().refresh(request, RequestOptions.DEFAULT);
            log.debug("Indices {} were refreshed.", (Object[]) indices);
        } catch (IOException e) {
            throw new ElasticsearchException("Failed to refresh indices.", e);
        }
    }

    @Override
    public SearchResponse search(final SearchRequest request) {
        try {
            log.debug("Billing request: {}", request);
            return client.search(request, RequestOptions.DEFAULT);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new ElasticsearchException("Failed to find results for search query:" + e.getMessage(), e);
        }
    }
}
