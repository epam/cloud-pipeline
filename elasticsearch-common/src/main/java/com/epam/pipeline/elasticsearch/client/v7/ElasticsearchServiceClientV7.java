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
import com.epam.pipeline.elasticsearch.model.v7.action.bulk.BulkItemResponseV7;
import com.epam.pipeline.elasticsearch.model.v7.action.bulk.BulkResponseV7;
import com.epam.pipeline.elasticsearch.model.v7.action.search.MultiSearchRequestV7;
import com.epam.pipeline.elasticsearch.model.v7.action.search.MultiSearchResponseV7;
import com.epam.pipeline.elasticsearch.model.v7.action.search.SearchRequestV7;
import com.epam.pipeline.elasticsearch.model.v7.action.search.SearchResponseV7;
import com.epam.pipeline.elasticsearch.model.v7.search.ScrollV7;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.util.Timeout;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.MsearchResponse;
import org.opensearch.client.opensearch.core.ScrollRequest;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.indices.CreateIndexResponse;
import org.opensearch.client.opensearch.indices.GetIndexResponse;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;

import jakarta.annotation.Nullable;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
public class ElasticsearchServiceClientV7 implements ElasticsearchServiceClient {

    private static final String DEFAULT_SCROLL_KEEPALIVE = "1m";

    @SuppressWarnings("unchecked")
    private static final Class<Map<String, Object>> DOCUMENT_CLASS =
            (Class<Map<String, Object>>) (Class<?>) Map.class;

    private final OpenSearchClient client;
    private final JacksonJsonpMapper mapper;

    public ElasticsearchServiceClientV7(final String elasticsearchUrl,
                                        final int elasticsearchPort,
                                        final String elasticsearchScheme,
                                        final String elasticsearchAuth) {
        this(elasticsearchUrl, elasticsearchPort, elasticsearchScheme, null, elasticsearchAuth);
    }

    public ElasticsearchServiceClientV7(final String elasticsearchUrl,
                                        final int elasticsearchPort,
                                        final String elasticsearchScheme,
                                        final Integer socketTimeout,
                                        final String elasticsearchAuth) {
        this.mapper = new JacksonJsonpMapper();
        final HttpHost host = new HttpHost(elasticsearchScheme, elasticsearchUrl, elasticsearchPort);
        final ApacheHttpClient5TransportBuilder builder =
                ApacheHttpClient5TransportBuilder.builder(host)
                        .setMapper(mapper);

        if (StringUtils.isNotBlank(elasticsearchAuth)) {
            final String encodedAuth = Base64.getEncoder()
                    .encodeToString(elasticsearchAuth.getBytes(StandardCharsets.UTF_8));
            builder.setHttpClientConfigCallback(httpClientBuilder ->
                    httpClientBuilder.setDefaultHeaders(List.of(
                            new BasicHeader("Authorization", "Basic " + encodedAuth))));
        }
        if (socketTimeout != null && socketTimeout != 0) {
            builder.setRequestConfigCallback(reqConfigBuilder ->
                    reqConfigBuilder.setResponseTimeout(Timeout.ofMilliseconds(socketTimeout)));
        }
        this.client = new OpenSearchClient(builder.build());
    }

    @Override
    public ElasticStackVersion getVersion() {
        return ElasticStackVersion.V7;
    }

    @Override
    public void createIndex(final String indexName, final String source) {
        log.debug("Start to create Elasticsearch index ...");
        try {
            if (isIndexExists(indexName)) {
                log.debug("Index with name {} already exists", indexName);
                return;
            }
            final CreateIndexRequest request = CreateIndexRequest._DESERIALIZER
                    .deserialize(
                            mapper.jsonProvider().createParser(new StringReader(source)),
                            mapper
                    ).toBuilder()
                    .index(indexName).build();
            final CreateIndexResponse response = client.indices().create(request);
            if (!response.acknowledged()) {
                throw new IllegalStateException("Create Elasticsearch index: " + response);
            }
        } catch (IOException e) {
            throw new ElasticsearchException("Failed to create index request: " + e.getMessage(), e);
        }
        log.debug("Elasticsearch index with name {} was created.", indexName);
    }

