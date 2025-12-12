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

package com.epam.pipeline.manager.search;

import com.epam.pipeline.controller.vo.search.ElasticSearchRequest;
import com.epam.pipeline.controller.vo.search.FacetedSearchRequest;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.StorageUsage;
import com.epam.pipeline.entity.search.FacetedSearchResult;
import com.epam.pipeline.entity.search.SearchResult;
import com.epam.pipeline.exception.search.SearchException;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.utils.GlobalSearchElasticHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.search.MultiSearchRequest;
import org.elasticsearch.action.search.MultiSearchResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.ContextParser;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.StringTerms;
import org.elasticsearch.search.aggregations.metrics.sum.ParsedSum;
import org.elasticsearch.search.aggregations.metrics.sum.SumAggregationBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
@Slf4j
@RequiredArgsConstructor
public class SearchManager {

    private static final String TYPE_AGGREGATION = "by_type";

    private final PreferenceManager preferenceManager;
    private final GlobalSearchElasticHelper globalSearchElasticHelper;
    private final SearchResultConverter resultConverter;
    private final SearchRequestBuilder requestBuilder;

    public SearchResult search(final ElasticSearchRequest searchRequest) {
        validateRequest(searchRequest);
        try (RestHighLevelClient client = globalSearchElasticHelper.buildClient()) {
            final String typeFieldName = getTypeFieldName();
            final Set<String> metadataSourceFields =
                    new HashSet<>(ListUtils.emptyIfNull(searchRequest.getMetadataFields()));
            final SearchRequest request = requestBuilder.buildRequest(
                    searchRequest, typeFieldName, TYPE_AGGREGATION, metadataSourceFields);
            final SearchResponse searchResult = client.search(request, RequestOptions.DEFAULT);
            return resultConverter.buildResult(searchResult, TYPE_AGGREGATION, typeFieldName, getAclFilterFields(),
                    metadataSourceFields, searchRequest.getScrollingParameters());
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new SearchException(e.getMessage(), e);
        }
    }

    public StorageUsage getStorageUsage(final AbstractDataStorage dataStorage, final String path,
                                        final Set<String> storageSizeMasks, final Set<String> storageClasses,
                                        final boolean allowVersions) {
        return getStorageUsage(dataStorage, path, true, storageSizeMasks, storageClasses, allowVersions);
    }

    public StorageUsage getStorageUsage(final AbstractDataStorage dataStorage, final String path,
                                        final boolean allowNoIndex, final Set<String> storageSizeMasks,
                                        final Set<String> storageClasses, final boolean allowVersions) {
        try (RestHighLevelClient client = globalSearchElasticHelper.buildClient()) {
            final MultiSearchRequest searchRequest = requestBuilder.buildStorageSumRequest(
                    dataStorage.getId(), dataStorage.getType(), path, allowNoIndex, storageSizeMasks,
                    storageClasses, allowVersions);
            final MultiSearchResponse searchResponse = msearch(searchRequest, allowNoIndex, client);
            return resultConverter.buildStorageUsageResponse(searchRequest, searchResponse, dataStorage, path);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new SearchException(e.getMessage(), e);
        }
    }

    public FacetedSearchResult facetedSearch(final FacetedSearchRequest searchRequest) {
        Assert.notNull(searchRequest.getPageSize(), "Page Size is required");
        if (Objects.isNull(searchRequest.getScrollingParameters()) && Objects.isNull(searchRequest.getOffset())) {
            searchRequest.setOffset(0);
        }
        try (RestHighLevelClient client = globalSearchElasticHelper.buildClient()) {
            final String typeFieldName = getTypeFieldName();
            final Set<String> metadataSourceFields =
                    new HashSet<>(ListUtils.emptyIfNull(searchRequest.getMetadataFields()));
            final SearchRequest request = requestBuilder.buildFacetedRequest(
                    searchRequest, typeFieldName, metadataSourceFields);
            final SearchResponse response = client.search(request, RequestOptions.DEFAULT);
            return resultConverter.buildFacetedResult(response, typeFieldName, getAclFilterFields(),
                    metadataSourceFields, searchRequest.getScrollingParameters());
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new SearchException(e.getMessage(), e);
        }
    }

