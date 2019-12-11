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

package com.epam.pipeline.billingreportagent.service;

import org.elasticsearch.action.DocWriteRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;

import java.util.List;

public interface ElasticsearchServiceClient {

    void createIndex(String indexName, String source);

    BulkResponse sendRequests(String indexName, List<? extends DocWriteRequest> docWriteRequests);

    void deleteIndex(String indexName);

    boolean isIndexExists(String indexName);

    void createIndexAlias(String indexName, String indexAlias);

    String getIndexNameByAlias(String alias);

    SearchResponse search(SearchRequest request);
}
