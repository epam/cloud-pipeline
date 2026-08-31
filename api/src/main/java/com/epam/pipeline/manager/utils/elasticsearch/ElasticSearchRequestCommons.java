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

package com.epam.pipeline.manager.utils.elasticsearch;

import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.entity.NByteArrayEntity;
import org.apache.lucene.util.BytesRef;
import org.elasticsearch.action.DocWriteRequest;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.MultiSearchRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.Request;
import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.lucene.uid.Versions;
import org.elasticsearch.common.xcontent.DeprecationHandler;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.VersionType;
import org.elasticsearch.index.seqno.SequenceNumbers;
import org.elasticsearch.rest.action.search.RestSearchAction;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Locale;

public final class ElasticSearchRequestCommons {

    private static final String EXPAND_WILDCARDS = "expand_wildcards";

    private ElasticSearchRequestCommons() {}

    // The elasticsearch library version 6.8.3 is not fully compatible with elasticsearch V7,
    // so an implementation that ensures proper functionality is required.
    // In this custom realization of  RequestConverters::multiSearch from ElasticSearch SDK 6.8
    // We remove serialization of Request.types() to types field, to make sure it will be compatible with ELK v7+
    public static Request multiSearchRequestConverter(MultiSearchRequest multiSearchRequest) throws IOException {
        Request request = new Request(HttpPost.METHOD_NAME, "/_msearch");

        putParam(request, RestSearchAction.TYPED_KEYS_PARAM, "true");
        putParam(request, RestSearchAction.TOTAL_HIT_AS_INT_PARAM, "true");
        if (multiSearchRequest.maxConcurrentSearchRequests() !=
                MultiSearchRequest.MAX_CONCURRENT_SEARCH_REQUESTS_DEFAULT) {
            request.addParameter("max_concurrent_searches",
                    Integer.toString(multiSearchRequest.maxConcurrentSearchRequests()));
        }

        XContent xContent =  XContentType.JSON.xContent();
        byte[] source = writeMultiLineFormat(multiSearchRequest, xContent);
        request.setEntity(new NByteArrayEntity(source, createContentType(xContent.type())));
        return request;
    }

