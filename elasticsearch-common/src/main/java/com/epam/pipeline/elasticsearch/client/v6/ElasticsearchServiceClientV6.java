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

package com.epam.pipeline.elasticsearch.client.v6;

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
import com.epam.pipeline.elasticsearch.model.v6.action.DocWriterRequestFactoryV6;
import com.epam.pipeline.elasticsearch.model.v6.action.bulk.BulkItemResponseV6;
import com.epam.pipeline.elasticsearch.model.v6.action.bulk.BulkResponseV6;
import com.epam.pipeline.elasticsearch.model.v6.action.search.MultiSearchRequestV6;
import com.epam.pipeline.elasticsearch.model.v6.action.search.MultiSearchResponseV6;
import com.epam.pipeline.elasticsearch.model.v6.action.search.SearchRequestV6;
import com.epam.pipeline.elasticsearch.model.v6.action.search.SearchResponseV6;
import com.epam.pipeline.elasticsearch.model.v6.search.ScrollV6;
import javax.annotation.Nullable;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHost;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequest;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.get.GetIndexRequest;
import org.elasticsearch.action.admin.indices.get.GetIndexResponse;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.search.SearchScrollRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
public class ElasticsearchServiceClientV6 implements ElasticsearchServiceClient {
    private static final org.elasticsearch.search.Scroll TIME_SCROLL =
            new org.elasticsearch.search.Scroll(new TimeValue(60000));
    private static final String INDEX_TYPE = "_doc";

    private final RestHighLevelClient client;

    public ElasticsearchServiceClientV6(final String elasticsearchUrl,
                                        final int elasticsearchPort,
                                        final String elasticsearchScheme,
                                        final String elasticsearchAuth) {
       this(elasticsearchUrl, elasticsearchPort, elasticsearchScheme, null, elasticsearchAuth);
    }

    public ElasticsearchServiceClientV6(final String elasticsearchUrl,
                                        final int elasticsearchPort,
                                        final String elasticsearchScheme,
                                        final Integer socketTimeout,
                                        final String elasticsearchAuth) {
        final RestClientBuilder builder = RestClient.builder(
                new HttpHost(elasticsearchUrl, elasticsearchPort, elasticsearchScheme));
        if (StringUtils.isNotBlank(elasticsearchAuth)) {
            builder.setDefaultHeaders(ElasticsearchServiceClient.getAuthHeaders(elasticsearchAuth));
        }
        if (socketTimeout != null && socketTimeout != 0) {
            builder.setRequestConfigCallback(requestConfigBuilder ->
                    requestConfigBuilder.setSocketTimeout(socketTimeout));
        }
        this.client = new RestHighLevelClient(builder);
    }

