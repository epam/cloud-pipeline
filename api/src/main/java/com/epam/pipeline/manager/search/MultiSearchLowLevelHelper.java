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

package com.epam.pipeline.manager.search;

import org.apache.commons.collections4.MapUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.entity.NStringEntity;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.action.search.MultiSearchRequest;
import org.elasticsearch.action.search.MultiSearchResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.xcontent.ContextParser;
import org.elasticsearch.common.xcontent.DeprecationHandler;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.elasticsearch.rest.action.search.RestSearchAction;
import org.elasticsearch.search.aggregations.Aggregation;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Provides implementation for low level elasticsearch client for multi-search request.
 * This class is implemented in favour of elasticsearch version 7.
 */
public class MultiSearchLowLevelHelper {
    private static final String MSEARCH_ENDPOINT = "/_msearch";
    private static final String NDJSON = "application/x-ndjson";

    public MultiSearchResponse msearch(
            final MultiSearchRequest searchRequest,
            final boolean allowNoIndex,
            final RestClient lowLevelClient,
            final Map<String, ContextParser<Object, ? extends Aggregation>> aggregations) throws IOException {
        final Request lowLevelRequest = buildRequest(searchRequest, allowNoIndex);
        final Response response = lowLevelClient.performRequest(lowLevelRequest);
        return parseResponse(response, aggregations);
    }

    private MultiSearchResponse parseResponse(
            final Response response,
            final Map<String, ContextParser<Object, ? extends Aggregation>> aggregationParsers) throws IOException {
        final String responseBody = EntityUtils.toString(response.getEntity());

        final List<NamedXContentRegistry.Entry> entries = aggregationParsers.entrySet().stream()
                .map(entry -> new NamedXContentRegistry.Entry(Aggregation.class,
                        new ParseField(entry.getKey()), entry.getValue()))
                .collect(Collectors.toList());

        final XContentParser parser = JsonXContent.jsonXContent
                .createParser(new NamedXContentRegistry(entries),
                        DeprecationHandler.THROW_UNSUPPORTED_OPERATION, responseBody);

        return MultiSearchResponse.fromXContext(parser);
    }

    private Request buildRequest(final MultiSearchRequest searchRequest,
                                 final boolean allowNoIndex) {
        final Request lowLevelRequest = new Request(HttpPost.METHOD_NAME, MSEARCH_ENDPOINT);

        if (allowNoIndex) {
            // migrating IndicesOptions.LENIENT_EXPAND_OPEN
            final Map<String, String> parameters = new HashMap<>();
            parameters.put(RestSearchAction.TYPED_KEYS_PARAM, Boolean.TRUE.toString());
            parameters.put(RestSearchAction.TOTAL_HIT_AS_INT_PARAM, Boolean.TRUE.toString());
            parameters.put("expand_wildcards", "open");
            MapUtils.emptyIfNull(parameters).forEach(lowLevelRequest::addParameter);
        }

        final String request = multiSerachRequestToString(searchRequest);
        final HttpEntity httpEntity = new NStringEntity(request, ContentType.create(NDJSON));
        lowLevelRequest.setEntity(httpEntity);

        return lowLevelRequest;
    }

    private String multiSerachRequestToString(final MultiSearchRequest searchRequest) {
        final StringBuilder requestString = new StringBuilder();
        searchRequest.requests()
                .forEach(r -> appendSearchRequest(r, requestString));
        return requestString.toString();
    }

    private void appendSearchRequest(final SearchRequest request, final StringBuilder requestString) {
        requestString.append("{ \"index\": ")
                .append(indicesToString(request.indices()))
                .append("}\n");
        requestString.append(request.source())
                .append('\n');
    }

    private String indicesToString(final String[] indices) {
        return '[' + Arrays.stream(indices)
                .map(s -> "\"" + s + "\"")
                .collect(Collectors.joining(","))
                + ']';
    }
}