    @Override
    public BulkResponseV7 sendRequests(final @Nullable String indexName,
                                       final List<? extends DocWriteRequest> docWriteRequests) {
        if (CollectionUtils.isEmpty(docWriteRequests)) {
            log.warn("Index requests are empty. ");
            return null;
        }
        final List<BulkOperation> bulkOperations = docWriteRequests.stream()
                .map(DocWriterRequestFactoryV7::toRequest)
                .collect(Collectors.toList());

        if (StringUtils.isNotBlank(indexName)) {
            log.debug("Start to insert documents for index {}", indexName);
        } else {
            log.debug("Start to bulk insert of {} documents", docWriteRequests.size());
        }

        try {
            final org.opensearch.client.opensearch.core.BulkResponse bulkResponse =
                    client.bulk(BulkRequest.of(r -> {
                        r.operations(bulkOperations);
                        if (StringUtils.isNotBlank(indexName)) {
                            r.index(indexName);
                        }
                        return r;
                    }));

            if (bulkResponse.errors()) {
                throw new IllegalStateException("Failed to create Elasticsearch documents");
            }

            if (StringUtils.isNotBlank(indexName)) {
                log.debug("Stop to insert documents for index {}", indexName);
            } else {
                log.debug("Stop to bulk documents insert");
            }

            return BulkResponseV7.builder()
                    .response(bulkResponse)
                    .build();
        } catch (IOException e) {
            throw new ElasticsearchException("Failed to insert Elasticsearch documents: " + e.getMessage(), e);
        }
    }

    @Override
    public void indexChunk(final List<DocWriteRequest> documentRequests) {
        final BulkResponseV7 response = sendRequests(null, documentRequests);
        if (Objects.isNull(response)) {
            log.debug("No documents were created in Elasticsearch for {} request(s).", documentRequests.size());
            return;
        }
        final Map<Boolean, List<BulkItemResponseV7>> indexResults = Arrays.stream(response.getItems())
                .collect(Collectors.partitioningBy(BulkItemResponseV7::isFailed));
        final List<BulkItemResponseV7> failed = indexResults.get(true);
        if (CollectionUtils.isNotEmpty(failed)) {
            log.error("Failed to insert {} of {} document(s) into Elasticsearch.",
                    failed.size(), documentRequests.size());
            failed.forEach(item -> log.error("Error for doc {} index {}: {}.",
                    item.getId(), item.getIndex(), item.getFailureMessage()));
        }
        final List<BulkItemResponseV7> successful = indexResults.get(false);
        if (CollectionUtils.isNotEmpty(successful)) {
            log.debug("Successfully inserted {} of {} document(s) into Elasticsearch).",
                    successful.size(), documentRequests.size());
        }
    }

    @Override
    public void deleteIndex(final String indexName) {
        log.debug("Start to delete index...");
        try {
            if (!isIndexExists(indexName)) {
                log.debug("Index with name does not exist. ");
                return;
            }
            client.indices().delete(r -> r.index(indexName));
        } catch (OpenSearchException | IOException e) {
            throw new ElasticsearchException("Failed to delete Elasticsearch index: " + e.getMessage(), e);
        }
        log.debug("Stop to delete index...");
    }

    @Override
    public boolean isIndexExists(final String indexName) {
        try {
            return client.indices().exists(r -> r.index(indexName)).value();
        } catch (IOException e) {
            throw new ElasticsearchException("Failed to send the request to checks index " + e.getMessage(), e);
        }
    }

    @Override
    public void createIndexAlias(final String indexName, final String indexAlias) {
        try {
            client.indices().updateAliases(r -> r.actions(a ->
                    a.add(add -> add.index(indexName).alias(indexAlias))));
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
            final GetIndexResponse response = client.indices().get(r -> r.index(alias));
            if (response.result().isEmpty()) {
                throw new ElasticsearchException("No alias is available.");
            }
            if (response.result().size() != 1) {
                throw new ElasticsearchException(
                        String.format("Unexpected indexes count: %s", response.result().size()));
            }
            return response.result().keySet().iterator().next();
        } catch (IOException e) {
            throw new ElasticsearchException("Failed to get alias name:" + e.getMessage(), e);
        }
    }