    private static byte[] writeMultiLineFormat(MultiSearchRequest multiSearchRequest, XContent xContent)
            throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (SearchRequest request : multiSearchRequest.requests()) {
            try (XContentBuilder xContentBuilder = XContentBuilder.builder(xContent)) {
                writeSearchRequestParams(request, xContentBuilder);
                BytesReference.bytes(xContentBuilder).writeTo(output);
            }
            output.write(xContent.streamSeparator());
            try (XContentBuilder xContentBuilder = XContentBuilder.builder(xContent)) {
                if (request.source() != null) {
                    request.source().toXContent(xContentBuilder, ToXContent.EMPTY_PARAMS);
                } else {
                    xContentBuilder.startObject();
                    xContentBuilder.endObject();
                }
                BytesReference.bytes(xContentBuilder).writeTo(output);
            }
            output.write(xContent.streamSeparator());
        }
        return output.toByteArray();
    }

    private static void writeSearchRequestParams(SearchRequest request, XContentBuilder xContentBuilder)
            throws IOException {
        xContentBuilder.startObject();
        if (request.indices() != null) {
            xContentBuilder.field("index", request.indices());
        }
        if (request.indicesOptions() != null && request.indicesOptions() != SearchRequest.DEFAULT_INDICES_OPTIONS) {
            if (request.indicesOptions().expandWildcardsOpen() && request.indicesOptions().expandWildcardsClosed()) {
                xContentBuilder.field(EXPAND_WILDCARDS, "all");
            } else if (request.indicesOptions().expandWildcardsOpen()) {
                xContentBuilder.field(EXPAND_WILDCARDS, "open");
            } else if (request.indicesOptions().expandWildcardsClosed()) {
                xContentBuilder.field(EXPAND_WILDCARDS, "closed");
            } else {
                xContentBuilder.field(EXPAND_WILDCARDS, "none");
            }
            xContentBuilder.field("ignore_unavailable", request.indicesOptions().ignoreUnavailable());
            xContentBuilder.field("allow_no_indices", request.indicesOptions().allowNoIndices());
        }
        if (request.searchType() != null) {
            xContentBuilder.field("search_type", request.searchType().name().toLowerCase(Locale.ROOT));
        }
        if (request.requestCache() != null) {
            xContentBuilder.field("request_cache", request.requestCache());
        }
        if (request.preference() != null) {
            xContentBuilder.field("preference", request.preference());
        }
        if (request.routing() != null) {
            xContentBuilder.field("routing", request.routing());
        }
        if (request.allowPartialSearchResults() != null) {
            xContentBuilder.field("allow_partial_search_results", request.allowPartialSearchResults());
        }
        xContentBuilder.endObject();
    }

    // The elasticsearch library version 6.8.3 is not fully compatible with elasticsearch V7,
    // so an implementation that ensures proper functionality is required.
    // In this custom realization of  RequestConverters::bulk from ElasticSearch SDK 6.8
    // We remove serialization of Request.type() to _type, to make sure it will be compatible with ELK v7+
    public static Request bulkRequestConverter(BulkRequest bulkRequest) throws IOException {
        Request request = new Request(HttpPost.METHOD_NAME, "/_bulk");

        putParam(request, "timeout", bulkRequest.timeout().getStringRep());
        if (bulkRequest.getRefreshPolicy() != WriteRequest.RefreshPolicy.NONE) {
            putParam(request, "refresh", bulkRequest.getRefreshPolicy().getValue());
        }
        putParam(request, "pipeline", bulkRequest.pipeline());
        putParam(request, "routing", bulkRequest.routing());
        // Bulk API only supports newline delimited JSON or Smile. Before executing
        // the bulk, we need to check that all requests have the same content-type
        // and this content-type is supported by the Bulk API.
        XContentType bulkContentType = null;
        for (int i = 0; i < bulkRequest.numberOfActions(); i++) {
            DocWriteRequest<?> action = bulkRequest.requests().get(i);

            DocWriteRequest.OpType opType = action.opType();
            if (opType == DocWriteRequest.OpType.INDEX || opType == DocWriteRequest.OpType.CREATE) {
                bulkContentType = enforceSameContentType(
                        (IndexRequest) action, bulkContentType);

            } else if (opType == DocWriteRequest.OpType.UPDATE) {
                UpdateRequest updateRequest = (UpdateRequest) action;
                if (updateRequest.doc() != null) {
                    bulkContentType = enforceSameContentType(
                            updateRequest.doc(), bulkContentType);
                }
                if (updateRequest.upsertRequest() != null) {
                    bulkContentType = enforceSameContentType(
                            updateRequest.upsertRequest(), bulkContentType);
                }
            }
        }

        if (bulkContentType == null) {
            bulkContentType = XContentType.JSON;
        }

        final byte separator = bulkContentType.xContent().streamSeparator();
        final ContentType requestContentType = createContentType(bulkContentType);

        ByteArrayOutputStream content = new ByteArrayOutputStream();
        for (DocWriteRequest<?> action : bulkRequest.requests()) {
            DocWriteRequest.OpType opType = action.opType();

            try (XContentBuilder metadata = XContentBuilder.builder(bulkContentType.xContent())) {
                metadata.startObject();
                metadata.startObject(opType.getLowercase());
                if (Strings.hasLength(action.index())) {
                    metadata.field("_index", action.index());
                }
                if (Strings.hasLength(action.id())) {
                    metadata.field("_id", action.id());
                }
                if (Strings.hasLength(action.routing())) {
                    metadata.field("routing", action.routing());
                }
                if (Strings.hasLength(action.parent())) {
                    metadata.field("parent", action.parent());
                }
                if (action.version() != Versions.MATCH_ANY) {
                    metadata.field("version", action.version());
                }

                VersionType versionType = action.versionType();
                if (versionType != VersionType.INTERNAL) {
                    if (versionType == VersionType.EXTERNAL) {
                        metadata.field("version_type", "external");
                    } else if (versionType == VersionType.EXTERNAL_GTE) {
                        metadata.field("version_type", "external_gte");
                    } else if (versionType == VersionType.FORCE) {
                        metadata.field("version_type", "force");
                    }
                }

                if (action.ifSeqNo() != SequenceNumbers.UNASSIGNED_SEQ_NO) {
                    metadata.field("if_seq_no", action.ifSeqNo());
                    metadata.field("if_primary_term", action.ifPrimaryTerm());
                }

                if (opType == DocWriteRequest.OpType.INDEX || opType == DocWriteRequest.OpType.CREATE) {
                    IndexRequest indexRequest = (IndexRequest) action;
                    if (Strings.hasLength(indexRequest.getPipeline())) {
                        metadata.field("pipeline", indexRequest.getPipeline());
                    }
                } else if (opType == DocWriteRequest.OpType.UPDATE) {
                    UpdateRequest updateRequest = (UpdateRequest) action;
                    if (updateRequest.retryOnConflict() > 0) {
                        metadata.field("retry_on_conflict", updateRequest.retryOnConflict());
                    }
                    if (updateRequest.fetchSource() != null) {
                        metadata.field("_source", updateRequest.fetchSource());
                    }
                }
                metadata.endObject();
                metadata.endObject();

                BytesRef metadataSource = BytesReference.bytes(metadata).toBytesRef();
                content.write(metadataSource.bytes, metadataSource.offset, metadataSource.length);
                content.write(separator);
            }

            BytesRef source = null;
            if (opType == DocWriteRequest.OpType.INDEX || opType == DocWriteRequest.OpType.CREATE) {
                IndexRequest indexRequest = (IndexRequest) action;
                BytesReference indexSource = indexRequest.source();
                XContentType indexXContentType = indexRequest.getContentType();

                try (XContentParser parser = XContentHelper.createParser(
                        /*
                         * EMPTY and THROW are fine here because we just call
                         * copyCurrentStructure which doesn't touch the
                         * registry or deprecation.
                         */
                        NamedXContentRegistry.EMPTY, DeprecationHandler.THROW_UNSUPPORTED_OPERATION,
                        indexSource, indexXContentType)) {
                    try (XContentBuilder builder = XContentBuilder.builder(bulkContentType.xContent())) {
                        builder.copyCurrentStructure(parser);
                        source = BytesReference.bytes(builder).toBytesRef();
                    }
                }
            } else if (opType == DocWriteRequest.OpType.UPDATE) {
                source = XContentHelper.toXContent((UpdateRequest) action, bulkContentType, false).toBytesRef();
            }

            if (source != null) {
                content.write(source.bytes, source.offset, source.length);
                content.write(separator);
            }
        }
        request.setEntity(new NByteArrayEntity(content.toByteArray(), 0, content.size(), requestContentType));
        return request;
    }

    private static XContentType enforceSameContentType(IndexRequest indexRequest, @Nullable XContentType xContentType) {
        XContentType requestContentType = indexRequest.getContentType();
        if (requestContentType != XContentType.JSON && requestContentType != XContentType.SMILE) {
            throw new IllegalArgumentException("Unsupported content-type found for request with content-type ["
                    + requestContentType + "], only JSON and SMILE are supported");
        }
        if (xContentType == null) {
            return requestContentType;
        }
        if (requestContentType != xContentType) {
            throw new IllegalArgumentException("Mismatching content-type found for request with content-type ["
                    + requestContentType + "], previous requests have content-type [" + xContentType + "]");
        }
        return xContentType;
    }

    private static ContentType createContentType(final XContentType xContentType) {
        return ContentType.create(xContentType.mediaTypeWithoutParameters(), (Charset) null);
    }

    private static Request putParam(Request request, String name, String value) {
        if (Strings.hasLength(value)) {
            request.addParameter(name, value);
        }
        return request;
    }

}
