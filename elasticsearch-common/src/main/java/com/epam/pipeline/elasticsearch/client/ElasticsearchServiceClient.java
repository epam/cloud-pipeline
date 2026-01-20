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

package com.epam.pipeline.elasticsearch.client;

import com.epam.pipeline.elasticsearch.ElasticStackVersion;
import com.epam.pipeline.elasticsearch.model.BulkResponse;
import com.epam.pipeline.elasticsearch.model.DocWriteRequest;
import com.epam.pipeline.elasticsearch.model.MultiSearchRequest;
import com.epam.pipeline.elasticsearch.model.MultiSearchResponse;
import com.epam.pipeline.elasticsearch.model.Scroll;
import com.epam.pipeline.elasticsearch.model.SearchRequest;
import com.epam.pipeline.elasticsearch.model.SearchResponse;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.message.BasicHeader;

import jakarta.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

public interface ElasticsearchServiceClient {

    String ID_DELIMITER = "-";
    String WILDCARD = "*";

    ElasticStackVersion getVersion();
    void createIndex(String indexName, String source);
    BulkResponse sendRequests(@Nullable String indexName, List<? extends DocWriteRequest> docWriteRequests);
    void indexChunk(List<DocWriteRequest> documentRequests);
    void deleteIndex(String indexName);
    boolean isIndexExists(String indexName);
    void createIndexAlias(String indexName, String indexAlias);
    String getIndexNameByAlias(String alias);
    List<String> findIndices(String pattern);
    SearchResponse search(SearchRequest request);
    SearchResponse nextScrollPage(String scrollId, Scroll scroll);
    SearchResponse nextScrollPage(String scrollId);
    MultiSearchResponse search(MultiSearchRequest request);
    List<DocWriteRequest> getDeleteRequestsByTerm(String field, String value, String indexName);
    List<DocWriteRequest> getDeleteRequests(String id, String indexName);

    static Header[] getAuthHeaders(String elasticsearchAuth) {
        if (StringUtils.isNotBlank(elasticsearchAuth)) {
            final String encodedAuth = Base64.getEncoder()
                    .encodeToString(elasticsearchAuth.getBytes(StandardCharsets.UTF_8));
            return new Header[] {new BasicHeader("Authorization", String.format("Basic %s", encodedAuth))};
        }
        return new Header[0];
    }
}