    @Override
    public List<String> findIndices(final String pattern) {
        try {
            final GetIndexResponse response = client.indices().get(r -> r.index(pattern));
            if (response.result().isEmpty()) {
                return Collections.emptyList();
            }
            return new ArrayList<>(response.result().keySet());
        } catch (IOException e) {
            throw new ElasticsearchException("Failed to get indices for pattern:" + e.getMessage(), e);
        }
    }

    @Override
    public SearchResponse search(final SearchRequest request) {
        try {
            final SearchRequestV7 inner = (SearchRequestV7) request.getInner();
            return new SearchResponseV7(client.search(inner.build(), DOCUMENT_CLASS));
        } catch (IOException e) {
            throw new ElasticsearchException("Failed to find results for search query:" + e.getMessage(), e);
        }
    }

    @Override
    public SearchResponse nextScrollPage(final String scrollId, final Scroll scroll) {
        final String keepAlive = (scroll instanceof ScrollV7)
                ? ((ScrollV7) scroll).getKeepAlive()
                : DEFAULT_SCROLL_KEEPALIVE;
        try {
            return new SearchResponseV7(
                    client.scroll(ScrollRequest.of(r ->
                            r.scrollId(scrollId).scroll(t -> t.time(keepAlive))), DOCUMENT_CLASS));
        } catch (IOException e) {
            throw new ElasticsearchException(String.format("Failed to retrieve next scroll page for [%s]: %s",
                    scrollId, e.getMessage()), e);
        }
    }

    @Override
    public SearchResponse nextScrollPage(final String scrollId) {
        return nextScrollPage(scrollId, new ScrollV7(DEFAULT_SCROLL_KEEPALIVE));
    }

    @Override
    public MultiSearchResponse search(final MultiSearchRequest request) {
        try {
            final MultiSearchRequestV7 inner = (MultiSearchRequestV7) request.getInner();
            final MsearchResponse<Map<String, Object>> response =
                    client.msearch(inner.buildMsearchRequest(), DOCUMENT_CLASS);
            return new MultiSearchResponse(new MultiSearchResponseV7(response));
        } catch (IOException e) {
            throw new ElasticsearchException("Failed to find results for multi-search query:" + e.getMessage(), e);
        }
    }

    @Override
    public List<DocWriteRequest> getDeleteRequestsByTerm(final String field,
                                                         final String value,
                                                         final String indexName) {
        final Query query = Query.of(q -> q.term(t -> t.field(field).value(FieldValue.of(value))));
        try {
            return buildDeleteRequests(indexName, query);
        } catch (OpenSearchException | IOException e) {
            return Collections.emptyList();
        }
    }

    @Override
    public List<DocWriteRequest> getDeleteRequests(final String id,
                                                   final String indexName) {
        final String wildcardId = getWildcardId(id);
        final Query query = Query.of(q -> q.wildcard(w -> w.field("id").value(wildcardId)));
        log.debug("Search request: {}", query);
        try {
            final List<DocWriteRequest> requests = buildDeleteRequests(indexName, query);
            if (CollectionUtils.isEmpty(requests)) {
                return Collections.singletonList(
                        new DeleteRequest(indexName, wildcardId, ElasticStackVersion.V7));
            }
            return requests;
        } catch (OpenSearchException | IOException e) {
            return Collections.emptyList();
        }
    }

    private List<DocWriteRequest> buildDeleteRequests(final String indexName,
                                                      final Query query) throws IOException {
        final org.opensearch.client.opensearch.core.SearchResponse<Map<String, Object>> response =
                client.search(org.opensearch.client.opensearch.core.SearchRequest.of(r ->
                        r.index(indexName).query(query)), DOCUMENT_CLASS);
        if (response.hits().total() == null || response.hits().total().value() == 0) {
            log.debug("No documents found for {} {}", indexName, query);
            return Collections.emptyList();
        }
        return response.hits().hits().stream()
                .map(hit -> {
                    log.debug("Found {} entry doc: {}", indexName, hit.id());
                    return (DocWriteRequest) new DeleteRequest(indexName, hit.id(), ElasticStackVersion.V7);
                })
                .collect(Collectors.toList());
    }

    private String getWildcardId(final String id) {
        return id + ID_DELIMITER + WILDCARD;
    }
}