    @Override
    public ElasticStackVersion getVersion() {
        return ElasticStackVersion.V6;
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
    public BulkResponseV6 sendRequests(final @Nullable String indexName,
                                       final List<? extends DocWriteRequest> docWriteRequests) {
        if (CollectionUtils.isEmpty(docWriteRequests)) {
            log.warn("Index requests are empty. ");
            return null;
        }
        BulkRequest bulkRequest = new BulkRequest();
        docWriteRequests.stream()
                .map(DocWriterRequestFactoryV6::toRequest)
                .forEach(bulkRequest::add);

        if (StringUtils.isNotBlank(indexName)) {
            log.debug("Start to insert documents for index {}", indexName);
        }

        try {
            BulkResponse bulkResponse = client.bulk(bulkRequest, RequestOptions.DEFAULT);

            if (!(bulkResponse.status() == RestStatus.OK)) {
                throw new IllegalStateException("Failed to create Elasticsearch documents: " + bulkResponse);
            }

            if (StringUtils.isNotBlank(indexName)) {
                log.debug("Stop to insert documents for index {}", indexName);
            }

            return new BulkResponseV6(bulkResponse);
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

            DeleteIndexRequest request = new DeleteIndexRequest(indexName);
            client.indices().delete(request, RequestOptions.DEFAULT);

        } catch (org.elasticsearch.ElasticsearchException exception) {
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
            return new SearchResponseV6(
                    client.search(((SearchRequestV6) request.getInner()).getInner(), RequestOptions.DEFAULT));
        } catch (IOException e) {
            throw new ElasticsearchException("Failed to find results for search query:" + e.getMessage(), e);
        }
    }

    @Override
    public SearchResponse nextScrollPage(final String scrollId, final Scroll scroll) {
        final SearchScrollRequest searchScrollRequest = new SearchScrollRequest();
        searchScrollRequest.scrollId(scrollId);
        searchScrollRequest.scroll(((ScrollV6) scroll).getInner());
        try {
            return new SearchResponseV6(client.scroll(searchScrollRequest, RequestOptions.DEFAULT));
        } catch (IOException e) {
            throw new ElasticsearchException(String.format("Failed to retrieve next scroll page for [%s]: %s",
                    scrollId, e.getMessage()), e);
        }
    }

    @Override
    public SearchResponse nextScrollPage(final String scrollId) {
        return nextScrollPage(scrollId, new ScrollV6(TIME_SCROLL));
    }

    @Override
    public MultiSearchResponse search(final MultiSearchRequest request) {
        try {
            final MultiSearchResponseV6 response = new MultiSearchResponseV6(
                    client.msearch(((MultiSearchRequestV6) request.getInner()).getInner(), RequestOptions.DEFAULT));
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
        final org.elasticsearch.action.search.SearchRequest request =
                new org.elasticsearch.action.search.SearchRequest(indexName).source(searchSource);
        log.debug("Search request: {}", request);
        try {
            return buildDeleteRequests(indexName, request);
        } catch (ElasticsearchException | IOException e) {
            return Collections.emptyList();
        }
    }

    @Override
    public List<DocWriteRequest> getDeleteRequests(final String id,
                                                   final String indexName) {
        final org.elasticsearch.action.search.SearchRequest request = buildSearchRequestForConfigEntries(id, indexName);
        log.debug("Search request: {}", request);
        try {
            final List<DocWriteRequest> requests = buildDeleteRequests(indexName, request);
            // return dummy doc, since we need it to clear events DB
            if (CollectionUtils.isEmpty(requests)) {
                return Collections.singletonList(new DeleteRequest(indexName, INDEX_TYPE,
                        getWildcardId(id), ElasticStackVersion.V6));
            }
            return requests;
        } catch (org.elasticsearch.ElasticsearchException | IOException e) {
            return Collections.emptyList();
        }
    }

    @Override
    public void indexChunk(final List<DocWriteRequest> documentRequests) {
        final BulkResponseV6 response = sendRequests(null, documentRequests);
        if (Objects.isNull(response)) {
            log.debug("No documents were created in Elasticsearch for {} request(s).", documentRequests.size());
            return;
        }
        final Map<Boolean, List<BulkItemResponseV6>> indexResults = Arrays.stream(response.getItems())
                .collect(Collectors.partitioningBy(BulkItemResponseV6::isFailed));
        final List<BulkItemResponseV6> failed = indexResults.get(true);
        if (CollectionUtils.isNotEmpty(failed)) {
            log.error("Failed to insert {} of {} document(s) into Elasticsearch.",
                    failed.size(), documentRequests.size());
            failed.forEach(item -> log.error("Error for doc {} index {}: {}.",
                    item.getId(), item.getIndex(), item.getFailureMessage()));
        }
        final List<BulkItemResponseV6> successful = indexResults.get(false);
        if (CollectionUtils.isNotEmpty(successful)) {
            log.debug("Successfully inserted {} of {} document(s) into Elasticsearch).",
                    successful.size(), documentRequests.size());
        }
    }

    private List<DocWriteRequest> buildDeleteRequests(
            final String indexName,
            final org.elasticsearch.action.search.SearchRequest request) throws IOException {
        org.elasticsearch.action.search.SearchResponse search = client.search(request);
        if (search.getHits().getTotalHits() == 0) {
            log.debug("No documents found for {} {}", indexName, request);
            return Collections.emptyList();
        }
        return Arrays.stream(search.getHits().getHits())
                .map(hit -> {
                    log.debug("Found {} entry doc: {}", indexName, hit.getId());
                    return new DeleteRequest(indexName, INDEX_TYPE, hit.getId(), ElasticStackVersion.V6);
                })
                .collect(Collectors.toList());
    }

    private org.elasticsearch.action.search.SearchRequest buildSearchRequestForConfigEntries(
            final String id, final String indexName) {
        final SearchSourceBuilder searchSource = new SearchSourceBuilder()
                .query(QueryBuilders.wildcardQuery("id", getWildcardId(id)));
        return new org.elasticsearch.action.search.SearchRequest(indexName).source(searchSource);
    }

    private String getWildcardId(final String id) {
        return id + ID_DELIMITER + WILDCARD;
    }
}
