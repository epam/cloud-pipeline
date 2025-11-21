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

package com.epam.pipeline.elasticsearch.client.v7;

import com.epam.pipeline.elasticsearch.ElasticStackVersion;
import com.epam.pipeline.elasticsearch.ElasticsearchException;
import com.epam.pipeline.elasticsearch.client.ElasticsearchServiceClient;
import com.epam.pipeline.elasticsearch.model.DeleteRequest;
import com.epam.pipeline.elasticsearch.model.DocWriteRequest;
import com.epam.pipeline.elasticsearch.model.MultiSearchRequest;
import com.epam.pipeline.elasticsearch.model.MultiSearchResponse;
import com.epam.pipeline.elasticsearch.model.Scroll;
import com.epam.pipeline.elasticsearch.model.SearchRequest;
import com.epam.pipeline.elasticsearch.model.SearchResponse;
import com.epam.pipeline.elasticsearch.model.v7.action.DocWriterRequestFactoryV7;
import com.epam.pipeline.elasticsearch.model.v7.action.bulk.BulkResponseV7;
import com.epam.pipeline.elasticsearch.model.v7.action.search.MultiSearchRequestV7;
import com.epam.pipeline.elasticsearch.model.v7.action.search.MultiSearchResponseV7;
import com.epam.pipeline.elasticsearch.model.v7.action.search.SearchRequestV7;
import com.epam.pipeline.elasticsearch.model.v7.action.search.SearchResponseV7;
import com.epam.pipeline.elasticsearch.model.v7.search.ScrollV7;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.http.HttpHost;
import shaded.org.elasticsearch.v7.action.admin.indices.alias.IndicesAliasesRequest;
import shaded.org.elasticsearch.v7.action.admin.indices.create.CreateIndexRequest;
import shaded.org.elasticsearch.v7.action.admin.indices.create.CreateIndexResponse;
import shaded.org.elasticsearch.v7.action.admin.indices.delete.DeleteIndexRequest;
import shaded.org.elasticsearch.v7.action.admin.indices.get.GetIndexRequest;
import shaded.org.elasticsearch.v7.action.admin.indices.get.GetIndexResponse;
import shaded.org.elasticsearch.v7.action.bulk.BulkRequest;
import shaded.org.elasticsearch.v7.action.bulk.BulkResponse;
import shaded.org.elasticsearch.v7.action.search.SearchScrollRequest;
import shaded.org.elasticsearch.v7.client.RequestOptions;
import shaded.org.elasticsearch.v7.client.RestClient;
import shaded.org.elasticsearch.v7.client.RestHighLevelClient;
import shaded.org.elasticsearch.v7.common.unit.TimeValue;
import shaded.org.elasticsearch.v7.common.xcontent.XContentType;
import shaded.org.elasticsearch.v7.index.query.QueryBuilders;
import shaded.org.elasticsearch.v7.rest.RestStatus;
import shaded.org.elasticsearch.v7.search.builder.SearchSourceBuilder;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class ElasticsearchServiceClientV7 implements ElasticsearchServiceClient {
    private static final shaded.org.elasticsearch.v7.search.Scroll TIME_SCROLL =
            new shaded.org.elasticsearch.v7.search.Scroll(new TimeValue(60000));

    private final RestHighLevelClient client;

    public ElasticsearchServiceClientV7(final String elasticsearchUrl,
                                        final int elasticsearchPort,
                                        final String elasticsearchScheme) {
        this.client = new RestHighLevelClient(
                RestClient.builder(new HttpHost(elasticsearchUrl, elasticsearchPort, elasticsearchScheme))
        );
    }

    @Override
    public ElasticStackVersion getVersion() {
        return ElasticStackVersion.V7;
    }

    @Override
    public void createIndex(String indexName, String source) {
        log.debug("Start to create Elasticsearch index ...");

        CreateIndexRequest request = new CreateIndexRequest(indexName);
        request.source(source, XContentType.JSON);

        try {
            if (isIndexExists(indexName)) {
                log.debug("Index with name {} already exists", indexName);
                return;
            }
            CreateIndexResponse createIndexResponse = client.indices().create(request, RequestOptions.DEFAULT);

            if (!createIndexResponse.isAcknowledged()) {
                throw new IllegalStateException("Create Elasticsearch index: " + createIndexResponse);
            }
        } catch (IOException e) {
            throw new ElasticsearchException("Failed to create index request: " + e.getMessage(), e);
        }

        log.debug("Elasticsearch index with name {} was created.", indexName);
    }

    @Override
    public BulkResponseV7 sendRequests(String indexName, List<? extends DocWriteRequest> docWriteRequests) {
        if (CollectionUtils.isEmpty(docWriteRequests)) {
            log.warn("Index requests are empty. ");
            return null;
        }
        BulkRequest bulkRequest = new BulkRequest();
        docWriteRequests.stream()
                .map(DocWriterRequestFactoryV7::toRequest)
                .forEach(bulkRequest::add);

        log.debug("Start to insert documents for index {}", indexName);

        try {
            BulkResponse bulkResponse = client.bulk(bulkRequest, RequestOptions.DEFAULT);

            if (!(bulkResponse.status() == RestStatus.OK)) {
                throw new IllegalStateException("Failed to create Elasticsearch documents: " + bulkResponse);
            }

            log.debug("Stop to insert documents for index {}", indexName);

            return BulkResponseV7.builder()
                    .response(bulkResponse)
                    .build();
        } catch (IOException e) {
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

            DeleteIndexRequest request = new DeleteIndexRequest(indexName);
            client.indices().delete(request, RequestOptions.DEFAULT);

        } catch (shaded.org.elasticsearch.v7.ElasticsearchException exception) {
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
    public boolean isIndexExists(String indexName) {
        GetIndexRequest request = new GetIndexRequest();
        request.indices(indexName);
        try {
            return client.indices().exists(request, RequestOptions.DEFAULT);
        } catch (IOException e) {
            throw new ElasticsearchException("Failed to send the request to checks index " + e.getMessage(), e);
        }
    }

    @Override
    public void createIndexAlias(final String indexName, final String indexAlias) {
        IndicesAliasesRequest request = new IndicesAliasesRequest();
        IndicesAliasesRequest.AliasActions aliasAction =
                new IndicesAliasesRequest.AliasActions(IndicesAliasesRequest.AliasActions.Type.ADD)
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
            GetIndexRequest request = new GetIndexRequest();
            request.indices(alias);

            GetIndexResponse getIndexResponse = client.indices().get(request, RequestOptions.DEFAULT);
            if (getIndexResponse.aliases().isEmpty()) {
                throw new ElasticsearchException("No alias is available.");
            }
            String[] indices = getIndexResponse.indices();
            if (indices.length != 1) {
                throw new ElasticsearchException(String.format("Unexpected indexes count: %s", indices.length));
            }
            return indices[0];
        } catch (IOException e) {
            throw new ElasticsearchException("Failed to get alias name:" + e.getMessage(), e);
        }
    }

    @Override
    public List<String> findIndices(final String pattern) {
        try {
            final GetIndexRequest request = new GetIndexRequest();
            request.indices(pattern);

            final GetIndexResponse getIndexResponse = client.indices().get(request, RequestOptions.DEFAULT);
            final String[] indices = getIndexResponse.indices();
            if (ArrayUtils.isEmpty(indices)) {
                return Collections.emptyList();
            }
            return Arrays.asList(indices);
        } catch (IOException e) {
            throw new ElasticsearchException("Failed to get indices for pattern:" + e.getMessage(), e);
        }
    }

    @Override
    public SearchResponse search(final SearchRequest request) {
        try {
            return new SearchResponseV7(client.search(((SearchRequestV7) request.getInner()).getInner(),
                    RequestOptions.DEFAULT));
        } catch (IOException e) {
            throw new ElasticsearchException("Failed to find results for search query:" + e.getMessage(), e);
        }
    }

    @Override
    public SearchResponse nextScrollPage(final String scrollId, final Scroll scroll) {
        final SearchScrollRequest searchScrollRequest = new SearchScrollRequest();
        searchScrollRequest.scrollId(scrollId);
        searchScrollRequest.scroll(((ScrollV7) scroll).getInner());
        try {
            return new SearchResponseV7(client.scroll(searchScrollRequest, RequestOptions.DEFAULT));
        } catch (IOException e) {
            throw new ElasticsearchException(String.format("Failed to retrieve next scroll page for [%s]: %s",
                    scrollId, e.getMessage()), e);
        }
    }

    @Override
    public SearchResponse nextScrollPage(final String scrollId) {
        return nextScrollPage(scrollId, new ScrollV7(TIME_SCROLL));
    }

    @Override
    public MultiSearchResponse search(final MultiSearchRequest request) {
        try {
            final MultiSearchResponseV7 response = new MultiSearchResponseV7(
                    client.msearch(((MultiSearchRequestV7) request.getInner()).getInner(), RequestOptions.DEFAULT));
            return new MultiSearchResponse(response);
        } catch (IOException e) {
            throw new ElasticsearchException("Failed to find results for multi-search query:" + e.getMessage(), e);
        }
    }

    @Override
    public List<DocWriteRequest> getDeleteRequestsByTerm(final String field,
                                                         final String value,
                                                         final String indexName) {
        final SearchSourceBuilder searchSource = new SearchSourceBuilder()
                .query(QueryBuilders.termQuery(field, value));
        final shaded.org.elasticsearch.v7.action.search.SearchRequest request =
                new shaded.org.elasticsearch.v7.action.search.SearchRequest(indexName).source(searchSource);
        log.debug("Search request: {}", request);
        try {
            return buildDeleteRequests(indexName, request);
        } catch (shaded.org.elasticsearch.v7.ElasticsearchException | IOException e) {
            return Collections.emptyList();
        }
    }

    @Override
    public List<DocWriteRequest> getDeleteRequests(final String id,
                                                   final String indexName) {
        shaded.org.elasticsearch.v7.action.search.SearchRequest request =
                buildSearchRequestForConfigEntries(id, indexName);
        log.debug("Search request: {}", request);
        try {
            final List<DocWriteRequest> requests = buildDeleteRequests(indexName, request);
            // return dummy doc, since we need it to clear events DB
            if (CollectionUtils.isEmpty(requests)) {
                return Collections.singletonList(
                        new DeleteRequest(indexName,getWildcardId(id), ElasticStackVersion.V7));
            }
            return requests;
        } catch (shaded.org.elasticsearch.v7.ElasticsearchException | IOException e) {
            return Collections.emptyList();
        }
    }

    private List<DocWriteRequest> buildDeleteRequests(
            final String indexName,
            final shaded.org.elasticsearch.v7.action.search.SearchRequest request) throws IOException {
        shaded.org.elasticsearch.v7.action.search.SearchResponse search =
                client.search(request, RequestOptions.DEFAULT);
        if (search.getHits().getTotalHits().value == 0) {
            log.debug("No documents found for {} {}", indexName, request);
            return Collections.emptyList();
        }
        return Arrays.stream(search.getHits().getHits())
                .map(hit -> {
                    log.debug("Found {} entry doc: {}", indexName, hit.getId());
                    return new DeleteRequest(indexName, hit.getId(), ElasticStackVersion.V7);
                })
                .collect(Collectors.toList());
    }

    private shaded.org.elasticsearch.v7.action.search.SearchRequest buildSearchRequestForConfigEntries(
            final String id, final String indexName) {
        final SearchSourceBuilder searchSource = new SearchSourceBuilder()
                .query(QueryBuilders.wildcardQuery("id", getWildcardId(id)));
        return new shaded.org.elasticsearch.v7.action.search.SearchRequest(indexName).source(searchSource);
    }

    private String getWildcardId(final String id) {
        return id + ID_DELIMITER + WILDCARD;
    }
}
