package com.epam.pipeline.dao.monitoring.metricrequester;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.entity.NByteArrayEntity;
import org.apache.lucene.util.BytesRef;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.common.xcontent.XContentType;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Optional;
import java.util.StringJoiner;

import static java.util.Collections.emptySet;

public class HeapsterElasticRestHighLevelClient extends RestHighLevelClient {

    private static final String PATH_DELIMITER = "/";
    private static final String EMPTY_STRING = "";
    private static final String ITEM_DELIMITER = ",";

    public HeapsterElasticRestHighLevelClient(final RestClientBuilder restClientBuilder) {
        super(restClientBuilder);
    }

    public final SearchResponse searchHeapsterElastic(final SearchRequest searchRequest,
                                                      final RequestOptions options) throws IOException {
        return performRequestAndParseEntity(
                searchRequest,
                this::convertToRequest,
                options,
                SearchResponse::fromXContent,
                emptySet());
    }

    private Request convertToRequest(final SearchRequest searchRequest) throws IOException {
        final String endpoint = endpoint(searchRequest.indices(), searchRequest.types(), "_search");
        Request request = new Request(HttpPost.METHOD_NAME, endpoint);
        request.addParameter("typed_keys", "true");
        request.addParameter("allow_no_indices", "true");
        request.addParameter("expand_wildcards", "open,closed");
        request.addParameter("search_type", "query_then_fetch");
        request.addParameter("ignore_unavailable", "true");
        request.addParameter("batched_reduce_size", "512");
        if (searchRequest.source() != null) {
            request.setEntity(createEntity(searchRequest.source(), XContentType.JSON));
        }
        return request;
    }

    private String endpoint(final String[] indices, final String[] types, final String endpoint) {
        final StringJoiner joiner = new StringJoiner(PATH_DELIMITER, PATH_DELIMITER, EMPTY_STRING);
        add(joiner, indices);
        add(joiner, types);
        joiner.add(endpoint);
        return joiner.toString();
    }

    private void add(final StringJoiner joiner, final String[] items) {
        Optional.ofNullable(items)
                .map(it -> String.join(ITEM_DELIMITER, it))
                .filter(StringUtils::isNotBlank)
                .ifPresent(joiner::add);
    }

    static HttpEntity createEntity(final ToXContent toXContent, final XContentType xContentType) throws IOException {
        return createEntity(toXContent, xContentType, ToXContent.EMPTY_PARAMS);
    }

    static HttpEntity createEntity(final ToXContent toXContent,
                                   final XContentType xContentType,
                                   final ToXContent.Params toXContentParams) throws IOException {
        BytesRef source = XContentHelper.toXContent(toXContent, xContentType, toXContentParams, false).toBytesRef();
        return new NByteArrayEntity(source.bytes, source.offset, source.length, createContentType(xContentType));
    }

    public static ContentType createContentType(final XContentType xContentType) {
        return ContentType.create(xContentType.mediaTypeWithoutParameters(), (Charset) null);
    }
}
