/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.test.creator.search;

import com.epam.pipeline.controller.Result;
import com.epam.pipeline.controller.vo.search.ElasticSearchRequest;
import com.epam.pipeline.entity.search.SearchDocument;
import com.epam.pipeline.entity.search.SearchDocumentType;
import com.epam.pipeline.entity.search.SearchResult;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.Collections;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_INT;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING_LIST;

public final class SearchCreatorUtils {

    public static final TypeReference<Result<SearchResult>> SEARCH_RESULT_TYPE =
            new TypeReference<Result<SearchResult>>() { };
    private static final SearchDocumentType SEARCH_DOCUMENT_TYPE = SearchDocumentType.AZ_BLOB_FILE;

    private SearchCreatorUtils() {

    }

    public static SearchResult getSearchResult() {
        final SearchResult searchResult = new SearchResult();
        searchResult.setTotalHits(ID);
        searchResult.setSearchSucceeded(true);
        searchResult.setDocuments(Collections.singletonList(getSearchDocument()));
        searchResult.setAggregates(Collections.singletonMap(SEARCH_DOCUMENT_TYPE, ID));
        return searchResult;
    }

    public static ElasticSearchRequest getElasticSearchRequest() {
        final ElasticSearchRequest elasticSearchRequest = new ElasticSearchRequest();
        elasticSearchRequest.setAggregate(true);
        elasticSearchRequest.setHighlight(true);
        elasticSearchRequest.setOffset(TEST_INT);
        elasticSearchRequest.setPageSize(TEST_INT);
        elasticSearchRequest.setQuery(TEST_STRING);
        elasticSearchRequest.setFilterTypes(Collections.singletonList(SEARCH_DOCUMENT_TYPE));
        return elasticSearchRequest;
    }

    private static SearchDocument getSearchDocument() {
        return new SearchDocument(TEST_STRING, TEST_STRING, TEST_STRING, TEST_STRING, TEST_STRING, SEARCH_DOCUMENT_TYPE,
                Collections.singletonList(new SearchDocument.HightLight(TEST_STRING, TEST_STRING_LIST)), TEST_INT);
    }
}
