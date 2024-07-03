/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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
import com.epam.pipeline.controller.vo.search.FacetedSearchExportRequest;
import com.epam.pipeline.controller.vo.search.FacetedSearchExportVO;
import com.epam.pipeline.controller.vo.search.FacetedSearchRequest;
import com.epam.pipeline.entity.search.FacetedSearchResult;
import com.epam.pipeline.entity.search.SearchDocument;
import com.epam.pipeline.entity.search.SearchDocumentType;
import com.epam.pipeline.entity.search.SearchResult;
import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_INT;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING_LIST;

public final class SearchCreatorUtils {

    public static final TypeReference<Result<SearchResult>> SEARCH_RESULT_TYPE =
            new TypeReference<Result<SearchResult>>() { };
    public static final String ROLE_USER = "ROLE_USER";
    public static final String SPECIES = "Species";
    public static final String HUMAN = "human";
    public static final String MOUSE = "mouse";
    public static final String HEADER_WITH_ATTRIBUTE = "Name,Changed,Size,Species,Owner,Path,Cloud path,Mount path";

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

    public static FacetedSearchExportRequest getFacetedSearchExportRequest(final String exportFileName,
                                                                           final String attribute) {
        final FacetedSearchExportRequest exportRequest = new FacetedSearchExportRequest();
        exportRequest.setFacetedSearchRequest(getFacetedSearchRequest(attribute));
        exportRequest.setFacetedSearchExportVO(getFacetedSearchExportVO());
        exportRequest.setCsvFileName(exportFileName);
        return exportRequest;
    }

    public static FacetedSearchResult getFacetedSearchResult(final String facetValue) {
        final FacetedSearchResult facetedSearchResult = new FacetedSearchResult();
        facetedSearchResult.setTotalHits(ID);
        if (StringUtils.isBlank(facetValue)) {
            facetedSearchResult.setDocuments(Collections.singletonList(getSearchDocument()));
            facetedSearchResult.setFacets(null);
            return facetedSearchResult;
        }
        facetedSearchResult.setDocuments(Arrays.asList(getFacetedSearchDocument(HUMAN),
                getFacetedSearchDocument(MOUSE)));
        final Map<String, Map<String, Long>> facets = new HashMap<>();
        final Map<String, Long> facetValues = new HashMap<>();
        facetValues.put(facetValue, ID);
        facets.put(SPECIES, facetValues);
        facetedSearchResult.setFacets(facets);
        return facetedSearchResult;
    }

    public static FacetedSearchRequest getFacetedSearchRequest(final String attribute) {
        final FacetedSearchRequest facetedSearchRequest = new FacetedSearchRequest();
        facetedSearchRequest.setQuery(TEST_STRING);
        facetedSearchRequest.setPageSize(TEST_INT);
        facetedSearchRequest.setOffset(TEST_INT);
        facetedSearchRequest.setHighlight(true);
        if (StringUtils.isNotBlank(attribute)) {
            facetedSearchRequest.setMetadataFields(Collections.singletonList(attribute));
        }
        final Map<String, List<String>> filters = new HashMap<>();
        filters.put(TEST_STRING, Collections.singletonList(TEST_STRING));
        facetedSearchRequest.setFilters(filters);
        return facetedSearchRequest;
    }

    private static FacetedSearchExportVO getFacetedSearchExportVO() {
        final FacetedSearchExportVO facetedSearchExportVO = new FacetedSearchExportVO();
        facetedSearchExportVO.setIncludeName(true);
        facetedSearchExportVO.setIncludeChanged(true);
        facetedSearchExportVO.setIncludeOwner(true);
        facetedSearchExportVO.setIncludePath(true);
        facetedSearchExportVO.setIncludeSize(true);
        facetedSearchExportVO.setIncludeCloudPath(true);
        facetedSearchExportVO.setIncludeMountPath(true);
        return facetedSearchExportVO;
    }

    private static SearchDocument getSearchDocument() {
        return new SearchDocument(TEST_STRING, TEST_STRING, TEST_STRING, TEST_STRING, TEST_STRING, SEARCH_DOCUMENT_TYPE,
                Collections.singletonList(new SearchDocument.HightLight(TEST_STRING, TEST_STRING_LIST)), TEST_INT,
                TEST_STRING, null);
    }

    private static SearchDocument getFacetedSearchDocument(final String species) {
        final Map<String, String> attributes = new HashMap<>();
        attributes.put(SPECIES, species);
        return new SearchDocument(TEST_STRING, TEST_STRING, TEST_STRING, TEST_STRING, TEST_STRING, SEARCH_DOCUMENT_TYPE,
                Collections.singletonList(new SearchDocument.HightLight(SPECIES, Collections.singletonList(species))),
                TEST_INT, ROLE_USER, attributes);
    }
}
