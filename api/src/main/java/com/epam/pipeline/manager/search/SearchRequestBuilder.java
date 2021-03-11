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

package com.epam.pipeline.manager.search;

import com.epam.pipeline.controller.vo.search.ElasticSearchRequest;
import com.epam.pipeline.controller.vo.search.FacetedSearchRequest;
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
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.QueryStringQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.sum.SumAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
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

    private static final String STORAGE_SIZE_AGG_NAME = "sizeSumSearch";
    private static final String SIZE_FIELD = "size";
    private static final String NAME_FIELD = "id";
    private static final String ES_FILE_INDEX_PATTERN = "cp-%s-file-%d";

    private final PreferenceManager preferenceManager;
    private final AuthManager authManager;
    private final UserManager userManager;

    public SearchRequest buildRequest(final ElasticSearchRequest searchRequest,
                                      final String typeFieldName,
                                      final String aggregation) {
        final QueryBuilder query = getQuery(searchRequest.getQuery());
        log.debug("Search query: {} ", query.toString());
        final SearchSourceBuilder searchSource = new SearchSourceBuilder()
                .query(query)
                .storedFields(buildStoredFields(typeFieldName))
                .size(searchRequest.getPageSize())
                .from(searchRequest.getOffset());

        if (searchRequest.isHighlight()) {
            addHighlighterToSource(searchSource);
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

    public SearchRequest buildSumAggregationForStorage(final Long storageId, final DataStorageType storageType,
                                                       final String path) {
        final String searchIndex =
                String.format(ES_FILE_INDEX_PATTERN, storageType.toString().toLowerCase(), storageId);
        final SumAggregationBuilder sizeSumAggregator = AggregationBuilders.sum(STORAGE_SIZE_AGG_NAME)
                .field(SIZE_FIELD);
        final SearchSourceBuilder sizeSumSearch = new SearchSourceBuilder().aggregation(sizeSumAggregator);
        if (StringUtils.isNotBlank(path)) {
            sizeSumSearch.query(QueryBuilders.prefixQuery(NAME_FIELD, path));
        }
        return new SearchRequest()
                .indices(searchIndex)
                .source(sizeSumSearch);
    }

    public SearchRequest buildFacetedRequest(final FacetedSearchRequest facetedSearchRequest,
                                             final String typeFieldName) {
        final BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();

        final QueryBuilder queryBuilder = prepareFacetedQuery(facetedSearchRequest.getQuery());
        boolQueryBuilder.must(queryBuilder);

        MapUtils.emptyIfNull(facetedSearchRequest.getFilters())
                .forEach((fieldName, values) -> boolQueryBuilder.must(filterToTermsQuery(fieldName, values)));

        log.debug("Search query: {} ", boolQueryBuilder.toString());

        final SearchSourceBuilder searchSource = new SearchSourceBuilder()
                .query(boolQueryBuilder)
                .storedFields(buildStoredFields(typeFieldName))
                .size(facetedSearchRequest.getPageSize())
                .from(facetedSearchRequest.getOffset());

        if (facetedSearchRequest.isHighlight()) {
            addHighlighterToSource(searchSource);
        }

        ListUtils.emptyIfNull(facetedSearchRequest.getFacets())
                .forEach(facet -> addTermAggregationToSource(searchSource, facet));

        return new SearchRequest()
                .indices(buildAllIndexTypes())
                .source(searchSource);
    }

    private List<String> buildStoredFields(final String typeFieldName) {
        return Arrays.asList("id", typeFieldName, "name", "parentId", "description");
    }

    private void addHighlighterToSource(final SearchSourceBuilder searchSource) {
        searchSource.highlighter(SearchSourceBuilder.highlight()
                .field("*")
                .postTags("</highlight>")
                .preTags("<highlight>"));
    }

    private QueryBuilder getQuery(final String query) {
        final BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery().must(getBasicQuery(query));
        return prepareAclFiltersOrAdmin(queryBuilder);
    }

    private QueryBuilder prepareAclFiltersOrAdmin(final BoolQueryBuilder queryBuilder) {
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

    private QueryBuilder getBasicQuery(final String searchQuery) {
        QueryStringQueryBuilder query = QueryBuilders.queryStringQuery(searchQuery);
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
            return buildAllIndexTypes();
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

    private String[] buildAllIndexTypes() {
        return new String[]{preferenceManager.getPreference(SystemPreferences.SEARCH_ELASTIC_CP_INDEX_PREFIX)};
    }

    private QueryBuilder filterToTermsQuery(final String fieldName, final List<String> values) {
        return QueryBuilders.termsQuery(fieldName, values);
    }

    private QueryBuilder prepareFacetedQuery(final String requestQuery) {
        final BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery();
        if (StringUtils.isNotBlank(requestQuery)) {
            queryBuilder.must(getBasicQuery(requestQuery));
        }
        return prepareAclFiltersOrAdmin(queryBuilder);
    }

    private void addTermAggregationToSource(final SearchSourceBuilder searchSource, final String facet) {
        final TermsAggregationBuilder aggregationBuilder = AggregationBuilders.terms(facet)
                .field(String.format("%s.keyword", facet));
        searchSource.aggregation(aggregationBuilder);
    }
}