    public FacetedSearchResult getFacetedSearchResult(final FacetedSearchRequest facetedSearchRequest) {
        Assert.notNull(facetedSearchRequest, "Faceted search request is required");
        if (Objects.isNull(facetedSearchRequest.getPageSize())) {
            final Integer searchExportPageSize = Optional.ofNullable(
                    preferenceManager.getPreference(SystemPreferences.SEARCH_EXPORT_PAGE_SIZE))
                    .orElseThrow(() -> new IllegalArgumentException(
                            String.format("System preference %s or page size are not provided",
                                    SystemPreferences.SEARCH_EXPORT_PAGE_SIZE.getKey())));
            facetedSearchRequest.setPageSize(searchExportPageSize);
        }
        final String searchResultDisplayNameTag = preferenceManager.getPreference(
                SystemPreferences.FACETED_FILTER_DISPLAY_NAME_TAG);
        if (StringUtils.isNotBlank(searchResultDisplayNameTag)) {
            Optional.ofNullable(facetedSearchRequest.getMetadataFields())
                    .orElseGet(ArrayList::new).add(searchResultDisplayNameTag);
        }
        return facetedSearch(facetedSearchRequest);
    }

    private Set<String> getAclFilterFields() {
        final Set<String> aclFields = new HashSet<>();
        aclFields.add(preferenceManager.getSystemPreference(
                SystemPreferences.SEARCH_ELASTIC_DENIED_GROUPS_FIELD).getValue());
        aclFields.add(preferenceManager.getSystemPreference(
                SystemPreferences.SEARCH_ELASTIC_DENIED_USERS_FIELD).getValue());
        aclFields.add(preferenceManager.getSystemPreference(
                SystemPreferences.SEARCH_ELASTIC_ALLOWED_GROUPS_FIELD).getValue());
        aclFields.add(preferenceManager.getSystemPreference(
                SystemPreferences.SEARCH_ELASTIC_ALLOWED_USERS_FIELD).getValue());
        return aclFields;
    }

    private void validateRequest(final ElasticSearchRequest request) {
        Assert.isTrue(StringUtils.isNotBlank(request.getQuery()), "Search Query is required");
        Assert.notNull(request.getPageSize(), "Page Size is required");
        if (Objects.isNull(request.getScrollingParameters()) && Objects.isNull(request.getOffset())) {
            request.setOffset(0);
        }
    }

    private String getTypeFieldName() {
        return preferenceManager.getPreference(SystemPreferences.SEARCH_ELASTIC_TYPE_FIELD);
    }

    private Map<String, ContextParser<Object, ? extends Aggregation>> getSearchUsageAggregationParsers() {
        final Map<String, ContextParser<Object, ? extends Aggregation>> map = new HashMap<>();
        map.put(StringTerms.NAME, (p, c) -> ParsedStringTerms.fromXContent(p, (String) c));
        map.put(SumAggregationBuilder.NAME, (p, c) -> ParsedSum.fromXContent(p, (String) c));
        return map;
    }

    private MultiSearchResponse msearch(final MultiSearchRequest searchRequest,
                                        final boolean allowNoIndex,
                                        final RestHighLevelClient client) throws IOException {
        switch (preferenceManager.getPreference(SystemPreferences.SEARCH_ELASTIC_VERSION)) {
            case V6:
                return client.msearch(searchRequest, RequestOptions.DEFAULT);
            case V7:
                // The elasticsearch library version 6.8.3 is not fully compatible with elasticsearch V7,
                // so an implementation that ensures proper functionality is required.
                return new MultiSearchLowLevelHelper()
                        .msearch(searchRequest, allowNoIndex, client.getLowLevelClient(),
                                getSearchUsageAggregationParsers());
            default:
                throw new UnsupportedOperationException("Provided version of ELK stack currently not supported.");
        }
    }
}
