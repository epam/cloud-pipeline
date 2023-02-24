/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.StorageUsage;
import com.epam.pipeline.entity.search.SearchResult;
import com.epam.pipeline.exception.search.SearchException;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.utils.GlobalSearchElasticHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.search.MultiSearchRequest;
import org.elasticsearch.action.search.MultiSearchResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.io.IOException;
import java.util.HashSet;
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
            final SearchRequest request = requestBuilder.buildRequest(searchRequest, typeFieldName, TYPE_AGGREGATION);
            final SearchResponse searchResult = client.search(request, RequestOptions.DEFAULT);
            return resultConverter.buildResult(searchResult, TYPE_AGGREGATION, typeFieldName, getAclFilterFields());
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new SearchException(e.getMessage(), e);
        }
    }

    public StorageUsage getStorageUsage(final AbstractDataStorage dataStorage, final String path,
                                        final Set<String> storageSizeMasks, final Set<String> storageClasses,
                                        final boolean allowVersions) {
        return getStorageUsage(dataStorage, path, false, storageSizeMasks, storageClasses, allowVersions);
    }

    public StorageUsage getStorageUsage(final AbstractDataStorage dataStorage, final String path,
                                        final boolean allowNoIndex, final Set<String> storageSizeMasks,
                                        final Set<String> storageClasses, final boolean allowVersions) {
        try (RestHighLevelClient client = globalSearchElasticHelper.buildClient()) {
            final MultiSearchRequest searchRequest = requestBuilder.buildStorageSumRequest(
                    dataStorage.getId(), dataStorage.getType(), path, allowNoIndex, storageSizeMasks,
                    storageClasses, allowVersions);
            final MultiSearchResponse searchResponse = client.msearch(searchRequest, RequestOptions.DEFAULT);
            return resultConverter.buildStorageUsageResponse(searchRequest, searchResponse, dataStorage, path);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new SearchException(e.getMessage(), e);
        }
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
        Assert.notNull(request.getOffset(), "Offset is required");
    }

    private String getTypeFieldName() {
        return preferenceManager.getPreference(SystemPreferences.SEARCH_ELASTIC_TYPE_FIELD);
    }
}
