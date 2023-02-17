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
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.search.SearchDocumentType;
import com.epam.pipeline.entity.user.DefaultRoles;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.user.Role;
import com.epam.pipeline.exception.search.SearchException;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.manager.user.UserManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.search.MultiSearchRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.QueryStringQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.epam.pipeline.manager.preference.SystemPreferences.SEARCH_ELASTIC_ALLOWED_GROUPS_FIELD;
import static com.epam.pipeline.manager.preference.SystemPreferences.SEARCH_ELASTIC_ALLOWED_USERS_FIELD;
import static com.epam.pipeline.manager.preference.SystemPreferences.SEARCH_ELASTIC_DENIED_GROUPS_FIELD;
import static com.epam.pipeline.manager.preference.SystemPreferences.SEARCH_ELASTIC_DENIED_USERS_FIELD;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchRequestBuilder {

    private static final String SIZE_FIELD = "size";
    private static final String STORAGE_SIZE_BY_TIER_AGG_NAME = "sizeSumByTier";
    private static final String STORAGE_SIZE_AGG_NAME = "sizeSum";
    private static final String NAME_FIELD = "id";
    private static final String ES_FILE_INDEX_PATTERN = "cp-%s-file-%d";
    private static final String ES_DOC_ID_FIELD = "_id";
    private static final String ES_DOC_SCORE_FIELD = "_score";
    private static final String SEARCH_HIDDEN = "is_hidden";
    private static final String INDEX_WILDCARD_PREFIX = "*";

    private final PreferenceManager preferenceManager;
    private final AuthManager authManager;
    private final UserManager userManager;

    public SearchRequest buildRequest(final ElasticSearchRequest searchRequest,
                                      final String typeFieldName,
                                      final String aggregation) {
        final QueryBuilder query = getQuery(searchRequest);
        log.debug("Search query: {} ", query.toString());
        final SearchSourceBuilder searchSource = new SearchSourceBuilder()
                .query(query)
                .storedFields(Arrays.asList("id", typeFieldName, "name", "parentId", "description"))
                .size(searchRequest.getPageSize())
                .from(searchRequest.getOffset());

        if (searchRequest.isHighlight()) {
            searchSource.highlighter(SearchSourceBuilder.highlight()
                    .field("*")
                    .postTags("</highlight>")
                    .preTags("<highlight>"));
        }

        if (searchRequest.isAggregate()) {
            searchSource.aggregation(
                    AggregationBuilders.terms(aggregation)
                            .field(typeFieldName)
                            .size(SearchDocumentType.values().length));
        }

        return new SearchRequest(buildIndexNames(searchRequest.getFilterTypes()))
                .indicesOptions(IndicesOptions.lenientExpandOpen())
                .source(searchSource);
    }

    public MultiSearchRequest buildStorageSumRequest(final Long storageId, final DataStorageType storageType,
                                                     final String path, final boolean allowNoIndex,
                                                     final Set<String> storageSizeMasks) {
        final String searchIndex = String.format(
                ES_FILE_INDEX_PATTERN, storageType.toString().toLowerCase(), storageId
        );
        final MultiSearchRequest multiSearchRequest = new MultiSearchRequest();
        multiSearchRequest.add(buildSumAggRequest(
                searchIndex, path, storageType.getStorageClasses(), allowNoIndex, null));
        if (CollectionUtils.isNotEmpty(storageSizeMasks)) {
            multiSearchRequest.add(buildSumAggRequest(
                    searchIndex, path, storageType.getStorageClasses(), allowNoIndex, storageSizeMasks));
        }
        return multiSearchRequest;
    }

    private SearchRequest buildSumAggRequest(final String searchIndex, final String path,
                                             final Set<String> storageClasses, final boolean allowNoIndex,
                                             final Set<String> storageSizeMasks) {
        final BoolQueryBuilder fileFilteringQuery = QueryBuilders.boolQuery();
        CollectionUtils.emptyIfNull(storageSizeMasks)
            .forEach(mask -> fileFilteringQuery.mustNot(QueryBuilders.wildcardQuery(NAME_FIELD, mask)));
        if (StringUtils.isNotBlank(path)) {
            fileFilteringQuery.must(QueryBuilders.prefixQuery(NAME_FIELD, path));
        }
        final SearchSourceBuilder sizeSumSearch = new SearchSourceBuilder()
                .query(fileFilteringQuery)
                .size(0);

        final TermsAggregationBuilder sizeSumAgg = AggregationBuilders
                .terms(STORAGE_SIZE_BY_TIER_AGG_NAME).field("storage_class")
                .subAggregation(AggregationBuilders.sum(STORAGE_SIZE_AGG_NAME).field("size"));
        sizeSumSearch.aggregation(sizeSumAgg);
        for (String storageClass : storageClasses) {
            final String ovSizeFieldName = String.format("ov_%s_size", storageClass.toLowerCase(Locale.ROOT));
            sizeSumSearch.aggregation(AggregationBuilders.sum(ovSizeFieldName + "_agg").field(ovSizeFieldName));
        }
        final SearchRequest request = new SearchRequest().indices(searchIndex).source(sizeSumSearch);
        if (allowNoIndex) {
            request.indicesOptions(IndicesOptions.LENIENT_EXPAND_OPEN);
        }
        return request;
    }

    private QueryBuilder getQuery(final ElasticSearchRequest searchRequest) {
        final BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery()
                .must(getBasicQuery(searchRequest));
        final PipelineUser pipelineUser = userManager.loadUserByName(authManager.getAuthorizedUser());
        if (pipelineUser == null) {
            throw new IllegalArgumentException("Failed to find currently authorized user");
        }
        //no check for admins
        if (ListUtils.emptyIfNull(pipelineUser.getRoles()).stream()
                .anyMatch(role -> role.getId().equals(DefaultRoles.ROLE_ADMIN.getId()))) {
            return queryBuilder;
        }
        addAclFilters(queryBuilder, pipelineUser);
        return queryBuilder;
    }

    private QueryBuilder getBasicQuery(final ElasticSearchRequest searchRequest) {
        QueryStringQueryBuilder query = QueryBuilders.queryStringQuery(searchRequest.getQuery());
        ListUtils.emptyIfNull(preferenceManager.getPreference(SystemPreferences.SEARCH_ELASTIC_SEARCH_FIELDS))
                .forEach(query::field);
        return query;
    }

    private void addAclFilters(final BoolQueryBuilder queryBuilder, final PipelineUser pipelineUser) {
        final Set<String> authorities = getAuthorities(pipelineUser);
        final String userName = pipelineUser.getUserName();

        final String allowedUsersField = preferenceManager.getPreference(SEARCH_ELASTIC_ALLOWED_USERS_FIELD);
        final String deniedUsersField = preferenceManager.getPreference(SEARCH_ELASTIC_DENIED_USERS_FIELD);

        final List<QueryBuilder> aclQueries = queryBuilder.should();
        queryBuilder.minimumShouldMatch(1);
        // should be allowed for user
        aclQueries.add(QueryBuilders.boolQuery()
                .mustNot(QueryBuilders.termsQuery(deniedUsersField, userName))
                .filter(QueryBuilders.termsQuery(allowedUsersField, userName)));

        final String allowedGroupsField = preferenceManager.getPreference(SEARCH_ELASTIC_ALLOWED_GROUPS_FIELD);
        final String deniedGroupsField = preferenceManager.getPreference(SEARCH_ELASTIC_DENIED_GROUPS_FIELD);
        // or should be allowed and not denied for group
        aclQueries.add(QueryBuilders.boolQuery()
                .mustNot(QueryBuilders.termsQuery(deniedUsersField, userName))
                .filter(QueryBuilders.termsQuery(allowedGroupsField, authorities))
                .mustNot(QueryBuilders.termsQuery(deniedGroupsField, authorities)));
    }

    private Set<String> getAuthorities(final PipelineUser pipelineUser) {
        Set<String> authorities = new HashSet<>();
        authorities.addAll(pipelineUser.getRoles().stream().map(Role::getName).collect(Collectors.toList()));
        authorities.addAll(pipelineUser.getGroups());
        return authorities;
    }

    private String[] buildIndexNames(final List<SearchDocumentType> filterTypes) {
        if (CollectionUtils.isEmpty(filterTypes)) {
            return new String[]{preferenceManager.getPreference(SystemPreferences.SEARCH_ELASTIC_CP_INDEX_PREFIX)};
        }
        final Map<SearchDocumentType, String> typeIndexPrefixes = preferenceManager
                .getPreference(SystemPreferences.SEARCH_ELASTIC_TYPE_INDEX_PREFIX);
        if (MapUtils.isEmpty(typeIndexPrefixes)) {
            throw new SearchException("Index filtering is not configured");
        }
        return filterTypes.stream()
                .map(type -> Optional.ofNullable(typeIndexPrefixes.get(type))
                        .orElseThrow(() -> new SearchException("Missing index name for type: " + type)))
                .toArray(String[]::new);
    }
}
